package es.medeben.service;

/**
 * Enmascara un email para que pueda aparecer en logs sin filtrar la PII
 * completa (p.ej. {@code "t***@example.com"}). Compartido entre
 * {@link EnviadorVerificacionEmail} (log de error del envío real) y
 * {@link LogEmailSender} (sender de dev/test/CI): ninguno de los dos debe
 * volcar la lista de emails registrados en claro en los logs.
 */
final class EnmascaradorEmail {

    private EnmascaradorEmail() {
    }

    static String enmascara(String email) {
        if (email == null) {
            return "***";
        }
        int arroba = email.indexOf('@');
        return arroba > 1 ? email.charAt(0) + "***" + email.substring(arroba) : "***";
    }
}
