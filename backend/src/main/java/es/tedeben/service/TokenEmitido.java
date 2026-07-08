package es.tedeben.service;

import java.time.Instant;

/** Token JWT emitido tras un login correcto. */
public record TokenEmitido(String token, Instant expiraEn) {
}
