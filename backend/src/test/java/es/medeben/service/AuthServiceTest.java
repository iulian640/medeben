package es.medeben.service;

import es.medeben.domain.usuario.Sesion;
import es.medeben.domain.usuario.Usuario;
import es.medeben.domain.usuario.VerificacionEmail;
import es.medeben.repository.SesionRepository;
import es.medeben.repository.UsuarioRepository;
import es.medeben.repository.VerificacionEmailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("AuthService — registro, login y ciclo de sesión (D13.4 + B4)")
class AuthServiceTest {

    private static final String EMAIL = "trabajador@example.com";
    private static final String PASSWORD = "una-contraseña-larga";
    private static final Instant AHORA = Instant.parse("2026-07-10T21:00:00Z");
    private static final Duration DURACION_REFRESH = Duration.ofDays(7);

    private static final Duration DURACION_VERIFICACION = Duration.ofHours(24);

    private UsuarioRepository repositorio;
    private SesionRepository sesiones;
    private VerificacionEmailRepository verificaciones;
    private ApplicationEventPublisher eventos;
    private PasswordEncoder passwordEncoder;
    private AuthService servicio;
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void arranque() {
        repositorio = mock(UsuarioRepository.class);
        sesiones = mock(SesionRepository.class);
        verificaciones = mock(VerificacionEmailRepository.class);
        eventos = mock(ApplicationEventPublisher.class);
        passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        // El decoder valida la caducidad con el MISMO reloj fijo que emite el
        // token: si no, un token minteado en AHORA (pasado) caducaba al pasar la
        // hora real por su exp y el build reventaba solo por el paso del tiempo.
        var claves = JwtTestSupport.claves(Clock.fixed(AHORA, ZoneOffset.UTC));
        jwtDecoder = claves.decoder();
        // RegistroDeUsuario y EmisorVerificacion son beans REALES (no mocks):
        // solo sus dependencias de hoja (repositorios, eventos) están mockeadas.
        // Fuera de un contexto Spring el @Transactional no proxya nada, así que
        // esto prueba el orden de las llamadas, no la semántica transaccional
        // real (eso lo cubre AuthServiceSesionesIntegracionTest con Testcontainers).
        var emisorVerificacion = new EmisorVerificacion(verificaciones, eventos,
                Clock.fixed(AHORA, ZoneOffset.UTC), DURACION_VERIFICACION);
        var registroDeUsuario = new RegistroDeUsuario(repositorio, emisorVerificacion);
        servicio = new AuthService(repositorio, sesiones, verificaciones, passwordEncoder,
                claves.encoder(), Clock.fixed(AHORA, ZoneOffset.UTC),
                JwtTestSupport.DURACION, DURACION_REFRESH, registroDeUsuario, emisorVerificacion);
    }

    @Test
    @DisplayName("registro nuevo: email normalizado, hash BCrypt, usuario SIN verificar y token de verificación emitido")
    void registroNuevoGuardaHashYEmiteVerificacion() {
        when(repositorio.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(repositorio.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        String resultado = servicio.registra("  Trabajador@Example.com ", PASSWORD);

        assertThat(resultado).isEqualTo(EMAIL);
        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(repositorio).saveAndFlush(guardado.capture());
        assertThat(guardado.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(guardado.getValue().getPasswordHash()).doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, guardado.getValue().getPasswordHash())).isTrue();
        assertThat(guardado.getValue().isEmailVerificado()).isFalse();

        // En la BD solo entra el SHA-256 del token; el token en claro viaja
        // únicamente en el evento (que acaba en el correo del usuario).
        ArgumentCaptor<VerificacionEmail> verificacion = ArgumentCaptor.forClass(VerificacionEmail.class);
        verify(verificaciones).save(verificacion.capture());
        ArgumentCaptor<VerificacionEmailSolicitada> evento =
                ArgumentCaptor.forClass(VerificacionEmailSolicitada.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue().email()).isEqualTo(EMAIL);
        assertThat(verificacion.getValue().getTokenHash())
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .isEqualTo(sha256Hex(evento.getValue().token()));
        assertThat(verificacion.getValue().getCaducaEn())
                .isEqualTo(AHORA.plus(DURACION_VERIFICACION));
    }

    @Test
    @DisplayName("ANTI-ENUMERACIÓN: el registro devuelve el MISMO resultado para email nuevo, existente sin verificar y existente verificado")
    void registroDevuelveResultadoUniforme() {
        // Nuevo.
        when(repositorio.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(repositorio.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        assertThat(servicio.registra(EMAIL, PASSWORD)).isEqualTo(EMAIL);

        // Existente sin verificar.
        when(repositorio.findByEmail(EMAIL)).thenReturn(Optional.of(usuarioExistente()));
        assertThat(servicio.registra(EMAIL, PASSWORD)).isEqualTo(EMAIL);

        // Existente verificado: mismo resultado y, sobre todo, SIN excepción.
        Usuario verificado = usuarioExistente();
        verificado.marcaVerificado(AHORA.minusSeconds(3600));
        when(repositorio.findByEmail(EMAIL)).thenReturn(Optional.of(verificado));
        assertThatCode(() -> servicio.registra(EMAIL, PASSWORD)).doesNotThrowAnyException();
        assertThat(servicio.registra(EMAIL, PASSWORD)).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("SEGURIDAD (CRITICAL, review 2026-07-15): re-registro de una cuenta SIN verificar es NO-OP — no pisa la contraseña ni reemite token")
    void reRegistroSinVerificarEsNoOp() {
        Usuario sinVerificar = usuarioExistente();
        String hashOriginal = sinVerificar.getPasswordHash();
        when(repositorio.findByEmail(EMAIL)).thenReturn(Optional.of(sinVerificar));

        String resultado = servicio.registra(EMAIL, "intento-de-pisar-la-cuenta");

        assertThat(resultado).isEqualTo(EMAIL);
        // Antes del fix: esta línea sobrescribía la contraseña de la víctima con
        // UNA sola petición sabiendo su email — combinado con login sin exigir
        // verificación, era un account takeover completo.
        assertThat(sinVerificar.getPasswordHash()).isEqualTo(hashOriginal);
        verify(repositorio, never()).saveAndFlush(any());
        verifyNoInteractions(verificaciones);
        verifyNoInteractions(eventos);
    }

    @Test
    @DisplayName("re-registro de una cuenta YA verificada: no toca la contraseña, no emite token, no envía nada")
    void reRegistroVerificadoNoTocaNada() {
        Usuario verificado = usuarioExistente();
        verificado.marcaVerificado(AHORA.minusSeconds(3600));
        String hashOriginal = verificado.getPasswordHash();
        when(repositorio.findByEmail(EMAIL)).thenReturn(Optional.of(verificado));

        servicio.registra(EMAIL, "intento-de-pisar-la-cuenta");

        assertThat(verificado.getPasswordHash()).isEqualTo(hashOriginal);
        verify(repositorio, never()).saveAndFlush(any());
        verifyNoInteractions(verificaciones);
        verifyNoInteractions(eventos);
    }

    @Test
    @DisplayName("registro con contraseña corta → IllegalArgumentException")
    void registroPasswordCorta() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> servicio.registra(EMAIL, "corta"));
    }

    // --- Verificación y reenvío ---

    @Test
    @DisplayName("verificaEmail con token válido: consume el token (atómico) y sella el usuario como verificado")
    void verificaEmailValido() {
        Usuario usuario = usuarioExistente();
        VerificacionEmail viva = new VerificacionEmail(usuario.getId(), "a".repeat(64),
                AHORA.minusSeconds(60), AHORA.plus(DURACION_VERIFICACION));
        when(verificaciones.findByTokenHash(any())).thenReturn(Optional.of(viva));
        when(verificaciones.marcaUsadaSiIntacta(eq(viva.getId()), any())).thenReturn(1);
        when(repositorio.findById(usuario.getId())).thenReturn(Optional.of(usuario));

        servicio.verificaEmail("token-del-correo");

        assertThat(usuario.isEmailVerificado()).isTrue();
        assertThat(usuario.getVerificadoEn()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("verificaEmail: token desconocido, caducado o ya usado → la MISMA excepción (sin oráculo)")
    void verificaEmailInvalido() {
        when(verificaciones.findByTokenHash(any())).thenReturn(Optional.empty());
        assertThatExceptionOfType(VerificacionInvalidaException.class)
                .isThrownBy(() -> servicio.verificaEmail("desconocido"));

        Usuario usuario = usuarioExistente();
        VerificacionEmail gastada = new VerificacionEmail(usuario.getId(), "b".repeat(64),
                AHORA.minusSeconds(600), AHORA.plus(DURACION_VERIFICACION));
        when(verificaciones.findByTokenHash(any())).thenReturn(Optional.of(gastada));
        when(verificaciones.marcaUsadaSiIntacta(eq(gastada.getId()), any())).thenReturn(0);
        assertThatExceptionOfType(VerificacionInvalidaException.class)
                .isThrownBy(() -> servicio.verificaEmail("gastado-o-caducado"));
    }

    @Test
    @DisplayName("reenviaVerificacion a una cuenta sin verificar: emite token nuevo y publica el evento")
    void reenviaVerificacionSinVerificar() {
        Usuario sinVerificar = usuarioExistente();
        when(repositorio.findByEmail(EMAIL)).thenReturn(Optional.of(sinVerificar));

        servicio.reenviaVerificacion(EMAIL);

        verify(verificaciones).save(any(VerificacionEmail.class));
        verify(eventos).publishEvent(any(VerificacionEmailSolicitada.class));
    }

    @Test
    @DisplayName("ANTI EMAIL-BOMBING: al 4º token en una hora para el mismo usuario, el reenvío calla (uniforme, sin excepción)")
    void reenviaVerificacionConTopePorUsuario() {
        Usuario sinVerificar = usuarioExistente();
        when(repositorio.findByEmail(EMAIL)).thenReturn(Optional.of(sinVerificar));
        when(verificaciones.cuentaEmitidasDesde(eq(sinVerificar.getId()), any())).thenReturn(3L);

        assertThatCode(() -> servicio.reenviaVerificacion(EMAIL)).doesNotThrowAnyException();

        verify(verificaciones, never()).save(any(VerificacionEmail.class));
        verifyNoInteractions(eventos);
    }

    @Test
    @DisplayName("me: devuelve email y estado de verificación FRESCOS de BD (no de un claim que envejece)")
    void meDevuelveEstadoFresco() {
        Usuario usuario = usuarioExistente();
        usuario.marcaVerificado(AHORA.minusSeconds(60));
        when(repositorio.findById(usuario.getId())).thenReturn(Optional.of(usuario));

        var respuesta = servicio.me(usuario.getId());

        assertThat(respuesta.email()).isEqualTo(EMAIL);
        assertThat(respuesta.emailVerificado()).isTrue();
    }

    @Test
    @DisplayName("me: usuario borrado con token aún vivo → 401 (CredencialesInvalidasException)")
    void meUsuarioBorrado() {
        UUID id = UUID.randomUUID();
        when(repositorio.findById(id)).thenReturn(Optional.empty());

        assertThatExceptionOfType(CredencialesInvalidasException.class)
                .isThrownBy(() -> servicio.me(id));
    }

    @Test
    @DisplayName("ANTI-ENUMERACIÓN: reenviaVerificacion con email desconocido o ya verificado no hace nada y no revienta")
    void reenviaVerificacionUniforme() {
        when(repositorio.findByEmail("nadie@example.com")).thenReturn(Optional.empty());
        assertThatCode(() -> servicio.reenviaVerificacion("nadie@example.com"))
                .doesNotThrowAnyException();

        Usuario verificado = usuarioExistente();
        verificado.marcaVerificado(AHORA.minusSeconds(3600));
        when(repositorio.findByEmail(EMAIL)).thenReturn(Optional.of(verificado));
        assertThatCode(() -> servicio.reenviaVerificacion(EMAIL)).doesNotThrowAnyException();

        verifyNoInteractions(verificaciones);
        verifyNoInteractions(eventos);
    }

    /** El mismo SHA-256 hex que aplica el servicio: lo que debe llegar a la BD. */
    private static String sha256Hex(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("login correcto → JWT con el id como subject Y refresh opaco persistido HASHEADO")
    void loginCorrecto() {
        Usuario usuario = usuarioExistente();
        when(repositorio.findByEmail(EMAIL)).thenReturn(Optional.of(usuario));

        SesionEmitida sesion = servicio.login(EMAIL, PASSWORD);

        assertThat(sesion.token()).isNotBlank();
        var jwt = jwtDecoder.decode(sesion.token());
        assertThat(jwt.getSubject()).isEqualTo(usuario.getId().toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo(EMAIL);

        // El refresh que viaja al cliente NUNCA toca la BD en claro: se guarda
        // su SHA-256 (64 hex) y caduca según su propia duración, no la del JWT.
        assertThat(sesion.refreshToken()).isNotBlank();
        assertThat(sesion.refreshExpiraEn()).isEqualTo(AHORA.plus(DURACION_REFRESH));
        ArgumentCaptor<Sesion> guardada = ArgumentCaptor.forClass(Sesion.class);
        verify(sesiones).save(guardada.capture());
        assertThat(guardada.getValue().getTokenHash())
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .isNotEqualTo(sesion.refreshToken());
        assertThat(guardada.getValue().getUsuarioId()).isEqualTo(usuario.getId());
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

    // --- Refresh: rotación y revocación (B4) ---

    @Test
    @DisplayName("refresca: gasta la sesión vieja (atómico) y emite access + refresh NUEVOS")
    void refrescaRota() {
        Usuario usuario = usuarioExistente();
        Sesion viva = sesionViva(usuario);
        when(sesiones.findByTokenHash(any())).thenReturn(Optional.of(viva));
        when(sesiones.marcaUsadaSiIntacta(eq(viva.getId()), any())).thenReturn(1);
        when(repositorio.findById(usuario.getId())).thenReturn(Optional.of(usuario));

        SesionEmitida nueva = servicio.refresca("refresh-que-viaja");

        assertThat(nueva.token()).isNotBlank();
        assertThat(nueva.refreshToken()).isNotBlank();
        verify(sesiones).marcaUsadaSiIntacta(eq(viva.getId()), any());
        verify(sesiones).save(any(Sesion.class)); // la sesión rotada
        verify(sesiones, never()).revocaTodas(any(), any());
    }

    @Test
    @DisplayName("SEGURIDAD: un refresh YA GASTADO (posible robo) revoca TODAS las sesiones del usuario")
    void refrescaReusadoRevocaTodo() {
        Usuario usuario = usuarioExistente();
        Sesion viva = sesionViva(usuario);
        when(sesiones.findByTokenHash(any())).thenReturn(Optional.of(viva));
        // La reclamación atómica pierde: alguien lo gastó antes.
        when(sesiones.marcaUsadaSiIntacta(eq(viva.getId()), any())).thenReturn(0);

        assertThatExceptionOfType(CredencialesInvalidasException.class)
                .isThrownBy(() -> servicio.refresca("refresh-robado"));

        verify(sesiones).revocaTodas(eq(usuario.getId()), any());
        verify(sesiones, never()).save(any(Sesion.class));
    }

    @Test
    @DisplayName("refresh desconocido, caducado o revocado → el MISMO 401 (sin oráculo)")
    void refrescaInvalido() {
        when(sesiones.findByTokenHash(any())).thenReturn(Optional.empty());
        assertThatExceptionOfType(CredencialesInvalidasException.class)
                .isThrownBy(() -> servicio.refresca("desconocido"));

        Usuario usuario = usuarioExistente();
        Sesion caducada = new Sesion(usuario.getId(), "b".repeat(64),
                AHORA.minus(Duration.ofDays(9)), AHORA.minus(Duration.ofDays(2)));
        when(sesiones.findByTokenHash(any())).thenReturn(Optional.of(caducada));
        assertThatExceptionOfType(CredencialesInvalidasException.class)
                .isThrownBy(() -> servicio.refresca("caducado"));

        // Revocada (logout previo): mismo 401, y tampoco escala a reuso.
        Sesion revocada = sesionViva(usuario);
        when(sesiones.findByTokenHash(any())).thenReturn(Optional.of(revocada));
        when(sesiones.revocaPorHash(any(), any())).thenReturn(1);
        servicio.cierraSesion("da-igual");
        // Simula el estado revocado que vería el refresca posterior.
        Sesion conRevocacion = org.mockito.Mockito.spy(revocada);
        org.mockito.Mockito.doReturn(AHORA.minusSeconds(60)).when(conRevocacion).getRevocadaEn();
        when(sesiones.findByTokenHash(any())).thenReturn(Optional.of(conRevocacion));
        assertThatExceptionOfType(CredencialesInvalidasException.class)
                .isThrownBy(() -> servicio.refresca("revocado"));

        // Ni caducado ni revocado son reuso: sin revocación en bloque ni sesión nueva.
        verify(sesiones, never()).revocaTodas(any(), any());
        verify(sesiones, never()).save(any(Sesion.class));
    }

    @Test
    @DisplayName("cierraSesion revoca por hash y es idempotente (un token desconocido no explota ni revela nada)")
    void cierraSesion() {
        when(sesiones.revocaPorHash(any(), any())).thenReturn(0);

        servicio.cierraSesion("da-igual-si-existe");

        verify(sesiones).revocaPorHash(any(), any());
    }

    private Sesion sesionViva(Usuario usuario) {
        return new Sesion(usuario.getId(), "a".repeat(64), AHORA.minusSeconds(600),
                AHORA.plus(Duration.ofDays(6)));
    }

    private Usuario usuarioExistente() {
        Usuario usuario = new Usuario(EMAIL, passwordEncoder.encode(PASSWORD));
        usuario.setId(UUID.randomUUID());
        return usuario;
    }
}
