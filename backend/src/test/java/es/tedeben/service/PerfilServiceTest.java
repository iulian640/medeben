package es.tedeben.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.tedeben.domain.usuario.Perfil;
import es.tedeben.repository.ConvenioCatalog;
import es.tedeben.repository.HechosCatalog;
import es.tedeben.repository.OcupacionesCatalog;
import es.tedeben.repository.PerfilRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PerfilService — guardar dónde y de qué trabaja el usuario")
class PerfilServiceTest {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final OffsetDateTime AHORA = OffsetDateTime.parse("2026-07-08T10:15:00+02:00");
    private static final Clock RELOJ_FIJO = Clock.fixed(AHORA.toInstant(), ZoneId.of("Europe/Madrid"));

    private PerfilRepository repositorio;
    private PerfilService servicio;

    @BeforeEach
    void arranque() {
        repositorio = mock(PerfilRepository.class);
        ObjectMapper mapper = new ObjectMapper();
        servicio = new PerfilService(repositorio, new ConvenioCatalog(mapper),
                new OcupacionesCatalog(mapper),
                new DimensionesCatalogoValidator(new HechosCatalog(mapper)), RELOJ_FIJO);
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
        assertThat(perfil.getActualizadoEn()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("guardar con perfil existente lo actualiza in situ (fila mutable, nunca una segunda fila)")
    void actualizaPerfilExistente() {
        Perfil existente = new Perfil(USUARIO, "Madrid", "hosteleria", "madrid-hosteleria",
                "cocinero", Map.of("nivel", "III"), new BigDecimal("1400"), null,
                AHORA.minusDays(30));
        when(repositorio.findById(USUARIO)).thenReturn(Optional.of(existente));

        Perfil guardado = servicio.guarda(USUARIO, "Madrid", "hosteleria", "camarero",
                Map.of("tabla", "general", "nivel", "II-A", "claseEmpresa", "A"),
                new BigDecimal("1500"), null);

        assertThat(guardado).isSameAs(existente);
        assertThat(guardado.getPuestoId()).isEqualTo("camarero");
        assertThat(guardado.getDimensiones()).containsEntry("nivel", "II-A");
        assertThat(guardado.getSalarioBaseMensual()).isEqualByComparingTo("1500");
        assertThat(guardado.getActualizadoEn()).isEqualTo(AHORA);
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
    @DisplayName("dimensión con valor kilométrico → IllegalArgument (tope de almacenamiento)")
    void dimensionKilometrica() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> servicio.guarda(USUARIO, "Madrid", "hosteleria", null,
                        Map.of("nivel", "x".repeat(401)), null, null));
    }

    @Test
    @DisplayName("el valor real más largo del catálogo (categoría de restauración colectiva, cientos de caracteres) sí cabe y valida")
    void valorLargoDelCatalogoCabe() {
        // El validador de dimensiones no puede rechazar valores largos por ser
        // largos: el corpus real tiene categorías de cientos de caracteres. Se
        // toma el HECHO real con la categoría más larga y se usan SUS dimensiones
        // completas (categoria + provincia), que sí resuelven tabla.
        Map<String, String> dimsLargas = new HechosCatalog(new ObjectMapper())
                .deConvenio("estatal-restauracion-colectiva").stream()
                .filter(h -> "salarioBase".equals(h.concepto()))
                .map(h -> h.dimensiones())
                .filter(dims -> dims.get("categoria") != null)
                .max(java.util.Comparator.comparingInt(dims -> dims.get("categoria").length()))
                .orElseThrow();
        String categoriaLarga = dimsLargas.get("categoria");

        Perfil perfil = servicio.guarda(USUARIO, "Madrid", "restauracion-colectiva", null,
                dimsLargas, null, null);

        assertThat(categoriaLarga.length()).isGreaterThan(100);
        assertThat(perfil.getConvenioId()).isEqualTo("estatal-restauracion-colectiva");
        assertThat(perfil.getDimensiones()).containsEntry("categoria", categoriaLarga);
    }

    @Test
    @DisplayName("dimensión con clave inexistente en el convenio → 422 (no se guarda basura que no resolvería tabla)")
    void claveDeDimensionDesconocida() {
        assertThatExceptionOfType(es.tedeben.controller.DimensionDesconocidaException.class)
                .isThrownBy(() -> servicio.guarda(USUARIO, "Madrid", "hosteleria", null,
                        Map.of("sector", "restaurante"), null, null))
                .withMessageContaining("sector");
    }

    @Test
    @DisplayName("dimensión con valor inexistente para una clave válida → 422 con clave y valor en el mensaje")
    void valorDeDimensionDesconocido() {
        assertThatExceptionOfType(es.tedeben.controller.DimensionDesconocidaException.class)
                .isThrownBy(() -> servicio.guarda(USUARIO, "Madrid", "hosteleria", null,
                        Map.of("nivel", "ZZ"), null, null))
                .withMessageContaining("nivel")
                .withMessageContaining("ZZ");
    }

    @Test
    @DisplayName("carrera de creación: si el insert choca con la PK, reintenta como actualización")
    void carreraDeCreacionReintentaComoActualizacion() {
        Perfil creadoPorLaOtraPeticion = new Perfil(USUARIO, "Madrid", "hosteleria",
                "madrid-hosteleria", "cocinero", Map.of("nivel", "III"),
                new BigDecimal("1400"), null, AHORA.minusMinutes(1));
        // Primera lectura: el perfil aún no existe. Segunda (tras el choque de
        // PK): la petición concurrente ya lo había insertado.
        when(repositorio.findById(USUARIO))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(creadoPorLaOtraPeticion));
        when(repositorio.save(any(Perfil.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"perfiles_pkey\""))
                .thenAnswer(inv -> inv.getArgument(0));

        Perfil guardado = servicio.guarda(USUARIO, "Madrid", "hosteleria", "camarero",
                Map.of("tabla", "general", "nivel", "II-A", "claseEmpresa", "A"),
                new BigDecimal("1500"), null);

        assertThat(guardado).isSameAs(creadoPorLaOtraPeticion);
        assertThat(guardado.getPuestoId()).isEqualTo("camarero");
        assertThat(guardado.getSalarioBaseMensual()).isEqualByComparingTo("1500");
        assertThat(guardado.getActualizadoEn()).isEqualTo(AHORA);
        verify(repositorio, times(2)).save(any(Perfil.class));
    }

    @Test
    @DisplayName("si el reintento también choca, se propaga: un solo reintento, nunca bucle")
    void elReintentoNoSeRepiteEnBucle() {
        when(repositorio.findById(USUARIO)).thenReturn(Optional.empty());
        when(repositorio.save(any(Perfil.class)))
                .thenThrow(new DataIntegrityViolationException("choque persistente"));

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> servicio.guarda(USUARIO, "Madrid", "hosteleria", null,
                        Map.of(), null, null));

        verify(repositorio, times(2)).save(any(Perfil.class));
    }

    @Test
    @DisplayName("busca el perfil por usuario")
    void buscaPorUsuario() {
        Perfil existente = new Perfil(USUARIO, "Madrid", "hosteleria", "madrid-hosteleria",
                "cocinero", Map.of("nivel", "III"), null, null, AHORA);
        when(repositorio.findById(USUARIO)).thenReturn(Optional.of(existente));

        assertThat(servicio.busca(USUARIO)).contains(existente);
    }
}
