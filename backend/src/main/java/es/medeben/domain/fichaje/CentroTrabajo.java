package es.medeben.domain.fichaje;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * El centro de trabajo declarado por el usuario (síntesis D4/D5). APPEND-ONLY
 * en su uso normal — declarar de nuevo o cerrar (BAJA) siempre inserta una
 * fila, mismo patrón que {@code Cuadrante} — con UNA excepción explícita del
 * contrato: el tombstone del art. 17 (declaración errónea, o supresión tras
 * 24 h) SÍ muta una fila existente, porque el dato personal (coordenadas) no
 * puede sobrevivir a una supresión legítima aunque la fila en sí se conserve
 * por su valor probatorio (fecha de declaración, historial).
 *
 * <p>Un centro {@link EstadoCentroTrabajo#ALTA} SIEMPRE tiene coordenadas
 * (CHECK en V9); tombstoning un ALTA lo pasa a BAJA como parte de la misma
 * operación — un centro "vigente" sin coordenadas es una contradicción.</p>
 */
@Entity
@Table(name = "centros_trabajo")
public class CentroTrabajo implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(length = 60)
    private String alias;

    @Column(precision = 8, scale = 5)
    private BigDecimal latitud;

    @Column(precision = 8, scale = 5)
    private BigDecimal longitud;

    @Column(name = "radio_metros", nullable = false)
    private int radioMetros;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoCentroTrabajo estado;

    @Column(name = "declarado_en", nullable = false)
    private OffsetDateTime declaradoEn;

    /** true hasta que la entidad se persiste o se carga: save() hace persist(), no merge+SELECT. */
    @Transient
    private boolean nuevo = true;

    protected CentroTrabajo() {
        // requerido por JPA
    }

    /** Declaración nueva (ALTA): el usuario marca su centro estando allí. */
    public CentroTrabajo(UUID usuarioId, String alias, BigDecimal latitud, BigDecimal longitud,
                         int radioMetros, OffsetDateTime declaradoEn) {
        this.id = UUID.randomUUID();
        this.usuarioId = Objects.requireNonNull(usuarioId);
        this.alias = alias;
        this.latitud = Objects.requireNonNull(latitud, "latitud: obligatoria en un centro ALTA");
        this.longitud = Objects.requireNonNull(longitud, "longitud: obligatoria en un centro ALTA");
        this.radioMetros = radioMetros;
        this.estado = EstadoCentroTrabajo.ALTA;
        this.declaradoEn = Objects.requireNonNull(declaradoEn, "declaradoEn: el sello es obligatorio");
    }

    /**
     * Fila de cierre (BAJA): copia lat/lon/radio del centro vigente que cierra
     * (corrección del verificador técnico — sin copia, la fila BAJA no cumpliría
     * el CHECK de coordenadas si algún día se exigieran también en BAJA, y
     * documenta qué era "el centro" en el momento del cierre).
     */
    public static CentroTrabajo baja(CentroTrabajo vigenteQueCierra, OffsetDateTime declaradoEn) {
        CentroTrabajo fila = new CentroTrabajo();
        fila.id = UUID.randomUUID();
        fila.usuarioId = vigenteQueCierra.usuarioId;
        fila.alias = vigenteQueCierra.alias;
        fila.latitud = vigenteQueCierra.latitud;
        fila.longitud = vigenteQueCierra.longitud;
        fila.radioMetros = vigenteQueCierra.radioMetros;
        fila.estado = EstadoCentroTrabajo.BAJA;
        fila.declaradoEn = Objects.requireNonNull(declaradoEn, "declaradoEn: el sello es obligatorio");
        return fila;
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
        if (declaradoEn == null) {
            throw new IllegalStateException("CentroTrabajo sin sello de declaración");
        }
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getAlias() {
        return alias;
    }

    public BigDecimal getLatitud() {
        return latitud;
    }

    public BigDecimal getLongitud() {
        return longitud;
    }

    public int getRadioMetros() {
        return radioMetros;
    }

    public EstadoCentroTrabajo getEstado() {
        return estado;
    }

    public boolean isVigente() {
        return estado == EstadoCentroTrabajo.ALTA;
    }

    public OffsetDateTime getDeclaradoEn() {
        return declaradoEn;
    }

    /**
     * Tombstone (art. 17): anula alias/coordenadas. Si la fila estaba ALTA
     * pasa a BAJA en la misma operación — desviación mínima documentada: no
     * puede quedar "vigente" un centro sin coordenadas (CHECK de V9).
     */
    public void tombstone() {
        this.alias = null;
        this.latitud = null;
        this.longitud = null;
        this.estado = EstadoCentroTrabajo.BAJA;
    }
}
