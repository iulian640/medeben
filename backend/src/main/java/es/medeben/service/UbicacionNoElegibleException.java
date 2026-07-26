package es.medeben.service;

/**
 * 422: el apunte o el estado del usuario no son elegibles para anotar
 * ubicación (origen distinto de CONFIRMADO, ventana de 10 min superada,
 * discrepancia hora-vs-sello, o sin centro de trabajo vigente).
 */
public class UbicacionNoElegibleException extends RuntimeException {

    public UbicacionNoElegibleException(String mensaje) {
        super(mensaje);
    }
}
