-- Perfil laboral del usuario: dónde y de qué trabaja, y su salario real.
-- Un perfil por usuario (D12). Las dimensiones (nivel, clase de empresa...)
-- van como JSONB porque cada convenio tiene las suyas (D24).
CREATE TABLE perfiles (
    usuario_id            UUID         PRIMARY KEY REFERENCES usuarios (id) ON DELETE CASCADE,
    provincia             VARCHAR(60)  NOT NULL,
    subsector             VARCHAR(30)  NOT NULL,
    convenio_id           VARCHAR(80)  NOT NULL,
    puesto_id             VARCHAR(40),
    dimensiones           JSONB,
    salario_base_mensual  NUMERIC(9, 2),
    pluses_anuales        NUMERIC(9, 2),
    version               BIGINT       NOT NULL DEFAULT 0,
    actualizado_en        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
