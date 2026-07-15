package es.medeben.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sender de dev/test/CI: no envía nada, solo deja constancia de que el envío
 * se disparó. Los E2E registran cuentas a puñados y el flujo no debe
 * depender de un SMTP.
 *
 * <p>OJO: no vuelca ni el destinatario en claro ni el cuerpo. El cuerpo lleva
 * el token de verificación en la URL ({@code ...?token=<TOKEN>}), y este modo
 * es el DEFAULT en producción hasta que se configure el SMTP (Brevo) — un log
 * con eso sería una lista en claro de tokens vivos 24h y de cada email
 * registrado, justo la PII que la respuesta uniforme del registro protege.
 */
public class LogEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);

    @Override
    public void envia(String destinatario, String asunto, String cuerpo) {
        log.info("Correo (modo log, no se envía) — se enviaría '{}' a {}",
                asunto, EnmascaradorEmail.enmascara(destinatario));
    }
}
