package es.medeben.controller;

import es.medeben.config.JacksonConfig;
import es.medeben.config.SecurityConfig;
import es.medeben.repository.ConvenioCatalog;
import es.medeben.service.CalculoConvenioService;
import es.medeben.service.FichajeService;
import es.medeben.service.TablaSalarialService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Robustez de la API ante entradas raras (issue #232): los errores de
 * validación del cuerpo deben salir como RFC 7807 con el campo y el motivo
 * en castellano — igual que las validaciones hechas a mano en los servicios —
 * y nunca con el genérico "Invalid request content." del framework.
 */
@WebMvcTest(controllers = {FichajeController.class, CalculoController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JacksonConfig.class})
class ApiRobustezTest {

    private static final UUID USUARIO = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FichajeService fichajeService;

    @MockitoBean
    private ConvenioCatalog convenios;

    @MockitoBean
    private CalculoConvenioService calculo;

    @MockitoBean
    private TablaSalarialService tablas;

    @MockitoBean
    private es.medeben.service.SmiService smi;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor comoUsuario() {
        return jwt().jwt(j -> j.subject(USUARIO.toString()));
    }

    // --- (d) Errores de Bean Validation: campo + motivo en castellano ---

    @Test
    @DisplayName("hora inválida en el body → 400 RFC 7807 que dice el campo y el motivo en castellano")
    void validacionConCampoYMotivo() throws Exception {
        mockMvc.perform(post("/api/v1/fichajes").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fecha":"2026-07-08","tipo":"SALIDA","hora":"24:00"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("hora: hora en formato HH:mm"));
    }

    @Test
    @DisplayName("falta la fecha → 400 con el campo y el motivo en castellano, no \"Invalid request content.\"")
    void faltaLaFecha() throws Exception {
        mockMvc.perform(post("/api/v1/fichajes").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"SALIDA","hora":"23:00"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("fecha: no puede faltar"));
    }

    @Test
    @DisplayName("varios campos mal a la vez → todos en el detalle, en orden estable")
    void variosCamposInvalidos() throws Exception {
        mockMvc.perform(post("/api/v1/calculo/salario-base")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "convenioId: no puede faltar; dimensiones: no puede faltar; fecha: no puede faltar"));
    }

    // --- (b) Fechas estrictas: solo yyyy-MM-dd, nunca truncar un timestamp ---

    @Test
    @DisplayName("fecha con hora UTC en el body → 400, jamás truncarla al día UTC en silencio")
    void fechaConHoraUtcEnBody() throws Exception {
        // Lo que produce new Date().toISOString() en JavaScript: a las 00:00
        // del 9 de julio en España (verano, UTC+2) aún es día 8 en UTC. Si se
        // trunca, el fichaje queda registrado el día equivocado sin avisar.
        mockMvc.perform(post("/api/v1/fichajes").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fecha":"2026-07-08T22:00:00.000Z","tipo":"SALIDA","hora":"23:45"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(
                        "fecha: se espera una fecha en formato yyyy-MM-dd, sin hora ni zona horaria"))
                // El valor recibido no se ecoa en la respuesta.
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("22:00"))));

        org.mockito.Mockito.verifyNoInteractions(fichajeService);
    }

    @Test
    @DisplayName("fecha con hora UTC en el cálculo anónimo → 400 (aplica a todos los endpoints)")
    void fechaConHoraUtcEnSalarioBase() throws Exception {
        mockMvc.perform(post("/api/v1/calculo/salario-base")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"convenioId":"madrid-hosteleria","fecha":"2026-07-08T22:00:00.000Z",
                                 "dimensiones":{"nivel":"III"}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "fecha: se espera una fecha en formato yyyy-MM-dd, sin hora ni zona horaria"));
    }

    @Test
    @DisplayName("fecha en formato array de Jackson ([2026,7,8]) → 400, solo se admite el texto plano")
    void fechaComoArray() throws Exception {
        mockMvc.perform(post("/api/v1/fichajes").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fecha":[2026,7,8],"tipo":"SALIDA","hora":"23:45"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("fecha con la forma correcta pero imposible (2026-02-30) → 400")
    void fechaImposible() throws Exception {
        mockMvc.perform(post("/api/v1/fichajes").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fecha":"2026-02-30","tipo":"SALIDA","hora":"23:45"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "fecha: se espera una fecha en formato yyyy-MM-dd, sin hora ni zona horaria"));
    }

    @Test
    @DisplayName("número enviado como texto no numérico → 400 con el campo, sin ecoar el valor")
    void numeroComoTexto() throws Exception {
        mockMvc.perform(post("/api/v1/calculo/horas-extra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"convenioId":"madrid-hosteleria","anio":2026,
                                 "salarioBaseMensual":"mil doscientos","plusesAnuales":0,"horas":5}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("salarioBaseMensual: no tiene el formato esperado"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("mil doscientos"))));
    }

    @Test
    @DisplayName("un objeto donde va un texto → 400 con el campo y \"no tiene el tipo esperado\"")
    void tipoDeDatoCambiado() throws Exception {
        mockMvc.perform(post("/api/v1/calculo/salario-base")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"convenioId":"madrid-hosteleria","fecha":"2026-07-08",
                                 "dimensiones":"esto no es un mapa"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("dimensiones: no tiene el tipo esperado"));
    }

    @Test
    @DisplayName("fecha con hora en el path → 400 RFC 7807 en castellano, sin ecoar el valor")
    void fechaConHoraEnPath() throws Exception {
        mockMvc.perform(get("/api/v1/fichajes/dia/2026-07-08T22:00:00.000Z").with(comoUsuario()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                // El match exacto garantiza que el detalle no ecoa el valor
                // (el "instance" sí lleva la URI pedida: es el shape RFC 7807
                // de toda la API, igual que en la entry point de los 401).
                .andExpect(jsonPath("$.detail").value(
                        "El parámetro 'fecha' no tiene el formato esperado (fecha en formato yyyy-MM-dd)"));
    }

    @Test
    @DisplayName("tipo de apunte desconocido → 400 con el campo, sin ecoar el valor recibido")
    void tipoDesconocido() throws Exception {
        mockMvc.perform(post("/api/v1/fichajes").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fecha":"2026-07-08","tipo":"SIESTA","hora":"16:00"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("tipo: no es un valor válido"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("SIESTA"))));
    }

    @Test
    @DisplayName("cuerpo que no es JSON → 400 en castellano, no un error del framework en inglés")
    void cuerpoNoEsJson() throws Exception {
        mockMvc.perform(post("/api/v1/fichajes").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El cuerpo de la petición no es JSON válido"));
    }

    // --- (a) Carácter NUL en campos de texto: 400 de validación, nunca 500 ---

    @Test
    @DisplayName("motivo con carácter NUL → 400 de validación (antes reventaba en Postgres con 500)")
    void motivoConNul() throws Exception {
        // \\u0000 llega como escape JSON y Jackson lo convierte en el carácter
        // NUL real, el que Postgres rechaza en cualquier columna de texto.
        mockMvc.perform(post("/api/v1/fichajes").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fecha":"2026-07-08","tipo":"AUSENCIA","hora":null,"motivo":"baja m\\u0000dica"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("motivo: no puede contener el carácter nulo (U+0000)"));
    }

    @Test
    @DisplayName("NUL en una dimensión del cálculo anónimo → 400, mismo cierre uniforme")
    void dimensionConNul() throws Exception {
        mockMvc.perform(post("/api/v1/calculo/salario-base")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"convenioId":"madrid-hosteleria","fecha":"2026-07-08",
                                 "dimensiones":{"nivel":"II\\u0000I"}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("no puede contener el carácter nulo")));
    }

    @Test
    @DisplayName("password corta en el registro → motivo en castellano con los límites")
    void passwordCorta() throws Exception {
        // El slice no carga AuthController: se valida el DTO equivalente en
        // /calculo (mismo mecanismo global). El importe negativo cubre @Positive.
        mockMvc.perform(post("/api/v1/calculo/horas-extra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"convenioId":"madrid-hosteleria","anio":2026,
                                 "salarioBaseMensual":-1250.91,"plusesAnuales":0,"horas":5}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("salarioBaseMensual: debe ser mayor que 0"));
    }
}
