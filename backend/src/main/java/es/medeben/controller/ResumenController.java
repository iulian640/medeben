package es.medeben.controller;

import es.medeben.config.RequiereBaseDeDatos;
import es.medeben.dto.ResumenMensualResponse;
import es.medeben.service.ResumenMensualService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.UUID;

/**
 * "Te deben X€ este mes" del usuario autenticado (D12). El id sale SIEMPRE del
 * token, nunca de la petición. Respuesta con datos personales: sin Cache-Control
 * público (no-store), a diferencia de los cálculos anónimos de /api/v1/calculo.
 */
@RestController
@RequestMapping("/api/v1/resumen")
@RequiereBaseDeDatos
public class ResumenController {

    private final ResumenMensualService resumenes;

    public ResumenController(ResumenMensualService resumenes) {
        this.resumenes = resumenes;
    }

    @GetMapping("/mes/{anyoMes}")
    public ResponseEntity<ResumenMensualResponse> resumenMensual(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String anyoMes) {
        YearMonth mes = MesPath.parsea(anyoMes);
        ResumenMensualResponse cuerpo = ResumenMensualResponse.desde(
                resumenes.delMes(UsuarioAutenticado.id(jwt), mes));
        // Datos personales: nunca en una caché compartida.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(cuerpo);
    }

}
