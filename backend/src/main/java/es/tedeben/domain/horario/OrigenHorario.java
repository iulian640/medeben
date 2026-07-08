package es.tedeben.domain.horario;

/** De dónde sale el horario efectivo de una semana (D38). */
public enum OrigenHorario {
    /** La semana tipo que se repite sola. */
    SEMANA_TIPO,
    /** Una edición hecha para esa semana concreta (te cambiaron el turno). */
    SEMANA_EDITADA
}
