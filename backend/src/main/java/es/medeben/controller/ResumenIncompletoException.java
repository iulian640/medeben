package es.medeben.controller;

/**
 * 422: no se puede estimar el resumen del mes porque falta un dato NECESARIO
 * (perfil, horario, tabla salarial del convenio o jornada/pagas publicadas). El
 * mensaje dice EXACTAMENTE qué falta. Regla de oro: nunca se inventa una cifra.
 */
public class ResumenIncompletoException extends RuntimeException {

    public ResumenIncompletoException(String mensaje) {
        super(mensaje);
    }
}
