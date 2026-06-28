# IDE Report Phase 8 Results

Phase 8 rebased the IDE report branch onto current `origin/master`, resolved
conflicts, rebuilt the local CE VM for benchmark validation, and recollected the
post-rebase validation baselines.

## Rebase

The branch was rebased onto:

```text
25fdc04abe6 [GR-53330] Fix a broken link.
```

The rebase stopped in the original IDE report implementation commit and required
manual conflict resolution in:

- `substratevm/src/com.oracle.graal.pointsto/src/com/oracle/graal/pointsto/results/TypeFlowSimplifier.java`
- `substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/NativeImageGenerator.java`
- `substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/snippets/ReflectionPlugins.java`

The resolved code preserves the IDE report hooks while keeping the upstream
`GuestAccess`, `ImageKindInfoSingleton`, shared option, and shared utility
changes from master.

One post-rebase import fix was also required:

- `IDEReportingFeature` now imports
  `com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature`.

## Benchmark Setup Correction

The Phase 6 Spring PetClinic collection was useful as comparator input, but it
depended on stale local build state. The post-rebase collection rebuilt the
application with the current source distribution before collecting reports.
The repository retains the semantic expectation file and validation support,
but intentionally does not encode an environment-specific application build
recipe.

## Post-Rebase Baseline

The post-rebase baseline is stored locally under:

```text
.validation/ide-report/phase8-post-rebase
```

It contains five runs each for:

- focused fixture
- Hello World
- Spring PetClinic
- DaCapo H2

Validation command:

```bash
mx -p substratevm ide-report validate-baseline \
  .validation/ide-report/phase8-post-rebase \
  --case fixture --case helloworld --case spring-petclinic --case dacapo-h2
```

Result: all 20 canonical reports passed the semantic expectation checks.

## Observed Counts

Focused fixture remained stable:

- reports: 55
- used methods: 18
- grouped counts: `CLASS=8`, `FIELD=1`, `LINE=36`, `METHOD=9`,
  `UNREACHABLE=1`

Hello World remained stable:

- reports: 3
- used methods: 1
- grouped counts: `CLASS=1`, `LINE=2`

Spring PetClinic moved after rebuilding the application with the current
source distribution:

- reports: 898-901
- used methods: 141
- grouped counts: `CLASS=38`, `LINE=849-852`, `METHOD=11`
- run-to-run comparator differences were `LINE`-only

DaCapo H2 moved after rebasing onto current master:

- reports: 72889-73013
- used methods: 15434
- grouped counts: `CLASS=3252`, `FIELD=731`, `LINE=61941-62065`,
  `METHOD=6315`, `UNREACHABLE=650`
- run-to-run comparator differences were `LINE`-only
- the stable `CommonOptionParser` anchor moved from
  `com.oracle.svm.common.option` to `com.oracle.svm.shared.option`

The benchmark expectation files were updated to the observed post-rebase
semantic baselines. They remain extended validation data, not gate-quality tests.

## Hygiene

Checks completed after the rebase conflict resolution, collector correction, and
expectation updates:

- Python compile for `substratevm/mx.substratevm/ide_report/*.py`
- Python unit tests for `ide_report.test_ide_report`
- `mx -p substratevm --strict-compliance eclipseformat --primary`
- `mx -p substratevm --strict-compliance pyformat`
- `mx -p substratevm --strict-compliance checkstyle`
- ECJ warning-as-error build for
  `jdk.graal.compiler,com.oracle.graal.pointsto,com.oracle.svm.hosted`
- javac warning-as-error build for
  `jdk.graal.compiler,com.oracle.graal.pointsto,com.oracle.svm.hosted`
- full post-rebase baseline validation for all four validation applications
