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
                return Optional.of(new OcupacionResuelta(
                        directas, pendientes(convenioId, directas), mapeo.articulo()));
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
        // Nivel resuelto: dimensiones = {nivel} + las respuestas que además sean
        // dimensiones reales de la tabla (p. ej. la zona en Cataluña, que a la
        // vez fue la pregunta y es dimensión del salario). Las respuestas
        // "auxiliares" (tipo/categoría de establecimiento) solo servían para
        // llegar al nivel y no van a la tabla.
        Set<String> dimsTabla = dimensionesDeTabla(convenioId);
        Map<String, String> fijas = new LinkedHashMap<>();
        fijas.put("nivel", nivel.get());
        respuestas.forEach((clave, valor) -> {
            if (dimsTabla.contains(clave)) {
                fijas.put(clave, valor);
            }
        });
        return new OcupacionResuelta(fijas, pendientes(convenioId, fijas), articulo);
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
