# IDE Report Phase 11 Results

Date: 2026-06-28

Phase 11 implements split IDE report storage entirely in this repository.

## Implemented

- `-H:IDEReportStorage=split` now writes the deterministic IDE report envelope
  to the sibling `<image>.ide-report` file.
- Split output is registered as `BuildArtifacts.ArtifactType.IDE_REPORT` and
  appears under the `ide_report` key in `build-artifacts.json`.
- The hosted finalization path creates the canonical payload once. Canonical
  export writes those exact bytes, while split storage encodes those bytes once
  in the shared envelope format.
- Envelope producer metadata uses the GraalVM vendor version reported by the
  image builder.
- The Python source layer now decodes `split:` envelopes and loads their
  canonical payloads into the storage-neutral `ReportBundle` model.
- Invalid envelopes, corrupt checksums, invalid UTF-8, and invalid JSON produce
  `IDEReportSourceError` diagnostics.

`IDEReportStorage=embed` and `embed,split` remained intentionally unavailable
until Phase 12. The source parser reserved `image:` and `auto:` so later phases
could add them without changing the neutral model.

## Storage Path

For an image named `/path/to/app`, split mode writes:

```text
/path/to/app.ide-report
```

The side file contains the versioned binary envelope. It is not a second JSON
export. `mx ide-report` loads it directly with:

```text
mx ide-report summarize split:/path/to/app.ide-report
```

## Focused Validation

Validation used LabsJDK 25.1 b19 and the local Native Image distribution:

- `IDEReportStorageTest`: 9 tests pass, including exact split-file naming,
  byte preservation, and envelope decoding.
- `IDEReportTest`: all 3 existing lifecycle and filter tests pass; the combined
  focused Java run passes all 12 tests.
- Python tooling: 35 tests pass, including split loading, exact canonical
  round trips, corrupt-envelope rejection, and invalid-payload rejection.
- Java formatting, Checkstyle, and forced ECJ and javac warning-as-error builds
  pass.
- The focused full-scope split build produces 68 records, 24 used methods, and
  passes the fixture semantic expectations.
- The full envelope is 1,730 bytes and decodes to a 24,733-byte canonical
  payload with SHA-256
  `3f8a83382d98aed34836670ba2e1fe7fc7e4eb757f5083bb51122eae01bfe2b5`.
- The focused minimal-scope split build produces 41 records and no method
  inventory. Its envelope is 1,011 bytes and decodes to a 10,157-byte payload
  with SHA-256
  `531c43de7476f5bfd2246bc7a114f9d220e4632100212e28b8254afe3ba9bf67`.
- Both fixture payloads use deterministic gzip and record producer
  `GraalVM CE 25.2.4-dev+9.1`.
- A separately built minimal canonical export is byte-identical to the decoded
  minimal split payload. The semantic comparator reports no record or method
  differences.
- Full and minimal `build-artifacts.json` files both contain
  `"ide_report":["ide-report-fixture.ide-report"]`.
- Both generated fixture executables run successfully.

The repository-wide local Markdown link check still reports seven unrelated
pre-existing broken links outside `docs/ide-reports`; it reports no link issue
in the Phase 11 documentation.

## Next Step

Start Phase 12 by identifying the macOS/Mach-O object-file hook for the
`.svm_ide_report` section and the `ide_report` and `ide_report_length` symbols.
The embedded implementation must consume the same envelope bytes now used by
split storage.
