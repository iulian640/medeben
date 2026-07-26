package es.medeben.service;

import es.medeben.domain.fichaje.Apunte;
import es.medeben.domain.fichaje.EstadoDia;
import es.medeben.domain.fichaje.OrigenApunte;
import es.medeben.domain.fichaje.TipoApunte;
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
    // Excepción de regresión admitida por el contrato de "Anotar dónde fichas":
    // el constructor gana la dependencia de ubicaciones (overload sin ubicación
    // delega en false, así que este mock nunca ve una llamada en esta suite).
    private UbicacionApunteRepository ubicaciones;
    private InformeMensualService servicio;

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
        when(fichajes.estadosDelPeriodo(eq(USUARIO), any(), any())).thenReturn(diasEjemplo());
    }

    private static final String CONVENIO_NOMBRE = "Convenio Colectivo de Hostelería de Ejemplo";
    private static final String CONVENIO_BOLETIN = "BOP Ejemplo";

    private static ResumenMensual resumenEjemplo() {
        ValorHoraCalculado desglose = new ValorHoraCalculado(new BigDecimal("10.8979"),
                new BigDecimal("1250.91"), new BigDecimal("14"), BigDecimal.ZERO,
                new BigDecimal("1800"), false, List.of());
        ImporteEstimadoMensual importe = new ImporteEstimadoMensual(new BigDecimal("3.00"),
                new BigDecimal("10.90"), new BigDecimal("32.70"), new BigDecimal("1250.91"), false,
                desglose, List.of(new Cita("Salario base mínimo (Art. 20 del convenio)", "https://bocm.es")), false);
        TopeAnualResumen tope = new TopeAnualResumen(80, new BigDecimal("3.00"),
                List.of(new Cita("Tope de 80 h (art. 35.2 ET)", null)));
        return new ResumenMensual(MES, 960, 1140, 180, 0, 0,
                Map.of(EstadoDia.Estado.COMPLETO, 2),
                importe, tope, List.of("Estás cerca del tope anual de 80 h extraordinarias."),
                CONVENIO_NOMBRE, CONVENIO_BOLETIN);
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
        // Sin suelo SMI (fixture por defecto): la etiqueta de siempre.
        assertThat(texto).contains("el mínimo de tu convenio");
    }

    @Test
    @DisplayName("el pie del informe lleva el disclaimer C5 con el nombre y el boletín REALES del convenio, sin la palabra \"cifrado\"")
    void pieDelInformeConElConvenioReal() throws Exception {
        String texto = textoDelInforme();
        String plano = texto.replaceAll("\\s+", " ");

        assertThat(plano).contains("Cálculo orientativo según las tablas del convenio "
                + CONVENIO_NOMBRE + " (2026, " + CONVENIO_BOLETIN + ")");
        assertThat(plano).contains("Verifica con un profesional o tu sindicato antes de reclamar");
        assertThat(texto).doesNotContain("cifrado");
    }

    @Test
    @DisplayName("sin boletín del convenio (dato ausente en la fuente): el pie muestra solo nombre y año, no inventa nada")
    void pieDelInformeSinBoletinNoInventaNada() throws Exception {
        ResumenMensual base = resumenEjemplo();
        when(resumenes.delMes(USUARIO, MES)).thenReturn(new ResumenMensual(MES,
                base.minutosTeoricos(), base.minutosReales(), base.minutosExtra(),
                base.minutosDeficit(), base.diasSinCalcular(), base.contadoresPorEstado(),
                base.importe(), base.tope(), base.avisos(), CONVENIO_NOMBRE, null));

        String plano = textoDelInforme().replaceAll("\\s+", " ");

        assertThat(plano).contains("Cálculo orientativo según las tablas del convenio "
                + CONVENIO_NOMBRE + " (2026)");
        assertThat(plano).doesNotContain("(2026, null)");
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
                        + "— sus horas no están contadas en el total del mes."),
                base.convenioNombre(), base.convenioBoletin()));
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
    @DisplayName("el tope anual imprime el año real del mes del informe, no siempre \"año en curso\" (auditoría)")
    void topeAnualImprimeElAnioDelMesDelInforme() throws Exception {
        // Un informe de un mes de un año YA CERRADO (2025, con "hoy" fijo en
        // 2026-07-10) no puede decir "año en curso": ese año ya no es el actual.
        YearMonth mesAnioAnterior = YearMonth.of(2025, 3);
        ResumenMensual base = resumenEjemplo();
        ResumenMensual resumenAnioAnterior = new ResumenMensual(mesAnioAnterior,
                base.minutosTeoricos(), base.minutosReales(), base.minutosExtra(),
                base.minutosDeficit(), base.diasSinCalcular(), base.contadoresPorEstado(),
                base.importe(), base.tope(), base.avisos(),
                base.convenioNombre(), base.convenioBoletin());
        when(resumenes.delMes(USUARIO, mesAnioAnterior)).thenReturn(resumenAnioAnterior);
        when(fichajes.estadosDelPeriodo(eq(USUARIO), any(), any())).thenReturn(Map.of());

        byte[] pdf = servicio.genera(USUARIO, mesAnioAnterior);
        String texto;
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            texto = new PDFTextStripper().getText(doc);
        }
        String plano = texto.replaceAll("\\s+", " ");

        assertThat(plano).contains("h de 80 h (2025)");
        assertThat(plano).doesNotContain("año en curso");
    }

    @Test
    @DisplayName("las citas repetidas no se duplican en las fuentes")
    void fuentesSinDuplicados() throws Exception {
        Cita repetida = new Cita("Tope de 80 h (art. 35.2 ET)", null);
        ResumenMensual base = resumenEjemplo();
        ImporteEstimadoMensual conRepetida = new ImporteEstimadoMensual(
                base.importe().horasExtra(), base.importe().precioHora(), base.importe().importe(),
                base.importe().salarioBaseAplicado(), base.importe().salarioRealUsado(),
                base.importe().desglose(), List.of(repetida), base.importe().bajoSmi());
        when(resumenes.delMes(USUARIO, MES)).thenReturn(new ResumenMensual(MES, 960, 1140, 180, 0, 0,
                Map.of(EstadoDia.Estado.COMPLETO, 2), conRepetida,
                new TopeAnualResumen(80, new BigDecimal("3.00"), List.of(repetida)), List.of(),
                base.convenioNombre(), base.convenioBoletin()));

        String texto = textoDelInforme();

        // Aparece una sola vez en la sección de fuentes (numerada como 1.).
        assertThat(texto).contains("1. Tope de 80 h");
        assertThat(texto).doesNotContain("2. Tope de 80 h");
    }
}
