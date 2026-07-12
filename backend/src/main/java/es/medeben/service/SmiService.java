package es.medeben.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Salario Mínimo Interprofesional (SMI): el suelo legal ABSOLUTO por debajo del
 * cual ningún convenio puede pagar (art. 27 ET, en cómputo ANUAL). Muchas
 * tablas de convenio de categorías bajas quedan por debajo del SMI cuando el
 * convenio está vencido (ultraactividad) y el SMI sigue subiendo cada año; en
 * ese caso lo que de verdad se cobra es el SMI, no la tabla. La app debe
 * AVISARLO — mostrar la tabla sin más sería un dato engañoso.
 *
 * <p>El SMI se fija en 14 pagas por Real Decreto cada año (normalmente en
 * febrero). Para actualizarlo, añade la nueva entrada aquí (y su cita en
 * {@link #citaSmi}). Un año sin entrada usa el ÚLTIMO SMI conocido que sea
 * anterior o igual (nunca uno posterior; nunca se inventa).
 */
@Service
public class SmiService {

    /** SMI mensual (€/mes en 14 pagas) por año de entrada en vigor. Fuente: BOE. */
    private static final NavigableMap<Integer, BigDecimal> SMI_MENSUAL_14;

    static {
        NavigableMap<Integer, BigDecimal> tabla = new TreeMap<>();
        tabla.put(2023, new BigDecimal("1080.00")); // RD 99/2023
        tabla.put(2024, new BigDecimal("1134.00")); // RD 145/2024
        tabla.put(2025, new BigDecimal("1184.00")); // RD 87/2025
        tabla.put(2026, new BigDecimal("1221.00")); // RD 2026: 1.221 €/mes, 17.094 €/año (BOE feb-2026)
        // Inmutable: es el suelo legal, no debe poder alterarse en runtime.
        SMI_MENSUAL_14 = Collections.unmodifiableNavigableMap(tabla);
    }

    private static final int PAGAS_SMI = 14;

    /** SMI mensual (€/mes, 14 pagas) vigente en el año dado; el último conocido si el año es posterior. */
    public BigDecimal smiMensual(int anio) {
        var entrada = SMI_MENSUAL_14.floorEntry(anio);
        if (entrada == null) {
            // Año anterior al primer SMI que conocemos: usa el más antiguo (los
            // convenios vigentes son recientes; esto es un borde defensivo).
            entrada = SMI_MENSUAL_14.firstEntry();
        }
        return entrada.getValue();
    }

    /** SMI en cómputo ANUAL (€/año) del año dado: mensual × 14 pagas. */
    public BigDecimal smiAnual(int anio) {
        return smiMensual(anio).multiply(BigDecimal.valueOf(PAGAS_SMI));
    }

    /**
     * ¿El salario del convenio alcanza el SMI en cómputo ANUAL? Compara
     * {@code baseMensual × mensualidadesConvenio + plusesAnuales} contra el SMI
     * anual. Devuelve true si llega o lo supera. Solo tiene sentido con importes
     * en EUR/mes.
     */
    public boolean alcanzaElSmi(BigDecimal baseMensual, BigDecimal mensualidades,
                                BigDecimal plusesAnuales, int anio) {
        BigDecimal pluses = plusesAnuales == null ? BigDecimal.ZERO : plusesAnuales;
        BigDecimal anualConvenio = baseMensual.multiply(mensualidades).add(pluses);
        return anualConvenio.compareTo(smiAnual(anio)) >= 0;
    }

    /**
     * Resultado de aplicar el suelo del SMI a un salario base mensual: la base
     * que de verdad debe usarse ({@code baseAplicada}) —la original si ya llega,
     * o el suelo legal si no— y, solo cuando ha habido que elevarla, la cita del
     * SMI para arrastrar la fuente a las respuestas (D34).
     */
    public record SueloSmi(BigDecimal baseAplicada, boolean bajoSmi, Optional<Cita> cita) {
    }

    /**
     * Aplica el suelo del SMI (art. 27 ET, cómputo ANUAL) a un salario base
     * MENSUAL (EUR/mes; en EUR/año pásese {@code mensualidades = 1}). Si
     * {@code base × mensualidades + pluses} no alcanza el SMI anual del año, eleva
     * la base a {@code smiAnual / mensualidades} y adjunta la cita del SMI; si ya
     * llega, devuelve la base intacta y sin cita. El redondeo del suelo es a la
     * BAJA: antes un céntimo de menos que prometer uno que la ley no garantiza.
     *
     * <p>Es la MISMA cuenta que expone {@code /salario-base}: aquí vive una sola
     * vez para que la pantalla de perfil, el resumen mensual y el PDF no se
     * contradigan (una tabla de convenio por debajo del SMI es un dato engañoso).
     * El {@code anio} es el del periodo calculado, NO el de hoy: el SMI aplicable
     * es el vigente ese año.
     */
    public SueloSmi aplicaSuelo(BigDecimal baseMensual, BigDecimal mensualidades,
                                BigDecimal plusesAnuales, int anio) {
        if (alcanzaElSmi(baseMensual, mensualidades, plusesAnuales, anio)) {
            return new SueloSmi(baseMensual, false, Optional.empty());
        }
        BigDecimal minimoLegal = smiAnual(anio).divide(mensualidades, 2, RoundingMode.DOWN);
        return new SueloSmi(minimoLegal, true, Optional.of(citaSmi(anio)));
    }

    /** Cita legal del SMI vigente para arrastrarla a las respuestas (D34). */
    public Cita citaSmi(int anio) {
        var entrada = SMI_MENSUAL_14.floorEntry(anio);
        int anioVigente = entrada != null ? entrada.getKey() : SMI_MENSUAL_14.firstKey();
        // Notación española en el texto de la cita (issue #222): mismo formateador
        // que los PDF, para que pantalla y papel muestren el mismo número.
        return new Cita(
                "Salario Mínimo Interprofesional " + anioVigente + ": " + PdfInforme.dinero(smiMensual(anio))
                        + " €/mes en 14 pagas (" + PdfInforme.dinero(smiAnual(anio))
                        + " €/año). Ningún convenio puede pagar menos en cómputo anual (art. 27 ET).",
                "https://www.boe.es/biblioteca_juridica/codigos/codigo.php?id=093_Codigo_Laboral_y_de_la_Seguridad_Social");
    }
}
