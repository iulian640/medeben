package es.medeben.service;

import es.medeben.config.CorreoProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * El listener que convierte el evento (post-commit) en el correo real. Dos
 * invariantes: el enlace del cuerpo lleva el token tal cual (es lo único que
 * el usuario necesita), y un fallo del envío NUNCA se propaga — el registro
 * ya está commiteado y el usuario puede pedir el reenvío; propagar solo
 * ensuciaría el log del executor sin deshacer nada.
 */
@DisplayName("EnviadorVerificacionEmail — del evento al correo")
class EnviadorVerificacionEmailTest {

    private static final CorreoProperties PROPS = new CorreoProperties(
            "log", "no-reply@medeben.net", "https://medeben.net/verifica-email", null);

    private EmailSender sender;
    private EnviadorVerificacionEmail enviador;

    @BeforeEach
    void arranque() {
        sender = mock(EmailSender.class);
        enviador = new EnviadorVerificacionEmail(sender, PROPS);
    }

    @Test
    @DisplayName("envía al destinatario un correo cuyo cuerpo contiene el enlace con el token")
    void enviaConEnlace() {
        enviador.alSolicitarVerificacion(
                new VerificacionEmailSolicitada("trabajador@example.com", "token-opaco-123"));

        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(sender).envia(eq("trabajador@example.com"), anyString(), cuerpo.capture());
        assertThat(cuerpo.getValue())
                .contains("https://medeben.net/verifica-email?token=token-opaco-123");
    }

    @Test
    @DisplayName("un fallo del envío se registra pero NO se propaga (el reenvío existe para eso)")
    void falloDelEnvioNoSePropaga() {
        doThrow(new IllegalStateException("SMTP caído"))
                .when(sender).envia(any(), any(), any());

        assertThatCode(() -> enviador.alSolicitarVerificacion(
                new VerificacionEmailSolicitada("trabajador@example.com", "t")))
                .doesNotThrowAnyException();
    }
}
