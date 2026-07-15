package es.medeben.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ReenviaVerificacionRequest(@NotBlank @Email String email) {
}
