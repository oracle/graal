# IDE Report Phase 7 Results

Phase 7 converts the repeated Phase 6 report collections into a rebase safety
net. The ground truth is intentionally semantic and tolerant where the data
showed run-to-run variation.

## Expectation Files

The expectation files live in:

```text
substratevm/mx.substratevm/ide_report/expectations/
```

Current files:

- `fixture.json`: strict counts and representative records for the focused
  fixture.
- `helloworld.json`: strict counts and all three Hello World records.
- `spring-petclinic.json`: stable class/method anchors, one stable line
  anchor, exact stable group counts, and a tolerated total/`LINE` count range.
- `dacapo-h2.json`: stable class/field/method anchors, exact stable group
  counts, and a tolerated total/`LINE` count range.

The schema now supports:

- expected records with exact or regex message matching
- exact, range, delta, or ignored line matching
- grouped report counts by `kind`
- total report count checks
- `used_methods` count checks

## SHA-256 Decision

The canonical SHA-256 files remain exact artifact identifiers. They include
canonical provenance, including the raw report path, so they are expected to
differ between repeated runs even when the report is semantically stable.

The rebase safety net therefore uses expectation files and comparator output,
not SHA equality, as the semantic ground truth. A separate semantic hash can be
added later if exact machine-comparable semantic fingerprints become useful.

## Validation Command

Collect a new repeated baseline:

```bash
mx -p substratevm ide-report collect-baseline .validation/ide-report/pre-rebase \
  --case fixture --case helloworld --case dacapo-h2 \
  --runs 5
```

Spring PetClinic reports use the same directory and expectation contracts but
are supplied by an external application build recipe.

For a cheap local smoke, collect only the fixture:

```bash
mx -p substratevm ide-report collect-baseline .validation/ide-report/smoke \
  --case fixture --runs 1
```

Use `--java-home <path>` only when the active shell or `mx` configuration does
not already select the intended JDK.

Validate all Phase 6 canonical runs against the stored expectations:

```bash
mx -p substratevm ide-report validate-baseline .validation/ide-report/phase6
```

Validate one case only:

```bash
mx -p substratevm ide-report validate-baseline .validation/ide-report/phase6 \
  --case fixture
```

The baseline directory must use the layout produced by Phase 6:

```text
.validation/ide-report/<phase>/<case>/runs/run-XX/canonical.json
```

## Rebase Use

Before rebasing, collect or reuse a pre-rebase baseline and run
`validate-baseline`. After rebasing and resolving conflicts, collect the same
applications again and run the same validator. Comparator diffs should be used
for investigation when an expectation fails or benchmark counts move outside
the observed Phase 6 ranges.

Do not treat the benchmark expectation files as gate-quality tests. They are an
extended validation safety net for this rebase and future report-surface work.
