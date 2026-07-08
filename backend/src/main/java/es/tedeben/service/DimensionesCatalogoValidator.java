package es.tedeben.service;

import es.tedeben.controller.DimensionDesconocidaException;
import es.tedeben.domain.convenio.Hecho;
import es.tedeben.repository.HechosCatalog;
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
     *         dimensión del convenio, o si su valor no está entre los valores
     *         publicados para esa dimensión.
     */
    public void valida(String convenioId, Map<String, String> dimensiones) {
        if (dimensiones == null || dimensiones.isEmpty()) {
            return;
        }
        Map<String, Set<String>> conocidas = valoresPorDimension(convenioId);
        if (conocidas.isEmpty()) {
            // Convenio sin derivar: modo manual, no hay tablas contra las que validar.
            return;
        }
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
    }

    /** Claves de dimensión → conjunto de valores publicados, sobre los hechos de salarioBase del convenio. */
    private Map<String, Set<String>> valoresPorDimension(String convenioId) {
        List<Hecho> salarios = hechos.deConvenio(convenioId).stream()
                .filter(h -> CONCEPTO_SALARIO_BASE.equals(h.concepto()))
                .toList();
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
