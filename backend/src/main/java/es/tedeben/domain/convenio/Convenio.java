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
 *
 * <p>{@code raw} debe tratarse como SOLO LECTURA: la instancia se comparte entre
 * todos los consumidores del catálogo durante toda la vida del proceso.
 */
public record Convenio(
        String id,
        String nombre,
        String codigoRegcon,
        Subsector subsector,
        AmbitoTerritorial ambitoTerritorial,
        Vigencia vigencia,
        JsonNode raw
) {

    /** Jornada anual aplicable hoy; vacío si el convenio la deja pendiente o no la fija. */
    public Optional<BigDecimal> jornadaAnual() {
        return jornadaAnual(Year.now());
    }

    /**
     * Jornada anual aplicable en el año dado. La jornada aparece de varias formas
     * en el corpus: `jornadaAnual.horas` como número, como mapa por año o como
     * "pendiente"/null, y en dos convenios dentro de un bloque `jornada`
     * alternativo. Un mapa por año se resuelve a la última entrada publicada
     * ≤ año pedido (la que aplica por ultraactividad).
     */
    public Optional<BigDecimal> jornadaAnual(Year anio) {
        JsonNode horas = raw.path("jornadaAnual").path("horas");
        if (horas.isMissingNode() || horas.isNull()) {
            JsonNode jornada = raw.path("jornada");
            horas = jornada.path("anualHoras");
            if (horas.isMissingNode()) {
                horas = jornada.path("jornadaAnualHoras");
            }
        }
        if (horas.isNumber()) {
            return Optional.of(horas.decimalValue());
        }
        if (horas.isObject()) {
            return ultimaEntradaPorAnio(horas, anio);
        }
        return Optional.empty();
    }

    /**
     * Construye y valida un convenio desde su JSON. Falla en el arranque si el
     * fichero incumple el mínimo del ESQUEMA: un dato malo es peor que ninguno.
     */
    public static Convenio desdeJson(String nombreFichero, JsonNode json) {
        String stem = nombreFichero.endsWith(".json")
                ? nombreFichero.substring(0, nombreFichero.length() - ".json".length())
                : nombreFichero;

        String id = textoObligatorio(json, "id", nombreFichero);
        if (!id.equals(stem)) {
            throw new IllegalArgumentException(
                    nombreFichero + ": el id '" + id + "' no coincide con el nombre del fichero");
        }
        String nombre = textoObligatorio(json, "nombre", nombreFichero);
        Subsector subsector = Subsector.desdeClave(textoObligatorio(json, "ambitoFuncional", nombreFichero));

        JsonNode ambitoNodo = json.path("ambitoTerritorial");
        if (!ambitoNodo.isObject()) {
            throw new IllegalArgumentException(nombreFichero + ": falta ambitoTerritorial o no es un objeto");
        }
        JsonNode provinciasNodo = ambitoNodo.path("provincias");
        if (!provinciasNodo.isMissingNode() && !provinciasNodo.isArray()) {
            throw new IllegalArgumentException(nombreFichero + ": ambitoTerritorial.provincias no es una lista");
        }
        JsonNode vigenciaNodo = json.path("vigencia");
        String vigenciaDesde = vigenciaNodo.path("desde").asText(null);
        if (!vigenciaNodo.isObject() || vigenciaDesde == null || vigenciaDesde.isBlank()) {
            throw new IllegalArgumentException(nombreFichero + ": falta vigencia.desde");
        }

        return new Convenio(
                id,
                nombre,
                json.path("codigoRegcon").asText(null),
                subsector,
                AmbitoTerritorial.desdeJson(ambitoNodo),
                Vigencia.desdeJson(vigenciaNodo),
                json.deepCopy());
    }

    private static String textoObligatorio(JsonNode json, String campo, String nombreFichero) {
        String valor = json.path(campo).asText(null);
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(nombreFichero + ": falta el campo obligatorio '" + campo + "'");
        }
        return valor;
    }

    private static Optional<BigDecimal> ultimaEntradaPorAnio(JsonNode porAnio, Year anio) {
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
