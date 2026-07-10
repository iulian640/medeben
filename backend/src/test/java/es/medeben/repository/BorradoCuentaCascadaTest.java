package es.medeben.repository;

import es.medeben.domain.fichaje.Apunte;
import es.medeben.domain.fichaje.OrigenApunte;
import es.medeben.domain.fichaje.TipoApunte;
import es.medeben.domain.horario.Cuadrante;
import es.medeben.domain.usuario.Perfil;
import es.medeben.domain.usuario.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El borrado de cuenta (RGPD art. 17) contra PostgreSQL real: al borrar la fila
 * de usuarios, el ON DELETE CASCADE de las migraciones arrastra perfil,
 * cuadrantes y apuntes — TODO lo personal desaparece de la BD, y el email queda
 * libre para registrarse de nuevo. Esto no lo puede cubrir un mock: es el
 * contrato del esquema.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class BorradoCuentaCascadaTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final OffsetDateTime SELLO = OffsetDateTime.parse("2026-07-10T10:15:00+02:00");

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("borrar el usuario arrastra perfil, cuadrantes y apuntes (cascade) y libera el email")
    void borradoArrastraTodoYLiberaElEmail() {
        String email = "rgpd-" + UUID.randomUUID() + "@example.com";
        UUID usuarioId = usuarios.saveAndFlush(new Usuario(email, "{noop}hash")).getId();
        em.persistAndFlush(new Perfil(usuarioId, "Madrid", "hosteleria", "madrid-hosteleria",
                "cocinero", Map.of("nivel", "III"), null, null, SELLO));
        em.persistAndFlush(new Cuadrante(usuarioId, null, List.of(), SELLO));
        em.persistAndFlush(new Apunte(usuarioId, LocalDate.of(2026, 7, 9), TipoApunte.ENTRADA,
                "09:00", null, OrigenApunte.CONFIRMADO, SELLO));
        em.clear();

        usuarios.deleteById(usuarioId);
        usuarios.flush();
        em.clear();

        assertThat(usuarios.findById(usuarioId)).isEmpty();
        assertThat(cuenta("perfiles", usuarioId)).isZero();
        assertThat(cuenta("cuadrantes", usuarioId)).isZero();
        assertThat(cuenta("apuntes", usuarioId)).isZero();

        // El email vuelve a estar libre: registrarse de nuevo no choca con el UNIQUE.
        assertThat(usuarios.saveAndFlush(new Usuario(email, "{noop}otroHash")).getId())
                .isNotEqualTo(usuarioId);
    }

    private long cuenta(String tabla, UUID usuarioId) {
        return ((Number) em.getEntityManager()
                .createNativeQuery("SELECT count(*) FROM " + tabla + " WHERE usuario_id = :id")
                .setParameter("id", usuarioId)
                .getSingleResult()).longValue();
    }
}
