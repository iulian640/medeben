package es.medeben.domain.fichaje;

/** Qué apunta el trabajador en su diario (D38). */
public enum TipoApunte {
    ENTRADA,
    SALIDA,
    /** No fue a trabajar (enfermo, permiso...): queda registrado, con motivo opcional. */
    AUSENCIA
}
