package es.medeben.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import es.medeben.config.RequiereBaseDeDatos;
import es.medeben.domain.fichaje.Apunte;
import es.medeben.domain.fichaje.EstadoDia;
import es.medeben.domain.fichaje.OrigenApunte;
import es.medeben.domain.fichaje.TipoApunte;
import es.medeben.domain.fichaje.UbicacionApunte;
import es.medeben.domain.fichaje.VeredictoUbicacion;
import es.medeben.repository.UbicacionApunteRepository;
import es.medeben.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static es.medeben.service.PdfInforme.IMPORTE;
import static es.medeben.service.PdfInforme.SUAVE;
import static es.medeben.service.PdfInforme.TEXTO;
import static es.medeben.service.PdfInforme.TEXTO_NEGRITA;
import static es.medeben.service.PdfInforme.TITULO;
import static es.medeben.service.PdfInforme.cabeceraTabla;
import static es.medeben.service.PdfInforme.capitaliza;
import static es.medeben.service.PdfInforme.celda;
import static es.medeben.service.PdfInforme.dinero;
import static es.medeben.service.PdfInforme.espacio;
import static es.medeben.service.PdfInforme.fila;
import static es.medeben.service.PdfInforme.horas;
import static es.medeben.service.PdfInforme.numero;
import static es.medeben.service.PdfInforme.numeroFino;
import static es.medeben.service.PdfInforme.seccion;
import static es.medeben.service.PdfInforme.tabla;

/**
 * El informe mensual en PDF: la evidencia que promete el README ("exporta un
 * informe con tus registros y el detalle de los cálculos") para llevar al
 * SMAC o a un abogado laboralista. Todo sale del MISMO motor que la pantalla
 * del resumen — aquí no se recalcula ni un céntimo, solo se maqueta:
 *
 * <ul>
 *   <li>el resumen del mes con el importe estimado y su desglose (D35);</li>
 *   <li>el diario día a día CON los sellos del servidor y el origen de cada
 *       apunte (confirmado / reconstruido / rectificación tardía) — lo que da
 *       fuerza probatoria a la libreta (D38);</li>
 *   <li>cada cifra de convenio/ley con su cita (D34, "no me creas: compruébalo");</li>
 *   <li>una nota de metodología y el descargo: estimación orientativa, no un
 *       dictamen (plan legal: la app informa, no asesora).</li>
 * </ul>
 */
@Service
@RequiereBaseDeDatos
public class InformeMensualService {

    private static final DateTimeFormatter DIA_CORTO =
            DateTimeFormatter.ofPattern("EEE dd/MM", PdfInforme.ES);
    private static final DateTimeFormatter SELLO =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", PdfInforme.ES);

    /** Las etiquetas en cristiano, las mismas que usa la pantalla de la libreta. */
    private static final Map<EstadoDia.Estado, String> ETIQUETA_ESTADO = Map.of(
            EstadoDia.Estado.PENDIENTE, "Sin apuntar todavía",
            EstadoDia.Estado.EN_CURSO, "En curso: falta la salida",
            EstadoDia.Estado.COMPLETO, "Completo",
            EstadoDia.Estado.AUSENCIA, "No fue, y quedó apuntado",
            EstadoDia.Estado.HUECO, "Hueco: quedó sin apuntar",
            EstadoDia.Estado.NO_CUADRA, "Los apuntes no cuadran: revísalo");

    private static final Map<OrigenApunte, String> ETIQUETA_ORIGEN = Map.of(
            OrigenApunte.CONFIRMADO, "fichado al momento",
            OrigenApunte.RECONSTRUIDO, "reconstruido después",
            OrigenApunte.RECTIFICACION_TARDIA, "rectificación tardía");

    private static final Map<TipoApunte, String> ETIQUETA_TIPO = Map.of(
            TipoApunte.ENTRADA, "Entrada",
            TipoApunte.SALIDA, "Salida",
            TipoApunte.AUSENCIA, "Ausencia");

    /**
     * Etiquetas de "Anotar dónde fichas" (contrato §Backend, punto 10). FUERA
     * se rotula "en otra ubicación" (nunca "incidencia") y SIN distancia
     * numérica: la distancia a un punto que la empresa conoce podría revelar
     * el domicilio del trabajador si fichó por error desde casa. Por eso
     * ninguno de los tres veredictos geométricos imprime distancia en el PDF
     * principal — la fina, con su margen de error, solo en el anexo técnico
     * (endpoint aparte), que es del titular.
     */
    private static final Map<VeredictoUbicacion, String> ETIQUETA_VEREDICTO = Map.of(
            VeredictoUbicacion.DENTRO, "en el centro",
            VeredictoUbicacion.FUERA, "en otra ubicación",
            VeredictoUbicacion.NO_CONCLUYENTE, "no concluyente",
            VeredictoUbicacion.SUPRIMIDA, "ubicación retirada por el titular");

    private final ResumenMensualService resumenes;
    private final FichajeService fichajes;
    private final UsuarioRepository usuarios;
    private final UbicacionApunteRepository ubicaciones;
    private final Clock reloj;

    public InformeMensualService(ResumenMensualService resumenes, FichajeService fichajes,
                                 UsuarioRepository usuarios, UbicacionApunteRepository ubicaciones,
                                 Clock reloj) {
        this.resumenes = resumenes;
        this.fichajes = fichajes;
        this.usuarios = usuarios;
        this.ubicaciones = ubicaciones;
        this.reloj = reloj;
    }

    /** Overload sin ubicación: el informe de siempre, sin tocar ni una línea (no-regresión). */
    @Transactional(readOnly = true)
    public byte[] genera(UUID usuarioId, YearMonth mes) {
        return genera(usuarioId, mes, false);
    }

    /**
     * Genera el informe del mes del usuario autenticado. Reutiliza
     * {@link ResumenMensualService#delMes}, así que hereda sus mismas
     * salvaguardas: 400 si el mes es futuro y 422 (con la guía de qué falta)
     * si no hay perfil, horario o tabla — nunca un PDF con cifras inventadas.
     *
     * @param incluyeUbicacion casilla desmarcada por defecto en la UI
     *                         (contrato §Backend/§8 síntesis): con ella activa,
     *                         cada apunte con ubicación anotada gana una línea
     *                         (nunca coordenadas, nunca distancia en FUERA).
     */
    @Transactional(readOnly = true)
    public byte[] genera(UUID usuarioId, YearMonth mes, boolean incluyeUbicacion) {
        ResumenMensual resumen = resumenes.delMes(usuarioId, mes);
        LocalDate hoy = LocalDate.now(reloj);
        LocalDate finListado = min(mes.atEndOfMonth(), hoy);
        Map<LocalDate, EstadoDia> dias = fichajes.estadosDelPeriodo(usuarioId, mes.atDay(1), finListado);
        String email = usuarios.findById(usuarioId).map(u -> u.getEmail()).orElse("(cuenta no disponible)");
        // UNA sola query para todo el mes (anti N+1, misma disciplina que estadosDelPeriodo).
        // Sin el parámetro activado, ni se consulta: el informe de quien no usa la
        // feature no cambia aunque haya filas en base.
        Map<UUID, UbicacionApunte> ubicacionesPorApunte = incluyeUbicacion
                ? ubicaciones.findByUsuarioIdAndFechaBetween(usuarioId, mes.atDay(1), finListado).stream()
                        .collect(Collectors.toMap(UbicacionApunte::getApunteId, Function.identity(),
                                (a, b) -> a, LinkedHashMap::new))
                : Map.of();

        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 48, 48, 48, 56);
            try {
                PdfWriter.getInstance(doc, salida);
                doc.open();

                cabecera(doc, mes, email);
                resumenDelMes(doc, resumen);
                avisos(doc, resumen.avisos());
                diario(doc, mes, finListado, dias, ubicacionesPorApunte);
                fuentes(doc, resumen);
                comoLeerlo(doc, resumen, incluyeUbicacion);
            } finally {
                if (doc.isOpen()) {
                    doc.close();
                }
            }
            return salida.toByteArray();
        } catch (DocumentException | java.io.IOException e) {
            // Sin datos del usuario en el mensaje: el detalle va al log por la traza.
            throw new IllegalStateException("No se pudo generar el informe PDF", e);
        }
    }

    private void cabecera(Document doc, YearMonth mes, String email) {
        doc.add(new Paragraph("MeDeben — Informe de registro de jornada", TITULO));
        ZonedDateTime ahora = ZonedDateTime.now(reloj);
        Paragraph sub = new Paragraph(
                capitaliza(PdfInforme.MES_LARGO.format(mes)) + "  ·  generado el "
                        + PdfInforme.FECHA_LARGA.format(ahora) + " a las "
                        + PdfInforme.HORA_CORTA.format(ahora)
                        + "  ·  cuenta: " + email,
                SUAVE);
        sub.setSpacingAfter(14);
        doc.add(sub);
    }

    private void resumenDelMes(Document doc, ResumenMensual resumen) {
        doc.add(seccion("Lo tuyo, este mes"));

        ImporteEstimadoMensual importe = resumen.importe();
        boolean hayExtras = resumen.minutosExtra() > 0;
        if (hayExtras) {
            doc.add(new Paragraph("Por tus horas extra te deben, como mínimo", TEXTO));
            Paragraph cifra = new Paragraph(dinero(importe.importe()) + " €", IMPORTE);
            cifra.setSpacingAfter(4);
            doc.add(cifra);
            doc.add(new Paragraph(
                    horas(resumen.minutosExtra()) + " extra a " + dinero(importe.precioHora())
                            + " € la hora.", TEXTO));
        } else {
            Paragraph nada = new Paragraph("Este mes no hay horas extra apuntadas.", TEXTO);
            nada.setSpacingAfter(4);
            doc.add(nada);
        }

        PdfPTable tabla = tabla(new float[]{3, 2});
        fila(tabla, "Según tu horario", horas(resumen.minutosTeoricos()));
        fila(tabla, "Apuntado en tu libreta", horas(resumen.minutosReales()));
        fila(tabla, "Horas extra", horas(resumen.minutosExtra()));
        if (resumen.minutosDeficit() > 0) {
            fila(tabla, "Horas de menos (informativo)", horas(resumen.minutosDeficit()));
        }
        if (resumen.diasSinCalcular() > 0) {
            fila(tabla, "Días sin calcular (no cuentan)", String.valueOf(resumen.diasSinCalcular()));
        }
        // issue #230: los días cuyos apuntes se contradicen quedan fuera del
        // total, pero constan aquí y con su etiqueta en el diario de abajo —
        // en un informe que es evidencia, un cero mudo sería un dato falso.
        int diasNoCuadran = resumen.contadoresPorEstado().getOrDefault(EstadoDia.Estado.NO_CUADRA, 0);
        if (diasNoCuadran > 0) {
            fila(tabla, "Días que no cuadran (revísalos, no cuentan)", String.valueOf(diasNoCuadran));
        }
        fila(tabla, "Tope anual de horas extra",
                numero(resumen.tope().acumuladoAnioHoras()) + " h de "
                        + resumen.tope().horasTope() + " h (" + resumen.mes().getYear() + ")");
        tabla.setSpacingBefore(8);
        tabla.setSpacingAfter(8);
        doc.add(tabla);

        ValorHoraCalculado d = importe.desglose();
        String divisor = d.esDivisorExplicito() ? "divisor del convenio" : "jornada anual";
        Paragraph desglose = new Paragraph(
                "De dónde sale la hora: (" + dinero(d.salarioBaseMensual()) + " € de salario base"
                        + etiquetaOrigenBase(importe)
                        + " × " + numero(d.mensualidades()) + " pagas + "
                        + dinero(d.plusesAnuales()) + " € de pluses anuales) / "
                        + numero(d.divisorHoras()) + " h de " + divisor + " = "
                        + numeroFino(d.valorHora()) + " € la hora ordinaria.",
                SUAVE);
        desglose.setSpacingAfter(12);
        doc.add(desglose);
    }

    /**
     * Etiqueta del origen de la base del desglose (PR #251, retoque de copy):
     * salario real declarado, suelo del SMI (art. 27 ET, la tabla queda por
     * debajo) o el mínimo de la tabla del convenio. {@code bajoSmi} y
     * {@code salarioRealUsado} nunca son true a la vez.
     */
    private static String etiquetaOrigenBase(ImporteEstimadoMensual importe) {
        if (importe.salarioRealUsado()) {
            return " —tu salario declarado, mayor que el mínimo del convenio—";
        }
        if (importe.bajoSmi()) {
            return " —el suelo del SMI (tu tabla está por debajo)—";
        }
        return " —el mínimo de tu convenio—";
    }

    private void avisos(Document doc, List<String> avisos) {
        if (avisos.isEmpty()) {
            return;
        }
        doc.add(seccion("Avisos"));
        for (String aviso : avisos) {
            doc.add(new Paragraph("•  " + aviso, TEXTO));
        }
        doc.add(espacio());
    }

    private void diario(Document doc, YearMonth mes, LocalDate fin, Map<LocalDate, EstadoDia> dias,
                        Map<UUID, UbicacionApunte> ubicacionesPorApunte) {
        doc.add(seccion("Tu diario, día a día"));
        doc.add(new Paragraph(
                "Cada apunte lleva el sello del servidor (cuándo se apuntó de verdad) y su origen. "
                        + "Nada se reescribe: las correcciones se añaden y quedan.", SUAVE));

        PdfPTable tabla = tabla(new float[]{2.2f, 3.2f, 2.2f, 6.4f});
        cabeceraTabla(tabla, "Día", "Estado", "Jornada", "Apuntes (hora — origen — sello)");
        for (LocalDate dia = mes.atDay(1); !dia.isAfter(fin); dia = dia.plusDays(1)) {
            EstadoDia estado = dias.get(dia);
            if (estado == null) {
                continue; // defensivo: el servicio devuelve el periodo completo
            }
            celda(tabla, capitaliza(DIA_CORTO.format(dia)), TEXTO_NEGRITA);
            celda(tabla, ETIQUETA_ESTADO.get(estado.estado()), TEXTO);
            celda(tabla, jornadaDe(estado), TEXTO);
            celda(tabla, apuntesDe(estado.apuntes(), ubicacionesPorApunte), TEXTO);
        }
        tabla.setSpacingBefore(6);
        tabla.setSpacingAfter(12);
        doc.add(tabla);
    }

    /** La lectura del día: tramos derivados y, si la jornada quedó abierta, desde cuándo. */
    private static String jornadaDe(EstadoDia estado) {
        List<String> partes = new ArrayList<>();
        for (EstadoDia.TramoDia tramo : estado.tramos()) {
            partes.add(tramo.entrada() + " – " + tramo.salida());
        }
        if (estado.entradaAbierta() != null) {
            partes.add("desde " + estado.entradaAbierta());
        }
        if (partes.isEmpty()) {
            return "—";
        }
        String total = estado.minutosTrabajados() >= 0 ? "  (" + horas(estado.minutosTrabajados()) + ")" : "";
        return String.join("  ·  ", partes) + total;
    }

    private String apuntesDe(List<Apunte> apuntes, Map<UUID, UbicacionApunte> ubicacionesPorApunte) {
        if (apuntes.isEmpty()) {
            return "—";
        }
        List<String> lineas = new ArrayList<>(apuntes.size());
        for (Apunte a : apuntes) {
            StringBuilder linea = new StringBuilder(ETIQUETA_TIPO.get(a.getTipo()));
            if (a.getHora() != null) {
                linea.append(' ').append(a.getHora());
            }
            linea.append(" — ").append(ETIQUETA_ORIGEN.get(a.getOrigen()))
                    .append(" — ").append(sello(a.getRegistradoEn()));
            if (a.getMotivo() != null) {
                linea.append(" — motivo: ").append(a.getMotivo());
            }
            // Mismo patrón null-safe que motivo (sin tocar anchos de columna, D9/§8):
            // solo aparece la línea si HAY ubicación anotada para ese apunte, y solo
            // con el parámetro activado (el mapa llega vacío si no, ver genera()).
            UbicacionApunte ubicacion = ubicacionesPorApunte.get(a.getId());
            if (ubicacion != null) {
                linea.append(" — ubicación: ").append(ETIQUETA_VEREDICTO.get(ubicacion.getVeredicto()));
                if (ubicacion.getVeredicto() != VeredictoUbicacion.SUPRIMIDA) {
                    // Nunca distancia numérica aquí (ni siquiera en DENTRO/NO_CONCLUYENTE,
                    // que sí la permitirían por diseño): la fina vive solo en el anexo
                    // técnico. Solo la precisión reportada, que no localiza nada.
                    linea.append(" (±").append(ubicacion.getPrecisionMetros()).append(" m)");
                }
            }
            lineas.add(linea.toString());
        }
        return String.join("\n", lineas);
    }

    private void fuentes(Document doc, ResumenMensual resumen) {
        doc.add(seccion("Fuentes (compruébalo)"));
        Set<String> vistas = new LinkedHashSet<>();
        List<Cita> todas = new ArrayList<>(resumen.importe().citas());
        todas.addAll(resumen.tope().citas());
        int n = 0;
        for (Cita cita : todas) {
            String clave = cita.texto() + "|" + cita.url();
            if (!vistas.add(clave)) {
                continue;
            }
            n++;
            Paragraph p = new Paragraph(n + ". " + cita.texto()
                    + (cita.url() != null ? "\n    " + cita.url() : ""), SUAVE);
            p.setSpacingAfter(3);
            doc.add(p);
        }
        doc.add(espacio());
    }

    private void comoLeerlo(Document doc, ResumenMensual resumen, boolean incluyeUbicacion) {
        doc.add(seccion("Cómo leer este informe"));
        doc.add(new Paragraph(
                "El diario es de solo-añadir: ningún apunte se borra ni se reescribe; corregir es añadir "
                        + "otro apunte, y todos quedan a la vista con su sello. \"Fichado al momento\" se apuntó "
                        + "en el día (o esa madrugada); \"reconstruido después\", más tarde dentro de los 14 días "
                        + "de margen; \"rectificación tardía\", cuando el día ya estaba protegido — se registra "
                        + "aparte y lo protegido no se toca. Esa disciplina es la que hace de esta libreta un "
                        + "registro propio con valor como indicio de prueba.", TEXTO));
        if (incluyeUbicacion) {
            doc.add(parrafoSobreUbicacion());
        }
        // Disclaimer C5 (pie del informe): el mismo texto de la pantalla, con el
        // convenio y el año reales del mes de este informe.
        doc.add(PdfInforme.notaConvenio(resumen.convenioNombre(), resumen.mes().getYear(),
                resumen.convenioBoletin()));
        doc.add(PdfInforme.descargo());
    }

    /**
     * Párrafo "Sobre la ubicación" (contrato §Backend, síntesis §8), con las
     * correcciones vinculantes: los sellos se describen como generados por un
     * sistema independiente del trabajador y del empresario (nunca "que nadie
     * puede reescribir" — no hay anclaje externo todavía), y la afirmación de
     * reproducibilidad se imprime SOLO aquí, condicionada a que esta misma
     * opción esté activada (si no, el informe no puede afirmar por escrito una
     * propiedad — el anexo técnico — que el lector no puede verificar).
     */
    private static Paragraph parrafoSobreUbicacion() {
        Paragraph p = new Paragraph(
                "Sobre la ubicación. Cada punto lo capturó el propio dispositivo del titular en el "
                        + "momento de fichar, únicamente en los fichajes registrados al momento, y con "
                        + "precisión aproximada (de barrio, no de portal). El punto de referencia (centro de "
                        + "trabajo) lo declaró el titular en la fecha que consta en el anexo técnico. Los "
                        + "sellos de fecha y hora los genera un sistema independiente del trabajador y del "
                        + "empresario. Es un dato autocapturado en un dispositivo bajo control del titular: "
                        + "corrobora, no acredita por sí solo. Con el anexo técnico (que incluye coordenadas, "
                        + "el punto de referencia y la fórmula), un tercero puede recalcular cada resultado de "
                        + "este informe. \"En otra ubicación\" puede deberse a que ese día se trabajó en otro "
                        + "sitio, a mala cobertura o a que el margen de error era ajustado.",
                TEXTO);
        p.setSpacingBefore(4);
        p.setSpacingAfter(4);
        return p;
    }

    private String sello(OffsetDateTime registradoEn) {
        return SELLO.format(registradoEn.atZoneSameInstant(reloj.getZone()));
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
