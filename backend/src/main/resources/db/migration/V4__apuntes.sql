-- Diario de fichajes (D38): apuntes APPEND-ONLY. Nunca se actualiza ni se
-- borra una fila; corregir = apunte nuevo. El estado de un día se deriva del
-- diario. registrado_en es el sello del servidor: el dato probatorio.
CREATE TABLE apuntes (
    id            UUID         PRIMARY KEY,
    usuario_id    UUID         NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    fecha         DATE         NOT NULL,
    tipo          VARCHAR(20)  NOT NULL,
    hora          VARCHAR(5),
    motivo        VARCHAR(200),
    origen        VARCHAR(25)  NOT NULL,
    registrado_en TIMESTAMPTZ  NOT NULL
);

-- El diario de un día y los rangos (semana/mes) para agregados.
CREATE INDEX idx_apuntes_usuario_fecha ON apuntes (usuario_id, fecha, registrado_en);
