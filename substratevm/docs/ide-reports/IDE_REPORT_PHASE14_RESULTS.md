# IDE Report Phase 14 Results

Date: 2026-06-28

Phase 14 measures IDE report size and build cost for the focused fixture,
Spring PetClinic, and DaCapo H2. All measurements use Native Image
from the current feature branch on macOS 26.5.1/aarch64 with LabsJDK 25.1 b19.

## Measurement Method

The matrix contains these nine configurations:

```text
reporting disabled
legacy export
canonical export
embed full
embed minimal
split full
split minimal
embed,split full
embed,split minimal
```

The focused fixture uses three runs per configuration and reports medians.
Spring PetClinic and DaCapo H2 use one characterization run per configuration.
The application filters are:

- fixture: `com.oracle.svm.test.ide`
- Spring PetClinic: `org.springframework.samples.petclinic`
- DaCapo H2: unfiltered, because the benchmark image contains no report source
  paths or used-method classes under an H2 or DaCapo prefix

The checked-in drivers write ignored local manifests under
`/tmp/gr61707-phase14-final`. Raw benchmark outputs and report payloads are not
committed.

### Reproduction

Build the current Substrate VM distribution, then run the fixture matrix from
the `substratevm` suite:

```bash
mx --java-home=lookup:default build --dependencies=GRAALVM_5D3782F34A_JAVA25
python3 mx.substratevm/ide_report/phase14_measure_fixture.py
```

The checked-in application driver covers DaCapo H2. Spring PetClinic was
measured with an external application build recipe and the same storage matrix;
the raw environment-specific recipe is intentionally not part of this public
repository.

```bash
python3 mx.substratevm/ide_report/phase14_measure_benchmarks.py dacapo-h2
```

The fixture driver requires a fresh output directory and accepts
`--output-directory`. The application driver accepts `--output-root` and
resumes configurations that already contain `measurement.json`; always select
a fresh output location when measuring another commit.

Measured build values come from `build-output-final.json` and
`image_build_statistics-final.json`. IDE-report-specific timers cover:

```text
ide-report-snapshot
ide-report-serialization
ide-report-compression
ide-report-legacy-write
ide-report-canonical-write
ide-report-split-write
ide-report-embedding
```

Snapshot timing covers final collection materialization. Online report-event
recording occurs throughout analysis; its cost is represented only in the
analysis and whole-build measurements because timing every hot-path event would
perturb the workload being measured.

## Focused Fixture

The fixture completed all 27 builds. Every report-enabled row contains its
expected timer keys, and all six `embed,split` runs produced byte-identical
embedded and split envelopes.

These rows characterize the exact report sets produced during Phase 14. A
fresh Phase 16 gate run produces the current semantic ground truth of 68
reports and 24 used methods; its full/minimal payloads are 24,733/10,157 bytes
and its envelopes are 1,730/1,011 bytes. Use the Phase 16 values for current
correctness and the table below only for the recorded performance experiment.

| Configuration | Total s | Analysis ms | Peak RSS bytes | Image delta bytes | Payload bytes | Envelope bytes |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| disabled | 43.779 | 12,862 | 2,830,467,072 | 0 | - | - |
| legacy export | 77.397 | 19,818 | 3,342,811,136 | 0 | 19,154 | - |
| canonical export | 60.488 | 19,832 | 3,241,656,320 | 0 | 19,154 | - |
| embed full | 71.088 | 19,397 | 3,505,586,176 | 96 | 19,154 | 1,549 |
| embed minimal | 50.524 | 16,431 | 3,160,014,848 | 96 | 8,168 | 919 |
| split full | 48.609 | 12,691 | 3,166,846,976 | 0 | 19,154 | 1,549 |
| split minimal | 41.425 | 11,951 | 3,035,168,768 | 0 | 8,168 | 919 |
| embed,split full | 40.107 | 11,635 | 3,086,024,704 | 96 | 19,154 | 1,549 |
| embed,split minimal | 36.348 | 10,232 | 3,083,370,496 | 96 | 8,168 | 919 |

The fixture's direct report-finalization work is small: snapshotting takes
0-2 ms, canonical serialization normally takes 2-15 ms, compression takes
1-3 ms, and embedding or writing takes 0 ms at integer-millisecond resolution.
The large spread and negative apparent whole-build deltas in later rows show
that sequential fixture builds are affected by machine and cache variance.
Whole-build fixture deltas therefore are not causal overhead estimates.

Full scope contains 54 records and 18 used methods. Minimal scope contains 33
records and no used methods. Minimal reduces the canonical payload by 57.4%
and the compressed envelope by 40.7%.

## Spring PetClinic

Spring PetClinic was rebuilt with the current source distribution before the
final matrix. Only image-build stages were measured.

| Configuration | Total s | Analysis ms | Peak RSS bytes | Image delta bytes | Payload bytes | Envelope bytes |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| disabled | 75.605 | 18,469 | 9,376,088,064 | 0 | - | - |
| legacy export | 77.732 | 20,058 | 10,085,908,480 | -16,512 | 326,324 | - |
| canonical export | 76.105 | 19,811 | 10,135,011,328 | 0 | 326,079 | - |
| embed full | 72.421 | 18,529 | 10,193,960,960 | 112 | 326,324 | 13,434 |
| embed minimal | 72.671 | 18,238 | 9,973,022,720 | 96 | 263,636 | 10,170 |
| split full | 72.239 | 18,299 | 10,172,219,392 | 0 | 326,079 | 13,410 |
| split minimal | 70.388 | 17,516 | 10,861,019,136 | -16,512 | 263,881 | 10,190 |
| embed,split full | 72.051 | 18,405 | 10,139,467,776 | 112 | 326,324 | 13,434 |
| embed,split minimal | 66.986 | 16,100 | 10,041,655,296 | 16,608 | 263,636 | 10,170 |

Report finalization is small relative to this build: snapshotting takes 2 ms,
serialization takes 19-23 ms, deterministic gzip takes 6-24 ms, the legacy
write takes 6 ms, and canonical/split writes and embedding round to 0 ms.

Full scope contains 877-878 records and 141 used methods. Minimal scope
contains 817-818 records and no used methods. Minimal reduces the canonical
payload by 19.2% and the compressed envelope by 24.3%. Separate builds vary by
one return-value record, consistent with the tolerant benchmark comparison
policy established in Phase 7.

Executable sizes also vary by about 16 KiB between some non-embedded rows.
Because this is a one-run characterization, only the same-layout full embed
and `embed,split` rows provide a stable direct signal: both add 112 bytes.

## DaCapo H2

The first DaCapo attempt used an `org.h2` filter and correctly produced an
empty payload. Inspection of the established Phase 8 report found no H2 or
DaCapo source paths or used-method classes. The final matrix is therefore
unfiltered, matching the existing validation workflow and exercising a large
real report.

| Configuration | Total s | Analysis ms | Peak RSS bytes | Image delta bytes | Payload bytes | Envelope bytes |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| disabled | 15.731 | 3,092 | 1,551,777,792 | 0 | - | - |
| legacy export | 15.164 | 3,198 | 1,922,514,944 | 0 | 28,332,973 | - |
| canonical export | 18.196 | 3,302 | 2,176,401,408 | 0 | 28,334,786 | - |
| embed full | 18.739 | 3,878 | 2,160,312,320 | 957,792 | 28,327,572 | 958,288 |
| embed minimal | 17.065 | 3,081 | 1,898,283,008 | 578,016 | 20,694,974 | 568,694 |
| split full | 18.140 | 2,993 | 2,156,167,168 | 0 | 28,332,794 | 958,612 |
| split minimal | 16.811 | 3,006 | 1,835,663,360 | 0 | 20,695,327 | 568,655 |
| embed,split full | 18.244 | 3,030 | 2,150,268,928 | 957,792 | 28,336,749 | 958,922 |
| embed,split minimal | 16.729 | 3,014 | 1,884,995,584 | 578,016 | 20,694,934 | 568,663 |

The direct timers explain most of the positive whole-build delta:

- full snapshot: 76-83 ms
- full serialization: 2,414-2,646 ms
- full compression: 345-352 ms
- minimal snapshot: 78-88 ms
- minimal serialization: 1,747-1,911 ms
- minimal compression: 263-266 ms
- canonical file write: 15 ms
- split write: 0-1 ms
- embedding: 0 ms

Full scope contains about 71,800 records and 15,431 used methods. Minimal scope
contains about 58,600 records and no used methods. Minimal reduces the
canonical payload by 26.9%, the compressed envelope by 40.7%, and the embedded
image delta by 39.6%.

## Method Inventory Cost

The byte contributions below are calculated by re-encoding the same canonical
payload after removing each fact family. They are logical JSON contributions,
not independently compressed-envelope deltas.

| Application | Used methods | Used-method bytes | Compiled methods | Compiled bytes | Inlined-only methods | Inventory bytes | Inlined-only record bytes |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| fixture | 18 | 4,038 | 9 | 2,088 | 9 | 1,948 | 3,481 |
| Spring PetClinic | 141 | 40,972 | 130 | 37,603 | 11 | 3,367 | 5,363 |
| DaCapo H2 | 15,431 | 3,334,994 | 9,116 | 1,981,371 | 6,315 | 1,353,621 | 2,443,455 |

For DaCapo, method inventory accounts for 11.8% of the full canonical payload.
The larger full-to-minimal difference also includes class-initialization,
constant-field, parameter-value, and inlined-only report records.

## Extraction Cost

Mach-O envelope extraction was sampled 25 times for each embedded image.
DaCapo medians were 0.28-0.34 ms and Spring PetClinic was normally 0.33-0.34
ms. Some fixture and one Spring PetClinic sample ran under sustained machine
load and reported 4-7 ms medians; their minimum samples remained below 0.6 ms.
This operation locates and copies the envelope bytes. Payload decompression and
JSON parsing are outside this extraction timer.

## Conclusions

- Split storage has no executable-size impact and its file size equals the
  envelope size.
- Embedded image growth closely tracks the envelope once the payload is large
  enough to exceed existing alignment slack.
- Serialization dominates post-analysis report cost. Compression is secondary;
  snapshotting and output writes are comparatively small.
- Minimal scope is the stronger production candidate. Its effect is modest for
  package-filtered Spring PetClinic but material for the large unfiltered
  DaCapo report.
- Full scope remains useful for measurement and development because it exposes
  the actual cost of method inventories and non-minimal fact families.
- Whole-build and analysis deltas from single application runs are
  characterization data, not statistically rigorous performance claims.

## Validation

- all 45 fixture and application builds in the final matrices completed
- all 24 report-enabled fixture rows and all 16 report-enabled application rows
  contain their expected IDE-report timer families
- all same-build `embed,split` envelopes are byte-identical
- every envelope decodes, validates its SHA-256, and re-encodes to the loaded
  canonical `ReportBundle`
- both full and minimal scopes are populated for all three applications
- both application manifests record method-inventory byte contributions

Repository validation completed after collecting and documenting the results:

- Python compilation and repository-pinned formatting pass
- all 38 `ide_report.test_ide_report` tests pass
- all 13 focused `IDEReportTest` and `IDEReportStorageTest` Java tests pass
- the enabled/disabled end-to-end fixture smoke passes
- Eclipse formatting changes zero files and Checkstyle passes
- forced ECJ and javac warning-as-error builds of `com.oracle.svm.hosted` pass
- documentation references, private-path scan, and `git diff --check` pass

Phase 14 is complete. Phase 15 starts Linux/ELF split validation and the first
ELF embedding prototype.
