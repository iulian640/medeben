package es.tedeben.domain.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
public class Perfil {

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

    @Column(name = "actualizado_en", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime actualizadoEn;

    protected Perfil() {
        // requerido por JPA
    }

    public Perfil(UUID usuarioId, String provincia, String subsector, String convenioId,
                  String puestoId, Map<String, String> dimensiones,
                  BigDecimal salarioBaseMensual, BigDecimal plusesAnuales) {
        this.usuarioId = usuarioId;
        this.provincia = provincia;
        this.subsector = subsector;
        this.convenioId = convenioId;
        this.puestoId = puestoId;
        this.dimensiones = dimensiones == null ? new LinkedHashMap<>() : new LinkedHashMap<>(dimensiones);
        this.salarioBaseMensual = salarioBaseMensual;
        this.plusesAnuales = plusesAnuales;
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
        return dimensiones;
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
