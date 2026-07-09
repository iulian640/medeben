package es.medeben.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El árbol de decisión de los puestos con nivel condicional, con las TRES
 * formas reales del corpus (Asturias/Cataluña 1 nivel, Jaén 2 niveles hoja
 * string, Pontevedra 2 niveles hoja objeto) y las celdas {@code null} que el
 * corpus marca como "no aplicable".
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

    private static NodoCondicional arbol(String texto, String dimensionRaiz) {
        return NodoCondicional.desde(json(texto), dimensionRaiz).orElseThrow();
    }

    // --- 1 nivel, hoja objeto (Asturias) ---

    @Test
    @DisplayName("Asturias (1 nivel): elegir el establecimiento resuelve el nivel")
    void asturiasUnNivel() {
        NodoCondicional a = arbol("""
                {"hoteles_5o4_estrellas": {"nivel": "I"},
                 "cafes_bares_cervecerias_otros": {"nivel": "IV"}}""", "establecimiento");

        assertThat(a.siguientePregunta(Map.of()))
                .hasValueSatisfying(p -> {
                    assertThat(p.dimension()).isEqualTo("establecimiento");
                    assertThat(p.valores()).contains("hoteles_5o4_estrellas", "cafes_bares_cervecerias_otros");
                });
        assertThat(a.resuelveNivel(Map.of("establecimiento", "hoteles_5o4_estrellas"))).contains("I");
        assertThat(a.resuelveNivel(Map.of("establecimiento", "cafes_bares_cervecerias_otros"))).contains("IV");
        assertThat(a.siguientePregunta(Map.of("establecimiento", "hoteles_5o4_estrellas"))).isEmpty();
    }

    // --- 1 nivel, dimensión "zona" (Cataluña) ---

    @Test
    @DisplayName("Cataluña (1 nivel por zona): la dimensión de la pregunta es 'zona'")
    void catalunaPorZona() {
        NodoCondicional a = arbol("""
                {"barcelona": {"nivel": "II"}, "tarragona": {"nivel": "III"}}""", "zona");

        assertThat(a.dimension()).isEqualTo("zona");
        assertThat(a.siguientePregunta(Map.of()))
                .hasValueSatisfying(p -> assertThat(p.dimension()).isEqualTo("zona"));
        assertThat(a.resuelveNivel(Map.of("zona", "barcelona"))).contains("II");
        assertThat(a.resuelveNivel(Map.of("zona", "tarragona"))).contains("III");
    }

    // --- 2 niveles, hoja string (Jaén): preguntas ENCADENADAS ---

    @Test
    @DisplayName("Jaén (2 niveles, hoja string): tipo y luego categoría, encadenadas")
    void jaenDosNivelesEncadenados() {
        NodoCondicional a = arbol("""
                {"hoteles": {"5*y4*": "1.70", "3*": "1.60"},
                 "restaurantes": {"5T/lujo": "1.70", "1T/4a": "1.55"}}""", "establecimiento");

        assertThat(a.siguientePregunta(Map.of()))
                .hasValueSatisfying(p -> {
                    assertThat(p.dimension()).isEqualTo("establecimiento");
                    assertThat(p.valores()).contains("hoteles", "restaurantes");
                });
        assertThat(a.siguientePregunta(Map.of("establecimiento", "hoteles")))
                .hasValueSatisfying(p -> {
                    assertThat(p.dimension()).isEqualTo("categoria");
                    assertThat(p.valores()).containsExactly("3*", "5*y4*");
                });
        assertThat(a.resuelveNivel(Map.of("establecimiento", "hoteles", "categoria", "5*y4*"))).contains("1.70");
        assertThat(a.resuelveNivel(Map.of("establecimiento", "restaurantes", "categoria", "1T/4a"))).contains("1.55");
        assertThat(a.resuelveNivel(Map.of("establecimiento", "hoteles"))).isEmpty();
    }

    // --- 2 niveles, hoja objeto (Pontevedra) ---

    @Test
    @DisplayName("Pontevedra (2 niveles, hoja objeto): misma navegación, hoja {nivel}")
    void pontevedraDosNivelesHojaObjeto() {
        NodoCondicional a = arbol("""
                {"tipoA": {"5": {"nivel": "1"}, "3": {"nivel": "2"}, "1": {"nivel": "5"}}}""", "establecimiento");

        assertThat(a.resuelveNivel(Map.of("establecimiento", "tipoA", "categoria", "5"))).contains("1");
        assertThat(a.resuelveNivel(Map.of("establecimiento", "tipoA", "categoria", "1"))).contains("5");
    }

    // --- Nunca inventar ---

    @Test
    @DisplayName("una respuesta que no existe en las ramas NO resuelve (nunca se inventa un nivel)")
    void respuestaInvalidaNoResuelve() {
        NodoCondicional a = arbol("""
                {"hoteles": {"5*y4*": "1.70"}}""", "establecimiento");

        assertThat(a.resuelveNivel(Map.of("establecimiento", "castillo"))).isEmpty();
        assertThat(a.resuelveNivel(Map.of("establecimiento", "hoteles", "categoria", "7*"))).isEmpty();
        assertThat(a.siguientePregunta(Map.of("establecimiento", "castillo")))
                .hasValueSatisfying(p -> assertThat(p.dimension()).isEqualTo("establecimiento"));
    }

    // --- Celdas null: se podan, no encadenan preguntas vacías (review HIGH) ---

    @Test
    @DisplayName("una categoría con celda null NO se ofrece como opción (se poda)")
    void celdaNullSePoda() {
        // barman/hoteles del corpus: 5*y4*=1.55, 3*=1.55, 2*=null, 1*=null.
        NodoCondicional a = arbol("""
                {"hoteles": {"5*y4*": "1.55", "3*": "1.55", "2*": null, "1*": null}}""", "establecimiento");

        assertThat(a.siguientePregunta(Map.of("establecimiento", "hoteles")))
                .hasValueSatisfying(p -> {
                    assertThat(p.valores()).containsExactly("3*", "5*y4*"); // 2* y 1* podadas
                    assertThat(p.valores()).doesNotContain("2*", "1*");
                });
        // Y si se pidiera el podado, no resuelve (no se inventa).
        assertThat(a.resuelveNivel(Map.of("establecimiento", "hoteles", "categoria", "2*"))).isEmpty();
    }

    @Test
    @DisplayName("una zona con valor null NO se ofrece (Cataluña girona/tarragona null en algunos puestos)")
    void zonaNullSePoda() {
        NodoCondicional a = arbol("""
                {"barcelona": {"nivel": "III"}, "girona": null}""", "zona");

        assertThat(a.siguientePregunta(Map.of()))
                .hasValueSatisfying(p -> {
                    assertThat(p.valores()).containsExactly("barcelona");
                    assertThat(p.valores()).doesNotContain("girona");
                });
    }

    @Test
    @DisplayName("un puesto cuyas celdas son TODAS null no produce árbol (cae a modo manual)")
    void todoNullNoHayArbol() {
        assertThat(NodoCondicional.desde(json("""
                {"hoteles": {"5*": null, "3*": null}}"""), "establecimiento")).isEmpty();
    }
}
