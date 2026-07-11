package es.medeben.service;

import com.fasterxml.jackson.databind.JsonNode;
import es.medeben.domain.convenio.Convenio;
import es.medeben.domain.convenio.Hecho;
import es.medeben.domain.convenio.ValoresPorAnio;
import es.medeben.repository.HechosCatalog;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
 *
 * <p>Jornada y pagas que dependen de DIMENSIONES (issue #231, la colectiva las
 * publica por anexo provincial): cuando el convenio no las trae en sus nodos
 * propios, se resuelven como hechos de la capa derivada (`jornadaAnual` y
 * `mensualidadesEquivalentes`) filtrando por las dimensiones del llamador —
 * las mismas del perfil que ya resuelven la tabla salarial. Sin la dimensión
 * necesaria no se calcula nada: nunca se adivina la provincia.
 */
@Service
public class CalculoConvenioService {

    /** Tope del art. 35.2 del Estatuto de los Trabajadores si el convenio no fija otro. */
    private static final int TOPE_HORAS_EXTRA_ET = 80;

    private static final int DECIMALES_VALOR_HORA = 4;
    private static final int DECIMALES_IMPORTE = 2;
    private static final BigDecimal MENSUALIDADES_ORDINARIAS = BigDecimal.valueOf(12);
    private static final BigDecimal MINIMO_MENSUALIDADES = BigDecimal.valueOf(12);

    /** Conceptos de la capa derivada que puede necesitar el valor hora (D37). */
    private static final String CONCEPTO_JORNADA_ANUAL = "jornadaAnual";
    private static final String CONCEPTO_MENSUALIDADES = "mensualidadesEquivalentes";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final HechosCatalog hechos;

    public CalculoConvenioService(HechosCatalog hechos) {
        this.hechos = hechos;
    }

    /** Valor de la hora ordinaria sin dimensiones (convenios de jornada y pagas únicas). */
    public Optional<ValorHoraCalculado> valorHoraOrdinaria(
            Convenio convenio, Year anio, BigDecimal salarioBaseMensual, BigDecimal plusesAnuales) {
        return valorHoraOrdinaria(convenio, anio, salarioBaseMensual, plusesAnuales, Map.of());
    }

    /**
     * Valor de la hora ordinaria: (salario base × mensualidades totales + pluses
     * anuales) / divisor de horas. El divisor es la jornada anual salvo que el
     * convenio fije explícitamente otro (`divisorValorHora.horas`, p. ej. las
     * 1.829 h de Tenerife): el divisor explícito es un dato del convenio y tiene
     * prioridad. Si el convenio no publica jornada o pagas en sus nodos propios,
     * se buscan como hechos de la capa derivada con las {@code dimensiones} del
     * llamador (issue #231: en la colectiva van por provincia). Vacío si con
     * todo eso siguen faltando divisor o pagas para ese año.
     */
    public Optional<ValorHoraCalculado> valorHoraOrdinaria(
            Convenio convenio, Year anio, BigDecimal salarioBaseMensual, BigDecimal plusesAnuales,
            Map<String, String> dimensiones) {
        Objects.requireNonNull(convenio, "convenio");
        Objects.requireNonNull(anio, "anio");
        Objects.requireNonNull(dimensiones, "dimensiones");
        if (salarioBaseMensual == null || salarioBaseMensual.signum() <= 0) {
            throw new IllegalArgumentException("El salario base mensual debe ser positivo");
        }
        if (plusesAnuales == null || plusesAnuales.signum() < 0) {
            throw new IllegalArgumentException("Los pluses anuales no pueden ser negativos");
        }

        JsonNode divisorNodo = convenio.raw().path("divisorValorHora");
        Optional<BigDecimal> divisorExplicito = ValoresPorAnio.resuelve(divisorNodo.path("horas"), anio);
        Optional<BigDecimal> divisorPropio = divisorExplicito.or(() -> convenio.jornadaAnual(anio));
        // Los nodos propios del convenio mandan; el hecho derivado solo entra si faltan.
        Optional<Hecho> jornadaDerivada = divisorPropio.isPresent()
                ? Optional.empty()
                : hechoDerivado(convenio, CONCEPTO_JORNADA_ANUAL, dimensiones, anio);
        Optional<BigDecimal> divisor = divisorPropio
                .or(() -> jornadaDerivada.map(Hecho::importe).filter(h -> h.signum() > 0));

        Optional<BigDecimal> mensualidadesPropias = mensualidades(nodoPagas(convenio));
        Optional<Hecho> mensualidadesDerivadas = mensualidadesPropias.isPresent()
                ? Optional.empty()
                : hechoDerivado(convenio, CONCEPTO_MENSUALIDADES, dimensiones, anio)
                        // Mismo suelo de 12 que las ramas del nodo propio: menos de 12
                        // mensualidades solo puede ser una errata de derivación.
                        .filter(h -> h.importe().compareTo(MINIMO_MENSUALIDADES) >= 0);
        Optional<BigDecimal> mensualidades = mensualidadesPropias
                .or(() -> mensualidadesDerivadas.map(Hecho::importe));
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
        } else if (jornadaDerivada.isPresent()) {
            citas.add(citaDeHecho("Jornada anual de " + divisor.get().stripTrailingZeros().toPlainString()
                    + " h", jornadaDerivada.get(), anio, convenio));
        } else {
            citas.add(new Cita("Jornada anual de " + divisor.get().stripTrailingZeros().toPlainString()
                    + " h (" + articuloJornada(convenio) + " del convenio)", convenio.fuenteUrl()));
        }
        if (mensualidadesDerivadas.isPresent()) {
            citas.add(citaDeHecho(mensualidades.get().stripTrailingZeros().toPlainString()
                    + " mensualidades al año", mensualidadesDerivadas.get(), anio, convenio));
        } else {
            citas.add(new Cita(mensualidades.get().stripTrailingZeros().toPlainString()
                    + " mensualidades al año (" + articulo(nodoPagas(convenio)) + " del convenio)",
                    convenio.fuenteUrl()));
        }
        return Optional.of(new ValorHoraCalculado(
                valorHora, salarioBaseMensual, mensualidades.get(), plusesAnuales,
                divisor.get(), divisorExplicito.isPresent(), citas));
    }

    /**
     * Hecho de la capa derivada aplicable al año y a las dimensiones del
     * llamador: sus dimensiones deben estar TODAS contenidas en las del
     * llamador (un hecho de {provincia} casa con un perfil de
     * {provincia, categoria}; con un llamador sin provincia no casa nada —
     * nunca se adivina). Aplica el vigente al cierre del año o, si no lo hay,
     * el último anterior (ultraactividad, como {@link TablaSalarialService}).
     */
    private Optional<Hecho> hechoDerivado(
            Convenio convenio, String concepto, Map<String, String> dimensiones, Year anio) {
        List<Hecho> candidatos = hechos.deConvenio(convenio.id()).stream()
                .filter(h -> concepto.equals(h.concepto()))
                .filter(h -> dimensiones.entrySet().containsAll(h.dimensiones().entrySet()))
                .toList();
        if (candidatos.isEmpty()) {
            return Optional.empty();
        }
        LocalDate cierre = anio.atMonth(12).atEndOfMonth();
        List<Hecho> vigentes = candidatos.stream().filter(h -> h.vigenteEn(cierre)).toList();
        if (vigentes.size() > 1) {
            // Solo posible con una capa derivada corrupta (dos hechos del mismo
            // concepto casando a la vez): mejor romper que elegir a ciegas.
            throw new IllegalStateException("Capa derivada ambigua: " + vigentes.size()
                    + " hechos de " + concepto + " vigentes en " + cierre + " para " + dimensiones.keySet());
        }
        if (vigentes.size() == 1) {
            return Optional.of(vigentes.get(0));
        }
        // Ultraactividad: la última tabla publicada sigue aplicando hasta que
        // salga la nueva (el convenio vencido no caduca).
        List<Hecho> anteriores = candidatos.stream()
                .filter(h -> h.hasta().isBefore(cierre))
                .toList();
        Optional<Hecho> ultimo = anteriores.stream().max(Comparator.comparing(Hecho::hasta));
        if (ultimo.isPresent()
                && anteriores.stream().filter(h -> h.hasta().equals(ultimo.get().hasta())).count() > 1) {
            throw new IllegalStateException("Capa derivada ambigua: varios hechos de " + concepto
                    + " terminan en " + ultimo.get().hasta() + " para " + dimensiones.keySet());
        }
        return ultimo;
    }

    /**
     * Cita de un dato que sale de la capa derivada (D34): artículo del hecho y,
     * si el año pedido cae fuera de su vigencia, el aviso de ultraactividad —
     * mismo lenguaje que el lookup de tablas salariales.
     */
    private static Cita citaDeHecho(String texto, Hecho hecho, Year anio, Convenio convenio) {
        String cita = texto + " (" + hecho.articulo() + " del convenio";
        if (!hecho.vigenteEn(anio.atMonth(12).atEndOfMonth())) {
            cita += "; dato de vigencia hasta " + FECHA.format(hecho.hasta())
                    + ", aplicado por ultraactividad: sigue en vigor hasta que se publique el nuevo";
        }
        return new Cita(cita + ")", convenio.fuenteUrl());
    }

    /** Importe de unas horas extra sin dimensiones (convenios de jornada y pagas únicas). */
    public Optional<HorasExtraCalculadas> importeHorasExtra(
            Convenio convenio, Year anio, BigDecimal salarioBaseMensual, BigDecimal plusesAnuales,
            BigDecimal horas) {
        return importeHorasExtra(convenio, anio, salarioBaseMensual, plusesAnuales, horas, Map.of());
    }

    /**
     * Importe de unas horas extra. Precio aplicado: el fijado por el convenio si
     * existe y es mayor; nunca por debajo del valor de la hora ordinaria
     * (art. 35.1 ET, suelo legal). Las {@code dimensiones} solo intervienen si
     * la jornada o las pagas viven en la capa derivada (issue #231).
     */
    public Optional<HorasExtraCalculadas> importeHorasExtra(
            Convenio convenio, Year anio, BigDecimal salarioBaseMensual, BigDecimal plusesAnuales,
            BigDecimal horas, Map<String, String> dimensiones) {
        if (horas == null || horas.signum() < 0) {
            throw new IllegalArgumentException("Las horas extra no pueden ser negativas");
        }

        return valorHoraOrdinaria(convenio, anio, salarioBaseMensual, plusesAnuales, dimensiones)
                .map(valorHora -> {
            List<Cita> citas = new ArrayList<>(valorHora.citas());
            BigDecimal precio = valorHora.valorHora();
            citas.add(Cita.delEstatuto(
                    "La hora extra no puede pagarse por debajo de la hora ordinaria (art. 35.1 ET)"));

            JsonNode horasExtraNodo = convenio.raw().path("horasExtraordinarias");

            // (1) Precio €/hora FIJO del convenio (Teruel, Almería...).
            Optional<BigDecimal> precioConvenio = ValoresPorAnio.resuelve(horasExtraNodo.path("importe"), anio);
            if (precioConvenio.isPresent() && precioConvenio.get().compareTo(precio) > 0) {
                precio = precioConvenio.get();
                // Notación española en la cita (issue #222): mismo formateador que los PDF.
                citas.add(new Cita("Precio de hora extra fijado en " + PdfInforme.dinero(precio)
                        + " €/h (" + articulo(horasExtraNodo) + " del convenio)", convenio.fuenteUrl()));
            }

            // (2) RECARGO PORCENTUAL sobre la hora ordinaria: la forma más común
            // del corpus (Cádiz 75%, Granada 100%, Cuenca "al 175%"...). El factor
            // ya viene resuelto: recargo del X% → 1+X/100; abono AL X% → X/100.
            // Antes se ignoraba y la hora extra se pagaba igual que la ordinaria.
            Optional<RecargoExtra> recargo = recargoPorcentual(horasExtraNodo);
            if (recargo.isPresent()) {
                BigDecimal conRecargo = valorHora.valorHora().multiply(recargo.get().factor());
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
        Optional<BigDecimal> tope = topeAnualDelConvenio(horasExtraNodo, anio);
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

    /** Claves planas bajo las que el corpus guarda el tope anual (todas equivalen). */
    private static final String[] CLAVES_TOPE_ANUAL =
            {"topeHorasExtraAnual", "topeAnualHoras", "topeAnual"};

    /**
     * Tope anual de horas extra que fija el convenio, mirando todas las claves que
     * usa el corpus (planas y el anidado `topes.{año|anio}`). Vacío si el convenio
     * no lo cuantifica (p. ej. lo deja como texto "rige el ET") → se cita el ET.
     */
    private static Optional<BigDecimal> topeAnualDelConvenio(JsonNode horasExtraNodo, Year anio) {
        for (String clave : CLAVES_TOPE_ANUAL) {
            Optional<BigDecimal> valor = ValoresPorAnio.resuelve(horasExtraNodo.path(clave), anio);
            if (valor.isPresent()) {
                return valor;
            }
        }
        JsonNode topes = horasExtraNodo.path("topes");
        for (String clave : new String[]{"año", "anio"}) {
            JsonNode n = topes.path(clave);
            if (n.isNumber() && n.decimalValue().signum() > 0) {
                return Optional.of(n.decimalValue());
            }
        }
        return Optional.empty();
    }

    /** Recargo % de la hora extra: el FACTOR sobre la hora ordinaria y el texto de la cita. */
    private record RecargoExtra(BigDecimal factor, String nota) {}

    /** Nombres bajo los que el corpus guarda el recargo % plano de la hora extra. */
    private static final String[] CLAVES_RECARGO_PCT =
            {"porcentaje", "recargoPct", "incrementoAbonoPorcentaje", "recargoImplicitoPorcentaje"};

    /**
     * Recargo porcentual de la hora extra sobre la ordinaria, si el convenio lo
     * fija así (p. ej. 75 o 100). Vacío si no hay recargo porcentual o si su base
     * no es la hora ordinaria (no se aplica a ciegas sobre otra base).
     */
    private static Optional<RecargoExtra> recargoPorcentual(JsonNode horasExtraNodo) {
        JsonNode sobre = horasExtraNodo.path("sobre");
        if (sobre.isTextual()) {
            // Debe ser la hora/salario ORDINARIA. "ordinari" cubre "hora_ordinaria",
            // "valor_hora_ordinaria" y "salario_real_ordinario" (masculino); pero
            // "extraordinari(a/o)" también lo contiene, así que la excluimos.
            String base = sobre.asText().toLowerCase();
            if (!base.contains("ordinari") || base.contains("extraordinari")) {
                return Optional.empty();
            }
        }
        // ¿El % es un recargo SOBRE la ordinaria (1+X/100) o el abono TOTAL (X/100)?
        // Cuenca abona "al 175%" (×1,75); Cantabria recarga "el 175%" (×2,75).
        boolean abonoTotal = "abono_total".equals(horasExtraNodo.path("computoRecargo").asText(""));
        // Caso normal: un único recargo plano.
        for (String clave : CLAVES_RECARGO_PCT) {
            JsonNode n = horasExtraNodo.path(clave);
            if (n.isNumber() && n.decimalValue().signum() > 0) {
                BigDecimal pct = n.decimalValue();
                String pctTxt = pct.stripTrailingZeros().toPlainString();
                if (abonoTotal) {
                    return Optional.of(new RecargoExtra(pct.movePointLeft(2),
                            "La hora extra se abona al " + pctTxt + "% del valor de la hora ordinaria"));
                }
                return Optional.of(new RecargoExtra(BigDecimal.ONE.add(pct.movePointLeft(2)),
                        "La hora extra se paga con un recargo del " + pctTxt + "% sobre la ordinaria"));
            }
        }
        // Caso a tramos (Córdoba): 50% la primera hora de la semana, 75% el resto.
        // Aplicamos el MÍNIMO garantizado para no prometer de más, y citamos ambos.
        JsonNode resto = horasExtraNodo.path("recargoRestoHoras");
        if (resto.isNumber() && resto.decimalValue().signum() > 0) {
            JsonNode primera = horasExtraNodo.path("recargoPrimeraHoraSemanal");
            BigDecimal r = resto.decimalValue();
            BigDecimal p = primera.isNumber() && primera.decimalValue().signum() > 0
                    ? primera.decimalValue() : r;
            BigDecimal minimo = p.min(r);
            return Optional.of(new RecargoExtra(BigDecimal.ONE.add(minimo.movePointLeft(2)),
                    "La hora extra lleva recargo (el " + p.stripTrailingZeros().toPlainString()
                            + "% la primera hora de la semana y el " + r.stripTrailingZeros().toPlainString()
                            + "% el resto); mostramos el " + minimo.stripTrailingZeros().toPlainString()
                            + "% como mínimo garantizado"));
        }
        return Optional.empty();
    }

    /**
     * Mensualidades totales al año del convenio (14, 15...); vacío si el convenio
     * no las publica. Necesario para el cómputo ANUAL del SMI y del valor hora.
     * SOLO mira el nodo raíz del crudo: sirve para convenios que publican las
     * pagas en la raíz, pero devuelve vacío para los que las llevan por anexo
     * provincial (la colectiva). Para esos, usar la sobrecarga con dimensiones.
     */
    public Optional<BigDecimal> mensualidades(Convenio convenio) {
        return mensualidades(nodoPagas(convenio));
    }

    /**
     * Mensualidades resueltas también desde la capa DERIVADA por dimensiones,
     * con la misma prioridad que el valor hora (issue #231): primero el nodo
     * raíz del crudo y, si no lo hay, el hecho `mensualidadesEquivalentes` del
     * anexo que casa con las dimensiones del llamador (la colectiva publica las
     * pagas por provincia). Vacío solo si de verdad no consta en ninguno de los
     * dos sitios — entonces el aviso del SMI asume 14 como último recurso. Sin
     * esto, el aviso adivinaba 14 pagas para toda la colectiva y contradecía las
     * 15 que este mismo cálculo ya publica en el valor hora extra.
     */
    public Optional<BigDecimal> mensualidades(
            Convenio convenio, Map<String, String> dimensiones, Year anio) {
        return mensualidades(nodoPagas(convenio))
                .or(() -> hechoDerivado(convenio, CONCEPTO_MENSUALIDADES, dimensiones, anio)
                        .map(Hecho::importe)
                        .filter(m -> m.compareTo(MINIMO_MENSUALIDADES) >= 0));
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
