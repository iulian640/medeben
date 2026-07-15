-- Verificación de email (cierre real de la enumeración de cuentas, auditoría
-- R7): el registro pasa a responder SIEMPRE igual y la señal real viaja solo
-- por correo. La cuenta funciona sin verificar (decisión de producto: a un
-- trabajador no se le cierra el acceso a sus horas por un email), pero el
-- estado queda registrado y el aviso en la app pide confirmar.

-- DEFAULT true al añadirla = backfill: todos los usuarios EXISTENTES quedan
-- verificados (se registraron antes de la feature; invalidarlos ahora sería
-- castigar sin motivo). Acto seguido el default pasa a false: los NUEVOS
-- nacen sin verificar.
ALTER TABLE usuarios ADD COLUMN email_verificado BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE usuarios ALTER COLUMN email_verificado SET DEFAULT false;
ALTER TABLE usuarios ADD COLUMN verificado_en TIMESTAMPTZ;

-- Tokens de verificación: espejo del patrón de sesiones (V6/V7). token_hash
-- = SHA-256 (hex) del token opaco que viaja en el correo — si la BD se
-- filtra, los tokens no se reconstruyen. usada_en implementa el un-solo-uso:
-- la transición va por UPDATE atómico del repositorio, no por lectura+setter.
CREATE TABLE verificaciones_email (
    id         UUID         PRIMARY KEY,
    usuario_id UUID         NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    token_hash VARCHAR(64)  NOT NULL UNIQUE,
    emitida_en TIMESTAMPTZ  NOT NULL,
    caduca_en  TIMESTAMPTZ  NOT NULL,
    usada_en   TIMESTAMPTZ
);

-- Reemisión ("reenviar correo") localiza los tokens vivos del usuario.
CREATE INDEX idx_verificaciones_email_usuario ON verificaciones_email (usuario_id);
-- La purga programada barre por caducidad, como en sesiones (V7).
CREATE INDEX idx_verificaciones_email_caducidad ON verificaciones_email (caduca_en);
