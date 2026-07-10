package es.medeben.controller;

/**
 * 422: no se puede estimar el resumen del mes porque falta un dato NECESARIO.
 * El mensaje dice EXACTAMENTE qué falta y el {@link Codigo} dice de quién
 * depende: el frontend guía con él (RFC 7807, propiedad "codigo") en vez de
 * adivinar sobre el texto libre — adivinar mandaba a "completar tu perfil"
 * cuando lo que faltaba era una tabla del convenio que no depende del usuario.
 * Regla de oro: nunca se inventa una cifra.
 */
public class ResumenIncompletoException extends RuntimeException {

    /** De quién depende el dato que falta. Contrato con el frontend: no renombrar. */
    public enum Codigo {
        /** Falta el perfil del usuario: lo arregla él en su cuenta. */
        PERFIL,
        /** Falta el horario del usuario: lo arregla él en el editor. */
        HORARIO,
        /** Al convenio le falta un dato (tabla, unidad, jornada o pagas): NO depende del usuario. */
        DATOS_CONVENIO,
        /** El convenio del perfil no se pudo cargar ahora mismo (transitorio). */
        CONVENIO_NO_DISPONIBLE
    }

    private final Codigo codigo;

    public ResumenIncompletoException(Codigo codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public Codigo codigo() {
        return codigo;
    }
}
