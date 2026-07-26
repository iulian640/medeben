-- Anotar dónde fichas (síntesis §4, MODIFICADA por el contrato de
-- implementación §Backend). Feature opt-in, apagada de fábrica.
--
-- centros_trabajo: el centro de trabajo declarado por el usuario. APPEND-ONLY,
-- mismo patrón que cuadrantes (V3): cada fila es una DECLARACIÓN, nunca UPDATE.
-- Cerrar un centro = insertar fila con estado='BAJA' (que copia lat/lon/radio
-- de la alta que cierra: la corrección del verificador técnico). lat/lon
-- NULLABLE con CHECK: solo el centro ALTA vigente exige coordenadas — permite
-- el tombstone del art. 17 (declaración errónea o supresión por el titular)
-- sin perder la fila ni su fecha de declaración, que es parte de la prueba.
CREATE TABLE centros_trabajo (
    id            UUID          PRIMARY KEY,
    usuario_id    UUID          NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    alias         VARCHAR(60),                 -- "El bar". Libre, del usuario. Opcional.
    latitud       NUMERIC(8,5),                -- NULLABLE: ver CHECK más abajo (tombstone, D4/art.17)
    longitud      NUMERIC(8,5),
    radio_metros  INTEGER       NOT NULL,      -- lo pone el servidor, no el cliente (150 m fijo en v1)
    estado        VARCHAR(10)   NOT NULL,      -- ALTA | BAJA
    declarado_en  TIMESTAMPTZ   NOT NULL       -- sello del servidor (reloj inyectado)
);
CREATE INDEX idx_centros_usuario ON centros_trabajo (usuario_id, declarado_en DESC);

-- Un centro ALTA (vigente) siempre tiene coordenadas; uno tombstoned (lat/lon
-- a NULL tras purgar>=24h, D4/art.17) pasa a BAJA como parte de la misma
-- operación (desviación mínima documentada: una declaración "vigente" sin
-- coordenadas es una contradicción — ver UbicacionCentroService).
ALTER TABLE centros_trabajo ADD CONSTRAINT ck_centros_alta_tiene_coordenadas
    CHECK (estado <> 'ALTA' OR (latitud IS NOT NULL AND longitud IS NOT NULL));

-- Ubicación anotada en el momento de un fichaje. 1:0..1 con apunte. NO es
-- append-only como apuntes (D38): el usuario puede borrar su histórico de
-- ubicaciones, o suprimir una fila puntual, sin tocar su diario probatorio
-- (art. 7.3 y 17 RGPD) — de ahí que varias columnas sean anulables.
--
-- centro_id con ON DELETE CASCADE (corrección del verificador técnico: sin
-- ella, borrar un usuario podría chocar con el orden de disparo de los FK
-- triggers entre usuarios→centros_trabajo y usuarios→ubicaciones_apunte).
-- La copia congelada centro_latitud/centro_longitud/centro_radio (D5) es la
-- que hace irrelevante para el veredicto que el centro_id original se borre.
CREATE TABLE ubicaciones_apunte (
    apunte_id         UUID          PRIMARY KEY REFERENCES apuntes (id) ON DELETE CASCADE,
    usuario_id        UUID          NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    fecha             DATE          NOT NULL,  -- día del turno, desnormalizado: evita join en el informe
    latitud           NUMERIC(8,5),            -- NULLABLE: la supresión granular (art. 17) las anula
    longitud          NUMERIC(8,5),
    precision_metros  INTEGER       NOT NULL,  -- accuracy reportada por el sistema; se conserva siempre
    centro_id         UUID          NOT NULL REFERENCES centros_trabajo (id) ON DELETE CASCADE,
    centro_latitud    NUMERIC(8,5),            -- copia congelada (D5); NULLABLE: tombstone del centro (art.17)
    centro_longitud   NUMERIC(8,5),
    centro_radio      INTEGER       NOT NULL,  -- se conserva siempre (necesario para leer el veredicto)
    distancia_metros  INTEGER,                 -- NULLABLE: la supresión granular la anula
    veredicto         VARCHAR(20)   NOT NULL,
    simulada          BOOLEAN,                 -- NULL = desconocido (v1 no lo sabe)
    registrada_en     TIMESTAMPTZ   NOT NULL   -- sello del servidor
);
CREATE INDEX idx_ubicaciones_usuario_fecha ON ubicaciones_apunte (usuario_id, fecha);
-- La purga programada por antigüedad (15 meses) y la propagación del
-- tombstone de un centro filtran por estas columnas.
CREATE INDEX idx_ubicaciones_registrada_en ON ubicaciones_apunte (registrada_en);
CREATE INDEX idx_ubicaciones_centro_id ON ubicaciones_apunte (centro_id);

-- Vocabulario cerrado, mismo patrón que V5__checks_vocabulario.sql. SUPRIMIDA
-- añadida por el contrato: la supresión granular no borra la fila, marca el
-- veredicto y anula las coordenadas (art. 17 sin perder la consistencia del
-- conjunto — D12/D38: un diario con supresiones visibles no es un diario maquillado).
ALTER TABLE centros_trabajo ADD CONSTRAINT ck_centros_estado
    CHECK (estado IN ('ALTA','BAJA'));
ALTER TABLE ubicaciones_apunte ADD CONSTRAINT ck_ubicaciones_veredicto
    CHECK (veredicto IN ('DENTRO','FUERA','NO_CONCLUYENTE','SUPRIMIDA'));
