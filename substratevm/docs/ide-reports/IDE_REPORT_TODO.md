# IDE Report TODO

This file is the cross-session tracker for the Native Image IDE report work.
Use it together with:

- `IDE_REPORT_EXECUTION_PLAN.md` for the detailed step-by-step plan.
- `IDE_REPORT_REBASE_VALIDATION_DESIGN.md` for rebase validation details.
- `IDE_REPORT_EMBEDDED_STORAGE_DESIGN.md` for export/embed/split storage design.
- `IDE_REPORT_PHASE6_RESULTS.md` for repeated baseline collection results.
- `IDE_REPORT_PHASE7_RESULTS.md` for semantic expectation files and the baseline
  validation command.
- `IDE_REPORT_PHASE8_RESULTS.md` for the completed rebase, post-rebase
  validation, and benchmark setup correction.
- `IDE_REPORT_PHASE9_RESULTS.md` for the gate-scale tests and focused fixture
  smoke.
- `IDE_REPORT_PHASE10_RESULTS.md` for storage options, canonical payloads,
  payload scopes, envelope encoding, correctness fixes, and focused validation.
- `IDE_REPORT_PHASE11_RESULTS.md` for split side-file storage, `split:` loading,
  artifact registration, and full/minimal validation.
- `IDE_REPORT_PHASE12_RESULTS.md` for Mach-O embedded storage, `image:` loading,
  locator symbols, and embed/split byte validation.
- `IDE_REPORT_PHASE13_RESULTS.md` for the full/minimal storage-equivalence
  matrix, scope projection, and real gzip/uncompressed envelope validation.
- `IDE_REPORT_PHASE14_RESULTS.md` for the completed fixture, Spring PetClinic,
  and DaCapo H2 size/performance matrices.
- `IDE_REPORT_PHASE15_RESULTS.md` for Linux/ELF split and embedded storage,
  extraction, symbols, and strip behavior.
- `IDE_REPORT_PHASE16_RESULTS.md` for final review, validation, public hygiene,
  commit structure, and deferred production work.
- `IDE_REPORT_PR_REVIEW.md` for the post-Phase-16 deep review, merge blockers,
  code-quality assessment, and prioritized follow-up plan.
- `IDE_REPORT_JBANG_READER.md` for the standalone Java reference reader,
  supported contract, commands, and validation.

## Current Rule

The rebase validation safety net now exists and must remain usable before
changing the report surface:

- tooling home created
- report loader working
- canonicalizer working
- comparator working
- focused fixture created
- repeated baseline data collected
- ground truth and acceptable variance documented

## Current Status

- [x] Rebase validation design drafted.
- [x] Embedded/split storage design drafted.
- [x] Detailed execution plan drafted.
- [x] Phase 0 completed: current repository state and baseline metadata captured.
- [x] Phase 1 completed: `mx ide-report` tooling skeleton created.
- [x] Phase 2 completed: raw JSON loading, source parsing, `summarize`, and `query`.
- [x] Phase 3 completed: canonical JSON output, deterministic ordering, SHA-256 generation,
  and `canonical:` source loading.
- [x] Phase 4 completed: report identity, duplicate-aware comparison, grouped summaries,
  expectation parsing, and `assert`.
- [x] Phase 5 completed: focused `mx ide-report-fixture` coverage now exercises
  reflection, unreachable branches, devirtualization, return type calculation,
  constant field/parameter facts, class initialization mode, compiled/used method
  inventory, inlined-only methods, package filtering, and a no-report negative
  smoke.
- [x] Phase 6 completed: repeated five-run collections finished for the focused
  fixture, Hello World, DaCapo H2, and Spring PetClinic. Fixture and
  Hello World are semantically stable under the comparator. DaCapo and Spring PetClinic
  need tolerant benchmark assertions based on grouped counts and selected stable
  records.
- [x] Phase 7 completed: expectation files now define semantic ground truth for
  the focused fixture, Hello World, Spring PetClinic, and DaCapo H2.
  `mx ide-report collect-baseline` collects the built-in fixture, Hello World,
  and DaCapo cases. `mx ide-report validate-baseline` also validates externally
  collected Spring PetClinic directories against their expectations. Canonical
  SHA-256 remains an exact artifact identifier rather than the semantic gate.
- [x] Phase 8 completed: branch rebased onto current `origin/master`; conflicts
  resolved; Spring PetClinic collection corrected to run from the VM suite with
  `--env ni-ce`; post-rebase five-run baselines for fixture, Hello World,
  Spring PetClinic, and DaCapo H2 all pass semantic validation.
- [x] Phase 9 completed: added focused Python unit coverage for CLI and
  baseline helpers, plus `mx ide-report smoke-fixture` as a gate-scale fixture
  validation command that does not invoke Spring PetClinic or DaCapo.
- [x] Phase 10 completed: added build-scoped collection, storage and scope
  options, semantic categories, canonical Java payload production,
  deterministic envelope encoding, and focused Java/Python/native-image
  validation.
- [x] Phase 11 completed: `split` writes the shared envelope to
  `<image>.ide-report`, registers the side file under `ide_report`, and loads it
  through the storage-neutral `split:` adapter. Full and minimal fixture builds
  pass, and the minimal decoded payload is byte-identical to canonical export.
- [x] Phase 12 completed: Mach-O embeds the shared envelope in the read-only
  `__TEXT,__svm_idereport` section, exports locator symbols, and loads through
  the memory-mapped `image:` adapter. A same-build minimal `embed,split` fixture
  produces byte-identical embedded and split envelopes.
- [x] Phase 13 completed: separate export/embed fixture builds are semantically
  equivalent for full and minimal scopes, same-build embedded and split
  envelopes are byte-identical, and real images exercise both compression
  paths.
- [x] Phase 14 completed: IDE-report-specific timers and reproducible fixture
  and benchmark drivers collected 27 fixture builds plus nine-configuration
  Spring PetClinic and DaCapo H2 matrices. Full/minimal payload costs, method
  inventory contributions, executable deltas, and direct finalization costs
  are recorded in `IDE_REPORT_PHASE14_RESULTS.md`.
- [x] Phase 15 completed: Linux/ELF split and embedded storage pass on a custom
  current-branch `ni-ce` distribution. The allocated `.svm_ide_report` section,
  exported locator symbols, memory-mapped extraction, same-build split-byte
  identity, execution, and `strip-debug`/`strip-all` behavior are validated.
- [x] Phase 16 completed: the implementation and design passed deep review,
  confirmed gaps were fixed, final gate-scale and extended validation passed,
  public repository surfaces were sanitized, and the branch was prepared as a
  six-commit reviewer-oriented stack.
- [x] Post-Phase-16 PR deep review completed: the prototype remains validated,
  but collector ownership, inlining integration, and the production-readiness
  gaps in `IDE_REPORT_PR_REVIEW.md` must be addressed before merge.
- [x] Collector ownership review follow-up completed: report state now lives in
  the hosted `ImageSingletons` registry, compiler access uses a stateless SPI,
  and focused lifecycle coverage verifies registry cleanup.
- [x] A standalone Java/JBang reference reader now parses canonical JSON and
  split envelopes through a bounded `ByteBuffer` API, with fixed
  cross-language vectors and focused command-line tests.

## Branch Context

- Tracking id: GR-61707.
- Public GitHub mirror PR: https://github.com/oracle/graal/pull/12586.
- Branch: `vaebi/GR-61707/native-image-ide-reports`.
- Source of truth: `origin/vaebi/GR-61707/native-image-ide-reports`.
- Phase 16 completed final review, validation, and PR preparation on
  2026-06-28.

## Next Action

The prototype phases are complete, but the PR is not merge-ready. Collector
ownership now follows the hosted image-singleton lifecycle. Complete the
remaining standard inlining-integration blocker described in
`IDE_REPORT_PR_REVIEW.md`, then address output-failure semantics, bounded
payload decoding, semantic class subjects, and missing integration tests before
starting final CI gates. No CI gates are started automatically.

## Phase Checklist

- [x] Phase 0: Hygiene and current-state capture.
- [x] Phase 1: Create IDE report tooling home and CLI skeleton.
- [x] Phase 2: Implement raw JSON report loading and query commands.
- [x] Phase 3: Implement canonicalization.
- [x] Phase 4: Implement comparator and expectation format.
- [x] Phase 5: Create focused IDE report fixture.
- [x] Phase 6: Collect repeated baseline reports.
- [x] Phase 7: Define ground truth and rebase safety net.
- [x] Phase 8: Rebase onto current `origin/master`.
- [x] Phase 9: Add gate-scale tests.
- [x] Phase 10: Prepare embedded/split storage architecture.
- [x] Phase 11: Implement split storage artifact.
- [x] Phase 12: Implement macOS/Mach-O embedding prototype.
- [x] Phase 13: Validate export/split/embed equivalence.
- [x] Phase 14: Measure report size and build cost.
- [x] Phase 15: Validate Linux/ELF support early.
- [x] Phase 16: Final review and PR preparation.

## Validation Applications

Use repeated runs before defining ground truth:

- focused local fixture: five runs
- Hello World: five runs
- Spring PetClinic: five runs
- DaCapo H2: five runs

Only the focused fixture and tooling tests are candidates for regular gates.
Spring PetClinic and DaCapo remain extended validation.

## Storage Decisions

- `export`: developer/CI-facing standalone report output.
- `embed`: image-attached IDE report metadata on macOS/Mach-O and Linux/ELF.
- `split`: same envelope/payload as embedded storage, externalized as `<image>.ide-report`.
- Prototype payload scope defaults to full.
- Future production-candidate payload scope should likely be minimal.
- Reports may reveal internal program details; confidentiality policy is deferred to later project phases.

## Resume Checklist

At the start of a new development session:

1. Read this file first.
2. Read `IDE_REPORT_EXECUTION_PLAN.md` for the active phase details.
3. Check `git status --short` from the Graal repository root.
4. For branch provenance, check `git status --porcelain=v1 -b` and
   `git log --oneline --left-right --cherry-pick \
   vaebi/GR-61707/native-image-ide-reports...origin/vaebi/GR-61707/native-image-ide-reports`.
5. Continue from the first unchecked phase item above.
