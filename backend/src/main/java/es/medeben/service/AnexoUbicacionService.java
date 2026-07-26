package es.medeben.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import es.medeben.config.RequiereBaseDeDatos;
import es.medeben.domain.fichaje.CentroTrabajo;
import es.medeben.domain.fichaje.UbicacionApunte;
import es.medeben.domain.fichaje.VeredictoCalculador;
import es.medeben.domain.fichaje.VeredictoUbicacion;
import es.medeben.repository.CentroTrabajoRepository;
import es.medeben.repository.UbicacionApunteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static es.medeben.service.PdfInforme.SUAVE;
import static es.medeben.service.PdfInforme.TEXTO;
import static es.medeben.service.PdfInforme.TEXTO_NEGRITA;
import static es.medeben.service.PdfInforme.TITULO;
import static es.medeben.service.PdfInforme.cabeceraTabla;
import static es.medeben.service.PdfInforme.celda;
import static es.medeben.service.PdfInforme.espacio;
import static es.medeben.service.PdfInforme.seccion;
import static es.medeben.service.PdfInforme.tabla;

/**
 * Anexo técnico de "Anotar dónde fichas" (síntesis §8, contrato §Backend
 * punto 10) — PDF APARTE del informe mensual, bajo acción explícita del
 * titular. Es el ÚNICO canal por el que salen coordenadas del sistema: sin
 * él, el informe principal no podría afirmar que sus resultados son
 * recalculables por un tercero sin ser circular.
 */
@Service
@RequiereBaseDeDatos
public class AnexoUbicacionService {

    private static final DateTimeFormatter FECHA_CORTA = DateTimeFormatter.ofPattern("dd/MM/yyyy", PdfInforme.ES);
    private static final DateTimeFormatter SELLO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", PdfInforme.ES);

    private static final Map<VeredictoUbicacion, String> ETIQUETA_VEREDICTO = Map.of(
            VeredictoUbicacion.DENTRO, "Dentro",
            VeredictoUbicacion.FUERA, "Fuera",
            VeredictoUbicacion.NO_CONCLUYENTE, "No concluyente",
            VeredictoUbicacion.SUPRIMIDA, "Retirada por el titular");

    private final UbicacionApunteRepository ubicaciones;
    private final CentroTrabajoRepository centros;
    private final Clock reloj;

    public AnexoUbicacionService(UbicacionApunteRepository ubicaciones, CentroTrabajoRepository centros,
                                 Clock reloj) {
        this.ubicaciones = ubicaciones;
        this.centros = centros;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public byte[] genera(UUID usuarioId, YearMonth mes) {
        // UNA sola query para todo el mes (anti N+1, misma disciplina que el informe principal).
        List<UbicacionApunte> filas = ubicaciones.findByUsuarioIdAndFechaBetween(
                usuarioId, mes.atDay(1), mes.atEndOfMonth());
        Optional<CentroTrabajo> centro = centros.findFirstByUsuarioIdOrderByDeclaradoEnDesc(usuarioId)
                .filter(CentroTrabajo::isVigente);

        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 48, 48, 48, 56);
            try {
                PdfWriter.getInstance(doc, salida);
                doc.open();

                cabecera(doc, mes);
                avisos(doc);
                declaracionDelCentro(doc, centro);
                formula(doc);
                tablaDeCoordenadas(doc, filas);
            } finally {
                if (doc.isOpen()) {
                    doc.close();
                }
            }
            return salida.toByteArray();
        } catch (DocumentException | java.io.IOException e) {
            throw new IllegalStateException("No se pudo generar el anexo técnico", e);
        }
    }

    private void cabecera(Document doc, YearMonth mes) {
        doc.add(new Paragraph("MeDeben — Anexo técnico de ubicación", TITULO));
        Paragraph sub = new Paragraph(PdfInforme.capitaliza(PdfInforme.MES_LARGO.format(mes)), SUAVE);
        sub.setSpacingAfter(10);
        doc.add(sub);
    }

    private void avisos(Document doc) {
        Paragraph previo = new Paragraph(
                "Este anexo contiene tus coordenadas aproximadas. Entrégalo solo a tu abogado.", TEXTO_NEGRITA);
        previo.setSpacingAfter(6);
        doc.add(previo);

        // Aviso anti-coacción, LITERAL del texto de consentimiento v1.0 (contrato,
        // corrección ALTO del verificador rgpd-play: riesgo de coacción del empresario).
        Paragraph antiCoaccion = new Paragraph(
                "Importante: tu empresa no puede exigirte activar esto ni entregarle el anexo con "
                        + "tus coordenadas. Si te lo piden, eso es control por geolocalización y debe cumplir "
                        + "el art. 90 de la LOPDGDD.", TEXTO);
        antiCoaccion.setSpacingAfter(10);
        doc.add(antiCoaccion);
    }

    private void declaracionDelCentro(Document doc, Optional<CentroTrabajo> centro) {
        doc.add(seccion("Centro de trabajo declarado"));
        if (centro.isEmpty()) {
            doc.add(new Paragraph("No hay un centro de trabajo vigente declarado.", TEXTO));
            doc.add(espacio());
            return;
        }
        CentroTrabajo c = centro.get();
        String alias = c.getAlias() == null ? "(sin alias)" : c.getAlias();
        doc.add(new Paragraph(alias + " — declarado el "
                + SELLO.format(c.getDeclaradoEn().atZoneSameInstant(reloj.getZone()))
                + " — lat " + c.getLatitud() + ", lon " + c.getLongitud()
                + " — radio " + c.getRadioMetros() + " m", TEXTO));
        doc.add(espacio());
    }

    private void formula(Document doc) {
        doc.add(seccion("Cómo se calcula el veredicto"));
        doc.add(new Paragraph(
                "d = distancia (fórmula de Haversine) entre el punto anotado y el centro declarado; "
                        + "p = precisión reportada por el sistema; r = radio del centro. Con p > "
                        + VeredictoCalculador.PRECISION_MAXIMA_METROS + " m el resultado es no concluyente "
                        + "(un fix así no afirma nada). Si no, d + " + VeredictoCalculador.FACTOR_SIGMA
                        + "×p ≤ r es DENTRO; d − " + VeredictoCalculador.FACTOR_SIGMA + "×p > r es FUERA; "
                        + "el resto, no concluyente. El factor " + VeredictoCalculador.FACTOR_SIGMA
                        + " dobla el radio de confianza que reporta Android (68 %, 1σ) para acercarse al 95 %.",
                TEXTO));
        doc.add(espacio());
    }

    private void tablaDeCoordenadas(Document doc, List<UbicacionApunte> filas) {
        doc.add(seccion("Coordenadas del mes"));
        PdfPTable t = tabla(new float[]{2f, 1.6f, 2f, 2f, 1.6f, 1.8f, 2.2f});
        cabeceraTabla(t, "Fecha", "Hora", "Latitud", "Longitud", "Precisión", "Distancia", "Veredicto");
        for (UbicacionApunte u : filas) {
            celda(t, FECHA_CORTA.format(u.getFecha()), TEXTO);
            celda(t, SELLO.format(u.getRegistradaEn().atZoneSameInstant(reloj.getZone())).substring(11), TEXTO);
            celda(t, u.getLatitud() == null ? "—" : u.getLatitud().toPlainString(), TEXTO);
            celda(t, u.getLongitud() == null ? "—" : u.getLongitud().toPlainString(), TEXTO);
            celda(t, u.getPrecisionMetros() + " m", TEXTO);
            celda(t, u.getDistanciaMetros() == null ? "—" : u.getDistanciaMetros() + " m", TEXTO);
            celda(t, ETIQUETA_VEREDICTO.get(u.getVeredicto()), TEXTO);
        }
        t.setSpacingBefore(6);
        doc.add(t);
    }
}
