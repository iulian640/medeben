package es.medeben.service;

import es.medeben.controller.DimensionDesconocidaException;
import es.medeben.domain.convenio.Hecho;
import es.medeben.repository.HechosCatalog;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Valida que las dimensiones del perfil EXISTEN en las tablas del convenio
 * resuelto (deuda pedida por los reviewers). Se apoya en la MISMA capa derivada
 * que usa {@link TablaSalarialService} para resolver dimensiones: si una clave o
 * un valor no aparece en ningún hecho de salarioBase del convenio, guardarlo
 * dejaría un perfil que luego no resolvería ninguna tabla — se rechaza con 422.
 *
 * <p>Además, {@link TablaSalarialService#salarioBaseMinimo} exige IGUALDAD
 * EXACTA del mapa de dimensiones ({@code h.dimensiones().equals(dimensiones)}):
 * un subconjunto de claves (p.ej. solo {@code nivel}) o una combinación de
 * valores válidos por separado pero no publicada juntos pasaría la comprobación
 * clave a clave y aun así jamás resolvería tabla — el resumen mensual quedaría
 * roto (422 permanente) sin avisar al guardar. Por eso, tras validar clave y
 * valor, se exige que la terna COMPLETA case con al menos un hecho real.
 *
 * <p>Convenio sin capa derivada (modo manual, no está entre los normalizados):
 * no hay catálogo contra el que validar, así que NO se bloquea nada — la app
 * cae al modo manual, igual que en {@link TablaSalarialService}.
 */
@Component
public class DimensionesCatalogoValidator {

    private static final String CONCEPTO_SALARIO_BASE = "salarioBase";

    /** Tope de longitud del valor que se refleja en el mensaje de error (los hay de cientos de caracteres). */
    private static final int ECO_VALOR_MAX = 60;

    private final HechosCatalog hechos;

    public DimensionesCatalogoValidator(HechosCatalog hechos) {
        this.hechos = hechos;
    }

    /**
     * @throws DimensionDesconocidaException (422) si alguna clave no es una
     *         dimensión del convenio, si su valor no está entre los publicados
     *         para esa dimensión, o si la combinación completa de dimensiones no
     *         coincide con ningún hecho de salarioBase (subconjunto insuficiente
     *         o combinación no publicada): en esos casos el perfil se guardaría
     *         "válido" pero el resumen mensual nunca resolvería la tabla.
     */
    public void valida(String convenioId, Map<String, String> dimensiones) {
        if (dimensiones == null || dimensiones.isEmpty()) {
            return;
        }
        List<Hecho> salarios = salariosBase(convenioId);
        if (salarios.isEmpty()) {
            // Convenio sin derivar: modo manual, no hay tablas contra las que validar.
            return;
        }
        Map<String, Set<String>> conocidas = valoresPorDimension(salarios);
        for (Map.Entry<String, String> d : dimensiones.entrySet()) {
            Set<String> valores = conocidas.get(d.getKey());
            if (valores == null) {
                throw new DimensionDesconocidaException(
                        "La dimensión '" + d.getKey() + "' no existe para el convenio '" + convenioId
                                + "'. Dimensiones válidas: " + conocidas.keySet());
            }
            if (!valores.contains(d.getValue())) {
                throw new DimensionDesconocidaException(
                        "El valor '" + recorta(d.getValue()) + "' no existe para la dimensión '"
                                + d.getKey() + "' del convenio '" + convenioId + "'");
            }
        }
        // Clave y valor existen, pero la TABLA se resuelve por igualdad exacta del
        // mapa completo: si esta terna no casa con ningún hecho, el perfil sería
        // inservible para el resumen. Se rechaza aquí, no al pedir el resumen.
        boolean resuelveTabla = salarios.stream().anyMatch(h -> h.dimensiones().equals(dimensiones));
        if (!resuelveTabla) {
            throw new DimensionDesconocidaException(mensajeCombinacion(convenioId, dimensiones, salarios));
        }
    }

    /**
     * Mensaje útil cuando la terna no resuelve tabla: si el perfil es un
     * subconjunto de algún hecho (le faltan claves), se listan las claves que
     * faltan; si no encaja con ninguno (combinación no publicada), se dice tal
     * cual. Sin inventar nada: solo se nombran claves reales del convenio.
     */
    private static String mensajeCombinacion(String convenioId, Map<String, String> dimensiones,
                                             List<Hecho> salarios) {
        Set<String> faltan = new LinkedHashSet<>();
        for (Hecho h : salarios) {
            if (esSubconjunto(dimensiones, h.dimensiones())) {
                h.dimensiones().keySet().stream()
                        .filter(clave -> !dimensiones.containsKey(clave))
                        .forEach(faltan::add);
            }
        }
        if (!faltan.isEmpty()) {
            return "Te faltan dimensiones para localizar tu tabla salarial en el convenio '"
                    + convenioId + "': " + faltan + ". Sin ellas no puedo calcular tu resumen.";
        }
        return "Esa combinación de dimensiones no corresponde a ninguna tabla salarial publicada del convenio '"
                + convenioId + "'";
    }

    /** true si cada par clave→valor del perfil aparece igual en las dimensiones del hecho. */
    private static boolean esSubconjunto(Map<String, String> perfil, Map<String, String> hecho) {
        return perfil.entrySet().stream()
                .allMatch(e -> e.getValue() != null && e.getValue().equals(hecho.get(e.getKey())));
    }

    /** Hechos de salarioBase del convenio (la capa contra la que se valida). */
    private List<Hecho> salariosBase(String convenioId) {
        return hechos.deConvenio(convenioId).stream()
                .filter(h -> CONCEPTO_SALARIO_BASE.equals(h.concepto()))
                .toList();
    }

    /** Claves de dimensión → conjunto de valores publicados, sobre los hechos de salarioBase del convenio. */
    private static Map<String, Set<String>> valoresPorDimension(List<Hecho> salarios) {
        Map<String, Set<String>> conocidas = new LinkedHashMap<>();
        for (Hecho h : salarios) {
            h.dimensiones().forEach((clave, valor) ->
                    conocidas.computeIfAbsent(clave, k -> new LinkedHashSet<>()).add(valor));
        }
        return conocidas;
    }

    private static String recorta(String valor) {
        if (valor == null) {
            return "null";
        }
        return valor.length() <= ECO_VALOR_MAX ? valor : valor.substring(0, ECO_VALOR_MAX) + "…";
    }
}
