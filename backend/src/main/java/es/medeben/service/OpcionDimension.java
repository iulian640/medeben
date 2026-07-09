package es.medeben.service;

import java.util.List;

/** Una dimensión que el usuario aún debe responder (tipo de establecimiento...) y sus valores posibles. */
public record OpcionDimension(String dimension, List<String> valores) {

    public OpcionDimension {
        valores = List.copyOf(valores);
    }
}
