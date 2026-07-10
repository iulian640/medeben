package es.medeben.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import es.medeben.config.RequiereBaseDeDatos;
import es.medeben.domain.fichaje.Apunte;
import es.medeben.domain.fichaje.EstadoDia;
import es.medeben.domain.fichaje.OrigenApunte;
import es.medeben.domain.fichaje.TipoApunte;
import es.medeben.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

    private static final Locale ES = Locale.forLanguageTag("es-ES");
    private static final DateTimeFormatter DIA_CORTO = DateTimeFormatter.ofPattern("EEE dd/MM", ES);
    private static final DateTimeFormatter FECHA_LARGA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", ES);
    private static final DateTimeFormatter MES_LARGO = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ES);
    private static final DateTimeFormatter SELLO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", ES);

    private static final int MIN_POR_HORA = 60;

    /* La paleta del papel: tinta y el verde sobrio de la casa. */
    private static final Color TINTA = new Color(0x24, 0x24, 0x33);
    private static final Color TINTA_SUAVE = new Color(0x5c, 0x5e, 0x70);
    private static final Color VERDE = new Color(0x0f, 0x76, 0x6e);
    private static final Color LINEA = new Color(0xd9, 0xda, 0xe2);

    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, TINTA);
    private static final Font SECCION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, TINTA);
    private static final Font TEXTO = FontFactory.getFont(FontFactory.HELVETICA, 10, TINTA);
    private static final Font TEXTO_NEGRITA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, TINTA);
    private static final Font SUAVE = FontFactory.getFont(FontFactory.HELVETICA, 9, TINTA_SUAVE);
    private static final Font IMPORTE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, VERDE);

    static {
        // Font cachea su BaseFont de forma perezosa en el primer render y esa
        // inicialización no es thread-safe: se fuerza aquí, en la carga de la
        // clase (un solo hilo), y las peticiones concurrentes ya solo LEEN.
        for (Font fuente : List.of(TITULO, SECCION, TEXTO, TEXTO_NEGRITA, SUAVE, IMPORTE)) {
            fuente.getCalculatedBaseFont(true);
        }
    }

    /** Las etiquetas en cristiano, las mismas que usa la pantalla de la libreta. */
    private static final Map<EstadoDia.Estado, String> ETIQUETA_ESTADO = Map.of(
            EstadoDia.Estado.PENDIENTE, "Sin apuntar todavía",
            EstadoDia.Estado.EN_CURSO, "En curso: falta la salida",
            EstadoDia.Estado.COMPLETO, "Completo",
            EstadoDia.Estado.AUSENCIA, "No fue, y quedó apuntado",
            EstadoDia.Estado.HUECO, "Hueco: quedó sin apuntar");

    private static final Map<OrigenApunte, String> ETIQUETA_ORIGEN = Map.of(
            OrigenApunte.CONFIRMADO, "fichado al momento",
            OrigenApunte.RECONSTRUIDO, "reconstruido después",
            OrigenApunte.RECTIFICACION_TARDIA, "rectificación tardía");

    private static final Map<TipoApunte, String> ETIQUETA_TIPO = Map.of(
            TipoApunte.ENTRADA, "Entrada",
            TipoApunte.SALIDA, "Salida",
            TipoApunte.AUSENCIA, "Ausencia");

    private final ResumenMensualService resumenes;
    private final FichajeService fichajes;
    private final UsuarioRepository usuarios;
    private final Clock reloj;

    public InformeMensualService(ResumenMensualService resumenes, FichajeService fichajes,
                                 UsuarioRepository usuarios, Clock reloj) {
        this.resumenes = resumenes;
        this.fichajes = fichajes;
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    /**
     * Genera el informe del mes del usuario autenticado. Reutiliza
     * {@link ResumenMensualService#delMes}, así que hereda sus mismas
     * salvaguardas: 400 si el mes es futuro y 422 (con la guía de qué falta)
     * si no hay perfil, horario o tabla — nunca un PDF con cifras inventadas.
     */
    @Transactional(readOnly = true)
    public byte[] genera(UUID usuarioId, YearMonth mes) {
        ResumenMensual resumen = resumenes.delMes(usuarioId, mes);
        LocalDate hoy = LocalDate.now(reloj);
        LocalDate finListado = min(mes.atEndOfMonth(), hoy);
        Map<LocalDate, EstadoDia> dias = fichajes.estadosDelPeriodo(usuarioId, mes.atDay(1), finListado);
        String email = usuarios.findById(usuarioId).map(u -> u.getEmail()).orElse("(cuenta no disponible)");

        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 48, 48, 48, 56);
            try {
                PdfWriter.getInstance(doc, salida);
                doc.open();

                cabecera(doc, mes, email);
                resumenDelMes(doc, resumen);
                avisos(doc, resumen.avisos());
                diario(doc, mes, finListado, dias);
                fuentes(doc, resumen);
                comoLeerlo(doc);
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
                capitaliza(MES_LARGO.format(mes)) + "  ·  generado el "
                        + FECHA_LARGA.format(ahora) + " a las " + ahora.format(DateTimeFormatter.ofPattern("HH:mm"))
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
        fila(tabla, "Tope anual de horas extra",
                numero(resumen.tope().acumuladoAnioHoras()) + " h de "
                        + resumen.tope().horasTope() + " h (año en curso)");
        tabla.setSpacingBefore(8);
        tabla.setSpacingAfter(8);
        doc.add(tabla);

        ValorHoraCalculado d = importe.desglose();
        String divisor = d.esDivisorExplicito() ? "divisor del convenio" : "jornada anual";
        Paragraph desglose = new Paragraph(
                "De dónde sale la hora: (" + dinero(d.salarioBaseMensual()) + " € de salario base"
                        + (importe.salarioRealUsado()
                        ? " —tu salario declarado, mayor que el mínimo del convenio—"
                        : " —el mínimo de tu convenio—")
                        + " × " + numero(d.mensualidades()) + " pagas + "
                        + dinero(d.plusesAnuales()) + " € de pluses anuales) / "
                        + numero(d.divisorHoras()) + " h de " + divisor + " = "
                        + numeroFino(d.valorHora()) + " € la hora ordinaria.",
                SUAVE);
        desglose.setSpacingAfter(12);
        doc.add(desglose);
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

    private void diario(Document doc, YearMonth mes, LocalDate fin, Map<LocalDate, EstadoDia> dias) {
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
            celda(tabla, apuntesDe(estado.apuntes()), TEXTO);
        }
        tabla.setSpacingBefore(6);
        tabla.setSpacingAfter(12);
        doc.add(tabla);
    }

    /** La lectura del día: tramos derivados y, si la jornada quedó abierta, desde cuándo.
     *  Guion largo y no la flecha "→" de la app: fuera de cp1252, Helvetica la pierde. */
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

    private String apuntesDe(List<Apunte> apuntes) {
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

    private void comoLeerlo(Document doc) {
        doc.add(seccion("Cómo leer este informe"));
        doc.add(new Paragraph(
                "El diario es de solo-añadir: ningún apunte se borra ni se reescribe; corregir es añadir "
                        + "otro apunte, y todos quedan a la vista con su sello. \"Fichado al momento\" se apuntó "
                        + "en el día (o esa madrugada); \"reconstruido después\", más tarde dentro de los 14 días "
                        + "de margen; \"rectificación tardía\", cuando el día ya estaba protegido — se registra "
                        + "aparte y lo protegido no se toca. Esa disciplina es la que hace de esta libreta un "
                        + "registro propio con valor como indicio de prueba.", TEXTO));
        Paragraph descargo = new Paragraph(
                "El importe es el MÍNIMO estimado según las tablas y artículos citados (cómputo con el "
                        + "salario base aplicado y sin conceptos que este informe no recoge). Es información "
                        + "orientativa, no un dictamen: antes de reclamar, contrástalo con tu sindicato o con "
                        + "un profesional. Generado por MeDeben, software libre (AGPL).", SUAVE);
        descargo.setSpacingBefore(6);
        doc.add(descargo);
    }

    // --- utilidades de maquetación ---

    private static Paragraph seccion(String titulo) {
        Paragraph p = new Paragraph(titulo, SECCION);
        p.setSpacingBefore(10);
        p.setSpacingAfter(6);
        return p;
    }

    private static Paragraph espacio() {
        Paragraph p = new Paragraph(" ", TEXTO);
        p.setSpacingAfter(2);
        return p;
    }

    private static PdfPTable tabla(float[] anchos) {
        PdfPTable tabla = new PdfPTable(anchos);
        tabla.setWidthPercentage(100);
        tabla.setHorizontalAlignment(Element.ALIGN_LEFT);
        return tabla;
    }

    private static void cabeceraTabla(PdfPTable tabla, String... titulos) {
        for (String titulo : titulos) {
            PdfPCell celda = new PdfPCell(new Phrase(titulo, TEXTO_NEGRITA));
            celda.setBorderColor(LINEA);
            celda.setPadding(5);
            tabla.addCell(celda);
        }
    }

    private static void fila(PdfPTable tabla, String etiqueta, String valor) {
        celda(tabla, etiqueta, TEXTO);
        celda(tabla, valor, TEXTO_NEGRITA);
    }

    private static void celda(PdfPTable tabla, String contenido, Font fuente) {
        PdfPCell celda = new PdfPCell(new Phrase(contenido, fuente));
        celda.setBorderColor(LINEA);
        celda.setPadding(5);
        tabla.addCell(celda);
    }

    // --- formato ---

    private static String dinero(BigDecimal importe) {
        NumberFormat formato = NumberFormat.getNumberInstance(ES);
        formato.setMinimumFractionDigits(2);
        formato.setMaximumFractionDigits(2);
        // El mismo redondeo que Intl.NumberFormat en la app (halfExpand): sin
        // esto, un empate de céntimo saldría distinto en el papel y en pantalla.
        formato.setRoundingMode(RoundingMode.HALF_UP);
        return formato.format(importe);
    }

    /** Número es-ES sin decimales de relleno: 3.00 → "3", 3.5 → "3,5", 1800 → "1.800". */
    private static String numero(BigDecimal valor) {
        NumberFormat formato = NumberFormat.getNumberInstance(ES);
        formato.setMaximumFractionDigits(2);
        formato.setRoundingMode(RoundingMode.HALF_UP);
        return formato.format(valor);
    }

    /** El valor hora con su precisión de cálculo (4 decimales), también en es-ES. */
    private static String numeroFino(BigDecimal valor) {
        NumberFormat formato = NumberFormat.getNumberInstance(ES);
        formato.setMaximumFractionDigits(4);
        formato.setRoundingMode(RoundingMode.HALF_UP);
        return formato.format(valor);
    }

    /** Minutos → "7 h 30 min" (o "45 min"), como en la app. */
    private static String horas(long minutos) {
        long h = minutos / MIN_POR_HORA;
        long min = minutos % MIN_POR_HORA;
        if (h == 0) {
            return min + " min";
        }
        return min == 0 ? h + " h" : h + " h " + min + " min";
    }

    private String sello(OffsetDateTime registradoEn) {
        return SELLO.format(registradoEn.atZoneSameInstant(reloj.getZone()));
    }

    private static String capitaliza(String texto) {
        return texto.isEmpty() ? texto : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
