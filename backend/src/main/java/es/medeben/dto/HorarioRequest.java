package es.medeben.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Una semana de horario: exactamente 7 días, de lunes a domingo (D38). */
public record HorarioRequest(
        @NotNull(message = "no puede faltar") @Size(min = 7, max = 7, message = "una semana tiene 7 días")
        List<@Valid @NotNull(message = "no puede faltar") Dia> dias
) {

    /** Un día: libre (sin tramos), seguido (1) o partido (2, máximo). */
    public record Dia(
            @NotNull(message = "no puede faltar") @Size(max = 2, message = "máximo 2 tramos por día")
            List<@Valid @NotNull(message = "no puede faltar") TramoDto> tramos
    ) {
    }

    public record TramoDto(
            @NotNull(message = "no puede faltar")
            @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "hora en formato HH:mm") String entrada,
            @NotNull(message = "no puede faltar")
            @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "hora en formato HH:mm") String salida
    ) {
    }
}
