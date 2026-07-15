package es.medeben.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 en hex para los tokens opacos (refresh y verificación de email):
 * lo único que toca la BD — el token en claro solo viaja al cliente o al
 * evento que lo manda por correo. Compartido entre {@link AuthService} y
 * {@link EmisorVerificacion} para no duplicar el mismo cálculo dos veces.
 */
final class Sha256 {

    private Sha256() {
    }

    static String hex(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
