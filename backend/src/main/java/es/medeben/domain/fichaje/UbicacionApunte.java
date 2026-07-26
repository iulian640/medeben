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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

// OJO RGPD: dato de localización (art. 4.1). Base jurídica: consentimiento
// inequívoco y expreso (art. 6.1.a), NO 6.1.b — el servicio funciona entero
// sin esto. Precisión deliberadamente aproximada (COARSE). Nunca se loguea,
// nunca sale en una respuesta de API, nunca aparece en el PDF principal.
/**
 * La ubicación anotada en el momento de un fichaje (síntesis §4/D5/D6). 1:0..1
 * con {@code Apunte}, identidad = {@code apunteId}. NO es append-only como
 * apuntes: el usuario puede suprimir su histórico (borrado total o granular,
 * art. 17), lo que muta latitud/longitud/distancia/veredicto de esta fila sin
 * tocar el diario probatorio. El resto de campos (precisión, centro_radio,
 * sello) se conservan siempre.
 */
@Entity
@Table(name = "ubicaciones_apunte")
public class UbicacionApunte implements Persistable<UUID> {

    @Id
    @Column(name = "apunte_id")
    private UUID apunteId;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(precision = 8, scale = 5)
    private BigDecimal latitud;

    @Column(precision = 8, scale = 5)
    private BigDecimal longitud;

    @Column(name = "precision_metros", nullable = false)
    private int precisionMetros;

    @Column(name = "centro_id", nullable = false)
    private UUID centroId;

    @Column(name = "centro_latitud", precision = 8, scale = 5)
    private BigDecimal centroLatitud;

    @Column(name = "centro_longitud", precision = 8, scale = 5)
    private BigDecimal centroLongitud;

    @Column(name = "centro_radio", nullable = false)
    private int centroRadio;

    @Column(name = "distancia_metros")
    private Integer distanciaMetros;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VeredictoUbicacion veredicto;

    /** NULL = desconocido (v1 no detecta mock). */
    @Column
    private Boolean simulada;

    @Column(name = "registrada_en", nullable = false)
    private OffsetDateTime registradaEn;

    /** true hasta que la entidad se persiste o se carga: save() hace persist(), no merge+SELECT. */
    @Transient
    private boolean nueva = true;

    protected UbicacionApunte() {
        // requerido por JPA
    }

    public UbicacionApunte(UUID apunteId, UUID usuarioId, LocalDate fecha, BigDecimal latitud,
                           BigDecimal longitud, int precisionMetros, UUID centroId,
                           BigDecimal centroLatitud, BigDecimal centroLongitud, int centroRadio,
                           int distanciaMetros, VeredictoUbicacion veredicto,
                           OffsetDateTime registradaEn) {
        this.apunteId = Objects.requireNonNull(apunteId);
        this.usuarioId = Objects.requireNonNull(usuarioId);
        this.fecha = Objects.requireNonNull(fecha);
        this.latitud = Objects.requireNonNull(latitud);
        this.longitud = Objects.requireNonNull(longitud);
        this.precisionMetros = precisionMetros;
        this.centroId = Objects.requireNonNull(centroId);
        this.centroLatitud = Objects.requireNonNull(centroLatitud);
        this.centroLongitud = Objects.requireNonNull(centroLongitud);
        this.centroRadio = centroRadio;
        this.distanciaMetros = distanciaMetros;
        this.veredicto = Objects.requireNonNull(veredicto);
        this.registradaEn = Objects.requireNonNull(registradaEn, "registradaEn: el sello es obligatorio");
    }

    @Override
    public UUID getId() {
        return apunteId;
    }

    @Override
    public boolean isNew() {
        return nueva;
    }

    @PostLoad
    @PostPersist
    void yaPersistida() {
        this.nueva = false;
    }

    @PrePersist
    @PreUpdate
    void exigeSello() {
        if (registradaEn == null) {
            throw new IllegalStateException("UbicacionApunte sin sello de registro");
        }
    }

    public UUID getApunteId() {
        return apunteId;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public BigDecimal getLatitud() {
        return latitud;
    }

    public BigDecimal getLongitud() {
        return longitud;
    }

    public int getPrecisionMetros() {
        return precisionMetros;
    }

    public UUID getCentroId() {
        return centroId;
    }

    public BigDecimal getCentroLatitud() {
        return centroLatitud;
    }

    public BigDecimal getCentroLongitud() {
        return centroLongitud;
    }

    public int getCentroRadio() {
        return centroRadio;
    }

    public Integer getDistanciaMetros() {
        return distanciaMetros;
    }

    public VeredictoUbicacion getVeredicto() {
        return veredicto;
    }

    public Boolean getSimulada() {
        return simulada;
    }

    public OffsetDateTime getRegistradaEn() {
        return registradaEn;
    }

    /**
     * Supresión granular (art. 17, contrato §Backend): anula coordenadas y
     * distancia; el veredicto pasa a SUPRIMIDA. NO borra la fila — un diario
     * con supresiones visibles no es un diario maquillado (D12/D38); el PDF
     * imprime "ubicación retirada por el titular".
     */
    public void suprime() {
        this.latitud = null;
        this.longitud = null;
        this.distanciaMetros = null;
        this.veredicto = VeredictoUbicacion.SUPRIMIDA;
    }

    /**
     * Propagación del tombstone del centro (art. 17 sobre {@code CentroTrabajo}):
     * anula la copia congelada de sus coordenadas. Se conservan distancia,
     * centro_radio y veredicto — es lo que el veredicto necesita para seguir
     * siendo legible sin exponer dónde estaba el centro.
     */
    public void anulaCoordenadasDelCentro() {
        this.centroLatitud = null;
        this.centroLongitud = null;
    }
}
