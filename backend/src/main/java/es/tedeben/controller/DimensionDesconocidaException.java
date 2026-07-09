package es.tedeben.controller;

/**
 * 422: el perfil trae una dimensión (clave o valor) que NO existe en las tablas
 * del convenio resuelto. Regla de oro (D24/D34): no se guarda una dimensión que
 * luego no resolvería ninguna tabla — un dato que no cuadra con el convenio es
 * peor que ninguno.
 */
public class DimensionDesconocidaException extends RuntimeException {

    public DimensionDesconocidaException(String mensaje) {
        super(mensaje);
    }
}
