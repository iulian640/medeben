package es.medeben.service;

/**
 * Evento de dominio: hay que mandar (o re-mandar) el correo de verificación.
 * Lleva el token EN CLARO — es su único viaje fuera del cliente: la BD solo
 * guarda el hash. Se publica DENTRO de la transacción del registro y el
 * listener lo consume AFTER_COMMIT: si el registro hace rollback, no se
 * envía nada; si el SMTP falla, el registro ya está a salvo.
 */
public record VerificacionEmailSolicitada(String email, String token) {
}
