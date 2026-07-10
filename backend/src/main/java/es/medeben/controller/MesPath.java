package es.medeben.controller;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Parseo compartido del segmento {@code yyyy-MM} de la URL (resumen e informe).
 * El input crudo NUNCA se ecoa en el error: puede ser cualquier cosa que venga
 * en la URL y acabaría tal cual en logs y respuestas RFC 7807.
 */
final class MesPath {

    /** Formato estricto yyyy-MM: un mes concreto, sin día. */
    private static final DateTimeFormatter YYYY_MM = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * Cota inferior del mes consultable. La libreta nace con la app (2026) y el
     * registro horario obligatorio es de 2019: nada anterior tiene sentido, y
     * sin cota un año como el 0001 recorrería su año entero igualmente.
     */
    private static final YearMonth MES_MINIMO = YearMonth.of(2019, 1);

    /** Longitud exacta de yyyy-MM: lo que no mida eso ni se intenta parsear (ni se ecoa). */
    private static final int LONGITUD_YYYY_MM = 7;

    private static final String FORMATO_ESPERADO =
            "Mes inválido: usa el formato yyyy-MM (por ejemplo 2026-07)";

    private MesPath() {
    }

    static YearMonth parsea(String anyoMes) {
        if (anyoMes == null || anyoMes.length() != LONGITUD_YYYY_MM) {
            throw new IllegalArgumentException(FORMATO_ESPERADO);
        }
        YearMonth mes;
        try {
            mes = YearMonth.parse(anyoMes, YYYY_MM);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(FORMATO_ESPERADO);
        }
        if (mes.isBefore(MES_MINIMO)) {
            throw new IllegalArgumentException(
                    "El mes " + mes + " es anterior a " + MES_MINIMO + ": no hay libreta que resumir tan atrás");
        }
        return mes;
    }
}
