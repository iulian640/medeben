package es.medeben.repository;

import es.medeben.domain.fichaje.UbicacionApunte;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ubicaciones anotadas al fichar. A propósito NO extiende JpaRepository (mismo
 * criterio que ApunteRepository): la fila es inmutable salvo las dos
 * excepciones RGPD explícitas del contrato (supresión granular y tombstone de
 * centro), y el borrado total del histórico ({@link #deleteByUsuarioId}).
 */
public interface UbicacionApunteRepository extends Repository<UbicacionApunte, UUID> {

    UbicacionApunte save(UbicacionApunte ubicacion);

    boolean existsById(UUID apunteId);

    Optional<UbicacionApunte> findById(UUID apunteId);

    /** El mes/rango de un usuario en UNA consulta (anti N+1, misma disciplina que estadosDelPeriodo). */
    List<UbicacionApunte> findByUsuarioIdAndFechaBetween(UUID usuarioId, LocalDate desde, LocalDate hasta);

    /** Borrado total del histórico de ubicaciones (art. 17): el diario de apuntes queda intacto. */
    void deleteByUsuarioId(UUID usuarioId);

    /**
     * Propagación del tombstone de un centro (art. 17 sobre {@code CentroTrabajo}):
     * anula la copia congelada de coordenadas en todas las filas que lo
     * referencian. Se conservan distancia, centro_radio y veredicto.
     *
     * <p>{@code flushAutomatically = true} es OBLIGATORIO (bug CRÍTICO
     * reproducido contra PostgreSQL real): en {@code CentroTrabajoService.purga()}
     * el UPDATE pendiente de {@code centro.tombstone()} (sobre
     * {@code centros_trabajo}) y este bulk UPDATE (sobre
     * {@code ubicaciones_apunte}) no comparten query space, así que Hibernate
     * NO auto-flushea antes de ejecutar el bulk — sin este flag el tombstone
     * del centro se pierde en {@code em.clear()} y la transacción commitea sin
     * tocar la fila del centro.</p>
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update UbicacionApunte u set u.centroLatitud = null, u.centroLongitud = null "
            + "where u.centroId = :centroId")
    int anulaCoordenadasDelCentro(@Param("centroId") UUID centroId);

    /**
     * Purga de retención (15 meses desde el fichaje, contrato §Retención):
     * borra lo caducado salvo usuarios con reclamación en curso declarada.
     * NUNCA toca la tabla de apuntes.
     */
    @Modifying
    @Query("delete from UbicacionApunte u where u.registradaEn < :limite "
            + "and u.usuarioId not in (select usu.id from Usuario usu where usu.reclamacionEnCurso = true)")
    int purgaCaducadas(@Param("limite") OffsetDateTime limite);
}
