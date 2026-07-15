package es.medeben.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Sender de producción: SMTP plano vía {@link JavaMailSender}. El sender
 * concreto (host, puerto, credenciales) lo construye {@code CorreoConfig}
 * desde {@code medeben.correo.smtp.*} — a propósito FUERA del autoconfig de
 * {@code spring.mail.*}, para que el arranque sin SMTP configurado no
 * dependa de placeholders sin resolver.
 */
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String remitente;

    public SmtpEmailSender(JavaMailSender mailSender, String remitente) {
        this.mailSender = mailSender;
        this.remitente = remitente;
    }

    @Override
    public void envia(String destinatario, String asunto, String cuerpo) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente);
        mensaje.setTo(destinatario);
        mensaje.setSubject(asunto);
        mensaje.setText(cuerpo);
        mailSender.send(mensaje);
    }
}
