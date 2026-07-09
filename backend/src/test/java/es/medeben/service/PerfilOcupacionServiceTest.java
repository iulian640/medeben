package es.medeben.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.medeben.repository.HechosCatalog;
import es.medeben.repository.OcupacionesCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PerfilOcupacionService — del puesto en cristiano a las dimensiones de la tabla (D20)")
class PerfilOcupacionServiceTest {

    private static PerfilOcupacionService servicio;

    @BeforeAll
    static void arranque() {
        ObjectMapper mapper = new ObjectMapper();
        servicio = new PerfilOcupacionService(new OcupacionesCatalog(mapper), new HechosCatalog(mapper));
    }

    @Test
    @DisplayName("la lista curada de puestos tiene 16 entradas con etiqueta en cristiano")
    void listaCurada() {
        assertThat(servicio.puestos()).hasSize(16);
        assertThat(servicio.puestos())
                .anySatisfy(p -> {
                    assertThat(p.id()).isEqualTo("cocinero");
                    assertThat(p.etiqueta()).isEqualTo("Cocinero/a");
                });
    }

    @Test
    @DisplayName("cocinero en madrid-hosteleria → nivel III y queda pendiente la clase de empresa A/B/C")
    void cocineroMadrid() {
        var resuelta = servicio.resuelve("madrid-hosteleria", "cocinero").orElseThrow();

        assertThat(resuelta.dimensiones())
                .containsEntry("tabla", "general")
                .containsEntry("nivel", "III");
        assertThat(resuelta.pendientes()).hasSize(1);
        assertThat(resuelta.pendientes().getFirst().dimension()).isEqualTo("claseEmpresa");
        assertThat(resuelta.pendientes().getFirst().valores()).containsExactly("A", "B", "C");
    }

    @Test
    @DisplayName("puesto no contemplado en el convenio (camarera de pisos en hostelería) → vacío")
    void puestoNoContemplado() {
        assertThat(servicio.resuelve("madrid-hosteleria", "camarera-pisos")).isEmpty();
    }

    @Test
    @DisplayName("convenio sin mapeo de ocupaciones todavía → vacío (cae al modo manual)")
    void convenioSinMapeo() {
        assertThat(servicio.resuelve("convenio-inexistente", "cocinero")).isEmpty();
    }

    @Test
    @DisplayName("puesto desconocido → vacío, no explota")
    void puestoDesconocido() {
        assertThat(servicio.resuelve("madrid-hosteleria", "astronauta")).isEmpty();
    }

    // --- Puestos con nivel CONDICIONAL al establecimiento/zona (datos reales) ---

    @Test
    @DisplayName("Jaén cocinero (condicional): sin respuestas pide el tipo de establecimiento")
    void jaenCondicionalPrimeraPregunta() {
        var r = servicio.resuelve("jaen-hosteleria", "cocinero").orElseThrow();

        assertThat(r.dimensiones()).isEmpty();
        assertThat(r.pendientes()).hasSize(1);
        assertThat(r.pendientes().getFirst().dimension()).isEqualTo("establecimiento");
        assertThat(r.pendientes().getFirst().valores()).contains("hoteles", "restaurantes");
    }

    @Test
    @DisplayName("Jaén cocinero: respondido el tipo, encadena la categoría de ESE tipo")
    void jaenCondicionalSegundaPregunta() {
        var r = servicio.resuelve("jaen-hosteleria", "cocinero",
                java.util.Map.of("establecimiento", "hoteles")).orElseThrow();

        assertThat(r.dimensiones()).isEmpty();
        assertThat(r.pendientes()).hasSize(1);
        assertThat(r.pendientes().getFirst().dimension()).isEqualTo("categoria");
    }

    @Test
    @DisplayName("Jaén cocinero: con tipo y categoría, resuelve el nivel y no quedan preguntas")
    void jaenCondicionalResuelto() {
        var r = servicio.resuelve("jaen-hosteleria", "cocinero",
                java.util.Map.of("establecimiento", "hoteles", "categoria", "5*y4*")).orElseThrow();

        assertThat(r.dimensiones()).containsKey("nivel");
        assertThat(r.pendientes()).isEmpty();
    }

    @Test
    @DisplayName("Asturias cocinero (1 nivel): elegir el establecimiento resuelve el nivel")
    void asturiasCondicionalUnNivel() {
        var sinResp = servicio.resuelve("asturias-hosteleria", "cocinero").orElseThrow();
        assertThat(sinResp.pendientes()).hasSize(1);
        String estab = sinResp.pendientes().getFirst().valores().getFirst();

        var r = servicio.resuelve("asturias-hosteleria", "cocinero",
                java.util.Map.of("establecimiento", estab)).orElseThrow();
        assertThat(r.dimensiones()).containsKey("nivel");
        assertThat(r.pendientes()).isEmpty();
    }

    @Test
    @DisplayName("Cataluña camarero (por zona): la zona resuelve el nivel Y va a la tabla; queda categoriaEstablecimiento")
    void catalunaCondicionalPorZona() {
        var sinResp = servicio.resuelve("cataluna-hosteleria", "camarero").orElseThrow();
        assertThat(sinResp.pendientes().getFirst().dimension()).isEqualTo("zona");

        var r = servicio.resuelve("cataluna-hosteleria", "camarero",
                java.util.Map.of("zona", "barcelona")).orElseThrow();
        // La zona es a la vez respuesta condicional y dimensión del salario.
        assertThat(r.dimensiones()).containsEntry("zona", "barcelona").containsKey("nivel");
        // El nivel ya está; falta la categoría del establecimiento (pendiente normal).
        assertThat(r.pendientes()).anySatisfy(
                p -> assertThat(p.dimension()).isEqualTo("categoriaEstablecimiento"));
    }

    @Test
    @DisplayName("una respuesta inventada no resuelve el nivel: reaparece la pregunta (nunca se inventa)")
    void condicionalRespuestaInventada() {
        var r = servicio.resuelve("jaen-hosteleria", "cocinero",
                java.util.Map.of("establecimiento", "castillo-inventado")).orElseThrow();

        assertThat(r.dimensiones()).isEmpty();
        assertThat(r.pendientes().getFirst().dimension()).isEqualTo("establecimiento");
    }
}
