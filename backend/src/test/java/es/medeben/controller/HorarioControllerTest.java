package es.medeben.controller;

import es.medeben.config.SecurityConfig;
import es.medeben.domain.horario.Cuadrante;
import es.medeben.domain.horario.DiaCuadrante;
import es.medeben.domain.horario.OrigenHorario;
import es.medeben.domain.horario.Tramo;
import es.medeben.service.HorarioEfectivo;
import es.medeben.service.HorarioService;
import es.medeben.service.SemanaSelladaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HorarioController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class HorarioControllerTest {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final LocalDate LUNES = LocalDate.of(2026, 7, 6);
    private static final java.time.OffsetDateTime SELLO = java.time.OffsetDateTime.parse("2026-07-08T12:00:00+02:00");

    private static final String SEMANA_JSON = """
            {"dias":[
              {"tramos":[{"entrada":"12:00","salida":"16:00"},{"entrada":"20:00","salida":"00:30"}]},
              {"tramos":[{"entrada":"09:00","salida":"17:00"}]},
              {"tramos":[{"entrada":"09:00","salida":"17:00"}]},
              {"tramos":[{"entrada":"09:00","salida":"17:00"}]},
              {"tramos":[{"entrada":"09:00","salida":"17:00"}]},
              {"tramos":[{"entrada":"09:00","salida":"17:00"}]},
              {"tramos":[]}
            ]}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HorarioService horarioService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor comoUsuario() {
        return jwt().jwt(j -> j.subject(USUARIO.toString()));
    }

    private static List<DiaCuadrante> semanaDominio() {
        DiaCuadrante seguido = new DiaCuadrante(List.of(new Tramo("09:00", "17:00")));
        return List.of(seguido, seguido, seguido, seguido, seguido, seguido, new DiaCuadrante(List.of()));
    }

    @Test
    @DisplayName("sin token → 401 (el horario es privado)")
    void sinToken() throws Exception {
        mockMvc.perform(get("/api/v1/horario")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET sin semana tipo creada → 404 amable")
    void sinSemanaTipo() throws Exception {
        when(horarioService.semanaTipoActual(USUARIO)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/v1/horario").with(comoUsuario()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT guarda la semana tipo del usuario del token")
    void guardaSemanaTipo() throws Exception {
        when(horarioService.guardaSemanaTipo(eq(USUARIO), any()))
                .thenReturn(new Cuadrante(USUARIO, null, semanaDominio(), SELLO));

        mockMvc.perform(put("/api/v1/horario").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON).content(SEMANA_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias[0].tramos[0].entrada").value("09:00"));
    }

    @Test
    @DisplayName("GET con semana tipo → 200")
    void conSemanaTipo() throws Exception {
        when(horarioService.semanaTipoActual(USUARIO))
                .thenReturn(Optional.of(new Cuadrante(USUARIO, null, semanaDominio(), SELLO)));

        mockMvc.perform(get("/api/v1/horario").with(comoUsuario()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias.length()").value(7));
    }

    @Test
    @DisplayName("PUT de una semana concreta ancla la edición a su lunes")
    void editaSemana() throws Exception {
        when(horarioService.guardaSemana(eq(USUARIO), eq(LUNES), any()))
                .thenReturn(new Cuadrante(USUARIO, LUNES, semanaDominio(), SELLO));

        mockMvc.perform(put("/api/v1/horario/semana/2026-07-06").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON).content(SEMANA_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semanaInicio").value("2026-07-06"));
    }

    @Test
    @DisplayName("GET del horario efectivo de una semana dice de dónde sale (origen)")
    void horarioEfectivo() throws Exception {
        when(horarioService.horarioEfectivo(USUARIO, LUNES))
                .thenReturn(Optional.of(new HorarioEfectivo(semanaDominio(), OrigenHorario.SEMANA_TIPO, null)));

        mockMvc.perform(get("/api/v1/horario/semana/2026-07-06").with(comoUsuario()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origen").value("SEMANA_TIPO"));
    }

    @Test
    @DisplayName("editar una semana sellada → 409 con explicación (D38)")
    void semanaSellada() throws Exception {
        when(horarioService.guardaSemana(eq(USUARIO), any(), any()))
                .thenThrow(new SemanaSelladaException(LocalDate.of(2026, 6, 8)));

        mockMvc.perform(put("/api/v1/horario/semana/2026-06-08").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON).content(SEMANA_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    @DisplayName("fecha rota en la URL → 400, no 500 (review M2)")
    void fechaRota() throws Exception {
        mockMvc.perform(get("/api/v1/horario/semana/no-es-una-fecha").with(comoUsuario()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT con 6 días → 400 (validación del DTO)")
    void seisDias() throws Exception {
        mockMvc.perform(put("/api/v1/horario").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dias":[{"tramos":[]},{"tramos":[]},{"tramos":[]},{"tramos":[]},{"tramos":[]},{"tramos":[]}]}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT con hora en formato raro → 400 (validación del DTO)")
    void horaRara() throws Exception {
        mockMvc.perform(put("/api/v1/horario").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dias":[{"tramos":[{"entrada":"9h","salida":"17:00"}]},{"tramos":[]},{"tramos":[]},{"tramos":[]},{"tramos":[]},{"tramos":[]},{"tramos":[]}]}"""))
                .andExpect(status().isBadRequest());
    }
}
