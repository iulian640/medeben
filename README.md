# MeDeben

> Lee esto en español → [README.es.md](README.es.md)

**Your convenio and your hours, spelled out.**

Free app for hospitality workers in Spain. Look up the *convenio colectivo*
(collective bargaining agreement) that applies to you, understand your payslip
and keep your hours up to date, all explained in plain language, with every
figure backed by its article and a link to the official gazette.

It includes 55 hospitality convenios (all 50 provinces plus Ceuta and Melilla),
transcribed from the official gazettes. If a figure isn't published, the app
says so; it never makes one up.

> 🚧 **Work in progress (v1).** I'm still building this. See [Status](#status)
> for what's working today and what's still on the way.

## What it does

- Tells you which convenio applies to you based on your province and the type
  of establishment where you work.
- You pick your job from a dropdown ("cocinero/a", "camarero/a"...) and it
  explains your level and the wage your convenio sets, citing the article.
- Tells you what they owe you this month: it compares your schedule against
  what you clocked and works out the minimum you're due for the extra hours.
- Overtime calculator based on your convenio, with its sources.
- Simple time tracking: you enter your schedule and the app reminds you to
  clock in with one tap. A day you didn't clock stays visible as a gap: the
  app never fills in hours you didn't record. After 14 days the journal is
  sealed with a server timestamp; that's what makes your records usable as
  evidence.
- Saves your shift schedules with their dates, so you keep your own tidy
  history (and last-minute shift changes leave a trace).
- Exports PDF reports (month and year) with your records, their timestamps and
  the detail behind every calculation, ready to take to a union or a lawyer.

## Our promise

- You will never pay to use it.
- Your data is yours: export it or delete it whenever you want. We don't sell
  it.
- What's free today won't turn into a paid feature.
- You don't have to take our word for it: the code is public (AGPL-3.0) and
  anyone can check what the app does with your data.

## Who built this, honestly

I'm Iulian, a former cook now learning to code. MeDeben exists because I lived
the problem: I never really knew what my convenio said, or whether my hours
added up at the end of the month.

Here's the honest split, because the whole point of this app is being straight
about your rights and it would be odd to hide how it's made.

The idea is mine, and so is every product decision: what the app is for, who
it's for, what we will and won't do, and the rule that governs everything (a
wrong figure is worse than a missing one). The convenios we research together,
because that part needs someone who has worked a bar and knows what a "grupo de
actividad" means on a payslip. I check what goes in, and I test it with people
who still work in kitchens.

The code is mostly not mine. The Java/Spring backend, the Vue frontend, the
transcription pipeline, the tests, the CI: almost all of it is written by
[Claude Code](https://claude.com/claude-code), an AI coding agent, working as my
pair under my direction. It does most of the typing and many of the
implementation calls. I set the direction, keep the standards, and say no when
something is wrong.

So a project this size took shape in weeks instead of months. The judgment is
mine; the hands are mostly the AI's. That's the deal, and I'd rather you know it.

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
- **Code quality.** TDD on backend and frontend, code and security review
  (human and AI-assisted) on every major piece, and a CI that runs the full
  suite on every PR: the corpus validator, tests against a real PostgreSQL,
  and end-to-end user journeys (Playwright) against the actual running stack.
- **Oddities get written down.** Transcribing turns up curious things (a
  nocturnidad (night-work premium) of 1%, a job group that earns more in 3rd
  category than in 2nd...): they are collected with their sources in
  [convenios/curiosidades.md](convenios/curiosidades.md) (in Spanish).

## Status

🚧 In development (v1). Working today: convenio lookup, wage-by-job with
sources, "what they owe you this month", time tracking with a sealed journal,
a shift-schedule editor, monthly and yearly PDF reports, clock-in reminders
(Android app), and accounts with real deletion (GDPR) and sessions you can
actually log out of. On the way: the public release (Play Store) and hosting.
Full-coverage corpus of 55 convenios (~8,800 wage facts with provenance) is in
place.

Project decisions: [docs/ADR.md](docs/ADR.md) (in Spanish). Progress diary:
[docs/HISTORIAL.md](docs/HISTORIAL.md) (in Spanish). To run it locally:
[docs/dev-setup.md](docs/dev-setup.md).

## Stack

- **Backend:** Java / Spring Boot
- **Frontend:** Vue 3 (web PWA + Android via Capacitor)
- **Database:** PostgreSQL

## License

[AGPL-3.0](LICENSE): free forever, for everyone.
