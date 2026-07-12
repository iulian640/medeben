package es.medeben.service;

import es.medeben.controller.ResumenIncompletoException;
import es.medeben.domain.fichaje.EstadoDia;
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
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InformeAnualServiceTest {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    /** "Hoy" fijo: 2026-07-10, 12:00 en Madrid → el año en curso llega hasta julio. */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-07-10T10:00:00Z"), MADRID);

    private ResumenMensualService resumenes;
    private UsuarioRepository usuarios;
    private InformeAnualService servicio;

    @BeforeEach
    void arranque() {
        resumenes = mock(ResumenMensualService.class);
        usuarios = mock(UsuarioRepository.class);
        servicio = new InformeAnualService(resumenes, usuarios, RELOJ);

        Usuario usuario = mock(Usuario.class);
        when(usuario.getEmail()).thenReturn("ana@example.com");
        when(usuarios.findById(USUARIO)).thenReturn(Optional.of(usuario));
        // Enero y febrero sin perfil todavía; de marzo a julio, meses con datos.
        when(resumenes.delMes(eq(USUARIO), any())).thenAnswer(inv -> {
            YearMonth mes = inv.getArgument(1);
            if (mes.getMonthValue() < 3) {
                throw new ResumenIncompletoException(ResumenIncompletoException.Codigo.PERFIL, "Todavía no has creado tu perfil");
            }
            return resumenDe(mes);
        });
    }

    private static ResumenMensual resumenDe(YearMonth mes) {
        ValorHoraCalculado desglose = new ValorHoraCalculado(new BigDecimal("10.9000"),
                new BigDecimal("1250.91"), new BigDecimal("14"), BigDecimal.ZERO,
                new BigDecimal("1800"), false, List.of());
        ImporteEstimadoMensual importe = new ImporteEstimadoMensual(new BigDecimal("3.00"),
                new BigDecimal("10.90"), new BigDecimal("32.70"), new BigDecimal("1250.91"), false,
                desglose, List.of(new Cita("Salario base mínimo (Art. 20 del convenio)", "https://bocm.es")), false);
        // El acumulado del tope crece mes a mes: el último mes con datos manda.
        TopeAnualResumen tope = new TopeAnualResumen(80,
                BigDecimal.valueOf(3L * (mes.getMonthValue() - 2)),
                List.of(new Cita("Tope de 80 h (art. 35.2 ET)", null)));
        return new ResumenMensual(mes, 9600, 9780, 180, 0, 0,
                Map.of(EstadoDia.Estado.COMPLETO, 20), importe, tope, List.of(),
                "Convenio de prueba", "BOP de prueba");
    }

    private String texto(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    @DisplayName("el histórico agrega el año: totales, mes a mes, y los meses sin datos se listan sin cifras")
    void contenidoDelHistorico() throws Exception {
        String texto = texto(servicio.genera(USUARIO, Year.of(2026)));
        // El PDF parte las frases donde le cuadra: las frases largas se
        // comprueban con los espacios normalizados.
        String plano = texto.replaceAll("\\s+", " ");

        assertThat(plano).contains("Histórico anual de registro de jornada");
        assertThat(plano).contains("Año 2026");
        assertThat(plano).contains("ana@example.com");
        assertThat(plano).contains("se genera como mucho una vez al día");
        // Totales: 5 meses con datos (mar-jul) × 32,70 = 163,50 y 5 × 3 h = 15 h.
        assertThat(plano).contains("163,50 €");
        assertThat(plano).contains("15 h extra en total");
        // Mes a mes: julio con importe, enero honesto sin cifras. El motivo va
        // UNA vez bajo la tabla, no repetido en cada fila (QA de Iulian).
        assertThat(plano).contains("Julio de 2026");
        assertThat(plano).contains("Sin datos");
        assertThat(plano).contains("Meses sin datos: Todavía no has creado tu perfil");
        assertThat(plano.split("Todavía no has creado tu perfil", -1)).hasSize(2);
        // El tope según el último mes con datos (julio → 3 × 5 = 15 h).
        assertThat(plano).contains("15 h extra de las 80 h");
        // Fuentes deduplicadas entre meses y el descargo de siempre.
        assertThat(plano).contains("1. Salario base mínimo (Art. 20 del convenio)");
        assertThat(plano).doesNotContain("3. Salario base mínimo");
        assertThat(plano).contains("orientativa, no un dictamen");
        // No pasa del mes en curso: agosto ni aparece.
        assertThat(plano).doesNotContain("Agosto");
    }

    @Test
    @DisplayName("los días que no cuadran del año constan en el histórico, con la remisión al informe mensual (issue #230)")
    void diasQueNoCuadranConstanEnElHistorico() throws Exception {
        // Marzo con 2 días que no cuadran y mayo con 1: el histórico no detalla
        // días, pero avisa del total y remite al informe mensual de cada mes.
        when(resumenes.delMes(eq(USUARIO), any())).thenAnswer(inv -> {
            YearMonth mes = inv.getArgument(1);
            ResumenMensual base = resumenDe(mes);
            int noCuadran = mes.getMonthValue() == 3 ? 2 : mes.getMonthValue() == 5 ? 1 : 0;
            return new ResumenMensual(mes, base.minutosTeoricos(), base.minutosReales(),
                    base.minutosExtra(), base.minutosDeficit(), base.diasSinCalcular(),
                    Map.of(EstadoDia.Estado.COMPLETO, 20, EstadoDia.Estado.NO_CUADRA, noCuadran),
                    base.importe(), base.tope(), base.avisos(),
                    base.convenioNombre(), base.convenioBoletin());
        });

        String plano = texto(servicio.genera(USUARIO, Year.of(2026))).replaceAll("\\s+", " ");

        assertThat(plano).contains("3 día(s) del año no cuadran");
        assertThat(plano).contains("informe mensual");
    }

    @Test
    @DisplayName("la caché sirve el MISMO PDF durante el día: el año solo se recorre una vez")
    void cachePorUsuarioYAnio() {
        byte[] primero = servicio.genera(USUARIO, Year.of(2026));
        byte[] segundo = servicio.genera(USUARIO, Year.of(2026));

        assertThat(segundo).isSameAs(primero);
        // 7 meses del año en curso, recorridos UNA sola vez.
        verify(resumenes, times(7)).delMes(eq(USUARIO), any());
    }

    @Test
    @DisplayName("la caché es por usuario: otro usuario genera el suyo")
    void cachePorUsuario() {
        UUID otro = UUID.randomUUID();
        Usuario usuario = mock(Usuario.class);
        when(usuario.getEmail()).thenReturn("otro@example.com");
        when(usuarios.findById(otro)).thenReturn(Optional.of(usuario));
        when(resumenes.delMes(eq(otro), any())).thenAnswer(inv -> resumenDe(inv.getArgument(1)));

        servicio.genera(USUARIO, Year.of(2026));
        servicio.genera(otro, Year.of(2026));

        verify(resumenes, times(7)).delMes(eq(USUARIO), any());
        verify(resumenes, times(7)).delMes(eq(otro), any());
    }

    @Test
    @DisplayName("un año futuro o prehistórico no se intenta")
    void cotasDeAnio() {
        assertThatThrownBy(() -> servicio.genera(USUARIO, Year.of(2027)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("todavía no ha empezado");
        assertThatThrownBy(() -> servicio.genera(USUARIO, Year.of(2018)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2019");
    }

    @Test
    @DisplayName("con meses fallando por causas DISTINTAS, el código del año es el del PRIMER mes")
    void codigoDelPrimerMesFallido() {
        when(resumenes.delMes(eq(USUARIO), any())).thenAnswer(inv -> {
            YearMonth mes = inv.getArgument(1);
            if (mes.getMonthValue() == 1) {
                throw new ResumenIncompletoException(
                        ResumenIncompletoException.Codigo.PERFIL, "Todavía no has creado tu perfil");
            }
            throw new ResumenIncompletoException(
                    ResumenIncompletoException.Codigo.DATOS_CONVENIO,
                    "Tu convenio no tiene publicada la tabla salarial");
        });

        assertThatThrownBy(() -> servicio.genera(USUARIO, Year.of(2026)))
                .isInstanceOf(ResumenIncompletoException.class)
                .satisfies(e -> assertThat(((ResumenIncompletoException) e).codigo())
                        .isEqualTo(ResumenIncompletoException.Codigo.PERFIL));
    }

    @Test
    @DisplayName("si NINGÚN mes tiene datos, guía honesta (422), no un PDF vacío")
    void sinNingunMes() {
        when(resumenes.delMes(eq(USUARIO), any()))
                .thenThrow(new ResumenIncompletoException(ResumenIncompletoException.Codigo.PERFIL, "Todavía no has creado tu perfil"));

        assertThatThrownBy(() -> servicio.genera(USUARIO, Year.of(2026)))
                .isInstanceOf(ResumenIncompletoException.class)
                .hasMessageContaining("Ningún mes de 2026");
    }
}
