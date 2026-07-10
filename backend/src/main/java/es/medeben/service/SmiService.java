package es.medeben.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.NavigableMap;
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
    private static final NavigableMap<Integer, BigDecimal> SMI_MENSUAL_14 = new TreeMap<>();

    static {
        SMI_MENSUAL_14.put(2023, new BigDecimal("1080.00")); // RD 99/2023
        SMI_MENSUAL_14.put(2024, new BigDecimal("1134.00")); // RD 145/2024
        SMI_MENSUAL_14.put(2025, new BigDecimal("1184.00")); // RD 87/2025
        SMI_MENSUAL_14.put(2026, new BigDecimal("1221.00")); // RD 2026 (BOE feb-2026)
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

    /** Cita legal del SMI vigente para arrastrarla a las respuestas (D34). */
    public Cita citaSmi(int anio) {
        var entrada = SMI_MENSUAL_14.floorEntry(anio);
        int anioVigente = entrada != null ? entrada.getKey() : SMI_MENSUAL_14.firstKey();
        return new Cita(
                "Salario Mínimo Interprofesional " + anioVigente + ": " + smiMensual(anio).toPlainString()
                        + " €/mes en 14 pagas (" + smiAnual(anio).toPlainString()
                        + " €/año). Ningún convenio puede pagar menos en cómputo anual (art. 27 ET).",
                "https://www.boe.es/biblioteca_juridica/codigos/codigo.php?id=093_Codigo_Laboral_y_de_la_Seguridad_Social");
    }
}
