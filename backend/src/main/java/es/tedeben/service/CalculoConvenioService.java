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
import java.util.Objects;
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
 *
 * <p>Pendiente conocido: las pagas de CUANTÍA FIJA o menores que una mensualidad
 * (bonus de octubre de Álava, Santa Marta de Asturias, paga de octubre de
 * Zaragoza, gratificación de octubre de Alicante, Santa Marta de Lugo —7 días—...)
 * no entran aún en el valor hora; esos convenios declaran
 * `mensualidadesEquivalentes` solo con las pagas de mensualidad completa.
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
     * anuales) / divisor de horas. El divisor es la jornada anual salvo que el
     * convenio fije explícitamente otro (`divisorValorHora.horas`, p. ej. las
     * 1.829 h de Tenerife): el divisor explícito es un dato del convenio y tiene
     * prioridad. Vacío si el convenio no tiene publicados divisor o pagas para
     * ese año.
     */
    public Optional<ValorHoraCalculado> valorHoraOrdinaria(
            Convenio convenio, Year anio, BigDecimal salarioBaseMensual, BigDecimal plusesAnuales) {
        Objects.requireNonNull(convenio, "convenio");
        Objects.requireNonNull(anio, "anio");
        if (salarioBaseMensual == null || salarioBaseMensual.signum() <= 0) {
            throw new IllegalArgumentException("El salario base mensual debe ser positivo");
        }
        if (plusesAnuales == null || plusesAnuales.signum() < 0) {
            throw new IllegalArgumentException("Los pluses anuales no pueden ser negativos");
        }

        JsonNode divisorNodo = convenio.raw().path("divisorValorHora");
        Optional<BigDecimal> divisorExplicito = ValoresPorAnio.resuelve(divisorNodo.path("horas"), anio);
        Optional<BigDecimal> divisor = divisorExplicito.or(() -> convenio.jornadaAnual(anio));
        Optional<BigDecimal> mensualidades = mensualidades(nodoPagas(convenio));
        if (divisor.isEmpty() || mensualidades.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal retribucionAnual = salarioBaseMensual.multiply(mensualidades.get()).add(plusesAnuales);
        BigDecimal valorHora = retribucionAnual.divide(divisor.get(), DECIMALES_VALOR_HORA, RoundingMode.HALF_UP);

        List<Cita> citas = new ArrayList<>();
        if (divisorExplicito.isPresent()) {
            citas.add(new Cita("Divisor de valor hora de " + divisor.get().stripTrailingZeros().toPlainString()
                    + " h fijado por el convenio (" + articulo(divisorNodo) + " del convenio)",
                    convenio.fuenteUrl()));
        } else {
            citas.add(new Cita("Jornada anual de " + divisor.get().stripTrailingZeros().toPlainString()
                    + " h (" + articuloJornada(convenio) + " del convenio)", convenio.fuenteUrl()));
        }
        citas.add(new Cita(mensualidades.get().stripTrailingZeros().toPlainString()
                + " mensualidades al año (" + articulo(nodoPagas(convenio)) + " del convenio)",
                convenio.fuenteUrl()));
        return Optional.of(new ValorHoraCalculado(
                valorHora, salarioBaseMensual, mensualidades.get(), plusesAnuales, divisor.get(), citas));
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
            List<Cita> citas = new ArrayList<>(valorHora.citas());
            BigDecimal precio = valorHora.valorHora();
            citas.add(Cita.delEstatuto(
                    "La hora extra no puede pagarse por debajo de la hora ordinaria (art. 35.1 ET)"));

            JsonNode horasExtraNodo = convenio.raw().path("horasExtraordinarias");
            Optional<BigDecimal> precioConvenio = ValoresPorAnio.resuelve(horasExtraNodo.path("importe"), anio);
            if (precioConvenio.isPresent() && precioConvenio.get().compareTo(precio) > 0) {
                precio = precioConvenio.get();
                citas.add(new Cita("Precio de hora extra fijado en " + precio.toPlainString()
                        + " €/h (" + articulo(horasExtraNodo) + " del convenio)", convenio.fuenteUrl()));
            }

            BigDecimal importe = precio.multiply(horas).setScale(DECIMALES_IMPORTE, RoundingMode.HALF_UP);
            return new HorasExtraCalculadas(precio, importe, valorHora, citas);
        });
    }

    /** Tope anual de horas extra: el del convenio si lo fija, si no las 80 h del ET. */
    public TopeHorasExtra topeHorasExtraAnual(Convenio convenio, Year anio) {
        Objects.requireNonNull(convenio, "convenio");
        Objects.requireNonNull(anio, "anio");

        JsonNode horasExtraNodo = convenio.raw().path("horasExtraordinarias");
        Optional<BigDecimal> tope = ValoresPorAnio.resuelve(horasExtraNodo.path("topeHorasExtraAnual"), anio);
        if (tope.isPresent()) {
            int horas = tope.get().intValue();
            return new TopeHorasExtra(horas,
                    List.of(new Cita("Tope de " + horas + " h/año según el convenio ("
                            + articulo(horasExtraNodo) + ")", convenio.fuenteUrl())));
        }
        return new TopeHorasExtra(TOPE_HORAS_EXTRA_ET,
                List.of(Cita.delEstatuto(
                        "Tope de " + TOPE_HORAS_EXTRA_ET + " h extraordinarias al año (art. 35.2 ET)")));
    }

    /** El corpus usa `pagasExtraordinarias` casi siempre; tres convenios usan `pagas`. */
    private static JsonNode nodoPagas(Convenio convenio) {
        JsonNode nodo = convenio.raw().path("pagasExtraordinarias");
        return nodo.isObject() ? nodo : convenio.raw().path("pagas");
    }

    /**
     * Mensualidades totales al año. Prioridad: `mensualidadesEquivalentes`
     * (campo canónico, excluye pagas de cuantía fija) > `cantidad` (pagas EXTRA
     * sobre las 12 ordinarias) > `total`/`totalPagas`/`pagasAnualesTotales`
     * (mensualidades totales, sospechoso si < 12). El campo `numero` NO se lee:
     * significa "extras" en unos ficheros y "total" en otros.
     */
    private static Optional<BigDecimal> mensualidades(JsonNode pagasNodo) {
        JsonNode equivalentes = pagasNodo.path("mensualidadesEquivalentes");
        if (equivalentes.isNumber() && equivalentes.decimalValue().compareTo(MINIMO_MENSUALIDADES) >= 0) {
            return Optional.of(equivalentes.decimalValue());
        }
        JsonNode cantidad = pagasNodo.path("cantidad");
        if (cantidad.isNumber()) {
            return Optional.of(MENSUALIDADES_ORDINARIAS.add(cantidad.decimalValue()));
        }
        for (String campo : new String[]{"total", "totalPagas", "pagasAnualesTotales"}) {
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
