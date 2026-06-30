# GR-61707 IDE Report Embedded Storage Design

## Purpose

This document captures the storage design for Native Image IDE reports. The
GR-61707 branch supports legacy and canonical JSON export, a binary envelope
side file in split mode, and the same envelope embedded in macOS/Mach-O and
Linux/ELF images. Split storage remains the portable fallback for production
or Docker-base-image workflows and for platforms without an embedding backend.

The initial storage requirements are:

- Embedding reports directly in the binary is desirable.
- A new object-file section is acceptable.
- An SBOM-like storage model is also acceptable.
- Splittable storage, similar to debug info, is useful for Docker/base-image installations.
- The data format should be easy to access through a memory-mapped byte buffer.
- The format must not materially slow compilation.
- Initial content should cover the information already collected by the PR:
  - reflection reports
  - inline reports
  - reachability / unreachable reports
  - return type calculation reports

## Implementation Status

Phase 11 implements `IDEReportStorage=split` in CE. Phase 12 implements
`IDEReportStorage=embed` and `embed,split` for Mach-O. Phase 13 confirms
semantic equivalence with separate canonical exports for both scopes and
exercises both gzip and uncompressed envelopes through real image storage.
Phase 14 measures size and build cost for the focused fixture, Spring
PetClinic, and DaCapo H2. The results support `minimal` as the stronger future
production default while retaining `full` for development and measurement.
Phase 15 extends embedded storage to ELF. The prepared envelope is stored in
`__TEXT,__svm_idereport` on Mach-O or `.svm_ide_report` on ELF and is located
through the `ide_report` and `ide_report_length` symbols. `image:` detects the
object-file format, memory-maps the image, and validates the section, symbols,
envelope, and canonical payload. Full and minimal fixture payloads have been
validated, including same-build byte identity between embedded and split
envelopes. The `auto:` adapter detects raw and canonical JSON, split envelopes,
supported native images, image-adjacent side files, and `ide_report` entries in
`build-artifacts.json`.

## Relationship To Rebase Validation

This is a sibling workstream to [IDE_REPORT_REBASE_VALIDATION_DESIGN.md](IDE_REPORT_REBASE_VALIDATION_DESIGN.md).

The fine-grained execution sequence is captured in [IDE_REPORT_EXECUTION_PLAN.md](IDE_REPORT_EXECUTION_PLAN.md).

The rebase validation work builds the safety net:

- controlled fixture
- report model
- query tool
- semantic comparator
- pre/post rebase artifact comparison

The embedded storage work builds a new report source:

- data serialization
- embedding or splitting policy
- extraction support
- equivalence validation between JSON export and embedded report source

Both tracks must share the same neutral report model. The query, compare, summarize, and assert tools should operate on `ReportBundle`, not on JSON or object-file details.

## Core Design Principle

Build an IDE report inspection and storage pipeline, not a JSON-specific feature.

The current JSON sidecar is one source adapter. Future embedded report data, debug-info side data, or SBOM-like storage are additional source adapters. All sources should load into the same model:

- `ReportBundle`
- `ReportRecord`
- `ReportKind`
- `SourceLocation`
- `InlineContext`
- `MethodReference`
- `ReportProvenance`

The storage design must not leak into report analysis logic. For example, `mx ide-report query` should work the same way for:

```text
json:/path/to/native_image_ide_report.json
canonical:/path/to/canonical-native-image-ide-report.json
image:/path/to/app
split:/path/to/app.ide-report
auto:/path/to/app-or-report
```

Source scheme meanings:

- `json:` loads the current raw legacy JSON export.
- `canonical:` loads canonical `ReportBundle` JSON directly.
- `image:` extracts the embedded IDE report envelope from ELF or Mach-O
  executables.
- `split:` loads the implemented externalized envelope side file.
- `auto:` detects based on path and contents, trying canonical JSON, raw JSON,
  embedded image data, nearby split side files, and `build-artifacts.json`.

Defer `debug:` / `debuginfo:` until IDE reports are actually stored in debug-info-associated data. Since split mode is explicitly not debug-info-associated, using debug naming in the first CLI would be misleading.

## Existing Native Image Patterns To Reuse

### SBOM-Style Embedding

Native Image SBOM support is a strong reference point:

- `--enable-sbom` supports `embed`, `export`, and `classpath` storage.
- Embedded SBOM data is compressed to reduce binary size.
- The embedded SBOM is referenced by exported symbols:
  - `sbom`
  - `sbom_length`
- `native-image-utils extract-sbom --image-path=<path>` can extract the embedded content.

This pattern aligns well with the request for `MemoryByteBuffer`-friendly access: a consumer can find a symbol and length, map the bytes, and decode the payload.

### Debug Info Splitting

Native Image already supports split debug info:

- debug information is initially in the executable
- `objcopy --only-keep-debug` can create a separate debug file
- `objcopy --add-gnu-debuglink=<debug-file>` links the executable to the debug file
- the separate debug file is recorded as a `BuildArtifacts.ArtifactType.DEBUG_INFO` artifact

This pattern is useful for report data that should not stay in production binaries but should remain discoverable beside them.

### Object File Sections

The object-file layer supports custom sections:

- `ObjectFile.newUserDefinedSection`
- `ObjectFile.newDebugSection`
- `ObjectFile.newProgbitsSection`
- `BasicProgbitsSectionImpl`

Existing Native Image code already uses custom sections for image metadata and debug-related build metadata, including `.debug.svm.imagebuild.*` sections.

## Storage Strategies To Evaluate

### Option A: SBOM-Style Embedded Blob

Embed a compressed report payload into the image and expose it through exported symbols, for example:

```text
ide_report
ide_report_length
```

Possible section name:

```text
.svm_ide_report
```

Pros:

- Closest to the SBOM pattern.
- Easy for a memory-mapped consumer to locate by symbol and length.
- Works even if the consumer does not understand debug-info formats.
- Natural fit for `native-image-utils extract-ide-report`.

Cons:

- If stored as runtime-loadable data, it may increase the production executable size.
- Needs a clear policy for stripping and splitting.
- Exported symbols become part of the discoverability contract.

Best use:

- First embedded prototype.
- Tooling that wants cheap section/symbol lookup.

### Option B: Named Object-File Section

Write the report payload into a named section, for example:

```text
.svm_ide_report
.debug.svm.ide_report
```

Pros:

- Clear object-file-level representation.
- Can be non-writable and non-executable.
- Debug-section naming can express that data is not runtime-critical.
- Works naturally with object-file extraction tooling.

Cons:

- Section handling differs across ELF, Mach-O, and PE/COFF.
- A pure section without exported symbols may be less convenient for `MemoryByteBuffer` access from code.
- Need to decide whether the section should survive normal stripping.

Best use:

- Structured non-runtime storage.
- Future split/debug-style integration.

### Option C: Debug-Info Side Data

Treat the report as debug-associated metadata and let split-debug-info workflows move it to a side file.

Pros:

- Matches the Docker/base-image desire for splittable data.
- Production executable can remain small.
- Can be recorded as `BuildArtifacts.ArtifactType.DEBUG_INFO` or a future more specific artifact type.

Cons:

- Consumers need to find the side file.
- Data may disappear when debug info is not generated.
- Tying IDE analysis reports too tightly to debug info may be semantically awkward: the report is build-analysis metadata, not traditional source-level debug info.

Best use:

- Optional split mode after embedding/export semantics are stable.

## Recommended Initial Direction

Start with a SBOM-style embedded blob, but structure the implementation so it can also be exported and later split.

The accepted first design direction is:

- Use both a named section and exported symbols.
- Store embedded data in `.svm_ide_report`.
- Expose start and length symbols, initially named:
  - `ide_report`
  - `ide_report_length`
- Make embedded data non-writable and non-executable.
- Make `embed` mode survive `--strip-debug`.
- Keep split mode conceptually independent from full debug-info generation, even if it reuses debug-info mechanics internally.
- Make split mode produce a dedicated IDE report side file, not a debug-info-associated file.
- Apply `IDEReportFiltered` before payload-scope selection and serialization for all storage modes.
- Make payload scope configurable so we can measure the size impact of method-inventory data before narrowing production defaults.

Initial modes should be conceptually similar to SBOM:

```text
off
export
embed
embed,export
split
embed,split
```

Mode semantics:

- `export` writes a standalone developer/CI report dump. Its primary purpose is inspection, rebase validation, and compatibility with the current JSON sidecar behavior. It should be easy to read directly and can remain canonical JSON.
- `embed` writes the report into the generated image as image-attached metadata. It uses the IDE report envelope and payload format, stores the bytes in `.svm_ide_report`, and exposes the payload through the `ide_report` and `ide_report_length` symbols.
- `split` writes the same envelope/payload bytes that `embed` would have stored in the image, but places them in a dedicated IDE report side file. It is the externalized form of embedded report storage, not a second JSON export mode.
- `embed,export` produces both the image-attached metadata and the standalone developer/CI report dump.

The design rule is that `split` must mean "embed, but externalized." If split mode ever degenerates into "write another JSON report file," it no longer carries a distinct meaning and should be removed in favor of `export` plus `embed`.

During migration, distinguish raw legacy export from canonical export:

- raw legacy export is the current timestamped JSON file produced by the PR
- canonical export is a deterministic `ReportBundle` serialization

`-H:+IDEReport` should keep producing the current raw legacy export during the rebase work so existing behavior remains compatible. `mx ide-report` should be able to load that raw export and write canonical JSON for validation and baselines. Once the new tooling is stable, `export` can move toward canonical output by default.

Chosen option spelling:

Keep the existing enable/filter options and add a storage option:

```text
-H:+IDEReport
-H:IDEReportStorage=export
-H:IDEReportStorage=embed
-H:IDEReportStorage=embed,export
-H:IDEReportStorage=split
-H:IDEReportStorage=embed,split
-H:IDEReportStorage=off
```

This keeps `IDEReport` as the boolean that enables report collection, keeps `IDEReportFiltered` as the filter, and makes storage a separate concern. It also leaves room for future modes without overloading a boolean option.

Rejected alternate spelling because it conflicts with the existing boolean option:

```text
-H:IDEReport=export,embed
```

This is concise, but it would require migrating or overloading the existing `-H:+IDEReport` option. That makes it a weaker fit for the current branch.

Rejected alternate output-oriented spelling:

```text
-H:IDEReportOutput=export
-H:IDEReportOutput=embed
-H:IDEReportOutput=embed,export
-H:IDEReportOutput=split
```

This is understandable, but `Storage` better describes that one mode may embed bytes into the image while another produces a side artifact.

The current `-H:+IDEReport` behavior should remain compatible during migration. It can initially mean `export`, and a later option can request embedding.

For the first prototype, treat IDE report data as build-analysis metadata:

- It is not required at runtime.
- It is opt-in.
- It may be embedded for convenience and IDE access.
- It must remain exportable or splittable for production-size and confidentiality reasons.

## Payload Envelope

Do not start with a complex custom binary schema. Use a small versioned envelope around the payload.

Suggested envelope fields:

```text
magic
envelope_version
producer_version
payload_kind
payload_version
compression_kind
uncompressed_size
payload_size
checksum_kind
checksum
payload
```

Suggested magic:

```text
SVM_IDE_REPORT
```

Initial payload kind:

```text
json-v1
```

Initial compression:

```text
gzip
```

Envelope decisions:

- Use fixed big-endian byte order for numeric envelope fields.
- Use SHA-256 over the uncompressed logical payload.
- Include the compressed payload size in the envelope.
- Allow `compression_kind=none` for tiny payloads if compression does not help.
- Use `gzip` when the uncompressed payload is at least 4096 bytes and compression makes it smaller.
- Use `none` for payloads below 4096 bytes or when gzip does not reduce size.
- Treat the 4096-byte threshold as an initial arbitrary value, not a product decision.
- Add tests that cover both `gzip` and `none` payloads.
- Produce canonical payload bytes once per image build and reuse the same bytes for both `embed` and `split`.
- Do not serialize or compress separately for embedded and split targets in the same build.
- Keep load-source provenance, such as "loaded from image section" or "loaded from split file", outside the stored envelope. That belongs in `ReportProvenance` after loading.
- Make gzip output deterministic by using a fixed modification time, no original filename, and a fixed compression level.

Phase 10 fixes the initial binary layout as follows. `u16` and `u64` fields are
unsigned big-endian integers:

```text
14-byte magic "SVM_IDE_REPORT"
u16 envelope version
u16 producer-version byte length
UTF-8 producer-version bytes
u16 payload kind
u16 payload version
u8 compression kind
u64 uncompressed size
u64 stored size
u8 checksum kind
32-byte SHA-256 over the uncompressed payload
stored payload bytes
```

Version 1 uses payload kind `1` for canonical JSON, payload version `1`,
compression `0` for none or `1` for gzip, and checksum kind `1` for SHA-256.
The gzip header uses zero mtime, no filename, level 9, and a fixed OS byte so
Java and Python produce byte-identical envelopes.

Rationale:

- The envelope is cheap to inspect through a memory-mapped byte buffer.
- The payload can start as canonical JSON to minimize migration risk.
- Compression limits binary-size impact.
- The envelope leaves room for future binary or table-oriented payloads.
- Same-build `embed` and `split` byte equality preserves the meaning that split is "embed, but externalized."

## Payload Format

Initial payload can be canonical JSON derived from `ReportBundle`.

This should not be the raw current JSON dump. It should be a stable report payload format with:

- explicit format version
- stable top-level keys
- deterministic ordering where feasible
- clear record kinds
- explicit optional fields
- no timestamp-dependent file name baked into the payload

Future payloads may use a compact binary encoding, but that should be driven by measured size and access needs rather than assumed upfront.

Canonical JSON is the format used for:

- comparison baselines
- embedded payloads
- split payloads
- optional canonical export from `mx ide-report`

Raw legacy JSON is only an input source and compatibility output during migration. It is not a stable comparison or storage contract.

Phase 10 canonical payloads use schema version `1`, UTF-8, a trailing newline,
deterministic object/list ordering, and the top-level keys `extensions`,
`payload_scope`, `records`, `schema_version`, and `used_methods`. Every report
record has a semantic `category`. Load-source provenance is deliberately kept
outside these stored bytes and is attached by readers after loading.

## Initial Content Scope

The first embedded storage prototype should support two payload scopes.

### `full`

`full` is the measurement-first scope. It should include everything the current PR can report after `IDEReportFiltered` is applied:

- normal report records
- `used_methods`
- compiled-method facts
- inlined-only method facts
- class initialization mode reports
- constant field reports
- constant parameter reports
- return type reports

Use `full` as the default during the first prototype so we can measure the actual size cost across the validation applications.

### `minimal`

`minimal` is the production-candidate scope. It should include the initially
requested report families that the PR already knows how to collect:

- reflection reports
- inline reports
- reachability / unreachable code reports
- return type calculation reports

This scope excludes method-inventory-style data by default.

The PR also collects additional facts:

- class initialization mode
- constant fields
- constant parameters and returns
- compiled methods
- inlined-only methods

These should stay in the neutral model and full payload scope. They can be removed from `minimal` scope when size or confidentiality pressure requires it.

`used_methods`, compiled-method facts, and inlined-only method facts are expected to be expensive for large applications. Keep them configurable from the start so we can collect data rather than guessing.

## Extraction Tooling

There are two likely extraction surfaces.

### `mx ide-report`

Developer-facing inspection tool:

```text
mx ide-report summarize image:/path/to/app
mx ide-report query image:/path/to/app --kind reflection
mx ide-report compare json:/path/report.json image:/path/to/app
mx ide-report canonicalize json:/path/report.json --output /path/canonical.json
```

Commands that decode `split:`, `image:`, or `auto:` envelopes accept
`--max-payload-bytes` for trusted inputs above the 512 MiB default, up to
2,000,000,000 bytes.

This is the right place for development, rebase validation, semantic comparison, and report debugging.

### `native-image-utils`

User-facing extraction tool:

```text
native-image-utils extract-ide-report --image-path=<path_to_binary>
```

This mirrors `extract-sbom` and would be useful once the embedded format becomes user-facing.

Recommended sequence:

1. Build extraction in `mx ide-report` first.
2. Do not implement `native-image-utils extract-ide-report` for the current design step.
3. Revisit `native-image-utils` only after the embedded format is stable and there is a user-facing need outside development workflows.

The development command is mandatory for the rebase/storage validation work. The `native-image-utils` command is explicitly deferred.

## Build Artifact Tracking

Exported or split report files should be registered as build artifacts.

Use a dedicated artifact type:

```text
BuildArtifacts.ArtifactType.IDE_REPORT
```

Use this JSON key:

```text
ide_report
```

The split-mode side file and exported report file should be registered with `IDE_REPORT`, not `BUILD_INFO`. Update any build-artifacts JSON schema and docs that depend on `BuildArtifacts.ArtifactType`.

The artifact role differs by storage mode:

- exported reports are developer/CI report outputs
- split reports are companion artifacts for a specific generated image

Both can use the `IDE_REPORT` artifact type, but tools should treat split reports as image-adjacent metadata. A split report should be named and recorded so `mx ide-report auto:/path/to/image` can find it when possible.

Expected build-artifacts shape:

```json
{
  "ide_report": [
    "app.ide-report",
    "reports/native_image_ide_report.json"
  ]
}
```

Implementation checklist:

- add `IDE_REPORT("ide_report")` in the non-runtime artifact section
- update the build-artifacts JSON schema
- update schema tests or artifact-export tests that enumerate known keys
- update generated-build-artifacts docs/help text
- add a focused test that IDE report artifacts appear under `ide_report`
- keep `BUILD_INFO` for generic build reports only

Default split file naming:

```text
<image>.ide-report
```

For example:

```text
/path/to/app
/path/to/app.ide-report
```

The `.ide-report` file contains the IDE report envelope/payload bytes, not plain user-editable JSON. The envelope magic and version identify the binary format.

Discovery rules:

- `split:/path/to/app.ide-report` loads the split side file directly and does not require the image.
- `auto:/path/to/app` searches in this order:
  1. embedded payload inside `/path/to/app`, if present
  2. exact sibling `/path/to/app.ide-report`
  3. `IDE_REPORT` entry in the build-artifacts file, if available
- if no report is found, `auto:` fails with a diagnostic that lists the searched locations
- if embedded and split payloads both exist and differ, `auto:` prefers embedded data and warns about the differing split file
- direct comparison, such as `mx ide-report compare image:/path/to/app split:/path/to/app.ide-report`, should report any mismatch explicitly

## Option Semantics

IDE report data is build-analysis metadata:

- It is not required at runtime.
- It should be opt-in.
- It may be embedded for convenience.
- It should be exportable or splittable for production-size and confidentiality reasons.

Suggested defaults:

- `-H:+IDEReport` preserves current JSON export behavior during rebase.
- During migration, `-H:+IDEReport` is equivalent to `-H:IDEReportStorage=export`.
- Any `IDEReportStorage` value except `off` implies IDE report collection.
- Users do not need to pass both `-H:+IDEReport` and `-H:IDEReportStorage=<non-off>`.
- `-H:-IDEReport` combined with any non-`off` `IDEReportStorage` value is a user error.
- `IDEReportPayloadScope` is valid only when storage is not `off`; using it with `IDEReportStorage=off` is a user error.
- Embedding requires an explicit storage option.
- Splitting requires an explicit storage option.
- Only explicitly listed storage modes are supported. Do not accept arbitrary comma combinations by accident.
- Split mode should not require full debug-info generation from the user's point of view.
- Embed mode should survive `--strip-debug`.
- Split side-file mode does not need to survive `--strip-debug`; its purpose is to move the data out of the production executable.
- Split mode uses the same envelope and payload bytes as embed mode, except those bytes are written to a dedicated side file instead of an object-file section.
- `embed,split` exists to support same-build validation that the split side file is byte-identical to the embedded payload. It may also be useful for developer diagnostics.

Payload-scope option:

```text
-H:IDEReportPayloadScope=full
-H:IDEReportPayloadScope=minimal
```

Prototype default:

```text
full
```

Rationale: first measure the cost of all currently available facts, including method-inventory data.

Production-candidate default:

```text
minimal
```

Rationale: production builds are likely to need a smaller and less sensitive payload.

Filtering semantics:

- Apply `IDEReportFiltered` before serialization.
- Apply payload-scope selection after `IDEReportFiltered`.
- Embedded and split reports contain only records selected by both the filter and the payload scope.
- Do not embed full records plus filter hints in the first design. That may be useful for a later interactive mode, but it conflicts with size and confidentiality goals.
- `full` scope includes compiled-method, `used_methods`, and inlined-only method facts for measurement.
- `minimal` scope excludes method-inventory-style facts unless a later production requirement needs them.

Compatibility semantics:

- Version both the envelope and payload.
- During prototype development, compatibility is best effort.
- Before productization, readers should support at least the previous payload version or fail with a clear unsupported-version diagnostic.

## Validation Plan

After the rebase validation harness exists, add storage-equivalence validation:

1. Build the controlled fixture with JSON export.
2. Build the same fixture with embedded report output.
3. Extract embedded report through `mx ide-report`.
4. Load both into `ReportBundle`.
5. Compare semantically.
6. Assert the embedded source preserves required report families and key facts.
7. Build the fixture with split report output.
8. Load the split report through `mx ide-report`.
9. Assert that split output is byte-compatible at the envelope/payload level with the corresponding embedded payload, except for source-location/provenance metadata that identifies where the bytes were loaded from.
10. Compare split output semantically against both JSON export and embedded extraction.
11. Repeat embedded and split builds with both `IDEReportPayloadScope=full` and `IDEReportPayloadScope=minimal`.
12. Record record counts and payload sizes for each scope.

Later, repeat with:

- Spring PetClinic
- DaCapo `h2`

Storage-mode validation must cover both compression paths:

- a small payload stored with `compression_kind=none`
- a payload at or above the initial threshold stored with `compression_kind=gzip`

Payload-scope validation must cover both:

- `full`
- `minimal`

Embed/split determinism validation:

- when `embed` and `split` are produced by the same build, the stored envelope/payload bytes must be identical
- when `embed` and `split` are produced by separate builds, semantic equality and matching uncompressed payload SHA-256 are sufficient

## Performance And Size Checks

The storage feature must not materially slow compilation.

Measure these configurations for each validation application:

```text
baseline: IDE reports disabled
legacy export: current -H:+IDEReport raw JSON behavior
canonical export: IDEReportStorage=export
embed: IDEReportStorage=embed
split: IDEReportStorage=split
embed,split: IDEReportStorage=embed,split
```

For `embed`, `split`, and `embed,split`, measure both payload scopes:

```text
IDEReportPayloadScope=full
IDEReportPayloadScope=minimal
```

The full matrix is for dedicated measurement runs, not normal local validation. Frequent validation should use the custom fixture subset.

Measure at least:

- total build time
- analysis time, when available
- report collection time
- report serialization time
- compression time
- payload size before compression
- payload size after compression
- final executable size delta
- split side-file size
- extraction time
- record counts by report family
- size delta between `full` and `minimal` payload scopes
- specific contribution of `used_methods`, compiled-method facts, and inlined-only method facts

For large apps, record these for:

- custom fixture
- Spring PetClinic
- DaCapo `h2`

### Phase 14 Results

The completed measurements are recorded in
[IDE_REPORT_PHASE14_RESULTS.md](IDE_REPORT_PHASE14_RESULTS.md). The main size
results are:

| Application | Full payload | Minimal payload | Full envelope | Minimal envelope |
| --- | ---: | ---: | ---: | ---: |
| focused fixture | 24,733 B | 10,157 B | 1,730 B | 1,011 B |
| Spring PetClinic, package-filtered | about 326 KiB | about 264 KiB | about 13.4 KiB | about 10.2 KiB |
| DaCapo H2, unfiltered | about 28.3 MiB | about 20.7 MiB | about 958 KiB | about 569 KiB |

For the large unfiltered DaCapo report, minimal scope reduces the canonical
payload by 26.9%, the compressed envelope by 40.7%, and embedded image growth
from about 958 KiB to 578 KiB. Full method inventory contributes about 3.33
MiB of the canonical payload.

The fixture row is the fresh Phase 16 semantic baseline. Phase 14's earlier
performance rows remain valid characterization of the exact report sets
measured in that phase, but their smaller fixture record set is not the final
ground truth.

Direct timers show that canonical serialization is the dominant
post-analysis cost. DaCapo full serialization takes 2.4-2.6 seconds and gzip
takes about 0.35 seconds; minimal serialization takes 1.7-1.9 seconds and gzip
takes about 0.27 seconds. Snapshotting, embedding, and output writes are much
smaller. Single-run whole-build deltas remain characterization data rather
than statistically rigorous overhead claims.

## Deferred Security And Confidentiality

IDE reports can expose:

- source file paths
- class names
- method names
- field names
- reflection usage
- reachability decisions
- build-time initialization decisions

This means the feature may reveal internal details about the program being analyzed. Do not treat IDE report artifacts as anonymous or content-free build metadata.

Detailed confidentiality policy is deferred to a later project phase. That later phase should decide:

- production packaging guidance
- interaction with obfuscation
- CI artifact retention guidance
- whether any redaction or summary-only mode is needed

SBOM class-level metadata has similar confidentiality concerns and may guide the later wording, but confidentiality should not block the first rebase, validation, or storage prototype.

## Cross-Platform Handling

Hide ELF, Mach-O, and PE/COFF differences behind the embedding backend and source adapter.

The neutral `ReportBundle` model and `mx ide-report` CLI must not care which object-file format supplied the data.

The first `embed` prototype targeted macOS/Mach-O because that was the initial
development platform. Phase 15 adds Linux/ELF. Other formats fail with a clear
unsupported-platform diagnostic where embedding or extraction is not yet
implemented.

Logical names:

```text
section: .svm_ide_report
start symbol: ide_report
length symbol: ide_report_length
```

The Phase 12 Mach-O mapping uses `__TEXT,__svm_idereport`. The section name is
15 bytes because the current object writer reserves one byte for a terminating
null in the 16-byte Mach-O section-name field. The section has no instruction
flags, while `__TEXT` keeps the stored report non-writable. Other object-file
backends may use their natural spelling of the logical `.svm_ide_report` name.

The Phase 15 ELF mapping uses an allocated `PROGBITS` section named
`.svm_ide_report`, with eight-byte alignment and without `WRITE` or
`EXECINSTR` section flags. GNU ld places this read-only orphan section in the
same `R E` load segment as `.rodata`; this is analogous to Mach-O placing the
non-instruction section in `__TEXT`. The extractor requires a non-writable load
segment and rejects writable or executable section flags.

ELF exposes `ide_report` and `ide_report_length` as global object symbols in
the dynamic symbol table. They and the section survive Native Image's default
stripping, explicit `objcopy --strip-debug`, and explicit
`objcopy --strip-all`. Extraction therefore keeps using the same symbol and
length contract after stripping.

The embedding backend owns all platform-specific mapping:

- ELF section/symbol details
- Mach-O section/symbol details
- PE/COFF section/symbol details
- symbol visibility/export spelling
- strip behavior

Before considering embedded storage stable, validate at least:

- macOS/Mach-O: section present, symbols present, extraction works
- Linux/ELF: section present, symbols present, extraction works, strip behavior
  understood
- later: PE/COFF extraction and strip behavior

`split` is the portability fallback. Even if `embed` is unsupported on a platform, `split` can still produce the same envelope side file. In that case `image:` may fail with an unsupported-platform diagnostic, but `split:` should still work.

Section naming, symbol naming, and envelope layout should avoid choices that obviously block cross-platform support.

## Reader Resource Limits

Version 1 readers enforce a 512 MiB default limit on the decoded payload. This
default accommodates the 462,254,687-byte unfiltered Spring PetClinic full
payload measured on 2026-06-30 while still rejecting unexpectedly large
payloads before decoded-payload allocation. Trusted callers and command-line
users can raise the limit explicitly with `--max-payload-bytes`, up to
2,000,000,000 bytes.

Java and Python readers must:

- reject a declared decoded size above the configured limit before
  decompression
- reject a stored payload above the configured limit
- bound gzip expansion to the declared decoded size
- verify the decoded size and SHA-256 after decompression
- bound split-file reads to the configured payload limit plus the maximum
  envelope header overhead

This is a resource policy rather than an envelope-format limit. The current
end-to-end Python and JBang consumers materialize both the decoded JSON and an
object model. Java arrays are also `int`-indexed. Consequently, the
2,000,000,000-byte override is intended only for trusted inputs on
appropriately sized machines; supporting exact 2 GiB or larger payloads
reliably requires streaming decompression, hashing, and JSON parsing.

## Deferred Questions

- What user-facing compatibility promise is appropriate if `native-image-utils extract-ide-report` is eventually added?
- How much extraction implementation should be shared between `mx ide-report` and a future `native-image-utils` command?
- Should compiled-method, `used_methods`, or inlined-only method facts get a separate compact storage path later if `full` scope measurements show they are too expensive?
- Can the build-scoped compiler bridge and direct positive-inlining hooks be
  replaced by a standard compiler event consumer if parallel in-process builds
  or a production compiler-observability API is required? This remains tracked
  by GR-61707.
- What confidentiality, packaging, CI retention, and redaction policy should apply once the feature moves toward production use?

## Non-Goals For The First Embedded Prototype

- Do not design a full custom binary table format before measuring JSON payload size and extraction needs.
- Do not make embedded reports enabled by default.
- Do not require the IDE to parse object-file formats directly.
- Do not make the rebase depend on embedded storage.
- Do not implement `native-image-utils extract-ide-report` in the first embedded prototype.
- Do not remove JSON export until the embedded/split story is validated.
