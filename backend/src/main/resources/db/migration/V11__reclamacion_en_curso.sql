-- Flag "reclamación en curso" (contrato §Retención): el usuario la declara
-- para suspender la purga automática de sus ubicaciones a los 15 meses. Va en
-- usuarios (no en perfiles) por el mismo criterio que email_verificado (V8):
-- es un flag de CUENTA, no de la situación laboral que describe el perfil, y
-- el perfil es opcional/editable mientras que la cuenta siempre existe.
ALTER TABLE usuarios ADD COLUMN reclamacion_en_curso BOOLEAN NOT NULL DEFAULT false;
