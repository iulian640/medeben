package es.tedeben.domain.convenio;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Un hecho de la capa derivada normalizada (convenios/normalizado): un dato
 * numérico del convenio con sus dimensiones, vigencia como rango de fechas,
 * cita de artículo (D34) y procedencia (`rutaCruda`, JSON Pointer a la celda
 * de la transcripción de la que sale el importe).
 */
public record Hecho(
        String concepto,
        Map<String, String> dimensiones,
        LocalDate desde,
        LocalDate hasta,
        BigDecimal importe,
        String unidad,
        String articulo,
        String rutaCruda
) {

    public Hecho {
        dimensiones = Map.copyOf(dimensiones);
    }

    /** true si la fecha cae dentro de la vigencia del hecho (ambos extremos incluidos). */
    public boolean vigenteEn(LocalDate fecha) {
        return !fecha.isBefore(desde) && !fecha.isAfter(hasta);
    }

    /** Construye y valida un hecho; falla en el arranque si está incompleto. */
    public static Hecho desdeJson(String origen, JsonNode json) {
        String concepto = textoObligatorio(json, "concepto", origen);
        String rutaCruda = textoObligatorio(json, "rutaCruda", origen);

        JsonNode dimensionesNodo = json.path("dimensiones");
        if (!dimensionesNodo.isObject() || dimensionesNodo.isEmpty()) {
            throw new IllegalArgumentException(origen + ": hecho sin dimensiones (" + rutaCruda + ")");
        }
        Map<String, String> dimensiones = new LinkedHashMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = dimensionesNodo.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            dimensiones.put(e.getKey(), e.getValue().asText());
        }

        JsonNode importeNodo = json.path("importe");
        if (!importeNodo.isNumber()) {
            throw new IllegalArgumentException(origen + ": importe no numérico (" + rutaCruda + ")");
        }

        return new Hecho(
                concepto,
                dimensiones,
                LocalDate.parse(textoObligatorio(json, "desde", origen)),
                LocalDate.parse(textoObligatorio(json, "hasta", origen)),
                importeNodo.decimalValue(),
                json.path("unidad").asText(null),
                textoObligatorio(json, "articulo", origen),
                rutaCruda);
    }

    private static String textoObligatorio(JsonNode json, String campo, String origen) {
        String valor = json.path(campo).asText(null);
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(origen + ": falta el campo obligatorio '" + campo + "' en un hecho");
        }
        return valor;
    }
}
