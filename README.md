# TeDeben

**Las horas que trabajas, cobradas.**

App gratuita del lado del trabajador de hostelería en España: registra tu horario, te avisa para fichar, guarda tus cuadrantes como prueba y calcula lo que te deben según tu convenio.

## De qué lado estamos

Esta app está de tu parte. La hizo un excocinero que sabe lo que es doblar turnos que nadie apunta.

- **Nunca pagarás por usarla.**
- **Nunca venderemos ni tus datos ni tus herramientas** — lo que hoy es gratis no se convertirá en de pago jamás.
- **Tus datos son tuyos**: expórtalos o bórralos cuando quieras.
- **No nos creas: compruébalo.** El código es público (licencia AGPL-3.0) — cualquiera puede verificar qué hacemos con tus datos.

## Qué hace

- 📋 Subes tu horario (semanal, mensual o fijo) y **la app te persigue a ti**: notificación al entrar y al salir, fichas con un toque sin abrir la app.
- 😴 Si estás demasiado reventado para contestar, la app asume tu horario teórico (marcado como "auto", corregible). **El peor caso nunca es "no hay datos".**
- 📸 Guarda las fotos de tus cuadrantes con fecha — prueba de lo que la empresa publicó y de cuándo te lo cambiaron.
- 💶 Calcula las horas extras y te dice **"te deben X€ este mes"** según el convenio de hostelería de tu zona.
- 🗂️ Modo "ponerme al día": reconstruye meses pasados de golpe con las fotos que ya tienes en el móvil.
- 📄 Exporta un informe PDF como evidencia para el SMAC o un abogado laboralista.

## Estado

🚧 En desarrollo (v1). Las decisiones del proyecto están en [docs/ADR.md](docs/ADR.md).

## Stack

- **Backend:** Java / Spring Boot
- **Frontend:** Vue 3 (web PWA + Android vía Capacitor)
- **BD:** PostgreSQL
- **Convenios:** transcritos de los boletines oficiales como ficheros de datos en [`convenios/`](convenios/) — corrígelos o amplíalos con un PR

## Licencia

[AGPL-3.0](LICENSE) — libre para siempre. Nadie (tampoco nosotros) puede convertir esto en un producto cerrado de pago.
