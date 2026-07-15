package es.medeben.repository;

import es.medeben.domain.usuario.VerificacionEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface VerificacionEmailRepository extends JpaRepository<VerificacionEmail, UUID> {

    Optional<VerificacionEmail> findByTokenHash(String tokenHash);

    /**
     * Reclama el token para verificar: ATÓMICO (el WHERE decide, no una
     * lectura previa; mismo razonamiento que {@link SesionRepository}).
     * Devuelve 0 si ya se usó o si está caducado — un token del correo se
     * gasta UNA vez y dentro de su vigencia.
     */
    @Modifying(clearAutomatically = true)
    @Query("update VerificacionEmail v set v.usadaEn = :ahora "
            + "where v.id = :id and v.usadaEn is null and v.caducaEn > :ahora")
    int marcaUsadaSiIntacta(@Param("id") UUID id, @Param("ahora") Instant ahora);

    /**
     * Purga: fuera lo caducado y lo ya usado hace tiempo (el token gastado no
     * abre nada; se retiene un poco por trazabilidad, como en sesiones).
     */
    @Modifying
    @Query("delete from VerificacionEmail v where v.caducaEn < :ahora "
            + "or (v.usadaEn is not null and v.usadaEn < :limiteUsadas)")
    int purga(@Param("ahora") Instant ahora, @Param("limiteUsadas") Instant limiteUsadas);
}
