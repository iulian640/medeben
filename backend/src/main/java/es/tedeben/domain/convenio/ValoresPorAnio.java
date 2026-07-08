package es.tedeben.domain.convenio;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Year;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Resuelve un nodo que puede ser un número directo o un mapa {"2025": 1746, ...}
 * a la última entrada publicada ≤ año pedido — la que aplica por ultraactividad
 * cuando el convenio está vencido. Claves no numéricas (p. ej. "unidad") se
 * ignoran, y los valores no positivos se tratan como dato inválido (vacío):
 * una jornada o un precio de 0 € solo puede ser una errata.
 */
public final class ValoresPorAnio {

    private ValoresPorAnio() {
    }

    public static Optional<BigDecimal> resuelve(JsonNode nodo, Year anio) {
        if (nodo.isNumber()) {
            return Optional.of(nodo.decimalValue()).filter(v -> v.signum() > 0);
        }
        if (nodo.isObject()) {
            return ultimaEntrada(nodo, anio).filter(v -> v.signum() > 0);
        }
        return Optional.empty();
    }

    private static Optional<BigDecimal> ultimaEntrada(JsonNode porAnio, Year anio) {
        int tope = anio.getValue();
        int mejorAnio = Integer.MIN_VALUE;
        BigDecimal mejorValor = null;
        for (Iterator<Map.Entry<String, JsonNode>> it = porAnio.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entrada = it.next();
            if (!entrada.getValue().isNumber()) {
                continue;
            }
            int anioEntrada;
            try {
                anioEntrada = Integer.parseInt(entrada.getKey());
            } catch (NumberFormatException e) {
                continue;
            }
            if (anioEntrada <= tope && anioEntrada > mejorAnio) {
                mejorAnio = anioEntrada;
                mejorValor = entrada.getValue().decimalValue();
            }
        }
        return Optional.ofNullable(mejorValor);
    }
}
