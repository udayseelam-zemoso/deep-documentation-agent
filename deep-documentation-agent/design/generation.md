# Documentation Generation: Our Approach

Week 3. Code lives in `com.deepdoc.docs`.

Week 1 decided *which files matter*. Week 2 said *what is inside them*. This step writes that down as Markdown a stranger can read, with a picture of the packages and a link back to every source line we used as evidence.

## Pipeline

```
RankingResult + UnderstandingResult + DependencyGraph
        │
        ▼
OverviewPage            docs/README.md
ArchitecturePage        docs/architecture.md
PackagePage             docs/packages/<name>.md
        │
        ├── MermaidRenderer     package flowchart
        └── SourceCite          file:line links relative to the Markdown file
        │
        ▼
DocumentationResult     printed by `analyze`
```

The output folder is `<repo>/docs` by default (`output.defaultDirectory`), or whatever `--output` says. Generated files overwrite same names; anything else in the folder is left alone. This project's own `docs/` is gitignored so a self-run does not show up as a dirty tree.

## What each file is for

| File | Reader question it answers |
|---|---|
| `README.md` | What is this system? Where do I start? |
| `architecture.md` | How is it packaged, which frameworks fired, how do packages depend? |
| `packages/<name>.md` | What does this module do, and which files should I read first? |

## Documentation levels, again

The ranking already assigned a level. Generation does not re-rank; it only chooses a *shape*:

| Level | Shape on the package page |
|---|---|
| **DEEP** | Own `###` section: responsibility, types with line links, methods, uses (knowledge-graph names, falling back to import edges), incoming dependents, parse engine |
| **SUMMARY** | Short `###` paragraph: responsibility, source link, types, uses |
| **LISTED** | One row in "Other files": path, language, lines, one-line role |

LISTED files are often not outlined (`understand.includeListed: false`). The role is then guessed from the file stem (`OrderValidator` → "Order validator.") so the table is still readable.

## Package introductions

The package role from Ca/Ce decides the opening sentence:

- **FOUNDATION** — described by what it provides
- **ORCHESTRATOR** — described by what it coordinates
- **INTERMEDIATE** — both
- **ISOLATED** — no cross-package edges

That is the same table as `design/README.md` §5.6. The page then lists outgoing package edges with the import-line evidence `PackageEdge` already stored, and incoming edges as names only.

## Mermaid

Only the **package** graph is drawn. A file-level graph is unreadable past a few dozen nodes; those stay as lists. Node ids are sanitised (`order.service` → `order_service`) because a dot is Mermaid syntax. Labels still show the real name and the role.

`output.generateDiagrams: false` (or the same field on a loaded config in tests) skips every ````mermaid` fence.

## Source references

A claim that came from the code is a Markdown link:

```
[OrderService.java:3](../../src/main/java/com/acme/shop/order/OrderService.java#L3)
```

The path is relative to the Markdown file being written, so the same generator works whether the output folder is `docs/`, `/tmp/out`, or something the user passed to `--output`. The `#L` fragment is what GitHub and GitLab use to open that line.

Snippets (the import that created an edge, the architecture signal that fired) are quoted inline so the page is readable without leaving it.

## What we do not do yet

- No LLM-written prose beyond the one-sentence responsibility Week 2 already produced. Week 3 is structural: it must stay correct when there is no key.
- No HTML site, no search index. Markdown in a folder is the delivery format.
- No claim verification against the source (that is Week 4).

## Tuning

- `output.defaultDirectory` / `--output` — where files land
- `output.generateDiagrams` — Mermaid on or off
- `ranking.deepFraction` — how many files get a DEEP section
- `understand.includeListed` / `understand.maxFiles` — which files have a real outline vs a guessed one-liner
