package es.medeben.service;

/**
 * Token de verificación desconocido, caducado o ya usado. UNA sola excepción
 * para los tres casos (sin oráculo): el mensaje al cliente nunca dice cuál
 * fue el motivo.
 */
public class VerificacionInvalidaException extends RuntimeException {

    public VerificacionInvalidaException() {
        super("El enlace de verificación no es válido o ha caducado");
    }
}
