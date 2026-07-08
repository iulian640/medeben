package es.tedeben.service;

import java.time.LocalDate;

/**
 * El día ya está sellado (14 días tras acabar, D38). Fichar sobre él exige
 * confirmar explícitamente la rectificación tardía, que va a registro propio.
 */
public class DiaSelladoException extends RuntimeException {

    public DiaSelladoException(LocalDate fecha) {
        super("El día " + fecha + " ya está sellado; solo cabe una rectificación tardía, que queda registrada aparte");
    }
}
