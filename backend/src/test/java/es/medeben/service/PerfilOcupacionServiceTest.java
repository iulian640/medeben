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

    @Test
    @DisplayName("auto-fijado: una dimensión con un solo valor posible se fija sola, no se pregunta")
    void autofijaDimensionDeUnSoloValor() {
        // Málaga jefe de sala/maître: 'departamento' solo puede ser "sala" → es
        // ruido preguntarlo. Se pliega en la ocupación y no aparece como pendiente.
        var r = servicio.resuelve("malaga-hosteleria", "jefe-sala").orElseThrow();

        assertThat(r.dimensiones()).containsEntry("departamento", "sala");
        assertThat(r.pendientes()).noneMatch(p -> p.dimension().equals("departamento"));
        // Invariante del auto-fijado: ningún pendiente restante tiene una sola opción.
        assertThat(r.pendientes()).allSatisfy(p -> assertThat(p.valores()).hasSizeGreaterThan(1));
    }

    @Test
    @DisplayName("mapeo directo: la respuesta a una dimensión de tabla se pliega y deja de preguntarse")
    void directoPliegaRespuestaDeTabla() {
        // Cocinero Madrid pregunta la clase de empresa. Al responderla (re-resolución
        // del frontend), debe quedar plegada en las dimensiones y ya no como pendiente.
        var r = servicio.resuelve("madrid-hosteleria", "cocinero",
                java.util.Map.of("claseEmpresa", "B")).orElseThrow();

        assertThat(r.dimensiones())
                .containsEntry("nivel", "III")
                .containsEntry("claseEmpresa", "B");
        assertThat(r.pendientes()).noneMatch(p -> p.dimension().equals("claseEmpresa"));
    }

    @Test
    @DisplayName("mapeo directo: una respuesta que NO es dimensión de la tabla se ignora (no ensucia)")
    void directoIgnoraRespuestaAjena() {
        var r = servicio.resuelve("madrid-hosteleria", "cocinero",
                java.util.Map.of("basura", "x")).orElseThrow();

        assertThat(r.dimensiones()).doesNotContainKey("basura");
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

    @Test
    @DisplayName("Pontevedra camarero (profundidad mixta): tipoD resuelve en un paso, tipoA encadena categoría")
    void pontevedraProfundidadMixta() {
        // tipoD: {nivel} directo → resuelve sin preguntar categoría.
        var directo = servicio.resuelve("pontevedra-hosteleria", "camarero",
                java.util.Map.of("establecimiento", "tipoD")).orElseThrow();
        assertThat(directo.dimensiones()).containsKey("nivel");
        assertThat(directo.pendientes()).isEmpty();

        // tipoA: {categoria:{nivel}} → tras el tipo, encadena la categoría.
        var encadena = servicio.resuelve("pontevedra-hosteleria", "camarero",
                java.util.Map.of("establecimiento", "tipoA")).orElseThrow();
        assertThat(encadena.dimensiones()).isEmpty();
        assertThat(encadena.pendientes().getFirst().dimension()).isEqualTo("categoria");
    }

    @Test
    @DisplayName("SEGURIDAD (review CRITICAL): un 'nivel' inyectado por el cliente NO pisa el que resuelve el árbol")
    void condicionalNivelInyectadoNoManda() {
        // Jaén cocinero hotel 5*y4* resuelve nivel 1.70. El cliente intenta
        // colar nivel=1.35 (más barato). El árbol es autoritativo.
        var legitimo = servicio.resuelve("jaen-hosteleria", "cocinero",
                java.util.Map.of("establecimiento", "hoteles", "categoria", "5*y4*")).orElseThrow();
        String nivelReal = legitimo.dimensiones().get("nivel");

        var conInyeccion = servicio.resuelve("jaen-hosteleria", "cocinero", java.util.Map.of(
                "establecimiento", "hoteles", "categoria", "5*y4*", "nivel", "1.35")).orElseThrow();

        assertThat(conInyeccion.dimensiones().get("nivel")).isEqualTo(nivelReal);
        assertThat(conInyeccion.dimensiones().get("nivel")).isNotEqualTo("1.35");
    }

    @Test
    @DisplayName("SEGURIDAD: una dimensión de tabla suelta por query param (no la raíz del árbol) no se cuela")
    void condicionalDimensionSueltaNoSeCuela() {
        // categoriaEstablecimiento es dimensión de tabla en Cataluña, pero NO es
        // la raíz del árbol (que es 'zona'); no debe entrar por query param, sale
        // como pendiente normal para que el usuario la elija de valores reales.
        var r = servicio.resuelve("cataluna-hosteleria", "camarero", java.util.Map.of(
                "zona", "barcelona", "categoriaEstablecimiento", "ZZZ-inventada")).orElseThrow();

        assertThat(r.dimensiones()).doesNotContainKey("categoriaEstablecimiento");
        assertThat(r.dimensiones()).containsEntry("zona", "barcelona").containsKey("nivel");
        assertThat(r.pendientes()).anySatisfy(
                p -> assertThat(p.dimension()).isEqualTo("categoriaEstablecimiento"));
    }
}
