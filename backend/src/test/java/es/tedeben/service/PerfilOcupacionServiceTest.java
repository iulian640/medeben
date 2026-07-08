package es.tedeben.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.tedeben.repository.HechosCatalog;
import es.tedeben.repository.OcupacionesCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PerfilOcupacionService — del puesto en cristiano a las dimensiones de la tabla (D20)")
class PerfilOcupacionServiceTest {

    private static PerfilOcupacionService servicio;

    @BeforeAll
    static void arranque() {
        ObjectMapper mapper = new ObjectMapper();
        servicio = new PerfilOcupacionService(new OcupacionesCatalog(mapper), new HechosCatalog(mapper));
    }

    @Test
    @DisplayName("la lista curada de puestos tiene 16 entradas con etiqueta en cristiano")
    void listaCurada() {
        assertThat(servicio.puestos()).hasSize(16);
        assertThat(servicio.puestos())
                .anySatisfy(p -> {
                    assertThat(p.id()).isEqualTo("cocinero");
                    assertThat(p.etiqueta()).isEqualTo("Cocinero/a");
                });
    }

    @Test
    @DisplayName("cocinero en madrid-hosteleria → nivel III y queda pendiente la clase de empresa A/B/C")
    void cocineroMadrid() {
        var resuelta = servicio.resuelve("madrid-hosteleria", "cocinero").orElseThrow();

        assertThat(resuelta.dimensiones())
                .containsEntry("tabla", "general")
                .containsEntry("nivel", "III");
        assertThat(resuelta.pendientes()).hasSize(1);
        assertThat(resuelta.pendientes().getFirst().dimension()).isEqualTo("claseEmpresa");
        assertThat(resuelta.pendientes().getFirst().valores()).containsExactly("A", "B", "C");
    }

    @Test
    @DisplayName("puesto no contemplado en el convenio (camarera de pisos en hostelería) → vacío")
    void puestoNoContemplado() {
        assertThat(servicio.resuelve("madrid-hosteleria", "camarera-pisos")).isEmpty();
    }

    @Test
    @DisplayName("convenio sin mapeo de ocupaciones todavía → vacío (cae al modo manual)")
    void convenioSinMapeo() {
        assertThat(servicio.resuelve("convenio-inexistente", "cocinero")).isEmpty();
    }

    @Test
    @DisplayName("puesto desconocido → vacío, no explota")
    void puestoDesconocido() {
        assertThat(servicio.resuelve("madrid-hosteleria", "astronauta")).isEmpty();
    }
}
