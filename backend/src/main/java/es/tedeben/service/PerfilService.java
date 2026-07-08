package es.tedeben.service;

import es.tedeben.config.RequiereBaseDeDatos;
import es.tedeben.controller.RecursoNoEncontradoException;
import es.tedeben.domain.convenio.Convenio;
import es.tedeben.domain.convenio.Subsector;
import es.tedeben.domain.usuario.Perfil;
import es.tedeben.repository.ConvenioCatalog;
import es.tedeben.repository.OcupacionesCatalog;
import es.tedeben.repository.PerfilRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Perfil laboral del usuario. El cliente manda provincia + subsector y el
 * servidor resuelve el convenio (D20) — nunca se confía en un convenioId
 * elegido por el cliente. Puesto y dimensiones se validan contra los catálogos.
 */
@Service
@RequiereBaseDeDatos
public class PerfilService {

    /**
     * Topes de cada entrada de dimensiones (mismos que valida PerfilRequest en
     * el borde): el valor real más largo del catálogo tiene 325 caracteres
     * (estatal-restauracion-colectiva), de ahí el 400.
     */
    private static final int DIMENSION_CLAVE_MAX = 40;
    private static final int DIMENSION_VALOR_MAX = 400;

    private final PerfilRepository perfiles;
    private final ConvenioCatalog convenios;
    private final OcupacionesCatalog ocupaciones;
    private final DimensionesCatalogoValidator dimensionesValidator;
    private final Clock reloj;

    public PerfilService(PerfilRepository perfiles, ConvenioCatalog convenios,
                         OcupacionesCatalog ocupaciones,
                         DimensionesCatalogoValidator dimensionesValidator, Clock reloj) {
        this.perfiles = perfiles;
        this.convenios = convenios;
        this.ocupaciones = ocupaciones;
        this.dimensionesValidator = dimensionesValidator;
        this.reloj = reloj;
    }

    /**
     * Sin {@code @Transactional} a propósito: cada llamada al repositorio corre
     * en su propia transacción. Si la PRIMERA creación del perfil choca con una
     * petición concurrente del mismo usuario (doble clic, reintento de red),
     * ambas pueden ver findById() vacío y ambas hacer persist() → violación de
     * la PK usuario_id. Con una transacción envolvente PostgreSQL la marcaría
     * como abortada y no se podría reintentar; así, la transacción del insert
     * fallido ya está cerrada y el catch de abajo reintenta como actualización.
     */
    public Perfil guarda(UUID usuarioId, String provincia, String subsector, String puestoId,
                         Map<String, String> dimensiones, BigDecimal salarioBaseMensual,
                         BigDecimal plusesAnuales) {
        Convenio convenio = convenios.paraTrabajador(provincia, Subsector.desdeClave(subsector))
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No hay convenio para la provincia '" + provincia + "' y subsector '" + subsector + "'"));
        if (puestoId != null && ocupaciones.puestos().stream().noneMatch(p -> p.id().equals(puestoId))) {
            throw new IllegalArgumentException("Puesto desconocido: " + puestoId);
        }
        if (dimensiones != null) {
            for (Map.Entry<String, String> d : dimensiones.entrySet()) {
                if (d.getKey() == null || d.getKey().length() > DIMENSION_CLAVE_MAX
                        || d.getValue() == null || d.getValue().length() > DIMENSION_VALOR_MAX) {
                    throw new IllegalArgumentException(
                            "Dimensión inválida: claves hasta " + DIMENSION_CLAVE_MAX
                                    + " caracteres y valores hasta " + DIMENSION_VALOR_MAX);
                }
            }
        }
        // Tras el tope de almacenamiento (borde), el catálogo: cada clave/valor
        // debe existir en las tablas del convenio resuelto, o no se guarda (422).
        dimensionesValidator.valida(convenio.id(), dimensiones);
        if (salarioBaseMensual != null && salarioBaseMensual.signum() <= 0) {
            throw new IllegalArgumentException("El salario base mensual debe ser positivo");
        }
        if (plusesAnuales != null && plusesAnuales.signum() < 0) {
            throw new IllegalArgumentException("Los pluses anuales no pueden ser negativos");
        }
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        try {
            return creaOActualiza(usuarioId, provincia, subsector, convenio.id(), puestoId,
                    dimensiones, salarioBaseMensual, plusesAnuales, ahora);
        } catch (DataIntegrityViolationException e) {
            // Carrera de creación: otra petición concurrente insertó el perfil
            // entre nuestro findById() y el save(). La fila ya existe, así que
            // el reintento la encuentra y la actualiza in situ (un solo
            // reintento: si vuelve a fallar, ya no es la carrera y se propaga).
            return creaOActualiza(usuarioId, provincia, subsector, convenio.id(), puestoId,
                    dimensiones, salarioBaseMensual, plusesAnuales, ahora);
        }
    }

    /**
     * El perfil es una fila mutable 1:1 por usuario (NO append-only): si ya
     * existe se actualiza in situ. Crear siempre uno nuevo haría persist()
     * (Persistable.isNew()=true) y la segunda actualización violaría la PK
     * usuario_id → 500 para el usuario.
     */
    private Perfil creaOActualiza(UUID usuarioId, String provincia, String subsector,
                                  String convenioId, String puestoId, Map<String, String> dimensiones,
                                  BigDecimal salarioBaseMensual, BigDecimal plusesAnuales,
                                  OffsetDateTime ahora) {
        Perfil perfil = perfiles.findById(usuarioId)
                .map(existente -> {
                    existente.actualiza(provincia, subsector, convenioId, puestoId,
                            dimensiones, salarioBaseMensual, plusesAnuales, ahora);
                    return existente;
                })
                .orElseGet(() -> new Perfil(usuarioId, provincia, subsector, convenioId,
                        puestoId, dimensiones, salarioBaseMensual, plusesAnuales, ahora));
        return perfiles.save(perfil);
    }

    @Transactional(readOnly = true)
    public Optional<Perfil> busca(UUID usuarioId) {
        return perfiles.findById(usuarioId);
    }
}
