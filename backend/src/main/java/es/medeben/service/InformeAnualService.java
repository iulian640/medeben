package es.medeben.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import es.medeben.config.RequiereBaseDeDatos;
import es.medeben.controller.ResumenIncompletoException;
import es.medeben.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static es.medeben.service.PdfInforme.IMPORTE;
import static es.medeben.service.PdfInforme.SUAVE;
import static es.medeben.service.PdfInforme.TEXTO;
import static es.medeben.service.PdfInforme.TITULO;
import static es.medeben.service.PdfInforme.cabeceraTabla;
import static es.medeben.service.PdfInforme.capitaliza;
import static es.medeben.service.PdfInforme.celda;
import static es.medeben.service.PdfInforme.dinero;
import static es.medeben.service.PdfInforme.espacio;
import static es.medeben.service.PdfInforme.horas;
import static es.medeben.service.PdfInforme.numero;
import static es.medeben.service.PdfInforme.seccion;
import static es.medeben.service.PdfInforme.tabla;

/**
 * El histórico anual en PDF: la foto del año mes a mes, pensada para la
 * reclamación (el plazo del art. 59 ET es de un año). Cada mes se agrega con
 * {@link ResumenMensualService#delMes} — el mismo motor que la pantalla y que
 * el informe mensual, ni una cuenta nueva. El detalle día a día con sellos
 * vive en los informes MENSUALES; aquí van los agregados y los totales.
 *
 * <p>Generarlo recorre el año entero varias veces: por eso (petición de
 * producto) se genera COMO MUCHO una vez al día por usuario y año — la
 * primera descarga del día lo construye y el resto del día se sirve el mismo
 * PDF (que lleva visible su "generado el..."). Encima de esta caché sigue el
 * presupuesto estricto de rate limit de {@code /api/v1/informes/**}.
 */
@Service
@RequiereBaseDeDatos
public class InformeAnualService {

    /** "Una vez al día o así" (petición de producto): vigencia de la caché. */
    static final Duration VIGENCIA_CACHE = Duration.ofHours(24);

    /**
     * Tope de entradas cacheadas. La clave es usuario autenticado + año
     * validado, así que crece con usuarios REALES (no la infla un atacante
     * anónimo), pero un tope es un tope: al superarlo se purga lo caducado y,
     * si no basta, lo más antiguo.
     */
    static final int MAX_ENTRADAS_CACHE = 1000;

    /** Cota inferior del año consultable (la misma que la de los meses). */
    private static final Year ANIO_MINIMO = Year.of(2019);

    private record Generado(byte[] pdf, Instant en) {
    }

    private final ResumenMensualService resumenes;
    private final UsuarioRepository usuarios;
    private final Clock reloj;
    private final ConcurrentHashMap<String, Generado> cache = new ConcurrentHashMap<>();

    public InformeAnualService(ResumenMensualService resumenes, UsuarioRepository usuarios, Clock reloj) {
        this.resumenes = resumenes;
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    public byte[] genera(UUID usuarioId, Year anio) {
        Year anioActual = Year.now(reloj);
        if (anio.isAfter(anioActual)) {
            throw new IllegalArgumentException(
                    "El año " + anio + " todavía no ha empezado: aún no hay nada que resumir");
        }
        if (anio.isBefore(ANIO_MINIMO)) {
            throw new IllegalArgumentException(
                    "El año " + anio + " es anterior a " + ANIO_MINIMO + ": no hay libreta que resumir tan atrás");
        }

        String clave = usuarioId + "|" + anio;
        Instant ahora = Instant.now(reloj);
        Generado guardado = cache.get(clave);
        if (guardado != null && guardado.en().plus(VIGENCIA_CACHE).isAfter(ahora)) {
            return guardado.pdf();
        }

        byte[] pdf = generaDeVerdad(usuarioId, anio, anioActual);
        purgaSiHaceFalta(ahora);
        cache.put(clave, new Generado(pdf, ahora));
        return pdf;
    }

    /**
     * Purga la caché de un usuario. La debe llamar el borrado de cuenta (RGPD
     * art. 17, feature pendiente): sin esto, su PDF —con email y salarios—
     * viviría en memoria hasta 24 h después de borrarse.
     */
    public void invalida(UUID usuarioId) {
        String prefijo = usuarioId + "|";
        cache.keySet().removeIf(clave -> clave.startsWith(prefijo));
    }

    /** Mantiene la caché acotada: primero lo caducado; si sigue llena, lo más antiguo. */
    private void purgaSiHaceFalta(Instant ahora) {
        if (cache.size() < MAX_ENTRADAS_CACHE) {
            return;
        }
        cache.entrySet().removeIf(e -> e.getValue().en().plus(VIGENCIA_CACHE).isBefore(ahora));
        while (cache.size() >= MAX_ENTRADAS_CACHE) {
            cache.entrySet().stream()
                    .min(Comparator.comparing(e -> e.getValue().en()))
                    .map(Map.Entry::getKey)
                    .ifPresent(cache::remove);
        }
    }

    /** Un mes del año: su resumen si se pudo agregar, o el motivo de que no. */
    private record MesDelAnio(YearMonth mes, ResumenMensual resumen, String motivoSinDatos) {
    }

    /*
     * Sin @Transactional aquí a propósito, por dos razones que se pisan:
     * (1) el hit de caché no debe pedirle una conexión al pool solo para
     * devolver bytes de memoria (review Opus); (2) anotar este método privado
     * sería código muerto — el proxy de Spring no intercepta autollamadas.
     * Cada delMes ya corre en SU transacción de lectura (vía su propio proxy):
     * cada mes es internamente consistente; entre meses no hay snapshot común,
     * lo cual con una generación al día es irrelevante.
     */
    private byte[] generaDeVerdad(UUID usuarioId, Year anio, Year anioActual) {
        YearMonth ultimo = anio.equals(anioActual) ? YearMonth.now(reloj) : anio.atMonth(12);
        List<MesDelAnio> meses = new ArrayList<>();
        for (YearMonth mes = anio.atMonth(1); !mes.isAfter(ultimo); mes = mes.plusMonths(1)) {
            try {
                meses.add(new MesDelAnio(mes, resumenes.delMes(usuarioId, mes), null));
            } catch (ResumenIncompletoException e) {
                // Un mes sin perfil/horario/tabla no tumba el año: se lista con
                // su motivo (honestidad) y no aporta a los totales.
                meses.add(new MesDelAnio(mes, null, e.getMessage()));
            }
        }
        if (meses.stream().allMatch(m -> m.resumen() == null)) {
            throw new ResumenIncompletoException(
                    "Ningún mes de " + anio + " tiene datos suficientes para el histórico: "
                            + "crea tu perfil y tu horario, y ficha tus días");
        }

        String email = usuarios.findById(usuarioId).map(u -> u.getEmail()).orElse("(cuenta no disponible)");

        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 48, 48, 48, 56);
            try {
                PdfWriter.getInstance(doc, salida);
                doc.open();

                cabecera(doc, anio, email);
                totales(doc, meses);
                tablaMeses(doc, meses);
                topeAnual(doc, meses);
                fuentes(doc, meses);
                comoLeerlo(doc);
            } finally {
                if (doc.isOpen()) {
                    doc.close();
                }
            }
            return salida.toByteArray();
        } catch (DocumentException | java.io.IOException e) {
            throw new IllegalStateException("No se pudo generar el histórico anual PDF", e);
        }
    }

    private void cabecera(Document doc, Year anio, String email) {
        doc.add(new Paragraph("MeDeben — Histórico anual de registro de jornada", TITULO));
        ZonedDateTime ahora = ZonedDateTime.now(reloj);
        Paragraph sub = new Paragraph(
                "Año " + anio + "  ·  generado el " + PdfInforme.FECHA_LARGA.format(ahora)
                        + " a las " + PdfInforme.HORA_CORTA.format(ahora)
                        + "  ·  cuenta: " + email
                        + "  ·  se genera como mucho una vez al día",
                SUAVE);
        sub.setSpacingAfter(14);
        doc.add(sub);
    }

    private static void totales(Document doc, List<MesDelAnio> meses) {
        long extraMin = 0;
        BigDecimal importe = BigDecimal.ZERO;
        for (MesDelAnio m : meses) {
            if (m.resumen() != null) {
                extraMin += m.resumen().minutosExtra();
                importe = importe.add(m.resumen().importe().importe());
            }
        }
        doc.add(seccion("Lo tuyo, este año"));
        if (extraMin > 0) {
            doc.add(new Paragraph("Por tus horas extra del año te deben, como mínimo", TEXTO));
            Paragraph cifra = new Paragraph(dinero(importe) + " €", IMPORTE);
            cifra.setSpacingAfter(4);
            doc.add(cifra);
            doc.add(new Paragraph(horas(extraMin) + " extra en total (suma de los meses con datos).", TEXTO));
        } else {
            doc.add(new Paragraph("En los meses con datos no hay horas extra apuntadas.", TEXTO));
        }
        doc.add(espacio());
    }

    private static void tablaMeses(Document doc, List<MesDelAnio> meses) {
        doc.add(seccion("El año, mes a mes"));
        PdfPTable tabla = tabla(new float[]{2.4f, 2.6f, 2.6f, 2.2f, 2.2f});
        cabeceraTabla(tabla, "Mes", "Según horario", "Apuntado", "Extra", "Importe");
        for (MesDelAnio m : meses) {
            celda(tabla, capitaliza(PdfInforme.MES_LARGO.format(m.mes())), PdfInforme.TEXTO_NEGRITA);
            if (m.resumen() == null) {
                celda(tabla, "Sin datos suficientes: " + m.motivoSinDatos(), SUAVE);
                celda(tabla, "—", SUAVE);
                celda(tabla, "—", SUAVE);
                celda(tabla, "—", SUAVE);
                continue;
            }
            ResumenMensual r = m.resumen();
            celda(tabla, horas(r.minutosTeoricos()), TEXTO);
            celda(tabla, horas(r.minutosReales()), TEXTO);
            celda(tabla, horas(r.minutosExtra()), TEXTO);
            celda(tabla, r.minutosExtra() > 0 ? dinero(r.importe().importe()) + " €" : "—", TEXTO);
        }
        tabla.setSpacingBefore(6);
        tabla.setSpacingAfter(8);
        doc.add(tabla);
        doc.add(new Paragraph(
                "El detalle día a día, con los sellos y el origen de cada apunte, está en el informe "
                        + "mensual de cada mes.", SUAVE));
        doc.add(espacio());
    }

    /** El tope anual según el ÚLTIMO mes con datos: su acumulado ya es el del año hasta ahí. */
    private static void topeAnual(Document doc, List<MesDelAnio> meses) {
        meses.stream()
                .filter(m -> m.resumen() != null)
                .max(Comparator.comparing(MesDelAnio::mes))
                .ifPresent(m -> {
                    TopeAnualResumen tope = m.resumen().tope();
                    doc.add(seccion("Tu año, contra el tope legal"));
                    // Honestidad de cifras (review Opus): el acumulado del tope
                    // recorre TODO el año, también los días de meses que la tabla
                    // de arriba no pudo valorar — puede ser mayor que la suma.
                    doc.add(new Paragraph(
                            "Llevas " + numero(tope.acumuladoAnioHoras()) + " h extra de las "
                                    + tope.horasTope() + " h que permite la ley al año (a fecha del último "
                                    + "mes con datos; incluye también las horas de meses que arriba no "
                                    + "pudieron valorarse).", TEXTO));
                    doc.add(espacio());
                });
    }

    private static void fuentes(Document doc, List<MesDelAnio> meses) {
        doc.add(seccion("Fuentes (compruébalo)"));
        Set<String> vistas = new LinkedHashSet<>();
        int n = 0;
        for (MesDelAnio m : meses) {
            if (m.resumen() == null) {
                continue;
            }
            List<Cita> todas = new ArrayList<>(m.resumen().importe().citas());
            todas.addAll(m.resumen().tope().citas());
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
        }
        doc.add(espacio());
    }

    private static void comoLeerlo(Document doc) {
        doc.add(seccion("Cómo leer este informe"));
        doc.add(new Paragraph(
                "Cada fila agrega un mes comparando tu horario con tu diario, con las mismas reglas que "
                        + "la app: solo cuentan los días completos con total fiable, las horas de menos nunca "
                        + "compensan las extra, y un mes al que le falte perfil, horario o tabla se lista sin "
                        + "cifras antes que inventarlas.", TEXTO));
        doc.add(PdfInforme.descargo());
    }
}
