package es.medeben.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SmiService — el suelo legal del SMI (art. 27 ET, cómputo anual)")
class SmiServiceTest {

    private final SmiService smi = new SmiService();

    @Test
    @DisplayName("SMI mensual y anual por año (14 pagas)")
    void smiPorAnio() {
        assertThat(smi.smiMensual(2025)).isEqualByComparingTo("1184.00");
        assertThat(smi.smiMensual(2026)).isEqualByComparingTo("1221.00");
        assertThat(smi.smiAnual(2026)).isEqualByComparingTo("17094.00"); // 1221 × 14
    }

    @Test
    @DisplayName("un año posterior al último conocido usa el último SMI (nunca inventa uno mayor)")
    void anioFuturoUsaElUltimo() {
        assertThat(smi.smiMensual(2030)).isEqualByComparingTo("1221.00"); // el de 2026
    }

    @Test
    @DisplayName("un año intermedio usa el SMI vigente ese año, no el posterior")
    void anioIntermedio() {
        assertThat(smi.smiMensual(2024)).isEqualByComparingTo("1134.00");
    }

    @Test
    @DisplayName("el caso real de Iulian: ayudante de cocina Pontevedra 1166,15 €/mes NO alcanza el SMI en 2026")
    void casoPontevedraBajoSmi() {
        // 1166,15 × 14 = 16.326,10 €/año < 17.094 €/año (SMI 2026), sin pluses.
        boolean alcanza = smi.alcanzaElSmi(new BigDecimal("1166.15"), new BigDecimal("14"),
                BigDecimal.ZERO, 2026);
        assertThat(alcanza).isFalse();
    }

    @Test
    @DisplayName("personal de limpieza Madrid 1086,31 €/mes tampoco alcanza el SMI")
    void casoMadridBajoSmi() {
        assertThat(smi.alcanzaElSmi(new BigDecimal("1086.31"), new BigDecimal("14"),
                BigDecimal.ZERO, 2026)).isFalse();
    }

    @Test
    @DisplayName("un salario base holgado SÍ alcanza el SMI, incluso solo con la base")
    void salarioHolgadoAlcanza() {
        assertThat(smi.alcanzaElSmi(new BigDecimal("1400.00"), new BigDecimal("14"),
                BigDecimal.ZERO, 2026)).isTrue();
    }

    @Test
    @DisplayName("los pluses anuales cuentan para el cómputo del SMI: una base justa + pluses puede llegar")
    void plusesCuentanParaElComputo() {
        // 1166,15 × 14 = 16.326,10; + 800 de pluses = 17.126,10 ≥ 17.094 → alcanza.
        assertThat(smi.alcanzaElSmi(new BigDecimal("1166.15"), new BigDecimal("14"),
                new BigDecimal("800"), 2026)).isTrue();
    }

    @Test
    @DisplayName("la cita del SMI lleva el año, el importe y el artículo (D34)")
    void citaConFuente() {
        Cita c = smi.citaSmi(2026);
        assertThat(c.texto()).contains("art. 27 ET");
    }

    @Test
    @DisplayName("la cita del SMI va en notación española, no anglosajona (issue #222)")
    void citaEnNotacionEspanola() {
        Cita c = smi.citaSmi(2026);
        assertThat(c.texto()).contains("1.221,00 €/mes", "17.094,00 €/año");
        assertThat(c.texto()).doesNotContain("1221.00", "17094.00");
    }
}
