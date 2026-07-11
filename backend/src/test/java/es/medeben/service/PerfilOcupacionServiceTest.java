package es.medeben.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.medeben.controller.DimensionDesconocidaException;
import es.medeben.repository.HechosCatalog;
import es.medeben.repository.OcupacionesCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        // Málaga jefe de sala/maître en bares americanos: 'departamento' solo
        // puede ser "sala" → es ruido preguntarlo. Se pliega en la ocupación y
        // no aparece como pendiente. (Antes de responder la sección no se fija:
        // la fila de casinos/salas de fiestas ni siquiera tiene departamento —
        // las formas de tabla alternativas se resuelven pregunta a pregunta, #233.)
        var r = servicio.resuelve("malaga-hosteleria", "jefe-sala",
                java.util.Map.of("seccion", "5_baresAmericanos")).orElseThrow();

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

    @Test
    @DisplayName("mapeo directo: el cliente NO puede pisar el nivel que fija el puesto vía query param")
    void directoNivelDelPuestoEsAutoritativo() {
        // Cocinero Madrid = nivel III (lo fija el puesto). Un ?nivel=I inyectado no
        // debe cambiarlo y saltar a otra fila salarial (mismo blindaje que el árbol).
        var r = servicio.resuelve("madrid-hosteleria", "cocinero",
                java.util.Map.of("nivel", "I")).orElseThrow();

        assertThat(r.dimensiones()).containsEntry("nivel", "III");
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
    @DisplayName("#233 una respuesta inventada en el árbol ya no se ignora en silencio: 422 con las opciones reales")
    void condicionalRespuestaInventada() {
        // Antes se re-preguntaba sin decir que el valor se había descartado:
        // cualquier desajuste lista↔motor se volvía invisible. Sigue sin
        // inventarse nada — pero ahora se dice alto y claro qué opciones hay.
        assertThatThrownBy(() -> servicio.resuelve("jaen-hosteleria", "cocinero",
                java.util.Map.of("establecimiento", "castillo-inventado")))
                .isInstanceOf(DimensionDesconocidaException.class)
                .hasMessageContaining("castillo-inventado")
                .hasMessageContaining("establecimiento")
                .hasMessageContaining("hoteles");
    }

    @Test
    @DisplayName("#233 árbol encadenado: la categoría inventada TRAS un tipo válido también es 422")
    void condicionalCategoriaInventada() {
        assertThatThrownBy(() -> servicio.resuelve("jaen-hosteleria", "cocinero",
                java.util.Map.of("establecimiento", "hoteles", "categoria", "8*")))
                .isInstanceOf(DimensionDesconocidaException.class)
                .hasMessageContaining("categoria")
                .hasMessageContaining("8*");
    }

    @Test
    @DisplayName("#233 mapeo directo: un valor no reconocido responde 422 con los valores publicados, no se ignora")
    void directoValorNoReconocido() {
        assertThatThrownBy(() -> servicio.resuelve("madrid-hosteleria", "cocinero",
                java.util.Map.of("claseEmpresa", "Z")))
                .isInstanceOf(DimensionDesconocidaException.class)
                .hasMessageContaining("'Z'")
                .hasMessageContaining("claseEmpresa")
                .hasMessageContaining("[A, B, C]");
    }

    @Test
    @DisplayName("#233 dos valores válidos por separado cuya combinación no publica tabla → 422, no un 'sin tabla' mudo")
    void combinacionNoPublicada() {
        // Tenerife: grupoEstablecimiento 'A' existe (clasificación 1) y la
        // clasificación '2' existe (indexa por establecimiento), pero juntos no
        // corresponden a ninguna tabla. Callar aquí era el "sin tabla aplicable"
        // falso del issue.
        // El 422 además ORIENTA (issue #233, review): dice por qué combinaciones
        // de dimensiones se indexan las tablas, para que un cliente directo de la
        // API sepa qué respuestas encajan juntas en vez de recibir un "no cuadra"
        // opaco. Nombra la dimensión que de verdad discrimina para clasificación 2.
        assertThatThrownBy(() -> servicio.resuelve("tenerife-hosteleria", "cocinero",
                java.util.Map.of("clasificacion", "2", "grupoEstablecimiento", "A")))
                .isInstanceOf(DimensionDesconocidaException.class)
                .hasMessageContaining("combinación")
                .hasMessageContaining("se indexan por")
                .hasMessageContaining("establecimiento");
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

    // --- Preguntas encadenadas desde las combinaciones REALES de hechos (#233) ---
    // Tenerife tiene dos formas de tabla alternativas: clasificaciones 1/3/4 van
    // por grupoEstablecimiento y la clasificación 2 (apartamentos, campings,
    // vivienda vacacional) por establecimiento. Ofrecer las tres preguntas a la
    // vez producía combinaciones que no existen en ninguna tabla ("sin tabla
    // aplicable" siendo falso): las preguntas salen de los hechos compatibles
    // y se encadenan de una en una.

    @Test
    @DisplayName("#233 Tenerife cocinero: solo se ofrece la clasificación (el resto depende de ella)")
    void tenerifePreguntaPrimeroLaClasificacion() {
        var r = servicio.resuelve("tenerife-hosteleria", "cocinero").orElseThrow();

        assertThat(r.pendientes()).hasSize(1);
        assertThat(r.pendientes().getFirst().dimension()).isEqualTo("clasificacion");
        assertThat(r.pendientes().getFirst().valores()).containsExactly("1", "2", "3", "4");
    }

    @Test
    @DisplayName("#233 Tenerife clasificación 2: encadena el tipo de alojamiento (nunca el grupo)")
    void tenerifeClasificacionDosEncadenaAlojamiento() {
        var r = servicio.resuelve("tenerife-hosteleria", "cocinero",
                java.util.Map.of("clasificacion", "2")).orElseThrow();

        assertThat(r.pendientes()).hasSize(1);
        assertThat(r.pendientes().getFirst().dimension()).isEqualTo("establecimiento");
        assertThat(r.pendientes().getFirst().valores()).contains("Aptos 3*", "Vivienda Vacacional");
    }

    @Test
    @DisplayName("#233 Tenerife clasificación 1: encadena el grupo de establecimiento con SUS grupos (A-D)")
    void tenerifeClasificacionUnoEncadenaGrupo() {
        var r = servicio.resuelve("tenerife-hosteleria", "cocinero",
                java.util.Map.of("clasificacion", "1")).orElseThrow();

        assertThat(r.pendientes()).hasSize(1);
        assertThat(r.pendientes().getFirst().dimension()).isEqualTo("grupoEstablecimiento");
        assertThat(r.pendientes().getFirst().valores()).containsExactly("A", "B", "C", "D");
    }

    @Test
    @DisplayName("#233 Tenerife: contestando lo que se ofrece, la resolución queda completa (la tabla existe)")
    void tenerifeRespondiendoLoOfrecidoResuelve() {
        var r = servicio.resuelve("tenerife-hosteleria", "cocinero", java.util.Map.of(
                "clasificacion", "2", "establecimiento", "Aptos 3*")).orElseThrow();

        assertThat(r.pendientes()).isEmpty();
        assertThat(r.dimensiones())
                .containsEntry("clasificacion", "2")
                .containsEntry("establecimiento", "Aptos 3*")
                .containsEntry("puesto", "Cocinero/a")
                .containsKey("areaFuncional");
    }

    // --- Colectiva: la CATEGORÍA depende de la PROVINCIA (condicionalPorProvincia) ---

    @Test
    @DisplayName("colectiva cocinero: sin respuestas pide la provincia (solo se ofrecen las mapeadas)")
    void colectivaPrimeraPreguntaProvincia() {
        var r = servicio.resuelve("estatal-restauracion-colectiva", "cocinero").orElseThrow();

        assertThat(r.dimensiones()).isEmpty();
        assertThat(r.pendientes()).hasSize(1);
        assertThat(r.pendientes().getFirst().dimension()).isEqualTo("provincia");
        assertThat(r.pendientes().getFirst().valores()).contains("Caceres");
    }

    @Test
    @DisplayName("colectiva cocinero en Cáceres: la provincia resuelve la categoría agrupada del anexo y no quedan preguntas")
    void colectivaProvinciaResuelveCategoria() {
        var r = servicio.resuelve("estatal-restauracion-colectiva", "cocinero",
                java.util.Map.of("provincia", "Caceres")).orElseThrow();

        assertThat(r.dimensiones())
                .containsEntry("provincia", "Caceres")
                .containsEntry("categoria",
                        "Cocinero / Camarero / Especialista de mantenimiento y servicios auxiliares");
        assertThat(r.pendientes()).isEmpty();
    }

    @Test
    @DisplayName("#233 colectiva: una provincia inventada ya no repite la pregunta en silencio — 422 con las opciones")
    void colectivaProvinciaInventada() {
        assertThatThrownBy(() -> servicio.resuelve("estatal-restauracion-colectiva", "cocinero",
                java.util.Map.of("provincia", "Atlantida")))
                .isInstanceOf(DimensionDesconocidaException.class)
                .hasMessageContaining("Atlantida")
                .hasMessageContaining("provincia")
                .hasMessageContaining("Zaragoza");
    }

    @Test
    @DisplayName("#233 colectiva: la provincia con la grafía del perfil (Cáceres, A Coruña) casa con su anexo")
    void colectivaProvinciaConGrafiaDelPerfil() {
        // El perfil usa el vocabulario del ámbito territorial (con acentos);
        // los anexos de la colectiva publican el suyo (sin ellos). La MISMA
        // provincia no puede fallar por la tilde — y la dimensión promocionada
        // debe llevar la grafía CANÓNICA del anexo, que es la del lookup exacto.
        var caceres = servicio.resuelve("estatal-restauracion-colectiva", "cocinero",
                java.util.Map.of("provincia", "Cáceres")).orElseThrow();
        assertThat(caceres.dimensiones())
                .containsEntry("provincia", "Caceres")
                .containsEntry("categoria",
                        "Cocinero / Camarero / Especialista de mantenimiento y servicios auxiliares");
        assertThat(caceres.pendientes()).isEmpty();

        var coruna = servicio.resuelve("estatal-restauracion-colectiva", "cocinero",
                java.util.Map.of("provincia", "A Coruña")).orElseThrow();
        assertThat(coruna.dimensiones()).containsEntry("provincia", "A Coruna");
    }

    @Test
    @DisplayName("#233 colectiva: los nombres cooficiales del perfil (Bizkaia, Gipuzkoa, Illes Balears) casan con su anexo")
    void colectivaNombresCooficiales() {
        var bizkaia = servicio.resuelve("estatal-restauracion-colectiva", "cocinero",
                java.util.Map.of("provincia", "Bizkaia")).orElseThrow();
        assertThat(bizkaia.dimensiones()).containsEntry("provincia", "Vizcaya");

        var gipuzkoa = servicio.resuelve("estatal-restauracion-colectiva", "cocinero",
                java.util.Map.of("provincia", "Gipuzkoa")).orElseThrow();
        assertThat(gipuzkoa.dimensiones()).containsEntry("provincia", "Guipuzcoa");

        var balears = servicio.resuelve("estatal-restauracion-colectiva", "cocinero",
                java.util.Map.of("provincia", "Illes Balears")).orElseThrow();
        assertThat(balears.dimensiones()).containsEntry("provincia", "Islas Baleares");
    }

    @Test
    @DisplayName("#233 colectiva: una provincia real SIN este puesto mapeado (Alicante) también avisa con 422, no calla")
    void colectivaProvinciaSinPuestoMapeado() {
        // El anexo de Alicante publica niveles sin nombrar ocupaciones: el
        // cocinero está en null (podado). La respuesta debe decirlo, no
        // devolver lo mismo que si no se hubiera contestado nada.
        assertThatThrownBy(() -> servicio.resuelve("estatal-restauracion-colectiva", "cocinero",
                java.util.Map.of("provincia", "Alicante")))
                .isInstanceOf(DimensionDesconocidaException.class)
                .hasMessageContaining("Alicante");
    }

    @Test
    @DisplayName("SEGURIDAD: una 'categoria' inyectada por el cliente NO pisa la que resuelve el árbol de provincia")
    void colectivaCategoriaInyectadaNoManda() {
        // El árbol fija la categoría de Cáceres. El cliente intenta colar otra fila
        // salarial vía query param. El árbol es autoritativo (mismo blindaje que el nivel).
        var conInyeccion = servicio.resuelve("estatal-restauracion-colectiva", "cocinero",
                java.util.Map.of("provincia", "Caceres",
                        "categoria", "Jefe de recepción / Jefe de cocina / Jefe de restaurante o sala / Gobernante o Encargado general / Responsable de servicio"))
                .orElseThrow();

        assertThat(conInyeccion.dimensiones().get("categoria"))
                .isEqualTo("Cocinero / Camarero / Especialista de mantenimiento y servicios auxiliares");
    }

    @Test
    @DisplayName("condicional: al responder una dimensión de tabla no-raíz (categoría en Cataluña) se pliega y deja de preguntarse")
    void condicionalPliegaDimensionDeTablaRespondida() {
        // La categoría del establecimiento NO es la raíz del árbol (que es la zona),
        // pero SÍ es dimensión de tabla. Tras resolver el nivel desde la zona, sale
        // como pendiente; al responderla debe plegarse. Si no se plegara, se volvería
        // a preguntar en bucle (el bug que cazó el barrido de cobertura).
        var inicial = servicio.resuelve("cataluna-hosteleria", "camarero",
                java.util.Map.of("zona", "barcelona")).orElseThrow();
        var pendienteCategoria = inicial.pendientes().stream()
                .filter(p -> p.dimension().equals("categoriaEstablecimiento"))
                .findFirst().orElseThrow();
        String valorValido = pendienteCategoria.valores().getFirst();

        var r = servicio.resuelve("cataluna-hosteleria", "camarero", java.util.Map.of(
                "zona", "barcelona", "categoriaEstablecimiento", valorValido)).orElseThrow();

        assertThat(r.dimensiones())
                .containsEntry("zona", "barcelona")
                .containsEntry("categoriaEstablecimiento", valorValido)
                .containsKey("nivel");
        assertThat(r.pendientes()).noneMatch(p -> p.dimension().equals("categoriaEstablecimiento"));
    }
}
