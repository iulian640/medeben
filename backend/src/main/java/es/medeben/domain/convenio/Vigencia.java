package es.medeben.domain.convenio;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Vigencia declarada del convenio. Se conservan los valores crudos porque el
 * corpus contiene formatos no-fecha legítimos ("2023", "pendiente"): un convenio
 * con `hasta` en el pasado NO está caducado — sigue aplicando por ultraactividad
 * con su última tabla publicada (regla del motor, ver ADR).
 */
public record Vigencia(String desde, String hasta, String nota) {

    static Vigencia desdeJson(JsonNode nodo) {
        return new Vigencia(
                nodo.path("desde").asText(null),
                nodo.path("hasta").asText(null),
                nodo.path("nota").asText(null));
    }

    public Optional<LocalDate> desdeFecha() {
        return parseaFecha(desde);
    }

    public Optional<LocalDate> hastaFecha() {
        return parseaFecha(hasta);
    }

    /** true si la vigencia declarada terminó antes de la fecha dada (candidato a ultraactividad). */
    public boolean vencidaA(LocalDate fecha) {
        return hastaFecha().map(h -> h.isBefore(fecha)).orElse(false);
    }

    private static Optional<LocalDate> parseaFecha(String valor) {
        if (valor == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(valor));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
