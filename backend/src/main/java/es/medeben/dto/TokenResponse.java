package es.medeben.dto;

import java.time.Instant;

public record TokenResponse(String token, Instant expiraEn) {
}
