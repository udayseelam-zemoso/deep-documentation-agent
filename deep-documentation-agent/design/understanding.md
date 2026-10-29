# Day 6-7: Java AST, Fact Extraction, Knowledge Graph

Week 2, Days 6-7. Code lives in `com.deepdoc.understand`.

Week 1 told us *which files matter* and *how they import each other*. That is not enough to write documentation. We also need *what is inside those files*: the types they declare, the methods they expose, and a graph of “this type uses that type” that is based on names in the AST, not only on `import` lines.

This slice is **Java only**. Lexical outlines for other languages, one-sentence responsibilities, and the optional LLM rewrite are Day 8-10.

## What we built

Three steps, in order:

```
ranked Java files (DEEP + SUMMARY)
        │
        ▼
JavaAstUnderstander     JavaParser AST → FileOutline
        │
        ▼
FactExtractor           FileOutline → ExtractedFact list
        │
        ▼
KnowledgeGraph          unique DECLARES_TYPE name → file
                        mention facts → uses(file)
        │
        ▼
UnderstandingResult     printed by `analyze`
```

### 1. Java AST parsing (`JavaAstUnderstander`)

JavaParser is already a Maven dependency. For each ranked Java file it produces a `FileOutline`:

| Field | Where it comes from |
|---|---|
| package / imports | `CompilationUnit` |
| types | top-level `TypeDeclaration`s (class, interface, enum, record, annotation) |
| annotations | `@RestController` → `RestController` |
| extends / implements | kept separate so facts can say EXTENDS vs IMPLEMENTS |
| methods | method and constructor names |
| Javadoc | first sentence, `{@link}` / `{@code}` tags stripped |
| references | simple type names and method-call names in the body |

We do **not** turn on the symbol solver. That needs the project's classpath, which an autonomous scan of a stranger's repo does not have. Language level is Java 17 so records in the scanned repo still parse; this agent itself compiles as Java 11.

`java.lang` and common collection names (`String`, `List`, …) are ignored. They never point at a project file.

Garbage input such as `!!!` is marked `parsed = false`. JavaParser's recovery can yield an empty compilation unit for that; we treat “no types and not a successful empty file” as a failed parse.

### 2. Fact extraction (`FactExtractor`)

The outline is still nested (a type with lists hanging off it). Documentation and the knowledge graph need a **flat, named** list of claims. `FactExtractor` walks the outline and emits:

| Kind | Example |
|---|---|
| `DECLARES_TYPE` | `OrderService` is a class (line 6) |
| `DECLARES_METHOD` | `OrderService` declares `create` |
| `EXTENDS` / `IMPLEMENTS` | `OrdersController` extends `BaseController` |
| `ANNOTATED` | `OrdersController` is annotated `RestController` |
| `HAS_JAVADOC` | first sentence of the type comment |
| `IMPORTS` | `com.acme.shop.domain.Order` |
| `REFERENCES` | body mentions `Order` / `OrderValidator` / `valid` |

Each fact stores the file, a subject, an object, and a 1-based line. That is the “fact extraction” step: every later claim about the code can point at one of these rows.

### 3. Knowledge graph structure (`KnowledgeGraph`)

Built from facts, not from the Week 1 import graph.

1. Take every `DECLARES_TYPE` fact. If that simple name appears in **exactly one** outlined file, the name belongs to that file.
2. Take every mention fact (`REFERENCES`, `EXTENDS`, `IMPLEMENTS`, `IMPORTS`). If the mentioned simple name is a unique type in another file, add a *uses* edge.

Names declared in two files are skipped (ambiguous). Method names that happen to match a type name can also create an edge; that is acceptable for this slice and cheaper than a binder.

So after this step we can say: “`OrderService.java` uses `Order.java` and `OrderValidator.java`” because the AST mentioned those unique type names, including the same-package `OrderValidator` that never appeared in an import.

## What `analyze` prints

After the ranking table:

- how many Java files were outlined, failed, or skipped (non-Java)
- fact count, unique-type count, use-edge count
- for the top files: types, methods, a few facts, and `uses:` paths

```
understand:
  includeListed: false   # LISTED files are not parsed
  maxFiles: 40
```

## What this slice does not do

- No lexical parser for JS/TS/Python/… (Day 8-10)
- No one-sentence responsibility, no LLM (Day 8-10)
- No Markdown / Mermaid output (Week 3)
- No symbol solver, no classpath, no call-graph beyond simple-name mentions

## Why this order

Parsing first gives us structured symbols. Facts name those symbols so two later features share one list: the knowledge graph (who uses whom) and, later, the documentation writer (what to say about a file). If we had jumped straight from the AST to “uses” edges, the docs step would have to re-walk the outline with a second set of rules.

## Tuning and tests

- `understand.includeListed` / `understand.maxFiles` in `application.yml`
- `JavaAstUnderstanderTest` — types, methods, Javadoc tags, parents, failed parse
- `FactExtractorTest` — each fact kind on a `@RestController`
- `KnowledgeGraphTest` — four-file shop uses-edges; ambiguous `Helper` creates no edge
- `JavaUnderstandingServiceTest` — four Java files outlined; a lone `.ts` file is skipped
