# IDE Report Phase 16 Results

Date: 2026-06-28

Phase 16 completes the implementation/design review, final validation, public
repository hygiene, commit-stack preparation, and pull-request handoff for the
IDE report prototype.

## Review Outcome

The final review found and resolved the following issues:

- Disabled builds entered report helpers before checking the build-scoped
  enable flag. Report utilities now return before allocating capturing lambdas
  or inspecting analysis state.
- Whitespace-only class filters behaved differently from an empty filter.
  They now select the documented accept-all behavior, while empty list
  elements remain errors.
- Constant parameter/return reporting assumed every line-number table was
  non-empty. Empty tables are now ignored.
- The report schema compatibility note was an untracked `TODO`, and internal
  guard helpers were unnecessarily public. The compatibility constraint is now
  explicit and the helpers are private.
- `auto:` was reserved but not implemented. It now detects raw and canonical
  JSON, split envelopes, Mach-O/ELF images, image-adjacent split files, and
  `ide_report` entries in `build-artifacts.json`. Embedded data wins when both
  embedded and split copies exist, with a warning if their bytes differ.
- The checked-in validation helpers exposed an environment-specific Spring
  PetClinic harness. The public tooling now keeps only runner-neutral
  expectation and validation support for externally collected PetClinic
  reports.
- The compiler formatter found and corrected one indentation mismatch in the
  PE graph decoder hook.

No remaining correctness findings were identified within the documented
prototype scope after these fixes.

## Final Validation

Repository hygiene passes:

- strict Python formatting and bytecode compilation
- 47 Python unit tests
- strict Eclipse formatting and Checkstyle in the compiler and Substrate VM
  suites
- forced ECJ and javac warning-as-error builds for the compiler, compiler
  tests, points-to analysis, hosted Native Image, and Substrate VM tests
- 3 focused compiler tests
- 11 focused storage tests; the standalone test command opens
  `jdk.graal.compiler.debug` because the ELF test directly constructs the
  object-file backend

The fresh gate-scale fixture passes its positive and disabled-report smokes.
The positive report matches the semantic ground truth:

```text
reports: 68
used methods: 24
CLASS/FIELD/LINE/METHOD/UNREACHABLE: 11/1/43/12/1
canonical SHA-256: 3f8a83382d98aed34836670ba2e1fe7fc7e4eb757f5083bb51122eae01bfe2b5
```

A fresh Hello World report also passes its expectation. The saved extended
validation sets remain valid:

- Spring PetClinic: five of five pass
- DaCapo H2: five of five pass
- saved Hello World: five of five pass

The saved Phase 8 fixture reports predate the later fixture expansion and fail
the current expectation as expected. They are classified as superseded; the
fresh fixture is the gate-quality source of truth.

## Storage Matrix

Four fresh macOS/aarch64 images validate full/minimal canonical export and
same-build `embed,split`. All executables run. Separate export and embedded
reports are semantically equal, and each same-build embedded/split pair is
byte-identical:

| Scope | Reports | Used methods | Payload bytes | Envelope bytes | Envelope SHA-256 |
| --- | ---: | ---: | ---: | ---: | --- |
| full | 68 | 24 | 24,733 | 1,730 | `a6267dfd285ad573db80dc27554924b991635846eeeebcb4adfe29f9248a00ed` |
| minimal | 41 | 0 | 10,157 | 1,011 | `fae197e4f03b9feee287a559574ee2cd82a565245c7f45031fa4f05671b37267` |

The new `auto:` adapter loads the real embedded image and also resolves its
split file through the generated `build-artifacts.json`.

Linux/ELF real-image, execution, and strip behavior remain covered by the
Phase 15 custom source build. Phase 16 changes do not alter the ELF section,
symbol, or envelope layout; the expanded cross-platform Python suite covers
the new discovery behavior.

## Reviewable Commit Structure

The final branch is organized by reviewer surface:

1. Native Image IDE report collection and analysis hooks.
2. Configurable canonical, split, and embedded storage.
3. Focused Java fixtures and tests.
4. Storage-neutral command-line inspection and validation tooling.
5. Reproducible size and finalization-cost measurement drivers.
6. Design, validation evidence, and handoff documentation.

The history rewrite preserves the exact final tree and uses
`--force-with-lease` when updating the existing review branch.

## Deferred Production Work

The prototype intentionally leaves these production decisions open:

- PE/COFF embedded storage and extraction
- compatibility guarantees for the envelope and canonical schema
- decoded-payload limits for untrusted artifacts
- compact method-inventory storage and the production default payload scope
- confidentiality, packaging, retention, and redaction policy
- replacing the one-build-at-a-time compiler bridge and direct positive
  inlining hooks if a standard compiler event consumer becomes available

These are documented boundaries, not unclassified Phase 16 regressions.

## Conclusion

Phases 0 through 16 are complete. The branch is ready for review as a validated
prototype. No CI gates are started automatically as part of this phase.
