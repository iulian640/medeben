package es.tedeben.controller;

/** 404: el recurso pedido no existe (convenio, tabla aplicable...). */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
