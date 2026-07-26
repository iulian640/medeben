package es.medeben.service;

import es.medeben.controller.RecursoNoEncontradoException;
import es.medeben.domain.usuario.Usuario;
import es.medeben.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "Reclamación en curso" (contrato §Retención): la única vía para que el
 * texto de consentimiento v1.0 ("salvo que declares una reclamación en
 * curso") deje de ser una promesa incumplible — sin este servicio,
 * {@link Usuario#marcaReclamacionEnCurso} era inalcanzable desde ningún
 * endpoint y {@code UbicacionesPurga} habría borrado la prueba de una
 * reclamación viva a los 15 meses sin aviso.
 */
class ReclamacionEnCursoServiceTest {

    private static final UUID USUARIO = UUID.randomUUID();

    private UsuarioRepository usuarios;
    private ReclamacionEnCursoService servicio;

    @BeforeEach
    void arranque() {
        usuarios = mock(UsuarioRepository.class);
        servicio = new ReclamacionEnCursoService(usuarios);
    }

    @Test
    @DisplayName("declarar marca reclamacionEnCurso a true y guarda")
    void declararMarcaAtrue() {
        Usuario usuario = new Usuario("trabajador@example.com", "{noop}hash");
        when(usuarios.findById(USUARIO)).thenReturn(Optional.of(usuario));
        when(usuarios.save(any())).thenAnswer(inv -> inv.getArgument(0));

        servicio.declara(USUARIO);

        assertThat(usuario.isReclamacionEnCurso()).isTrue();
        verify(usuarios).save(usuario);
    }

    @Test
    @DisplayName("retirar marca reclamacionEnCurso a false y guarda")
    void retirarMarcaAfalse() {
        Usuario usuario = new Usuario("trabajador@example.com", "{noop}hash");
        usuario.marcaReclamacionEnCurso(true);
        when(usuarios.findById(USUARIO)).thenReturn(Optional.of(usuario));
        when(usuarios.save(any())).thenAnswer(inv -> inv.getArgument(0));

        servicio.retira(USUARIO);

        assertThat(usuario.isReclamacionEnCurso()).isFalse();
        verify(usuarios).save(usuario);
    }

    @Test
    @DisplayName("usuario inexistente da 404")
    void usuarioInexistenteDa404() {
        when(usuarios.findById(USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.declara(USUARIO)).isInstanceOf(RecursoNoEncontradoException.class);
    }
}
