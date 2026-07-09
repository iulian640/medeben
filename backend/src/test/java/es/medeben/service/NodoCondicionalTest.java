package es.medeben.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El árbol de decisión de los puestos con nivel condicional, con las TRES
 * formas reales del corpus (Asturias/Cataluña 1 nivel, Jaén 2 niveles hoja
 * string, Pontevedra 2 niveles hoja objeto).
 */
@DisplayName("NodoCondicional — resolución del nivel por establecimiento/zona")
class NodoCondicionalTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode json(String texto) {
        try {
            return JSON.readTree(texto);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // --- 1 nivel, hoja objeto (Asturias) ---

    @Test
    @DisplayName("Asturias (1 nivel): elegir el establecimiento resuelve el nivel")
    void asturiasUnNivel() {
        NodoCondicional arbol = NodoCondicional.desde(json("""
                {"hoteles_5o4_estrellas": {"nivel": "I"},
                 "cafes_bares_cervecerias_otros": {"nivel": "IV"}}"""), "establecimiento");

        assertThat(arbol.siguientePregunta(Map.of()))
                .hasValueSatisfying(p -> {
                    assertThat(p.dimension()).isEqualTo("establecimiento");
                    assertThat(p.valores()).contains("hoteles_5o4_estrellas", "cafes_bares_cervecerias_otros");
                });
        assertThat(arbol.resuelveNivel(Map.of("establecimiento", "hoteles_5o4_estrellas")))
                .contains("I");
        assertThat(arbol.resuelveNivel(Map.of("establecimiento", "cafes_bares_cervecerias_otros")))
                .contains("IV");
        // Ya resuelto: no quedan preguntas.
        assertThat(arbol.siguientePregunta(Map.of("establecimiento", "hoteles_5o4_estrellas"))).isEmpty();
    }

    // --- 1 nivel, dimensión "zona" (Cataluña) ---

    @Test
    @DisplayName("Cataluña (1 nivel por zona): la dimensión de la pregunta es 'zona'")
    void catalunaPorZona() {
        NodoCondicional arbol = NodoCondicional.desde(json("""
                {"barcelona": {"nivel": "II"}, "tarragona": {"nivel": "III"}}"""), "zona");

        assertThat(arbol.siguientePregunta(Map.of()))
                .hasValueSatisfying(p -> assertThat(p.dimension()).isEqualTo("zona"));
        assertThat(arbol.resuelveNivel(Map.of("zona", "barcelona"))).contains("II");
        assertThat(arbol.resuelveNivel(Map.of("zona", "tarragona"))).contains("III");
    }

    // --- 2 niveles, hoja string (Jaén): preguntas ENCADENADAS ---

    @Test
    @DisplayName("Jaén (2 niveles, hoja string): tipo y luego categoría, encadenadas")
    void jaenDosNivelesEncadenados() {
        NodoCondicional arbol = NodoCondicional.desde(json("""
                {"hoteles": {"5*y4*": "1.70", "3*": "1.60"},
                 "restaurantes": {"5T/lujo": "1.70", "1T/4a": "1.55"}}"""), "establecimiento");

        // 1ª pregunta: el tipo de establecimiento.
        assertThat(arbol.siguientePregunta(Map.of()))
                .hasValueSatisfying(p -> {
                    assertThat(p.dimension()).isEqualTo("establecimiento");
                    assertThat(p.valores()).contains("hoteles", "restaurantes");
                });
        // Respondido el tipo, la 2ª pregunta es la categoría de ESE tipo.
        var trasTipo = arbol.siguientePregunta(Map.of("establecimiento", "hoteles"));
        assertThat(trasTipo).hasValueSatisfying(p -> {
            assertThat(p.dimension()).isEqualTo("categoria");
            assertThat(p.valores()).containsExactly("3*", "5*y4*"); // ordenadas
        });
        // Las dos respuestas resuelven el nivel.
        assertThat(arbol.resuelveNivel(Map.of("establecimiento", "hoteles", "categoria", "5*y4*")))
                .contains("1.70");
        assertThat(arbol.resuelveNivel(Map.of("establecimiento", "restaurantes", "categoria", "1T/4a")))
                .contains("1.55");
        // Solo el tipo: aún no resuelve.
        assertThat(arbol.resuelveNivel(Map.of("establecimiento", "hoteles"))).isEmpty();
    }

    // --- 2 niveles, hoja objeto (Pontevedra) ---

    @Test
    @DisplayName("Pontevedra (2 niveles, hoja objeto): misma navegación, hoja {nivel}")
    void pontevedraDosNivelesHojaObjeto() {
        NodoCondicional arbol = NodoCondicional.desde(json("""
                {"tipoA": {"5": {"nivel": "1"}, "3": {"nivel": "2"}, "1": {"nivel": "5"}}}"""),
                "establecimiento");

        assertThat(arbol.resuelveNivel(Map.of("establecimiento", "tipoA", "categoria", "5")))
                .contains("1");
        assertThat(arbol.resuelveNivel(Map.of("establecimiento", "tipoA", "categoria", "1")))
                .contains("5");
    }

    // --- Nunca inventar ---

    @Test
    @DisplayName("una respuesta que no existe en las ramas NO resuelve (nunca se inventa un nivel)")
    void respuestaInvalidaNoResuelve() {
        NodoCondicional arbol = NodoCondicional.desde(json("""
                {"hoteles": {"5*y4*": "1.70"}}"""), "establecimiento");

        assertThat(arbol.resuelveNivel(Map.of("establecimiento", "castillo"))).isEmpty();
        assertThat(arbol.resuelveNivel(Map.of("establecimiento", "hoteles", "categoria", "7*"))).isEmpty();
        // Y la pregunta pendiente reaparece si el valor elegido no es válido.
        assertThat(arbol.siguientePregunta(Map.of("establecimiento", "castillo")))
                .hasValueSatisfying(p -> assertThat(p.dimension()).isEqualTo("establecimiento"));
    }
}
