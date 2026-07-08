package es.tedeben.service;

import com.fasterxml.jackson.databind.JsonNode;
import es.tedeben.domain.convenio.Convenio;
import es.tedeben.domain.convenio.ValoresPorAnio;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Motor de cálculo genérico sobre los convenios (D24). v1: valor de la hora
 * ordinaria y horas extra — el corazón de "te deben X€" (D12). Cada resultado
 * lleva sus citas de artículo (D34). Regla de oro: si falta un dato del convenio
 * (jornada o pagas pendientes), no se calcula nada — un dato malo es peor que
 * ninguno.
 *
 * <p>El salario base entra como parámetro: el convenio es el mínimo y el
 * trabajador puede cobrar su salario real (D25); la búsqueda del mínimo en las
 * tablas del convenio es una pieza aparte.
 */
@Service
public class CalculoConvenioService {

    /** Tope del art. 35.2 del Estatuto de los Trabajadores si el convenio no fija otro. */
    private static final int TOPE_HORAS_EXTRA_ET = 80;

    private static final int DECIMALES_VALOR_HORA = 4;
    private static final int DECIMALES_IMPORTE = 2;
    private static final BigDecimal MENSUALIDADES_ORDINARIAS = BigDecimal.valueOf(12);
    private static final BigDecimal MINIMO_MENSUALIDADES = BigDecimal.valueOf(12);

    /**
     * Valor de la hora ordinaria: (salario base × mensualidades totales + pluses
     * anuales) / jornada anual. Vacío si el convenio no tiene publicadas jornada
     * o pagas para ese año.
     */
    public Optional<ValorHoraCalculado> valorHoraOrdinaria(
            Convenio convenio, Year anio, BigDecimal salarioBaseMensual, BigDecimal plusesAnuales) {
        if (salarioBaseMensual == null || salarioBaseMensual.signum() <= 0) {
            throw new IllegalArgumentException("El salario base mensual debe ser positivo");
        }
        if (plusesAnuales == null || plusesAnuales.signum() < 0) {
            throw new IllegalArgumentException("Los pluses anuales no pueden ser negativos");
        }

        Optional<BigDecimal> jornada = convenio.jornadaAnual(anio);
        Optional<BigDecimal> mensualidades = mensualidades(convenio.raw().path("pagasExtraordinarias"));
        if (jornada.isEmpty() || mensualidades.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal retribucionAnual = salarioBaseMensual.multiply(mensualidades.get()).add(plusesAnuales);
        BigDecimal valorHora = retribucionAnual.divide(jornada.get(), DECIMALES_VALOR_HORA, RoundingMode.HALF_UP);

        List<String> citas = new ArrayList<>();
        citas.add("Jornada anual de " + jornada.get().stripTrailingZeros().toPlainString()
                + " h (" + articuloJornada(convenio) + " del convenio)");
        citas.add(mensualidades.get().stripTrailingZeros().toPlainString() + " mensualidades al año ("
                + articulo(convenio.raw().path("pagasExtraordinarias")) + " del convenio)");
        return Optional.of(new ValorHoraCalculado(valorHora, citas));
    }

    /**
     * Importe de unas horas extra. Precio aplicado: el fijado por el convenio si
     * existe y es mayor; nunca por debajo del valor de la hora ordinaria
     * (art. 35.1 ET, suelo legal).
     */
    public Optional<HorasExtraCalculadas> importeHorasExtra(
            Convenio convenio, Year anio, BigDecimal salarioBaseMensual, BigDecimal plusesAnuales,
            BigDecimal horas) {
        if (horas == null || horas.signum() < 0) {
            throw new IllegalArgumentException("Las horas extra no pueden ser negativas");
        }

        return valorHoraOrdinaria(convenio, anio, salarioBaseMensual, plusesAnuales).map(valorHora -> {
            List<String> citas = new ArrayList<>(valorHora.citas());
            BigDecimal precio = valorHora.valorHora();
            citas.add("La hora extra no puede pagarse por debajo de la hora ordinaria (art. 35.1 ET)");

            JsonNode horasExtraNodo = convenio.raw().path("horasExtraordinarias");
            Optional<BigDecimal> precioConvenio = ValoresPorAnio.resuelve(horasExtraNodo.path("importe"), anio);
            if (precioConvenio.isPresent() && precioConvenio.get().compareTo(precio) > 0) {
                precio = precioConvenio.get();
                citas.add("Precio de hora extra fijado en " + precio.toPlainString() + " €/h ("
                        + articulo(horasExtraNodo) + " del convenio)");
            }

            BigDecimal importe = precio.multiply(horas).setScale(DECIMALES_IMPORTE, RoundingMode.HALF_UP);
            return new HorasExtraCalculadas(precio, importe, citas);
        });
    }

    /** Tope anual de horas extra: el del convenio si lo fija, si no las 80 h del ET. */
    public TopeHorasExtra topeHorasExtraAnual(Convenio convenio) {
        JsonNode tope = convenio.raw().path("horasExtraordinarias").path("topeHorasExtraAnual");
        if (tope.isInt()) {
            return new TopeHorasExtra(tope.intValue(),
                    List.of("Tope de " + tope.intValue() + " h/año según el convenio ("
                            + articulo(convenio.raw().path("horasExtraordinarias")) + ")"));
        }
        return new TopeHorasExtra(TOPE_HORAS_EXTRA_ET,
                List.of("Tope de " + TOPE_HORAS_EXTRA_ET + " h extraordinarias al año (art. 35.2 ET)"));
    }

    /**
     * Mensualidades totales al año. El corpus lo expresa de dos maneras:
     * `cantidad`/`numero` = pagas EXTRA sobre las 12 ordinarias; `total`/`totalPagas`
     * = mensualidades totales. Un total menor que 12 es un dato sospechoso y se
     * trata como pendiente.
     */
    private static Optional<BigDecimal> mensualidades(JsonNode pagasNodo) {
        for (String campo : new String[]{"cantidad", "numero"}) {
            JsonNode n = pagasNodo.path(campo);
            if (n.isNumber()) {
                return Optional.of(MENSUALIDADES_ORDINARIAS.add(n.decimalValue()));
            }
        }
        for (String campo : new String[]{"total", "totalPagas"}) {
            JsonNode n = pagasNodo.path(campo);
            if (n.isNumber() && n.decimalValue().compareTo(MINIMO_MENSUALIDADES) >= 0) {
                return Optional.of(n.decimalValue());
            }
        }
        return Optional.empty();
    }

    private static String articuloJornada(Convenio convenio) {
        String articulo = convenio.raw().path("jornadaAnual").path("articulo").asText(null);
        if (articulo == null) {
            articulo = convenio.raw().path("jornada").path("articulo").asText("artículo no indicado");
        }
        return articulo;
    }

    private static String articulo(JsonNode nodo) {
        return nodo.path("articulo").asText("artículo no indicado");
    }
}
