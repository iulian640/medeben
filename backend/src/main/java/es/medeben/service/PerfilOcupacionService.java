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
        return ocupaciones.mapeo(convenioId)
                .flatMap(mapeo -> Optional.ofNullable(mapeo.dimensionesPorPuesto().get(puestoId))
                        .map(dims -> new OcupacionResuelta(dims, pendientes(convenioId, dims), mapeo.articulo())));
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
