package es.medeben.repository;

import es.medeben.domain.usuario.ConsentimientoUbicacion;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Registro de consentimientos de ubicación. A propósito NO extiende
 * JpaRepository: cada aceptación es una fila nueva (nunca se reescribe), y
 * revocar es cargar la última vigente y guardarla con su sello de revocación.
 */
public interface ConsentimientoUbicacionRepository extends Repository<ConsentimientoUbicacion, UUID> {

    ConsentimientoUbicacion save(ConsentimientoUbicacion consentimiento);

    Optional<ConsentimientoUbicacion> findFirstByUsuarioIdOrderByAceptadoEnDesc(UUID usuarioId);
}
