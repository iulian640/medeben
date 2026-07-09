package es.medeben.domain.fichaje;

/**
 * Cuándo se hizo el apunte respecto al momento que describe (D38). Es la
 * jerarquía probatoria de la libreta: el juez pregunta "¿cuándo lo apuntó?".
 */
public enum OrigenApunte {
    /** Fichado al momento (o en la madrugada del turno de cierre). Lo que más vale. */
    CONFIRMADO,
    /** Apuntado días después, dentro de la ventana de 14 días. Vale menos, pero es honesto. */
    RECONSTRUIDO,
    /** Tras el sellado, en el registro de rectificaciones. El original nunca se toca. */
    RECTIFICACION_TARDIA
}
