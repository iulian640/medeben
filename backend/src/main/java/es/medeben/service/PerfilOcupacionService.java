package es.medeben.service;

import es.medeben.controller.DimensionDesconocidaException;
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
import java.util.TreeSet;

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
                            validaValorDeTabla(convenioId, clave, valor);
                            fijas.put(clave, valor);
                        }
                    });
                }
                return Optional.of(resueltaConAutofijado(convenioId, fijas, mapeo.articulo()));
            }
            OcupacionesCatalog.Condicional condicional = mapeo.condicionalPorPuesto().get(puestoId);
            if (condicional != null) {
                return Optional.of(resuelveCondicional(convenioId, condicional, respuestas, mapeo.articulo()));
            }
            return Optional.empty();
        });
    }

    private OcupacionResuelta resuelveCondicional(String convenioId,
                                                  OcupacionesCatalog.Condicional condicional,
                                                  Map<String, String> respuestas, String articulo) {
        NodoCondicional arbol = condicional.arbol();
        // Una respuesta que no corresponde a ninguna rama ya no se ignora en
        // silencio (#233): 422 con las opciones reales de ESA pregunta. Cubre
        // tanto el valor inventado como la provincia real cuyo puesto está
        // podado en el anexo (Alicante) — para este puesto no es una opción.
        arbol.respuestaNoReconocida(respuestas).ifPresent(pregunta -> {
            throw new DimensionDesconocidaException(
                    "El valor '" + recorta(respuestas.get(pregunta.dimension()))
                            + "' no está entre las opciones de la pregunta '" + pregunta.dimension()
                            + "' para este puesto. Opciones: " + pregunta.valores());
        });
        Optional<String> nivel = arbol.resuelveNivel(respuestas);
        if (nivel.isEmpty()) {
            // Falta responder alguna pregunta del árbol: se pide la siguiente
            // (encadenada), sin dimensiones fijas todavía.
            List<OpcionDimension> pregunta = arbol.siguientePregunta(respuestas)
                    .map(List::of).orElseGet(List::of);
            return new OcupacionResuelta(Map.of(), pregunta, articulo);
        }
        // Valor resuelto por el árbol: es AUTORITATIVO y no se puede sobrescribir
        // con un input del cliente. Solo se promociona a la tabla la DIMENSIÓN
        // RAÍZ del árbol cuando además indexa el salario (la zona en Cataluña o la
        // provincia en colectiva, que fue la pregunta y también es dimensión de la
        // tabla); su valor ya lo validó el árbol al resolver.
        Map<String, String> fijas = new LinkedHashMap<>();
        String dimRaiz = arbol.dimension();
        Set<String> dimsTabla = dimensionesDeTabla(convenioId);
        if (dimRaiz != null && respuestas.containsKey(dimRaiz) && dimsTabla.contains(dimRaiz)) {
            fijas.put(dimRaiz, respuestas.get(dimRaiz));
        }
        // El árbol resuelve su dimensión OBJETIVO: el `nivel` en los árboles por
        // establecimiento/zona, la `categoria` agrupada en los de provincia.
        fijas.put(condicional.dimensionObjetivo(), nivel.get());
        // Además del objetivo, la tabla puede pedir OTRAS dimensiones que no son
        // del árbol (p. ej. la categoría del establecimiento en Cataluña): cuando
        // el usuario ya las ha respondido, se pliegan aquí para no volver a
        // preguntar en bucle. El objetivo del árbol es autoritativo (ya está en
        // `fijas`, así que no se pisa) y las respuestas que no son dimensión de
        // tabla se ignoran.
        respuestas.forEach((clave, valor) -> {
            if (!fijas.containsKey(clave) && dimsTabla.contains(clave)) {
                validaValorDeTabla(convenioId, clave, valor);
                fijas.put(clave, valor);
            }
        });
        return resueltaConAutofijado(convenioId, fijas, articulo);
    }

    /**
     * 422 si el valor no está entre los publicados para esa dimensión en las
     * tablas del convenio (#233): antes se plegaba tal cual, ningún hecho casaba
     * y la respuesta era idéntica a no haber contestado — el desajuste entre lo
     * que ofrece la lista y lo que entiende el motor se volvía invisible. Los
     * valores del catálogo son datos públicos del boletín: se pueden enseñar.
     */
    private void validaValorDeTabla(String convenioId, String dimension, String valor) {
        Set<String> publicados = new TreeSet<>();
        for (Hecho hecho : hechos.deConvenio(convenioId)) {
            if (CONCEPTO_SALARIO_BASE.equals(hecho.concepto())) {
                String v = hecho.dimensiones().get(dimension);
                if (v != null) {
                    publicados.add(v);
                }
            }
        }
        if (!publicados.contains(valor)) {
            throw new DimensionDesconocidaException(
                    "El valor '" + recorta(valor) + "' no existe para la dimensión '" + dimension
                            + "' del convenio '" + convenioId + "'. Valores publicados: " + publicados);
        }
    }

    /** Tope de longitud del valor que se refleja en el mensaje de error (misma disciplina que el validador del perfil). */
    private static final int ECO_VALOR_MAX = 60;

    private static String recorta(String valor) {
        if (valor == null) {
            return "null";
        }
        return valor.length() <= ECO_VALOR_MAX ? valor : valor.substring(0, ECO_VALOR_MAX) + "…";
    }

    /**
     * Ocupación resuelta fijando de una vez las dimensiones con un ÚNICO valor
     * posible: preguntar algo que solo tiene una respuesta es ruido. Se pliegan
     * en la ocupación (para que lleguen al cálculo) y solo quedan como pendientes
     * las que de verdad ofrecen elección. Es idempotente: fijar el único valor no
     * cambia el conjunto de hechos que casan, así que basta con repetir hasta que
     * no queden dimensiones de un solo valor.
     *
     * <p>De las preguntas que quedan solo se ofrece la PRIMERA (#233): los
     * valores de cada pregunta salen de los hechos compatibles con lo ya fijado,
     * así que cada respuesta puede cambiar los valores (y hasta la existencia)
     * de las siguientes. Ofrecerlas todas a la vez invita a combinaciones que
     * ninguna tabla publica; el cliente re-resuelve tras cada respuesta y va
     * recibiendo la siguiente pregunta encadenada (mismo contrato que los
     * árboles condicionales).
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
        List<OpcionDimension> siguiente =
                pendientes.isEmpty() ? List.of() : List.of(pendientes.getFirst());
        return new OcupacionResuelta(dimensiones, siguiente, articulo);
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
     *
     * <p>Solo se preguntan las dimensiones presentes en TODOS los hechos
     * compatibles (#233): cuando el convenio tiene formas de tabla alternativas
     * (Tenerife indexa por grupoEstablecimiento en las clasificaciones 1/3/4 y
     * por establecimiento en la 2), las dimensiones que solo existen en ALGUNA
     * forma son alternativas excluyentes entre sí — preguntarlas a la vez lleva
     * a combinaciones que ninguna tabla publica. Primero se pregunta lo común
     * (la clasificación); al responderse, los hechos compatibles se quedan en
     * una sola forma y la dimensión que corresponda pasa a ser común.
     */
    private List<OpcionDimension> pendientes(String convenioId, Map<String, String> fijas) {
        boolean hayTablas = false;
        List<Hecho> compatibles = new ArrayList<>();
        for (Hecho hecho : hechos.deConvenio(convenioId)) {
            if (!CONCEPTO_SALARIO_BASE.equals(hecho.concepto())) {
                continue;
            }
            hayTablas = true;
            if (contiene(hecho.dimensiones(), fijas)) {
                compatibles.add(hecho);
            }
        }
        if (compatibles.isEmpty()) {
            // Convenio sin capa derivada: no hay tablas contra las que preguntar
            // (modo manual, como en el validador del perfil). Pero si HAY tablas
            // y ninguna casa, el cliente ha plegado valores válidos por separado
            // que juntos no existen: callar aquí era el "sin tabla aplicable"
            // falso del issue #233 — se dice alto y claro.
            if (!hayTablas) {
                return List.of();
            }
            throw new DimensionDesconocidaException(
                    "Esa combinación de respuestas no corresponde a ninguna tabla salarial publicada del convenio '"
                            + convenioId + "'");
        }
        Set<String> comunes = new LinkedHashSet<>(compatibles.getFirst().dimensiones().keySet());
        for (Hecho hecho : compatibles) {
            comunes.retainAll(hecho.dimensiones().keySet());
        }
        comunes.removeAll(fijas.keySet());
        // Orden alfabético deliberado: el orden de iteración de las dimensiones
        // de un hecho no está garantizado (Map.copyOf), y la primera pregunta
        // de la lista es LA que ve el usuario — debe ser la misma en cada arranque.
        List<OpcionDimension> resultado = new ArrayList<>();
        for (String dimension : comunes.stream().sorted().toList()) {
            Set<String> valores = new LinkedHashSet<>();
            for (Hecho hecho : compatibles) {
                valores.add(hecho.dimensiones().get(dimension));
            }
            resultado.add(new OpcionDimension(dimension, valores.stream().sorted().toList()));
        }
        return resultado;
    }

    private static boolean contiene(Map<String, String> dimensiones, Map<String, String> fijas) {
        return fijas.entrySet().stream()
                .allMatch(e -> e.getValue().equals(dimensiones.get(e.getKey())));
    }
}
