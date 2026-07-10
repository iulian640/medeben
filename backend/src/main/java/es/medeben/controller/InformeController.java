package es.medeben.controller;

import es.medeben.config.RequiereBaseDeDatos;
import es.medeben.service.InformeAnualService;
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

import java.time.Year;
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
    private final InformeAnualService anuales;

    public InformeController(InformeMensualService informes, InformeAnualService anuales) {
        this.informes = informes;
        this.anuales = anuales;
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

    /** Longitud exacta de yyyy: lo que no mida eso ni se intenta parsear (ni se ecoa). */
    private static final int LONGITUD_YYYY = 4;

    @GetMapping(value = "/anio/{anyo}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> historicoAnual(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String anyo) {
        Year anio = parseaAnio(anyo);
        byte[] pdf = anuales.genera(UsuarioAutenticado.id(jwt), anio);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PDF)
                .headers(h -> h.setContentDisposition(ContentDisposition.attachment()
                        // El nombre sale del Year ya parseado, nunca del input crudo.
                        .filename("medeben-historico-" + anio + ".pdf")
                        .build()))
                .body(pdf);
    }

    private static Year parseaAnio(String anyo) {
        // El input crudo no se ecoa en el error (mismo criterio que MesPath).
        // Dígitos ASCII estrictos: Character.isDigit tragaría numerales Unicode.
        if (anyo == null || anyo.length() != LONGITUD_YYYY
                || !anyo.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw new IllegalArgumentException("Año inválido: usa el formato yyyy (por ejemplo 2026)");
        }
        // Las cotas (2019 y el año en curso) las valida el servicio, que tiene el reloj.
        return Year.of(Integer.parseInt(anyo));
    }
}
