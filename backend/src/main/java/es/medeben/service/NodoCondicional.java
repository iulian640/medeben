package es.medeben.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Árbol de decisión para los puestos cuyo nivel es CONDICIONAL al tipo y la
 * categoría del establecimiento (o a la zona). Unifica las tres formas del
 * corpus bajo una sola estructura:
 *
 * <ul>
 *   <li>1 nivel, hoja objeto — Asturias/Cataluña:
 *       {@code {"hoteles_5o4_estrellas": {"nivel": "I"}, ...}}</li>
 *   <li>2 niveles, hoja string — Jaén:
 *       {@code {"hoteles": {"5*y4*": "1.70", ...}, ...}}</li>
 *   <li>2 niveles, hoja objeto — Pontevedra:
 *       {@code {"tipoA": {"5": {"nivel": "1"}, ...}}}</li>
 * </ul>
 *
 * <p>Un nodo es una PREGUNTA (tiene {@code dimension} y {@code ramas}) o una
 * HOJA (tiene {@code nivel}). Navegar el árbol con las respuestas del usuario
 * lleva a una hoja = el nivel de la tabla salarial. Nunca se inventa un nivel:
 * si una respuesta no existe en las ramas, no se resuelve.
 */
public record NodoCondicional(String dimension, Map<String, NodoCondicional> ramas, String nivel) {

    public NodoCondicional {
        ramas = ramas == null ? Map.of() : Map.copyOf(ramas);
    }

    private boolean esHoja() {
        return nivel != null;
    }

    /**
     * Construye el árbol desde el JSON del condicional. {@code dimensionRaiz} es
     * la dimensión de la primera pregunta ("establecimiento" o "zona"); los
     * niveles interiores preguntan por "categoria".
     */
    /**
     * Construye el árbol, o vacío si el puesto no resuelve a ningún nivel (todas
     * sus celdas son {@code null} = no aplicable). El corpus marca {@code null}
     * a propósito ("dato ausente &gt; dato erróneo, modo manual"): esas ramas se
     * PODAN, no se ofrecen como opciones ni encadenan preguntas vacías.
     */
    public static Optional<NodoCondicional> desde(JsonNode condicional, String dimensionRaiz) {
        return nodo(condicional, dimensionRaiz);
    }

    private static Optional<NodoCondicional> nodo(JsonNode valor, String dimension) {
        // Celda no aplicable: se poda (ni hoja ni rama).
        if (valor.isNull() || valor.isMissingNode()) {
            return Optional.empty();
        }
        // Hoja string (Jaén): el propio valor es el nivel.
        if (valor.isTextual()) {
            return valor.asText().isBlank() ? Optional.empty() : Optional.of(hoja(valor.asText()));
        }
        // Hoja objeto: {"nivel": "X"} (con nivel null = no aplicable, se poda).
        if (valor.isObject() && valor.has("nivel")) {
            JsonNode n = valor.path("nivel");
            return n.isNull() || n.asText().isBlank() ? Optional.empty() : Optional.of(hoja(n.asText()));
        }
        // Rama: objeto cuyas entradas son sub-nodos; se pregunta por 'dimension'
        // y cada hijo interior pregunta por la categoría. Las ramas que se podan
        // (solo llevan a null) no se incluyen; si no queda ninguna, no hay nodo.
        Map<String, NodoCondicional> ramas = new LinkedHashMap<>();
        valor.fields().forEachRemaining(e ->
                nodo(e.getValue(), "categoria").ifPresent(sub -> ramas.put(e.getKey(), sub)));
        return ramas.isEmpty() ? Optional.empty() : Optional.of(new NodoCondicional(dimension, ramas, null));
    }

    private static NodoCondicional hoja(String nivel) {
        return new NodoCondicional(null, Map.of(), nivel);
    }

    /**
     * El nivel resuelto si las respuestas llevan hasta una hoja; vacío si falta
     * alguna respuesta o una respuesta no corresponde a ninguna rama (nunca se
     * inventa: respuesta inválida = sin resolver).
     */
    public Optional<String> resuelveNivel(Map<String, String> respuestas) {
        if (esHoja()) {
            return Optional.of(nivel);
        }
        String elegido = respuestas.get(dimension);
        if (elegido == null) {
            return Optional.empty();
        }
        NodoCondicional siguiente = ramas.get(elegido);
        return siguiente == null ? Optional.empty() : siguiente.resuelveNivel(respuestas);
    }

    /**
     * La primera pregunta sin responder (con sus valores posibles), navegando
     * con las respuestas dadas; vacío si el árbol ya está resuelto hasta una
     * hoja. Modela las preguntas ENCADENADAS: la categoría depende del tipo.
     */
    public Optional<OpcionDimension> siguientePregunta(Map<String, String> respuestas) {
        if (esHoja()) {
            return Optional.empty();
        }
        String elegido = respuestas.get(dimension);
        if (elegido == null || !ramas.containsKey(elegido)) {
            return Optional.of(new OpcionDimension(dimension, ramas.keySet().stream().sorted().toList()));
        }
        return ramas.get(elegido).siguientePregunta(respuestas);
    }
}
