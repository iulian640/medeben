package es.medeben.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * El token viaja en el BODY de un POST, nunca como query param del API: los
 * query params acaban en logs de acceso y en historiales; el body no.
 */
public record VerificaEmailRequest(@NotBlank String token) {
}
