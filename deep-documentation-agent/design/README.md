# Dependency Graph and Importance Ranking: Our Approach

This document explains how the agent decides **which files to document, and how deeply** (the Week 1, Day 3-4 work). It covers the reasoning behind each step, the formulas, and a worked example that matches the unit tests.

- Code: `com.deepdoc.graph` (dependency graph) and `com.deepdoc.ranker` (ranking)
- Settings: the `graph`, `git` and `ranking` sections of `src/main/resources/application.yml`

## 1. The problem

A repository with thousands of files cannot be documented file by file. The output would be too long to read, and too expensive once an LLM writes it. Documentation effort has to go where a new reader needs it most.

- **Central files** are the ones much of the system depends on, directly or through other files. Understanding them unlocks the rest.
- **Active files** are the ones developers change often. That is where people spend their time, and where documentation goes stale fastest.
- **Boundary files** are the entry points (`main`, controllers, routes) and the files other modules use. They show how a module is meant to be used.

So the ranking combines **structure** (the dependency graph) with **activity** (Git history), and gives a bonus to **boundary** files.

## 2. Pipeline

```
RepositoryAnalyzer      files + language of each file
        │
        ▼
DependencyGraphBuilder  1. pick the files to rank (code, not tests)
        │               2. extract file -> file dependencies (per language)
        │               3. group files into packages, roll edges up
        ▼
DependencyGraph         file graph + package graph
        │
        ├── GitInterface ──► ActivityScores       recency-weighted commit counts
        ├── EntryPointDetector                     main / controllers / routes ...
        ▼
ImportanceRanker        package score, file score, priority, doc level
        │
        ▼
RankingResult           DEEP / SUMMARY / LISTED per file
```

## 3. Building the dependency graph

### 3.1 Which files are ranked

- Only **programming-language** files, as classified in `languages.yml`. Markup, data, config and prose files are counted but not ranked.
- **Tests are excluded** by default (`graph.includeTests: false`). A test imports everything it tests, so tests would look like the most central files. They also say nothing about how the application itself is wired. Test files are recognized by directory (`test/`, `__tests__/`, `spec/`) and by naming conventions (`*Test.java`, `*.spec.ts`, `test_*.py`, `*_test.go`).

### 3.2 File edges

An edge `A -> B` means *file A depends on file B*. Each edge has:

- **reference count**: how many times A refers to B (e.g. how often the class name appears in the body). It is used as the edge weight.
- **evidence**: the line in A where the dependency first appears, so that generated documentation can cite its claims.

Extraction is **lexical**: regular expressions run over source text whose comments (and, where useful, string literals) have been blanked out by `SourceScanner`. Blanking keeps the character offsets, so line numbers stay correct.

- Lexical extraction is fast enough for very large repositories.
- It needs no compiler or build setup, and tolerates code that does not compile.
- It finds the dependencies a human reader would see.

Each language family has its own extractor. For Java, an edge comes from:

- explicit imports (`import com.acme.order.OrderService;`)
- wildcard imports, resolved to the classes actually used
- fully-qualified names used without an import (`com.acme.util.Ids.next()`)
- classes from the **same package**, which Java lets you use without an import

The other families (JS/TS, Python, Go, C#, C/C++, Rust, Ruby, PHP, Swift, shell) follow their own rules and are listed in the main `README.md`. Any other language goes through a generic fallback (see section 7).

### 3.3 Packages

Readers think in modules before files, so files are grouped into **packages**. A package is:

- the declared namespace, for languages that have one (Java/Kotlin package, C# or PHP namespace); or
- the directory, for everything else (shown with a trailing `/`, e.g. `src/api/`).

The package graph must be readable: 2 nodes say nothing, 200 are unreadable. Rather than asking the user for a depth, `PackageGrouper` picks one:

1. **Strip the common prefix.** If every package starts with `com.acme.shop`, that prefix carries no information, so `com.acme.shop.order` becomes `order`.
2. **Choose the depth.** Starting at depth 1, increase it until there are at least `minPackages` (5) groups. Step back one level if that overshoots `maxPackages` (25).
3. **Split oversized groups.** A group holding more than `splitFraction` (30%) of all files, and at least `splitMinFiles` (20), is split one level deeper. This repeats until no group qualifies, or until splitting would not help or would exceed `maxPackages`.

### 3.4 Rolling edges up

File edges between two packages become one **package edge** `P -> Q`. It records:

- **import count**: the number of crossing file edges, used as the weight;
- **reference count**: the sum of the file edges' reference counts;
- up to three evidence lines.

Edges inside a package do not become package edges. They are counted as the package's *internal dependencies*, a sign of cohesion.

## 4. Git activity

`GitInterface` reads history with JGit, so no `git` binary is needed. It reads commits from the last `historyMonths` (24) months, at most `maxCommits` (5000) of them. Two kinds of commit are dropped:

- **Merge commits**, because they repeat changes already counted on the merged branch.
- **Bulk commits** that touch more than `bulkCommitFileLimit` (50) files, such as reformatting, renames, vendoring or the initial import. They say nothing about where work happens.

Each commit's weight decays with age (`ActivityScores`):

```
weight = 0.5 ^ (age in days / halfLifeDays)        halfLifeDays = 180
```

A commit made today counts 1, one from 6 months ago counts 0.5, and one from a year ago counts 0.25. Old bursts of work (a finished rewrite) therefore fade instead of dominating.

- **File activity** is the sum of the weights of the commits that touched the file.
- **Package activity** counts each commit once, however many of the package's files it touched.

The analyzed directory may be one module inside a larger repository. Commit paths are relative to the Git work tree, so graph files are mapped to work-tree paths before matching.

## 5. Ranking

### 5.1 Weighted, personalized PageRank

Structure is measured with PageRank, which asks: *if a reader started somewhere and kept following dependencies, where would they spend their time?* A file is important if important files depend on it. `WeightedPageRank` solves:

```
r(v) = (1 - d) · p(v) + d · Σ over u→v of  r(u) · w(u,v) / out(u)
```

| Symbol | Meaning |
|---|---|
| `d = 0.85` | damping: the probability of following an edge rather than jumping |
| `w(u,v)` | edge weight |
| `out(u)` | total outgoing weight of `u` |
| `p(v)` | prior: where the reader "jumps" to |

Rank held by nodes without outgoing edges is redistributed by the prior, so all ranks sum to 1. The prior is a personalization vector rather than a uniform one, which is why the implementation is our own instead of JGraphT's.

We make three changes to plain PageRank:

1. **`sqrt` edge weights.** `w = sqrt(reference count)` for files and `sqrt(import count)` for packages. A dependency used 100 times matters more than one used once, but not 100 times more.
2. **Back edges.** Every edge `A -> B` also gets a reverse edge `B -> A` with `backEdgeFactor` (0.25) times the weight. Plain PageRank only rewards being depended on, so utility classes beat the services and controllers that tie the system together. The weaker reverse edge lets "uses many important things" count too.
3. **Entry-point prior.** Entry points get a prior of `1 + N` (N = number of nodes) instead of 1. Readers start at `main()`, controllers and routes, so everything reachable from them gains rank. Packages containing an entry point get the same boost.

### 5.2 Package score

```
package score = 0.6 · pct(package PageRank) + 0.4 · pct(ln(1 + package activity))
```

`pct` is the percentile among all packages: 0 for the lowest, 1 for the highest, with tied values sharing their average position. PageRank and commit counts live on completely different scales, and percentiles make them comparable and robust to one outlier. `ln(1 + x)` is applied because commit counts are heavy-tailed.

### 5.3 File score inside its package (local score)

```
local score = 0.5 · PageRank / max PageRank in package
            + 0.3 · ln(1 + activity) / max ln(1 + activity) in package
            + 0.2 · boundary        (1 if entry point or used by another package, else 0)
```

Normalizing against the best file *of the same package* means every package has a file with a structure value of 1. The local score answers "which files matter inside this module?"

### 5.4 Priority

```
priority = package score × local score
```

The two are multiplied rather than added, so a file needs **both** an important package and an important place within it to reach the top. The central file of a marginal package ranks below the key files of the package everything depends on.

**Without Git history**, the activity terms are dropped and the remaining weights are re-normalized:

- package score = `pct(PageRank)`
- local score = `(0.5 · structure + 0.2 · boundary) / 0.7`

### 5.5 Documentation levels

| Level | Who gets it | Planned output |
|---|---|---|
| **DEEP** | the top `deepFraction` (20%) of files by priority, at least one | its own section: purpose, design, key flows, with source references |
| **SUMMARY** | the best file of every package without a DEEP file, plus every entry point | a paragraph in its package's section |
| **LISTED** | everything else | name and one-line role in the package's file list |

This guarantees that every package is represented, and that every way into the system is explained.

### 5.6 Package roles

Each package also gets a role from Robert C. Martin's instability metric:

```
I = Ce / (Ca + Ce)
```

Here `Ca` is the number of packages depending on this one, and `Ce` is the number of packages it depends on.

| Role | Condition | Typical packages | How to describe it |
|---|---|---|---|
| FOUNDATION | I ≤ 0.3 | domain, utilities | by what it provides |
| INTERMEDIATE | 0.3 < I < 0.7 | services | by both |
| ORCHESTRATOR | I ≥ 0.7 | controllers, wiring | by what it coordinates |
| ISOLATED | no package edges | | |

The role will tell the documentation step how to introduce the package.

## 6. Worked example

This is the repository used by `ImportanceRankerTest` (built by `ExampleRepos.writeFourFile`). All numbers below are asserted by that test, and were cross-checked with an independent Python implementation.

```
web/OrderController   (@RestController, the entry point)
    └─► order/OrderService ──► domain/Order    (Order mentioned 4 times)
             └─► order/OrderValidator ──► domain/Order
```

Git history: OrderController 10 commits, OrderService 20, OrderValidator 6, Order 3 (all recent).

**Package graph.** The prefix `com.acme.shop` is stripped. `web -> order` comes from 1 file edge; `order -> domain` from 2 file edges.

| Package | PageRank | pct(PR) | Activity | pct(act) | Score | Role |
|---|---|---|---|---|---|---|
| order | 0.473 | 1.0 | 26 | 1.0 | 0.6·1 + 0.4·1 = **1.00** | INTERMEDIATE |
| domain | 0.367 | 0.5 | 3 | 0.0 | 0.6·0.5 + 0.4·0 = **0.30** | FOUNDATION |
| web | 0.160 | 0.0 | 10 | 0.5 | 0.6·0 + 0.4·0.5 = **0.20** | ORCHESTRATOR |

**Files.** These are the file-graph PageRanks:

| File | PageRank |
|---|---|
| OrderService | 0.343 |
| Order | 0.337 |
| OrderValidator | 0.204 |
| OrderController | 0.116 |

OrderService and OrderValidator are in the same package, so:

- **OrderService** is the best in its package on both signals and is used from `web` (a boundary file). Its local score is 0.5 + 0.3 + 0.2 = **1.00**, and its priority is 1.00 × 1.00 = **1.00**.
- **OrderValidator**:
  - structure = 0.204 / 0.343 = 0.595
  - activity = ln 7 / ln 21 = 0.639
  - it is not a boundary file
  - local = 0.5·0.595 + 0.3·0.639 = **0.489**, and priority = 1.00 × 0.489 = **0.489**
- **Order** is alone in `domain`, so local = 1.00 and priority = 0.30 × 1.00 = **0.30**.
- **OrderController** is alone in `web`, so local = 1.00 and priority = 0.20 × 1.00 = **0.20**.

With `deepFraction = 0.5`:

- OrderService and OrderValidator are **DEEP**.
- Order is **SUMMARY**, as the best file of `domain`, which has no DEEP file.
- OrderController is **SUMMARY**, both as the entry point and as the best file of `web`.

Note that Order has almost the same PageRank as OrderService, yet ranks much lower. Its package is rarely changed and depends on nothing, while `order` is where the work happens. That is the intended effect of combining structure with activity.

## 7. Beyond the dedicated extractors

- **Generic fallback.** Programming languages without a dedicated extractor (Clojure, Elixir, Dart, Lua, Haskell, ...) are parsed by `GenericDependencyExtractor`. It uses three conventions shared by most languages:
  - quoted file paths;
  - module names that map onto directories (`Data.Cart` to `Data/Cart.hs`);
  - type names declared in exactly one file.
- **Co-change edges.** For those fallback files, `CoChangeEdges` also links files that keep being committed together. This requires at least 3 shared commits, making up at least half of the less-changed file's commits. These edges only fill gaps where the code shows no edge, and they are marked `isCoChange()`.

## 8. Design decisions and trade-offs

| Decision | Why | Cost |
|---|---|---|
| Lexical extraction instead of full parsers | fast, works for every language, no build needed | can miss unusual import forms, macros and generated code |
| Tests excluded | they would dominate centrality | test-only utilities are not ranked |
| `sqrt` weights | heavy use matters, but sub-linearly | none significant |
| Back edges at 0.25 | orchestrators are not starved | a slight blur of dependency direction |
| Percentiles at package level | combines signals on different scales | loses the size of the gaps between packages |
| Normalization per package at file level | every package gets its own "most important file" | file scores are not comparable across packages until multiplied by the package score |
| Multiplying package × local | needs both to be high | a brilliant file in a weak package ranks low |
| 180-day half-life | current work counts most | long-stable core files rely on structure alone |
| Deterministic ordering everywhere | same repo, same ranking | none |

## 9. Tuning

All weights live in `application.yml`; rebuild after editing.

- **Different packages wanted:** change `graph.minPackages` / `graph.maxPackages`, or `graph.splitFraction` / `graph.splitMinFiles`.
- **History matters less (or more):** change `ranking.activityWeight` and `ranking.localActivityWeight`, or `git.halfLifeDays`.
- **More or fewer deep write-ups:** change `ranking.deepFraction`.
- **Orchestrators rank too low:** raise `ranking.backEdgeFactor`.
- **No history wanted:** set `git.enabled: false`.

Run `java -jar target/deep-documentation-agent-1.0.0.jar analyze <repo> --top 30`. It prints:

- the package graph;
- the package table (score, percentiles, Ca/Ce, role);
- the top files, with their level and flags.

Use that output to check the effect of a change.
