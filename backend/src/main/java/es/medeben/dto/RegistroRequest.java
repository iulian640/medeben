package es.medeben.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistroRequest(
        @NotBlank(message = "no puede faltar") @Email(message = "no tiene formato de email")
        @Size(max = 320, message = "no puede pasar de 320 caracteres") @SinNul String email,
        @NotBlank(message = "no puede faltar")
        @Size(min = 10, max = 72, message = "debe tener entre 10 y 72 caracteres") @SinNul String password
) {
}
