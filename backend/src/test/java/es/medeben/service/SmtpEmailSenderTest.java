package es.medeben.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("SmtpEmailSender — composición del mensaje")
class SmtpEmailSenderTest {

    @Test
    @DisplayName("compone el mensaje con remitente, destinatario, asunto y cuerpo tal cual")
    void componeElMensaje() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpEmailSender sender = new SmtpEmailSender(mailSender, "no-reply@medeben.net");

        sender.envia("trabajador@example.com", "Confirma tu correo", "cuerpo del mensaje");

        ArgumentCaptor<SimpleMailMessage> mensaje = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mensaje.capture());
        assertThat(mensaje.getValue().getFrom()).isEqualTo("no-reply@medeben.net");
        assertThat(mensaje.getValue().getTo()).containsExactly("trabajador@example.com");
        assertThat(mensaje.getValue().getSubject()).isEqualTo("Confirma tu correo");
        assertThat(mensaje.getValue().getText()).isEqualTo("cuerpo del mensaje");
    }
}
