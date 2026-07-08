package es.tedeben.dto;

import es.tedeben.domain.fichaje.EstadoDia;
import es.tedeben.service.Cita;
import es.tedeben.service.ImporteEstimadoMensual;
import es.tedeben.service.ResumenMensual;
import es.tedeben.service.TopeAnualResumen;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contrato JSON de "te deben X€ este mes". Datos personales: la respuesta va sin
 * Cache-Control público (lo pone el controller). Cada cifra derivada de
 * convenio/ley lleva sus citas (D34).
 */
public record ResumenMensualResponse(
        String mes,
        long minutosTeoricos,
        long minutosReales,
        HorasResumen horasExtra,
        HorasResumen deficitInformativo,
        int diasSinCalcular,
        Map<String, Integer> contadoresPorEstado,
        ImporteEstimadoResponse importeEstimado,
        TopeAnualResponse topeAnual,
        List<String> avisos
) {

    private static final DateTimeFormatter YYYY_MM = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int MIN_POR_HORA = 60;

    public record HorasResumen(long minutos, BigDecimal horas) {
        static HorasResumen de(long minutos) {
            return new HorasResumen(minutos, BigDecimal.valueOf(minutos)
                    .divide(BigDecimal.valueOf(MIN_POR_HORA), 2, RoundingMode.HALF_UP));
        }
    }

    public record ImporteEstimadoResponse(
            BigDecimal horasExtra,
            BigDecimal precioHora,
            BigDecimal importe,
            BigDecimal salarioBaseAplicado,
            boolean salarioRealUsado,
            DesgloseValorHora desglose,
            List<Cita> citas
    ) {
        static ImporteEstimadoResponse desde(ImporteEstimadoMensual i) {
            return new ImporteEstimadoResponse(i.horasExtra(), i.precioHora(), i.importe(),
                    i.salarioBaseAplicado(), i.salarioRealUsado(),
                    DesgloseValorHora.desde(i.desglose()), i.citas());
        }
    }

    public record TopeAnualResponse(int horas, BigDecimal acumuladoAnioHoras, List<Cita> citas) {
        static TopeAnualResponse desde(TopeAnualResumen t) {
            return new TopeAnualResponse(t.horasTope(), t.acumuladoAnioHoras(), t.citas());
        }
    }

    public static ResumenMensualResponse desde(ResumenMensual r) {
        Map<String, Integer> contadores = new LinkedHashMap<>();
        for (EstadoDia.Estado estado : EstadoDia.Estado.values()) {
            contadores.put(estado.name(), r.contadoresPorEstado().getOrDefault(estado, 0));
        }
        return new ResumenMensualResponse(
                r.mes().format(YYYY_MM),
                r.minutosTeoricos(),
                r.minutosReales(),
                HorasResumen.de(r.minutosExtra()),
                HorasResumen.de(r.minutosDeficit()),
                r.diasSinCalcular(),
                contadores,
                ImporteEstimadoResponse.desde(r.importe()),
                TopeAnualResponse.desde(r.tope()),
                r.avisos());
    }
}
