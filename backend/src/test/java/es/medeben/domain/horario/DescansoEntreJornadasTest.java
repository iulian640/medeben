package es.medeben.domain.horario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DescansoEntreJornadas — 12 h (art. 34.3 ET), 7 h mínimo (RD 1561/1995)")
class DescansoEntreJornadasTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 7, 6);

    private static Map.Entry<LocalDate, DiaCuadrante> dia(LocalDate fecha, Tramo... tramos) {
        return Map.entry(fecha, new DiaCuadrante(List.of(tramos)));
    }

    @Test
    @DisplayName("descanso holgado (16 h) → sin incidencia")
    void descansoHolgado() {
        var dias = List.of(
                dia(LUNES, new Tramo("09:00", "17:00")),
                dia(LUNES.plusDays(1), new Tramo("09:00", "17:00")));

        assertThat(DescansoEntreJornadas.incidencias(dias)).isEmpty();
    }

    @Test
    @DisplayName("exactamente 12 h → sin incidencia (la frontera cumple)")
    void frontera12h() {
        var dias = List.of(
                dia(LUNES, new Tramo("13:00", "21:00")),   // fin 21:00
                dia(LUNES.plusDays(1), new Tramo("09:00", "17:00"))); // inicio 09:00 → 12 h justas

        assertThat(DescansoEntreJornadas.incidencias(dias)).isEmpty();
    }

    @Test
    @DisplayName("11 h de descanso → incidencia compensable (1 h), no ilegal")
    void eleveHorasCompensable() {
        var dias = List.of(
                dia(LUNES, new Tramo("09:00", "22:00")),   // fin 22:00
                dia(LUNES.plusDays(1), new Tramo("09:00", "17:00"))); // inicio 09:00 → 11 h

        var inc = DescansoEntreJornadas.incidencias(dias);
        assertThat(inc).hasSize(1);
        assertThat(inc.get(0).fecha()).isEqualTo(LUNES.plusDays(1));
        assertThat(inc.get(0).minutosDescanso()).isEqualTo(11 * 60);
        assertThat(inc.get(0).minutosCompensables()).isEqualTo(60);
        assertThat(inc.get(0).bajoMinimoLegal()).isFalse();
    }

    @Test
    @DisplayName("turno de cierre que cruza medianoche + apertura → 6 h: incidencia ILEGAL (< 7 h)")
    void cierreYAperturaEsIlegal() {
        var dias = List.of(
                dia(LUNES, new Tramo("18:00", "02:00")),   // cierre: fin 02:00 del martes
                dia(LUNES.plusDays(1), new Tramo("08:00", "16:00"))); // inicio 08:00 → 6 h

        var inc = DescansoEntreJornadas.incidencias(dias);
        assertThat(inc).hasSize(1);
        assertThat(inc.get(0).minutosDescanso()).isEqualTo(6 * 60);
        assertThat(inc.get(0).minutosCompensables()).isEqualTo(6 * 60); // 12 − 6
        assertThat(inc.get(0).bajoMinimoLegal()).isTrue();
    }

    @Test
    @DisplayName("turno partido: cuenta la SALIDA del último tramo")
    void turnoPartidoUsaUltimoTramo() {
        var dias = List.of(
                dia(LUNES, new Tramo("09:00", "13:00"), new Tramo("20:00", "23:00")), // fin 23:00
                dia(LUNES.plusDays(1), new Tramo("09:00", "17:00"))); // inicio 09:00 → 10 h

        var inc = DescansoEntreJornadas.incidencias(dias);
        assertThat(inc).hasSize(1);
        assertThat(inc.get(0).minutosCompensables()).isEqualTo(2 * 60);
    }

    @Test
    @DisplayName("un día libre en medio rompe la cadena: sin incidencia")
    void diaLibreNoIncumple() {
        // Lunes trabajado hasta las 23:00, martes LIBRE (sin tramos), miércoles 09:00.
        // El hueco lunes 23:00 → miércoles 09:00 es de 34 h, muy por encima de 12 h.
        var conLibre = List.of(
                dia(LUNES, new Tramo("15:00", "23:00")),
                Map.entry(LUNES.plusDays(1), new DiaCuadrante(List.of())),
                dia(LUNES.plusDays(2), new Tramo("09:00", "17:00")));

        assertThat(DescansoEntreJornadas.incidencias(conLibre)).isEmpty();
    }

    @Test
    @DisplayName("lista vacía o de un solo día → sin incidencias")
    void bordes() {
        assertThat(DescansoEntreJornadas.incidencias(List.of())).isEmpty();
        assertThat(DescansoEntreJornadas.incidencias(
                List.of(dia(LUNES, new Tramo("09:00", "17:00"))))).isEmpty();
    }
}
