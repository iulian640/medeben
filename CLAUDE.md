# TeDeben — instrucciones del proyecto

## Qué es
App gratuita lado-trabajador para hostelería España: horario + fichaje por notificaciones + cuadrantes como prueba + "te deben X€" según convenio. **La fuente de verdad de todas las decisiones es `docs/ADR.md` — léelo antes de tocar nada.**

## Reglas de oro (del ADR, no negociables)
- El trabajador nunca paga; ni sus datos ni sus herramientas se venden. Sin paywall, sin ads, sin telemetría comercial.
- RGPD desde el diseño: minimización de datos, hosting UE, export/borrado de cuenta, las fotos de cuadrantes contienen datos de terceros — tratarlas con máximo cuidado.
- NUNCA features de reviews públicas de empresas o listas negras (riesgo difamación).
- Disclaimers legales en todo lo que huela a cálculo ("orientativo") o asesoramiento ("no es asesoramiento jurídico").
- Cada registro de jornada lleva su origen: `confirmado` / `auto` / `reconstruido` (honestidad probatoria).

## Stack y estructura
- `backend/` — Java + Spring Boot (API REST), PostgreSQL, JPA, Spring Security + JWT
- `frontend/` — Vue 3 + Vite (PWA; empaquetado Android con Capacitor más adelante)
- `convenios/` — tablas salariales transcritas de boletines oficiales, un fichero JSON por convenio, con vigencia. Las verifica Iulian a mano: no inventar NUNCA datos de convenios.
- `docs/` — ADR y documentación

## Cómo se trabaja
- El código lo escribe Claude con supervisión de Iulian. NO es proyecto didáctico: nada de modo enseñanza aquí.
- TDD (skills springboot-tdd / tdd-workflow), revisores java-reviewer / vue-reviewer / security-reviewer tras cambios relevantes.
- Git: rama por feature, conventional commits en inglés, PR con `gh`, merge, borrar rama local y remota. Todo lo hace Claude sin preguntar. Push directo a main bloqueado.
- Castellano de España con Iulian; código, commits y nombres de código en inglés.

## Notificaciones (decisión técnica clave, D13)
Las notificaciones son LOCALES (programadas en el dispositivo desde el horario), no push del servidor — el backend en free tier se duerme. No diseñar nada que dependa de un servidor despierto a una hora concreta.
