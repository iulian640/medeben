package es.medeben.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ninguna coordenada en ningún log, jamás — mismo criterio que el motivo de
 * una ausencia (art. 9 RGPD), aplicado aquí a un dato de localización (art. 4.1).
 */
@DisplayName("DTOs de ubicación — ninguna coordenada sale por toString")
class UbicacionDtoTest {

    private static final BigDecimal LAT = new BigDecimal("40.41675");
    private static final BigDecimal LON = new BigDecimal("-3.70379");

    @Test
    @DisplayName("UbicacionRequest.toString redacta lat/lon pero conserva la precisión")
    void ubicacionRequestRedactaCoordenadas() {
        var peticion = new UbicacionRequest(LAT, LON, 20);

        assertThat(peticion.toString())
                .doesNotContain("40.41675")
                .doesNotContain("-3.70379")
                .doesNotContain("40,41675")
                .contains("precisionMetros=20");
    }

    @Test
    @DisplayName("CentroTrabajoRequest.toString redacta lat/lon pero conserva el alias")
    void centroTrabajoRequestRedactaCoordenadas() {
        var peticion = new CentroTrabajoRequest(LAT, LON, "El bar");

        assertThat(peticion.toString())
                .doesNotContain("40.41675")
                .doesNotContain("-3.70379")
                .contains("El bar");
    }
}
