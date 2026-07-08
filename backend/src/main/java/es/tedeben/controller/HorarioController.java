package es.tedeben.controller;

import es.tedeben.config.RequiereBaseDeDatos;
import es.tedeben.domain.horario.DiaCuadrante;
import es.tedeben.domain.horario.Tramo;
import es.tedeben.dto.CuadranteResponse;
import es.tedeben.dto.HorarioEfectivoResponse;
import es.tedeben.dto.HorarioRequest;
import es.tedeben.service.HorarioService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Horario del usuario autenticado (D38): semana tipo + ediciones por semana.
 * El id del usuario sale SIEMPRE del token, nunca de la petición.
 */
@RestController
@RequestMapping("/api/v1/horario")
@RequiereBaseDeDatos
public class HorarioController {

    private final HorarioService horario;

    public HorarioController(HorarioService horario) {
        this.horario = horario;
    }

    @GetMapping
    public CuadranteResponse semanaTipo(@AuthenticationPrincipal Jwt jwt) {
        return horario.semanaTipoActual(usuarioId(jwt))
                .map(CuadranteResponse::desde)
                .orElseThrow(() -> new RecursoNoEncontradoException("Todavía no has creado tu horario"));
    }

    @PutMapping
    public CuadranteResponse guardaSemanaTipo(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody HorarioRequest peticion) {
        return CuadranteResponse.desde(horario.guardaSemanaTipo(usuarioId(jwt), aDominio(peticion)));
    }

    @GetMapping("/semana/{lunes}")
    public HorarioEfectivoResponse horarioDeSemana(@AuthenticationPrincipal Jwt jwt,
                                                   @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lunes) {
        return horario.horarioEfectivo(usuarioId(jwt), lunes)
                .map(HorarioEfectivoResponse::desde)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No hay horario para esa semana: crea antes tu semana tipo"));
    }

    @PutMapping("/semana/{lunes}")
    public CuadranteResponse editaSemana(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lunes,
                                         @Valid @RequestBody HorarioRequest peticion) {
        return CuadranteResponse.desde(horario.guardaSemana(usuarioId(jwt), lunes, aDominio(peticion)));
    }

    private static List<DiaCuadrante> aDominio(HorarioRequest peticion) {
        return peticion.dias().stream()
                .map(dia -> new DiaCuadrante(dia.tramos().stream()
                        .map(t -> new Tramo(t.entrada(), t.salida()))
                        .toList()))
                .toList();
    }

    private static UUID usuarioId(Jwt jwt) {
        return UsuarioAutenticado.id(jwt);
    }
}
