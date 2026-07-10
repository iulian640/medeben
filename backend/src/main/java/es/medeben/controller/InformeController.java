package es.medeben.controller;

import es.medeben.config.RequiereBaseDeDatos;
import es.medeben.service.InformeMensualService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

/**
 * El informe mensual en PDF del usuario autenticado (la evidencia del README).
 * Mismas reglas que el resumen: el id sale SIEMPRE del token, datos personales
 * sin caché compartida, y los errores hablan RFC 7807 (400 mes inválido/futuro,
 * 422 con la guía de qué falta).
 */
@RestController
@RequestMapping("/api/v1/informes")
@RequiereBaseDeDatos
public class InformeController {

    private final InformeMensualService informes;

    public InformeController(InformeMensualService informes) {
        this.informes = informes;
    }

    @GetMapping(value = "/mes/{anyoMes}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> informeMensual(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String anyoMes) {
        YearMonth mes = MesPath.parsea(anyoMes);
        byte[] pdf = informes.genera(UsuarioAutenticado.id(jwt), mes);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PDF)
                .headers(h -> h.setContentDisposition(ContentDisposition.attachment()
                        // El nombre sale del YearMonth ya parseado, nunca del input crudo.
                        .filename("medeben-informe-" + mes + ".pdf")
                        .build()))
                .body(pdf);
    }
}
