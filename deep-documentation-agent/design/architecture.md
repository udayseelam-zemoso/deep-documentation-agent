# Architecture Detection: Our Approach

Week 1, Day 5. Code lives in `com.deepdoc.analyzer` (`ArchitectureDetector`, `ModuleDetector`, `architectures.yml`).

The earlier detector answered one question: "Spring Boot, React, or Express?" and guessed from a single string. That is not enough for documentation. A reader also needs to know **how sure we are**, **which modules exist**, and **whether this is one app or several services**.

## What we report

| Field | Meaning |
|---|---|
| Architecture | A short label: `Spring Boot Application`, `Full-stack (Express.js + React)`, `Spring Boot microservices` |
| Confidence | 0–100%. How much of the pattern's evidence was actually found |
| Style | monolith, modular monolith, microservices, library, or unknown |
| Frameworks | Every YAML pattern of kind `framework` that cleared the threshold |
| Technologies | Build tools and datastores (`Maven`, `PostgreSQL`, …) |
| Modules | Build units (Maven/Gradle/npm/Go/…) and whether each one looks deployable |
| Evidence | Up to a few source lines that justified the frameworks |

## Patterns (`architectures.yml`)

Each pattern is a name, a kind (`framework`, `architecture`, `build`, `datastore`), an optional layer (`backend`, `frontend`, `mobile`), and a list of **signals** with weights.

```
confidence = (weight of signals that hit) / (weight of all signals)
```

A Spring Boot repo that only has `@SpringBootApplication` scores 0.55. The same repo with `spring-boot` in the POM and an `application.yml` scores 1.0. The documentation step can treat those differently.

Signal types:

- `file-exists` / `directory` — a path relative to the repository root
- `filename` — any discovered file with that name (`Dockerfile`, `manage.py`)
- `file-contains` — a discovered file whose name or extension is listed contains a string or regex
- `file-count` — at least `min` matching files

A pattern is reported only when confidence ≥ `analysis.architectureMinConfidence` (default 0.3).

Source files are matched after comments and string literals are blanked, and test files are skipped. A Javadoc example of `@SpringBootApplication`, or that annotation only in a unit test, does not make the repository look like Spring Boot.

**Specific beats generic.** `suppresses` drops a weaker sibling when both match: Spring Boot hides Spring, Next.js hides React, NestJS hides Express. Otherwise every Boot app would also be reported as Spring.

Adding a framework is a YAML change. The detector code does not grow.

## Modules

`ModuleDetector` walks the files the analyzer already discovered:

- Maven: every `pom.xml`, plus `<module>` children
- Gradle: `settings.gradle` `include`, plus each `build.gradle`
- npm: each `package.json`, expanding `workspaces: ["packages/*"]`
- Go / Cargo / Python / .NET: `go.mod`, `Cargo.toml`, `pyproject.toml`, `*.csproj`

A module is **deployable** when it has a `main` / `@SpringBootApplication`, a Dockerfile, or a start script. Files that sit in a child module do not make the parent deployable: a root aggregator POM is not an application just because `api/` has a `main`.

## Style

Style is **not** a YAML pattern. It is counted from modules and ops files, so "this is Spring Boot" and "this is three services" stay independent.

| Style | Rule |
|---|---|
| Microservices | ≥ 2 deployable modules **and** (Compose has ≥ 2 app services, or ≥ 2 Dockerfiles, or a `services/` / `apps/` layout) |
| Modular monolith | Several modules, at most one deployable — or several deployable modules under one parent build with no ops evidence |
| Monolith | Exactly one deployable module |
| Library | Modules exist, none look runnable |
| Unknown | Almost no files |

Compose services named `postgres`, `redis`, `nginx`, … do not count as applications.

## How the label is chosen

1. Take the strongest backend framework and the strongest frontend framework.
2. Both present → `Full-stack (Spring Boot + React)`.
3. Style is microservices → keep the framework name, change the ending (`Spring Boot microservices`).
4. Serverless files (`serverless.yml`, SAM `template.yaml`) with confidence ≥ 0.5 → `Serverless (Express.js)`.
5. Mobile wins when it outranks the backend (Android, Flutter).
6. Otherwise the primary framework's `architecture` field, or `Library` / `Unknown`.

## Why not parse every POM as XML?

Lexical checks are the same trade-off as the dependency extractors: fast, no build, works on incomplete trees. A false positive (the word `spring-boot` in a comment) is rarer than missing a framework because Maven was not on the PATH.

## Tuning

- `analysis.architectureMinConfidence` in `application.yml`
- Add or re-weight a pattern in `architectures.yml`, then rebuild
