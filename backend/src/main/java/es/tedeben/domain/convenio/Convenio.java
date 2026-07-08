package es.tedeben.domain.convenio;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Year;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Un convenio colectivo transcrito de su boletín oficial. Los campos comunes van
 * tipados; el resto (tablas salariales con dimensiones propias de cada convenio,
 * condiciones, pluses...) queda accesible en {@link #raw()} para el motor de
 * cálculo genérico (D24: se normaliza la búsqueda, no el almacenamiento).
 */
public record Convenio(
        String id,
        String nombre,
        String codigoRegcon,
        Subsector subsector,
        AmbitoTerritorial ambitoTerritorial,
        Vigencia vigencia,
        BigDecimal jornadaAnualHoras,
        JsonNode raw
) {

    /** Jornada anual aplicable hoy; vacío si el convenio la deja pendiente o no la fija. */
    public Optional<BigDecimal> jornadaAnual() {
        return Optional.ofNullable(jornadaAnualHoras);
    }

    /**
     * Construye y valida un convenio desde su JSON. Falla en el arranque si el
     * fichero incumple el mínimo del ESQUEMA: un dato malo es peor que ninguno.
     */
    public static Convenio desdeJson(String nombreFichero, JsonNode raw) {
        String stem = nombreFichero.endsWith(".json")
                ? nombreFichero.substring(0, nombreFichero.length() - ".json".length())
                : nombreFichero;

        String id = textoObligatorio(raw, "id", nombreFichero);
        if (!id.equals(stem)) {
            throw new IllegalArgumentException(
                    nombreFichero + ": el id '" + id + "' no coincide con el nombre del fichero");
        }
        String nombre = textoObligatorio(raw, "nombre", nombreFichero);
        Subsector subsector = Subsector.desdeClave(textoObligatorio(raw, "ambitoFuncional", nombreFichero));

        JsonNode ambitoNodo = raw.path("ambitoTerritorial");
        if (!ambitoNodo.isObject()) {
            throw new IllegalArgumentException(nombreFichero + ": falta ambitoTerritorial o no es un objeto");
        }
        JsonNode vigenciaNodo = raw.path("vigencia");
        if (!vigenciaNodo.isObject() || vigenciaNodo.path("desde").asText(null) == null) {
            throw new IllegalArgumentException(nombreFichero + ": falta vigencia.desde");
        }

        return new Convenio(
                id,
                nombre,
                raw.path("codigoRegcon").asText(null),
                subsector,
                AmbitoTerritorial.desdeJson(ambitoNodo),
                Vigencia.desdeJson(vigenciaNodo),
                extraeJornadaAnual(raw),
                raw);
    }

    private static String textoObligatorio(JsonNode raw, String campo, String nombreFichero) {
        String valor = raw.path(campo).asText(null);
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(nombreFichero + ": falta el campo obligatorio '" + campo + "'");
        }
        return valor;
    }

    /**
     * La jornada anual aparece de varias formas en el corpus: `jornadaAnual.horas`
     * como número, como mapa por año o como "pendiente"/null, y en dos convenios
     * dentro de un bloque `jornada` alternativo. Se resuelve al valor numérico
     * aplicable al año en curso (mapa por año → última entrada publicada ≤ hoy,
     * que es la que aplica por ultraactividad).
     */
    private static BigDecimal extraeJornadaAnual(JsonNode raw) {
        JsonNode horas = raw.path("jornadaAnual").path("horas");
        if (horas.isMissingNode() || horas.isNull()) {
            JsonNode jornada = raw.path("jornada");
            horas = jornada.path("anualHoras");
            if (horas.isMissingNode()) {
                horas = jornada.path("jornadaAnualHoras");
            }
        }
        if (horas.isNumber()) {
            return horas.decimalValue();
        }
        if (horas.isObject()) {
            return ultimaEntradaPorAnio(horas);
        }
        return null;
    }

    private static BigDecimal ultimaEntradaPorAnio(JsonNode porAnio) {
        int anioActual = Year.now().getValue();
        int mejorAnio = Integer.MIN_VALUE;
        BigDecimal mejorValor = null;
        for (Iterator<Map.Entry<String, JsonNode>> it = porAnio.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entrada = it.next();
            if (!entrada.getValue().isNumber()) {
                continue;
            }
            int anio;
            try {
                anio = Integer.parseInt(entrada.getKey());
            } catch (NumberFormatException e) {
                continue;
            }
            if (anio <= anioActual && anio > mejorAnio) {
                mejorAnio = anio;
                mejorValor = entrada.getValue().decimalValue();
            }
        }
        return mejorValor;
    }
}
