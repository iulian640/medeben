package es.medeben.config;

import es.medeben.service.EmailSender;
import es.medeben.service.LogEmailSender;
import es.medeben.service.SmtpEmailSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * Cablea el {@link EmailSender} según {@code medeben.correo.modo}: SMTP real
 * solo cuando se pide explícitamente; en cualquier otro caso, log. El
 * {@link JavaMailSenderImpl} se construye a mano (no autoconfig de
 * {@code spring.mail.*}): sin SMTP configurado no hay placeholders que
 * resolver ni beans a medias.
 */
@Configuration
@EnableConfigurationProperties(CorreoProperties.class)
public class CorreoConfig {

    @Bean
    @ConditionalOnProperty(name = "medeben.correo.modo", havingValue = "smtp")
    public EmailSender smtpEmailSender(CorreoProperties propiedades) {
        CorreoProperties.Smtp smtp = propiedades.smtp();
        if (smtp == null || smtp.host() == null || smtp.host().isBlank()) {
            // Mejor reventar el arranque con un motivo claro que arrancar
            // "bien" y descubrir en el primer registro que no sale ningún correo.
            throw new IllegalStateException(
                    "medeben.correo.modo=smtp exige medeben.correo.smtp.host (y credenciales)");
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(smtp.host());
        sender.setPort(smtp.puerto());
        sender.setUsername(smtp.usuario());
        sender.setPassword(smtp.password());
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        // Sin timeouts, un SMTP colgado retiene el hilo async indefinidamente.
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        return new SmtpEmailSender(sender, propiedades.remitente());
    }

    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    public EmailSender logEmailSender() {
        return new LogEmailSender();
    }
}
