package es.tedeben.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Map;

public record SalarioBaseRequest(
        @NotBlank String convenioId,
        @NotNull LocalDate fecha,
        @NotEmpty Map<String, String> dimensiones
) {
}
