package es.medeben.service;

import es.medeben.config.CorreoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Convierte el evento de verificación en el correo real. AFTER_COMMIT (el
 * default de {@link TransactionalEventListener}) + {@link Async}, por dos
 * motivos que son seguridad, no estilo:
 *
 * <ul>
 *   <li>AFTER_COMMIT: si el registro hace rollback no sale ningún correo, y
 *       si el SMTP falla el usuario ya está creado (puede pedir reenvío).</li>
 *   <li>Async: el envío no suma latencia a la respuesta HTTP del registro —
 *       los casos con correo (nuevo, sin verificar) y sin correo (verificado)
 *       tardan lo mismo, así que el tiempo de respuesta no es un oráculo de
 *       enumeración.</li>
 * </ul>
 *
 * <p>Un fallo del envío se REGISTRA siempre (nunca se traga en silencio)
 * pero no se propaga: no hay transacción que deshacer y el reenvío existe
 * para eso.
 */
@Component
public class EnviadorVerificacionEmail {

    private static final Logger log = LoggerFactory.getLogger(EnviadorVerificacionEmail.class);
    private static final String ASUNTO = "Confirma tu correo en MeDeben";

    private final EmailSender sender;
    private final CorreoProperties propiedades;

    public EnviadorVerificacionEmail(EmailSender sender, CorreoProperties propiedades) {
        this.sender = sender;
        this.propiedades = propiedades;
    }

    @Async
    @TransactionalEventListener
    public void alSolicitarVerificacion(VerificacionEmailSolicitada evento) {
        try {
            sender.envia(evento.email(), ASUNTO, cuerpo(evento.token()));
        } catch (Exception e) {
            // El email se enmascara: el log no debe regalar la lista de usuarios.
            log.error("No se pudo enviar el correo de verificación a {}: {}",
                    EnmascaradorEmail.enmascara(evento.email()), e.getMessage());
        }
    }

    private String cuerpo(String token) {
        return """
                Hola:

                Alguien (esperamos que tú) ha creado una cuenta en MeDeben con este correo.
                Para confirmar que es tuyo, entra en este enlace (caduca en 24 horas):

                %s?token=%s

                Si no has sido tú, puedes ignorar este mensaje.

                — MeDeben
                """.formatted(propiedades.urlVerificacion(), token);
    }
}
