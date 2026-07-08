package es.tedeben.controller;

import es.tedeben.config.SecurityConfig;
import es.tedeben.repository.ConvenioCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web slice test — los datos de convenios son públicos (ya viven en el repo);
 * los endpoints de consulta no requieren autenticación.
 */
@WebMvcTest(ConvenioController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, ConvenioCatalog.class})
class ConvenioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/provincias es público y devuelve las 52 provincias ordenadas")
    void provincias() throws Exception {
        mockMvc.perform(get("/api/v1/provincias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(52)))
                .andExpect(jsonPath("$[0]").value("A Coruña"));
    }

    @Test
    @DisplayName("GET /api/v1/convenios lista los 55 con resumen")
    void listaConvenios() throws Exception {
        mockMvc.perform(get("/api/v1/convenios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(55)))
                .andExpect(jsonPath("$[?(@.id == 'madrid-hosteleria')].subsector").value("hosteleria"));
    }

    @Test
    @DisplayName("GET /api/v1/convenios/{id} devuelve el detalle con el JSON completo (visor D4)")
    void detalleConvenio() throws Exception {
        mockMvc.perform(get("/api/v1/convenios/madrid-hosteleria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("madrid-hosteleria"))
                .andExpect(jsonPath("$.convenio.nocturnidad.articulo").value("Art. 27"));
    }

    @Test
    @DisplayName("GET /api/v1/convenios/{id} inexistente → 404 con problema RFC 7807")
    void detalleNoExiste() throws Exception {
        mockMvc.perform(get("/api/v1/convenios/narnia-hosteleria"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    @DisplayName("GET /api/v1/convenios/para-trabajador resuelve provincia + subsector (D20)")
    void paraTrabajador() throws Exception {
        mockMvc.perform(get("/api/v1/convenios/para-trabajador")
                        .param("provincia", "Soria").param("subsector", "hospedaje"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("soria-hosteleria"));
    }

    @Test
    @DisplayName("para-trabajador con subsector desconocido → 400")
    void paraTrabajadorSubsectorMalo() throws Exception {
        mockMvc.perform(get("/api/v1/convenios/para-trabajador")
                        .param("provincia", "Soria").param("subsector", "peluquería"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("para-trabajador con provincia desconocida → 404")
    void paraTrabajadorProvinciaDesconocida() throws Exception {
        mockMvc.perform(get("/api/v1/convenios/para-trabajador")
                        .param("provincia", "Narnia").param("subsector", "hosteleria"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("para-trabajador sin parámetros → 400 en formato RFC 7807")
    void paraTrabajadorSinParametros() throws Exception {
        mockMvc.perform(get("/api/v1/convenios/para-trabajador"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("los GET de datos estáticos llevan Cache-Control público")
    void cacheControlEnDatosEstaticos() throws Exception {
        mockMvc.perform(get("/api/v1/convenios/madrid-hosteleria"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age")));
    }
}
