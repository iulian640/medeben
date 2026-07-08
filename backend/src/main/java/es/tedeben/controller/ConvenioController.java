package es.tedeben.controller;

import es.tedeben.domain.convenio.Subsector;
import es.tedeben.dto.ConvenioDetalleDto;
import es.tedeben.dto.ConvenioResumenDto;
import es.tedeben.repository.ConvenioCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Consulta pública de convenios: los datos son públicos (boletines oficiales,
 * ya versionados en el repo) — no requieren autenticación.
 */
@RestController
@RequestMapping("/api/v1")
public class ConvenioController {

    private final ConvenioCatalog catalog;

    public ConvenioController(ConvenioCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/provincias")
    public List<String> provincias() {
        return catalog.provincias();
    }

    @GetMapping("/convenios")
    public List<ConvenioResumenDto> convenios() {
        return catalog.todos().stream().map(ConvenioResumenDto::desde).toList();
    }

    /** El convenio que aplica según dónde y en qué tipo de sitio trabajas (D20). */
    @GetMapping("/convenios/para-trabajador")
    public ConvenioResumenDto paraTrabajador(
            @RequestParam String provincia, @RequestParam String subsector) {
        Subsector clave = Subsector.desdeClave(subsector);
        return catalog.paraTrabajador(provincia, clave)
                .map(ConvenioResumenDto::desde)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No hay convenio para la provincia '" + provincia + "' y subsector '" + subsector + "'"));
    }

    @GetMapping("/convenios/{id}")
    public ConvenioDetalleDto detalle(@PathVariable String id) {
        return catalog.porId(id)
                .map(ConvenioDetalleDto::desde)
                .orElseThrow(() -> new RecursoNoEncontradoException("Convenio no encontrado: " + id));
    }
}
