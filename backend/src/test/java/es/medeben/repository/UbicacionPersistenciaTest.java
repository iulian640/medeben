package es.medeben.repository;

import es.medeben.domain.fichaje.Apunte;
import es.medeben.domain.fichaje.CentroTrabajo;
import es.medeben.domain.fichaje.OrigenApunte;
import es.medeben.domain.fichaje.TipoApunte;
import es.medeben.domain.fichaje.UbicacionApunte;
import es.medeben.domain.fichaje.VeredictoUbicacion;
import es.medeben.domain.usuario.ConsentimientoUbicacion;
import es.medeben.domain.usuario.Usuario;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * "Anotar dónde fichas" contra PostgreSQL real (Testcontainers; se salta sin
 * Docker). Cubre lo que los mocks no pueden: los CHECKs de V9/V10, el cascade
 * de borrado de usuario, y que borrar el histórico de ubicaciones NUNCA toca
 * la tabla de apuntes — el diario probatorio es append-only y esto lo
 * verifica contra el esquema real, no contra un mock que se lo crea.
 *
 * <p>Correcciones del contrato aplicadas al plan de tests de la síntesis:
 * {@code borrarElApunteArrastraSuUbicacion} se degrada a assert de esquema
 * (los apuntes no se borran nunca en la aplicación — {@code ApunteRepository}
 * no expone {@code delete} — así que probar el escenario en runtime probaría
 * algo que el sistema no permite; lo que importa es que la FK esté bien
 * declarada, por si algún día hiciera falta). Nombre correcto:
 * {@code borrarLasUbicacionesNoTocaNingunApunte}.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class UbicacionPersistenciaTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final OffsetDateTime SELLO = OffsetDateTime.parse("2026-07-26T10:00:00+02:00");
    private static final BigDecimal LAT = new BigDecimal("40.41675");
    private static final BigDecimal LON = new BigDecimal("-3.70379");

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private ApunteRepository apuntes;

    @Autowired
    private ApunteLecturaRepository apuntesLectura;

    @Autowired
    private CentroTrabajoRepository centros;

    @Autowired
    private UbicacionApunteRepository ubicaciones;

    @Autowired
    private ConsentimientoUbicacionRepository consentimientos;

    @Autowired
    private TestEntityManager em;

    private UUID usuarioId;

    @BeforeEach
    void creaUsuario() {
        usuarioId = usuarios.saveAndFlush(
                new Usuario("gps-" + UUID.randomUUID() + "@example.com", "{noop}hash")).getId();
    }

    // --- V9/V10 aplican limpio y los apuntes siguen legibles (migración) ---

    @Test
    @DisplayName("la migración aplica limpio sobre una base con V1-V8 y los apuntes siguen legibles")
    void laMigracionAplicaLimpioYLosApuntesSiguenLegibles() {
        Apunte apunte = apuntes.save(new Apunte(usuarioId, LocalDate.of(2026, 7, 26), TipoApunte.ENTRADA,
                "10:00", null, OrigenApunte.CONFIRMADO, SELLO));
        em.flush();
        em.clear();

        assertThat(apuntesLectura.findById(apunte.getId())).isPresent();
    }

    // --- CHECK de vocabulario (V9) ---

    @Test
    @DisplayName("el CHECK de vocabulario rechaza un veredicto inventado (chk_ubicaciones_veredicto)")
    void elCheckDeVocabularioRechazaUnVeredictoInventado() {
        Apunte apunte = apuntes.save(new Apunte(usuarioId, LocalDate.of(2026, 7, 26), TipoApunte.ENTRADA,
                "10:00", null, OrigenApunte.CONFIRMADO, SELLO));
        CentroTrabajo centro = centros.save(new CentroTrabajo(usuarioId, "El bar", LAT, LON, 150, SELLO));
        em.flush();

        Query insert = em.getEntityManager().createNativeQuery(
                        "INSERT INTO ubicaciones_apunte (apunte_id, usuario_id, fecha, latitud, longitud, "
                                + "precision_metros, centro_id, centro_latitud, centro_longitud, centro_radio, "
                                + "distancia_metros, veredicto, registrada_en) VALUES (:apunte, :usuario, "
                                + "DATE '2026-07-26', :lat, :lon, 20, :centro, :lat, :lon, 150, 10, "
                                + ":veredicto, now())")
                .setParameter("apunte", apunte.getId())
                .setParameter("usuario", usuarioId)
                .setParameter("lat", LAT)
                .setParameter("lon", LON)
                .setParameter("centro", centro.getId())
                .setParameter("veredicto", "COMPATIBLE_A_MEDIAS");

        assertThatThrownBy(insert::executeUpdate)
                .rootCause()
                .hasMessageContaining("ck_ubicaciones_veredicto");
    }

    @Test
    @DisplayName("un centro ALTA sin coordenadas viola el CHECK ck_centros_alta_tiene_coordenadas")
    void unCentroAltaSinCoordenadasViolaElCheck() {
        Query insert = em.getEntityManager().createNativeQuery(
                        "INSERT INTO centros_trabajo (id, usuario_id, alias, latitud, longitud, radio_metros, "
                                + "estado, declarado_en) VALUES (:id, :usuario, NULL, NULL, NULL, 150, 'ALTA', now())")
                .setParameter("id", UUID.randomUUID())
                .setParameter("usuario", usuarioId);

        assertThatThrownBy(insert::executeUpdate)
                .rootCause()
                .hasMessageContaining("ck_centros_alta_tiene_coordenadas");
    }

    // --- Cascade de borrado de usuario ---

    @Test
    @DisplayName("borrar el usuario arrastra centros, ubicaciones y consentimientos")
    void borrarElUsuarioArrastraCentrosUbicacionesYConsentimientos() {
        Apunte apunte = apuntes.save(new Apunte(usuarioId, LocalDate.of(2026, 7, 26), TipoApunte.ENTRADA,
                "10:00", null, OrigenApunte.CONFIRMADO, SELLO));
        CentroTrabajo centro = centros.save(new CentroTrabajo(usuarioId, "El bar", LAT, LON, 150, SELLO));
        em.flush();
        ubicaciones.save(new UbicacionApunte(apunte.getId(), usuarioId, apunte.getFecha(), LAT, LON, 20,
                centro.getId(), LAT, LON, 150, 10, VeredictoUbicacion.DENTRO, SELLO));
        consentimientos.save(new ConsentimientoUbicacion(usuarioId, "1.0", "hash", SELLO));
        em.flush();
        em.clear();

        usuarios.deleteById(usuarioId);
        usuarios.flush();
        em.clear();

        assertThat(cuenta("centros_trabajo", usuarioId)).isZero();
        assertThat(cuenta("ubicaciones_apunte", usuarioId)).isZero();
        assertThat(cuenta("consentimientos_ubicacion", usuarioId)).isZero();
    }

    // --- El diario append-only nunca se toca desde la supresión de ubicaciones ---

    @Test
    @DisplayName("borrar las ubicaciones no toca ningún apunte — protege el append-only del diario")
    void borrarLasUbicacionesNoTocaNingunApunte() {
        Apunte apunte = apuntes.save(new Apunte(usuarioId, LocalDate.of(2026, 7, 26), TipoApunte.ENTRADA,
                "10:00", null, OrigenApunte.CONFIRMADO, SELLO));
        CentroTrabajo centro = centros.save(new CentroTrabajo(usuarioId, "El bar", LAT, LON, 150, SELLO));
        em.flush();
        ubicaciones.save(new UbicacionApunte(apunte.getId(), usuarioId, apunte.getFecha(), LAT, LON, 20,
                centro.getId(), LAT, LON, 150, 10, VeredictoUbicacion.DENTRO, SELLO));
        em.flush();
        em.clear();

        ubicaciones.deleteByUsuarioId(usuarioId);
        em.flush();
        em.clear();

        assertThat(cuenta("ubicaciones_apunte", usuarioId)).isZero();
        // El diario probatorio permanece intacto: mismo apunte, misma fila.
        assertThat(apuntesLectura.findById(apunte.getId())).isPresent();
        assertThat(cuenta("apuntes", usuarioId)).isEqualTo(1);
    }

    // --- Degradado (correción BAJO del verificador técnico): assert de esquema, no de comportamiento ---

    @Test
    @DisplayName("el esquema declara ON DELETE CASCADE de apuntes a ubicaciones_apunte (apunte_id)")
    void elEsquemaDeclaraCascadeDeApuntesAUbicaciones() {
        String reglaBorrado = (String) em.getEntityManager().createNativeQuery(
                        "SELECT rc.delete_rule FROM information_schema.referential_constraints rc "
                                + "JOIN information_schema.table_constraints tc "
                                + "ON tc.constraint_name = rc.constraint_name "
                                + "WHERE tc.table_name = 'ubicaciones_apunte' "
                                + "AND tc.constraint_type = 'FOREIGN KEY' "
                                + "AND rc.unique_constraint_name = (SELECT constraint_name FROM "
                                + "information_schema.table_constraints WHERE table_name = 'apuntes' "
                                + "AND constraint_type = 'PRIMARY KEY')")
                .getSingleResult();

        assertThat(reglaBorrado).isEqualTo("CASCADE");
    }

    // --- Duplicado ---

    @Test
    @DisplayName("no se pueden insertar dos ubicaciones para el mismo apunte (PK = apunte_id)")
    void noSePuedenInsertarDosUbicacionesParaElMismoApunte() {
        Apunte apunte = apuntes.save(new Apunte(usuarioId, LocalDate.of(2026, 7, 26), TipoApunte.ENTRADA,
                "10:00", null, OrigenApunte.CONFIRMADO, SELLO));
        CentroTrabajo centro = centros.save(new CentroTrabajo(usuarioId, "El bar", LAT, LON, 150, SELLO));
        em.flush();
        ubicaciones.save(new UbicacionApunte(apunte.getId(), usuarioId, apunte.getFecha(), LAT, LON, 20,
                centro.getId(), LAT, LON, 150, 10, VeredictoUbicacion.DENTRO, SELLO));
        em.flush();
        em.clear();

        // La violación surge en el flush de un EntityManager crudo (TestEntityManager),
        // que no pasa por el proxy de Spring Data: no se traduce a
        // DataIntegrityViolationException, sale como la excepción nativa de Hibernate.
        // Lo que importa —que la BD lo impide de verdad— sí queda probado.
        assertThatThrownBy(() -> {
            ubicaciones.save(new UbicacionApunte(apunte.getId(), usuarioId, apunte.getFecha(), LAT, LON, 25,
                    centro.getId(), LAT, LON, 150, 12, VeredictoUbicacion.DENTRO, SELLO));
            em.flush();
        }).rootCause().hasMessageContaining("ubicaciones_apunte_pkey");
    }

    private long cuenta(String tabla, UUID usuarioId) {
        return ((Number) em.getEntityManager()
                .createNativeQuery("SELECT count(*) FROM " + tabla + " WHERE usuario_id = :id")
                .setParameter("id", usuarioId)
                .getSingleResult()).longValue();
    }
}
