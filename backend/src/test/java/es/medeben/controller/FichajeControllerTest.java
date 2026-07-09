package es.medeben.controller;

import es.medeben.config.SecurityConfig;
import es.medeben.domain.fichaje.Apunte;
import es.medeben.domain.fichaje.EstadoDia;
import es.medeben.domain.fichaje.OrigenApunte;
import es.medeben.domain.fichaje.TipoApunte;
import es.medeben.service.DiaSelladoException;
import es.medeben.service.FichajeService;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FichajeController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class FichajeControllerTest {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final LocalDate FECHA = LocalDate.of(2026, 7, 8);
    private static final OffsetDateTime SELLO = OffsetDateTime.parse("2026-07-08T23:47:12+02:00");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FichajeService fichajeService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor comoUsuario() {
        return jwt().jwt(j -> j.subject(USUARIO.toString()));
    }

    @Test
    @DisplayName("sin token → 401 (el diario es privado)")
    void sinToken() throws Exception {
        mockMvc.perform(get("/api/v1/fichajes/dia/2026-07-08")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST ficha del usuario del token y devuelve el apunte con su origen y sello")
    void ficha() throws Exception {
        when(fichajeService.apunta(eq(USUARIO), eq(FECHA), eq(TipoApunte.SALIDA), eq("23:45"), any(), anyBoolean()))
                .thenReturn(new Apunte(USUARIO, FECHA, TipoApunte.SALIDA, "23:45", null,
                        OrigenApunte.CONFIRMADO, SELLO));

        mockMvc.perform(post("/api/v1/fichajes").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fecha":"2026-07-08","tipo":"SALIDA","hora":"23:45"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origen").value("CONFIRMADO"))
                .andExpect(jsonPath("$.registradoEn").exists());
    }

    @Test
    @DisplayName("fichar un día sellado sin confirmación → 409 con explicación")
    void diaSellado() throws Exception {
        when(fichajeService.apunta(eq(USUARIO), any(), any(), any(), any(), anyBoolean()))
                .thenThrow(new DiaSelladoException(LocalDate.of(2026, 6, 10)));

        mockMvc.perform(post("/api/v1/fichajes").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fecha":"2026-06-10","tipo":"SALIDA","hora":"23:00"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    @DisplayName("POST sin fecha o con tipo desconocido → 400")
    void validacion() throws Exception {
        mockMvc.perform(post("/api/v1/fichajes").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"SALIDA","hora":"23:00"}"""))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/fichajes").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fecha":"2026-07-08","tipo":"SIESTA","hora":"16:00"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET del estado de un día: estado, sellado, contador de sellado y diario completo")
    void estadoDia() throws Exception {
        Apunte entrada = new Apunte(USUARIO, FECHA, TipoApunte.ENTRADA, "12:00", null,
                OrigenApunte.CONFIRMADO, SELLO);
        when(fichajeService.estadoDia(USUARIO, FECHA)).thenReturn(new EstadoDia(
                FECHA, EstadoDia.Estado.EN_CURSO, false, FECHA.plusDays(15), -1, List.of(entrada)));

        mockMvc.perform(get("/api/v1/fichajes/dia/2026-07-08").with(comoUsuario()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_CURSO"))
                .andExpect(jsonPath("$.sellado").value(false))
                .andExpect(jsonPath("$.selladoDesde").value("2026-07-23"))
                .andExpect(jsonPath("$.apuntes[0].tipo").value("ENTRADA"));
    }
}
