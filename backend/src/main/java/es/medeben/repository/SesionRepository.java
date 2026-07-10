package es.medeben.repository;

import es.medeben.domain.usuario.Sesion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SesionRepository extends JpaRepository<Sesion, UUID> {

    Optional<Sesion> findByTokenHash(String tokenHash);

    /**
     * Reclama la sesión para rotarla: ATÓMICO (el WHERE decide, no una lectura
     * previa). Devuelve 0 si otro refresh la gastó antes o si está revocada —
     * dos refresh simultáneos con el mismo token no pueden ganar los dos.
     */
    @Modifying
    @Query("update Sesion s set s.usadaEn = :ahora "
            + "where s.id = :id and s.usadaEn is null and s.revocadaEn is null")
    int marcaUsadaSiIntacta(@Param("id") UUID id, @Param("ahora") Instant ahora);

    /** Revoca TODAS las sesiones vivas del usuario (reuso detectado = posible robo). */
    @Modifying
    @Query("update Sesion s set s.revocadaEn = :ahora "
            + "where s.usuarioId = :usuarioId and s.revocadaEn is null")
    int revocaTodas(@Param("usuarioId") UUID usuarioId, @Param("ahora") Instant ahora);

    /** Revoca por hash (logout). Idempotente: 0 filas si no existe o ya estaba revocada. */
    @Modifying
    @Query("update Sesion s set s.revocadaEn = :ahora "
            + "where s.tokenHash = :tokenHash and s.revocadaEn is null")
    int revocaPorHash(@Param("tokenHash") String tokenHash, @Param("ahora") Instant ahora);
}
