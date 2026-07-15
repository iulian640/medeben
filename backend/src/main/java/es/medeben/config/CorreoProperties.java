package es.medeben.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del correo transaccional (prefijo {@code medeben.correo}).
 * A propósito FUERA del autoconfig {@code spring.mail.*}: así el arranque
 * sin SMTP (dev, test, o prod antes de configurar el proveedor) no depende
 * de placeholders sin resolver, y el modo queda explícito.
 *
 * @param modo            {@code log} (default: el correo se escribe en el log,
 *                        no sale nada) o {@code smtp} (envío real; exige
 *                        {@code smtp.host} y credenciales).
 * @param remitente       dirección From de los correos.
 * @param urlVerificacion URL (sin token) de la página del frontend que
 *                        consume el enlace de verificación.
 * @param smtp            host/puerto/credenciales, solo si {@code modo=smtp}.
 */
@ConfigurationProperties(prefix = "medeben.correo")
public record CorreoProperties(
        String modo,
        String remitente,
        String urlVerificacion,
        Smtp smtp) {

    public CorreoProperties {
        modo = modo != null ? modo : "log";
        remitente = remitente != null ? remitente : "no-reply@medeben.net";
        urlVerificacion = urlVerificacion != null ? urlVerificacion : "https://medeben.net/verifica-email";
    }

    public record Smtp(String host, int puerto, String usuario, String password) {
    }
}
