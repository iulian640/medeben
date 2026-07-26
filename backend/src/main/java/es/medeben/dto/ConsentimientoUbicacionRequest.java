package es.medeben.dto;

import jakarta.validation.constraints.NotBlank;

/** Body de {@code POST /api/v1/ubicacion/consentimiento}: qué versión del texto se aceptó. */
public record ConsentimientoUbicacionRequest(
        @NotBlank(message = "no puede faltar") String versionTexto
) {
}
