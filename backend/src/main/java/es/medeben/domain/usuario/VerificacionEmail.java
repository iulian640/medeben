package es.medeben.domain.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Un token de verificación de email pendiente. Mismo patrón endurecido que
 * {@link Sesion} (B4): solo se guarda el hash SHA-256 del token opaco que
 * viaja en el correo (si la BD se filtra, los tokens no se reconstruyen) y
 * {@code usadaEn} implementa el un-solo-uso — la transición de estado va por
 * consulta atómica del repositorio, no por setters: dos clicks simultáneos
 * en el enlace no pueden ganar los dos.
 */
@Entity
@Table(name = "verificaciones_email")
public class VerificacionEmail implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "emitida_en", nullable = false)
    private Instant emitidaEn;

    @Column(name = "caduca_en", nullable = false)
    private Instant caducaEn;

    @Column(name = "usada_en")
    private Instant usadaEn;

    /** true hasta que la entidad se persiste o se carga: save() hace persist(), no merge+SELECT. */
    @Transient
    private boolean nueva = true;

    protected VerificacionEmail() {
        // requerido por JPA
    }

    public VerificacionEmail(UUID usuarioId, String tokenHash, Instant emitidaEn, Instant caducaEn) {
        this.id = UUID.randomUUID();
        this.usuarioId = Objects.requireNonNull(usuarioId);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.emitidaEn = Objects.requireNonNull(emitidaEn);
        this.caducaEn = Objects.requireNonNull(caducaEn);
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return nueva;
    }

    @PostPersist
    @PostLoad
    void yaPersistida() {
        this.nueva = false;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getEmitidaEn() {
        return emitidaEn;
    }

    public Instant getCaducaEn() {
        return caducaEn;
    }

    public Instant getUsadaEn() {
        return usadaEn;
    }
}
