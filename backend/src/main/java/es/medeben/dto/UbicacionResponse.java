package es.medeben.dto;

import es.medeben.domain.fichaje.UbicacionApunte;
import es.medeben.domain.fichaje.VeredictoUbicacion;

import java.time.OffsetDateTime;

/**
 * La ubicación anotada, tal y como la ve el usuario. Deliberadamente SIN
 * coordenadas (D3/síntesis §5): la app no necesita repintar el punto, y así
 * el dato bruto no viaja en cada respuesta ni acaba en caches, logs de proxy
 * o capturas de pantalla.
 */
public record UbicacionResponse(
        VeredictoUbicacion veredicto,
        Integer distanciaMetros,
        Integer precisionMetros,
        OffsetDateTime registradaEn
) {

    public static UbicacionResponse desde(UbicacionApunte u) {
        return new UbicacionResponse(u.getVeredicto(), u.getDistanciaMetros(), u.getPrecisionMetros(),
                u.getRegistradaEn());
    }
}
