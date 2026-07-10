package es.medeben.service;

import es.medeben.config.RequiereBaseDeDatos;
import es.medeben.domain.usuario.Usuario;
import es.medeben.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Borrado de cuenta (RGPD art. 17, derecho de supresión). Borrado REAL, no
 * marcado: la fila de usuarios desaparece y el ON DELETE CASCADE del esquema
 * arrastra perfil, cuadrantes y apuntes — que son la evidencia del usuario,
 * por eso el frontend avisa de descargar los PDF antes. Exige re-confirmar la
 * contraseña: un móvil desbloqueado en la barra no puede destruir la evidencia
 * de meses.
 */
@Service
@RequiereBaseDeDatos
public class CuentaService {

    private static final Logger log = LoggerFactory.getLogger(CuentaService.class);

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final InformeAnualService informesAnuales;

    public CuentaService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder,
                         InformeAnualService informesAnuales) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.informesAnuales = informesAnuales;
    }

    @Transactional
    public void borraCuenta(UUID usuarioId, String password) {
        Usuario usuario = usuarios.findById(usuarioId)
                // Token vivo de una cuenta que ya no existe: 401, la sesión no vale.
                .orElseThrow(CredencialesInvalidasException::new);
        if (!passwordEncoder.matches(password, usuario.getPasswordHash())) {
            throw new PasswordIncorrectaException();
        }
        usuarios.delete(usuario);
        // Su PDF anual cacheado lleva email y salarios: purgarlo TRAS el commit
        // (security review, TOCTOU): purgado dentro de la transacción, una
        // petición concurrente de informe aún ve al usuario en BD (READ
        // COMMITTED), regeneraría el PDF y lo repoblaría en la caché hasta 24 h
        // después del borrado. Tras el commit ya no hay datos con que regenerar.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    informesAnuales.invalida(usuarioId);
                }
            });
        } else {
            // Sin transacción activa (tests unitarios): purga directa.
            informesAnuales.invalida(usuarioId);
        }
        // Solo el id técnico: tras el borrado ya no identifica a nadie. Nunca el email.
        log.info("Cuenta borrada a petición del usuario (RGPD art. 17): {}", usuarioId);
    }
}
