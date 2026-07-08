package es.tedeben.service;

import es.tedeben.domain.convenio.Hecho;
import es.tedeben.repository.HechosCatalog;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Lookup del salario base mínimo en la capa derivada normalizada. Una sola
 * lógica para los 55 convenios: filtrar hechos por dimensiones y fecha.
 * Si la fecha cae fuera de toda vigencia publicada, aplica la última tabla
 * anterior (ultraactividad) y lo dice en las citas. Convenio sin derivar o
 * dimensiones que no existen → vacío, nunca se inventa.
 */
@Service
public class TablaSalarialService {

    private static final String CONCEPTO_SALARIO_BASE = "salarioBase";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final HechosCatalog hechos;

    public TablaSalarialService(HechosCatalog hechos) {
        this.hechos = hechos;
    }

    public Optional<SalarioBaseResuelto> salarioBaseMinimo(
            String convenioId, Map<String, String> dimensiones, LocalDate fecha) {
        Objects.requireNonNull(convenioId, "convenioId");
        Objects.requireNonNull(dimensiones, "dimensiones");
        Objects.requireNonNull(fecha, "fecha");

        List<Hecho> candidatos = hechos.deConvenio(convenioId).stream()
                .filter(h -> CONCEPTO_SALARIO_BASE.equals(h.concepto()))
                .filter(h -> h.dimensiones().equals(dimensiones))
                .toList();
        if (candidatos.isEmpty()) {
            return Optional.empty();
        }

        Optional<Hecho> vigente = unico(candidatos.stream().filter(h -> h.vigenteEn(fecha)).toList(), fecha);
        if (vigente.isPresent()) {
            Hecho h = vigente.get();
            return Optional.of(new SalarioBaseResuelto(h.importe(), List.of(cita(h))));
        }

        // Ultraactividad: la última tabla publicada antes de la fecha sigue aplicando
        // hasta que se publique una nueva (el convenio vencido no caduca).
        return candidatos.stream()
                .filter(h -> h.hasta().isBefore(fecha))
                .max(Comparator.comparing(Hecho::hasta))
                .map(h -> new SalarioBaseResuelto(h.importe(), List.of(
                        cita(h),
                        "Tabla vigente hasta " + FECHA.format(h.hasta())
                                + ", aplicada por ultraactividad: sigue en vigor hasta que se publique la nueva")));
    }

    private static Optional<Hecho> unico(List<Hecho> vigentes, LocalDate fecha) {
        if (vigentes.size() > 1) {
            // Solo posible si la capa derivada viola la regla de no-solape: dato corrupto.
            throw new IllegalStateException(
                    "Capa derivada ambigua: " + vigentes.size() + " hechos vigentes en " + fecha
                            + " para " + vigentes.getFirst().dimensiones());
        }
        return vigentes.stream().findFirst();
    }

    private static String cita(Hecho h) {
        return "Salario base mínimo de " + h.importe().toPlainString() + " "
                + (h.unidad() == null ? "EUR/mes" : h.unidad())
                + " (" + h.articulo() + " del convenio, vigencia "
                + FECHA.format(h.desde()) + " a " + FECHA.format(h.hasta()) + ")";
    }
}
