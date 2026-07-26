package es.medeben.service;

import es.medeben.repository.UbicacionApunteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Purga programada de retención (contrato §Retención): 15 meses desde el
 * fichaje, salvo reclamación en curso (el filtro vive en la propia query del
 * repositorio, {@code UbicacionApunteRepository.purgaCaducadas}). El test que
 * más importa —que NUNCA toca la tabla de apuntes— es estructural: esta clase
 * no tiene ninguna dependencia sobre ApunteRepository ni sobre entidades de
 * fichaje.
 */
class UbicacionesPurgaTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-07-26T04:00:00Z"), ZoneId.of("Europe/Madrid"));

    private UbicacionApunteRepository ubicaciones;
    private UbicacionesPurga purga;

    @BeforeEach
    void arranque() {
        ubicaciones = mock(UbicacionApunteRepository.class);
        purga = new UbicacionesPurga(ubicaciones, RELOJ);
    }

    @Test
    @DisplayName("purga con el límite de 15 meses desde ahora (reloj inyectado)")
    void purgaConElLimiteDeQuinceMeses() {
        when(ubicaciones.purgaCaducadas(any())).thenReturn(3);

        purga.purga();

        OffsetDateTime esperado = OffsetDateTime.now(RELOJ).minus(Period.ofMonths(15));
        verify(ubicaciones).purgaCaducadas(eq(esperado));
    }

    @Test
    @DisplayName("la retención configurada es de 15 meses")
    void laRetencionEsDeQuinceMeses() {
        assertThat(UbicacionesPurga.RETENCION).isEqualTo(Period.ofMonths(15));
    }
}
