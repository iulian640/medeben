package es.medeben.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "no puede faltar")
        @Size(max = 320, message = "no puede pasar de 320 caracteres") String email,
        @NotBlank(message = "no puede faltar")
        @Size(max = 72, message = "no puede pasar de 72 caracteres") String password
) {
}
