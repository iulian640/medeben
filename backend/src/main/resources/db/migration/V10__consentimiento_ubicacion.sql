-- Registro de consentimiento art. 7.1 RGPD para "Anotar dónde fichas": hay
-- que poder acreditar que se prestó, cuándo, y sobre QUÉ TEXTO exacto
-- (corrección MEDIO del verificador rgpd-play: version_texto sola no basta si
-- el texto se edita sin bumpear versión). texto_sha256 es el hash SHA-256
-- (hex) calculado en SERVIDOR sobre el texto canónico de esa versión
-- (docs/legal/consentimiento-ubicacion-v1.0.md) — nunca el que mande el
-- cliente, para que el registro sea acreditable de verdad.
CREATE TABLE consentimientos_ubicacion (
    id            UUID        PRIMARY KEY,
    usuario_id    UUID        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    version_texto VARCHAR(10) NOT NULL,   -- p. ej. '1.0'
    texto_sha256  VARCHAR(64) NOT NULL,   -- SHA-256 hex del texto canónico de esa versión
    aceptado_en   TIMESTAMPTZ NOT NULL,   -- sello del servidor
    revocado_en   TIMESTAMPTZ             -- NULL = vigente
);
CREATE INDEX idx_consent_ubic_usuario ON consentimientos_ubicacion (usuario_id, aceptado_en DESC);
