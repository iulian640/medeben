package es.medeben.repository;

import es.medeben.domain.fichaje.Apunte;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Lectura de UN apunte por id. Repositorio NUEVO y aparte de
 * {@link ApunteRepository} a propósito: {@code ApunteRepository} está en la
 * lista de intocables (regla de oro del contrato de "Anotar dónde fichas") y
 * no expone {@code findById}. Este repositorio es de solo lectura (ni
 * {@code save} ni {@code delete}) sobre la MISMA tabla append-only: lo único
 * que necesita {@code UbicacionService} para comprobar existencia y dueño
 * antes de adjuntar una ubicación.
 */
public interface ApunteLecturaRepository extends Repository<Apunte, UUID> {

    Optional<Apunte> findById(UUID id);
}
