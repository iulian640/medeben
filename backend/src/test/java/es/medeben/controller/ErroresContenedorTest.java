package es.medeben.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Errores que corta el CONTENEDOR antes de llegar a Spring MVC (issue #232):
 * una barra codificada (%2F) en la ruta la rechaza Tomcat de fábrica con su
 * página HTML, rompiendo la promesa de que todos los errores de la API salen
 * como RFC 7807. MockMvc no pasa por Tomcat, así que esto exige servidor
 * real: perfil {@code local} (sin base de datos) porque el rechazo ocurre
 * antes de tocar ningún controller.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "local"})
class ErroresContenedorTest {

    @LocalServerPort
    private int puerto;

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("%2F en la ruta → 400 problem+json, no la página HTML de Tomcat")
    void barraCodificadaEnLaRuta() throws Exception {
        // URI ya construida para que el %2F viaje tal cual, sin re-codificar.
        URI uri = URI.create("http://localhost:" + puerto + "/api/v1/fichajes/dia/..%2F..%2Fetc%2Fpasswd");

        ResponseEntity<String> respuesta = rest.getForEntity(uri, String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getHeaders().getContentType())
                .as("content type de la respuesta")
                .isNotNull()
                .satisfies(tipo -> assertThat(tipo.toString()).startsWith("application/problem+json"));
        assertThat(respuesta.getBody())
                .contains("\"status\":400")
                .doesNotContain("<html")
                .doesNotContain("<!doctype")
                // La ruta pedida (basura del cliente) no se ecoa en el error.
                .doesNotContain("passwd");
    }

    @Test
    @DisplayName("los errores que sí llegan a Spring siguen igual: 401 problem+json en ruta privada")
    void rutaPrivadaSigueDandoProblemJson() {
        URI uri = URI.create("http://localhost:" + puerto + "/api/v1/fichajes/dia/2026-07-08");

        ResponseEntity<String> respuesta = rest.getForEntity(uri, String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(respuesta.getHeaders().getContentType()).isNotNull();
        assertThat(respuesta.getHeaders().getContentType().toString()).startsWith("application/problem+json");
    }
}
