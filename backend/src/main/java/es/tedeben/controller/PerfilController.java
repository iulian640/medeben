package es.tedeben.controller;

import es.tedeben.config.RequiereBaseDeDatos;
import es.tedeben.dto.PerfilRequest;
import es.tedeben.dto.PerfilResponse;
import es.tedeben.service.PerfilService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Perfil laboral del usuario autenticado. El id del usuario sale SIEMPRE del
 * token (subject), nunca de la petición — nadie puede tocar el perfil de otro.
 */
@RestController
@RequestMapping("/api/v1/perfil")
@RequiereBaseDeDatos
public class PerfilController {

    private final PerfilService perfiles;

    public PerfilController(PerfilService perfiles) {
        this.perfiles = perfiles;
    }

    @GetMapping
    public PerfilResponse miPerfil(@AuthenticationPrincipal Jwt jwt) {
        return perfiles.busca(usuarioId(jwt))
                .map(PerfilResponse::desde)
                .orElseThrow(() -> new RecursoNoEncontradoException("Todavía no has creado tu perfil"));
    }

    @PutMapping
    public PerfilResponse guardaPerfil(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody PerfilRequest peticion) {
        return PerfilResponse.desde(perfiles.guarda(
                usuarioId(jwt), peticion.provincia(), peticion.subsector(), peticion.puestoId(),
                peticion.dimensiones(), peticion.salarioBaseMensual(), peticion.plusesAnuales()));
    }

    private static UUID usuarioId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException e) {
            // Identidad rara en un token válido: 401 genérico, sin eco del valor.
            throw new TokenInvalidoException();
        }
    }
}
