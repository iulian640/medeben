package es.tedeben.repository;

import es.tedeben.domain.fichaje.Apunte;
import org.springframework.data.repository.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Diario de fichajes. Como CuadranteRepository: a propósito NO extiende
 * JpaRepository — la libreta es append-only (D38), solo guardar y leer.
 */
public interface ApunteRepository extends Repository<Apunte, UUID> {

    Apunte save(Apunte apunte);

    /** El diario de un día, en orden de registro (el último de cada tipo gana). */
    List<Apunte> findByUsuarioIdAndFechaOrderByRegistradoEnAscIdAsc(UUID usuarioId, LocalDate fecha);

    /** El diario de un rango (semana, mes) para los agregados. */
    List<Apunte> findByUsuarioIdAndFechaBetweenOrderByFechaAscRegistradoEnAscIdAsc(
            UUID usuarioId, LocalDate desde, LocalDate hasta);
}
