package es.medeben.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.medeben.domain.convenio.Convenio;
import es.medeben.domain.convenio.Hecho;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EL VALIDADOR CRUZADO de la capa derivada: cada hecho de convenios/normalizado
 * debe cuadrar con la transcripción verificada (su fuente). Si este test falla,
 * la capa derivada ha divergido de la transcripción — se corrige la transcripción
 * y se re-deriva, nunca al revés.
 */
@DisplayName("Capa normalizada — validación cruzada contra las transcripciones")
class CapaNormalizadaValidadorTest {

    private static ConvenioCatalog convenios;
    private static HechosCatalog hechos;

    @BeforeAll
    static void carga() {
        ObjectMapper mapper = new ObjectMapper();
        convenios = new ConvenioCatalog(mapper);
        hechos = new HechosCatalog(mapper);
    }

    @Test
    @DisplayName("hay capa derivada cargada (pilotos: madrid y baleares)")
    void hayCapaDerivada() {
        assertThat(hechos.conveniosDerivados()).contains("madrid-hosteleria", "baleares-hosteleria");
        assertThat(hechos.deConvenio("madrid-hosteleria")).hasSize(78);
        assertThat(hechos.deConvenio("baleares-hosteleria")).hasSize(54);
    }

    @Test
    @DisplayName("todo convenio derivado existe en el catálogo de transcripciones")
    void convenioExiste() {
        for (String id : hechos.conveniosDerivados()) {
            assertThat(convenios.porId(id)).as("transcripción de %s", id).isPresent();
        }
    }

    @Test
    @DisplayName("PROCEDENCIA: cada importe coincide con la celda de la transcripción que cita su rutaCruda")
    void importesCuadranConLaTranscripcion() {
        for (String id : hechos.conveniosDerivados()) {
            Convenio convenio = convenios.porId(id).orElseThrow();
            for (Hecho hecho : hechos.deConvenio(id)) {
                JsonNode celda = convenio.raw().at(hecho.rutaCruda());
                assertThat(celda.isNumber())
                        .as("%s: rutaCruda %s no resuelve a un número", id, hecho.rutaCruda())
                        .isTrue();
                assertThat(celda.decimalValue())
                        .as("%s: %s difiere de la transcripción", id, hecho.rutaCruda())
                        .isEqualByComparingTo(hecho.importe());
            }
        }
    }

    @Test
    @DisplayName("cada hecho está completo: concepto, importe > 0, vigencia coherente, artículo")
    void hechosCompletos() {
        for (String id : hechos.conveniosDerivados()) {
            for (Hecho hecho : hechos.deConvenio(id)) {
                assertThat(hecho.concepto()).as("%s concepto", id).isNotBlank();
                assertThat(hecho.importe().signum()).as("%s importe > 0", id).isPositive();
                assertThat(hecho.articulo()).as("%s articulo", id).isNotBlank();
                assertThat(hecho.dimensiones()).as("%s dimensiones", id).isNotEmpty();
                assertThat(hecho.desde()).as("%s desde", id).isNotNull();
                assertThat(hecho.hasta()).as("%s hasta", id).isNotNull();
                assertThat(hecho.desde()).as("%s desde <= hasta", id).isBeforeOrEqualTo(hecho.hasta());
            }
        }
    }

    @Test
    @DisplayName("sin solapes: no hay dos hechos con mismas dimensiones y vigencias que se pisen")
    void sinSolapesDeVigencia() {
        // OJO (issue #231, review): este validador solo detecta el solape entre
        // hechos con dimensiones EXACTAMENTE iguales. CalculoConvenioService.hechoDerivado
        // casa por SUBCONJUNTO (un hecho de {provincia} sirve a un llamador de
        // {provincia,categoria}), así que dos hechos del mismo concepto donde una
        // dimensión es subconjunto de otra (p.ej. {provincia} y {provincia,categoria})
        // podrían casar ambos a la vez sin que este test lo cace — hoy no ocurre
        // (jornadaAnual/mensualidadesEquivalentes solo usan 'provincia'), pero si se
        // derivan hechos más granulares habrá que ampliar este validador al solape
        // por subconjunto, no solo por igualdad. Mientras, el motor rompe ruidoso
        // (IllegalStateException), no inventa: falla segura, no silenciosa.
        for (String id : hechos.conveniosDerivados()) {
            List<Hecho> lista = hechos.deConvenio(id);
            for (int i = 0; i < lista.size(); i++) {
                for (int j = i + 1; j < lista.size(); j++) {
                    Hecho a = lista.get(i);
                    Hecho b = lista.get(j);
                    if (!a.concepto().equals(b.concepto()) || !a.dimensiones().equals(b.dimensiones())) {
                        continue;
                    }
                    boolean solapan = !a.hasta().isBefore(b.desde()) && !b.hasta().isBefore(a.desde());
                    assertThat(solapan)
                            .as("%s: solape entre %s y %s para %s", id, a.rutaCruda(), b.rutaCruda(), a.dimensiones())
                            .isFalse();
                }
            }
        }
    }

    @Test
    @DisplayName("issue #231: ninguna dimensión del corpus pasa de los topes anti-DoS de los DTOs (clave 40, valor 400, 10 por hecho)")
    void dimensionesDentroDeLosTopesDeLosDto() {
        // Los endpoints anónimos acotan las dimensiones de la petición (clave
        // <=40, valor <=400, <=10 por mapa: SalarioBaseRequest/CalculoHorasExtraRequest).
        // El valor más largo del corpus es de la colectiva (categorías compuestas
        // separadas por «/»), a 325 hoy. Si una re-transcripción o derivación futura
        // superara un tope, ese usuario recibiría un 400 en la calculadora sin que
        // ninguna otra suite lo avisara: este test ata el corpus a los DTOs.
        for (String id : hechos.conveniosDerivados()) {
            for (Hecho hecho : hechos.deConvenio(id)) {
                assertThat(hecho.dimensiones().size())
                        .as("%s %s: nº de dimensiones", id, hecho.rutaCruda()).isLessThanOrEqualTo(10);
                hecho.dimensiones().forEach((clave, valor) -> {
                    assertThat(clave.length())
                            .as("%s %s: clave '%s' pasa de 40", id, hecho.rutaCruda(), clave).isLessThanOrEqualTo(40);
                    assertThat(valor.length())
                            .as("%s %s: valor de '%s' pasa de 400", id, hecho.rutaCruda(), clave).isLessThanOrEqualTo(400);
                });
            }
        }
    }

    @Test
    @DisplayName("las dimensiones de un hecho concreto se leen bien (madrid cocinero III clase B 2025)")
    void hechoConcretoBienParseado() {
        Hecho hecho = hechos.deConvenio("madrid-hosteleria").stream()
                .filter(h -> h.rutaCruda().equals("/tablasAnexoI/salariosBaseMensuales/2025/general/III/B"))
                .findFirst().orElseThrow();

        assertThat(hecho.dimensiones())
                .isEqualTo(Map.of("tabla", "general", "nivel", "III", "claseEmpresa", "B"));
        assertThat(hecho.importe()).isEqualByComparingTo("1250.91");
        assertThat(hecho.articulo()).isEqualTo("Anexo I C) c)");
    }
}
