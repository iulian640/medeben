package es.medeben.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fija el hash del texto canónico v1.0 (contrato §Backend): si alguien edita
 * el texto sin bumpear la versión, este test se rompe — el registro de
 * consentimiento deja de ser acreditable si el texto puede cambiar en
 * silencio bajo la misma "v1.0".
 */
class ConsentimientoUbicacionTextoTest {

    /** SHA-256 (hex) del texto v1.0 tal y como está en el contrato de implementación. */
    private static final String HASH_V1_0 =
            "c65cf204076a895e520ba2234f497dc3972b899624bd83fd8d99411710fb4dc2".substring(0, 64);

    @Test
    @DisplayName("el hash del texto canónico v1.0 queda fijado")
    void elHashDelTextoCanonicoQuedaFijado() {
        assertThat(Sha256.hex(ConsentimientoUbicacionTexto.TEXTO_V1_0)).isEqualTo(HASH_V1_0);
    }

    @Test
    @DisplayName("el texto canónico contiene el nombre de la feature, la versión y el aviso anti-coacción")
    void elTextoContieneElNombreYLaVersion() {
        assertThat(ConsentimientoUbicacionTexto.TEXTO_V1_0)
                .contains("Anotar dónde fichas")
                .contains("v1.0")
                .contains("art. 6.1.a")
                .contains("art. 90 de la LOPDGDD");
        // "GPS" no aparece nunca (nombre y encuadre, contrato §10): la única mención
        // de vigilancia por posición es el aviso anti-coacción del art. 90 LOPDGDD,
        // que SÍ debe decir "geolocalización" porque describe la práctica ilegal del
        // empresario, no el nombre de la feature.
        assertThat(ConsentimientoUbicacionTexto.TEXTO_V1_0.toLowerCase()).doesNotContain("gps");
        assertThat(ConsentimientoUbicacionTexto.TEXTO_V1_0).contains("control por geolocalización");
    }
}
