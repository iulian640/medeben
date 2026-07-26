package es.medeben.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.medeben.domain.fichaje.EstadoDia;
import es.medeben.domain.horario.DiaCuadrante;
import es.medeben.domain.horario.OrigenHorario;
import es.medeben.domain.horario.Tramo;
import es.medeben.domain.usuario.Perfil;
import es.medeben.domain.usuario.Usuario;
import es.medeben.repository.ConvenioCatalog;
import es.medeben.repository.HechosCatalog;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * El suelo del SMI en el motor de dinero (bug bloqueante): {@code delMes} tomaba
 * el mínimo de la tabla del convenio SIN comprobar el SMI. Hay tablas vigentes y
 * en ultraactividad por debajo del SMI (art. 27 ET, cómputo anual): el resumen y
 * el PDF imprimían un "te deben X€" infravalorado citando una tabla que es ilegal
 * pagar, mientras el perfil ya decía {@code bajoSmi=true}.
 *
 * <p>Estos casos usan el corpus REAL (catálogo + capa derivada) y solo simulan
 * horario y fichajes: los números salen de los JSON de convenio de verdad.
 */
@DisplayName("ResumenMensualService — suelo del SMI (art. 27 ET): una tabla de convenio bajo el SMI se eleva al SMI del año")
class ResumenMensualServiceSueloSmiTest {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    /** "Hoy" fijo en agosto de 2026: julio (mes bajo prueba) queda entero en el pasado. */
    private static final Clock RELOJ = Clock.fixed(
            LocalDateTime.parse("2026-08-15T12:00").atZone(MADRID).toInstant(), MADRID);
    private static final YearMonth JULIO = YearMonth.of(2026, 7);

    /** SMI 2026 repartido en las 14 pagas de estos convenios: 17.094 / 14 = 1.221,00 €/mes. */
    private static final BigDecimal SUELO_SMI_2026 = new BigDecimal("1221.00");

    /** Semana tipo de 8 h/día para tener horas teóricas contra las que comparar. */
    private static final HorarioEfectivo SEMANA_8H = new HorarioEfectivo(List.of(
            new DiaCuadrante(List.of(new Tramo("09:00", "17:00"))),
            new DiaCuadrante(List.of(new Tramo("09:00", "17:00"))),
            new DiaCuadrante(List.of(new Tramo("09:00", "17:00"))),
            new DiaCuadrante(List.of(new Tramo("09:00", "17:00"))),
            new DiaCuadrante(List.of(new Tramo("09:00", "17:00"))),
            new DiaCuadrante(List.of(new Tramo("09:00", "17:00"))),
            new DiaCuadrante(List.of(new Tramo("09:00", "17:00")))
    ), OrigenHorario.SEMANA_TIPO, OffsetDateTime.parse("2026-01-01T00:00:00+01:00"));

    private PerfilService perfiles;
    private HorarioService horarios;
    private FichajeService fichajes;
    private UsuarioRepository usuarios;
    private ConvenioCatalog convenios;
    private ResumenMensualService servicio;

    private final Map<LocalDate, EstadoDia> diario = new ConcurrentHashMap<>();
    private Function<LocalDate, EstadoDia> porDefecto;

    @BeforeEach
    void arranque() {
        // Servicios REALES contra el corpus del classpath: los números (tabla,
        // jornada, pagas) salen de los JSON de convenio, no de un mock.
        ObjectMapper mapper = new ObjectMapper();
        convenios = new ConvenioCatalog(mapper);
        HechosCatalog hechos = new HechosCatalog(mapper);
        TablaSalarialService tablas = new TablaSalarialService(hechos, convenios);
        CalculoConvenioService calculo = new CalculoConvenioService(hechos);
        SmiService smi = new SmiService();

        perfiles = mock(PerfilService.class);
        horarios = mock(HorarioService.class);
        fichajes = mock(FichajeService.class);
        usuarios = mock(UsuarioRepository.class);

        servicio = new ResumenMensualService(perfiles, horarios, fichajes, tablas, calculo, convenios,
                smi, RELOJ);

        diario.clear();
        porDefecto = fecha -> estado(fecha, EstadoDia.Estado.HUECO, -1);
        // Un martes de julio con 9 h fichadas (teórico 8 h) → 1 h extra que valorar.
        diario.put(LocalDate.of(2026, 7, 7), estado(LocalDate.of(2026, 7, 7), EstadoDia.Estado.COMPLETO, 540));

        when(horarios.horariosEfectivosDelRango(eq(USUARIO), any(), any())).thenAnswer(inv -> {
            LocalDate desde = inv.getArgument(1);
            LocalDate hasta = inv.getArgument(2);
            Map<LocalDate, Optional<HorarioEfectivo>> semanas = new LinkedHashMap<>();
            for (LocalDate lunes = desde; !lunes.isAfter(hasta); lunes = lunes.plusDays(7)) {
                semanas.put(lunes, Optional.of(SEMANA_8H));
            }
            return semanas;
        });
        when(fichajes.estadosDelPeriodo(eq(USUARIO), any(), any())).thenAnswer(inv -> {
            LocalDate desde = inv.getArgument(1);
            LocalDate hasta = inv.getArgument(2);
            Map<LocalDate, EstadoDia> estados = new LinkedHashMap<>();
            for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
                estados.put(d, diario.getOrDefault(d, porDefecto.apply(d)));
            }
            return estados;
        });

        Usuario usuario = mock(Usuario.class);
        when(usuario.getEmail()).thenReturn("iulian@example.com");
        when(usuarios.findById(USUARIO)).thenReturn(Optional.of(usuario));
    }

    private void perfil(String provincia, String convenioId, Map<String, String> dimensiones,
                        BigDecimal salarioReal) {
        Perfil perfil = new Perfil(USUARIO, provincia, "hosteleria", convenioId, "puesto",
                dimensiones, salarioReal, null, OffsetDateTime.parse("2026-01-01T00:00:00+01:00"));
        when(perfiles.busca(USUARIO)).thenReturn(Optional.of(perfil));
    }

    private static EstadoDia estado(LocalDate fecha, EstadoDia.Estado estado, int minutos) {
        return new EstadoDia(fecha, estado, false, fecha.plusDays(15), minutos, List.of(), null, List.of());
    }

    // --- (1) y (2): los dos casos reales de la auditoría ---

    @Test
    @DisplayName("(1) Madrid nivel V-C en ultraactividad (1.086,31 €/mes) → base elevada al SMI del año y cita del SMI")
    void madridNivelVUltraactividadSeElevaAlSmi() {
        // Última tabla publicada: 2025 (1.086,31). En julio de 2026 aplica por
        // ultraactividad y 1.086,31 × 14 = 15.208,34 < 17.094 (SMI 2026 anual).
        perfil("Madrid", "madrid-hosteleria",
                Map.of("tabla", "general", "nivel", "V", "claseEmpresa", "C"), null);

        ResumenMensual r = servicio.delMes(USUARIO, JULIO);

        assertThat(r.importe().salarioBaseAplicado()).isEqualByComparingTo(SUELO_SMI_2026);
        assertThat(r.importe().salarioRealUsado()).isFalse();
        // La tabla real sigue citada (D18: compruébalo) Y se añade la del SMI (D34).
        assertThat(r.importe().citas())
                .anySatisfy(c -> assertThat(c.texto()).contains("1.086,31"))
                .anySatisfy(c -> assertThat(c.texto()).contains("art. 27 ET"));
    }

    @Test
    @DisplayName("(2) Asturias nivel IX con tabla vigente de 2026 (1.179,02 €/mes) → base elevada al SMI del año y cita del SMI")
    void asturiasNivelIX2026SeElevaAlSmi() {
        // Tabla VIGENTE de 2026: 1.179,02 × 14 = 16.506,28 < 17.094 (SMI 2026 anual).
        perfil("Asturias", "asturias-hosteleria", Map.of("nivel", "IX"), null);

        ResumenMensual r = servicio.delMes(USUARIO, JULIO);

        assertThat(r.importe().salarioBaseAplicado()).isEqualByComparingTo(SUELO_SMI_2026);
        assertThat(r.importe().salarioRealUsado()).isFalse();
        assertThat(r.importe().citas())
                .anySatisfy(c -> assertThat(c.texto()).contains("1.179,02"))
                .anySatisfy(c -> assertThat(c.texto()).contains("art. 27 ET"));
    }

    // --- (3) regresión: por ENCIMA del SMI, ni se toca la base ni se cita el SMI ---

    @Test
    @DisplayName("(3) regresión: Madrid nivel III-B (1.250,91 €/mes, por la MISMA vía que los casos bajos) SÍ alcanza el SMI → base intacta y sin cita del SMI")
    void porEncimaDelSmiNoSeToca() {
        // 1.250,91 × 14 = 17.512,74 ≥ 17.094 → el suelo no debe dispararse.
        perfil("Madrid", "madrid-hosteleria",
                Map.of("tabla", "general", "nivel", "III", "claseEmpresa", "B"), null);

        ResumenMensual r = servicio.delMes(USUARIO, JULIO);

        assertThat(r.importe().salarioBaseAplicado()).isEqualByComparingTo("1250.91");
        assertThat(r.importe().salarioRealUsado()).isFalse();
        assertThat(r.importe().citas())
                .noneSatisfy(c -> assertThat(c.texto()).contains("art. 27 ET"));
        // Regresión de la etiqueta del PDF: sin suelo SMI, sigue siendo "el
        // mínimo de tu convenio" — la etiqueta del suelo es SOLO para bajoSmi.
        assertThat(r.importe().bajoSmi()).isFalse();
    }

    // --- (4) el PDF mensual bebe de la MISMA fuente y arrastra la corrección y la cita ---

    @Test
    @DisplayName("(4) el PDF mensual arrastra la base corregida (1.221,00) y la cita del SMI: misma fuente que el resumen")
    void pdfMensualArrastraLaCorreccion() throws Exception {
        perfil("Madrid", "madrid-hosteleria",
                Map.of("tabla", "general", "nivel", "V", "claseEmpresa", "C"), null);

        ResumenMensual r = servicio.delMes(USUARIO, JULIO);
        InformeMensualService informe = new InformeMensualService(servicio, fichajes, usuarios,
                mock(UbicacionApunteRepository.class), RELOJ);
        String texto = textoPdf(informe.genera(USUARIO, JULIO));

        // La cifra corregida del resumen es la que se maqueta en el PDF (D35).
        assertThat(r.importe().salarioBaseAplicado()).isEqualByComparingTo(SUELO_SMI_2026);
        assertThat(texto).contains("1.221,00"); // base corregida en el desglose
        assertThat(texto).doesNotContain("1.086,31 € de salario base"); // nunca la cifra bajo SMI como base
        assertThat(texto).contains("art. 27 ET"); // la cita del SMI, en Fuentes
        // Copy del suelo SMI (retoque tras la PR #251): la cifra elevada NO es
        // "el mínimo de tu convenio" (esa etiqueta es para la tabla real, que
        // aquí queda por debajo) — es el suelo legal del art. 27 ET.
        assertThat(texto).contains("el suelo del SMI (tu tabla está por debajo)");
        assertThat(texto).doesNotContain("el mínimo de tu convenio");
    }

    // --- (5) el histórico anual también bebe de la misma fuente y arrastra la cita ---

    @Test
    @DisplayName("(5) el informe anual arrastra la cita del SMI en sus Fuentes: mismo motor que el resumen y el mensual")
    void informeAnualArrastraLaCita() throws Exception {
        perfil("Madrid", "madrid-hosteleria",
                Map.of("tabla", "general", "nivel", "V", "claseEmpresa", "C"), null);

        InformeAnualService anual = new InformeAnualService(servicio, usuarios, RELOJ);
        String texto = textoPdf(anual.genera(USUARIO, Year.of(2026)));

        assertThat(texto).contains("art. 27 ET");
    }

    private static String textoPdf(byte[] pdf) throws Exception {
        assertThat(pdf).isNotEmpty();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }
}
