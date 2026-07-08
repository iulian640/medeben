package es.tedeben.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Map;

public record SalarioBaseRequest(
        @NotBlank String convenioId,
        @NotNull LocalDate fecha,
        @NotEmpty @Size(max = 10) Map<String, String> dimensiones
) {
}
