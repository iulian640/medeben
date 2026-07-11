package es.medeben.dto;

import jakarta.validation.constraints.NotBlank;

/** El refresh opaco, para /auth/refresh (rotarlo) o /auth/logout (revocarlo). */
public record RefreshRequest(@NotBlank(message = "no puede faltar") String refreshToken) {
}
