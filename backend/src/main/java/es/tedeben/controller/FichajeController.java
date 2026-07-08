package es.tedeben.controller;

import es.tedeben.config.RequiereBaseDeDatos;
import es.tedeben.dto.ApunteRequest;
import es.tedeben.dto.ApunteResponse;
import es.tedeben.dto.EstadoDiaResponse;
import es.tedeben.service.FichajeService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * El diario de fichajes del usuario autenticado (D38). El id del usuario sale
 * SIEMPRE del token, nunca de la petición.
 */
@RestController
@RequestMapping("/api/v1/fichajes")
@RequiereBaseDeDatos
public class FichajeController {

    private final FichajeService fichajes;

    public FichajeController(FichajeService fichajes) {
        this.fichajes = fichajes;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApunteResponse apunta(@AuthenticationPrincipal Jwt jwt,
                                 @Valid @RequestBody ApunteRequest peticion) {
        return ApunteResponse.desde(fichajes.apunta(UsuarioAutenticado.id(jwt),
                peticion.fecha(), peticion.tipo(), peticion.hora(), peticion.motivo(),
                peticion.rectificacionTardiaConfirmada()));
    }

    @GetMapping("/dia/{fecha}")
    public EstadoDiaResponse estadoDia(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return EstadoDiaResponse.desde(fichajes.estadoDia(UsuarioAutenticado.id(jwt), fecha));
    }
}
