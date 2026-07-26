package es.medeben.service;

import es.medeben.config.RequiereBaseDeDatos;
import es.medeben.domain.usuario.ConsentimientoUbicacion;
import es.medeben.repository.ConsentimientoUbicacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Consentimiento art. 6.1.a RGPD de "Anotar dónde fichas" (contrato
 * §Backend). El hash SIEMPRE se calcula en SERVIDOR sobre el texto canónico
 * ({@link ConsentimientoUbicacionTexto#TEXTO_V1_0}) — nunca sobre nada que
 * mande el cliente, o el registro no acreditaría qué se mostró de verdad.
 */
@Service
@RequiereBaseDeDatos
public class ConsentimientoUbicacionService {

    private final ConsentimientoUbicacionRepository consentimientos;
    private final Clock reloj;

    public ConsentimientoUbicacionService(ConsentimientoUbicacionRepository consentimientos, Clock reloj) {
        this.consentimientos = consentimientos;
        this.reloj = reloj;
    }

    @Transactional
    public ConsentimientoUbicacion acepta(UUID usuarioId, String versionTexto) {
        if (!ConsentimientoUbicacionTexto.VERSION_ACTUAL.equals(versionTexto)) {
            throw new IllegalArgumentException("Versión de texto de consentimiento no reconocida");
        }
        String hash = Sha256.hex(ConsentimientoUbicacionTexto.TEXTO_V1_0);
        return consentimientos.save(new ConsentimientoUbicacion(usuarioId, versionTexto, hash,
                OffsetDateTime.now(reloj)));
    }

    /** Idempotente: sin consentimiento vigente, no hace nada (no hay nada que revocar). */
    @Transactional
    public void revoca(UUID usuarioId) {
        Optional<ConsentimientoUbicacion> vigente = consentimientos
                .findFirstByUsuarioIdOrderByAceptadoEnDesc(usuarioId)
                .filter(ConsentimientoUbicacion::isVigente);
        if (vigente.isEmpty()) {
            return;
        }
        ConsentimientoUbicacion consentimiento = vigente.get();
        consentimiento.revoca(OffsetDateTime.now(reloj));
        consentimientos.save(consentimiento);
    }

    /**
     * Primer filtro de TODOS los endpoints de /ubicacion y /centro-trabajo
     * (contrato §Backend): sin esto, ninguna coordenada llega a servicio ni a BD.
     */
    @Transactional(readOnly = true)
    public boolean vigente(UUID usuarioId) {
        return consentimientos.findFirstByUsuarioIdOrderByAceptadoEnDesc(usuarioId)
                .map(ConsentimientoUbicacion::isVigente)
                .orElse(false);
    }
}
