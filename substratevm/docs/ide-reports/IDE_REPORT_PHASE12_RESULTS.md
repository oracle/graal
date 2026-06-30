# IDE Report Phase 12 Results

Date: 2026-06-28

Phase 12 implements the first embedded IDE report prototype for macOS/Mach-O.

## Implemented

- Added build-scoped storage data prepared after all feature
  `afterCompilation` callbacks. Canonical export, embed, and split modes reuse
  the same snapshot, canonical payload, and deterministic envelope.
- Enabled `IDEReportStorage=embed` and `embed,split` for Mach-O images.
- Added a read-only Mach-O section containing the exact envelope bytes followed
  by an eight-byte little-endian envelope length.
- Exported `ide_report` at the envelope start and `ide_report_length` at the
  length field. Mach-O exposes these as `_ide_report` and
  `_ide_report_length`.
- Implemented the `image:` source adapter with memory-mapped Mach-O parsing.
  The parser validates the file format, load commands, section location,
  segment protections, section flags, symbol table, length field, bounds,
  envelope, and canonical payload.
- Added an early user error for embed mode on non-Mach-O platforms. Split mode
  remains the portable fallback.

## Mach-O Mapping

The cross-platform logical section name remains `.svm_ide_report`. The current
Mach-O object writer reserves one byte for a terminating null in its 16-byte
section-name field, so the concrete 15-byte spelling is:

```text
segment: __TEXT
section: __svm_idereport
start symbol: _ide_report
length symbol: _ide_report_length
```

The section has no instruction flags and its final segment is not writable.
The length field is section metadata and is not part of the envelope range.
Consequently, `image:` returns bytes that can be compared directly with the
`<image>.ide-report` side file.

## Focused Validation

Validation used LabsJDK 25.1 b19 and the local Native Image distribution:

- `IDEReportStorageTest`: 10 tests pass, including exact embedded envelope
  bytes, length metadata, section name, segment name, and symbols.
- `IDEReportTest`: all 3 existing lifecycle and filter tests pass; the combined
  focused Java run passes all 13 tests.
- Python tooling: 38 tests pass, including synthetic Mach-O extraction,
  canonical loading through `image:`, unsupported-format diagnostics, and
  missing-symbol diagnostics.
- Repository-pinned Black 23 formatting, Java formatting, Checkstyle, and
  forced ECJ and javac warning-as-error builds pass.
- A full embedded fixture produces 68 records and 24 used methods and passes
  the fixture semantic expectations.
- The full envelope is 1,730 bytes and decodes to a 24,733-byte canonical
  payload with SHA-256
  `3f8a83382d98aed34836670ba2e1fe7fc7e4eb757f5083bb51122eae01bfe2b5`.
- A minimal `embed,split` fixture produces 41 records and no method inventory.
  Its envelope is 1,011 bytes with SHA-256
  `fae197e4f03b9feee287a559574ee2cd82a565245c7f45031fa4f05671b37267`.
- The minimal envelope decodes to a 10,157-byte canonical payload with SHA-256
  `531c43de7476f5bfd2246bc7a114f9d220e4632100212e28b8254afe3ba9bf67`.
- The embedded and split minimal envelopes from the same build are
  byte-identical. The semantic comparator reports no record or method
  differences.
- `otool` finds `__TEXT,__svm_idereport` with zero section flags, and `nm`
  finds both exported symbols.
- Full and minimal generated fixture executables run successfully.
- Embed-only output adds no external `ide_report` build artifact. The
  `embed,split` manifest records only the companion
  `ide-report-fixture.ide-report` file.

## Next Step

Start Phase 13 by running the formal export/embed and embed/split equivalence
matrix for both payload scopes. Keep source provenance outside the stored
payload and use byte identity only for targets produced from the same prepared
storage data.
