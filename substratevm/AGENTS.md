# GraalVM Native Image Community Edition (CE)

This guide applies to the `substratevm` directory.

## Scope

- `substratevm` is an `mx` suite for GraalVM Native Image. It provides the native-image tool that is part of GraalVM.
- Most code lives under `src/`, with one directory per module, for example `src/com.oracle.svm.core`, `src/com.oracle.svm.hosted`, `src/com.oracle.svm.graal`, and `src/com.oracle.svm.test`.
- Java sources are typically under `src/<module>/src/com/oracle/...`.
- Use `docs/` for developer documentation, `ci/` for CI configuration, and `mx.substratevm/` for suite metadata.
- Treat `mxbuild/`, `svmbuild/`, and `sources/` as generated output.

## Build and Development Commands

Run commands from this directory.

- `mx build`: compile the suite; run this before tests or image builds so changed sources are rebuilt.
- `mx checkstyle`: validate style and formatting expectations before sending a change.
- `mx native-unittest [options]`: runs tests for this suite.
- `mx helloworld [options]`: quick smoke test with a basic hello-world application.
- `mx native-image [options] -cp <classpath> <mainClass>`: use this as the `native-image` build-tool when building an image is requested as part of substratevm development workflows.

If a failure looks inconsistent with the checked-out sources, run `mx clean` and then `mx build` before deeper debugging.
Do not run `mx` commands concurrently; parallel runs can produce misleading failures.

## Tests and Validation

- Before treating a test run as validation, check whether the affected test is platform-restricted, for example by annotations, runtime assumptions, or architecture-specific options. A run that skips the affected test because the local machine does not match the required platform does not count as validation.

## Change Hygiene

- Avoid regular expressions in core runtime code when straightforward parsing suffices; regex support increases image code size.
- When porting HotSpot code, preserve its names and structure where practical so reviewers and maintainers can compare the implementations.
- If you touch documented behavior, update `docs/`.
- When adding or changing substitutions, include durable comments that explain when and why
  the substitution intentionally deviates from the original JDK implementation.
- Do not commit generated output from `mxbuild/`, `svmbuild/`, `graal_dumps/`, or `sources/`.
- Be careful when changing `mx native-unittest` / `svmjunit` feature registration. Features added to the shared native test image affect unrelated tests too.
- Do not register production ImageSingletons or other global runtime markers from shared `svmjunit` test features. In particular, avoid making the shared test image look like a different product or launch mode, such as a `libjvm` image.
- If a test needs product-specific global state, prefer a targeted image or test-specific setup over adding that state to the global native-unit-test feature list in `mx.substratevm/mx_substratevm.py`.
- For Crema/libjvm support, prefer expanding `lib_jvm_preserved_packages` in `mx.substratevm/mx_substratevm.py` when runtime-loaded JDK bytecode needs package-level method or field availability. Do not add individual `java.*` analysis roots to hosted features when preserving the owning JDK package expresses the requirement.

<!-- BEGIN GRUND MANAGED BLOCK -->
## Grounding with grund (block v45)

This project uses [`grund`](https://github.com/agent-grounds/grund): every spec, goal, decision, and end-to-end test has a stable ID `<KIND>-<NNN>-<slug>[.<section>]` (`KIND ∈ {FS, FD, AR, PH}`), cited with the marker `§` — e.g. `<§>FS-042-user-login.3.1`, where the ID is a shape illustration rather than a real one in this repo, which is what the `<§>` escape says. Type `$$` in a grund-aware editor and it becomes `§`. Bare ID-shaped tokens are ignored — config version 2 recognizes only `§`-prefixed citations, with no opt-out.

### Grounding from a citation

A `§<ID>` is a pointer to a fact, not a file path. Resolve it with `grund` and climb only as far as needed:

- `grund <ID>` — the lead, cut at the first child section: heading-less for a whole declaration, while `grund <ID>.<section>` opens with that section's heading line. The cheap first read for a bare `§<ID>` citation.
- `grund <ID> --toc` — the lead plus the nested section map. Use to choose which subsection to fetch next.
- `grund <ID> --full` — the entire body. Escalate to this when narrower reads aren't enough.
- `grund <ID> --brief` — heading + first paragraph only.
- `grund refs <ID>` — every site that cites the ID; add `--summary` for one line per file. Run before renaming or moving a declaration.
  After the rename or move, edit the declaration and affected authored citation IDs, then run `grund fmt --write` from the config/project root or workspace root. When cross-reference emission applies, it re-derives generated Markdown link destinations and anchors; the root-scoped run also updates eligible section locks. Run `grund check` and `grund fmt --check`, then commit the lock changes. Locks are keyed by ID, so a file move alone does not rename one. fmt does not replace authored IDs, repair arbitrary URLs, or silently approve a protected section move; fix citations or leave a pointer for that move first. See [Maintaining declarations](https://github.com/agent-grounds/grund/blob/main/docs/user-facing/structure.md#maintaining-declarations).
- `grund list` / `grund list --kind FS,FD` — discover IDs if you get lost
- `grund list --unused` — the declarations nothing cites yet, in one call
- `grund list --size=words --top 10` — find heavy leads and move detail into citable child points before reading them in full
- `grund list --glob '<KIND>-*' --total` — count the coordinates a pattern selects; `grund show --batch --glob '<KIND>-*' --toc --format json` then reads each one's section map in the same scan, from `.result.sections[]` (`path`, `title`, `depth`). `*` matches within one component, `**` any number of whole components, and `{a,b}` either alternative. More recipes: [Querying grund](https://github.com/agent-grounds/grund/blob/main/docs/user-facing/querying.md).

### Project map

- [FS](docs/functional-spec): Native Image behavior
- [FD](docs/functional-spec/decisions): Native Image functional decisions
- [AR](src), [docs/architecture](docs/architecture): Native Image architecture
- [PH](docs/architecture/phases): Native Image build phases

### Project namespaces

A namespace is a project boundary, not a docs folder. The current project is the local namespace: cite its IDs as `§<ID>`.

Create or use a separate namespace when work introduces an independently checked app, package, service, or subproject. Give that project its own `grund.toml`, add it to the workspace root's `[workspace] members`, run `grund init` there, and set a stable `project_name`.

Do not create a namespace for a regular module or component that still belongs to this project. Cite across namespaces as `§alias/<ID>` and run `grund check` from the workspace root.

### Workspace members

Cross-project citations use §alias/<ID>, one alias segment per workspace level.

- [`graalvm-workspace`](../../)

### Declarations and citations

Declarations are heading lines `# <ID>: …` in markdown. In a code doc-comment (Rustdoc, Javadoc, JSDoc, Python docstring, Go `//`, …) drop the `#` — write `/// <ID>: …` directly. Named sections are enabled: use explicit complete paths (`## goals: Goals`, `### goals.performance: Performance`, `### goals.3: Ordered child`) so `§<ID>.goals.performance` resolves; handles are letter-first lowercase names, and `name.number` and `number.name` are both legal; a cited address with a name under a number is recorded in the section lock. Heading depth must match each path component (an error). Purely numbered headings remain citable. In a declaration body, Markdown or doc-comment, a heading may instead write only the component it adds and take the rest from the heading one level up: `### .1: Username` under `## format: Formatting` declares `§<ID>.format.1`, the one coordinate it is cited and reported by. After citing such a heading, run `grund fmt --write` so the section lock records it, and commit the lock. Every non-declaration heading inside a Markdown declaration body must carry a numbered or enabled named section path; file titles, headings that close the body, fenced examples, non-ATX text, and source doc-comments stay exempt, while bold labels remain the non-citable alternative. One doc-comment may declare multiple IDs (e.g. an `AR-` and an `FS-` on the same class) — each gets its own body. An inline source declaration is reachable from the configured kind home via a one-line stub: `# <ID>: [<path>](<path>)`.

### Rules

- **Spec first.** For behavior or design changes, write or update the most-specific spec point before code.
- **Cite as you write.** Place `§<ID>` at the point a claim or behavior is made — on the doc-comment for a whole behavior, inline beside the clause it enforces.
- **Marker = live citation.** A `§`-prefixed token resolves and is checked wherever it appears — including inside Markdown backticks. To mention an ID without citing it, write `<§><ID>`, omit the marker, or use a fenced code block.
- **Inline citation style.** Inline notes: ≤ 1 line preferred, hard cap 3 lines; ≤ 100 columns. A note is one comment block: a blank line splits it, an empty comment line does not. Doc-comments (`///`, `//!`, `/** */`, a docstring, a Go, Ruby, shell, SQL or Lisp comment right above a definition) are documentation, not notes: they are never measured, so cite in-sentence there.
- **Always cite the most-specific point.**

### Citation directions

Specs cite goals, architecture cites specs, code and executable tests cite the specs they realize. In a citation rule array, entries are all required; `|` inside one entry means any one alternative. See https://github.com/agent-grounds/grund/blob/main/docs/user-facing/citation-directions.md for the levels and examples.

### Declaration shapes

`grund check` holds each declaration of these kinds to these fields, as `grund schema` lists them:

- **FS**
  - field terminology must · first
  - field requirements must · last
- **AR**
  - field system-overview must · first
  - field terminology must · after system-overview
  - field north-star warn · after terminology
  - field design must · after north-star
  - field requirements must · last
- **PH**
  - field system-overview must · first
  - field entry must · after system-overview
  - field exit must · after entry
  - field components must · after exit
  - field flow must · after components
  - field data must · last
  - closed must

### Clickable citations

On repository web surfaces, link `§<ID>` to the PR branch in PR bodies, the reviewed commit in reviews, an exact commit for permalinks, and the default branch otherwise; fall back to plain when unsure.
<!-- END GRUND MANAGED BLOCK -->
