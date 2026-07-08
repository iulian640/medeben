package es.tedeben.repository;

import es.tedeben.domain.usuario.Usuario;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los CHECKs de vocabulario cerrado de la V5 (contra PostgreSQL real,
 * Testcontainers; se salta sin Docker). El diario es la prueba del trabajador:
 * la propia base de datos rechaza tipos u orígenes fuera del vocabulario,
 * vengan de donde vengan (bug, SQL manual, cliente futuro).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ApunteVocabularioTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private TestEntityManager em;

    private UUID usuarioId;

    @BeforeEach
    void creaUsuario() {
        usuarioId = usuarios.saveAndFlush(
                new Usuario("vocab-" + UUID.randomUUID() + "@example.com", "{noop}hash")).getId();
    }

    private Query inserta(String tipo, String origen) {
        return em.getEntityManager().createNativeQuery(
                        "INSERT INTO apuntes (id, usuario_id, fecha, tipo, hora, motivo, origen, registrado_en) "
                                + "VALUES (:id, :usuario, DATE '2026-07-08', :tipo, NULL, NULL, :origen, now())")
                .setParameter("id", UUID.randomUUID())
                .setParameter("usuario", usuarioId)
                .setParameter("tipo", tipo)
                .setParameter("origen", origen);
    }

    @Test
    @DisplayName("un tipo fuera del vocabulario no entra ni por SQL directo (chk_apuntes_tipo)")
    void rechazaTipoDesconocido() {
        assertThatThrownBy(inserta("JORNADA", "CONFIRMADO")::executeUpdate)
                .rootCause()
                .hasMessageContaining("chk_apuntes_tipo");
    }

    @Test
    @DisplayName("un origen fuera del vocabulario no entra ni por SQL directo (chk_apuntes_origen)")
    void rechazaOrigenDesconocido() {
        assertThatThrownBy(inserta("AUSENCIA", "INVENTADO")::executeUpdate)
                .rootCause()
                .hasMessageContaining("chk_apuntes_origen");
    }

    @Test
    @DisplayName("los valores reales de los enums sí entran (los CHECKs no bloquean de más)")
    void aceptaVocabularioReal() {
        assertThat(inserta("AUSENCIA", "RECTIFICACION_TARDIA").executeUpdate()).isEqualTo(1);
    }
}
