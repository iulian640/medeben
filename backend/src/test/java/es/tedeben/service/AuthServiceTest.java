package es.tedeben.service;

import es.tedeben.domain.usuario.Usuario;
import es.tedeben.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AuthService — registro y login con JWT (D13.4)")
class AuthServiceTest {

    private static final String EMAIL = "trabajador@example.com";
    private static final String PASSWORD = "una-contraseña-larga";

    private UsuarioRepository repositorio;
    private PasswordEncoder passwordEncoder;
    private AuthService servicio;
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void arranque() {
        repositorio = mock(UsuarioRepository.class);
        passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        var claves = JwtTestSupport.claves();
        jwtDecoder = claves.decoder();
        servicio = new AuthService(repositorio, passwordEncoder, claves.encoder(), JwtTestSupport.DURACION);
    }

    @Test
    @DisplayName("registro: guarda el email normalizado y el hash (nunca la contraseña en claro)")
    void registroGuardaHash() {
        when(repositorio.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(repositorio.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario usuario = servicio.registra("  Trabajador@Example.com ", PASSWORD);

        assertThat(usuario.getEmail()).isEqualTo(EMAIL);
        assertThat(usuario.getPasswordHash()).doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, usuario.getPasswordHash())).isTrue();
        verify(repositorio).saveAndFlush(any(Usuario.class));
    }

    @Test
    @DisplayName("registro con email ya usado → EmailYaRegistradoException")
    void registroDuplicado() {
        when(repositorio.findByEmail(EMAIL)).thenReturn(Optional.of(usuarioExistente()));

        assertThatExceptionOfType(EmailYaRegistradoException.class)
                .isThrownBy(() -> servicio.registra(EMAIL, PASSWORD));
    }

    @Test
    @DisplayName("registro con contraseña corta → IllegalArgumentException")
    void registroPasswordCorta() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> servicio.registra(EMAIL, "corta"));
    }

    @Test
    @DisplayName("login correcto → token JWT con el id del usuario como subject")
    void loginCorrecto() {
        Usuario usuario = usuarioExistente();
        when(repositorio.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));

        TokenEmitido token = servicio.login(EMAIL, PASSWORD);

        assertThat(token.token()).isNotBlank();
        var jwt = jwtDecoder.decode(token.token());
        assertThat(jwt.getSubject()).isEqualTo(usuario.getId().toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("login con contraseña errónea o email inexistente → CredencialesInvalidasException (mismo error, sin filtrar cuál)")
    void loginIncorrecto() {
        when(repositorio.findByEmail(EMAIL)).thenReturn(Optional.of(usuarioExistente()));
        when(repositorio.findByEmail("nadie@example.com")).thenReturn(Optional.empty());

        assertThatExceptionOfType(CredencialesInvalidasException.class)
                .isThrownBy(() -> servicio.login(EMAIL, "otra-contraseña-mala"));
        assertThatExceptionOfType(CredencialesInvalidasException.class)
                .isThrownBy(() -> servicio.login("nadie@example.com", PASSWORD));
    }

    private Usuario usuarioExistente() {
        Usuario usuario = new Usuario(EMAIL, passwordEncoder.encode(PASSWORD));
        usuario.setId(UUID.randomUUID());
        return usuario;
    }
}
