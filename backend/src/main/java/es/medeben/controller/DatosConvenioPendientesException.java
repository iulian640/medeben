package es.medeben.controller;

/**
 * 422: el convenio existe pero no tiene publicados los datos necesarios para
 * el cálculo pedido (jornada o pagas pendientes). Regla de oro: no se inventa.
 */
public class DatosConvenioPendientesException extends RuntimeException {

    public DatosConvenioPendientesException(String mensaje) {
        super(mensaje);
    }
}
