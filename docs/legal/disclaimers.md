# Descargos de responsabilidad (disclaimers) — textos para pantalla

> Versión 1.0 — 12 de julio de 2026. Textos concretos y **dónde** colocarlos.
> Son parte del cumplimiento (responsabilidad por cálculo erróneo y
> "información ≠ asesoramiento jurídico", ADR D11.2/D11.3). Los marcadores
> [nombre]/[año]/[boletín] del punto 1 NO son huecos por rellenar aquí: los
> resuelve la app en tiempo real con los datos del convenio del usuario.

## 1. En la cifra "te deben X€" y en el informe PDF

> "Cálculo **orientativo** según las tablas del convenio [nombre] ([año],
> [boletín]). Puede contener errores o no reflejar tu situación concreta.
> **Verifica con un profesional o tu sindicato antes de reclamar.**"

## 2. En guías y en el asistente de denuncias (cuando existan)

> "Esta información es **educativa y no constituye asesoramiento jurídico**.
> MeDeben no es un despacho de abogados. Para tu caso concreto, consulta con un
> abogado laboralista, tu sindicato o la Inspección de Trabajo."

## 3. En el campo "motivo" de una ausencia

**Versión actual (mientras el motivo NO esté cifrado en la base de datos —
C4 pendiente):**

> "El motivo es **opcional**. Solo se usa para tu propia reclamación y nunca se
> comparte. Si prefieres, déjalo en blanco."

**Versión cuando se implemente el cifrado a nivel de columna (C4):** sustituir
por "…se guarda **cifrado** y solo se usa para tu propia reclamación…". **No usar
la palabra "cifrado" hasta que C4 esté hecho** — sería inexacto y un problema de
cumplimiento en sí mismo.

## Nota de implementación

Estos textos deben aparecer en la UI (no solo en un documento). Cuando se
implementen, `avoid-ai-writing` para el copy final y `vue-reviewer` para que el
del motivo jamás se pinte con `v-html` (dato del usuario).
