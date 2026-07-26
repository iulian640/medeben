package es.medeben.repository;

import es.medeben.domain.fichaje.CentroTrabajo;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Centros de trabajo declarados. A propósito NO extiende JpaRepository (mismo
 * criterio que ApunteRepository/CuadranteRepository): el append-only es la
 * norma; el tombstone (art. 17) se hace cargando la fila y guardándola de
 * nuevo (mutación explícita en {@link CentroTrabajo#tombstone()}), nunca con
 * un delete/update genérico.
 */
public interface CentroTrabajoRepository extends Repository<CentroTrabajo, UUID> {

    CentroTrabajo save(CentroTrabajo centro);

    Optional<CentroTrabajo> findById(UUID id);

    /**
     * La última DECLARACIÓN del usuario, sea ALTA o BAJA (corrección MEDIO del
     * verificador técnico): un filtro {@code AndEstado(ALTA)} resucitaría un
     * ALTA anterior a una BAJA posterior. El servicio decide "vigente"
     * comprobando {@link CentroTrabajo#isVigente()} sobre esta fila.
     */
    Optional<CentroTrabajo> findFirstByUsuarioIdOrderByDeclaradoEnDesc(UUID usuarioId);

    /**
     * TODAS las declaraciones (ALTA y BAJA) del usuario — art. 17: "borrar
     * todo tu histórico" tiene que poder alcanzar cada fila, no solo la
     * vigente (un usuario que declaró/cerró varias veces acumula varias filas
     * con coordenadas, cada una purgable solo con su propio id hasta ahora).
     */
    List<CentroTrabajo> findAllByUsuarioId(UUID usuarioId);

    /** Borrado físico (purgar &lt;24h): la cascada de V9 arrastra sus ubicaciones. */
    void deleteById(UUID id);
}
