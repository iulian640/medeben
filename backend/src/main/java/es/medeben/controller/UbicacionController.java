package es.medeben.controller;

import es.medeben.config.RequiereBaseDeDatos;
import es.medeben.domain.fichaje.CentroTrabajo;
import es.medeben.dto.CentroTrabajoRequest;
import es.medeben.dto.CentroTrabajoResponse;
import es.medeben.dto.ConsentimientoUbicacionRequest;
import es.medeben.dto.UbicacionRequest;
import es.medeben.dto.UbicacionResponse;
import es.medeben.service.CentroTrabajoService;
import es.medeben.service.ConsentimientoUbicacionService;
import es.medeben.service.ReclamacionEnCursoService;
import es.medeben.service.UbicacionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * "Anotar dónde fichas" (contrato §Backend punto 11): consentimiento, centro
 * de trabajo y ubicación anotada. Vive en {@code es.medeben.controller}
 * (obligado: {@link UsuarioAutenticado} es package-private). El
 * {@code usuarioId} sale SIEMPRE del JWT — norma de {@code FichajeController}
 * — nunca del body, en ningún endpoint de esta clase.
 */
@RestController
@RequiereBaseDeDatos
public class UbicacionController {

    private final UbicacionService ubicaciones;
    private final CentroTrabajoService centros;
    private final ConsentimientoUbicacionService consentimientos;
    private final ReclamacionEnCursoService reclamaciones;

    public UbicacionController(UbicacionService ubicaciones, CentroTrabajoService centros,
                               ConsentimientoUbicacionService consentimientos,
                               ReclamacionEnCursoService reclamaciones) {
        this.ubicaciones = ubicaciones;
        this.centros = centros;
        this.consentimientos = consentimientos;
        this.reclamaciones = reclamaciones;
    }

    @PostMapping("/api/v1/ubicacion/consentimiento")
    @ResponseStatus(HttpStatus.CREATED)
    public void aceptaConsentimiento(@AuthenticationPrincipal Jwt jwt,
                                     @Valid @RequestBody ConsentimientoUbicacionRequest peticion) {
        consentimientos.acepta(UsuarioAutenticado.id(jwt), peticion.versionTexto());
    }

    @DeleteMapping("/api/v1/ubicacion/consentimiento")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revocaConsentimiento(@AuthenticationPrincipal Jwt jwt) {
        consentimientos.revoca(UsuarioAutenticado.id(jwt));
    }

    @PutMapping("/api/v1/centro-trabajo")
    public CentroTrabajoResponse declaraCentro(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody CentroTrabajoRequest peticion) {
        CentroTrabajo centro = centros.declara(UsuarioAutenticado.id(jwt), peticion.alias(),
                peticion.latitud(), peticion.longitud());
        return CentroTrabajoResponse.desde(centro);
    }

    @GetMapping("/api/v1/centro-trabajo")
    public CentroTrabajoResponse centroVigente(@AuthenticationPrincipal Jwt jwt) {
        return centros.vigente(UsuarioAutenticado.id(jwt))
                .map(CentroTrabajoResponse::desde)
                .orElseThrow(() -> new RecursoNoEncontradoException("No tienes un centro de trabajo declarado"));
    }

    @DeleteMapping("/api/v1/centro-trabajo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cierraCentro(@AuthenticationPrincipal Jwt jwt) {
        centros.cierra(UsuarioAutenticado.id(jwt));
    }

    /**
     * Supresión (art. 17) de una declaración concreta. {@code ?purgar=true} es
     * obligatorio y explícito: sin él, 400 — no hay una versión "silenciosa"
     * de un borrado potencialmente irreversible (síntesis §9, checklist RGPD).
     */
    @DeleteMapping("/api/v1/centro-trabajo/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void purgaCentro(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                            @RequestParam(defaultValue = "false") boolean purgar) {
        if (!purgar) {
            throw new IllegalArgumentException("Añade ?purgar=true para confirmar el borrado");
        }
        centros.purga(UsuarioAutenticado.id(jwt), id);
    }

    @PostMapping("/api/v1/fichajes/{apunteId}/ubicacion")
    @ResponseStatus(HttpStatus.CREATED)
    public UbicacionResponse adjunta(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID apunteId,
                                     @Valid @RequestBody UbicacionRequest peticion) {
        return UbicacionResponse.desde(ubicaciones.adjunta(UsuarioAutenticado.id(jwt), apunteId,
                peticion.latitud(), peticion.longitud(), peticion.precisionMetros()));
    }

    /** Supresión granular (art. 17): anula la fila, no la borra (contrato §Backend punto 7). */
    @DeleteMapping("/api/v1/fichajes/{apunteId}/ubicacion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void suprime(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID apunteId) {
        ubicaciones.suprime(UsuarioAutenticado.id(jwt), apunteId);
    }

    /**
     * Borra TODO el histórico de ubicaciones y purga TODAS las declaraciones
     * de centro de trabajo del usuario (art. 17): sin lo segundo, la promesa
     * del consentimiento v1.0 ("borrar todo tu histórico... en un toque")
     * dejaba coordenadas en {@code centros_trabajo}, solo alcanzables
     * purgando cada declaración por su id a mano.
     */
    @DeleteMapping("/api/v1/ubicaciones")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borraTodas(@AuthenticationPrincipal Jwt jwt) {
        UUID usuarioId = UsuarioAutenticado.id(jwt);
        ubicaciones.borraTodas(usuarioId);
        centros.purgaTodas(usuarioId);
    }

    /**
     * Declara "reclamación en curso" (contrato §Retención): suspende la purga
     * automática de ubicaciones a los 15 meses. Es la vía de escape que el
     * texto de consentimiento v1.0 promete literalmente.
     */
    @PutMapping("/api/v1/usuario/reclamacion-en-curso")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void declaraReclamacionEnCurso(@AuthenticationPrincipal Jwt jwt) {
        reclamaciones.declara(UsuarioAutenticado.id(jwt));
    }

    /** Retira "reclamación en curso": la purga vuelve a aplicar con normalidad. */
    @DeleteMapping("/api/v1/usuario/reclamacion-en-curso")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retiraReclamacionEnCurso(@AuthenticationPrincipal Jwt jwt) {
        reclamaciones.retira(UsuarioAutenticado.id(jwt));
    }
}
