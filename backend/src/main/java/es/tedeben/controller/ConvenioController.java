package es.tedeben.controller;

import es.tedeben.domain.convenio.Subsector;
import es.tedeben.dto.ConvenioDetalleDto;
import es.tedeben.dto.ConvenioResumenDto;
import es.tedeben.repository.ConvenioCatalog;
import es.tedeben.service.OcupacionResuelta;
import es.tedeben.service.PerfilOcupacionService;
import es.tedeben.service.Puesto;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * Consulta pública de convenios: los datos son públicos (boletines oficiales,
 * ya versionados en el repo) — no requieren autenticación.
 */
@RestController
@RequestMapping("/api/v1")
public class ConvenioController {

    private final ConvenioCatalog catalog;
    private final PerfilOcupacionService perfilOcupacion;

    public ConvenioController(ConvenioCatalog catalog, PerfilOcupacionService perfilOcupacion) {
        this.catalog = catalog;
        this.perfilOcupacion = perfilOcupacion;
    }

    // Datos estáticos (cambian solo con un despliegue): cacheables en cliente/proxy.
    private static final CacheControl CACHE_DATOS_ESTATICOS =
            CacheControl.maxAge(Duration.ofHours(24)).cachePublic();

    @GetMapping("/provincias")
    public ResponseEntity<List<String>> provincias() {
        return ResponseEntity.ok().cacheControl(CACHE_DATOS_ESTATICOS).body(catalog.provincias());
    }

    @GetMapping("/convenios")
    public ResponseEntity<List<ConvenioResumenDto>> convenios() {
        return ResponseEntity.ok().cacheControl(CACHE_DATOS_ESTATICOS)
                .body(catalog.todos().stream().map(ConvenioResumenDto::desde).toList());
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

    /** Lista curada del desplegable "¿de qué trabajas?" (D20/D15). */
    @GetMapping("/puestos")
    public ResponseEntity<List<Puesto>> puestos() {
        return ResponseEntity.ok().cacheControl(CACHE_DATOS_ESTATICOS).body(perfilOcupacion.puestos());
    }

    /** Qué determina el puesto en este convenio y qué falta por preguntar al usuario. */
    @GetMapping("/convenios/{id}/puestos/{puestoId}")
    public OcupacionResuelta resuelvePuesto(@PathVariable String id, @PathVariable String puestoId) {
        return perfilOcupacion.resuelve(id, puestoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El puesto '" + puestoId + "' no está mapeado en el convenio '" + id + "'"));
    }

    @GetMapping("/convenios/{id}")
    public ResponseEntity<ConvenioDetalleDto> detalle(@PathVariable String id) {
        return catalog.porId(id)
                .map(ConvenioDetalleDto::desde)
                .map(dto -> ResponseEntity.ok().cacheControl(CACHE_DATOS_ESTATICOS).body(dto))
                .orElseThrow(() -> new RecursoNoEncontradoException("Convenio no encontrado: " + id));
    }
}
