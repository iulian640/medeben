package es.medeben.service;

import es.medeben.config.RequiereBaseDeDatos;
import es.medeben.controller.RecursoNoEncontradoException;
import es.medeben.domain.fichaje.Apunte;
import es.medeben.domain.fichaje.CentroTrabajo;
import es.medeben.domain.fichaje.Distancia;
import es.medeben.domain.fichaje.OrigenApunte;
import es.medeben.domain.fichaje.UbicacionApunte;
import es.medeben.domain.fichaje.VeredictoCalculador;
import es.medeben.domain.fichaje.VeredictoUbicacion;
import es.medeben.repository.ApunteLecturaRepository;
import es.medeben.repository.CentroTrabajoRepository;
import es.medeben.repository.UbicacionApunteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * "Anotar dónde fichas": adjunta una ubicación aproximada a un apunte
 * CONFIRMADO al momento (síntesis §5/§7, con las correcciones vinculantes del
 * contrato §Backend). Nunca en el camino crítico de fichar — este servicio se
 * llama DESPUÉS de que el apunte ya está guardado, por un endpoint aparte.
 *
 * <p>Reglas del endpoint de adjuntar, EN ESTE ORDEN (contrato §Backend):
 * <ol>
 *   <li>consentimiento vigente → si no, 403 ({@link ConsentimientoUbicacionRequeridoException});</li>
 *   <li>el apunte existe y es del usuario del JWT → si no, 404 en ambos casos
 *       ({@link RecursoNoEncontradoException}, sin filtrar existencia);</li>
 *   <li>{@code origen == CONFIRMADO} → si no, 422;</li>
 *   <li>ventana de 10 min desde {@code registradoEn} → si no, 422;</li>
 *   <li>coherencia hora-vs-sello (±3 min, corrección CRÍTICA — el agujero del
 *       manual-de-hoy) → si no, 422;</li>
 *   <li>existe centro vigente (ALTA) → si no, 422;</li>
 *   <li>ya hay ubicación para ese apunte → 409 (inmutable, no se pisa).</li>
 * </ol>
 * Los rangos de latitud/longitud/precisión los valida el DTO (400, Bean
 * Validation) antes de llegar aquí.
 */
@Service
@RequiereBaseDeDatos
public class UbicacionService {

    /** Ventana desde el sello del apunte dentro de la cual se puede adjuntar ubicación. */
    static final Duration VENTANA_ADJUNTAR = Duration.ofMinutes(10);

    /**
     * Tolerancia entre la hora declarada por el trabajador y la hora local del
     * sello del servidor (corrección CRÍTICA del verificador técnico): sin
     * esto, un fichaje MANUAL de hoy con hora tecleada muy alejada del sello
     * pasaba como "al momento" y capturaba la ubicación del domicilio del
     * usuario, no la del centro.
     */
    static final int TOLERANCIA_HORA_SELLO_MINUTOS = 3;

    private static final int MINUTOS_DIA = 24 * 60;

    private final ApunteLecturaRepository apuntes;
    private final CentroTrabajoRepository centros;
    private final UbicacionApunteRepository ubicaciones;
    private final ConsentimientoUbicacionService consentimientos;
    private final Clock reloj;

    public UbicacionService(ApunteLecturaRepository apuntes, CentroTrabajoRepository centros,
                            UbicacionApunteRepository ubicaciones,
                            ConsentimientoUbicacionService consentimientos, Clock reloj) {
        this.apuntes = apuntes;
        this.centros = centros;
        this.ubicaciones = ubicaciones;
        this.consentimientos = consentimientos;
        this.reloj = reloj;
    }

    @Transactional
    public UbicacionApunte adjunta(UUID usuarioId, UUID apunteId, BigDecimal latitud,
                                   BigDecimal longitud, int precisionMetros) {
        if (!consentimientos.vigente(usuarioId)) {
            throw new ConsentimientoUbicacionRequeridoException();
        }

        Apunte apunte = apuntes.findById(apunteId)
                .filter(a -> a.getUsuarioId().equals(usuarioId))
                .orElseThrow(() -> new RecursoNoEncontradoException("Fichaje no encontrado"));

        if (apunte.getOrigen() != OrigenApunte.CONFIRMADO) {
            throw new UbicacionNoElegibleException(
                    "Solo se anota ubicación en fichajes confirmados al momento");
        }

        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        if (Duration.between(apunte.getRegistradoEn(), ahora).compareTo(VENTANA_ADJUNTAR) > 0) {
            throw new UbicacionNoElegibleException(
                    "Han pasado más de " + VENTANA_ADJUNTAR.toMinutes() + " minutos desde el fichaje");
        }

        if (apunte.getHora() != null && diferenciaCircularMinutos(apunte.getHora(), apunte.getRegistradoEn())
                > TOLERANCIA_HORA_SELLO_MINUTOS) {
            throw new UbicacionNoElegibleException(
                    "La hora declarada no coincide con el momento del fichaje");
        }

        CentroTrabajo centro = centros.findFirstByUsuarioIdOrderByDeclaradoEnDesc(usuarioId)
                .filter(CentroTrabajo::isVigente)
                .orElseThrow(() -> new UbicacionNoElegibleException(
                        "No tienes un centro de trabajo declarado"));

        if (ubicaciones.existsById(apunteId)) {
            throw new UbicacionYaRegistradaException();
        }

        int distanciaMetros = Distancia.metrosEntre(latitud, longitud, centro.getLatitud(), centro.getLongitud());
        VeredictoUbicacion veredicto = VeredictoCalculador.calcula(distanciaMetros, precisionMetros,
                centro.getRadioMetros());

        UbicacionApunte nueva = new UbicacionApunte(apunteId, usuarioId, apunte.getFecha(), latitud, longitud,
                precisionMetros, centro.getId(), centro.getLatitud(), centro.getLongitud(),
                centro.getRadioMetros(), distanciaMetros, veredicto, ahora);
        return ubicaciones.save(nueva);
    }

    /**
     * Diferencia en minutos, circular (mod 24h), entre la hora declarada
     * ("HH:mm") y la hora LOCAL del sello (misma zona del Clock inyectado).
     * Circular porque un turno de cierre cruza la medianoche del reloj sin
     * que eso signifique ninguna discrepancia real (mismo criterio que
     * {@code FichajeService.minutosEntre}).
     */
    private long diferenciaCircularMinutos(String horaDeclarada, OffsetDateTime sello) {
        int minutosDeclarados = minutosDelDia(LocalTime.parse(horaDeclarada));
        int minutosSello = minutosDelDia(sello.atZoneSameInstant(reloj.getZone()).toLocalTime());
        int diferencia = Math.abs(minutosDeclarados - minutosSello);
        return Math.min(diferencia, MINUTOS_DIA - diferencia);
    }

    private static int minutosDelDia(LocalTime hora) {
        return hora.getHour() * 60 + hora.getMinute();
    }
}
