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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

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

    /**
     * Carrera de creación (hallazgo de la revisión): dos peticiones del MISMO
     * usuario llegan a la primera creación, ambas ven findById() vacío y ambas
     * insertan → violación de la PK usuario_id. Se reproduce de forma
     * determinista: la "otra petición" ya insertó el perfil, pero la primera
     * lectura del servicio no lo ve (stub de lectura rancia) y su INSERT choca
     * contra la PK real de Postgres; el servicio debe capturar la violación y
     * reintentar como actualización. Sin transacción de test (NOT_SUPPORTED):
     * cada llamada al repositorio necesita su propia transacción para que la
     * del insert fallido quede cerrada antes del reintento, igual que en
     * producción.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("carrera de creación: el insert que choca con la PK real se reintenta como update")
    void carreraDeCreacionReintentaContraPostgres() {
        try {
            // La "otra petición" gana la carrera e inserta el perfil.
            servicio.guarda(usuarioId, "Madrid", "hosteleria", "cocinero",
                    Map.of("nivel", "III"), new BigDecimal("1400"), null);

            // Repositorio con lectura rancia: la primera findById() no ve la
            // fila recién creada; el resto de llamadas van al repositorio real.
            PerfilRepository conLecturaRancia = mock(PerfilRepository.class, delegatesTo(perfiles));
            doReturn(Optional.empty())
                    .doAnswer(inv -> perfiles.findById(usuarioId))
                    .when(conLecturaRancia).findById(usuarioId);
            PerfilService servicioEnCarrera = new PerfilService(conLecturaRancia,
                    new ConvenioCatalog(new ObjectMapper()),
                    new OcupacionesCatalog(new ObjectMapper()),
                    Clock.fixed(AHORA.toInstant(), ZoneId.of("Europe/Madrid")));

            Perfil guardado = servicioEnCarrera.guarda(usuarioId, "Alicante", "hosteleria",
                    "camarero", Map.of("nivel", "II"), new BigDecimal("1500"), null);

            assertThat(guardado.getConvenioId()).isEqualTo("alicante-hosteleria");
            Perfil recargado = perfiles.findById(usuarioId).orElseThrow();
            assertThat(recargado.getConvenioId()).isEqualTo("alicante-hosteleria");
            assertThat(recargado.getPuestoId()).isEqualTo("camarero");
            assertThat(recargado.getSalarioBaseMensual()).isEqualByComparingTo("1500");
            assertThat(recargado.getActualizadoEn()).isEqualTo(AHORA);
            assertThat(perfiles.count()).isEqualTo(1);
        } finally {
            // Sin transacción de test no hay rollback automático: se limpia a
            // mano para no dejar filas que contaminen los demás tests.
            perfiles.deleteById(usuarioId);
            usuarios.deleteById(usuarioId);
        }
    }
}
