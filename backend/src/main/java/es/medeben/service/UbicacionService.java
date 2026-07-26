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
import java.time.LocalDate;
import java.time.LocalDateTime;
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

        if (apunte.getHora() != null && diferenciaMinutos(apunte.getFecha(), apunte.getHora(), apunte.getRegistradoEn())
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
     * Supresión granular (art. 17): NO borra la fila, anula latitud/longitud/
     * distancia y marca el veredicto como {@link VeredictoUbicacion#SUPRIMIDA}
     * (contrato §Backend punto 7). Deliberadamente SIN el filtro de
     * consentimiento vigente: es el ejercicio del derecho de supresión, tiene
     * que seguir disponible aunque el usuario haya revocado el consentimiento
     * del art. 6.1.a (ver javadoc de la clase).
     */
    @Transactional
    public void suprime(UUID usuarioId, UUID apunteId) {
        UbicacionApunte ubicacion = ubicaciones.findById(apunteId)
                .filter(u -> u.getUsuarioId().equals(usuarioId))
                .orElseThrow(() -> new RecursoNoEncontradoException("Ubicación no encontrada"));
        ubicacion.suprime();
        ubicaciones.save(ubicacion);
    }

    /** Borra TODO el histórico de ubicaciones del usuario (art. 17). El diario de apuntes queda intacto. */
    @Transactional
    public void borraTodas(UUID usuarioId) {
        ubicaciones.deleteByUsuarioId(usuarioId);
    }

    /**
     * Diferencia en minutos entre la hora declarada ("HH:mm", combinada con
     * {@code Apunte.fecha}) y el sello LOCAL del servidor (misma zona del
     * Clock inyectado), en DATETIME COMPLETO — nunca solo hora-del-día.
     *
     * <p>Corrección CRÍTICA (agujero circular): la versión anterior comparaba
     * solo {@link LocalTime} con aritmética modular (mod 24h), así que una
     * hora tecleada de madrugada ("00:01") con fecha=HOY y un sello de última
     * hora del mismo día ("23:58") colapsaba a 3 minutos de "diferencia
     * circular" cuando la discrepancia real era de casi 24 horas — exactamente
     * el agujero del manual-de-hoy que esta regla debía cerrar. Al combinar
     * {@code apunte.getFecha()} (la fecha real del apunte, no la del reloj)
     * con la hora declarada, la diferencia sale literal: un turno de cierre
     * que de verdad cruza la medianoche (hora declarada "23:59", sello ya en
     * la madrugada del día siguiente) sigue dando pocos minutos sin necesidad
     * de ningún ajuste adicional, porque el sello ya lleva su fecha real.</p>
     */
    private long diferenciaMinutos(LocalDate fecha, String horaDeclarada, OffsetDateTime sello) {
        LocalDateTime declarado = LocalDateTime.of(fecha, LocalTime.parse(horaDeclarada));
        LocalDateTime selloLocal = sello.atZoneSameInstant(reloj.getZone()).toLocalDateTime();
        return Math.abs(Duration.between(declarado, selloLocal).toMinutes());
    }
}
