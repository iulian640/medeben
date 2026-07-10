package es.medeben.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.medeben.domain.convenio.Convenio;
import es.medeben.repository.ConvenioCatalog;
import es.medeben.repository.HechosCatalog;
import es.medeben.repository.OcupacionesCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Barrido de cobertura: recorre TODOS los convenios × TODOS los puestos y, para
 * cada combinación mapeada, responde las preguntas como haría el usuario en el
 * frontend (re-resolviendo tras cada respuesta) hasta que no quedan pendientes.
 * En ese punto exige que exista tabla salarial (que salga un salario, no un 404).
 *
 * <p>Un puesto no mapeado en un convenio es honesto (se salta). Lo que NO puede
 * pasar es que la resolución "termine" (sin preguntas) y luego no haya salario:
 * eso es el callejón sin salida (el bug de los condicionales, generalizado).
 */
@DisplayName("Cobertura: ningún puesto resuelve a un callejón sin salario")
class CoberturaSalarioTest {

    private static ConvenioCatalog convenios;
    private static PerfilOcupacionService perfil;
    private static TablaSalarialService tablas;

    private static final LocalDate FECHA = LocalDate.of(2026, 7, 8);
    private static final int TOPE_HOJAS = 400; // corta la explosión combinatoria por par
    private static final int TOPE_PROFUNDIDAD = 7;

    @BeforeAll
    static void arranque() {
        ObjectMapper mapper = new ObjectMapper();
        convenios = new ConvenioCatalog(mapper);
        HechosCatalog hechos = new HechosCatalog(mapper);
        perfil = new PerfilOcupacionService(new OcupacionesCatalog(mapper), hechos);
        tablas = new TablaSalarialService(hechos, convenios);
    }

    @Test
    @DisplayName("cada puesto mapeado, respondiendo sus preguntas, llega a un salario")
    void ningunCallejonSinSalario() {
        List<String> callejones = new ArrayList<>();
        List<String> truncados = new ArrayList<>();
        int hojasOk = 0;
        int paresMapeados = 0;

        for (Convenio c : convenios.todos()) {
            for (var puesto : perfil.puestos()) {
                if (perfil.resuelve(c.id(), puesto.id()).isEmpty()) {
                    continue; // puesto no mapeado en este convenio: honesto
                }
                paresMapeados++;
                int[] contador = {0};
                hojasOk += explora(c.id(), puesto.id(), new LinkedHashMap<>(), 0,
                        callejones, truncados, contador);
            }
        }

        // Deduplicamos por par (convenio/puesto) + tipo para que un bucle no infle la lista.
        List<String> resumen = callejones.stream()
                .map(s -> {
                    int corte = s.indexOf(" respuestas=");
                    if (corte < 0) {
                        corte = s.indexOf(" dims=");
                    }
                    return corte < 0 ? s : s.substring(0, corte);
                })
                .distinct()
                .sorted()
                .toList();

        System.out.println("=== COBERTURA ===");
        System.out.println("Pares (convenio,puesto) mapeados: " + paresMapeados);
        System.out.println("Hojas que llegan a salario: " + hojasOk);
        System.out.println("Truncados por tope combinatorio: " + truncados.stream().distinct().count());
        System.out.println("CALLEJONES (hojas): " + callejones.size()
                + " | DISTINTOS (convenio/puesto/tipo): " + resumen.size());
        resumen.forEach(s -> System.out.println("  " + s));

        // El tope combinatorio no debe estar ocultando un callejón en la cola sin
        // explorar: si algún par lo alcanza, el guardián degrada en silencio → falla.
        assertThat(truncados)
                .as("pares truncados por el tope combinatorio (podrían esconder un callejón)")
                .isEmpty();
        assertThat(resumen)
                .as("pares convenio/puesto que resuelven a un callejón sin salario")
                .isEmpty();
    }

    /** DFS: responde la primera pregunta con cada valor y re-resuelve, como el frontend. */
    private int explora(String cid, String puesto, Map<String, String> respuestas, int prof,
                        List<String> callejones, List<String> truncados, int[] contador) {
        if (prof > TOPE_PROFUNDIDAD) {
            callejones.add("BUCLE " + cid + "/" + puesto + " respuestas=" + respuestas);
            return 0;
        }
        var opt = perfil.resuelve(cid, puesto, respuestas);
        if (opt.isEmpty()) {
            return 0;
        }
        var oc = opt.get();
        if (oc.pendientes().isEmpty()) {
            boolean haySalario = tablas.salarioBaseMinimo(cid, oc.dimensiones(), FECHA).isPresent();
            if (!haySalario) {
                callejones.add(cid + "/" + puesto + " SIN TABLA dims=" + oc.dimensiones()
                        + " (respondiendo " + respuestas + ")");
            }
            return 1;
        }
        var pregunta = oc.pendientes().get(0);
        int hojas = 0;
        for (String valor : pregunta.valores()) {
            if (contador[0] >= TOPE_HOJAS) {
                truncados.add(cid + "/" + puesto);
                break;
            }
            contador[0]++;
            Map<String, String> siguiente = new LinkedHashMap<>(respuestas);
            siguiente.put(pregunta.dimension(), valor);
            hojas += explora(cid, puesto, siguiente, prof + 1, callejones, truncados, contador);
        }
        return hojas;
    }
}
