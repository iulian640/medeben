package es.tedeben.domain.horario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Una versión del cuadrante del usuario. APPEND-ONLY (D38): las versiones
 * nunca se editan ni se borran; cambiar el horario = añadir una versión nueva.
 * Las versiones antiguas son el historial con fecha que prueba los cambios de
 * última hora (D6).
 *
 * <p>{@code semanaInicio} nulo = semana tipo (se repite sola); con fecha
 * (siempre lunes) = edición de esa semana concreta.</p>
 */
@Entity
@Table(name = "cuadrantes")
public class Cuadrante implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "semana_inicio")
    private LocalDate semanaInicio;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<DiaCuadrante> dias = List.of();

    @Column(name = "creado_en", nullable = false)
    private OffsetDateTime creadoEn;

    /** true hasta que la entidad se persiste o se carga: save() hace persist(), no merge+SELECT. */
    @Transient
    private boolean nuevo = true;

    protected Cuadrante() {
        // requerido por JPA
    }

    /**
     * @param creadoEn sello de creación, SIEMPRE del reloj inyectado del
     *                 servicio (zona controlada): es el dato probatorio de la
     *                 libreta (D38), no puede depender de la zona de la JVM.
     */
    public Cuadrante(UUID usuarioId, LocalDate semanaInicio, List<DiaCuadrante> dias, OffsetDateTime creadoEn) {
        this.id = UUID.randomUUID();
        this.usuarioId = usuarioId;
        this.semanaInicio = semanaInicio;
        this.dias = List.copyOf(dias);
        this.creadoEn = java.util.Objects.requireNonNull(creadoEn, "creadoEn: el sello es obligatorio");
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
    void exigeSello() {
        if (creadoEn == null) {
            // Nunca rellenar aquí con el reloj del sistema: el sello legal
            // viene del servicio con su Clock inyectado (review H2).
            throw new IllegalStateException("Cuadrante sin sello de creación");
        }
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public LocalDate getSemanaInicio() {
        return semanaInicio;
    }

    public List<DiaCuadrante> getDias() {
        return List.copyOf(dias);
    }

    public OffsetDateTime getCreadoEn() {
        return creadoEn;
    }
}
