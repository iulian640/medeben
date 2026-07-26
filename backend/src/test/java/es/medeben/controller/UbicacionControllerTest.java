package es.medeben.controller;

import es.medeben.config.SecurityConfig;
import es.medeben.domain.fichaje.CentroTrabajo;
import es.medeben.domain.fichaje.UbicacionApunte;
import es.medeben.domain.fichaje.VeredictoUbicacion;
import es.medeben.domain.usuario.ConsentimientoUbicacion;
import es.medeben.service.CentroTrabajoService;
import es.medeben.service.ConsentimientoUbicacionRequeridoException;
import es.medeben.service.ConsentimientoUbicacionService;
import es.medeben.service.ReclamacionEnCursoService;
import es.medeben.service.UbicacionService;
import es.medeben.service.UbicacionYaRegistradaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Plantilla de {@code FichajeControllerTest}: {@code @RequiereBaseDeDatos}
 * (implícito vía Import de SecurityConfig+GlobalExceptionHandler, mismo
 * montaje), sin BD real.
 */
@WebMvcTest(UbicacionController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class UbicacionControllerTest {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final UUID OTRO_USUARIO = UUID.randomUUID();
    private static final UUID APUNTE_ID = UUID.randomUUID();
    private static final BigDecimal LAT = new BigDecimal("40.41675");
    private static final BigDecimal LON = new BigDecimal("-3.70379");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UbicacionService ubicaciones;

    @MockitoBean
    private CentroTrabajoService centros;

    @MockitoBean
    private ConsentimientoUbicacionService consentimientos;

    @MockitoBean
    private ReclamacionEnCursoService reclamaciones;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor comoUsuario() {
        return jwt().jwt(j -> j.subject(USUARIO.toString()));
    }

    private static UbicacionApunte ubicacionEjemplo() {
        return new UbicacionApunte(APUNTE_ID, USUARIO, LocalDate.of(2026, 7, 26), LAT, LON, 20,
                UUID.randomUUID(), LAT, LON, 150, 30, VeredictoUbicacion.DENTRO,
                OffsetDateTime.parse("2026-07-26T10:03:00+02:00"));
    }

    // --- Consentimiento ---

    @Test
    @DisplayName("sin token → 401 en todos los endpoints de ubicación")
    void sinToken() throws Exception {
        mockMvc.perform(post("/api/v1/ubicacion/consentimiento")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"versionTexto":"1.0"}"""))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/centro-trabajo")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/fichajes/" + APUNTE_ID + "/ubicacion")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"latitud":40.41675,"longitud":-3.70379,"precisionMetros":20}"""))
                .andExpect(status().isUnauthorized());

        verify(ubicaciones, never()).adjunta(any(), any(), any(), any(), anyInt());
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }

    @Test
    @DisplayName("POST consentimiento acepta la versión del texto — 201")
    void aceptaConsentimiento() throws Exception {
        when(consentimientos.acepta(eq(USUARIO), eq("1.0"))).thenReturn(
                new ConsentimientoUbicacion(USUARIO, "1.0", "hash", OffsetDateTime.now()));

        mockMvc.perform(post("/api/v1/ubicacion/consentimiento").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"versionTexto":"1.0"}"""))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("DELETE consentimiento revoca — 204")
    void revocaConsentimiento() throws Exception {
        mockMvc.perform(delete("/api/v1/ubicacion/consentimiento").with(comoUsuario()))
                .andExpect(status().isNoContent());

        verify(consentimientos).revoca(USUARIO);
    }

    // --- Centro de trabajo ---

    @Test
    @DisplayName("PUT centro-trabajo declara y la respuesta NO incluye coordenadas")
    void declaraCentroSinCoordenadasEnLaRespuesta() throws Exception {
        CentroTrabajo centro = new CentroTrabajo(USUARIO, "El bar", LAT, LON, 150,
                OffsetDateTime.parse("2026-07-26T09:00:00+02:00"));
        when(centros.declara(eq(USUARIO), eq("El bar"), eq(LAT), eq(LON))).thenReturn(centro);

        mockMvc.perform(put("/api/v1/centro-trabajo").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"latitud":40.41675,"longitud":-3.70379,"alias":"El bar"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(centro.getId().toString()))
                .andExpect(jsonPath("$.alias").value("El bar"))
                .andExpect(jsonPath("$.radioMetros").value(150))
                .andExpect(jsonPath("$.latitud").doesNotExist())
                .andExpect(jsonPath("$.longitud").doesNotExist());
    }

    @Test
    @DisplayName("GET centro-trabajo incluye el id — sin él, DELETE .../{id}?purgar=true es inalcanzable")
    void centroVigenteIncluyeElId() throws Exception {
        CentroTrabajo centro = new CentroTrabajo(USUARIO, "El bar", LAT, LON, 150,
                OffsetDateTime.parse("2026-07-26T09:00:00+02:00"));
        when(centros.vigente(USUARIO)).thenReturn(Optional.of(centro));

        mockMvc.perform(get("/api/v1/centro-trabajo").with(comoUsuario()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(centro.getId().toString()));
    }

    @Test
    @DisplayName("declarar sin consentimiento vigente → 403")
    void declararSinConsentimientoDa403() throws Exception {
        when(centros.declara(any(), any(), any(), any()))
                .thenThrow(new ConsentimientoUbicacionRequeridoException());

        mockMvc.perform(put("/api/v1/centro-trabajo").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"latitud":40.41675,"longitud":-3.70379}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET centro-trabajo sin centro declarado → 404")
    void centroTrabajoSinDeclararDa404() throws Exception {
        when(centros.vigente(USUARIO)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/centro-trabajo").with(comoUsuario()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE centro-trabajo cierra el vigente — 204")
    void cierraCentro() throws Exception {
        mockMvc.perform(delete("/api/v1/centro-trabajo").with(comoUsuario()))
                .andExpect(status().isNoContent());

        verify(centros).cierra(USUARIO);
    }

    @Test
    @DisplayName("DELETE centro-trabajo/{id} sin ?purgar=true se rechaza con 400")
    void purgarSinConfirmarSeRechaza() throws Exception {
        UUID centroId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/centro-trabajo/" + centroId).with(comoUsuario()))
                .andExpect(status().isBadRequest());

        verify(centros, never()).purga(any(), any());
    }

    @Test
    @DisplayName("DELETE centro-trabajo/{id}?purgar=true purga — 204")
    void purgarConConfirmacion() throws Exception {
        UUID centroId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/centro-trabajo/" + centroId + "?purgar=true").with(comoUsuario()))
                .andExpect(status().isNoContent());

        verify(centros).purga(USUARIO, centroId);
    }

    // --- Adjuntar ubicación a un fichaje ---

    @Test
    @DisplayName("el usuarioId sale del JWT aunque el cliente no lo mande en ningún sitio del body")
    void elUsuarioIdSaleDelJwt() throws Exception {
        when(ubicaciones.adjunta(eq(USUARIO), eq(APUNTE_ID), eq(LAT), eq(LON), eq(20)))
                .thenReturn(ubicacionEjemplo());

        mockMvc.perform(post("/api/v1/fichajes/" + APUNTE_ID + "/ubicacion").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"latitud":40.41675,"longitud":-3.70379,"precisionMetros":20}"""))
                .andExpect(status().isCreated());

        verify(ubicaciones).adjunta(USUARIO, APUNTE_ID, LAT, LON, 20);
    }

    @Test
    @DisplayName("la respuesta de adjuntar NO incluye coordenadas — test de minimización")
    void laRespuestaNoIncluyeCoordenadas() throws Exception {
        when(ubicaciones.adjunta(any(), any(), any(), any(), anyInt())).thenReturn(ubicacionEjemplo());

        mockMvc.perform(post("/api/v1/fichajes/" + APUNTE_ID + "/ubicacion").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"latitud":40.41675,"longitud":-3.70379,"precisionMetros":20}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.veredicto").value("DENTRO"))
                .andExpect(jsonPath("$.latitud").doesNotExist())
                .andExpect(jsonPath("$.longitud").doesNotExist())
                .andExpect(jsonPath("$.latitude").doesNotExist());
    }

    @Test
    @DisplayName("sin consentimiento vigente → 403 explícito, nunca 204 mudo")
    void adjuntarSinConsentimientoDa403() throws Exception {
        when(ubicaciones.adjunta(any(), any(), any(), any(), anyInt()))
                .thenThrow(new ConsentimientoUbicacionRequeridoException());

        mockMvc.perform(post("/api/v1/fichajes/" + APUNTE_ID + "/ubicacion").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"latitud":40.41675,"longitud":-3.70379,"precisionMetros":20}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("una segunda ubicación sobre el mismo apunte → 409")
    void segundaUbicacionDa409() throws Exception {
        when(ubicaciones.adjunta(any(), any(), any(), any(), anyInt()))
                .thenThrow(new UbicacionYaRegistradaException());

        mockMvc.perform(post("/api/v1/fichajes/" + APUNTE_ID + "/ubicacion").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"latitud":40.41675,"longitud":-3.70379,"precisionMetros":20}"""))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("otro usuario no puede leer ni borrar mis ubicaciones — el servicio ya lo blinda, el controller no filtra")
    void otroUsuarioNoPuedeLeerNiBorrarMisUbicaciones() throws Exception {
        when(ubicaciones.adjunta(eq(OTRO_USUARIO), any(), any(), any(), anyInt()))
                .thenThrow(new RecursoNoEncontradoException("Fichaje no encontrado"));

        mockMvc.perform(post("/api/v1/fichajes/" + APUNTE_ID + "/ubicacion")
                        .with(jwt().jwt(j -> j.subject(OTRO_USUARIO.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"latitud":40.41675,"longitud":-3.70379,"precisionMetros":20}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("rechaza latitud, longitud o precisión fuera de rango — 400")
    void rechazaLatitudLongitudOPrecisionFueraDeRango() throws Exception {
        mockMvc.perform(post("/api/v1/fichajes/" + APUNTE_ID + "/ubicacion").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"latitud":200,"longitud":-3.70379,"precisionMetros":20}"""))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/fichajes/" + APUNTE_ID + "/ubicacion").with(comoUsuario())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"latitud":40.41675,"longitud":-3.70379,"precisionMetros":50000}"""))
                .andExpect(status().isBadRequest());

        verify(ubicaciones, never()).adjunta(any(), any(), any(), any(), anyInt());
    }

    // --- Supresión ---

    @Test
    @DisplayName("DELETE fichajes/{apunteId}/ubicacion suprime — 204")
    void suprimeGranular() throws Exception {
        mockMvc.perform(delete("/api/v1/fichajes/" + APUNTE_ID + "/ubicacion").with(comoUsuario()))
                .andExpect(status().isNoContent());

        verify(ubicaciones).suprime(USUARIO, APUNTE_ID);
    }

    @Test
    @DisplayName("DELETE ubicaciones borra todo el histórico Y purga las declaraciones de centro — 204")
    void borraTodasLasUbicaciones() throws Exception {
        mockMvc.perform(delete("/api/v1/ubicaciones").with(comoUsuario()))
                .andExpect(status().isNoContent());

        verify(ubicaciones).borraTodas(USUARIO);
        // Sin esto, "borrar todo tu histórico de ubicaciones" (promesa del
        // consentimiento v1.0) dejaba las coordenadas de centros_trabajo
        // intactas, alcanzables solo purgando cada id a mano.
        verify(centros).purgaTodas(USUARIO);
    }

    // --- Reclamación en curso (contrato §Retención) ---

    @Test
    @DisplayName("PUT usuario/reclamacion-en-curso declara — 204")
    void declaraReclamacionEnCurso() throws Exception {
        mockMvc.perform(put("/api/v1/usuario/reclamacion-en-curso").with(comoUsuario()))
                .andExpect(status().isNoContent());

        verify(reclamaciones).declara(USUARIO);
    }

    @Test
    @DisplayName("DELETE usuario/reclamacion-en-curso retira — 204")
    void retiraReclamacionEnCurso() throws Exception {
        mockMvc.perform(delete("/api/v1/usuario/reclamacion-en-curso").with(comoUsuario()))
                .andExpect(status().isNoContent());

        verify(reclamaciones).retira(USUARIO);
    }

    @Test
    @DisplayName("sin token → 401 en reclamación en curso")
    void sinTokenReclamacionEnCurso() throws Exception {
        mockMvc.perform(put("/api/v1/usuario/reclamacion-en-curso"))
                .andExpect(status().isUnauthorized());

        verify(reclamaciones, never()).declara(any());
    }
}
