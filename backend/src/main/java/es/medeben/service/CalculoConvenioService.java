package es.medeben.service;

import com.fasterxml.jackson.databind.JsonNode;
import es.medeben.domain.convenio.Convenio;
import es.medeben.domain.convenio.ValoresPorAnio;
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
                valorHora, salarioBaseMensual, mensualidades.get(), plusesAnuales,
                divisor.get(), divisorExplicito.isPresent(), citas));
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

            // (1) Precio €/hora FIJO del convenio (Teruel, Almería...).
            Optional<BigDecimal> precioConvenio = ValoresPorAnio.resuelve(horasExtraNodo.path("importe"), anio);
            if (precioConvenio.isPresent() && precioConvenio.get().compareTo(precio) > 0) {
                precio = precioConvenio.get();
                citas.add(new Cita("Precio de hora extra fijado en " + precio.toPlainString()
                        + " €/h (" + articulo(horasExtraNodo) + " del convenio)", convenio.fuenteUrl()));
            }

            // (2) RECARGO PORCENTUAL sobre la hora ordinaria: la forma más común
            // del corpus (23 convenios: Cádiz 75%, Granada 100%, Ourense 100%...).
            // precio = valorHora × (1 + %/100). Antes se ignoraba y la hora extra
            // se pagaba igual que la ordinaria — dinero de menos para el trabajador.
            Optional<RecargoExtra> recargo = recargoPorcentual(horasExtraNodo);
            if (recargo.isPresent()) {
                BigDecimal factor = BigDecimal.ONE.add(
                        BigDecimal.valueOf(recargo.get().porcentaje()).movePointLeft(2));
                BigDecimal conRecargo = valorHora.valorHora().multiply(factor);
                if (conRecargo.compareTo(precio) > 0) {
                    precio = conRecargo;
                    citas.add(new Cita(recargo.get().nota() + " (" + articulo(horasExtraNodo)
                            + " del convenio)", convenio.fuenteUrl()));
                }
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

    /** Recargo % de la hora extra: el porcentaje a aplicar y el texto de la cita. */
    private record RecargoExtra(int porcentaje, String nota) {}

    /** Nombres bajo los que el corpus guarda el recargo % plano de la hora extra. */
    private static final String[] CLAVES_RECARGO_PCT =
            {"porcentaje", "recargoPct", "incrementoAbonoPorcentaje"};

    /**
     * Recargo porcentual de la hora extra sobre la ordinaria, si el convenio lo
     * fija así (entero, p. ej. 75 o 100). Vacío si no hay recargo porcentual o si
     * su base no es la hora ordinaria (no se aplica a ciegas sobre otra base).
     */
    private static Optional<RecargoExtra> recargoPorcentual(JsonNode horasExtraNodo) {
        JsonNode sobre = horasExtraNodo.path("sobre");
        // Si el convenio dice explícitamente sobre qué base va, exigimos que sea
        // la hora/salario ordinaria; si no lo dice (p. ej. incrementoAbono), se
        // asume ordinaria (que es la definición del incremento del abono).
        // "ordinari" cubre las tres formas del corpus: "hora_ordinaria",
        // "valor_hora_ordinaria" y "salario_real_ordinario" (masculino).
        if (sobre.isTextual() && !sobre.asText().toLowerCase().contains("ordinari")) {
            return Optional.empty();
        }
        // Caso normal: un único recargo plano.
        for (String clave : CLAVES_RECARGO_PCT) {
            JsonNode n = horasExtraNodo.path(clave);
            if (n.isInt() && n.asInt() > 0) {
                return Optional.of(new RecargoExtra(n.asInt(),
                        "La hora extra se paga con un recargo del " + n.asInt() + "% sobre la ordinaria"));
            }
        }
        // Caso a tramos (Córdoba): 50% la primera hora de la semana, 75% el resto.
        // Aplicamos el MÍNIMO garantizado para no prometer de más, y citamos ambos.
        JsonNode resto = horasExtraNodo.path("recargoRestoHoras");
        if (resto.isInt() && resto.asInt() > 0) {
            JsonNode primera = horasExtraNodo.path("recargoPrimeraHoraSemanal");
            int r = resto.asInt();
            int p = primera.isInt() && primera.asInt() > 0 ? primera.asInt() : r;
            int minimo = Math.min(p, r);
            return Optional.of(new RecargoExtra(minimo,
                    "La hora extra lleva recargo (el " + p + "% la primera hora de la semana y el "
                            + r + "% el resto); mostramos el " + minimo + "% como mínimo garantizado"));
        }
        return Optional.empty();
    }

    /**
     * Mensualidades totales al año del convenio (14, 15...); vacío si el convenio
     * no las publica. Necesario para el cómputo ANUAL del SMI y del valor hora.
     */
    public Optional<BigDecimal> mensualidades(Convenio convenio) {
        return mensualidades(nodoPagas(convenio));
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
            BigDecimal total = MENSUALIDADES_ORDINARIAS.add(cantidad.decimalValue());
            // Mismo suelo de 12 que las demás ramas: una 'cantidad' negativa
            // (errata) desinflaría el valor hora en contra del trabajador.
            if (total.compareTo(MINIMO_MENSUALIDADES) >= 0) {
                return Optional.of(total);
            }
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
