package es.medeben.domain.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Consentimiento art. 7.1 RGPD para "Anotar dónde fichas": hay que poder
 * acreditar que se prestó, cuándo y sobre QUÉ TEXTO exacto. Revocar RELLENA
 * {@code revocadoEn} (no borra la fila: la trazabilidad es lo que se acredita
 * ante la AEPD); re-aceptar crea una fila nueva, no reabre la vieja.
 */
@Entity
@Table(name = "consentimientos_ubicacion")
public class ConsentimientoUbicacion implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "version_texto", nullable = false, length = 10)
    private String versionTexto;

    @Column(name = "texto_sha256", nullable = false, length = 64)
    private String textoSha256;

    @Column(name = "aceptado_en", nullable = false)
    private OffsetDateTime aceptadoEn;

    @Column(name = "revocado_en")
    private OffsetDateTime revocadoEn;

    /** true hasta que la entidad se persiste o se carga: save() hace persist(), no merge+SELECT. */
    @Transient
    private boolean nuevo = true;

    protected ConsentimientoUbicacion() {
        // requerido por JPA
    }

    public ConsentimientoUbicacion(UUID usuarioId, String versionTexto, String textoSha256,
                                   OffsetDateTime aceptadoEn) {
        this.id = UUID.randomUUID();
        this.usuarioId = Objects.requireNonNull(usuarioId);
        this.versionTexto = Objects.requireNonNull(versionTexto);
        this.textoSha256 = Objects.requireNonNull(textoSha256);
        this.aceptadoEn = Objects.requireNonNull(aceptadoEn, "aceptadoEn: el sello es obligatorio");
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return nuevo;
    }

    @PostLoad
    @PostPersist
    void yaPersistido() {
        this.nuevo = false;
    }

    @PrePersist
    @PreUpdate
    void exigeSello() {
        if (aceptadoEn == null) {
            throw new IllegalStateException("ConsentimientoUbicacion sin sello de aceptación");
        }
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getVersionTexto() {
        return versionTexto;
    }

    public String getTextoSha256() {
        return textoSha256;
    }

    public OffsetDateTime getAceptadoEn() {
        return aceptadoEn;
    }

    public OffsetDateTime getRevocadoEn() {
        return revocadoEn;
    }

    public boolean isVigente() {
        return revocadoEn == null;
    }

    /** Revoca (no borra): rellena el sello de revocación. */
    public void revoca(OffsetDateTime revocadoEn) {
        this.revocadoEn = Objects.requireNonNull(revocadoEn, "revocadoEn: el sello es obligatorio");
    }
}
