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

Decoded payloads are limited to 64 MiB by default. The header is checked before
decompression and gzip output is bounded while streaming. A trusted larger
report can be opened with `--max-payload-bytes`, up to the Java array limit.

The 64 MiB default is intentionally conservative but still accommodates the
largest Phase 14 payload, DaCapo H2 full scope at about 28 MiB. It is a reader
policy, not yet a production format limit.

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

The bounded JBang reader does not resolve the production-readiness issue for
the existing Native Image Java and Python envelope readers. Those readers still
need their own explicit limits and tests before report extraction is treated as
a production-facing interface.
