package es.medeben.repository;

import es.medeben.domain.fichaje.CentroTrabajo;
import org.springframework.data.repository.Repository;

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

    /** Borrado físico (purgar &lt;24h): la cascada de V9 arrastra sus ubicaciones. */
    void deleteById(UUID id);
}
