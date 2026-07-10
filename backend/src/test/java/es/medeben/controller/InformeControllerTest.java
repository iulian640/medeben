package es.medeben.controller;

import es.medeben.config.SecurityConfig;
import es.medeben.service.InformeMensualService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.YearMonth;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InformeController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class InformeControllerTest {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final byte[] PDF = {'%', 'P', 'D', 'F'};

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InformeMensualService informes;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor comoUsuario() {
        return jwt().jwt(j -> j.subject(USUARIO.toString()));
    }

    @Test
    @DisplayName("sin token → 401 (el informe lleva datos personales)")
    void sinToken() throws Exception {
        mockMvc.perform(get("/api/v1/informes/mes/2026-07"))
                .andExpect(status().isUnauthorized());

        verify(informes, never()).genera(any(), any());
    }

    @Test
    @DisplayName("GET con token → PDF adjunto con nombre limpio y sin caché compartida")
    void descargaPdf() throws Exception {
        when(informes.genera(eq(USUARIO), eq(YearMonth.of(2026, 7)))).thenReturn(PDF);

        mockMvc.perform(get("/api/v1/informes/mes/2026-07").with(comoUsuario()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Content-Disposition",
                        containsString("attachment; filename=\"medeben-informe-2026-07.pdf\"")))
                .andExpect(content().bytes(PDF));
    }

    @Test
    @DisplayName("mes inválido → 400 RFC 7807 sin ecoar el input y sin tocar el servicio")
    void mesInvalido() throws Exception {
        mockMvc.perform(get("/api/v1/informes/mes/patata9").with(comoUsuario()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail", containsString("yyyy-MM")));

        verify(informes, never()).genera(any(), any());
    }
}
