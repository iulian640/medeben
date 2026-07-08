-- Cuadrantes de horario (D38): versiones APPEND-ONLY. Cambiar el horario =
-- añadir una fila nueva; nunca se actualiza ni se borra ninguna. Las versiones
-- antiguas son el historial con fecha que prueba los cambios de última hora (D6).
--
-- semana_inicio NULL  = semana tipo (se repite sola)
-- semana_inicio lunes = edición de esa semana concreta
CREATE TABLE cuadrantes (
    id            UUID        PRIMARY KEY,
    usuario_id    UUID        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    semana_inicio DATE,
    dias          JSONB       NOT NULL,
    creado_en     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Las dos consultas del servicio: última semana tipo (as-of) y última edición de una semana.
CREATE INDEX idx_cuadrantes_usuario ON cuadrantes (usuario_id, semana_inicio, creado_en DESC);
