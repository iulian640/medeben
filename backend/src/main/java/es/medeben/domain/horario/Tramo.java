package es.medeben.domain.horario;

/**
 * Un tramo de trabajo dentro de un día: de entrada a salida, en formato "HH:mm".
 * Si la salida es menor o igual que la entrada, el tramo cruza la medianoche
 * (turno de cierre: 20:00 → 02:00). Se guarda como texto para que el JSONB sea
 * estable y legible; la validación semántica vive en HorarioService.
 */
public record Tramo(String entrada, String salida) {
}
