package es.medeben.domain.fichaje;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Un apunte del diario de fichajes. APPEND-ONLY (D38): nunca se edita ni se
 * borra; corregir = apunte nuevo. El estado de un día se deriva de su diario.
 *
 * <p>{@code fecha} es el día del TURNO, no del reloj: la salida de un turno de
 * cierre fichada a las 02:00 pertenece al día anterior.</p>
 */
@Entity
@Table(name = "apuntes")
public class Apunte implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoApunte tipo;

    /** "HH:mm" declarada por el trabajador; nula en las ausencias. */
    @Column(length = 5)
    private String hora;

    /**
     * Solo ausencias: por qué no fue (opcional). OJO RGPD: texto libre que puede
     * ser dato de salud (art. 9; base: defensa de derechos, art. 9.2.f). Nunca
     * loguearlo ni añadir un toString() que lo incluya; en el frontend, jamás
     * renderizarlo con v-html.
     */
    @Column(length = 200)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private OrigenApunte origen;

    /** Sello del servidor (reloj inyectado): el dato probatorio. */
    @Column(name = "registrado_en", nullable = false)
    private OffsetDateTime registradoEn;

    /** true hasta que la entidad se persiste o se carga: save() hace persist(), no merge+SELECT. */
    @Transient
    private boolean nuevo = true;

    protected Apunte() {
        // requerido por JPA
    }

    public Apunte(UUID usuarioId, LocalDate fecha, TipoApunte tipo, String hora,
                  String motivo, OrigenApunte origen, OffsetDateTime registradoEn) {
        this.id = UUID.randomUUID();
        this.usuarioId = usuarioId;
        this.fecha = fecha;
        this.tipo = tipo;
        this.hora = hora;
        this.motivo = motivo;
        this.origen = origen;
        this.registradoEn = Objects.requireNonNull(registradoEn, "registradoEn: el sello es obligatorio");
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
        if (registradoEn == null) {
            // Nunca rellenar aquí con el reloj del sistema: el sello legal
            // viene del servicio con su Clock inyectado.
            throw new IllegalStateException("Apunte sin sello de registro");
        }
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public TipoApunte getTipo() {
        return tipo;
    }

    public String getHora() {
        return hora;
    }

    public String getMotivo() {
        return motivo;
    }

    public OrigenApunte getOrigen() {
        return origen;
    }

    public OffsetDateTime getRegistradoEn() {
        return registradoEn;
    }
}
