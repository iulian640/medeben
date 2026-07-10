-- La purga programada de sesiones (security review de B4: la tabla crecía sin
-- techo — cada login Y cada refresh insertan una fila y nada borraba nunca)
-- barre por caducidad: sin este índice sería un full scan diario.
CREATE INDEX idx_sesiones_caducidad ON sesiones (caduca_en);
