package es.medeben.dto;

/**
 * Respuesta de {@code GET /me}: estado FRESCO de BD, no claims del JWT. En
 * una tablet compartida el claim envejece 15 minutos; el banner "confirma tu
 * correo" no puede enseñar el estado de otro trabajador ni uno caducado.
 */
public record MeResponse(String email, boolean emailVerificado) {
}
