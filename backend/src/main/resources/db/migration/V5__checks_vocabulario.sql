-- Vocabulario cerrado con valor probatorio (libreta sellada, D38): CHECKs de
-- defensa en profundidad sobre las columnas enum de apuntes. La tabla es
-- append-only y es la prueba del trabajador; un valor fuera del vocabulario
-- (bug, SQL manual, cliente futuro) contaminaría el diario sin ruido.
-- Los valores replican los enums de es.tedeben.domain.fichaje (TipoApunte y
-- OrigenApunte). cuadrantes no lleva CHECK: no tiene columnas de vocabulario
-- cerrado (dias es JSONB y el origen del horario se deriva, no se almacena).

ALTER TABLE apuntes
    ADD CONSTRAINT chk_apuntes_tipo
        CHECK (tipo IN ('ENTRADA', 'SALIDA', 'AUSENCIA'));

ALTER TABLE apuntes
    ADD CONSTRAINT chk_apuntes_origen
        CHECK (origen IN ('CONFIRMADO', 'RECONSTRUIDO', 'RECTIFICACION_TARDIA'));
