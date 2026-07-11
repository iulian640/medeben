package es.medeben.dto;

import jakarta.validation.constraints.NotBlank;

/** Confirmación del borrado de cuenta: la contraseña actual, nada más. */
public record BorradoCuentaRequest(@NotBlank(message = "no puede faltar") String password) {
}
