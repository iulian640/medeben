package es.tedeben.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.tedeben.domain.usuario.Perfil;
import es.tedeben.domain.usuario.Usuario;
import es.tedeben.repository.ConvenioCatalog;
import es.tedeben.repository.OcupacionesCatalog;
import es.tedeben.repository.PerfilRepository;
import es.tedeben.repository.UsuarioRepository;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test del servicio contra PostgreSQL real (Testcontainers; se salta sin
 * Docker). Cubre lo que los mocks no pueden (auditoría, hallazgo del perfil):
 * que guardar el perfil es un UPSERT de verdad — la fila es mutable 1:1 por
 * usuario y una segunda actualización NO viola la PK usuario_id.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PerfilServiceIntegracionTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final OffsetDateTime AHORA = OffsetDateTime.parse("2026-07-08T10:15:00+02:00");

    @Autowired
    private PerfilRepository perfiles;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private TestEntityManager em;

    private PerfilService servicio;
    private UUID usuarioId;

    @BeforeEach
    void arranque() {
        ObjectMapper mapper = new ObjectMapper();
        servicio = new PerfilService(perfiles, new ConvenioCatalog(mapper),
                new OcupacionesCatalog(mapper),
                Clock.fixed(AHORA.toInstant(), ZoneId.of("Europe/Madrid")));
        usuarioId = usuarios.saveAndFlush(
                new Usuario("perfil-" + UUID.randomUUID() + "@example.com", "{noop}hash")).getId();
    }

    @Test
    @DisplayName("crear → actualizar → segunda actualización: la fila se sobrescribe, sin choque de PK")
    void segundaActualizacionNoViolaLaPk() {
        servicio.guarda(usuarioId, "Madrid", "hosteleria", "cocinero",
                Map.of("nivel", "III"), new BigDecimal("1400"), null);
        em.flush();
        em.clear();

        servicio.guarda(usuarioId, "Madrid", "hosteleria", "camarero",
                Map.of("nivel", "II"), new BigDecimal("1500"), null);
        em.flush();
        em.clear();

        // La segunda actualización era la que reventaba: persist() sobre una
        // PK ya existente. Ahora debe funcionar y devolver los datos nuevos.
        servicio.guarda(usuarioId, "Alicante", "hosteleria", null,
                Map.of(), new BigDecimal("1600"), new BigDecimal("250.50"));
        em.flush();
        em.clear();

        Perfil recargado = perfiles.findById(usuarioId).orElseThrow();
        assertThat(recargado.getConvenioId()).isEqualTo("alicante-hosteleria");
        assertThat(recargado.getPuestoId()).isNull();
        assertThat(recargado.getSalarioBaseMensual()).isEqualByComparingTo("1600");
        assertThat(recargado.getPlusesAnuales()).isEqualByComparingTo("250.50");
        assertThat(recargado.getActualizadoEn()).isEqualTo(AHORA);
        assertThat(perfiles.count()).isEqualTo(1);
    }
}
