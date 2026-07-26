package es.medeben.service;

import es.medeben.domain.fichaje.CentroTrabajo;
import es.medeben.domain.fichaje.UbicacionApunte;
import es.medeben.domain.fichaje.VeredictoCalculador;
import es.medeben.domain.fichaje.VeredictoUbicacion;
import es.medeben.repository.CentroTrabajoRepository;
import es.medeben.repository.UbicacionApunteRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Anexo técnico (síntesis §8, endpoint aparte §5): el ÚNICO canal por el que
 * salen coordenadas del sistema. Bajo petición explícita del titular, nunca
 * el que ve la empresa.
 */
class AnexoUbicacionServiceTest {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-07-10T10:00:00Z"), ZoneId.of("Europe/Madrid"));
    private static final YearMonth MES = YearMonth.of(2026, 7);
    private static final BigDecimal LAT = new BigDecimal("40.41675");
    private static final BigDecimal LON = new BigDecimal("-3.70379");

    private UbicacionApunteRepository ubicaciones;
    private CentroTrabajoRepository centros;
    private AnexoUbicacionService servicio;

    @BeforeEach
    void arranque() {
        ubicaciones = mock(UbicacionApunteRepository.class);
        centros = mock(CentroTrabajoRepository.class);
        servicio = new AnexoUbicacionService(ubicaciones, centros, RELOJ);

        CentroTrabajo centro = new CentroTrabajo(USUARIO, "El bar", LAT, LON, 150,
                OffsetDateTime.parse("2026-07-01T09:00:00+02:00"));
        when(centros.findFirstByUsuarioIdOrderByDeclaradoEnDesc(USUARIO)).thenReturn(Optional.of(centro));
    }

    private String textoDelAnexo() throws Exception {
        byte[] pdf = servicio.genera(USUARIO, MES);
        assertThat(pdf).isNotEmpty();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    @DisplayName("el anexo SÍ contiene las coordenadas — es el único canal permitido")
    void elAnexoContieneCoordenadas() throws Exception {
        UbicacionApunte fila = new UbicacionApunte(UUID.randomUUID(), USUARIO, LocalDate.of(2026, 7, 8),
                LAT, LON, 20, UUID.randomUUID(), LAT, LON, 150, 15, VeredictoUbicacion.DENTRO,
                OffsetDateTime.parse("2026-07-08T10:00:00+02:00"));
        when(ubicaciones.findByUsuarioIdAndFechaBetween(eq(USUARIO), any(), any())).thenReturn(List.of(fila));

        String texto = textoDelAnexo().replaceAll("\\s+", " ");

        assertThat(texto).contains("40.41675").contains("3.70379");
    }

    @Test
    @DisplayName("lleva el aviso previo y el aviso anti-coacción del art. 90 LOPDGDD")
    void llevaLosAvisos() throws Exception {
        when(ubicaciones.findByUsuarioIdAndFechaBetween(eq(USUARIO), any(), any())).thenReturn(List.of());

        String texto = textoDelAnexo().replaceAll("\\s+", " ");

        assertThat(texto).contains("Entrégalo solo a tu abogado");
        assertThat(texto).contains("tu empresa no puede exigirte activar esto");
        assertThat(texto).contains("art. 90");
        assertThat(texto).contains("LOPDGDD");
    }

    @Test
    @DisplayName("lleva la fórmula del veredicto, con el factor sigma documentado")
    void llevaLaFormula() throws Exception {
        when(ubicaciones.findByUsuarioIdAndFechaBetween(eq(USUARIO), any(), any())).thenReturn(List.of());

        String texto = textoDelAnexo().replaceAll("\\s+", " ");

        assertThat(texto).contains("radio de confianza");
        assertThat(texto).contains(String.valueOf(VeredictoCalculador.FACTOR_SIGMA));
        assertThat(texto).contains(String.valueOf(VeredictoCalculador.PRECISION_MAXIMA_METROS));
    }

    @Test
    @DisplayName("una fila SUPRIMIDA no muestra sus coordenadas, aunque sea el anexo")
    void filaSuprimidaNoMuestraCoordenadas() throws Exception {
        UbicacionApunte suprimida = new UbicacionApunte(UUID.randomUUID(), USUARIO, LocalDate.of(2026, 7, 8),
                LAT, LON, 20, UUID.randomUUID(), LAT, LON, 150, 15, VeredictoUbicacion.DENTRO,
                OffsetDateTime.parse("2026-07-08T10:00:00+02:00"));
        suprimida.suprime();
        when(ubicaciones.findByUsuarioIdAndFechaBetween(eq(USUARIO), any(), any())).thenReturn(List.of(suprimida));

        String texto = textoDelAnexo();

        // "40.41675" SÍ aparece una vez, en la declaración del centro (legítimo:
        // es el centro vigente, no la fila suprimida). Lo que no puede pasar es
        // que aparezca una SEGUNDA vez —la de la fila, que debe salir "—"—.
        int apariciones = texto.split("40\\.41675", -1).length - 1;
        assertThat(apariciones).isEqualTo(1);
        assertThat(texto.replaceAll("\\s+", " ")).containsIgnoringCase("retirada por el titular");
    }

    @Test
    @DisplayName("cada fila imprime SU propio centro congelado, no el centro vigente actual — recomputabilidad")
    void cadaFilaImprimeSuPropioCentroCongelado() throws Exception {
        // El usuario redeclaró el centro DESPUÉS: el vigente ahora está en otras
        // coordenadas ("41.38706", solo debe salir en el bloque de contexto de
        // cabecera). La fila del mes pasado se calculó contra el centro VIEJO
        // (columnas congeladas centro_latitud/centro_longitud = "40.41675"),
        // que es el que el anexo tiene que imprimir como referencia de ESA fila
        // — su propia ubicación anotada usa una tercera coordenada ("41.00000")
        // para poder distinguir las tres en el texto plano del PDF.
        BigDecimal ubicacionLat = new BigDecimal("41.00000");
        BigDecimal ubicacionLon = new BigDecimal("1.00000");
        BigDecimal centroViejoLat = LAT;
        BigDecimal centroViejoLon = LON;
        BigDecimal centroNuevoLat = new BigDecimal("41.38706");
        BigDecimal centroNuevoLon = new BigDecimal("2.17009");
        CentroTrabajo vigenteDistinto = new CentroTrabajo(USUARIO, "El bar nuevo", centroNuevoLat, centroNuevoLon,
                150, OffsetDateTime.parse("2026-07-20T09:00:00+02:00"));
        when(centros.findFirstByUsuarioIdOrderByDeclaradoEnDesc(USUARIO)).thenReturn(Optional.of(vigenteDistinto));
        UbicacionApunte fila = new UbicacionApunte(UUID.randomUUID(), USUARIO, LocalDate.of(2026, 7, 8),
                ubicacionLat, ubicacionLon, 20, UUID.randomUUID(), centroViejoLat, centroViejoLon, 150, 15,
                VeredictoUbicacion.DENTRO, OffsetDateTime.parse("2026-07-08T10:00:00+02:00"));
        when(ubicaciones.findByUsuarioIdAndFechaBetween(eq(USUARIO), any(), any())).thenReturn(List.of(fila));

        String texto = textoDelAnexo().replaceAll("\\s+", " ");

        // El centro VIEJO (el que de verdad se usó para calcular la distancia
        // de esta fila) tiene que aparecer como referencia de la tabla.
        assertThat(texto).contains("40.41675");
        // El centro NUEVO (vigente ahora) solo puede salir UNA vez: en el
        // bloque de contexto de cabecera, nunca repetido como si fuera la
        // referencia de cálculo de una fila que no lo usó.
        int aparicionesNuevo = texto.split("41\\.38706", -1).length - 1;
        assertThat(aparicionesNuevo).isEqualTo(1);
    }

    @Test
    @DisplayName("declara el centro vigente con su fecha de declaración")
    void declaraElCentroVigente() throws Exception {
        when(ubicaciones.findByUsuarioIdAndFechaBetween(eq(USUARIO), any(), any())).thenReturn(List.of());

        String texto = textoDelAnexo().replaceAll("\\s+", " ");

        assertThat(texto).contains("El bar");
        assertThat(texto).contains("01/07/2026");
    }

    @Test
    @DisplayName("un mes entero se resuelve con una sola query (anti N+1)")
    void unMesEnteroSeResuelveConUnaSolaQuery() throws Exception {
        when(ubicaciones.findByUsuarioIdAndFechaBetween(eq(USUARIO), any(), any())).thenReturn(List.of());

        textoDelAnexo();

        verify(ubicaciones, times(1)).findByUsuarioIdAndFechaBetween(eq(USUARIO), any(), any());
    }
}
