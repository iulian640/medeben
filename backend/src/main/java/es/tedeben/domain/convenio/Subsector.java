package es.tedeben.domain.convenio;

import java.util.Arrays;

/**
 * Los tres subsectores de hostelería (D19/ADR): el par (territorio, subsector)
 * determina el convenio aplicable a un trabajador.
 */
public enum Subsector {

    HOSTELERIA("hosteleria"),
    HOSPEDAJE("hospedaje"),
    RESTAURACION_COLECTIVA("restauracion-colectiva");

    private final String clave;

    Subsector(String clave) {
        this.clave = clave;
    }

    public String clave() {
        return clave;
    }

    public static Subsector desdeClave(String clave) {
        return Arrays.stream(values())
                .filter(s -> s.clave.equals(clave))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Subsector desconocido: '" + clave + "' (esperado: hosteleria, hospedaje o restauracion-colectiva)"));
    }
}
