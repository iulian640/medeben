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
        @NotBlank(message = "no puede faltar")
        @Size(max = 40, message = "no puede pasar de 40 caracteres") @SinNul String convenioId,
        @NotNull(message = "no puede faltar") LocalDate fecha,
        @NotEmpty(message = "no puede faltar") @Size(max = 10, message = "máximo 10 dimensiones")
        Map<@NotBlank(message = "no puede faltar") @Size(max = 40, message = "no puede pasar de 40 caracteres") @SinNul String,
                @NotBlank(message = "no puede faltar") @Size(max = 400, message = "no puede pasar de 400 caracteres") @SinNul String> dimensiones
) {
}
