package es.tedeben.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * La estimación de "te deben X€" de un mes: las horas extra del mes valoradas
 * con la hora extra del motor de convenio y el salario base aplicado.
 *
 * <p>{@code salarioRealUsado} distingue el origen del salario (D25): el salario
 * real del perfil si está configurado y es MAYOR que el mínimo del convenio, o
 * el mínimo del convenio en caso contrario. {@code citas} reúne las fuentes de
 * cada cifra derivada de convenio/ley (D34): tabla salarial, jornada, pagas y el
 * suelo del art. 35 ET.
 */
public record ImporteEstimadoMensual(
        BigDecimal horasExtra,
        BigDecimal precioHora,
        BigDecimal importe,
        BigDecimal salarioBaseAplicado,
        boolean salarioRealUsado,
        ValorHoraCalculado desglose,
        List<Cita> citas
) {

    public ImporteEstimadoMensual {
        citas = List.copyOf(citas);
    }
}
