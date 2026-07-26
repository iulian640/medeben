package es.medeben.service;

/** 409: ese apunte ya tiene una ubicación; es inmutable, no se pisa. */
public class UbicacionYaRegistradaException extends RuntimeException {

    public UbicacionYaRegistradaException() {
        super("Ese fichaje ya tiene una ubicación anotada");
    }
}
