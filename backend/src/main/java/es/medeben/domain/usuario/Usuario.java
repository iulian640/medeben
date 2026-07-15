package es.medeben.domain.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Usuario de la app. Minimización de datos (RGPD, D11): email + hash de
 * contraseña, nada más hasta que una feature lo justifique.
 */
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime creadoEn;

    /**
     * Estado de la verificación de email (cierre de la enumeración, R7). Los
     * usuarios NUEVOS nacen sin verificar; los anteriores a la feature los
     * dejó verificados el backfill de V8. Que esté a false NO bloquea el
     * login (decisión de producto): solo activa el aviso en la app.
     */
    @Column(name = "email_verificado", nullable = false)
    private boolean emailVerificado = false;

    @Column(name = "verificado_en")
    private Instant verificadoEn;

    protected Usuario() {
        // requerido por JPA
    }

    public Usuario(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public OffsetDateTime getCreadoEn() {
        return creadoEn;
    }

    public boolean isEmailVerificado() {
        return emailVerificado;
    }

    public Instant getVerificadoEn() {
        return verificadoEn;
    }

    /** Sella la verificación. Idempotente a efectos prácticos: el consumo del token ya es único. */
    public void marcaVerificado(Instant ahora) {
        this.emailVerificado = true;
        this.verificadoEn = Objects.requireNonNull(ahora);
    }

    /**
     * Sobrescribe el hash de la contraseña. Solo tiene un uso legítimo: el
     * re-registro de una cuenta SIN verificar (la propiedad del email no está
     * probada, así que el último en registrarlo se la queda — anti-okupación).
     */
    public void actualizaPasswordHash(String passwordHash) {
        this.passwordHash = Objects.requireNonNull(passwordHash);
    }
}
