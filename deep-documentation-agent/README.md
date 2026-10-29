# Deep Documentation Agent

An autonomous Java-based tool that generates high-quality, explanatory documentation for software repositories without requiring any user input or prior knowledge of the codebase.

## Overview

The Deep Documentation Agent automatically analyzes code structure, infers architecture patterns, extracts design decisions from Git history, and produces comprehensive Markdown documentation that enables developers unfamiliar with the codebase to understand what the system does and why it is built the way it is.

### Key Features

- **Fully Autonomous**: Requires zero user input - just point it at a repository
- **Explanatory Documentation**: Captures the "why" behind design decisions, not just the "what"
- **Verifiable Claims**: Every generated claim links to specific source code locations
- **Multi-Language Support**: dedicated dependency extraction for Java/Kotlin/Scala, JS/TS, Python, Go, C#, C/C++, Rust, Ruby, PHP, Swift and shell, plus a generic fallback for every other language
- **Architecture Detection**: Automatically detects Spring Boot, React, Express.js, and more
- **Git History Analysis**: Extracts insights from commit history and code churn
- **Fast Processing**: Processes 100K-line repositories in 30-60 minutes

## Requirements

- JDK 11 or higher (`java -version`)
- Maven 3.6 or higher (`mvn -version`)
- Git, only if the repo you analyze has history

## Run on your machine

All commands below start from the `deep-documentation-agent` folder (the one that contains `pom.xml`).

### 1. Check Java and Maven

```bash
java -version
mvn -version
```

Install JDK 11+ and Maven if either command fails.

### 2. Build

```bash
mvn clean package
```

This compiles the code, runs tests, and writes:

`target/deep-documentation-agent-1.0.0.jar`

Skip tests if you only want a fast rebuild:

```bash
mvn -q -DskipTests package
```

### 3. Run the agent

Analyze this project:

```bash
java -jar target/deep-documentation-agent-1.0.0.jar analyze .
```

Analyze any other local repo (use your real path):

```bash
java -jar target/deep-documentation-agent-1.0.0.jar analyze /path/to/your/repo
```

On Windows:

```bat
java -jar target\deep-documentation-agent-1.0.0.jar analyze C:\path\to\your\repo
```

Without building a JAR, after dependencies are downloaded once:

```bash
mvn -q exec:java -Dexec.args="analyze /path/to/your/repo"
```

### 4. Other commands

```bash
java -jar target/deep-documentation-agent-1.0.0.jar --help
java -jar target/deep-documentation-agent-1.0.0.jar version
java -jar target/deep-documentation-agent-1.0.0.jar analyze /path/to/repo --verbose
```

| Option | Meaning today |
|---|---|
| `analyze <path>` | Scan the repo and print a summary |
| `-v`, `--verbose` | Print a stack trace if something fails |
| `-o`, `--output` | Reserved for later (docs output folder) |
| `--no-llm` | Reserved for later (skip LLM) |

You do **not** need an OpenAI/Anthropic key for this slice. LLM calls are not wired yet.

### 5. Change settings

Edit `src/main/resources/application.yml` (ignore folders, extensions, Git on/off). Then rebuild (`mvn -q -DskipTests package`) and run again.

Logging format stays in `src/main/resources/logback.xml` because Logback only reads XML.

### 6. Run tests

```bash
mvn test
```

### IntelliJ / VS Code / Eclipse

1. Open the `deep-documentation-agent` folder as a Maven project and wait for it to import.
2. Run `com.deepdoc.cli.DeepDocCLI`.
3. Program arguments: `analyze` plus a repo path, for example `analyze .`

## Project Status

### Week 1 - Foundation (Days 1-5) ✓ COMPLETED

**Day 1-2: Project Setup and Core Components**
- ✓ Maven project structure
- ✓ CLI scaffolding with Picocli
- ✓ File discovery system
- ✓ Git interface with JGit
- ✓ Basic domain models (FileInfo, RepositoryManifest, RepositoryContext)
- ✓ Language detection
- ✓ Architecture pattern detection (Spring Boot, React, Express)

**Deliverable**: CLI that analyzes repositories and outputs file lists with detected architecture ✓

**Day 3-4: Importance Ranking** ✓
- ✓ File dependency graph from Java imports (incl. same-package usage) and JS/TS `import`/`require` (`com.deepdoc.graph`)
- ✓ Package roll-up at an auto-chosen depth, with weighted package edges and import-line evidence
- ✓ Recency-weighted Git activity (half-life decay, merge and bulk commits ignored)
- ✓ Personalized weighted PageRank with reverse edges and entry-point priors (`com.deepdoc.ranker`)
- ✓ Package score, in-package file score, global priority, and DEEP / SUMMARY / LISTED levels
- ✓ Tunable from the `graph`, `git` and `ranking` sections of `application.yml`

`analyze` now also prints the package graph, the ranked packages (with Ca/Ce and role) and the top files; use `--top N` to change how many.

The approach, formulas and a worked example are documented in [`design/README.md`](design/README.md).

**Day 5: Architecture Detection** ✓
- Patterns live in `src/main/resources/architectures.yml` (Spring Boot, Quarkus, React, Next.js, Django, Rails, …). Confidence is the fraction of each pattern's signal weight that matched.
- More specific patterns hide weaker siblings (Spring Boot hides Spring; Next.js hides React).
- Build modules are detected for Maven, Gradle, npm workspaces, Go, Cargo, Python and .NET. A module is deployable when it has a `main`, a Dockerfile or a start script.
- Style is counted from those modules plus Docker Compose / Dockerfiles: monolith, modular monolith, microservices, or library.
- The CLI prints the label with confidence and style, the module list, and a few evidence lines.
- Approach: [`design/architecture.md`](design/architecture.md).

**Language coverage** ✓
- Languages are defined in `src/main/resources/languages.yml` (about 65 entries: extensions, special file names such as `Dockerfile`, and shebang interpreters). Add a language there; no code change is needed for it to be detected.
- Dedicated dependency extractors (`com.deepdoc.graph.*DependencyExtractor`):

| Family | Languages | What becomes an edge |
|---|---|---|
| JVM | Java, Kotlin, Scala, Groovy | imports, wildcard imports, fully-qualified names without an import, same-package types |
| JavaScript | JS, TS, Vue, Svelte | relative imports, `tsconfig`/`jsconfig` `paths` and `baseUrl` (with `extends`), workspace packages, `graph.jsAliases`, `@/` and `~/` |
| Python | Python | `import` / `from … import`, relative imports, submodule imports |
| Go | Go | import paths resolved via `go.mod`, linked to the file declaring the used identifier |
| C# | C# | `using` (incl. static, alias, global), namespaces, qualified type names |
| C family | C, C++, Objective-C | `#include` / `#import` of project headers |
| Rust | Rust | `mod x;`, `use` trees, `crate::` / `super::` / `self::` paths |
| Ruby | Ruby | `require_relative`, `require`, Rails-style autoloaded constants |
| PHP | PHP | `use` (incl. grouped), namespaces, `require`/`include` |
| Swift | Swift | type names declared in exactly one or two files |
| Shell | sh, bash, zsh | `source` / `.` |

- Every other programming language (Clojure, Elixir, Dart, Lua, Haskell, Perl, Erlang, …) goes through `GenericDependencyExtractor`, which links quoted file paths, module names that map onto paths (`Data.Cart` to `Data/Cart.hs`, `Shop.CartItem` to `shop/cart_item.ex`) and type names declared in exactly one file.
- For those fallback languages, files repeatedly committed together are also linked (`CoChangeEdges`, tunable with `graph.coChange*`).
- Entry points and test-file conventions are recognised for every family above.

**Week 2: Code Understanding** ✓
- Java files are parsed with JavaParser (AST: types, methods, Javadoc, annotations, extends/implements). Other languages get a lexical outline (same comment-blanking as the graph).
- A knowledge graph links outlined files that mention each other's unique type names.
- Each DEEP/SUMMARY file gets a one-sentence responsibility: Javadoc if present, otherwise a name/annotation heuristic, optionally rewritten by an LLM when `OPENAI_API_KEY` is set. `--no-llm` skips the model.
- `analyze` prints the outlines after the ranking. Approach: [`design/understanding.md`](design/understanding.md).

### Upcoming Work

**Week 3: Documentation Generation**
- Markdown sections from DEEP / SUMMARY / LISTED
- Mermaid diagrams from the package graph
- Source references and the understanding outlines

## Architecture

### Component Overview

```
┌─────────────────┐
│  CLI Interface  │ (Picocli)
└────────┬────────┘
         │
         ▼
┌─────────────────────────┐
│  Repository Analyzer    │
│  - File Discovery       │
│  - Architecture Detection│
└────────┬────────────────┘
         │
         ▼
┌─────────────────────────┐
│  Git Interface          │ (JGit)
│  - History Analysis     │
│  - Churn Calculation    │
└─────────────────────────┘
```

### Core Domain Models

- **FileInfo**: Represents a discovered file with metadata (path, language, LOC, importance score)
- **RepositoryManifest**: Complete repository inventory with files and statistics
- **RepositoryContext**: Detected architecture, frameworks, and technologies

## Configuration

Settings live in `src/main/resources/application.yml` (loaded at startup). Example:

```yaml
analysis:
  ignoreDirectories:
    - node_modules
    - target
  ignoreExtensions:
    - .class
    - .jar

git:
  enabled: true
```

Rebuild after you edit that file. Logging stays in `logback.xml`.

## Development

### Running Tests

```bash
mvn test
```

### Building

```bash
mvn clean package
```

### Code Style

The project follows standard Java conventions with:
- Package structure by component
- Comprehensive logging with SLF4J
- Clear separation of concerns

## Technology Stack

- **Java 11+**: Core implementation language
- **Picocli**: CLI framework
- **JGit**: Git operations
- **JavaParser**: Java AST parsing
- **ANTLR4**: JavaScript/TypeScript parsing
- **JGraphT**: Graph algorithms (for dependency analysis)
- **SLF4J + Logback**: Logging
- **JUnit 5 + AssertJ**: Testing
- **SnakeYAML**: Loads `src/main/resources/application.yml`

## Design Decisions

### Why Java?
- Excellent static analysis tooling (JavaParser, JGit)
- Strong performance for large repositories
- Target user base (enterprise Java teams) prefers Java tools
- Mature ecosystem for code analysis

### Why Autonomous?
- User may have never seen the repository before
- Pattern detection achieves similar accuracy to user input
- Removes setup friction

### Why Focus on "Why" Not Just "What"?
- API references and file catalogs already exist
- Design rationale is what's missing and most valuable
- Captured through Git history + LLM inference

## Roadmap

- **Week 1** (Days 1-5): Foundation ✓
- **Week 2** (Days 6-10): Code Understanding ✓
- **Week 3** (Days 11-15): Documentation Generation
- **Week 4** (Days 16-20): Verification and Delivery

## Contributing

This is an implementation of a design specification. See `design-document.pdf` for the complete technical design.

## License

TBD

## Contact

TBD
