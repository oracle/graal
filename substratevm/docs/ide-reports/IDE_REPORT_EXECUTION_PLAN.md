# GR-61707 IDE Report Execution Plan

## Purpose

This document turns the current design notes into an executable sequence. It covers:

- rebase validation and report comparison
- repeated report collection to discover determinism
- the actual rebase onto current `origin/master`
- the first embedded/split storage prototype
- storage equivalence, size, and performance measurement

Source design documents:

- [IDE_REPORT_REBASE_VALIDATION_DESIGN.md](IDE_REPORT_REBASE_VALIDATION_DESIGN.md)
- [IDE_REPORT_EMBEDDED_STORAGE_DESIGN.md](IDE_REPORT_EMBEDDED_STORAGE_DESIGN.md)

## Current Feature Surface

The public GitHub mirror PR is https://github.com/oracle/graal/pull/12586,
titled `[GR-61707] Native Image IDE Reports`.

The current branch is expected to provide:

- `-H:+IDEReport` for the compatible timestamped legacy JSON export.
- `-H:IDEReportFiltered=<comma-separated class-name prefixes>` to scope report
  collection.
- `-H:IDEReportStorage=export|embed|split|embed,export|embed,split` for canonical
  export, image-attached storage, and an image-adjacent side file.
- `-H:IDEReportPayloadScope=full|minimal` to select all prototype facts or the
  smaller production-oriented category subset.
- deterministic canonical JSON and a versioned, checksummed, optionally
  compressed envelope shared by split and embedded storage.
- embedded storage in macOS/Mach-O and Linux/ELF images.
- `mx ide-report` loading through `json:`, `canonical:`, `image:`, `split:`,
  and `auto:` sources, with inspection, comparison, baseline, and fixture-smoke
  commands.
- Source-backed information about unreachable code, devirtualization, constant
  parameters, constant returns, constant fields, reflection resolution, class
  initialization mode, compiled methods, and inlined-only methods.

Important implementation entry points in the current branch:

- `compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/ide/IDEReport.java`
  defines the main options, enables `TrackNodeSourcePosition`, aggregates
  reports, and writes JSON.
- `compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/ide/ClassFilter.java`
  parses prefix filters. An empty prefix currently matches everything.
- `compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/ide/QualifiedStacktraceElement.java`
  represents inlining context entries as source path plus line.
- `compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/nodes/InliningIDEReporting.java`
  records inlined callees and source call sites.
- `compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/nodes/StructuredGraph.java`
  reports positive normal inlining decisions.
- `compiler/src/jdk.graal.compiler/src/jdk/graal/compiler/replacements/PEGraphDecoder.java`
  reports PE graph decoder inlining through the same path.
- `substratevm/src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/ide/AnalysisIDEReporting.java`
  reports unreachable code, devirtualization, constants, exact parameter and
  return information, and narrowed return values at invokes.
- `substratevm/src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/results/TypeFlowSimplifier.java`
  calls reporting before simplifying unreachable branches, unreachable invokes,
  possible return types, and devirtualized calls.
- `substratevm/src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/results/StrengthenGraphs.java`
  calls parameter and return reporting before applying
  `AnalysisStrengthenGraphsPhase`.
- `substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/meta/UniverseBuilder.java`
  reports constant fields while creating hosted fields.
- `substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGenerator.java`
  creates build-scoped reporting state and writes export or split artifacts
  after `doRun`.
- `substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/ide/`
  owns option resolution, canonical payloads, envelopes, shared storage data,
  and object-file embedding.
- `substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/ide/IDEReportingFeature.java`
  registers only when reporting is enabled, reports class initialization modes
  after analysis, and reports compiled methods after compilation.
- `substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/snippets/ReflectionPlugins.java`
  reports `Class.forName` and `Class.getClassLoader` folding/resolution.
- `substratevm/mx.substratevm/suite.py` includes the relevant pointsto IDE
  package.
- `substratevm/mx.substratevm/ide_report/` contains the neutral model, source
  adapters, comparison tooling, baseline helpers, extractors, and measurement
  drivers.

## Invariants To Preserve

- Reporting must stay opt-in and must not affect default builds.
- Enabling reporting must provide source positions by enabling
  `TrackNodeSourcePosition`.
- Reporting failures must not mask or alter Native Image analysis or
  compilation behavior.
- Reports must tolerate missing source positions, missing source file names,
  and non-bytecode methods.
- Filtering must be applied consistently by fully qualified class name.
- Reported line ranges should be stable enough for IDE consumption and should
  not include unrelated reachable code.
- Report aggregation must remain thread-safe because collection may happen from
  multiple build threads.

## Design Review

### Overall Suitability

The two design documents are now coherent enough to execute. The key architectural decision is sound: all tooling should operate on a neutral `ReportBundle` model rather than raw JSON or object-file details. That lets the rebase validation work become the foundation for later embedded and split storage.

The most important execution constraint is ordering. The rebase validation safety net must come first. Embedded storage should not be implemented until the report model, canonicalization, query, comparison, and repeated baseline collection are working.

### Strengths

- The rebase validation design avoids byte-for-byte JSON equality and uses semantic comparison.
- The comparator identity model is explicit enough for an initial implementation.
- The plan requires repeated runs before defining ground truth, which is the right way to discover real nondeterminism.
- The app set balances controlled coverage with realistic workloads: custom fixture, Spring PetClinic, and DaCapo `h2`.
- Gate policy is appropriately small: only fixture/tooling tests, not full benchmarks.
- Embedded storage is modeled as a new source adapter, not as a separate tool universe.
- `export`, `embed`, and `split` now have distinct meanings.
- Payload scope is measurable first (`full`) and can later become production-oriented (`minimal`).

### Resolved Execution Questions

Phases 5 through 15 resolved the original execution gaps:

- the focused fixture and its semantic report-family coverage are stable
- five-run baselines characterize fixture, Hello World, Spring PetClinic, and
  DaCapo H2 determinism
- expectation files define exact anchors and tolerant grouped counts
- the CLI syntax and baseline directory layout are implemented and tested
- Mach-O and ELF use format-specific read-only sections behind one envelope
  and source-adapter contract
- report size and finalization cost are measured for full and minimal scopes

### Risks

- Current JSON is timestamped and collector-order dependent; canonicalization must be implemented before meaningful comparison.
- Current report generation lives partly under compiler and partly under Native Image hosted/points-to code; avoid making the Python tooling depend on Java package ownership.
- Rebase conflicts touch report-producing semantic points, not only formatting or lifecycle code.
- Spring PetClinic and DaCapo runs may require network, caches, or benchmark
  setup outside a restricted development sandbox.
- `used_methods` and compiled/inlined method facts may dominate payload size; measurements must separate their contribution.
- Cross-platform embedding can fail in platform-specific ways even if the envelope design is portable.
- Build artifact schema changes must be kept in sync with enum changes.
- `InliningIDEReporting.java` had checkstyle-sensitive formatting in the
  original branch context; verify this during Phase 0 and fix only if still
  present.
- `AnalysisIDEReporting.reportDevirtualizedInvoke` should be reviewed against
  possible `Invoke` implementations because reporting must not fail if a plain
  invoke reaches that path.
- `IDEReport` state is owned by the hosted `ImageSingletons` registry. Keep the
  stateless compiler access SPI and the hosted-registry lifecycle covered when
  changing report collection.
- `ClassFilter.parseFilterDescr` treats an empty string as a prefix matching
  everything. Confirm whether this should remain intentional behavior.

## Remaining Questions

All questions required for this prototype and its validation are resolved.
The following production concerns remain deferred by design:

- `native-image-utils extract-ide-report` support.
- User-facing compatibility promises for embedded format.
- Compact method-inventory storage.
- Confidentiality, packaging, CI retention, and redaction policy.
- PE/COFF support.

## Execution Plan

Each step below should be completed in order. Do not rebase until Phase 7 passes.

### Phase 0: Hygiene And Current-State Capture

#### Step 0.1: Verify Worktree State

Input:

- current checkout under `graal/`

Action:

- run `git -C graal status --short`
- record branch and commit

Output:

- clean/dirty state noted in the work log or validation manifest

Validation:

- no unexpected source changes before implementation starts

#### Step 0.2: Refresh Remote State Without Rebasing

Input:

- `graal` checkout

Action:

- run `git -C graal fetch origin master`
- record `origin/master`
- record merge-base with current branch

Output:

- refreshed commit ids for the validation manifest

Validation:

- no working tree changes created
- no rebase performed

#### Step 0.3: Update Design Stale Items

Input:

- current design docs

Action:

- remove or mark resolved stale open items, especially `IDEReportStorage` spelling
- keep `README.md` and `IDE_REPORT_TODO.md` pointing at this execution plan

Output:

- design docs point to this execution plan

Validation:

- `rg "Remaining Open Questions|Open Design Topics" *.md` shows only intentional open/deferred items

### Phase 1: Tool Skeleton

#### Step 1.1: Create Tool Package Directory

Input:

- `graal/substratevm/mx.substratevm/`

Action:

- create `graal/substratevm/mx.substratevm/ide_report/`
- add `__init__.py`
- add placeholder modules for model, sources, canonicalization, comparison, and CLI

Output:

- importable Python package

Validation:

- `python -m py_compile` succeeds for all new files

#### Step 1.2: Register `mx ide-report`

Input:

- `mx_substratevm.py`
- tool package from Step 1.1

Action:

- add a thin `@mx.command(..., "ide-report")`
- delegate argument parsing to the tool package

Output:

- `mx -p graal/substratevm ide-report --help` works

Validation:

- command prints help
- command exits non-zero with useful diagnostics for invalid subcommands

#### Step 1.3: Add Initial Subcommands

Input:

- tool CLI skeleton

Action:

- implement empty/placeholder command routing for:
  - `summarize`
  - `query`
  - `compare`
  - `assert`
  - `canonicalize`

Output:

- command parser and help text

Validation:

- every subcommand has `--help`
- unknown arguments produce clear errors

### Phase 2: Raw JSON Loading And Neutral Model

#### Step 2.1: Define `ReportBundle`

Input:

- current JSON keys from `IDEReport.java`

Action:

- define Python dataclasses or simple structured classes:
  - `ReportBundle`
  - `ReportRecord`
  - `SourceLocation`
  - `MethodReference`
  - `ReportProvenance`

Output:

- model module

Validation:

- unit test constructs and serializes a minimal bundle

#### Step 2.2: Implement `json:` Source Adapter

Input:

- raw current JSON report path

Action:

- load raw legacy JSON
- map `reports` records into `ReportRecord`
- map `used_methods` into model extension data
- preserve unknown fields in extension maps

Output:

- `ReportBundle` from raw JSON

Validation:

- unit test with synthetic raw JSON containing `reports` and `used_methods`

#### Step 2.3: Implement Source URI Parsing

Input:

- source strings:
  - `json:...`
  - `canonical:...`
  - `image:...`
  - `split:...`
  - `auto:...`

Action:

- parse scheme and path
- implement `json:` only
- return clear "not implemented yet" for other schemes

Output:

- source parser module

Validation:

- unit tests for valid and invalid source strings

#### Step 2.4: Implement Basic `summarize`

Input:

- `json:/path/report.json`

Action:

- count records by kind
- count records with source locations
- count used methods

Output:

- human table
- JSON output option

Validation:

- summary of synthetic sample matches expected counts

#### Step 2.5: Implement Basic `query`

Input:

- `json:/path/report.json`
- query filters

Action:

- support filters:
  - `--kind`
  - `--class`
  - `--class-prefix`
  - `--source-file`
  - `--method`
  - `--field`
  - `--message-regex`

Output:

- filtered records as table/JSON/JSONL

Validation:

- unit tests for each filter

### Phase 3: Canonicalization

#### Step 3.1: Define Canonical JSON Shape

Input:

- observed model fields

Action:

- define stable top-level keys:
  - `schema_version`
  - `payload_scope`
  - `records`
  - `used_methods`
  - `provenance`
  - `extensions`

Output:

- documented canonical schema in code comments or a small markdown under tool docs

Validation:

- sample canonical JSON can round-trip through the loader

#### Step 3.2: Implement Deterministic Ordering

Input:

- `ReportBundle`

Action:

- sort records by identity fields and details
- sort `used_methods`
- sort object keys during JSON emission

Output:

- deterministic canonical JSON bytes

Validation:

- two bundles with shuffled raw input produce identical canonical output

#### Step 3.3: Implement `canonicalize`

Input:

- `json:/path/raw.json`
- output path

Action:

- load raw JSON
- emit canonical JSON
- optionally emit SHA-256 over canonical bytes

Output:

- canonical report file
- optional hash file

Validation:

- repeated canonicalization of same raw file produces byte-identical output

#### Step 3.4: Implement `canonical:` Source Adapter

Input:

- canonical report path

Action:

- load canonical JSON into `ReportBundle`

Output:

- `ReportBundle` from canonical JSON

Validation:

- raw JSON canonicalized then reloaded has same semantic summary

### Phase 4: Comparator And Expectations

#### Step 4.1: Implement Record Identity

Input:

- `ReportRecord`

Action:

- compute identity:
  - kind
  - normalized source file
  - semantic subject
  - message category

Output:

- identity key function

Validation:

- unit tests cover class, field, method, and no-subject records

#### Step 4.2: Implement Multiset Comparison

Input:

- two `ReportBundle` instances

Action:

- compare record multisets by identity
- classify missing, added, and changed detail fields

Output:

- machine-readable diff model

Validation:

- unit tests for missing, added, changed, duplicates, and tolerated order changes

#### Step 4.3: Implement Grouped Summary

Input:

- diff model

Action:

- group by report kind
- group by class/package where available

Output:

- concise grouped summary

Validation:

- synthetic diff produces expected grouping

#### Step 4.4: Implement Initial Expectation Parser

Input:

- expectation JSON

Action:

- parse expected records
- support line modes:
  - `ignore`
  - `exact`
  - `range`
  - `delta`
- support exact or regex message matching

Output:

- expectation model

Validation:

- unit tests for valid/invalid expectation files

#### Step 4.5: Implement `assert`

Input:

- report source
- expectation file

Action:

- check expected records and grouped counts
- produce clear failure diagnostics

Output:

- pass/fail result
- machine-readable failure details

Validation:

- passing and failing synthetic expectation tests

### Phase 5: Custom Fixture

#### Step 5.1: Choose Fixture Location

Input:

- existing `substratevm` test layout

Action:

- choose source location for a tiny Java app or test project
- document the choice in the rebase design or this plan

Output:

- fixture location decision

Validation:

- location fits suite ownership and can be built by `mx`

#### Step 5.2: Implement Minimal Fixture

Input:

- chosen location

Action:

- create a small Java app that initially only builds and runs

Output:

- compilable native-image fixture

Validation:

- native-image build succeeds without IDE report enabled

#### Step 5.3: Add Reflection Case

Input:

- fixture app

Action:

- add controlled reflection usage that should produce an IDE report

Output:

- fixture reflection path

Validation:

- `-H:+IDEReport` report contains expected reflection family

#### Step 5.4: Add Unreachable/Devirtualization/Return Cases

Input:

- fixture app

Action:

- add controlled code for:
  - unreachable branch/node
  - devirtualized invoke
  - return type calculation

Output:

- fixture analysis paths

Validation:

- report contains expected families after build

#### Step 5.5: Add Constant/Class-Init/Method Inventory Cases

Input:

- fixture app

Action:

- add cases for:
  - constant field
  - constant parameter/return
  - class initialization mode
  - compiled method
  - inlined-only method

Output:

- full fixture coverage

Validation:

- report has at least one record/fact from each intended family

#### Step 5.6: Add Package Prefix Filter Case

Input:

- fixture app with package prefix

Action:

- run with `IDEReportFiltered`

Output:

- filtered report

Validation:

- fixture package records remain
- out-of-filter records are absent

#### Step 5.7: Add Negative Smoke

Input:

- fixture app

Action:

- build without `-H:+IDEReport`

Output:

- no report output

Validation:

- no `native_image_ide_report_*.json` is produced

### Phase 6: Repeated Local Data Collection

#### Step 6.1: Create Validation Output Root

Input:

- workspace root

Action:

- create ignored `.validation/ide-report/`
- add ignore rule if needed

Output:

- artifact directory

Validation:

- `git status --short` does not show generated validation artifacts

#### Step 6.2: Collect Fixture Five Times

Input:

- fixture build command

Action:

- run fixture five times with report enabled
- save raw reports, canonical reports, summaries, and manifest

Output:

- five fixture report sets

Validation:

- each run has raw and canonical report
- summaries are generated

#### Step 6.3: Analyze Fixture Determinism

Input:

- five fixture canonical reports

Action:

- compare all runs pairwise or against run 1
- classify instability

Output:

- determinism summary

Validation:

- report families are classified as strict or tolerant candidates

#### Step 6.4: Collect `mx helloworld` Five Times

Input:

- CE `mx helloworld` build/report command

Action:

- run five report collections
- save raw/canonical/summary/manifest

Output:

- five Hello World report sets

Validation:

- each run loads into `ReportBundle`
- no collection failures unclassified

#### Step 6.5: Analyze `mx helloworld` Determinism

Input:

- five Hello World canonical reports

Action:

- compare grouped summaries and selected records

Output:

- Hello World determinism summary

Validation:

- stable grouped assertions identified

#### Step 6.6: Collect Spring PetClinic Five Times

Input:

- Spring PetClinic build/report command

Action:

- run five report collections
- save raw/canonical/summary/manifest

Output:

- five Spring PetClinic report sets

Validation:

- each run loads into `ReportBundle`
- no collection failures unclassified

#### Step 6.7: Analyze Spring PetClinic Determinism

Input:

- five Spring PetClinic canonical reports

Action:

- compare grouped summaries and selected records

Output:

- Spring PetClinic determinism summary

Validation:

- stable grouped assertions identified

#### Step 6.8: Collect DaCapo `h2` Five Times

Input:

- CE DaCapo `h2` report command

Action:

- run five report collections
- save raw/canonical/summary/manifest

Output:

- five DaCapo report sets

Validation:

- each run loads into `ReportBundle`

#### Step 6.9: Analyze DaCapo Determinism

Input:

- five DaCapo canonical reports

Action:

- compare grouped summaries and selected records

Output:

- DaCapo determinism summary

Validation:

- stable grouped assertions identified

### Phase 7: Ground Truth And Rebase Safety Net

#### Step 7.1: Finalize Expectation Schema

Input:

- repeated-run determinism summaries
- initial expectation parser

Action:

- adjust expectation schema for observed data
- decide whether canonical SHA-256 should identify exact artifact bytes or add a
  separate semantic hash that excludes `provenance.source`
- document schema

Output:

- final initial expectation schema

Validation:

- schema covers fixture and Hello World strict assertions plus benchmark grouped
  assertions

#### Step 7.2: Write Fixture Expectations

Input:

- fixture determinism summary

Action:

- encode strict expectations for stable fixture records

Output:

- fixture expectation JSON

Validation:

- `mx ide-report assert` passes on all five fixture runs

#### Step 7.3: Write Hello World Expectations

Input:

- Hello World determinism summary

Action:

- encode strict expectations for stable Hello World records

Output:

- Hello World expectation JSON

Validation:

- `mx ide-report assert` passes on all five Hello World runs

#### Step 7.4: Write Spring PetClinic Expectations

Input:

- Spring PetClinic determinism summary

Action:

- encode medium-strength grouped and family assertions

Output:

- Spring PetClinic expectation JSON

Validation:

- expectations pass on all five Spring PetClinic runs

#### Step 7.5: Write DaCapo Expectations

Input:

- DaCapo determinism summary

Action:

- encode medium-strength grouped and family assertions

Output:

- DaCapo expectation JSON

Validation:

- expectations pass on all five DaCapo runs

#### Step 7.6: Create Baseline Collection And Validation Commands

Input:

- manual commands from Phase 6

Action:

- create repeatable commands for collecting and validating pre/post rebase
  collections

Output:

- baseline collection entry point
- baseline validation entry point

Validation:

- validation command validates all five Phase 6 runs for the fixture, Hello
  World, Spring PetClinic, and DaCapo
- collection command can reproduce a fixture smoke baseline from a clean output
  directory

#### Phase 7 Decisions

- Canonical SHA-256 identifies exact generated artifact bytes, including
  provenance. It is not the semantic rebase gate.
- Semantic ground truth is expressed as expectation JSON files under
  `substratevm/mx.substratevm/ide_report/expectations/`.
- The validation entry point is:

```bash
mx -p substratevm ide-report validate-baseline .validation/ide-report/phase6
```

- The collection entry point is:

```bash
mx -p substratevm ide-report collect-baseline .validation/ide-report/pre-rebase \
  --case fixture --case helloworld --case dacapo-h2 \
  --runs 5
```

Spring PetClinic reports are collected by an external application build recipe
and placed in the same baseline layout before `validate-baseline` runs.

- Fixture and Hello World expectations are strict.
- Spring PetClinic and DaCapo H2 expectations use stable record anchors
  plus tolerated total and `LINE` count ranges observed in Phase 6.

### Phase 8: Rebase

#### Step 8.1: Run Pre-Rebase Baseline

Input:

- baseline collection command
- expectation files

Action:

- collect baseline for fixture, Spring PetClinic, and DaCapo

Output:

- pre-rebase baseline directory

Validation:

- expectations pass before rebase

#### Step 8.2: Rebase Onto `origin/master`

Input:

- clean branch
- pre-rebase baseline

Action:

- rebase branch onto current `origin/master`

Output:

- rebased branch or conflict state

Validation:

- no unrelated worktree changes introduced

#### Step 8.3: Resolve `TypeFlowSimplifier.java`

Input:

- conflict in `TypeFlowSimplifier.java`
- conflict checklist

Action:

- preserve upstream structure
- reinsert reporting at equivalent semantic phase boundaries

Output:

- resolved file

Validation:

- fixture expectations for unreachable, return type, and devirtualization pass after build

#### Step 8.4: Resolve `ReflectionPlugins.java`

Input:

- conflict in `ReflectionPlugins.java`
- conflict checklist

Action:

- preserve upstream reflection plugin semantics
- reinsert reflection reporting point

Output:

- resolved file

Validation:

- fixture reflection expectation passes

#### Step 8.5: Resolve `NativeImageGenerator.java`

Input:

- conflict in `NativeImageGenerator.java`
- conflict checklist

Action:

- preserve report initialization, filtering, and print lifecycle

Output:

- resolved file

Validation:

- enabled, filtered, and disabled-mode fixture checks pass

#### Step 8.6: Run Build Hygiene

Input:

- rebased source

Action:

- run focused build/checkstyle for affected suites

Output:

- build/checkstyle logs

Validation:

- no compile or style failures

#### Step 8.7: Run Post-Rebase Validation

Input:

- pre-rebase baseline
- rebased branch
- expectations

Action:

- collect fixture, Spring PetClinic, and DaCapo reports
- compare against baseline and expectations

Output:

- post-rebase baseline directory
- comparison reports

Validation:

- all differences classified
- no unclassified missing required report families

### Phase 9: Gate-Scale Tests

#### Step 9.1: Add Tool Unit Tests

Input:

- synthetic samples
- reduced real fragments

Action:

- add tests for model, adapters, canonicalization, query, compare, assert

Output:

- unit test suite

Validation:

- tests pass locally

#### Step 9.2: Add Small Fixture Smoke

Input:

- custom fixture
- expectation file

Action:

- add small local/gate-eligible smoke if practical

Output:

- gate-scale validation hook

Validation:

- runs quickly
- does not invoke Spring PetClinic/DaCapo

### Phase 10: Embedded/Split Storage Preparation

Status: completed on 2026-06-27. See
`IDE_REPORT_PHASE10_RESULTS.md`. Storage option parsing, canonical export,
payload-scope selection, and envelope encoding are implemented. Phase 11 has
since enabled `split`; `embed` remains unavailable until Phase 12.

#### Step 10.1: Introduce Java Storage Options

Input:

- `IDEReport` options

Action:

- add:
  - `IDEReportStorage`
  - `IDEReportPayloadScope`
- implement option conflict validation

Output:

- new options accepted by native-image

Validation:

- valid modes parse
- invalid combinations fail clearly

#### Step 10.2: Add Canonical Payload Producer

Input:

- existing report collectors
- canonical schema

Action:

- produce canonical payload bytes from the Java report data

Output:

- canonical payload byte array

Validation:

- payload matches Python canonicalization for equivalent raw report where practical

#### Step 10.3: Implement Payload Scope Selection

Input:

- full report data

Action:

- implement `full` and `minimal` scope filtering after `IDEReportFiltered`

Output:

- scoped canonical payload

Validation:

- `full` includes method inventory facts
- `minimal` excludes method-inventory-style facts

#### Step 10.4: Implement Envelope Encoding

Input:

- canonical payload bytes

Action:

- encode envelope with magic, versions, compression, sizes, and SHA-256

Output:

- envelope bytes

Validation:

- unit tests decode envelope
- SHA-256 validates against uncompressed payload
- gzip and none paths both covered

#### Step 10.5: Implement Deterministic Compression

Input:

- canonical payload bytes

Action:

- gzip with fixed metadata and compression level
- choose `none` below threshold or when gzip does not shrink

Output:

- deterministic compressed or uncompressed payload

Validation:

- same input produces byte-identical envelope

### Phase 11: Split Storage

Status: completed on 2026-06-28. See
`IDE_REPORT_PHASE11_RESULTS.md`. The shared envelope is written to the sibling
`<image>.ide-report`, registered under `ide_report`, and loaded through the
`split:` source adapter. Full and minimal fixture validation passes, and the
decoded minimal payload is byte-identical to canonical export.

#### Step 11.1: Write `<image>.ide-report`

Input:

- envelope bytes
- image path

Action:

- write sibling split side file

Output:

- `<image>.ide-report`

Validation:

- file exists
- `split:` adapter can parse it after Step 11.3

#### Step 11.2: Register `IDE_REPORT` Artifact

Input:

- split/export paths

Action:

- add `BuildArtifacts.ArtifactType.IDE_REPORT`
- use JSON key `ide_report`
- register split/export files

Output:

- build artifacts include `ide_report`

Validation:

- schema tests pass
- generated artifact file contains expected key

#### Step 11.3: Implement `split:` Source Adapter

Input:

- `<image>.ide-report`

Action:

- decode envelope
- load canonical payload into `ReportBundle`

Output:

- `ReportBundle` from split file

Validation:

- summary matches canonical export for same scope

### Phase 12: macOS/Mach-O Embed Prototype

Status: completed on 2026-06-28. See
`IDE_REPORT_PHASE12_RESULTS.md`. Mach-O embeds the prepared envelope in the
read-only `__TEXT,__svm_idereport` section, exports `ide_report` and
`ide_report_length`, and loads through the memory-mapped `image:` adapter.
A minimal same-build `embed,split` fixture produces byte-identical envelopes.

#### Step 12.1: Identify Mach-O Section/Symbol Hook

Input:

- Native Image object-file integration points

Action:

- locate correct hook to add `.svm_ide_report`
- locate symbol creation/export mechanism

Output:

- implementation location note

Validation:

- small prototype can add section without breaking image build

#### Step 12.2: Embed Envelope Bytes

Input:

- envelope bytes
- Mach-O backend hook

Action:

- add non-writable/non-executable report section
- expose `ide_report` and `ide_report_length`

Output:

- image containing embedded report

Validation:

- section present
- symbols present
- image still runs

#### Step 12.3: Implement `image:` Adapter For Mach-O

Input:

- image with embedded report

Action:

- locate section/symbols
- extract envelope bytes
- decode into `ReportBundle`

Output:

- `ReportBundle` from image

Validation:

- `mx ide-report summarize image:/path/app` works on macOS

### Phase 13: Storage Equivalence Validation

Status: completed on 2026-06-28. See
`IDE_REPORT_PHASE13_RESULTS.md`. Separate export and embedded fixture builds
are semantically equivalent for full and minimal payload scopes. Same-build
`embed,split` envelopes are byte-identical, and real image builds validate both
deterministic gzip and uncompressed envelope paths.

#### Step 13.1: Validate Export Versus Embed

Input:

- fixture built with export
- fixture built with embed

Action:

- load both through `mx ide-report`
- compare scoped semantics

Output:

- equivalence report

Validation:

- no unexplained semantic differences

#### Step 13.2: Validate Embed Versus Split Same Build

Input:

- fixture built with `IDEReportStorage=embed,split`

Action:

- extract embedded envelope
- read split side file
- compare bytes

Output:

- byte-equivalence result

Validation:

- envelope/payload bytes are identical

#### Step 13.3: Validate Payload Scopes

Input:

- fixture builds with `full` and `minimal`

Action:

- compare record counts and report families

Output:

- scope-difference report

Validation:

- `full` includes method inventory facts
- `minimal` excludes expected method-inventory facts

#### Step 13.4: Validate Compression Modes

Input:

- small payload and larger payload

Action:

- produce reports below and above threshold

Output:

- compression validation report

Validation:

- `none` path works
- `gzip` path works

### Phase 14: Size And Performance Measurement

Status: completed on macOS/aarch64 on 2026-06-28. See
`IDE_REPORT_PHASE14_RESULTS.md` for the 27-build fixture matrix, the
nine-configuration Spring PetClinic and DaCapo H2 characterization matrices,
direct timer costs, payload-size measurements, and method-inventory
contributions.

#### Step 14.1: Measure Fixture Matrix

Input:

- fixture
- storage modes
- payload scopes

Action:

- run measurement matrix

Output:

- fixture performance manifest

Validation:

- all required metrics populated

Result:

- all 27 builds completed
- all report-enabled rows contain the expected timer families
- all same-build embedded and split envelopes are byte-identical

#### Step 14.2: Measure Spring PetClinic Matrix

Input:

- Spring PetClinic

Action:

- run dedicated measurement matrix

Output:

- Spring PetClinic performance manifest

Validation:

- method inventory size contribution recorded

Result:

- all nine rows completed from a refreshed Native Image Bundle
- the package-filtered full payload is about 326 KiB and the envelope about
  13.4 KiB
- method inventory contributes about 41 KiB of canonical JSON

#### Step 14.3: Measure DaCapo Matrix

Input:

- CE DaCapo `h2`

Action:

- run dedicated measurement matrix

Output:

- DaCapo performance manifest

Validation:

- method inventory size contribution recorded

Result:

- all nine final rows completed unfiltered because the benchmark image exposes
  no stable H2 or DaCapo report prefix
- full payload is about 28.3 MiB with a 958 KiB envelope
- minimal payload is about 20.7 MiB with a 569 KiB envelope
- method inventory contributes about 3.33 MiB of canonical JSON

### Phase 15: Linux/ELF Early Validation

Status: completed on Linux/aarch64 on 2026-06-28. See
`IDE_REPORT_PHASE15_RESULTS.md` for the custom `ni-ce` build, split and embedded
fixture evidence, ELF mapping, extraction checks, and explicit strip matrix.

#### Step 15.1: Build Linux/ELF Image With Split

Input:

- Linux/ELF environment

Action:

- run split storage build

Output:

- Linux split side file

Validation:

- `split:` works

Result:

- the split-only minimal fixture runs and loads 41 records through `split:`
- the 1,013-byte side file decodes to the expected 10,157-byte canonical
  payload
- the split-only executable contains no `.svm_ide_report` section

#### Step 15.2: Prototype Linux/ELF Embed

Input:

- Linux/ELF object-file backend

Action:

- add/extract `.svm_ide_report`

Output:

- Linux image with embedded report

Validation:

- section present
- symbols present
- extraction works
- strip behavior understood

Result:

- `.svm_ide_report` is an allocated, non-writable, non-instruction
  `PROGBITS` section with eight-byte alignment
- global object symbols `ide_report` and `ide_report_length` remain in the
  dynamic symbol table
- `image:` loads the real ELF image and the embedded envelope is byte-identical
  to the same-build split file
- the fixture executes before and after `objcopy --strip-debug` and
  `objcopy --strip-all`; section, symbols, and extraction survive both

### Phase 16: Final Review And PR Preparation

Status: completed on 2026-06-28. See `IDE_REPORT_PHASE16_RESULTS.md` for the
resolved review findings, fresh fixture/storage matrix, extended validation,
public repository hygiene, reviewable commit structure, and deferred
production work.

#### Step 16.1: Update Design Docs From Reality

Input:

- implementation results
- measurement manifests

Action:

- update design docs where implementation differs from plan

Output:

- accurate design docs

Validation:

- no stale open questions that were answered by implementation

#### Step 16.2: Prepare Reviewable Commit Structure

Input:

- implemented changes

Action:

- split commits by review surface:
  - tooling model/canonicalization
  - fixture/tests
  - rebase conflict resolution
  - storage options/envelope
  - split storage
  - embed storage
  - build artifact schema

Output:

- reviewable commit stack

Validation:

- each commit builds or is clearly paired with the next where unavoidable

#### Step 16.3: Run Final Validation

Input:

- final branch

Action:

- run gate-scale tests
- run extended validation
- run storage equivalence checks

Output:

- final validation manifest

Validation:

- no unclassified regressions
- all required report families covered
- known deferred items documented
