package es.medeben.service;

import java.time.LocalDate;

/**
 * La semana ya está sellada (pasaron 14 días desde su fin, D38): su horario no
 * se puede editar. Las rectificaciones tardías irán por su registro propio.
 */
public class SemanaSelladaException extends RuntimeException {

    public SemanaSelladaException(LocalDate semanaInicio) {
        super("La semana del " + semanaInicio + " ya está sellada y no se puede editar");
    }
}
