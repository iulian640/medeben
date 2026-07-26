package es.medeben.service;

import es.medeben.config.RequiereBaseDeDatos;
import es.medeben.controller.RecursoNoEncontradoException;
import es.medeben.domain.usuario.Usuario;
import es.medeben.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Declaración de "reclamación en curso" (contrato §Retención): suspende la
 * purga automática de ubicaciones a los 15 meses ({@link UbicacionesPurga})
 * mientras el usuario tiene una reclamación viva cuya prueba no quiere
 * perder. El texto de consentimiento v1.0 promete literalmente esta vía de
 * escape ("hasta 15 meses ... salvo que declares una reclamación en curso");
 * sin este servicio {@link Usuario#marcaReclamacionEnCurso} era código muerto
 * y la promesa, inalcanzable desde ningún endpoint.
 *
 * <p>Revisión anual, no automática (contrato): este servicio solo declara o
 * retira el flag; no hay caducidad propia.</p>
 */
@Service
@RequiereBaseDeDatos
public class ReclamacionEnCursoService {

    private final UsuarioRepository usuarios;

    public ReclamacionEnCursoService(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Transactional
    public void declara(UUID usuarioId) {
        cambia(usuarioId, true);
    }

    @Transactional
    public void retira(UUID usuarioId) {
        cambia(usuarioId, false);
    }

    private void cambia(UUID usuarioId, boolean enCurso) {
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        usuario.marcaReclamacionEnCurso(enCurso);
        usuarios.save(usuario);
    }
}
