package es.medeben.service;

import es.medeben.config.RequiereBaseDeDatos;
import es.medeben.controller.RecursoNoEncontradoException;
import es.medeben.domain.fichaje.CentroTrabajo;
import es.medeben.repository.CentroTrabajoRepository;
import es.medeben.repository.UbicacionApunteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * El centro de trabajo declarado por el usuario (síntesis D4/D5, con las
 * correcciones vinculantes del contrato §Backend).
 *
 * <p>El consentimiento vigente se exige como primer filtro SOLO en las
 * operaciones que capturan coordenadas nuevas ({@link #declara}) — no en las
 * de lectura ni en las de supresión ({@link #cierra}, {@link #purga}):
 * condicionar el ejercicio del derecho de supresión (art. 17) a mantener el
 * consentimiento del art. 6.1.a sería una contradicción (un usuario que
 * revoca su consentimiento debe poder seguir borrando su histórico). Es la
 * lectura mínima sensata de "primer filtro en TODOS los endpoints" del
 * contrato — documentado como desviación en la entrega.</p>
 */
@Service
@RequiereBaseDeDatos
public class CentroTrabajoService {

    /** Radio fijo v1 (síntesis §7): lo pone el servidor, no el cliente. Se revisa con datos de campo. */
    public static final int RADIO_METROS_DEFECTO = 150;

    /** Bajo esta antigüedad, purgar borra físicamente: una declaración errónea nunca fue prueba. */
    static final Duration VENTANA_BORRADO_FISICO = Duration.ofHours(24);

    private final CentroTrabajoRepository centros;
    private final UbicacionApunteRepository ubicaciones;
    private final ConsentimientoUbicacionService consentimientos;
    private final Clock reloj;

    public CentroTrabajoService(CentroTrabajoRepository centros, UbicacionApunteRepository ubicaciones,
                                ConsentimientoUbicacionService consentimientos, Clock reloj) {
        this.centros = centros;
        this.ubicaciones = ubicaciones;
        this.consentimientos = consentimientos;
        this.reloj = reloj;
    }

    /** Declaración nueva (ALTA). El radio SIEMPRE es {@link #RADIO_METROS_DEFECTO}, nunca el que mande el cliente. */
    @Transactional
    public CentroTrabajo declara(UUID usuarioId, String alias, BigDecimal latitud, BigDecimal longitud) {
        if (!consentimientos.vigente(usuarioId)) {
            throw new ConsentimientoUbicacionRequeridoException();
        }
        return centros.save(new CentroTrabajo(usuarioId, alias, latitud, longitud, RADIO_METROS_DEFECTO,
                OffsetDateTime.now(reloj)));
    }

    /** El centro vigente (ALTA), o vacío si el usuario no tiene ninguno o el último quedó BAJA. */
    @Transactional(readOnly = true)
    public Optional<CentroTrabajo> vigente(UUID usuarioId) {
        return centros.findFirstByUsuarioIdOrderByDeclaradoEnDesc(usuarioId).filter(CentroTrabajo::isVigente);
    }

    /** Cierra el centro vigente: inserta una fila BAJA que copia lat/lon/radio (nunca borra la ALTA). */
    @Transactional
    public void cierra(UUID usuarioId) {
        CentroTrabajo actual = vigente(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No tienes un centro de trabajo declarado"));
        centros.save(CentroTrabajo.baja(actual, OffsetDateTime.now(reloj)));
    }

    /**
     * Supresión (art. 17) de una declaración concreta, propia. Con menos de
     * 24 h desde su declaración: borrado físico (la cascada de V9 arrastra
     * sus ubicaciones — una declaración errónea nunca fue prueba). Con 24 h o
     * más: tombstone — se anulan alias/coordenadas de la fila (que pasa a
     * BAJA si era ALTA) y se propaga a las copias congeladas de
     * {@code ubicaciones_apunte} de ese centro (se conservan distancia,
     * radio y veredicto).
     */
    @Transactional
    public void purga(UUID usuarioId, UUID centroId) {
        CentroTrabajo centro = centros.findById(centroId)
                .filter(c -> c.getUsuarioId().equals(usuarioId))
                .orElseThrow(() -> new RecursoNoEncontradoException("Centro de trabajo no encontrado"));
        purgaFila(centro);
    }

    /**
     * Purga TODAS las declaraciones (ALTA y BAJA) del usuario, una por una,
     * con la misma regla de 24h que {@link #purga} — corrección del
     * verificador rgpd-play: sin esto, {@code DELETE /api/v1/ubicaciones}
     * ("borrar todo tu histórico de ubicaciones", que el consentimiento v1.0
     * promete "en un toque") dejaba intactas las coordenadas de
     * {@code centros_trabajo}, alcanzables solo purgando cada id a mano.
     */
    @Transactional
    public void purgaTodas(UUID usuarioId) {
        centros.findAllByUsuarioId(usuarioId).forEach(this::purgaFila);
    }

    private void purgaFila(CentroTrabajo centro) {
        Duration antiguedad = Duration.between(centro.getDeclaradoEn(), OffsetDateTime.now(reloj));
        if (antiguedad.compareTo(VENTANA_BORRADO_FISICO) < 0) {
            centros.deleteById(centro.getId());
        } else {
            centro.tombstone();
            centros.save(centro);
            ubicaciones.anulaCoordenadasDelCentro(centro.getId());
        }
    }
}
