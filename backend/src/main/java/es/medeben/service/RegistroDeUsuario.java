package es.medeben.service;

import es.medeben.config.RequiereBaseDeDatos;
import es.medeben.domain.usuario.Usuario;
import es.medeben.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inserta el usuario NUEVO y emite su verificación en la MISMA transacción,
 * SIEMPRE en una transacción PROPIA (REQUIRES_NEW), aunque quien llama ya
 * tenga una abierta. Motivo (documentado también en {@code PerfilService},
 * que sufre la misma trampa con el UNIQUE de perfiles): si dos registros
 * simultáneos del mismo email nuevo chocan contra el UNIQUE de
 * {@code usuarios.email}, PostgreSQL aborta la transacción a nivel de
 * CONEXIÓN — abortada significa que NINGÚN comando posterior, ni siquiera el
 * COMMIT final, puede ejecutarse en ella. Si esa transacción fuera la misma
 * que la del llamador, capturar la excepción en Java no arregla nada: el
 * intento de commit del proxy exterior revienta con
 * {@code UnexpectedRollbackException} (la transacción ya estaba marcada
 * rollback-only) y el registro, que debía responder 201 uniforme, se
 * convierte en un 500.
 *
 * <p>Confinar el INSERT en su PROPIA transacción hace que ese aborto no se
 * lleve nada más por delante: si {@link UsuarioRepository#saveAndFlush}
 * revienta, esta transacción (y solo esta) hace rollback de forma limpia —
 * la excepción escapa del método y Spring nunca intenta comitearla — y quien
 * llama ({@link AuthService#registra}) recibe una excepción normal sobre una
 * transacción propia que nunca se tocó.
 *
 * <p>El token de verificación se emite DENTRO de esta misma transacción a
 * propósito: si el INSERT del usuario falla por la carrera, el método nunca
 * llega a emitir el token, así que no puede quedar un token de verificación
 * huérfano (de un usuario que, por la carrera, no llegó a crearse). Si el
 * INSERT tiene éxito, el token se guarda y el evento se publica en la MISMA
 * transacción — se comitean juntos o no se comitea ninguno.
 *
 * <p>Tiene que ser un bean DISTINTO de {@code AuthService}: la
 * auto-invocación ({@code this.metodo()}) no pasa por el proxy de Spring, así
 * que un {@code @Transactional} en un método (privado o no) de la misma clase
 * que lo llama no tendría ningún efecto — necesitamos una llamada real a
 * TRAVÉS del proxy para que la propagación REQUIRES_NEW se aplique.
 */
@Service
@RequiereBaseDeDatos
class RegistroDeUsuario {

    private final UsuarioRepository usuarios;
    private final EmisorVerificacion emisor;

    RegistroDeUsuario(UsuarioRepository usuarios, EmisorVerificacion emisor) {
        this.usuarios = usuarios;
        this.emisor = emisor;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void registra(String email, String passwordHash) {
        Usuario nuevo = usuarios.saveAndFlush(new Usuario(email, passwordHash));
        emisor.emite(nuevo);
    }
}
