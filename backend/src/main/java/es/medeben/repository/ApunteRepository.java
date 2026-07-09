package es.medeben.repository;

import es.medeben.domain.fichaje.Apunte;
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

    /**
     * El diario de un rango de fechas (ambos extremos incluidos) en UNA consulta,
     * ordenado por fecha y, dentro de cada día, por orden de registro. Lo usa el
     * resumen mensual/anual para no lanzar una query por día (N+1).
     */
    List<Apunte> findByUsuarioIdAndFechaBetweenOrderByFechaAscRegistradoEnAscIdAsc(
            UUID usuarioId, LocalDate desde, LocalDate hasta);
}
