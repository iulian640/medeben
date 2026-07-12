package es.medeben.service;

import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;

import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * La maquetación común de los informes en PDF (mensual y anual): la paleta de
 * la casa en papel, las fuentes, las tablas con su borde fino y el formateo
 * es-ES de números y horas. Aquí no vive NINGUNA cuenta de dinero — solo cómo
 * se pinta lo que los motores ya calcularon.
 */
final class PdfInforme {

    static final Locale ES = Locale.forLanguageTag("es-ES");
    static final java.time.format.DateTimeFormatter FECHA_LARGA =
            java.time.format.DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", ES);
    static final java.time.format.DateTimeFormatter MES_LARGO =
            java.time.format.DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ES);
    static final java.time.format.DateTimeFormatter HORA_CORTA =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm", ES);

    /* La paleta del papel: tinta y el verde sobrio de la casa. */
    private static final Color TINTA = new Color(0x24, 0x24, 0x33);
    private static final Color TINTA_SUAVE = new Color(0x5c, 0x5e, 0x70);
    private static final Color VERDE = new Color(0x0f, 0x76, 0x6e);
    private static final Color LINEA = new Color(0xd9, 0xda, 0xe2);

    static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, TINTA);
    static final Font SECCION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, TINTA);
    static final Font TEXTO = FontFactory.getFont(FontFactory.HELVETICA, 10, TINTA);
    static final Font TEXTO_NEGRITA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, TINTA);
    static final Font SUAVE = FontFactory.getFont(FontFactory.HELVETICA, 9, TINTA_SUAVE);
    static final Font IMPORTE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, VERDE);

    static {
        // Font cachea su BaseFont de forma perezosa en el primer render y esa
        // inicialización no es thread-safe: se fuerza aquí, en la carga de la
        // clase (un solo hilo), y las peticiones concurrentes ya solo LEEN.
        for (Font fuente : List.of(TITULO, SECCION, TEXTO, TEXTO_NEGRITA, SUAVE, IMPORTE)) {
            fuente.getCalculatedBaseFont(true);
        }
    }

    private PdfInforme() {
    }

    private static final int MIN_POR_HORA = 60;

    static Paragraph seccion(String titulo) {
        Paragraph p = new Paragraph(titulo, SECCION);
        p.setSpacingBefore(10);
        p.setSpacingAfter(6);
        return p;
    }

    static Paragraph espacio() {
        Paragraph p = new Paragraph(" ", TEXTO);
        p.setSpacingAfter(2);
        return p;
    }

    static PdfPTable tabla(float[] anchos) {
        PdfPTable tabla = new PdfPTable(anchos);
        tabla.setWidthPercentage(100);
        tabla.setHorizontalAlignment(Element.ALIGN_LEFT);
        return tabla;
    }

    static void cabeceraTabla(PdfPTable tabla, String... titulos) {
        for (String titulo : titulos) {
            celda(tabla, titulo, TEXTO_NEGRITA);
        }
    }

    static void fila(PdfPTable tabla, String etiqueta, String valor) {
        celda(tabla, etiqueta, TEXTO);
        celda(tabla, valor, TEXTO_NEGRITA);
    }

    static void celda(PdfPTable tabla, String contenido, Font fuente) {
        PdfPCell celda = new PdfPCell(new Phrase(contenido, fuente));
        celda.setBorderColor(LINEA);
        celda.setPadding(5);
        tabla.addCell(celda);
    }

    // --- formato es-ES: los mismos números que la pantalla, sin sorpresas ---

    static String dinero(BigDecimal importe) {
        NumberFormat formato = NumberFormat.getNumberInstance(ES);
        formato.setMinimumFractionDigits(2);
        formato.setMaximumFractionDigits(2);
        // El mismo redondeo que Intl.NumberFormat en la app (halfExpand): sin
        // esto, un empate de céntimo saldría distinto en el papel y en pantalla.
        formato.setRoundingMode(RoundingMode.HALF_UP);
        return formato.format(importe);
    }

    /** Número es-ES sin decimales de relleno: 3.00 → "3", 3.5 → "3,5", 1800 → "1.800". */
    static String numero(BigDecimal valor) {
        NumberFormat formato = NumberFormat.getNumberInstance(ES);
        formato.setMaximumFractionDigits(2);
        formato.setRoundingMode(RoundingMode.HALF_UP);
        return formato.format(valor);
    }

    /** El valor hora con su precisión de cálculo (4 decimales), también en es-ES. */
    static String numeroFino(BigDecimal valor) {
        NumberFormat formato = NumberFormat.getNumberInstance(ES);
        formato.setMaximumFractionDigits(4);
        formato.setRoundingMode(RoundingMode.HALF_UP);
        return formato.format(valor);
    }

    /** Minutos → "7 h 30 min" (o "45 min"), como en la app. */
    static String horas(long minutos) {
        long h = minutos / MIN_POR_HORA;
        long min = minutos % MIN_POR_HORA;
        if (h == 0) {
            return min + " min";
        }
        return min == 0 ? h + " h" : h + " h " + min + " min";
    }

    static String capitaliza(String texto) {
        return texto.isEmpty() ? texto : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    /**
     * El disclaimer C5 (punto 1 de docs/legal/disclaimers.md), con el convenio
     * y el año REALES: nunca "cifrado" ni datos inventados. Sin boletín (la
     * fuente del convenio no lo trae tipado), se omite en vez de rellenarlo.
     */
    static Paragraph notaConvenio(String convenioNombre, int anio, String convenioBoletin) {
        String referencia = convenioBoletin != null ? anio + ", " + convenioBoletin : String.valueOf(anio);
        Paragraph p = new Paragraph(
                "Cálculo orientativo según las tablas del convenio " + convenioNombre + " (" + referencia
                        + "). Puede contener errores o no reflejar tu situación concreta. Verifica con un "
                        + "profesional o tu sindicato antes de reclamar.", SUAVE);
        p.setSpacingBefore(4);
        p.setSpacingAfter(4);
        return p;
    }

    /** El descargo honesto, igual en todos los informes: informa, no dictamina. */
    static Paragraph descargo() {
        Paragraph p = new Paragraph(
                "El importe es el MÍNIMO estimado según las tablas y artículos citados (cómputo con el "
                        + "salario base aplicado y sin conceptos que este informe no recoge). Es información "
                        + "orientativa, no un dictamen: antes de reclamar, contrástalo con tu sindicato o con "
                        + "un profesional. Generado por MeDeben, software libre (AGPL).", SUAVE);
        p.setSpacingBefore(6);
        return p;
    }
}
