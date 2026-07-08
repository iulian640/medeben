# TeDeben

> Lee esto en español → [README.es.md](README.es.md)

**Your convenio and your hours, spelled out.**

Free app for hospitality workers in Spain. Look up the *convenio colectivo*
(collective bargaining agreement) that applies to you, understand your payslip
and keep your hours up to date, all explained in plain language, with every
figure backed by its article and a link to the official gazette.

It includes 55 hospitality convenios (all 50 provinces plus Ceuta and Melilla),
transcribed from the official gazettes and verified cell by cell. If a figure
isn't published, the app says so; it never makes one up.

## What it does

- Tells you which convenio applies to you based on your province and the type
  of establishment where you work.
- You pick your job from a dropdown ("cocinero/a", "camarero/a"...) and it
  explains your level and the wage your convenio sets, citing the article.
- Overtime calculator based on your convenio, with its sources.
- Simple time tracking: you enter your schedule and the app reminds you to
  clock it with one tap. If you don't answer one day, it assumes your usual
  schedule (marked as automatic, editable later).
- Saves your shift schedules with their dates, so you keep your own tidy
  history.
- Exports a PDF report with your records and the detail behind the
  calculations.

## Our promise

Built by a former cook who knows the sector from the inside.

- You will never pay to use it.
- Your data is yours: export it or delete it whenever you want. We don't sell
  it.
- What's free today won't turn into a paid feature.
- You don't have to take our word for it: the code is public (AGPL-3.0) and
  anyone can check what the app does with your data.

## The data

The convenios live in [`convenios/`](convenios/) as data files, each figure
with its article and the gazette it comes from. An automatic validator checks
on every build that the derived layers match the transcription. See an error,
or is your province missing? It gets fixed with a PR.

## How it's built

The inside matters as much as the outside:

- **Transcription against the image of the official PDF.** Text extracted from
  the gazettes misaligns the columns, so each table is read rendered as an
  image, cell by cell, and then a second independent pass re-verifies it.
  Golden rule: a wrong figure is worse than a missing one. Whatever isn't
  published is marked as pending; it is never filled in.
- **Verifiable provenance.** The numbers the app uses live in a normalized
  layer where each fact carries a pointer to the exact cell of the
  transcription it comes from. A cross-validator runs on every build and
  breaks it if a single amount diverges.
- **Every answer with its source.** Calculations cite the convenio article and
  link to the gazette PDF (or to the BOE, Spain's state gazette, for the
  Estatuto de los Trabajadores, the Workers' Statute).
- **Code quality.** TDD on backend and frontend, code and security review on
  every major piece, and a CI that runs the full suite on every PR, including
  the corpus validator and tests against a real PostgreSQL.
- **Oddities get written down.** Transcribing turns up curious things (a
  nocturnidad (night-work premium) of 1%, a job group that earns more in 3rd
  category than in 2nd...): they are collected with their sources in
  [convenios/curiosidades.md](convenios/curiosidades.md) (in Spanish).

## Milestones

Built in the first week of July 2026:

1. **Complete corpus**: 55 hospitality convenios transcribed and verified,
   covering 100% of the territory.
2. **Normalized layer**: ~8,800 wage facts with provenance, validity periods
   as date ranges and a cross-validator in the build.
3. **Calculation engine**: value of the ordinary hour, overtime and minimum
   wage per job, with article citations and the ultraactividad rule (a
   convenio staying in force after it expires).
4. **Public query API** and **first screen**: from "where do you work, and as
   what?" to your minimum wage with sources, in two questions.
5. **User accounts** with JWT, hardened with a security audit.

The day-by-day detail is in the [project diary](docs/HISTORIAL.md) (in
Spanish).

## Status

🚧 In development (v1). Project decisions: [docs/ADR.md](docs/ADR.md) (in
Spanish). Progress diary: [docs/HISTORIAL.md](docs/HISTORIAL.md) (in Spanish).
To run it locally: [docs/dev-setup.md](docs/dev-setup.md).

## Stack

- **Backend:** Java / Spring Boot
- **Frontend:** Vue 3 (web PWA + Android via Capacitor)
- **Database:** PostgreSQL

## License

[AGPL-3.0](LICENSE): free forever, for everyone.
