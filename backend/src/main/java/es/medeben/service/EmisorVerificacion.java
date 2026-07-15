package es.medeben.service;

import es.medeben.config.RequiereBaseDeDatos;
import es.medeben.domain.usuario.Usuario;
import es.medeben.domain.usuario.VerificacionEmail;
import es.medeben.repository.VerificacionEmailRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.security.SecureRandom;

/**
 * Emite el token de verificación de email: lo genera, guarda su SHA-256 y
 * publica el evento que dispara el correo (AFTER_COMMIT). Extraído de
 * {@link AuthService} para que {@link RegistroDeUsuario} (registro de email
 * nuevo, con su propia transacción REQUIRES_NEW) y {@code AuthService}
 * (reenvío) compartan la MISMA lógica — incluido el tope anti email-bombing —
 * sin duplicarla y sin crear una dependencia circular entre esos dos beans.
 *
 * <p>{@code @Transactional} (REQUIRED, el default): si quien llama ya tiene
 * una transacción activa (el caso normal: {@code RegistroDeUsuario} o
 * {@code AuthService.reenviaVerificacion}), esta se UNE a ella — el guardado
 * del token y la publicación del evento quedan atados al mismo commit que el
 * resto de la operación.
 */
@Service
@RequiereBaseDeDatos
public class EmisorVerificacion {

    /** Máximo de tokens de verificación por usuario y hora (anti email-bombing). */
    private static final int MAX_VERIFICACIONES_POR_HORA = 3;

    /** 256 bits de entropía para el token opaco. */
    private static final int BYTES_TOKEN = 32;

    private final VerificacionEmailRepository verificaciones;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;
    private final Duration duracionVerificacion;
    private final SecureRandom aleatorio = new SecureRandom();

    public EmisorVerificacion(VerificacionEmailRepository verificaciones,
                              ApplicationEventPublisher eventos,
                              Clock reloj,
                              @Value("${medeben.verificacion.duracion:PT24H}") Duration duracionVerificacion) {
        this.verificaciones = verificaciones;
        this.eventos = eventos;
        this.reloj = reloj;
        this.duracionVerificacion = duracionVerificacion;
    }

    /**
     * Token opaco de 256 bits; a la BD solo va su SHA-256, al evento (correo)
     * el claro. Tope SILENCIOSO por usuario: al cuarto token en una hora no se
     * emite nada — callar mantiene la respuesta uniforme (sin oráculo) y corta
     * el bombardeo del buzón de una víctima aunque el atacante rote IPs (el
     * rate limit por IP no cubre ese caso).
     */
    @Transactional
    public void emite(Usuario usuario) {
        Instant ahora = Instant.now(reloj);
        if (verificaciones.cuentaEmitidasDesde(usuario.getId(),
                ahora.minus(Duration.ofHours(1))) >= MAX_VERIFICACIONES_POR_HORA) {
            return;
        }
        byte[] crudo = new byte[BYTES_TOKEN];
        aleatorio.nextBytes(crudo);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(crudo);
        verificaciones.save(new VerificacionEmail(
                usuario.getId(), Sha256.hex(token), ahora, ahora.plus(duracionVerificacion)));
        eventos.publishEvent(new VerificacionEmailSolicitada(usuario.getEmail(), token));
    }
}
