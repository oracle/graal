# GR-61707 IDE Report Rebase Validation Design

## Purpose

This document captures the agreed design before implementation work starts. The immediate goal is to make the GR-61707 branch safe to rebase onto current `origin/master` by proving that conflict resolution preserves IDE report behavior. The longer-term goal is to build report tooling that remains useful if IDE report data moves from JSON sidecar files into debug information or a custom binary section.

The embedded/splittable storage workstream is captured separately in [IDE_REPORT_EMBEDDED_STORAGE_DESIGN.md](IDE_REPORT_EMBEDDED_STORAGE_DESIGN.md).

The fine-grained execution sequence is captured in [IDE_REPORT_EXECUTION_PLAN.md](IDE_REPORT_EXECUTION_PLAN.md).

## Current Branch Context

- Branch: `vaebi/GR-61707/native-image-ide-reports`
- Current local head when Phase 0 started: `361c298a41a71c6b03ecc900d63613267d3492a0`
- Feature implementation commit: `ba44d40f429ecf14728e885c7d91529717d78ac6`
- Refreshed `origin/master` during Phase 0: `25fdc04abe6d5589a7cf85debc65e07fd3d24dff`
- Merge-base before rebase: `9742cdee0ce378fef8da95148e086c952048ca73`
- Non-destructive merge analysis reported conflicts in:
  - `substratevm/src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/results/TypeFlowSimplifier.java`
  - `substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGenerator.java`
  - `substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/snippets/ReflectionPlugins.java`

## Core Design Principle

Do not validate the rebase with byte-for-byte report equality.

The current JSON report is not guaranteed to be stable at raw serialization level. Ordering, timestamps, path spelling, and some incidental details may vary even when the feature is behaviorally equivalent. Raw reports and hashes are still useful for provenance, but they must not be the main pass/fail signal.

The pass/fail signal should be semantic:

- The report source can be loaded.
- The loaded report has valid shape.
- Required report families are present.
- Key facts are preserved.
- Differences are classified as missing, added, changed, or tolerated instability.

## Report Tooling Architecture

Build an IDE report inspection tool, not a JSON-only comparator.

### Proposed Location

Use the Native Image suite tooling area:

```text
substratevm/mx.substratevm/ide_report/
```

Register a thin `mx` command from `mx_substratevm.py`, for example:

```text
mx ide-report ...
```

Rationale:

- Existing Python developer utilities live under `mx.<suite>`.
- IDE reports are Native Image build artifacts even though some report producers live in `compiler`.
- Tooling should not be mixed into Java source packages such as `jdk.graal.compiler.ide` or `com.oracle.graal.pointsto.ide`.

### Neutral Model

The core logic should operate on a source-independent in-memory model:

- `ReportBundle`
- `ReportRecord`
- `ReportKind`
- `SourceLocation`
- `InlineContext`
- `MethodReference`
- `ReportProvenance`

Avoid JSON-specific names in the model. For example, prefer `ReportBundle` over `JsonReport`, and `SourceLocation` over hard-coded `filename`/`line` assumptions.

Each model object should allow extension fields so future source formats can preserve format-specific data without forcing the query and comparison logic to understand it.

### Source Adapters

Use adapters to load concrete report formats into the neutral model:

```text
ide_report/
  sources/
    json.py
    auto.py
    binary_section.py      # future
    debug_info.py          # future
```

Initial implementation only needs the JSON adapter. The CLI and model should already allow future input kinds such as:

```text
json:/path/to/native_image_ide_report.json
canonical:/path/to/canonical-native-image-ide-report.json
image:/path/to/executable
split:/path/to/executable.ide-report
auto:/path/to/app-or-report
```

Meaning:

- `json:` loads the current raw legacy JSON export.
- `canonical:` loads canonical `ReportBundle` JSON directly.
- `image:` extracts the embedded `.svm_ide_report` payload from an executable or shared library.
- `split:` loads the externalized embed payload side file.
- `auto:` detects based on path and contents, trying canonical JSON, raw JSON, image, and then nearby split side files.

Do not add `debug:` or `debuginfo:` in the first implementation. Those source schemes should wait until IDE reports are actually stored in debug-info-associated data. The important design constraint is that `query`, `compare`, `summarize`, and `assert` must depend on `ReportBundle`, not directly on JSON or object-file details.

### Initial Commands

Start with these commands:

```text
mx ide-report summarize <source>
mx ide-report query <source> [filters]
mx ide-report compare <before-source> <after-source>
mx ide-report assert <source> --expect <expectations.json>
```

Useful query filters:

- `--kind`
- `--class`
- `--class-prefix`
- `--source-file`
- `--method`
- `--field`
- `--message-regex`
- `--line`
- `--line-range`
- `--has-inline-context`

Useful output formats:

- table for human inspection
- JSON for tooling
- JSON Lines for scripting

### Comparator Semantics

The comparator should provide both a detailed diff and grouped summaries.

Record matching should use a two-level identity model.

Base identity for normal report records:

```text
report kind
+ normalized source file
+ semantic subject
+ message category
```

The semantic subject is whichever applies to the record:

```text
class
class + field
class + method name + method signature
```

Line numbers, exact message text, and inline context should usually be comparison fields rather than part of the base identity. This lets the comparator classify a record as "same report, changed details" instead of producing one missing record and one added record when a line number or wording changes.

Report records should be treated as a multiset, not a simple set. If two records have the same identity and details, duplicate count should be preserved unless an expectation explicitly marks duplicates as irrelevant.

Classification:

- missing expected records
- added records
- changed key facts
- tolerated instability
- schema or loading errors

Stable facts should include, when available:

- report kind
- class name
- field name
- method name
- method signature
- message, or a deliberate message pattern
- source file identity
- line or line range, when expected to be stable
- inline context, when expected to be stable

Known or likely unstable facts:

- report file timestamp
- raw ordering of records
- raw ordering of `used_methods`
- absolute path spelling
- some incidental line movement after upstream rebases

Expectation files should support both strict and tolerant matching. A representative expectation shape:

```json
{
  "kind": "METHOD",
  "class": "example.Foo",
  "method": "bar",
  "signature": "(I)Ljava/lang/String;",
  "messageRegex": "devirtualized|return type",
  "sourceFile": "example/Foo.java",
  "line": {
    "mode": "exact",
    "value": 42
  }
}
```

Line matching modes:

```text
ignore
exact
range
delta
```

Default line policy:

- controlled fixture: `exact` where the fixture source is deliberately stable
- Spring PetClinic and DaCapo: `ignore` or `range`, with grouped count checks instead of exact identity for every record

The first pre-rebase experiment should run each validation target several times on the current branch to identify actual nondeterminism before encoding tolerances.

For the initial data-gathering pass, collect at least five reports per target:

```text
custom Hello / IDE report fixture: 5 runs
Spring PetClinic: 5 runs
DaCapo h2: 5 runs
```

Use these repeated reports to decide the practical ground truth for post-rebase validation:

- which report families are deterministic enough for strict assertions
- which records need tolerant matching
- which line numbers are stable
- which messages need exact matching versus regex/category matching
- which ordering differences should be ignored
- which count ranges are realistic for larger benchmarks

Do not finalize the expectation-file schema or strictness policy until these repeated reports have been inspected. The comparator and expectation format should be shaped by the observed data rather than by assumptions about report determinism.

## Validation Application Set

Use three applications for the pre/post rebase comparison. They should cover different levels of determinism and realism.

### 1. Custom Hello / IDE Report Fixture

Purpose:

- Deterministic, fast, controlled coverage of report families.
- Main fixture for regular development and eventual gate-style testing.

Should intentionally exercise:

- reflection reports
- unreachable code
- devirtualization
- constant field reports
- constant parameter or return reports
- class initialization mode reports
- compiled method reporting
- inlined-only method reporting
- package-prefix filtering
- negative smoke without `-H:+IDEReport`

Expectation style:

- Strong assertions.
- Required report families must be present.
- Key messages and class/member facts should match.
- Line numbers may be asserted where the fixture is stable.

### 2. Spring PetClinic

Purpose:

- Realistic framework-heavy application.
- Exercises Spring, reflection/configuration/resource-heavy behavior, and large application structure.

Expectation style:

- Medium-strength semantic checks.
- Compare grouped counts and required high-level report families.
- Use class/package filters where possible to reduce JDK/Graal noise.

Operational notes:

- Spring PetClinic may need network and local server binding outside a
  restricted development sandbox.
- On Apple Silicon, Spring PetClinic dependency setup has known `wrk2` issues for runtime-style runs. For analysis/report collection, avoid requiring load-generator tools if possible.

### 3. DaCapo `h2`

Purpose:

- Large realistic non-web workload.
- Provides a different shape than Hello and Spring: database/application logic rather than another microservice framework.
- Part of the existing Native Image benchmark set.
- Avoids Spring PetClinic-specific load-generator setup issues.

Expectation style:

- Medium-strength semantic checks.
- Focus on report shape, report-family coverage, and broad grouped summaries rather than exact record identity.

Alternative candidate:

- Renaissance `finagle-http` is a reasonable alternate third workload if `h2` proves awkward. Prefer `h2` initially because it diversifies away from web frameworks while staying in the known Native Image benchmark set.

## Test Layers

### Unit Tests

Target:

- report model
- source adapters
- normalization
- query filters
- comparator classification
- expectation checking

Inputs:

- tiny checked-in synthetic report samples
- one or two reduced real report fragments once available

### Integration Smoke

Target:

- custom Hello / IDE report fixture

Checks:

- report generated when enabled
- no report generated when disabled
- filter behavior
- required report families and key facts

This is the only validation tier that should be considered for frequent local runs or gate-style execution.

Gate policy:

- only small fixture runs and tooling unit tests should be considered for `mx gate`
- do not include Spring PetClinic, DaCapo, or Renaissance benchmark runs in normal gates
- benchmark validation remains an explicit extended validation step for rebases and larger changes

### Extended Rebase Validation

Target:

- custom Hello / IDE report fixture
- Spring PetClinic
- DaCapo `h2`

Run before and after rebase:

- collect raw report artifacts
- load into `ReportBundle`
- summarize
- compare against pre-rebase expectations
- classify differences

This tier is intended for rebases and major feature changes, not every small edit.

Edition policy:

- run the extended validation in CE first
- treat CE as the default target because the IDE report feature is open-source
- add companion-repository validation only if later changes introduce a relevant cross-repository dependency

### Baseline Artifacts And Manifests

Generated baseline artifacts should live in a workspace-local ignored directory:

```text
.validation/ide-report/<date>-<branch>-<commit>/
```

The directory should contain three artifact layers:

- raw collected output, for audit
- canonicalized report output, for repeatable comparison
- summary and diff output, for quick review

Each validation run should write a manifest next to the collected reports. Representative manifest shape:

```json
{
  "schemaVersion": 1,
  "purpose": "pre-rebase-baseline",
  "timestamp": "...",
  "graalCommit": "...",
  "branch": "...",
  "mxCommand": "...",
  "nativeImageArgs": ["..."],
  "benchmark": {
    "suite": "spring-petclinic",
    "name": "spring-petclinic",
    "revision": "..."
  },
  "platform": {
    "os": "macOS",
    "arch": "aarch64",
    "jdk": "..."
  },
  "reportStorage": {
    "storage": "export",
    "payloadScope": "full",
    "compression": "gzip"
  },
  "artifacts": {
    "rawExport": "...",
    "canonicalExport": "...",
    "summary": "...",
    "hashes": "..."
  }
}
```

Large Spring PetClinic and DaCapo baselines must not be checked into source control. Tiny synthetic samples and reduced real report fragments are acceptable for unit tests.

### Broader Confidence Sweep

Optional later tier:

- selected DaCapo
- selected Renaissance
- selected Spring PetClinic

Use when changing core report producers or output format.

## Rebase Workflow

1. Build the report inspection tool skeleton with JSON source adapter.
2. Build the custom Hello / IDE report fixture.
3. Run the fixture multiple times on the current branch to learn nondeterminism.
4. Encode expectations and tolerances.
5. Run baseline collection for:
   - custom Hello fixture
   - Spring PetClinic
   - DaCapo `h2`
6. Rebase onto `origin/master`.
7. Resolve conflicts by preserving upstream structure and reinserting reporting at equivalent semantic phase boundaries.
8. Run formatting/checkstyle/build validation for affected suites.
9. Re-run the three-app validation set.
10. Classify all differences before doing feature improvements.

### Conflict-Specific Checklist

Known conflict files from the non-destructive merge analysis must map to explicit fixture assertions or comparator checks.

#### `TypeFlowSimplifier.java`

Report areas:

- unreachable branch and node reports
- return type calculation reports
- devirtualized invoke reports

Risks:

- reporting can move before or after the analysis transformation that determines the final fact
- source positions can change
- report families can silently stop firing

Required coverage:

- controlled unreachable report assertion
- controlled return type report assertion
- controlled devirtualization report assertion

#### `ReflectionPlugins.java`

Report areas:

- reflection reports

Risks:

- upstream reflection plugin changes can move or remove the reporting point
- source positions or messages can change
- some reflection reports may stop firing even though compilation still succeeds

Required coverage:

- at least one controlled reflection report with class/member/source facts

#### `NativeImageGenerator.java`

Report areas:

- report instance creation
- report printing/export lifecycle
- disabled/enabled behavior
- filter propagation

Risks:

- report initialization happens too late
- report printing happens too early or too late
- disabled builds still produce report output
- filtered builds collect or serialize the wrong scope

Required coverage:

- enabled report generation
- filtered report generation
- negative smoke without `-H:+IDEReport`

Rebase acceptance rule:

- do not treat conflict resolution as complete just because the code compiles
- each conflict file must be covered by at least one fixture assertion or comparator check
- every difference in the three-application validation set must be classified before feature improvements continue

## Validation Commands To Design Later

Exact commands are intentionally not finalized yet. They depend on how the fixture and `mx ide-report` command are implemented.

The final command set should be written after a few manual report collection and extraction runs. Trial runs should drive:

- exact fixture build command
- exact benchmark report collection command
- repeated-run collection layout
- canonicalization command shape
- comparison command shape
- summary/diff artifact names

The command set should eventually make these operations easy:

```text
mx ide-report summarize json:/path/report.json
mx ide-report query json:/path/report.json --kind LINE --message-regex reflection
mx ide-report compare json:/path/before.json json:/path/after.json
mx ide-report assert json:/path/report.json --expect /path/hello-expectations.json
```

For benchmarks, prefer existing Native Image benchmark machinery where possible:

- The Spring PetClinic application is available as a realistic validation target.
- DaCapo native-image benchmark source includes `h2`.
- DaCapo/Renaissance analysis-oriented runs should include agent data when reachability metadata matters.

## Remaining Design Topics

These still need planning before implementation:

- The exact fixture application shape and source location.
- The expectation file format, after inspecting repeated reports from the fixture and benchmarks.
- The concrete ground-truth assertions for post-rebase validation, based on repeated pre-rebase report collection.
- The final implementation commands, after a few manual extraction and comparison runs.

## Non-Goals For The First Implementation Step

- Do not design the final binary/debug-info storage format yet.
- Do not require byte-for-byte JSON equality.
- Do not turn Spring PetClinic or DaCapo into normal per-commit tests.
- Do not make the query tool depend on JSON internals.
- Do not rebase until baseline collection and comparison mechanics exist.
