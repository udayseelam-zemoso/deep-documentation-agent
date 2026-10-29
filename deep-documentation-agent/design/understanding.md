# Code Understanding: Our Approach

Week 2. Code lives in `com.deepdoc.understand`.

Week 1 told us *which files matter* and *how they import each other*. That is not enough to write documentation. We also need *what is inside those files*: the types they declare, the methods they expose, and a one-sentence answer to "what is this file for?"

## Pipeline

```
ranked files (DEEP + SUMMARY)
        │
        ▼
JavaAstUnderstander     JavaParser AST (Java only)
LexicalUnderstander     comment-stripped regex (everything else)
        │
        ▼
FileOutline             types, methods, annotations, Javadoc, mentioned names
        │
        ▼
KnowledgeGraph          unique type name → file; file A uses file B
        │
        ▼
ResponsibilityInferrer  javadoc → name heuristic → optional LLM rewrite
        │
        ▼
UnderstandingResult     printed by `analyze`, handed to Week 3
```

LISTED files are skipped (`understand.includeListed: false`). There are many of them and they will only appear as names in a package listing.

## Why JavaParser, and why not ANTLR for TypeScript

JavaParser is already a dependency. It gives a real AST: Javadoc, annotations, extends/implements, method names, and the simple type names mentioned in a body. We do **not** turn on the symbol solver. That needs the project's classpath, which an autonomous scan of a stranger's repo does not have.

A full ANTLR TypeScript grammar is huge and still needs a binder to be useful. For JS/TS, Python, Go and the rest, Week 2 reuses `SourceScanner` (same comment-blanking as the graph) and a short list of declaration patterns. That is enough for a symbol list and a responsibility sentence. Java is the language we parse in earnest because that is this agent's home ground.

## Knowledge graph

Each outlined file contributes the types it declares. If a name is declared in exactly one outlined file, any other outlined file that mentions that name gets a *uses* edge to it. Names declared in two files are skipped (ambiguous). This is the same "unique declaration" idea as the generic dependency extractor, applied to the files we will actually document.

## Responsibility sentence

1. The author's first Javadoc / `#` / `///` sentence, if present.
2. Otherwise a heuristic from the type name and annotations:
   `OrdersController` + `@RestController` → "Handles HTTP requests for orders."
3. For DEEP files only, if `llm.enabled` is true, `--no-llm` was not passed, and `OPENAI_API_KEY` (or `llm.apiKeyEnv`) is set, the heuristic is sent to the model as a draft. The model must return one sentence. Any HTTP or parse failure keeps the heuristic.

The key never goes in `application.yml` or on the command line. The client never logs the request body.

## What Week 3 takes from this

- `FileOutline.getResponsibility()` — opening sentence of a file's section
- `typeNames()` / `methodNames()` — the "what" list
- `KnowledgeGraph.uses(file)` — "this file depends on …" with symbols, not only imports
- `getEngine()` — so the docs can say when a claim came from a real AST vs a lexical scan

See [`generation.md`](generation.md).
