# IDE Report Phase 10 Results

Date: 2026-06-27

Phase 10 prepares the storage-neutral payload and envelope needed by the later
split and embedded-storage phases. The implementation is contained in this
repository.

## Implemented

- Added `-H:IDEReportStorage=<off|export|embed|split|...>` and
  `-H:IDEReportPayloadScope=<full|minimal>` with explicit conflict and
  combination validation.
- Preserved `-H:+IDEReport` as the legacy timestamped JSON export path.
- Added canonical UTF-8 JSON export for explicit `IDEReportStorage=export`.
- Added stable semantic categories to every report record so payload selection
  does not depend on message text.
- Added `full` and `minimal` payload selection after `IDEReportFiltered`.
  `minimal` retains reflection, inlining, unreachable-code, devirtualization,
  and return-value facts and omits method inventory.
- Added a deterministic versioned envelope implementation in Java and Python,
  including SHA-256, `none` and deterministic gzip compression, and strict
  decoding.
- Added the `ide_report` build-artifact type and schema key for canonical
  exports and future split artifacts.
- Made IDE report collection build-scoped and repeatable, and made report write
  failures non-fatal to an otherwise successful image build.
- Corrected analysis reporting issues found during review: engine-specific
  field casts, heterogeneous return-value overclaiming, missing source-position
  guards, and incorrect inlinee attribution.
- Extended comparison and expectations to cover semantic categories, forbidden
  records, and the `used_methods` inventory.

`embed` and `split` are parsed and validated in Phase 10 but intentionally fail
with a clear not-implemented diagnostic. Phase 11 will implement split output;
Phase 12 will implement the first embedded-storage prototype.

## Canonical Payload Contract

Canonical payloads are UTF-8 JSON with a trailing newline and these stable
top-level fields:

```text
extensions
payload_scope
records
schema_version
used_methods
```

Object keys and record lists are deterministic. Load-source provenance is not
stored in the payload; readers attach it after loading. The current schema
version is `1`.

## Envelope Contract

All numeric fields are unsigned big-endian values unless noted otherwise:

```text
14 bytes  magic = SVM_IDE_REPORT
u16       envelope version
u16       UTF-8 producer-version length
bytes     producer version
u16       payload kind (1 = JSON)
u16       payload version (1)
u8        compression (0 = none, 1 = gzip)
u64       uncompressed payload size
u64       stored payload size
u8        checksum kind (1 = SHA-256)
32 bytes  SHA-256 of the uncompressed payload
bytes     stored payload
```

Payloads below 4096 bytes remain uncompressed. Larger payloads use deterministic
gzip at level 9 only when it shrinks the data. The gzip header has zero mtime,
no filename, and a fixed OS byte.

## Focused Validation

The focused validation completed with LabsJDK 25.1 b19:

- `IDEReportStorageTest`: 8 tests pass for option semantics, canonical payloads,
  build-artifact classification, deterministic envelope bytes, both compression
  paths, corruption, and truncation.
- `IDEReportTest`: 3 tests pass for filter parsing, build-scoped state, nested
  build rejection, and invalid-filter cleanup.
- Python tooling: 31 tests pass for sources, canonicalization, comparison,
  expectations, baselines, envelope compatibility, and disabled output checks.
- The CE host standalone points-to wrapper passes all 28 tests.
- The standard points-to fixture smoke produces 68 filtered records and 24 used
  methods; the heterogeneous `mixedValue` call produces no unsound
  `always returns` report.
- The same fixture builds successfully with the experimental reachability
  analysis engine, confirming that unsupported points-to-only field facts are
  skipped rather than cast.
- An intentional `ide-reports` file/directory collision emits a report-write
  warning while the native executable still builds successfully, confirming
  that diagnostic output cannot replace a successful build outcome.
- Explicit canonical `full` export was accepted by native-image and produced a
  schema-version-1 payload. An unfiltered measurement contained 224,180 records
  and 39,273 used methods (89,947,981 bytes), confirming why filtering and the
  minimal scope matter.
- Explicit filtered `minimal` export produced 41 records, zero used methods, and
  10,157 bytes. All records came from the requested fixture package. With build
  artifacts enabled, it is recorded as
  `"ide_report":["ide-reports/native_image_ide_report.json"]`.

The final formatting, checkstyle, warning-as-error build, focused unit suites,
and updated enabled/disabled smoke all passed before handoff.

## Next Step

Start Phase 11 by writing the already-defined envelope bytes to
`<image>.ide-report`, registering that path as `IDE_REPORT`, and teaching the
Python source adapter to load `split:` reports.
