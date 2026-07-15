package es.medeben.service;

/**
 * Envío de correo transaccional, agnóstico del proveedor. Dos
 * implementaciones (ver {@code CorreoConfig}): {@link LogEmailSender} para
 * dev/test/CI (no sale nada de la máquina) y {@link SmtpEmailSender} para
 * producción (cualquier SMTP: las credenciales llegan por entorno).
 */
public interface EmailSender {

    void envia(String destinatario, String asunto, String cuerpo);
}
