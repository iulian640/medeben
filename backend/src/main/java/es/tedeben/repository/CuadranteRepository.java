package es.tedeben.repository;

import es.tedeben.domain.horario.Cuadrante;
import org.springframework.data.repository.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Cuadrantes de horario. A propósito NO extiende JpaRepository: la libreta es
 * append-only (D38), así que este repositorio solo expone guardar y leer —
 * borrar o machacar versiones no existe ni por accidente.
 */
public interface CuadranteRepository extends Repository<Cuadrante, UUID> {

    Cuadrante save(Cuadrante cuadrante);

    /** Última versión de la semana tipo. */
    Optional<Cuadrante> findTopByUsuarioIdAndSemanaInicioIsNullOrderByCreadoEnDescIdDesc(UUID usuarioId);

    /** La semana tipo que estaba vigente en un momento dado (para no reescribir el pasado). */
    Optional<Cuadrante> findTopByUsuarioIdAndSemanaInicioIsNullAndCreadoEnBeforeOrderByCreadoEnDescIdDesc(
            UUID usuarioId, OffsetDateTime antesDe);

    /** Última edición de una semana concreta. */
    Optional<Cuadrante> findTopByUsuarioIdAndSemanaInicioOrderByCreadoEnDescIdDesc(UUID usuarioId, LocalDate semanaInicio);
}
