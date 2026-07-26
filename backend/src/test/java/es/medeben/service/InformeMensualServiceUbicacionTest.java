package es.medeben.service;

import es.medeben.domain.fichaje.Apunte;
import es.medeben.domain.fichaje.EstadoDia;
import es.medeben.domain.fichaje.OrigenApunte;
import es.medeben.domain.fichaje.TipoApunte;
import es.medeben.domain.fichaje.UbicacionApunte;
import es.medeben.domain.fichaje.VeredictoUbicacion;
import es.medeben.domain.usuario.Usuario;
import es.medeben.repository.UbicacionApunteRepository;
import es.medeben.repository.UsuarioRepository;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integración de "Anotar dónde fichas" en el PDF mensual (contrato §Backend,
 * punto 10): parámetro opcional {@code ubicacion}, por defecto apagado y sin
 * ningún efecto sobre el informe de quien no usa la feature.
 */
class InformeMensualServiceUbicacionTest {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-07-10T10:00:00Z"), MADRID);
    private static final YearMonth MES = YearMonth.of(2026, 7);
    private static final LocalDate DIA = LocalDate.of(2026, 7, 8);

    /** Coordenadas cualquiera: solo deben aparecer si el PDF tiene un bug, nunca en el texto. */
    private static final BigDecimal LAT = new BigDecimal("40.41675");
    private static final BigDecimal LON = new BigDecimal("-3.70379");

    private ResumenMensualService resumenes;
    private FichajeService fichajes;
    private UsuarioRepository usuarios;
    private UbicacionApunteRepository ubicaciones;
    private InformeMensualService servicio;

    private Apunte entradaDentro;
    private Apunte salidaFuera;
    private Apunte entradaNoConcluyente;
    private Apunte salidaSuprimida;

    @BeforeEach
    void arranque() {
        resumenes = mock(ResumenMensualService.class);
        fichajes = mock(FichajeService.class);
        usuarios = mock(UsuarioRepository.class);
        ubicaciones = mock(UbicacionApunteRepository.class);
        servicio = new InformeMensualService(resumenes, fichajes, usuarios, ubicaciones, RELOJ);

        Usuario usuario = mock(Usuario.class);
        when(usuario.getEmail()).thenReturn("ana@example.com");
        when(usuarios.findById(USUARIO)).thenReturn(Optional.of(usuario));
        when(resumenes.delMes(USUARIO, MES)).thenReturn(resumenEjemplo());

        entradaDentro = new Apunte(USUARIO, DIA, TipoApunte.ENTRADA, "10:00", null,
                OrigenApunte.CONFIRMADO, OffsetDateTime.parse("2026-07-08T10:01:00+02:00"));
        salidaFuera = new Apunte(USUARIO, DIA, TipoApunte.SALIDA, "18:00", null,
                OrigenApunte.CONFIRMADO, OffsetDateTime.parse("2026-07-08T18:00:30+02:00"));
        entradaNoConcluyente = new Apunte(USUARIO, DIA.plusDays(1), TipoApunte.ENTRADA, "09:00", null,
                OrigenApunte.CONFIRMADO, OffsetDateTime.parse("2026-07-09T09:00:20+02:00"));
        salidaSuprimida = new Apunte(USUARIO, DIA.plusDays(1), TipoApunte.SALIDA, "17:00", null,
                OrigenApunte.CONFIRMADO, OffsetDateTime.parse("2026-07-09T17:00:10+02:00"));

        EstadoDia dia8 = new EstadoDia(DIA, EstadoDia.Estado.COMPLETO, false, DIA.plusDays(15),
                480, List.of(new EstadoDia.TramoDia("10:00", "18:00")), null,
                List.of(entradaDentro, salidaFuera));
        EstadoDia dia9 = new EstadoDia(DIA.plusDays(1), EstadoDia.Estado.COMPLETO, false,
                DIA.plusDays(16), 480, List.of(new EstadoDia.TramoDia("09:00", "17:00")), null,
                List.of(entradaNoConcluyente, salidaSuprimida));
        when(fichajes.estadosDelPeriodo(eq(USUARIO), any(), any()))
                .thenReturn(Map.of(DIA, dia8, DIA.plusDays(1), dia9));
    }

    private static ResumenMensual resumenEjemplo() {
        ValorHoraCalculado desglose = new ValorHoraCalculado(new BigDecimal("10.8979"),
                new BigDecimal("1250.91"), new BigDecimal("14"), BigDecimal.ZERO,
                new BigDecimal("1800"), false, List.of());
        ImporteEstimadoMensual importe = new ImporteEstimadoMensual(new BigDecimal("3.00"),
                new BigDecimal("10.90"), new BigDecimal("32.70"), new BigDecimal("1250.91"), false,
                desglose, List.of(), false);
        TopeAnualResumen tope = new TopeAnualResumen(80, new BigDecimal("3.00"), List.of());
        return new ResumenMensual(MES, 960, 1140, 180, 0, 0,
                Map.of(EstadoDia.Estado.COMPLETO, 2), importe, tope, List.of(),
                "Convenio Colectivo de Hostelería de Ejemplo", "BOP Ejemplo");
    }

    private UbicacionApunte ubicacion(Apunte apunte, VeredictoUbicacion veredicto, Integer distancia,
                                      int precision) {
        UbicacionApunte u = new UbicacionApunte(apunte.getId(), USUARIO, apunte.getFecha(), LAT, LON,
                precision, UUID.randomUUID(), LAT, LON, 150, distancia == null ? 0 : distancia,
                veredicto == VeredictoUbicacion.SUPRIMIDA ? VeredictoUbicacion.DENTRO : veredicto,
                apunte.getRegistradoEn());
        if (veredicto == VeredictoUbicacion.SUPRIMIDA) {
            u.suprime();
        }
        return u;
    }

    private void conCuatroUbicaciones() {
        when(ubicaciones.findByUsuarioIdAndFechaBetween(eq(USUARIO), any(), any())).thenReturn(List.of(
                ubicacion(entradaDentro, VeredictoUbicacion.DENTRO, 20, 80),
                ubicacion(salidaFuera, VeredictoUbicacion.FUERA, 3400, 60),
                ubicacion(entradaNoConcluyente, VeredictoUbicacion.NO_CONCLUYENTE, 140, 340),
                ubicacion(salidaSuprimida, VeredictoUbicacion.SUPRIMIDA, null, 50)));
    }

    private String textoCon(boolean incluyeUbicacion) throws Exception {
        byte[] pdf = servicio.genera(USUARIO, MES, incluyeUbicacion);
        assertThat(pdf).isNotEmpty();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    @DisplayName("sin el parámetro, el PDF es el de siempre aunque haya filas en base — no-regresión")
    void sinElParametroDeUbicacionElPdfEsElDeSiempreAunqueHayaFilasEnBase() throws Exception {
        conCuatroUbicaciones();

        String texto = textoCon(false);

        assertThat(texto).doesNotContain("ubicación:");
        verify(ubicaciones, never()).findByUsuarioIdAndFechaBetween(any(), any(), any());
    }

    @Test
    @DisplayName("el overload de 2 argumentos equivale a ubicación desactivada")
    void elOverloadDeDosArgumentosEquivaleAUbicacionDesactivada() throws Exception {
        conCuatroUbicaciones();

        byte[] pdf = servicio.genera(USUARIO, MES);
        String texto;
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            texto = new PDFTextStripper().getText(doc);
        }

        assertThat(texto).doesNotContain("ubicación:");
    }

    @Test
    @DisplayName("con el parámetro activado aparece la línea en los tres veredictos, y la supresión se rotula aparte")
    void conElParametroActivadoApareceLaLineaEnLosTresVeredictos() throws Exception {
        conCuatroUbicaciones();

        String texto = textoCon(true);
        String plano = texto.replaceAll("\\s+", " ");

        assertThat(plano).contains("ubicación: en el centro (±80 m)");
        assertThat(plano).contains("ubicación: en otra ubicación (±60 m)");
        assertThat(plano).contains("ubicación: no concluyente (±340 m)");
        assertThat(plano).contains("ubicación retirada por el titular");
    }

    @Test
    @DisplayName("ningún FUERA lleva distancia numérica, ni con la opción activada")
    void ningunFueraLlevaDistanciaNumerica() throws Exception {
        conCuatroUbicaciones();

        String texto = textoCon(true);

        assertThat(texto).doesNotContain("3400").doesNotContain("3,4 km").doesNotContain("3.4 km");
    }

    @Test
    @DisplayName("un apunte sin ubicación sale exactamente igual que hoy, aunque la feature esté activada")
    void unApunteSinUbicacionSaleExactamenteIgualQueHoy() throws Exception {
        // Sin ubicaciones en base: el mock por defecto devuelve lista vacía.
        when(ubicaciones.findByUsuarioIdAndFechaBetween(eq(USUARIO), any(), any())).thenReturn(List.of());

        String texto = textoCon(true);

        assertThat(texto).doesNotContain("ubicación:");
        assertThat(texto).contains("Entrada 10:00");
    }

    @Test
    @DisplayName("el PDF principal nunca contiene una coordenada, incluso con la opción activada")
    void elPdfNuncaContieneUnaCoordenada() throws Exception {
        conCuatroUbicaciones();

        String texto = textoCon(true);

        // Ni la representación con coma (es-ES) ni con punto de las coordenadas
        // de fixture, en ninguno de los dos ejes.
        assertThat(texto).doesNotContain("40,41675").doesNotContain("40.41675")
                .doesNotContain("-3,70379").doesNotContain("-3.70379");
    }

    @Test
    @DisplayName("un mes entero de ubicaciones se resuelve con una sola query (anti N+1)")
    void unMesEnteroDeUbicacionesSeResuelveConUnaSolaQuery() throws Exception {
        conCuatroUbicaciones();

        textoCon(true);

        verify(ubicaciones, times(1)).findByUsuarioIdAndFechaBetween(eq(USUARIO), any(), any());
    }
}
