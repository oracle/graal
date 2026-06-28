# Native Image IDE Report PR Deep Review

Date: 2026-06-28

## Executive Status

The implementation is complete as the validated prototype defined by Phases 0
through 16. It is not ready to merge into `master` yet.

The review found no P0 data-corruption issue and no regression in the validated
prototype workflows. It did find one explicit architectural merge blocker,
three production robustness gaps, incomplete integration coverage, and several
lower-priority maintainability and reviewability concerns.

Review scope:

- target: `origin/master` at `25fdc04abe6d5589a7cf85debc65e07fd3d24dff`
- reviewed implementation tip: `5086ea2b398246a46445d49651969f74674f67af`
- implementation history: six reviewer-oriented commits
- branch delta before this review document: 74 files, 12,472 insertions, and
  14 deletions
- affected areas: compiler reporting, points-to analysis, Native Image hosted
  build lifecycle, object-file storage, build artifacts, Python tooling,
  fixtures, tests, measurements, and design documentation

The distinction between "prototype complete" and "merge-ready" is important.
The phase documents prove that the intended experiment works. They do not
remove the remaining product and integration requirements listed below.

## Findings

Findings are ordered by severity. P2 denotes a meaningful correctness,
robustness, integration, or merge-readiness issue. P3 denotes a maintainability,
coverage, or future-risk concern.

### P2: The Compiler Integration Still Has An Open Architectural Blocker

The current collector is exposed to compiler and analysis code through a
process-global static bridge in `IDEReport`. `beginBuild` rejects overlapping
builds, publishes a volatile instance, and clears it when the build scope
closes. This is internally coherent for the documented assumption of one
active image build per process, but it is not the desired long-term Native
Image integration and it remains an explicit reviewer blocker.

Positive inlining is also reported through dedicated calls in
`StructuredGraph.notifyInliningDecision` and `PEGraphDecoder`, parallel to the
standard inlining log and `TraceInlining` machinery. The code documents why
the prototype does this, but the review requirement is to integrate with the
standard path instead of maintaining a second observability path.

Relevant code:

- `compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/ide/IDEReport.java:116`
- `compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/nodes/InliningIDEReporting.java:37`
- `compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/nodes/StructuredGraph.java:716`
- `compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/replacements/PEGraphDecoder.java:1454`

Required direction:

1. Replace the static report instance with an accepted build-scoped singleton
   or event-consumer mechanism that compiler code can access without creating
   a forbidden dependency.
2. Route positive inlining observations through the standard inlining event or
   log infrastructure.
3. Retain coverage for parsing-time and later inlining paths, duplicate
   suppression, disabled-build behavior, failed builds, and two sequential
   builds in one process.
4. Resolve the reviewer blocker only after the reviewer agrees that the new
   ownership and event path satisfy the original request.

This is classified P2 rather than P1 because the present implementation
rejects unsupported overlap instead of silently mixing reports, and the
feature is opt-in. It is nevertheless a merge blocker.

### P2: Explicit Export And Split Write Failures Do Not Fail The Build

Canonical export and split output are written from the `finally` block in
`NativeImageGenerator`. If serialization or file output fails after an
otherwise successful image build, the exception is converted into a warning.
The image build can therefore return successfully even though an explicitly
requested IDE report is absent. Embedded preparation is different: it runs in
the embedding feature before image creation and can fail the build.

Relevant code:

- `substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGenerator.java:623`
- `substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/ide/IDEReportEmbeddingFeature.java:57`
- `substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/ide/IDEReportStorageData.java:55`

Required direction:

- Decide and document whether explicitly requested report output is required
  or best effort.
- Prefer consistent failure semantics across export, split, and embed.
- If output remains best effort, expose the missing artifact in structured
  build status rather than only a warning.
- Add tests for unwritable destinations, serialization failure, a failed image
  build, and successful image generation with failed report output.

### P2: Envelope Readers Have No Decoded-Payload Limit

Both envelope readers trust the declared uncompressed size up to implementation
limits and decompress the entire payload into memory. A small gzip input can
therefore allocate a very large byte array when an IDE or developer tool opens
an untrusted executable or side file. The design already records this as
deferred work, but it must be resolved before treating extraction as a
production-facing feature.

Relevant code:

- `substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/ide/IDEReportEnvelope.java:117`
- `substratevm/mx.substratevm/ide_report/envelope.py:80`
- `substratevm/docs/ide-reports/IDE_REPORT_EMBEDDED_STORAGE_DESIGN.md:830`

Required direction:

- Define maximum stored-envelope and decoded-payload sizes.
- Reject oversized headers before allocation.
- Decompress through a bounded stream and fail as soon as the limit is
  exceeded.
- Add Java and Python tests with oversized headers and high-ratio compressed
  payloads.

### P2: Line-Oriented Records Lose Their Class Subject

`saveLineReport` and `saveUnreachableRangeReport` receive the fully qualified
class name and use it for producer-side filtering, but do not serialize it.
Consequently, `mx ide-report query --class` and `--class-prefix` cannot select
reflection, inlining, devirtualization, return-value, parameter-value, or
unreachable records. Comparator summaries also cannot group these records by
class.

The Phase 16 fixture makes the gap concrete: it has 43 `LINE` records and one
`UNREACHABLE` record, and none of those 44 records contains a `class` field.

Relevant code:

- `compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/ide/IDEReport.java:234`
- `compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/ide/IDEReport.java:251`
- `substratevm/mx.substratevm/ide_report/cli.py:354`
- `substratevm/mx.substratevm/ide_report/compare.py:244`

Required direction:

- Decide whether adding `class` is a backward-compatible extension of the
  legacy schema or only a canonical-payload extension.
- Preserve the subject in the neutral model for every producer that already
  knows it.
- Add query and comparator tests covering class selection for line and range
  records.

### P2: Storage Lifecycle And Artifact Registration Need More Integration Tests

The current tests strongly cover canonical bytes, envelope corruption,
option parsing, primitive split output, ELF section construction, and synthetic
Mach-O/ELF extraction. Real image smoke tests provide valuable external
evidence. The checked-in Java tests do not yet exercise several contracts at
the integration boundary:

- actual `build-artifacts.json` registration and path rendering
- the `embed,export` combination end to end
- report-output failure semantics
- cleanup after failed builds
- two sequential image builds in one process
- a future replacement for the static compiler bridge
- shared-library and layered-image builds

`BuildArtifactsExporter` also contains a correct fix for an upstream
`startsWith` typo, but that broader path-normalization behavior has no focused
test in this PR.

Relevant code:

- `substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/BuildArtifactsExporter.java:70`
- `substratevm/src/com.oracle.svm.test/src/com/oracle/svm/test/ide/IDEReportStorageTest.java:66`
- `substratevm/mx.substratevm/ide_report/test_ide_report.py:153`

Required direction:

- Add a small build-artifact exporter test for inside-build and outside-build
  paths.
- Add a gate-scale Native Image integration test that checks files, manifest
  entries, decoded content, and cleanup across repeated builds.
- Keep large application matrices outside regular gates.

### P3: Minimal-Scope Membership Has Three Sources Of Truth

`IDEReportCategory` stores an `includedInMinimalPayload` flag, but canonical
serialization does not use it. Java and Python each maintain a separate set of
serialized category names. A new category can therefore drift between the
producer, Java canonical export, and Python canonicalization.

Relevant code:

- `compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/ide/IDEReportCategory.java:28`
- `substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/ide/IDEReportCanonicalPayload.java:48`
- `substratevm/mx.substratevm/ide_report/canonicalize.py:41`

Required direction:

- Make the Java enum authoritative for Java serialization.
- Keep a cross-language golden-vector test for Python parity.
- Add a test that enumerates every category and its expected scope.

### P3: Filters Reduce Output More Reliably Than Collection Cost

The disabled fast paths are good: hot hooks return before allocating capturing
lambdas or reading analysis state. Once reporting is enabled, many producers
compute source positions, traverse graph regions, inspect type flows, or walk
all analyzed types before the final `save*` call applies the class filter.

This is acceptable for the prototype, but a package filter should eventually
reduce collection work as well as payload size. The Phase 14 data also shows
that large unfiltered reports are expensive: the DaCapo characterization
produced roughly 28 MB of canonical JSON and spent approximately 2.4 to 2.6
seconds in full-payload serialization, plus compression time.

Relevant code:

- `substratevm/src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/ide/AnalysisIDEReporting.java`
- `substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/ide/IDEReportingFeature.java:80`
- `substratevm/docs/ide-reports/IDE_REPORT_PHASE14_RESULTS.md`

Required direction:

- Expose a cheap class-admission check to producers.
- Measure disabled-build overhead separately from opt-in reporting cost.
- Re-run statistically meaningful measurements after the collector ownership
  and inlining path are changed.

### P3: Reporting Widens Existing Hosted APIs

The feature makes `InitKind` and `computedInitKindFor` public so a reporting
feature in a neighboring package can inspect class-initialization state. These
are hosted implementation classes rather than supported public APIs, but the
visibility increase still enlarges the internal coupling surface.

Relevant code:

- `substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/classinitialization/InitKind.java:31`
- `substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/classinitialization/ClassInitializationSupport.java:225`

Required direction:

- Prefer a narrow reporting/query method or package placement that does not
  expose the enum and general lookup more broadly than necessary.
- If the visibility remains, document it as an intentional internal contract.

### P3: Tooling Contracts And Diagnostics Are Not Fully Uniform

The neutral model is a strong boundary, but its frozen dataclasses contain
mutable lists and dictionaries, `ReportBundle.to_dict` does not include
`payload_scope`, explicit JSON source adapters do not consistently wrap file
and JSON errors, and malformed count expectations can express contradictory
or negative ranges. The design also lists line, line-range, and inline-context
query filters that the CLI does not implement.

These do not invalidate current validation results. They should be cleaned up
before presenting the Python package as a stable developer API.

Relevant code:

- `substratevm/mx.substratevm/ide_report/model.py`
- `substratevm/mx.substratevm/ide_report/sources.py`
- `substratevm/mx.substratevm/ide_report/compare.py`
- `substratevm/mx.substratevm/ide_report/cli.py`
- `substratevm/docs/ide-reports/IDE_REPORT_REBASE_VALIDATION_DESIGN.md:110`

### P3: The Checked-In Historical Material Is Valuable But Large

The design and phase evidence make the work reproducible and provide an
unusually strong handoff. They also account for about 4,700 lines of the PR,
while measurement drivers and their tests add substantial additional review
surface. Before merge, maintainers should decide which files are durable
product/developer documentation and which are project-history records better
kept in the issue or review system.

A reasonable retained set is:

- the storage design
- the rebase-validation design, trimmed to current behavior
- concise user/developer instructions
- the final PR review and validation summary
- the reusable CLI and gate-scale fixture documentation

Per-phase diaries can remain if maintainers value them, but they should not be
mistaken for user documentation.

## Implementation Assessment

### Report Collection

Strengths:

- Reporting is opt-in.
- Disabled hooks return early and do not inspect analysis state.
- Concurrent collectors are used for compiler and analysis events.
- Snapshot creation deduplicates reports without mutating live collectors.
- Class filtering is parsed once per build, trims whitespace, and rejects empty
  comma-separated elements.
- The fixture exercises reflection, inlining, reachability, devirtualization,
  value facts, class initialization, compiled methods, and inlined-only methods.

Shortcomings:

- Collector ownership and inlining integration remain blocked as described
  above.
- Report records are untyped maps, so schema errors are found late.
- Some source-location choices are implicit, such as subtracting one from the
  first method line to approximate a declaration line.
- Most producer semantics are tested through one end-to-end fixture rather
  than focused unit tests at each analysis hook.

Assessment: good experimental coverage and acceptable prototype structure,
but the compiler integration must change before merge.

### Canonical Payload And Envelope

Strengths:

- Deterministic UTF-8 JSON with stable keys and record ordering.
- Explicit payload scope and schema version.
- Deterministic gzip with a fixed header.
- SHA-256 over decoded payload bytes.
- Cross-language Java/Python golden-vector coverage.
- Embedded and split storage share exact envelope bytes.

Shortcomings:

- Compatibility is best effort and readers accept only version 1.
- Decoded size is not bounded.
- Full payloads are large for realistic unfiltered applications.
- Minimal-scope category membership is duplicated.

Assessment: a sound prototype format, not yet a durable external contract.

### Export, Split, And Embedded Storage

Strengths:

- The three modes have distinct, documented semantics.
- Split is a real externalization of embedded bytes, not a second JSON export.
- Mach-O and ELF sections are non-writable and non-instruction data.
- Locator symbols and adjacent length data are validated.
- Real macOS/AArch64 and Linux/AArch64 custom-source builds execute and extract.
- Linux default stripping and explicit debug/all stripping were validated.
- Unsupported object formats receive a clear embed diagnostic while split
  remains available.

Shortcomings:

- PE/COFF has no embed/extract backend.
- Readers support only little-endian 64-bit ELF and Mach-O.
- Shared-library, layered-image, and additional architectures are untested.
- Output-failure semantics differ by storage mode.

Assessment: strong platform prototype for the two tested formats, with an
appropriately portable split fallback.

### Command-Line Tooling

Strengths:

- `ReportBundle` isolates consumers from storage details.
- Raw JSON, canonical JSON, split, image, and automatic sources converge on the
  same model.
- Canonicalization, semantic comparison, expectations, summaries, and queries
  cover the core development workflows.
- `auto:` has deterministic precedence and reports embedded/split mismatch.
- The comparator preserves duplicate counts and separates identity from
  details.

Shortcomings:

- Class queries are incomplete for line/range facts.
- Some planned filters are absent.
- Explicit-source error handling is less polished than `auto:` diagnostics.
- The baseline collector assumes a prepared custom CE benchmark environment;
  large-application reproduction remains more environment-dependent than the
  gate-scale fixture.

Assessment: useful and well-tested developer tooling, with clear areas to
finish before calling it a stable interface.

### Tests And Validation

Strengths:

- 47 Python tests cover model, canonicalization, envelopes, readers, automatic
  discovery, comparison, expectations, CLI helpers, and baseline helpers.
- 3 compiler tests cover filter and build-scope behavior.
- 11 storage tests cover options, payloads, envelopes, split bytes, ELF object
  construction, and unsupported formats.
- The gate-scale fixture has positive and reporting-disabled smokes.
- Five-run semantic baselines exist for Hello World, Spring PetClinic, and
  DaCapo H2.
- Real storage matrices validate full/minimal and export/embed/split
  equivalence.
- Both ECJ and javac warning-as-error builds passed for affected projects.

Shortcomings:

- No CI builds have been run for the reviewed tip.
- The large application baselines are saved evidence, not regular gates.
- The Java storage test needs an explicit module opening when run standalone.
- Manifest registration and failure lifecycle are not covered by a checked-in
  integration test.

Assessment: excellent prototype validation, but final CI and the missing
integration contracts remain necessary.

## Design Compliance

| Requirement | Status | Review conclusion |
| --- | --- | --- |
| Preserve report behavior across rebase | Complete | Semantic pre/post validation passed. |
| Avoid raw byte equality as the semantic gate | Complete | Canonical hashes are provenance; expectations drive pass/fail. |
| Use a source-neutral consumer model | Complete | All implemented sources load into `ReportBundle`. |
| Provide query, comparison, and expectations | Complete with limitations | Core commands work; class and planned location filters are incomplete. |
| Validate fixture, Hello World, and realistic applications | Complete | Five-run evidence exists for all selected applications. |
| Use a custom current-source GraalVM | Complete | macOS and Linux image evidence used current-branch distributions. |
| Preserve legacy `-H:+IDEReport` behavior | Complete | Timestamped raw JSON remains supported. |
| Add canonical export | Complete | Deterministic full/minimal JSON is implemented. |
| Add split storage | Complete | `<image>.ide-report` contains the shared envelope. |
| Add embedded storage | Complete for Mach-O/ELF | PE/COFF is explicitly deferred. |
| Keep embedded/split bytes equivalent | Complete | Same-build byte identity was validated. |
| Exercise compressed and uncompressed envelopes | Complete | Unit and real-image coverage exists. |
| Register dedicated build artifacts | Partially complete | Type and schema exist; actual exporter integration lacks a focused test. |
| Provide automatic source discovery | Complete | Embedded, sibling, manifest, JSON, and split discovery are covered. |
| Bound untrusted extraction | Deferred | Must be completed before production-facing extraction. |
| Define compatibility policy | Deferred | Version 1 is prototype-only and best effort. |
| Define confidentiality/packaging policy | Deferred | Reports can reveal internal program structure. |
| Support `native-image-utils` extraction | Deferred | `mx ide-report` is the only intended reader today. |
| Establish production payload defaults | Deferred | Measurements favor `minimal`; `full` remains the prototype default. |

## Current Validation Evidence

The implementation tip reviewed here has the following recorded evidence:

- strict Python formatting and bytecode compilation
- 47 Python tests
- compiler and Substrate VM Eclipse formatting and Checkstyle
- ECJ and javac warning-as-error builds for affected compiler, points-to,
  hosted, and test projects
- 3 compiler IDE-report tests
- 11 storage tests
- fresh enabled and disabled fixture smokes
- fresh Hello World semantic validation
- fresh macOS/AArch64 export/embed/split storage matrix
- saved five-run Hello World, Spring PetClinic, and DaCapo H2 validation
- real Linux/AArch64 ELF image, extraction, execution, and strip validation

This deep-review pass additionally confirmed:

- local and remote implementation tips were identical before adding this file
- `origin/master` had not moved from the reviewed base
- the branch had no left/right divergence from its remote
- `git diff --check origin/master...HEAD` passed
- all 47 Python tests passed again
- the current fixture has zero class-bearing line or unreachable records,
  confirming the class-query limitation
- no CI build result exists for the reviewed implementation tip

The earlier phase documents contain exact commands, payload sizes, hashes, and
platform evidence. This document summarizes their merge-readiness meaning
rather than duplicating every raw result.

## Review And Repository State

At the time of this review:

- the implementation branch is rebased directly on the recorded target
- the six implementation commits form a coherent review order
- the worktree is clean before this documentation change
- the public title and summary are concise and do not expose private build
  details
- an obsolete companion change is closed and no companion repository change is
  required by the current implementation
- there are no reviewer approvals yet
- one technical reviewer blocker remains open
- routine performance, security, and checklist acknowledgements remain open
- no CI gates or build jobs have been started for the reviewed tip

These facts are a dated snapshot. The technical findings remain authoritative
until code or design changes address them; approval and CI state must always be
rechecked live.

## Recommended Next Work

### 1. Resolve Collector Ownership And Inlining Integration

Input:

- the current static bridge
- the two custom positive-inlining hooks
- the open review requirement

Output:

- accepted build-scoped ownership
- standard inlining event consumption
- no duplicate or missing positive-inlining facts

Validation:

- focused compiler tests
- enabled/disabled fixture
- sequential and failed-build lifecycle tests
- reviewer confirmation that the blocker is resolved

### 2. Define Output Failure And Extraction Safety Contracts

Input:

- current warning-only export/split behavior
- current unbounded readers

Output:

- consistent storage failure policy
- bounded Java and Python decoding
- documented limits and diagnostics

Validation:

- unwritable output tests
- oversized-header tests
- compression-bomb tests
- unchanged valid full/minimal vectors

### 3. Complete Semantic Subjects And Tool Queries

Input:

- line/range producers that already know the class
- compatibility constraints of the legacy schema

Output:

- class-bearing neutral records
- working class filters for all report families
- decision on canonical-only versus legacy extension

Validation:

- query tests for reflection, inlining, reachability, and return facts
- comparator grouping by class
- compatibility check with the existing IDE consumer

### 4. Add Missing Integration Tests

Input:

- build artifact exporter
- storage combinations
- Native Image build lifecycle

Output:

- gate-scale manifest and storage integration coverage
- inside/outside build-path coverage
- repeated-build cleanup coverage

Validation:

- focused gate from a clean worktree
- no benchmark suite dependency in regular gates

### 5. Revisit Performance And Payload Defaults

Input:

- Phase 14 measurements
- collector and inlining changes from step 1

Output:

- disabled-build overhead result
- representative enabled overhead result
- production recommendation for `minimal` versus `full`
- decision on compact method inventory

Validation:

- repeated measurements with variance reported
- performance acknowledgement completed

### 6. Finish Product Policy And User Documentation

Input:

- prototype design
- compatibility, confidentiality, packaging, and retention questions

Output:

- supported platform and format statement
- schema compatibility promise
- production packaging policy
- concise user-facing option and extraction documentation
- release-note decision

Validation:

- documentation review
- security review when required
- public-surface hygiene scan

### 7. Run CI And Final Human Review

Input:

- all blocker fixes and focused tests

Output:

- green required gates
- resolved tasks
- reviewer approvals

Validation:

- current target rechecked
- CI results attached to the final tip
- no uncommitted or unpushed changes

## Merge-Readiness Checklist

- [x] Rebase completed and semantic report surface preserved.
- [x] Custom fixture and semantic comparison tooling implemented.
- [x] Legacy, canonical, split, Mach-O, and ELF workflows validated.
- [x] Full/minimal size and build-cost characterization recorded.
- [x] Public repository content sanitized.
- [ ] Static collector bridge replaced with accepted ownership.
- [ ] Positive inlining routed through the accepted standard path.
- [ ] Output-failure semantics decided and tested.
- [ ] Decoded-payload limits implemented and tested.
- [ ] Class subjects preserved for line/range records.
- [ ] Build artifact and repeated-build integration tests added.
- [ ] Performance and security/checklist tasks completed.
- [ ] User-facing compatibility and packaging policy decided.
- [ ] Required CI gates pass on the final tip.
- [ ] Human review tasks are resolved and approvals are present.

## Final Recommendation

The PR is in good shape for continued review and collaboration. It demonstrates
the complete end-to-end concept, preserves the original report behavior across
the rebase, provides useful storage-neutral tooling, and has unusually strong
prototype validation.

It should not be described as finished for merge. The next implementation work
should start with the collector singleton and inlining integration because that
is the explicit review blocker and can affect the shape of later performance
and lifecycle tests. Output failure policy, bounded extraction, semantic class
subjects, and missing integration coverage should follow before gates and final
approval.
