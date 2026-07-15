package es.medeben.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEGURIDAD (MEDIUM, review 2026-07-15): {@code LogEmailSender} es el sender
 * por DEFAULT en producción hasta que se configure el SMTP (Brevo) — antes
 * del fix volcaba a INFO el destinatario en claro y el CUERPO ENTERO, que
 * incluye {@code ...?token=<TOKEN_EN_CLARO>}. Eso dejaba, en los logs,
 * tokens de verificación vivos 24h y la lista completa de emails registrados:
 * justo la PII que la respuesta uniforme del registro (anti-enumeración)
 * protege. Este test enganchа un {@link ListAppender} al logger real (no hay
 * forma de mockear un {@code Logger} SLF4J estático) y comprueba que ni el
 * token ni el email en claro sobreviven al log.
 */
@DisplayName("LogEmailSender — sender de dev/test/CI, no debe filtrar PII ni tokens")
class LogEmailSenderTest {

    private static final String DESTINATARIO = "trabajador@example.com";
    private static final String TOKEN = "token-super-secreto-123";
    private static final String CUERPO = """
            Hola:

            Entra en este enlace (caduca en 24 horas):

            https://medeben.net/verifica-email?token=%s
            """.formatted(TOKEN);

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;
    private LogEmailSender sender;

    @BeforeEach
    void arranque() {
        logger = (Logger) LoggerFactory.getLogger(LogEmailSender.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        sender = new LogEmailSender();
    }

    @AfterEach
    void limpieza() {
        logger.detachAppender(appender);
    }

    @Test
    @DisplayName("NO vuelca el token de verificación en el log")
    void noVuelcaElToken() {
        sender.envia(DESTINATARIO, "Confirma tu correo", CUERPO);

        String logueado = mensajesConcatenados();
        assertThat(logueado).doesNotContain(TOKEN);
    }

    @Test
    @DisplayName("NO vuelca el cuerpo completo del correo (ahí viaja el enlace con el token)")
    void noVuelcaElCuerpo() {
        sender.envia(DESTINATARIO, "Confirma tu correo", CUERPO);

        String logueado = mensajesConcatenados();
        assertThat(logueado).doesNotContain("?token=");
        assertThat(logueado).doesNotContain("verifica-email");
    }

    @Test
    @DisplayName("NO vuelca el email del destinatario en claro — lo enmascara")
    void noVuelcaElEmailEnClaro() {
        sender.envia(DESTINATARIO, "Confirma tu correo", CUERPO);

        String logueado = mensajesConcatenados();
        assertThat(logueado).doesNotContain(DESTINATARIO);
        assertThat(logueado).contains("t***@example.com");
    }

    @Test
    @DisplayName("SÍ deja constancia de que el envío se disparó (visibilidad de dev)")
    void dejaConstanciaDelEnvio() {
        sender.envia(DESTINATARIO, "Confirma tu correo", CUERPO);

        assertThat(appender.list).hasSize(1);
        assertThat(mensajesConcatenados()).containsIgnoringCase("no se envía");
    }

    private String mensajesConcatenados() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
    }
}
