package es.medeben.service;

import es.medeben.domain.convenio.Hecho;
import es.medeben.repository.ConvenioCatalog;
import es.medeben.repository.HechosCatalog;
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
    private final ConvenioCatalog convenios;

    public TablaSalarialService(HechosCatalog hechos, ConvenioCatalog convenios) {
        this.hechos = hechos;
        this.convenios = convenios;
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

        String fuenteUrl = convenios.porId(convenioId).map(c -> c.fuenteUrl()).orElse(null);
        Optional<Hecho> vigente = unico(candidatos.stream().filter(h -> h.vigenteEn(fecha)).toList(), fecha);
        if (vigente.isPresent()) {
            Hecho h = vigente.get();
            return Optional.of(new SalarioBaseResuelto(h.importe(), unidad(h),
                    List.of(new Cita(cita(h), fuenteUrl))));
        }

        // Ultraactividad: la última tabla publicada antes de la fecha sigue aplicando
        // hasta que se publique una nueva (el convenio vencido no caduca).
        List<Hecho> anteriores = candidatos.stream()
                .filter(h -> h.hasta().isBefore(fecha))
                .toList();
        Optional<Hecho> ultima = anteriores.stream().max(Comparator.comparing(Hecho::hasta));
        if (ultima.isPresent()
                && anteriores.stream().filter(h -> h.hasta().equals(ultima.get().hasta())).count() > 1) {
            // Mismo guardia que en la rama vigente: dos tablas "últimas" empatadas = capa derivada corrupta.
            throw new IllegalStateException(
                    "Capa derivada ambigua: varias tablas terminan en " + ultima.get().hasta()
                            + " para " + ultima.get().dimensiones());
        }
        return ultima.map(h -> new SalarioBaseResuelto(h.importe(), unidad(h), List.of(
                new Cita(cita(h), fuenteUrl),
                new Cita("Tabla vigente hasta " + FECHA.format(h.hasta())
                        + ", aplicada por ultraactividad: sigue en vigor hasta que se publique la nueva",
                        fuenteUrl))));
    }

    private static String unidad(Hecho h) {
        return h.unidad() == null ? "EUR/mes" : h.unidad();
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
