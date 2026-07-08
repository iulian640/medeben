package es.tedeben.domain.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import org.springframework.data.domain.Persistable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Perfil laboral del usuario: dónde y de qué trabaja y su salario real (D25:
 * el convenio es el mínimo; el usuario puede cobrar más). Las dimensiones de
 * tabla van como JSONB porque cada convenio tiene las suyas (D24).
 */
@Entity
@Table(name = "perfiles")
public class Perfil implements Persistable<UUID> {

    @Id
    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(nullable = false, length = 60)
    private String provincia;

    @Column(nullable = false, length = 30)
    private String subsector;

    @Column(name = "convenio_id", nullable = false, length = 80)
    private String convenioId;

    @Column(name = "puesto_id", length = 40)
    private String puestoId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> dimensiones = new LinkedHashMap<>();

    @Column(name = "salario_base_mensual", precision = 9, scale = 2)
    private BigDecimal salarioBaseMensual;

    @Column(name = "pluses_anuales", precision = 9, scale = 2)
    private BigDecimal plusesAnuales;

    @Version
    private long version;

    @Column(name = "actualizado_en", nullable = false)
    private OffsetDateTime actualizadoEn;

    /** true hasta que la entidad se persiste o se carga: save() hace persist(), no merge+SELECT. */
    @Transient
    private boolean nuevo = true;

    protected Perfil() {
        // requerido por JPA
    }

    /**
     * @param actualizadoEn sello de actualización, SIEMPRE del reloj inyectado
     *                      del servicio (zona controlada), como en Apunte y
     *                      Cuadrante — nunca del reloj del sistema.
     */
    public Perfil(UUID usuarioId, String provincia, String subsector, String convenioId,
                  String puestoId, Map<String, String> dimensiones,
                  BigDecimal salarioBaseMensual, BigDecimal plusesAnuales,
                  OffsetDateTime actualizadoEn) {
        this.usuarioId = usuarioId;
        this.actualizadoEn = java.util.Objects.requireNonNull(
                actualizadoEn, "actualizadoEn: el sello es obligatorio");
        rellena(provincia, subsector, convenioId, puestoId, dimensiones,
                salarioBaseMensual, plusesAnuales);
    }

    /**
     * Actualiza el perfil existente in situ. El perfil es una fila mutable 1:1
     * por usuario (NO append-only): guardar de nuevo = sobrescribir esta fila,
     * nunca insertar otra.
     */
    public void actualiza(String provincia, String subsector, String convenioId,
                          String puestoId, Map<String, String> dimensiones,
                          BigDecimal salarioBaseMensual, BigDecimal plusesAnuales,
                          OffsetDateTime actualizadoEn) {
        this.actualizadoEn = java.util.Objects.requireNonNull(
                actualizadoEn, "actualizadoEn: el sello es obligatorio");
        rellena(provincia, subsector, convenioId, puestoId, dimensiones,
                salarioBaseMensual, plusesAnuales);
    }

    private void rellena(String provincia, String subsector, String convenioId, String puestoId,
                         Map<String, String> dimensiones, BigDecimal salarioBaseMensual,
                         BigDecimal plusesAnuales) {
        this.provincia = provincia;
        this.subsector = subsector;
        this.convenioId = convenioId;
        this.puestoId = puestoId;
        this.dimensiones = dimensiones == null ? new LinkedHashMap<>() : new LinkedHashMap<>(dimensiones);
        this.salarioBaseMensual = salarioBaseMensual;
        this.plusesAnuales = plusesAnuales;
    }

    @Override
    public UUID getId() {
        return usuarioId;
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
        if (actualizadoEn == null) {
            // Nunca rellenar aquí con el reloj del sistema: el sello viene del
            // servicio con su Clock inyectado (Europe/Madrid), como en Apunte.
            throw new IllegalStateException("Perfil sin sello de actualización");
        }
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getProvincia() {
        return provincia;
    }

    public String getSubsector() {
        return subsector;
    }

    public String getConvenioId() {
        return convenioId;
    }

    public String getPuestoId() {
        return puestoId;
    }

    public Map<String, String> getDimensiones() {
        return Map.copyOf(dimensiones);
    }

    public BigDecimal getSalarioBaseMensual() {
        return salarioBaseMensual;
    }

    public BigDecimal getPlusesAnuales() {
        return plusesAnuales;
    }

    public OffsetDateTime getActualizadoEn() {
        return actualizadoEn;
    }
}
