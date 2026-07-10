package es.medeben.dto;

import java.math.BigDecimal;
import es.medeben.service.Cita;

import java.util.List;

/**
 * Salario base mínimo del convenio. Si la tabla queda por debajo del SMI vigente
 * ({@code bajoSmi}), la app DEBE avisar: por ley el suelo real es el SMI en
 * cómputo anual, no la tabla del convenio (que pudo quedar congelada por
 * ultraactividad). {@code smiMensual} es el SMI de referencia (€/mes, 14 pagas).
 *
 * <p>{@code minimoLegal} es ese suelo EXPRESADO EN LA MISMA UNIDAD que
 * {@code importe}: el SMI anual repartido entre las mensualidades de ESTE
 * convenio (sin contar pluses, igual que la comparación de {@code bajoSmi}).
 * Es la cifra que la UI debe enseñar como mínimo cuando {@code bajoSmi} —
 * enseñar en grande la tabla superada sería un dato engañoso. Null cuando la
 * tabla ya alcanza el SMI o la unidad no se compara (EUR/hora).
 *
 * <p>{@code comparativaSmi} cubre el caso contrario, el que PARECE ilegal sin
 * serlo: un mensual por debajo del SMI mensual que en cómputo ANUAL (art. 27
 * ET) sí cumple porque el convenio paga más de 14 pagas (Almería 15,
 * Pontevedra 15…). Quien vea la cifra va a compararla con el SMI de los
 * titulares: la UI debe adelantarse con estos números. Null si no hay nada
 * que aclarar.
 */
public record SalarioBaseResponse(
        BigDecimal importe,
        String unidad,
        boolean bajoSmi,
        BigDecimal smiMensual,
        BigDecimal minimoLegal,
        ComparativaSmi comparativaSmi,
        List<Cita> citas
) {

    /** Los números que explican por qué un mensual "bajo" cumple el SMI anual. */
    public record ComparativaSmi(
            BigDecimal mensualidades,
            BigDecimal anualConvenio,
            BigDecimal smiAnual
    ) {
    }
}
