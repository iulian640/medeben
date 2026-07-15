package es.medeben.service;

import es.medeben.domain.usuario.Usuario;
import es.medeben.repository.SesionRepository;
import es.medeben.repository.UsuarioRepository;
import es.medeben.repository.VerificacionEmailRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

/**
 * El ciclo de sesiones (B4) con TRANSACCIONES REALES contra PostgreSQL. Los
 * mocks no pueden ver el bug que caza esto: la revocación en bloque del camino
 * de reuso lanzaba el 401 y el rollback de la propia transacción DESHACÍA la
 * revocación — el ladrón (y el legítimo) seguían dentro. Aquí el servicio corre
 * con su proxy @Transactional de verdad y los test methods SIN transacción
 * envolvente (commits reales, como en producción).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Import(AuthServiceSesionesIntegracionTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthServiceSesionesIntegracionTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String PASSWORD = "una-contraseña-larga";

    @TestConfiguration
    static class Config {
        @Bean
        PasswordEncoder passwordEncoder() {
            return PasswordEncoderFactories.createDelegatingPasswordEncoder();
        }

        @Bean
        JwtEncoder jwtEncoder() {
            return JwtTestSupport.claves().encoder();
        }

        @Bean
        Clock reloj() {
            return Clock.systemUTC();
        }

        @Bean
        EmisorVerificacion emisorVerificacion(es.medeben.repository.VerificacionEmailRepository verificaciones,
                                              org.springframework.context.ApplicationEventPublisher eventos,
                                              Clock reloj) {
            return new EmisorVerificacion(verificaciones, eventos, reloj, java.time.Duration.ofHours(24));
        }

        @Bean
        RegistroDeUsuario registroDeUsuario(UsuarioRepository usuarios, EmisorVerificacion emisorVerificacion) {
            return new RegistroDeUsuario(usuarios, emisorVerificacion);
        }

        @Bean
        AuthService authService(UsuarioRepository usuarios, SesionRepository sesiones,
                                es.medeben.repository.VerificacionEmailRepository verificaciones,
                                PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
                                Clock reloj, RegistroDeUsuario registroDeUsuario,
                                EmisorVerificacion emisorVerificacion) {
            return new AuthService(usuarios, sesiones, verificaciones, passwordEncoder, jwtEncoder,
                    reloj, JwtTestSupport.DURACION, java.time.Duration.ofDays(7),
                    registroDeUsuario, emisorVerificacion);
        }
    }

    @Autowired
    private AuthService servicio;

    @Autowired
    private SesionRepository sesiones;

    /**
     * Espía (no mock): por defecto delega TODO en el repositorio real. Solo
     * {@link #carreraDeRegistroNoEnvenenaLaTransaccion} lo stubea, y con un
     * email aleatorio único por test — cero riesgo de contaminar los demás.
     */
    @MockitoSpyBean
    private UsuarioRepository usuarios;

    @Autowired
    private VerificacionEmailRepository verificaciones;

    @Test
    @DisplayName("SEGURIDAD: la revocación por reuso SOBREVIVE al 401 (no la deshace el rollback)")
    void revocacionPorReusoSobreviveAl401() {
        String email = "b4-" + UUID.randomUUID() + "@example.com";
        servicio.registra(email, PASSWORD);
        SesionEmitida sesion1 = servicio.login(email, PASSWORD);

        // Rotación normal: R1 se gasta y nace R2 (la sesión legítima).
        SesionEmitida sesion2 = servicio.refresca(sesion1.refreshToken());

        // Llega OTRA VEZ R1 (robo simulado): 401...
        assertThatExceptionOfType(CredencialesInvalidasException.class)
                .isThrownBy(() -> servicio.refresca(sesion1.refreshToken()));

        // ...y la revocación en bloque tiene que haberse COMMITEADO: R2 muerto.
        assertThatExceptionOfType(CredencialesInvalidasException.class)
                .isThrownBy(() -> servicio.refresca(sesion2.refreshToken()));
    }

    @Test
    @DisplayName("logout revoca de verdad: el refresh deja de rotar")
    void logoutRevoca() {
        String email = "b4-" + UUID.randomUUID() + "@example.com";
        servicio.registra(email, PASSWORD);
        Usuario usuario = usuarios.findByEmail(email).orElseThrow();
        SesionEmitida sesion = servicio.login(email, PASSWORD);

        servicio.cierraSesion(sesion.refreshToken());

        assertThatExceptionOfType(CredencialesInvalidasException.class)
                .isThrownBy(() -> servicio.refresca(sesion.refreshToken()));
        assertThat(sesiones.findAll().stream()
                .filter(s -> s.getUsuarioId().equals(usuario.getId()))
                .allMatch(s -> s.getRevocadaEn() != null)).isTrue();
    }

    /**
     * SEGURIDAD (HIGH, review 2026-07-15): la carrera del INSERT no debe
     * envenenar la transacción de {@code registra()}. Antes del fix, esto
     * reventaba con {@code UnexpectedRollbackException} en vez de devolver el
     * email de forma uniforme (ver el javadoc de {@link RegistroDeUsuario}).
     *
     * <p>Simula la carrera de forma DETERMINISTA (sin hilos reales): la fila
     * conflictiva ya está commiteada en la BD real, pero el {@code
     * findByEmail} de ESTA petición la ve vacía (el mismo snapshot que vería
     * si de verdad hubiera perdido la carrera de lectura) — así el INSERT
     * choca de verdad contra el UNIQUE de {@code usuarios.email}.
     */
    @Test
    @DisplayName("SEGURIDAD: la carrera del INSERT no envenena la transacción — 201 uniforme, no 500, y sin token huérfano")
    void carreraDeRegistroNoEnvenenaLaTransaccion() {
        String email = "carrera-" + UUID.randomUUID() + "@example.com";
        // La fila conflictiva YA existe en la BD real (el registro que "ganó").
        usuarios.saveAndFlush(new Usuario(email, "hash-de-otro-registro-que-gano-la-carrera"));
        long tokensAntes = verificaciones.count();

        // ...pero el findByEmail de ESTA petición la ve vacía: fuerza la rama
        // "email nuevo", cuyo INSERT choca con el UNIQUE.
        when(usuarios.findByEmail(email)).thenReturn(java.util.Optional.empty());

        String[] resultado = new String[1];
        assertThatCode(() -> resultado[0] = servicio.registra(email, PASSWORD))
                .doesNotThrowAnyException();

        assertThat(resultado[0]).isEqualTo(email);
        // El choque pasó ANTES de emitir el token: no puede quedar uno huérfano
        // de un usuario que, por la carrera, no llegó a crearse.
        assertThat(verificaciones.count()).isEqualTo(tokensAntes);
    }
}
