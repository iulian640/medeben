package es.tedeben.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.tedeben.controller.DimensionDesconocidaException;
import es.tedeben.repository.HechosCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("DimensionesCatalogoValidator — las dimensiones del perfil deben existir en las tablas del convenio")
class DimensionesCatalogoValidatorTest {

    private static DimensionesCatalogoValidator validador;

    @BeforeAll
    static void arranque() {
        validador = new DimensionesCatalogoValidator(new HechosCatalog(new ObjectMapper()));
    }

    @Test
    @DisplayName("dimensiones válidas del convenio → no lanza")
    void dimensionesValidas() {
        assertThatCode(() -> validador.valida("madrid-hosteleria",
                Map.of("tabla", "general", "nivel", "III", "claseEmpresa", "B")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("la terna COMPLETA de catering (tabla + nivel, sin claseEmpresa) también resuelve tabla → no lanza")
    void ternaCompletaCatering() {
        assertThatCode(() -> validador.valida("madrid-hosteleria", Map.of("tabla", "catering", "nivel", "I")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un subconjunto de dimensiones (solo nivel) NO resuelve tabla → 422 diciendo qué claves faltan (review HIGH)")
    void subconjuntoNoResuelveTabla() {
        // TablaSalarialService exige igualdad exacta del mapa: {nivel: II-A} jamás
        // casaría con un hecho (usan tabla+nivel[+claseEmpresa]). Antes pasaba y
        // dejaba el resumen roto para siempre; ahora se rechaza al guardar.
        assertThatExceptionOfType(DimensionDesconocidaException.class)
                .isThrownBy(() -> validador.valida("madrid-hosteleria", Map.of("nivel", "II-A")))
                .withMessageContaining("faltan")
                .withMessageContaining("tabla");
    }

    @Test
    @DisplayName("combinación de valores válidos por separado pero no publicada juntos (catering + claseEmpresa) → 422")
    void combinacionNoPublicada() {
        // 'catering' es un tabla válido y 'A' un claseEmpresa válido (en general),
        // pero ningún hecho de catering lleva claseEmpresa → no resuelve tabla.
        assertThatExceptionOfType(DimensionDesconocidaException.class)
                .isThrownBy(() -> validador.valida("madrid-hosteleria",
                        Map.of("tabla", "catering", "claseEmpresa", "A")))
                .withMessageContaining("no corresponde a ninguna tabla");
    }

    @Test
    @DisplayName("clave que no es dimensión del convenio → DimensionDesconocida con las válidas en el mensaje")
    void claveDesconocida() {
        assertThatExceptionOfType(DimensionDesconocidaException.class)
                .isThrownBy(() -> validador.valida("madrid-hosteleria", Map.of("sector", "restaurante")))
                .withMessageContaining("sector")
                .withMessageContaining("nivel");
    }

    @Test
    @DisplayName("valor que no existe para una clave válida → DimensionDesconocida con clave y valor")
    void valorDesconocido() {
        assertThatExceptionOfType(DimensionDesconocidaException.class)
                .isThrownBy(() -> validador.valida("madrid-hosteleria", Map.of("nivel", "ZZ")))
                .withMessageContaining("nivel")
                .withMessageContaining("ZZ");
    }

    @Test
    @DisplayName("un valor kilométrico desconocido se recorta en el mensaje (no se vuelca entero)")
    void valorLargoSeRecortaEnElMensaje() {
        String kilometrico = "x".repeat(300);
        assertThatExceptionOfType(DimensionDesconocidaException.class)
                .isThrownBy(() -> validador.valida("madrid-hosteleria", Map.of("nivel", kilometrico)))
                .satisfies(e -> assertThat(e.getMessage()).hasSizeLessThan(200));
    }

    @Test
    @DisplayName("dimensiones vacías o nulas → no valida nada (nada que comprobar)")
    void dimensionesVaciasOnulas() {
        assertThatCode(() -> validador.valida("madrid-hosteleria", Map.of())).doesNotThrowAnyException();
        assertThatCode(() -> validador.valida("madrid-hosteleria", null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("valor nulo dentro del mapa → DimensionDesconocida (no casa con ningún valor publicado)")
    void valorNuloEnElMapa() {
        Map<String, String> conNulo = new HashMap<>();
        conNulo.put("nivel", null);
        assertThatExceptionOfType(DimensionDesconocidaException.class)
                .isThrownBy(() -> validador.valida("madrid-hosteleria", conNulo));
    }

    @Test
    @DisplayName("convenio sin capa derivada (modo manual) → no hay catálogo, no se bloquea nada")
    void convenioSinDerivar() {
        // aleh-estatal existe como convenio pero no está entre los normalizados:
        // sin hechos de salarioBase no hay tablas contra las que validar.
        assertThatCode(() -> validador.valida("aleh-estatal", Map.of("loquesea", "valor")))
                .doesNotThrowAnyException();
    }
}
