package es.medeben.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sender de dev/test/CI: escribe el correo en el log y no envía nada. Los
 * E2E registran cuentas a puñados y el flujo no debe depender de un SMTP;
 * además deja el enlace de verificación a mano para probarlo en local.
 */
public class LogEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);

    @Override
    public void envia(String destinatario, String asunto, String cuerpo) {
        log.info("Correo (modo log, no se envía) para {}: [{}]\n{}", destinatario, asunto, cuerpo);
    }
}
