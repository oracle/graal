# IDE Report Phase 13 Results

Date: 2026-06-28

Phase 13 validates storage equivalence for the focused fixture on macOS/Mach-O.
The validation uses the source distribution built from this repository.

## Comparison Rule

- Compare separately built export and embedded reports semantically. Build
  scheduling may change independently even when the report contract is
  preserved, so byte identity is not the cross-build requirement.
- Compare embedded and split envelopes byte-for-byte only when they come from
  the same `IDEReportStorage=embed,split` build. Both outputs must reuse the
  prepared build-scoped envelope.
- Compare payload scopes as an intentional projection. Minimal output must
  omit method inventory and other full-only facts without changing retained
  records.

## Fixture Matrix

Seven fresh fixture images were built with LabsJDK 25.1 b19 and the local CE
Native Image distribution:

| Filter | Scope | Storage | Purpose |
| --- | --- | --- | --- |
| `com.oracle.svm.test.ide` | full | export | canonical comparison source |
| `com.oracle.svm.test.ide` | full | embed | separate-build image source |
| `com.oracle.svm.test.ide` | full | embed,split | same-build byte comparison |
| `com.oracle.svm.test.ide` | minimal | export | canonical comparison source |
| `com.oracle.svm.test.ide` | minimal | embed | separate-build image source |
| `com.oracle.svm.test.ide` | minimal | embed,split | same-build byte comparison |
| `no.such.ide.report.package` | minimal | embed,split | uncompressed-envelope path |

All seven generated executables run successfully.

## Full Scope

- Canonical export and the separately built embedded image both contain 68
  records and 24 used methods. The semantic comparator reports no missing,
  added, or changed records or methods.
- The independent export bytes also happen to equal the decoded embedded
  payload in this run. This is useful evidence but is not the cross-build
  contract.
- The same-build embedded and split envelopes are byte-identical.
- The canonical payload is 24,733 bytes with SHA-256
  `3f8a83382d98aed34836670ba2e1fe7fc7e4eb757f5083bb51122eae01bfe2b5`.
- The deterministic gzip envelope is 1,730 bytes with SHA-256
  `a6267dfd285ad573db80dc27554924b991635846eeeebcb4adfe29f9248a00ed`.

## Minimal Scope

- Canonical export and the separately built embedded image both contain 41
  records and no used methods. The semantic comparator reports no missing,
  added, or changed records or methods.
- The independent export bytes also happen to equal the decoded embedded
  payload in this run.
- The same-build embedded and split envelopes are byte-identical.
- The canonical payload is 10,157 bytes with SHA-256
  `531c43de7476f5bfd2246bc7a114f9d220e4632100212e28b8254afe3ba9bf67`.
- The deterministic gzip envelope is 1,011 bytes with SHA-256
  `fae197e4f03b9feee287a559574ee2cd82a565245c7f45031fa4f05671b37267`.

## Scope Projection

Compared with full scope, minimal scope intentionally removes 27 records and
all 24 used methods. The removed records consist of:

- 11 class-initialization records
- 1 constant-field record
- 12 inlined-only method records
- 3 parameter-value line records

Minimal scope retains 41 line and unreachable-code records. It adds or changes
nothing relative to the retained full-scope records.

## Compression Paths

The filtered full and minimal reports both exceed the 4,096-byte compression
threshold and use deterministic gzip (`compression=1`). A seventh build used a
non-matching filter and produced an empty minimal report:

- 0 records and 0 used methods
- 115-byte canonical payload with SHA-256
  `bf17c0c22b704d2ff7bb256fd7ca1b98f2f33bc0d588f12ce517b4cb57543c12`
- 212-byte uncompressed envelope with SHA-256
  `2e3f7eff39cc93167b09f5f7eab36dab35d29c70078ae8a31a51e519f54520d8`
- `compression=0`
- byte-identical embedded and split envelopes
- no semantic differences between `image:` and `split:` loading

This validates both envelope compression branches through real image storage,
not only through unit tests.

## Next Step

Start Phase 14 by measuring image size, report size, and build cost for the
fixture matrix, then extend the measurements to Spring PetClinic and
DaCapo `h2` as time and machine resources allow.
