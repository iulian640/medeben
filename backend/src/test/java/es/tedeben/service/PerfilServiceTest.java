package es.tedeben.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.tedeben.domain.usuario.Perfil;
import es.tedeben.repository.ConvenioCatalog;
import es.tedeben.repository.OcupacionesCatalog;
import es.tedeben.repository.PerfilRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("PerfilService — guardar dónde y de qué trabaja el usuario")
class PerfilServiceTest {

    private static final UUID USUARIO = UUID.randomUUID();

    private PerfilRepository repositorio;
    private PerfilService servicio;

    @BeforeEach
    void arranque() {
        repositorio = mock(PerfilRepository.class);
        ObjectMapper mapper = new ObjectMapper();
        servicio = new PerfilService(repositorio, new ConvenioCatalog(mapper), new OcupacionesCatalog(mapper));
        when(repositorio.save(any(Perfil.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("guarda un perfil coherente: el convenio se resuelve de provincia+subsector, no lo elige el cliente")
    void guardaPerfilValido() {
        Perfil perfil = servicio.guarda(USUARIO, "Madrid", "hosteleria", "cocinero",
                Map.of("tabla", "general", "nivel", "III", "claseEmpresa", "B"),
                new BigDecimal("1400"), new BigDecimal("2103.42"));

        assertThat(perfil.getConvenioId()).isEqualTo("madrid-hosteleria");
        assertThat(perfil.getPuestoId()).isEqualTo("cocinero");
        assertThat(perfil.getDimensiones()).containsEntry("nivel", "III");
    }

    @Test
    @DisplayName("provincia sin convenio → RecursoNoEncontrado (no se guarda basura)")
    void provinciaInvalida() {
        assertThatExceptionOfType(es.tedeben.controller.RecursoNoEncontradoException.class)
                .isThrownBy(() -> servicio.guarda(USUARIO, "Narnia", "hosteleria", null,
                        Map.of(), null, null));
    }

    @Test
    @DisplayName("puesto fuera de la lista curada → IllegalArgument")
    void puestoInvalido() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> servicio.guarda(USUARIO, "Madrid", "hosteleria", "astronauta",
                        Map.of(), null, null));
    }

    @Test
    @DisplayName("salario negativo → IllegalArgument")
    void salarioNegativo() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> servicio.guarda(USUARIO, "Madrid", "hosteleria", null,
                        Map.of(), new BigDecimal("-1"), null));
    }

    @Test
    @DisplayName("busca el perfil por usuario")
    void buscaPorUsuario() {
        Perfil existente = new Perfil(USUARIO, "Madrid", "hosteleria", "madrid-hosteleria",
                "cocinero", Map.of("nivel", "III"), null, null);
        when(repositorio.findById(USUARIO)).thenReturn(Optional.of(existente));

        assertThat(servicio.busca(USUARIO)).contains(existente);
    }
}
