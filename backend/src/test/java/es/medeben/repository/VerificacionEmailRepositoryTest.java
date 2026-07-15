package es.medeben.repository;

import es.medeben.domain.usuario.Usuario;
import es.medeben.domain.usuario.VerificacionEmail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Consumo ATÓMICO del token de verificación de email, contra PostgreSQL real
 * (mismo razonamiento que {@link SesionRepositoryTest}: el WHERE es la
 * defensa, un mock no lo prueba). Un token se gasta UNA vez y solo si no ha
 * caducado: dos clicks simultáneos en el enlace del correo no pueden ganar
 * los dos, y un token caducado no verifica nada.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class VerificacionEmailRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Instant AHORA = Instant.parse("2026-07-15T12:00:00Z");
    private static final Duration VIGENCIA = Duration.ofHours(24);

    @Autowired
    private VerificacionEmailRepository verificaciones;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private TestEntityManager em;

    private UUID usuarioId;

    @BeforeEach
    void arranque() {
        usuarioId = usuarios.saveAndFlush(
                new Usuario("verifica-" + UUID.randomUUID() + "@example.com", "{noop}hash")).getId();
    }

    @Test
    @DisplayName("marcaUsadaSiIntacta gana UNA vez: la segunda reclamación del mismo token pierde")
    void consumoAtomico() {
        VerificacionEmail v = verificaciones.saveAndFlush(verificacionNueva("a1"));

        assertThat(verificaciones.marcaUsadaSiIntacta(v.getId(), AHORA)).isEqualTo(1);
        assertThat(verificaciones.marcaUsadaSiIntacta(v.getId(), AHORA.plusSeconds(1))).isZero();
    }

    @Test
    @DisplayName("un token caducado no se puede reclamar")
    void caducadaNoSeReclama() {
        VerificacionEmail caducada = verificaciones.saveAndFlush(new VerificacionEmail(
                usuarioId, hash("a2"), AHORA.minus(Duration.ofDays(2)), AHORA.minus(Duration.ofDays(1))));

        assertThat(verificaciones.marcaUsadaSiIntacta(caducada.getId(), AHORA)).isZero();
    }

    @Test
    @DisplayName("findByTokenHash localiza por hash y no inventa nada")
    void localizaPorHash() {
        VerificacionEmail v = verificaciones.saveAndFlush(verificacionNueva("b1"));

        assertThat(verificaciones.findByTokenHash(v.getTokenHash())).isPresent();
        assertThat(verificaciones.findByTokenHash(hash("no-existe"))).isEmpty();
    }

    @Test
    @DisplayName("el hash del token es UNIQUE: dos verificaciones no pueden compartirlo")
    void hashUnico() {
        verificaciones.saveAndFlush(verificacionNueva("c1"));

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> verificaciones.saveAndFlush(verificacionNueva("c1")));
    }

    @Test
    @DisplayName("el borrado del usuario arrastra sus verificaciones (CASCADE, RGPD art. 17)")
    void borradoDeUsuarioArrastraVerificaciones() {
        VerificacionEmail v = verificaciones.saveAndFlush(verificacionNueva("d1"));

        usuarios.deleteById(usuarioId);
        usuarios.flush();

        // El CASCADE borra en BD; sin limpiar el contexto de persistencia,
        // findById devolvería la entidad cacheada y el test mentiría.
        em.clear();
        assertThat(verificaciones.findById(v.getId())).isEmpty();
    }

    private VerificacionEmail verificacionNueva(String semilla) {
        return new VerificacionEmail(usuarioId, hash(semilla), AHORA, AHORA.plus(VIGENCIA));
    }

    /** 64 hex deterministas por semilla, como el SHA-256 real. */
    private static String hash(String semilla) {
        return (semilla + "0".repeat(64)).substring(0, 64).replaceAll("[^0-9a-f]", "e");
    }
}
