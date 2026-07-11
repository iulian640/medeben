package es.medeben.service;

import es.medeben.domain.fichaje.Apunte;
import es.medeben.domain.fichaje.EstadoDia;
import es.medeben.domain.fichaje.OrigenApunte;
import es.medeben.domain.fichaje.TipoApunte;
import es.medeben.domain.usuario.Usuario;
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
import static org.mockito.Mockito.when;

class InformeMensualServiceTest {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    /** "Hoy" fijo: 2026-07-10, 12:00 en Madrid. */
    private static final Clock RELOJ = Clock.fixed(
            Instant.parse("2026-07-10T10:00:00Z"), MADRID);
    private static final YearMonth MES = YearMonth.of(2026, 7);

    private ResumenMensualService resumenes;
    private FichajeService fichajes;
    private UsuarioRepository usuarios;
    private InformeMensualService servicio;

    @BeforeEach
    void arranque() {
        resumenes = mock(ResumenMensualService.class);
        fichajes = mock(FichajeService.class);
        usuarios = mock(UsuarioRepository.class);
        servicio = new InformeMensualService(resumenes, fichajes, usuarios, RELOJ);

        Usuario usuario = mock(Usuario.class);
        when(usuario.getEmail()).thenReturn("ana@example.com");
        when(usuarios.findById(USUARIO)).thenReturn(Optional.of(usuario));
        when(resumenes.delMes(USUARIO, MES)).thenReturn(resumenEjemplo());
        when(fichajes.estadosDelPeriodo(eq(USUARIO), any(), any())).thenReturn(diasEjemplo());
    }

    private static ResumenMensual resumenEjemplo() {
        ValorHoraCalculado desglose = new ValorHoraCalculado(new BigDecimal("10.8979"),
                new BigDecimal("1250.91"), new BigDecimal("14"), BigDecimal.ZERO,
                new BigDecimal("1800"), false, List.of());
        ImporteEstimadoMensual importe = new ImporteEstimadoMensual(new BigDecimal("3.00"),
                new BigDecimal("10.90"), new BigDecimal("32.70"), new BigDecimal("1250.91"), false,
                desglose, List.of(new Cita("Salario base mínimo (Art. 20 del convenio)", "https://bocm.es")));
        TopeAnualResumen tope = new TopeAnualResumen(80, new BigDecimal("3.00"),
                List.of(new Cita("Tope de 80 h (art. 35.2 ET)", null)));
        return new ResumenMensual(MES, 960, 1140, 180, 0, 0,
                Map.of(EstadoDia.Estado.COMPLETO, 2),
                importe, tope, List.of("Estás cerca del tope anual de 80 h extraordinarias."));
    }

    private static Map<LocalDate, EstadoDia> diasEjemplo() {
        LocalDate dia = LocalDate.of(2026, 7, 8);
        Apunte entrada = new Apunte(USUARIO, dia, TipoApunte.ENTRADA, "10:00", null,
                OrigenApunte.CONFIRMADO, OffsetDateTime.parse("2026-07-08T10:01:00+02:00"));
        Apunte salida = new Apunte(USUARIO, dia, TipoApunte.SALIDA, "20:00", null,
                OrigenApunte.RECONSTRUIDO, OffsetDateTime.parse("2026-07-09T13:00:00+02:00"));
        EstadoDia estado = new EstadoDia(dia, EstadoDia.Estado.COMPLETO, false, dia.plusDays(15),
                600, List.of(new EstadoDia.TramoDia("10:00", "20:00")), null, List.of(entrada, salida));
        return Map.of(dia, estado);
    }

    private String textoDelInforme() throws Exception {
        byte[] pdf = servicio.genera(USUARIO, MES);
        assertThat(pdf).isNotEmpty();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    @DisplayName("el informe lleva el mes, la cuenta y el importe estimado con su desglose")
    void cabeceraEImporte() throws Exception {
        String texto = textoDelInforme();

        assertThat(texto).contains("Informe de registro de jornada");
        assertThat(texto).contains("Julio de 2026");
        assertThat(texto).contains("ana@example.com");
        assertThat(texto).contains("32,70 €");
        assertThat(texto).contains("3 h extra a 10,90 € la hora");
        // El desglose D35: que se vea la cuenta entera, no solo el resultado,
        // y TODO en notación española (nada de puntos decimales anglosajones).
        assertThat(texto).contains("1.250,91");
        assertThat(texto).contains("14 pagas");
        assertThat(texto).contains("1.800 h");
        assertThat(texto).contains("10,8979");
        assertThat(texto).doesNotContain("10.8979");
    }

    @Test
    @DisplayName("el diario sale con sellos y orígenes: lo que da valor probatorio (D38)")
    void diarioConSellos() throws Exception {
        String texto = textoDelInforme();

        assertThat(texto).contains("Entrada 10:00");
        assertThat(texto).contains("fichado al momento");
        assertThat(texto).contains("08/07/2026 10:01");
        assertThat(texto).contains("Salida 20:00");
        assertThat(texto).contains("reconstruido después");
        assertThat(texto).contains("09/07/2026 13:00");
        // La lectura del día (tramos) además del diario en bruto.
        assertThat(texto).contains("10:00 – 20:00");
    }

    @Test
    @DisplayName("fuentes citadas, avisos y el descargo: informa, no dictamina")
    void fuentesAvisosYDescargo() throws Exception {
        String texto = textoDelInforme();

        assertThat(texto).contains("Salario base mínimo (Art. 20 del convenio)");
        assertThat(texto).contains("https://bocm.es");
        assertThat(texto).contains("art. 35.2 ET");
        assertThat(texto).contains("cerca del tope anual");
        assertThat(texto).contains("rectificación tardía");
        // El PDF parte las líneas donde le cabe: se normaliza el blanco.
        assertThat(texto.replaceAll("\\s+", " ")).contains("información orientativa, no un dictamen");
    }

    @Test
    @DisplayName("un día que no cuadra CONSTA en el informe: etiqueta en el diario, fila en el resumen y sus apuntes en bruto — no un cero mudo (issue #230)")
    void diaQueNoCuadraConstaEnElInforme() throws Exception {
        // El resumen trae el contador y el aviso (los pone ResumenMensualService);
        // el diario trae el día NO_CUADRA sin lectura pero con sus 4 apuntes.
        ResumenMensual base = resumenEjemplo();
        when(resumenes.delMes(USUARIO, MES)).thenReturn(new ResumenMensual(MES,
                base.minutosTeoricos(), base.minutosReales(), base.minutosExtra(),
                base.minutosDeficit(), base.diasSinCalcular(),
                Map.of(EstadoDia.Estado.COMPLETO, 2, EstadoDia.Estado.NO_CUADRA, 1),
                base.importe(), base.tope(),
                List.of("El 09/07 tus apuntes no cuadran entre sí: revisa ese día en la libreta "
                        + "— sus horas no están contadas en el total del mes.")));
        LocalDate dia = LocalDate.of(2026, 7, 9);
        List<Apunte> desordenados = List.of(
                new Apunte(USUARIO, dia, TipoApunte.SALIDA, "14:00", null,
                        OrigenApunte.RECONSTRUIDO, OffsetDateTime.parse("2026-07-09T21:00:00+02:00")),
                new Apunte(USUARIO, dia, TipoApunte.ENTRADA, "10:00", null,
                        OrigenApunte.RECONSTRUIDO, OffsetDateTime.parse("2026-07-09T21:01:00+02:00")),
                new Apunte(USUARIO, dia, TipoApunte.SALIDA, "21:00", null,
                        OrigenApunte.RECONSTRUIDO, OffsetDateTime.parse("2026-07-09T21:02:00+02:00")),
                new Apunte(USUARIO, dia, TipoApunte.ENTRADA, "17:00", null,
                        OrigenApunte.RECONSTRUIDO, OffsetDateTime.parse("2026-07-09T21:03:00+02:00")));
        when(fichajes.estadosDelPeriodo(eq(USUARIO), any(), any())).thenReturn(Map.of(
                dia, new EstadoDia(dia, EstadoDia.Estado.NO_CUADRA, false, dia.plusDays(15),
                        -1, List.of(), null, desordenados)));

        String texto = textoDelInforme();
        // El PDF parte las líneas donde le cabe: se normaliza el blanco.
        String plano = texto.replaceAll("\\s+", " ");

        // La etiqueta del estado en el diario, y el aviso bien alto.
        assertThat(plano).contains("Los apuntes no cuadran: revísalo");
        assertThat(plano).contains("no están contadas en el total del mes");
        // La fila del resumen: el día consta, no desaparece del recuento.
        assertThat(plano).contains("no cuadran (revísalos, no cuentan) 1");
        // Y la prueba en bruto sigue ahí, apunte a apunte con su sello.
        assertThat(texto).contains("Salida 14:00");
        assertThat(texto).contains("Entrada 17:00");
    }

    @Test
    @DisplayName("las citas repetidas no se duplican en las fuentes")
    void fuentesSinDuplicados() throws Exception {
        Cita repetida = new Cita("Tope de 80 h (art. 35.2 ET)", null);
        ResumenMensual base = resumenEjemplo();
        ImporteEstimadoMensual conRepetida = new ImporteEstimadoMensual(
                base.importe().horasExtra(), base.importe().precioHora(), base.importe().importe(),
                base.importe().salarioBaseAplicado(), base.importe().salarioRealUsado(),
                base.importe().desglose(), List.of(repetida));
        when(resumenes.delMes(USUARIO, MES)).thenReturn(new ResumenMensual(MES, 960, 1140, 180, 0, 0,
                Map.of(EstadoDia.Estado.COMPLETO, 2), conRepetida,
                new TopeAnualResumen(80, new BigDecimal("3.00"), List.of(repetida)), List.of()));

        String texto = textoDelInforme();

        // Aparece una sola vez en la sección de fuentes (numerada como 1.).
        assertThat(texto).contains("1. Tope de 80 h");
        assertThat(texto).doesNotContain("2. Tope de 80 h");
    }
}
