package es.tedeben.service;

import java.util.List;
import java.util.Map;

/**
 * Resultado de elegir un puesto: las dimensiones que el puesto ya determina en
 * ese convenio (nivel, grupo...) y las que faltan por preguntar al usuario
 * (tipo de establecimiento...), con sus valores posibles según las tablas.
 */
public record OcupacionResuelta(
        Map<String, String> dimensiones,
        List<OpcionDimension> pendientes,
        String articulo
) {

    public OcupacionResuelta {
        dimensiones = Map.copyOf(dimensiones);
        pendientes = List.copyOf(pendientes);
    }
}
