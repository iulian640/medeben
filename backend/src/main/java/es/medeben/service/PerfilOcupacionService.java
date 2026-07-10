package es.medeben.service;

import es.medeben.domain.convenio.Hecho;
import es.medeben.repository.HechosCatalog;
import es.medeben.repository.OcupacionesCatalog;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Del puesto en cristiano a las dimensiones de la tabla (D20): el puesto fija
 * unas dimensiones (nivel, grupo...) y las que falten (tipo de establecimiento)
 * se preguntan al usuario con los valores reales que existen en las tablas del
 * convenio. Convenio sin mapeo o puesto no contemplado → vacío (modo manual).
 */
@Service
public class PerfilOcupacionService {

    private static final String CONCEPTO_SALARIO_BASE = "salarioBase";

    private final OcupacionesCatalog ocupaciones;
    private final HechosCatalog hechos;

    public PerfilOcupacionService(OcupacionesCatalog ocupaciones, HechosCatalog hechos) {
        this.ocupaciones = ocupaciones;
        this.hechos = hechos;
    }

    public List<Puesto> puestos() {
        return ocupaciones.puestos();
    }

    public Optional<OcupacionResuelta> resuelve(String convenioId, String puestoId) {
        return resuelve(convenioId, puestoId, Map.of());
    }

    /**
     * Resuelve el puesto teniendo en cuenta las {@code respuestas} que el
     * usuario ya ha dado a las preguntas (tipo/categoría de establecimiento,
     * zona...). Un puesto puede estar mapeado de dos formas:
     *
     * <ul>
     *   <li>DIRECTO: el nivel es fijo; las respuestas no hacen falta.</li>
     *   <li>CONDICIONAL: el nivel sale de un árbol de decisión según el
     *       establecimiento o la zona (ver {@link NodoCondicional}); mientras
     *       falten respuestas se devuelven como preguntas encadenadas, y al
     *       completarse el nivel se une a las dimensiones para la tabla.</li>
     * </ul>
     */
    public Optional<OcupacionResuelta> resuelve(String convenioId, String puestoId,
                                                Map<String, String> respuestas) {
        return ocupaciones.mapeo(convenioId).flatMap(mapeo -> {
            Map<String, String> directas = mapeo.dimensionesPorPuesto().get(puestoId);
            if (directas != null) {
                // Las respuestas del usuario a dimensiones de la tabla (p. ej. la
                // clase de empresa, el grupo de actividad) se pliegan también en los
                // mapeos DIRECTOS, no solo en los condicionales: así, al re-resolver,
                // `dimensiones` queda completo para el cálculo y las preguntas ya
                // respondidas dejan de aparecer.
                Map<String, String> fijas = new LinkedHashMap<>(directas);
                if (!respuestas.isEmpty()) {
                    Set<String> dimsTabla = dimensionesDeTabla(convenioId);
                    respuestas.forEach((clave, valor) -> {
                        // Solo se pliegan dimensiones de tabla que el puesto NO fija
                        // ya: las fijas del puesto son AUTORITATIVAS y una respuesta
                        // del cliente no puede pisarlas (p. ej. no puede cambiar su
                        // `nivel` vía query param y saltar a otra fila salarial).
                        // Misma disciplina que el `nivel` del árbol en el condicional.
                        if (dimsTabla.contains(clave) && !directas.containsKey(clave)) {
                            fijas.put(clave, valor);
                        }
                    });
                }
                return Optional.of(resueltaConAutofijado(convenioId, fijas, mapeo.articulo()));
            }
            NodoCondicional arbol = mapeo.condicionalPorPuesto().get(puestoId);
            if (arbol != null) {
                return Optional.of(resuelveCondicional(convenioId, arbol, respuestas, mapeo.articulo()));
            }
            return Optional.empty();
        });
    }

    private OcupacionResuelta resuelveCondicional(String convenioId, NodoCondicional arbol,
                                                  Map<String, String> respuestas, String articulo) {
        Optional<String> nivel = arbol.resuelveNivel(respuestas);
        if (nivel.isEmpty()) {
            // Falta responder alguna pregunta del árbol: se pide la siguiente
            // (encadenada), sin dimensiones fijas todavía.
            List<OpcionDimension> pregunta = arbol.siguientePregunta(respuestas)
                    .map(List::of).orElseGet(List::of);
            return new OcupacionResuelta(Map.of(), pregunta, articulo);
        }
        // Nivel resuelto por el árbol: es AUTORITATIVO y no se puede sobrescribir
        // con un input del cliente. Solo se promociona a la tabla la DIMENSIÓN
        // RAÍZ del árbol cuando además indexa el salario (la zona en Cataluña,
        // que fue la pregunta y también es dimensión de la tabla); su valor ya
        // lo validó el árbol al resolver el nivel. Ninguna otra respuesta entra
        // aquí (ni 'nivel', ni auxiliares, ni query params sueltos): las demás
        // dimensiones de tabla se piden como pendientes normales.
        Map<String, String> fijas = new LinkedHashMap<>();
        String dimRaiz = arbol.dimension();
        if (dimRaiz != null && respuestas.containsKey(dimRaiz)
                && dimensionesDeTabla(convenioId).contains(dimRaiz)) {
            fijas.put(dimRaiz, respuestas.get(dimRaiz));
        }
        fijas.put("nivel", nivel.get());
        return resueltaConAutofijado(convenioId, fijas, articulo);
    }

    /**
     * Ocupación resuelta fijando de una vez las dimensiones con un ÚNICO valor
     * posible: preguntar algo que solo tiene una respuesta es ruido. Se pliegan
     * en la ocupación (para que lleguen al cálculo) y solo quedan como pendientes
     * las que de verdad ofrecen elección. Es idempotente: fijar el único valor no
     * cambia el conjunto de hechos que casan, así que basta con repetir hasta que
     * no queden dimensiones de un solo valor.
     */
    private OcupacionResuelta resueltaConAutofijado(String convenioId, Map<String, String> fijas,
                                                    String articulo) {
        Map<String, String> dimensiones = new LinkedHashMap<>(fijas);
        List<OpcionDimension> pendientes;
        while (true) {
            pendientes = pendientes(convenioId, dimensiones);
            List<OpcionDimension> unicos = pendientes.stream()
                    .filter(p -> p.valores().size() == 1)
                    .toList();
            if (unicos.isEmpty()) {
                break;
            }
            for (OpcionDimension u : unicos) {
                dimensiones.put(u.dimension(), u.valores().get(0));
            }
        }
        return new OcupacionResuelta(dimensiones, pendientes, articulo);
    }

    /** Todas las dimensiones que aparecen en los hechos de salarioBase del convenio. */
    private Set<String> dimensionesDeTabla(String convenioId) {
        Set<String> dims = new LinkedHashSet<>();
        for (Hecho hecho : hechos.deConvenio(convenioId)) {
            if (CONCEPTO_SALARIO_BASE.equals(hecho.concepto())) {
                dims.addAll(hecho.dimensiones().keySet());
            }
        }
        return dims;
    }

    /**
     * Dimensiones que los hechos de salarioBase tienen y el puesto no fija:
     * lo que hay que preguntarle al usuario, con los valores reales de la tabla.
     */
    private List<OpcionDimension> pendientes(String convenioId, Map<String, String> fijas) {
        Map<String, Set<String>> valoresPorDimension = new LinkedHashMap<>();
        for (Hecho hecho : hechos.deConvenio(convenioId)) {
            if (!CONCEPTO_SALARIO_BASE.equals(hecho.concepto()) || !contiene(hecho.dimensiones(), fijas)) {
                continue;
            }
            hecho.dimensiones().forEach((clave, valor) -> {
                if (!fijas.containsKey(clave)) {
                    valoresPorDimension.computeIfAbsent(clave, k -> new LinkedHashSet<>()).add(valor);
                }
            });
        }
        List<OpcionDimension> resultado = new ArrayList<>();
        valoresPorDimension.forEach((dimension, valores) ->
                resultado.add(new OpcionDimension(dimension, valores.stream().sorted().toList())));
        return resultado;
    }

    private static boolean contiene(Map<String, String> dimensiones, Map<String, String> fijas) {
        return fijas.entrySet().stream()
                .allMatch(e -> e.getValue().equals(dimensiones.get(e.getKey())));
    }
}
