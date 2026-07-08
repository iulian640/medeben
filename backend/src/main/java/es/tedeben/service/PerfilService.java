package es.tedeben.service;

import es.tedeben.config.RequiereBaseDeDatos;
import es.tedeben.controller.RecursoNoEncontradoException;
import es.tedeben.domain.convenio.Convenio;
import es.tedeben.domain.convenio.Subsector;
import es.tedeben.domain.usuario.Perfil;
import es.tedeben.repository.ConvenioCatalog;
import es.tedeben.repository.OcupacionesCatalog;
import es.tedeben.repository.PerfilRepository;
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

    private final PerfilRepository perfiles;
    private final ConvenioCatalog convenios;
    private final OcupacionesCatalog ocupaciones;
    private final Clock reloj;

    public PerfilService(PerfilRepository perfiles, ConvenioCatalog convenios,
                         OcupacionesCatalog ocupaciones, Clock reloj) {
        this.perfiles = perfiles;
        this.convenios = convenios;
        this.ocupaciones = ocupaciones;
        this.reloj = reloj;
    }

    @Transactional
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
                if (d.getKey() == null || d.getKey().length() > 40
                        || d.getValue() == null || d.getValue().length() > 100) {
                    throw new IllegalArgumentException(
                            "Dimensión inválida: claves hasta 40 caracteres y valores hasta 100");
                }
            }
        }
        if (salarioBaseMensual != null && salarioBaseMensual.signum() <= 0) {
            throw new IllegalArgumentException("El salario base mensual debe ser positivo");
        }
        if (plusesAnuales != null && plusesAnuales.signum() < 0) {
            throw new IllegalArgumentException("Los pluses anuales no pueden ser negativos");
        }
        // El perfil es una fila mutable 1:1 por usuario (NO append-only):
        // si ya existe se actualiza in situ. Crear siempre uno nuevo haría
        // persist() (Persistable.isNew()=true) y la segunda actualización
        // violaría la PK usuario_id → 500 para el usuario.
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        Perfil perfil = perfiles.findById(usuarioId)
                .map(existente -> {
                    existente.actualiza(provincia, subsector, convenio.id(), puestoId,
                            dimensiones, salarioBaseMensual, plusesAnuales, ahora);
                    return existente;
                })
                .orElseGet(() -> new Perfil(usuarioId, provincia, subsector, convenio.id(),
                        puestoId, dimensiones, salarioBaseMensual, plusesAnuales, ahora));
        return perfiles.save(perfil);
    }

    @Transactional(readOnly = true)
    public Optional<Perfil> busca(UUID usuarioId) {
        return perfiles.findById(usuarioId);
    }
}
