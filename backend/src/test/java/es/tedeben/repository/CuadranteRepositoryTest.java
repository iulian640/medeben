package es.tedeben.repository;

import es.tedeben.domain.horario.Cuadrante;
import es.tedeben.domain.horario.DiaCuadrante;
import es.tedeben.domain.horario.Tramo;
import es.tedeben.domain.usuario.Usuario;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test JPA contra PostgreSQL real (Testcontainers; se salta sin Docker — en CI
 * corre siempre). Cubre lo que los mocks no pueden (review M2): que los records
 * anidados sobreviven el viaje al JSONB y vuelven idénticos, que la consulta
 * as-of corta por instante real, y que el desempate por id es determinista.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class CuadranteRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final OffsetDateTime SELLO = OffsetDateTime.parse("2026-07-01T10:00:00+02:00");

    @Autowired
    private CuadranteRepository cuadrantes;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private TestEntityManager em;

    private UUID usuarioId;

    @BeforeEach
    void creaUsuario() {
        usuarioId = usuarios.saveAndFlush(
                new Usuario("horario-" + UUID.randomUUID() + "@example.com", "{noop}hash")).getId();
    }

    private static List<DiaCuadrante> semana() {
        DiaCuadrante partido = new DiaCuadrante(List.of(
                new Tramo("12:00", "16:00"), new Tramo("20:00", "00:30")));
        DiaCuadrante libre = new DiaCuadrante(List.of());
        return List.of(partido, libre, libre, libre, libre, libre, libre);
    }

    @Test
    @DisplayName("los records anidados (día → tramos) sobreviven el viaje al JSONB y vuelven idénticos")
    void jsonbIdaYVuelta() {
        cuadrantes.save(new Cuadrante(usuarioId, null, semana(), SELLO));
        em.flush();
        em.clear();

        Cuadrante recargado = cuadrantes
                .findTopByUsuarioIdAndSemanaInicioIsNullOrderByCreadoEnDescIdDesc(usuarioId)
                .orElseThrow();

        assertThat(recargado.getDias()).isEqualTo(semana());
        assertThat(recargado.getDias().get(0).tramos().get(1).salida()).isEqualTo("00:30");
        assertThat(recargado.getCreadoEn()).isEqualTo(SELLO);
    }

    @Test
    @DisplayName("la consulta as-of devuelve la versión vigente ANTES del corte, no la más nueva")
    void asOfCortaPorInstante() {
        cuadrantes.save(new Cuadrante(usuarioId, null, semana(), SELLO));
        List<DiaCuadrante> semanaNueva = List.of(
                new DiaCuadrante(List.of(new Tramo("08:00", "15:00"))),
                new DiaCuadrante(List.of()), new DiaCuadrante(List.of()), new DiaCuadrante(List.of()),
                new DiaCuadrante(List.of()), new DiaCuadrante(List.of()), new DiaCuadrante(List.of()));
        cuadrantes.save(new Cuadrante(usuarioId, null, semanaNueva, SELLO.plusDays(10)));
        em.flush();
        em.clear();

        Cuadrante vigenteEntonces = cuadrantes
                .findTopByUsuarioIdAndSemanaInicioIsNullAndCreadoEnBeforeOrderByCreadoEnDescIdDesc(
                        usuarioId, SELLO.plusDays(5))
                .orElseThrow();

        assertThat(vigenteEntonces.getDias()).isEqualTo(semana());
    }

    @Test
    @DisplayName("con el mismo creadoEn, el desempate por id es determinista (review L2/M1)")
    void desempateDeterminista() {
        LocalDate lunes = LocalDate.of(2026, 7, 6);
        Cuadrante a = cuadrantes.save(new Cuadrante(usuarioId, lunes, semana(), SELLO));
        Cuadrante b = cuadrantes.save(new Cuadrante(usuarioId, lunes, semana(), SELLO));
        em.flush();
        em.clear();

        UUID esperado = a.getId().compareTo(b.getId()) > 0 ? a.getId() : b.getId();
        Cuadrante ganador = cuadrantes
                .findTopByUsuarioIdAndSemanaInicioOrderByCreadoEnDescIdDesc(usuarioId, lunes)
                .orElseThrow();

        assertThat(ganador.getId()).isEqualTo(esperado);
    }
}
