# Native Image IDE Reports

This directory contains design and execution notes for the Native Image IDE
reports work.

The files here are the repository copies intended to travel with the branch
across development sessions and machines. When continuing the work, start with:

1. `IDE_REPORT_TODO.md`
2. `IDE_REPORT_PR_REVIEW.md`
3. `IDE_REPORT_EXECUTION_PLAN.md`
4. `IDE_REPORT_REBASE_VALIDATION_DESIGN.md`
5. `IDE_REPORT_EMBEDDED_STORAGE_DESIGN.md`

## Files

- `IDE_REPORT_TODO.md`: cross-session TODO and active phase tracker.
- `IDE_REPORT_PR_REVIEW.md`: dated deep review of implementation quality,
  merge blockers, residual risks, validation evidence, and recommended next
  work.
- `IDE_REPORT_EXECUTION_PLAN.md`: detailed plan with inputs, outputs, and
  validation for each step.
- `IDE_REPORT_REBASE_VALIDATION_DESIGN.md`: validation design for rebasing the
  current IDE report branch safely.
- `IDE_REPORT_EMBEDDED_STORAGE_DESIGN.md`: design for export, embed, and split
  report storage.
- `IDE_REPORT_PHASE6_RESULTS.md`: repeated baseline collection results.
- `IDE_REPORT_PHASE7_RESULTS.md`: semantic ground-truth expectations and
  baseline validation command.
- `IDE_REPORT_PHASE8_RESULTS.md`: completed rebase and post-rebase validation
  results.
- `IDE_REPORT_PHASE9_RESULTS.md`: gate-scale unit tests and focused fixture
  smoke command.
- `IDE_REPORT_PHASE10_RESULTS.md`: storage options, canonical payloads, payload
  scopes, and deterministic envelope preparation.
- `IDE_REPORT_PHASE11_RESULTS.md`: split side-file storage, `split:` loading,
  artifact registration, and full/minimal validation.
- `IDE_REPORT_PHASE12_RESULTS.md`: Mach-O embedded storage, `image:` loading,
  exported locator symbols, and embed/split byte validation.
- `IDE_REPORT_PHASE13_RESULTS.md`: full/minimal storage equivalence, scope
  projection, and real gzip/uncompressed envelope validation.
- `IDE_REPORT_PHASE14_RESULTS.md`: fixture, Spring PetClinic, and DaCapo H2
  size/performance matrices and payload-scope conclusions.
- `IDE_REPORT_PHASE15_RESULTS.md`: Linux/ELF split and embedded storage,
  extraction, locator symbols, and strip validation.
- `IDE_REPORT_PHASE16_RESULTS.md`: final review, public hygiene, validation
  manifest, reviewable history, and deferred production work.

## Rule Of Engagement

The rebase validation safety net and split storage now exist. Future work
should keep this safety net usable before changing the report surface:

- tooling home created
- report loader working
- canonicalizer working
- comparator working
- focused fixture created
- repeated baseline data collected
- ground truth and acceptable variance documented
