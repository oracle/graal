# IDE Report Phase 6 Results

Phase 6 collected repeated Native Image IDE reports before rebasing so the
validation safety net can be based on observed determinism rather than
byte-for-byte assumptions.

## Scope

Each application was collected five times. For each run, the validation artifacts
under `.validation/ide-report/phase6/` contain:

- raw `native_image_ide_report_*.json`
- canonical report JSON
- SHA-256 file for the canonical bytes
- summary JSON
- run manifest
- comparison against run 1 for runs 2 through 5

The validation artifacts are intentionally ignored by Git because they include
large generated reports and build logs.

## Commands Used

Focused fixture:

```bash
mx -p substratevm ide-report-fixture \
  --output-path .validation/ide-report/phase6/fixture/runs/run-XX \
  --build-only \
  --ide-report-filter com.oracle.svm.test.ide
```

Hello World:

```bash
mx -p substratevm helloworld \
  --output-path .validation/ide-report/phase6/helloworld/runs/run-XX \
  --build-only -- \
  -H:+UnlockExperimentalVMOptions \
  -H:+IDEReport \
  -H:IDEReportFiltered=HelloWorld \
  -H:-UnlockExperimentalVMOptions
```

DaCapo H2:

```bash
mx -p substratevm benchmark dacapo-native-image:h2 \
  --results-file .validation/ide-report/phase6/dacapo-h2/runs/run-XX/bench-results.json \
  -- --no-scratch --jvm=native-image --jvm-config=default-ce \
  -Dnative-image.benchmark.stages=image \
  -Dnative-image.benchmark.extra-image-build-argument=-H:+IDEReport --
```

Spring PetClinic reports were collected with an external application build
recipe and then canonicalized with `mx ide-report`. The repository intentionally
keeps the expectation data independent of that environment-specific recipe.

## Summary

| Application | Filter | Reports | Used methods | Stable grouped counts | Comparator result |
| --- | --- | ---: | ---: | --- | --- |
| Focused fixture | `com.oracle.svm.test.ide` | 55 each run | 18 each run | yes | no missing/added/changed records |
| Hello World | `HelloWorld` | 3 each run | 1 each run | yes | no missing/added/changed records |
| DaCapo H2 | none | 75,291 to 75,479 | 14,889 each run | mostly, except `LINE` count | line-level differences |
| Spring PetClinic | `org.springframework.samples.petclinic` | 907 to 909 | 141 each run | mostly, except `LINE` count | small line-level differences |

## Determinism Notes

The focused fixture is semantically deterministic under the comparator. All five
runs had identical report counts, grouped counts, used method counts, and no
record-level comparator differences. This is a good candidate for strict Phase 7
expectations.

Hello World is also semantically deterministic under the comparator. It gives a
middle step between the focused fixture and benchmark applications while still
being cheap enough to run locally.

DaCapo H2 is not semantically identical run-to-run at line-report granularity.
The stable facts are the used method count and grouped counts for `CLASS`,
`FIELD`, `METHOD`, and `UNREACHABLE`. The `LINE` count moved from 64,815 to
65,003 across the five runs, and comparisons against run 1 showed added,
missing, and changed `LINE` records. Phase 7 should use tolerant benchmark
assertions for DaCapo, probably grouped counts and selected stable records rather
than exact full-record matching.

Spring PetClinic is close but not fully deterministic at line-report
granularity. The used method count was stable at 141, `CLASS` and `METHOD`
counts were stable, and `LINE` records varied slightly. Comparisons against run
1 showed 2 missing line records and 4 to 7 changed line records. Phase 7 should
use grouped assertions and selected stable records rather than exact full-record
matching.

## Follow-Up For Phase 7

The canonical SHA-256 files are useful for identifying exact generated artifact
bytes, but they are not currently semantic stability checks. The canonical JSON
contains `provenance.source`, including the per-run raw report path and timestamped
filename, so hashes differ even when the comparator reports zero semantic
differences. Phase 7 should either add a semantic hash that excludes provenance
or adjust canonical hashing if the intended hash is semantic rather than
artifact-identifying.

The initial DaCapo H2 collection used the filter `org.h2,org.dacapo`, but that
produced empty reports. The final collected DaCapo H2 data is therefore
unfiltered.
