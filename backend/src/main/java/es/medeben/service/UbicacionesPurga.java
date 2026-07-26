package es.medeben.service;

import es.medeben.config.RequiereBaseDeDatos;
import es.medeben.repository.UbicacionApunteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.Period;

/**
 * Purga diaria de retención de ubicaciones (contrato §Retención): "hasta 15
 * meses desde el fichaje, salvo que declares una reclamación en curso"
 * (texto de consentimiento v1.0). El filtro de "reclamación en curso" vive en
 * la propia query del repositorio ({@link UbicacionApunteRepository#purgaCaducadas}),
 * que excluye a esos usuarios. Mismo patrón que {@link SesionesPurga}: cron
 * configurable, de madrugada, en Europe/Madrid.
 *
 * <p>NUNCA toca la tabla de apuntes — estructuralmente, ni siquiera depende
 * de {@code ApunteRepository}.</p>
 */
@Component
@RequiereBaseDeDatos
public class UbicacionesPurga {

    private static final Logger log = LoggerFactory.getLogger(UbicacionesPurga.class);

    /** Retención (contrato §Retención): 15 meses desde el fichaje. */
    static final Period RETENCION = Period.ofMonths(15);

    private final UbicacionApunteRepository ubicaciones;
    private final Clock reloj;

    public UbicacionesPurga(UbicacionApunteRepository ubicaciones, Clock reloj) {
        this.ubicaciones = ubicaciones;
        this.reloj = reloj;
    }

    @Scheduled(cron = "${medeben.ubicaciones.purga-cron:0 30 4 * * *}", zone = "Europe/Madrid")
    @Transactional
    public void purga() {
        OffsetDateTime limite = OffsetDateTime.now(reloj).minus(RETENCION);
        int borradas = ubicaciones.purgaCaducadas(limite);
        if (borradas > 0) {
            log.info("Purga de ubicaciones: {} filas caducadas fuera (retención de 15 meses)", borradas);
        }
    }
}
