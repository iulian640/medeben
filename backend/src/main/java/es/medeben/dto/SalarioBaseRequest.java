package es.medeben.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Map;

/**
 * Endpoint ANÓNIMO (calculo/salario-base es permitAll): cada clave y cada valor
 * del mapa van acotados además del número de entradas — igual que
 * {@link PerfilRequest}. Sin el tope individual, 10 entradas podían colar megas
 * de texto en un endpoint sin autenticar (DoS de memoria/parseo).
 */
public record SalarioBaseRequest(
        @NotBlank @Size(max = 40) String convenioId,
        @NotNull LocalDate fecha,
        @NotEmpty @Size(max = 10) Map<@NotBlank @Size(max = 40) String, @NotBlank @Size(max = 400) String> dimensiones
) {
}
