# IDE Report JBang Reader

`IdeReportReader.java` is a small Java reference implementation for consumers
of Native Image IDE report data. It is deliberately independent of Native
Image build classes and can be run directly with JBang.

The reader complements `mx ide-report`:

- use `mx ide-report` for object-file discovery, rich queries, comparison,
  canonicalization, and expectation checking
- use the JBang reader as a minimal executable example of the payload and
  split-envelope contracts
- reuse its `ByteBuffer` API when prototyping a Java IDE integration

## Requirements

- JBang 0.139.3 or newer
- JDK 17 or newer
- Maven access on the first run so JBang can resolve the pinned Jackson
  Databind 2.22.0 dependency

On macOS, install JBang with:

```bash
brew install jbangdev/tap/jbang
```

The dependency and Java version are declared in the source file, so no Maven
or Gradle project is required.

## Commands

From the Graal repository root, summarize a canonical JSON export:

```bash
jbang substratevm/mx.substratevm/ide_report/IdeReportReader.java \
  /path/to/native_image_ide_report.json
```

Summarize a split report envelope:

```bash
jbang substratevm/mx.substratevm/ide_report/IdeReportReader.java \
  /path/to/application.ide-report
```

Print selected records as a JSON array:

```bash
jbang substratevm/mx.substratevm/ide_report/IdeReportReader.java \
  --list --category reflection /path/to/application.ide-report
```

The available exact-match filters are `--kind` and `--category`. Run with
`--help` for the complete command line.

## Supported Contract

The reader accepts:

- canonical schema-version-1 JSON payloads
- version-1 `SVM_IDE_REPORT` split envelopes
- uncompressed and gzip-compressed envelope payloads
- SHA-256 envelope checksums

The parser checks required top-level JSON fields and rejects duplicate fields,
unsupported versions, malformed UTF-8 producer data, truncated envelopes,
size mismatches, invalid gzip data, and checksum mismatches.

Decoded payloads are limited to 512 MiB by default. The header is checked
before decoded-payload allocation and gzip output is bounded to the declared
size. A trusted larger report can be opened with `--max-payload-bytes`, up to
2,000,000,000 bytes. The same default and override ceiling apply to the Python
and production Java envelope decoders.

The default accommodates the unfiltered Spring PetClinic full payload measured
on 2026-06-30 at 462,254,687 bytes (440.84 MiB). The limit is a reader resource
policy, not a format limit. The current Java reader uses `byte[]` and Jackson's
in-memory tree model, so payloads near the override ceiling require
substantially more than two gigabytes of heap. Supporting exact 2 GiB or larger
payloads reliably requires a streaming parser and model.

## Java API

`IdeReportReader` exposes three entry points for Java prototypes:

- `decodeEnvelope(ByteBuffer, long)` validates and decodes an envelope
- `parsePayload(ByteBuffer)` validates canonical JSON and returns a `Report`
- `read(Path, long)` detects canonical JSON versus a split envelope

The input buffer can therefore come from a memory-mapped object-file section.
It must cover the exact envelope range identified by the report symbol, not an
entire section with its trailing length field. The reader does not locate
Mach-O or ELF sections itself. That separation keeps the report format parser
independent of how the bytes are stored.

## Validation

Run the standalone test script with:

```bash
jbang substratevm/mx.substratevm/ide_report/IdeReportReaderTest.java
```

The test covers fixed cross-language uncompressed and gzip vectors, payload
parsing, checksum corruption, truncation, decoded-size limits, schema errors,
duplicate JSON fields, summary output, and filtered record output. The test is
manual and does not make normal Graal gates depend on JBang or Maven downloads.

The Java and Python production envelope readers use the same bounded policy and
cover oversized headers and high-ratio compressed payloads in focused tests.
The JBang test additionally checks the shared default and configurable ceiling.
