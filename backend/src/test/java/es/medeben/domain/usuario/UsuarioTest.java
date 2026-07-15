package es.medeben.domain.usuario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Estado de verificación de email del usuario (cierre de la enumeración de
 * cuentas): los usuarios NUEVOS nacen sin verificar; los que existían antes
 * de la feature los pone a verificado el backfill de la migración V8, no
 * esta clase.
 */
@DisplayName("Usuario — verificación de email")
class UsuarioTest {

    @Test
    @DisplayName("un usuario recién creado nace SIN verificar y sin fecha de verificación")
    void usuarioNuevoNaceSinVerificar() {
        Usuario usuario = new Usuario("trabajador@example.com", "{noop}hash");

        assertThat(usuario.isEmailVerificado()).isFalse();
        assertThat(usuario.getVerificadoEn()).isNull();
    }

    @Test
    @DisplayName("marcaVerificado deja el flag a true y sella el instante")
    void marcaVerificadoSellaElInstante() {
        Usuario usuario = new Usuario("trabajador@example.com", "{noop}hash");
        Instant ahora = Instant.parse("2026-07-15T12:00:00Z");

        usuario.marcaVerificado(ahora);

        assertThat(usuario.isEmailVerificado()).isTrue();
        assertThat(usuario.getVerificadoEn()).isEqualTo(ahora);
    }
}
