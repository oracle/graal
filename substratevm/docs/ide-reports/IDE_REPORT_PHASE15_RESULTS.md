# IDE Report Phase 15 Results

Date: 2026-06-28

Phase 15 adds and validates Linux/ELF embedded IDE report storage in this
repository.

## Environment

Validation ran in a Linux/aarch64 container with:

- Oracle Linux 10
- LabsJDK `25.0.3+9-jvmci-25.1-b19`, obtained with `mx fetch-jdk`
- GCC 14.3.1
- GNU binutils 2.41
- Python 3.12
- custom current-branch `mx --env ni-ce build`
- GraalVM CE `25.2.4-dev+9.1`

The released GraalVM from the container image was used only to bootstrap the
environment. It was rejected as a Graal build JDK and was never used for the
fixture validation. Every image below was built with the custom distribution:

```text
GRAALVM_B0D7B51F55_JAVA25
```

## Implementation

The Java embedding backend now accepts ELF and Mach-O. Both formats store:

```text
envelope bytes
8-byte little-endian envelope length
```

ELF uses:

```text
section: .svm_ide_report
section type: PROGBITS
section flags: ALLOC
alignment: 8
start symbol: ide_report
length symbol: ide_report_length
```

The Python `image:` source now dispatches by object-file magic. The new
memory-mapped ELF64 reader validates:

- little-endian ELF64 executable or shared-object headers
- section and program-header bounds
- the unique `.svm_ide_report` section
- allocated, non-writable, non-instruction section flags
- a non-writable containing load segment
- global object locator symbols from `.dynsym` or `.symtab`
- symbol section indices and addresses
- the adjacent eight-byte length field
- envelope bounds, checksum, and canonical payload through the existing loader

The neutral `ReportBundle` model and all higher-level CLI behavior remain
unchanged.

## Linux Fixture Matrix

Three minimal-scope fixture images were built from the custom distribution:

| Storage | Native Image stripping | Purpose |
| --- | --- | --- |
| `split` | default | portable Linux side-file path |
| `embed,split` | default | real stripped ELF and same-build byte identity |
| `embed,split` | disabled | source for explicit strip operations |

All three executables run successfully. Each report contains 41 records, no
used-method inventory, and deterministic gzip compression.

The report bytes are identical across all three builds:

```text
canonical payload: 10,157 bytes
payload SHA-256: 531c43de7476f5bfd2246bc7a114f9d220e4632100212e28b8254afe3ba9bf67
envelope: 1,013 bytes
envelope SHA-256: 6a40df8137dfd4be73b668b5f974cee8e7216a90ad78ca44ca87de8de731058f
```

The split-only executable is 42,011,608 bytes and has no
`.svm_ide_report` section. The default-stripped embedded executable is
42,011,688 bytes, an 80-byte filesystem delta in this layout. As in the
Phase 14 Mach-O measurements, existing alignment slack means that image-size
growth is not necessarily equal to section content size for small payloads.

## ELF Evidence

The default-stripped image reports:

```text
[16] .svm_ide_report PROGBITS 00000000010251b0 10251b0 0003fd 00 A 0 0 8
```

The dynamic symbol table retains:

```text
ide_report         value 0x10251b0, size 1013, section 16
ide_report_length  value 0x10255a5, size 8, section 16
```

GNU ld maps the section into the same `R E` load segment as `.rodata`. The
section itself has no `EXECINSTR` flag and is not writable. This matches the
existing Mach-O design, where the non-instruction report section lives in
`__TEXT`.

Loading the real image through `image:` produces the same 41-record bundle as
`split:`. In every `embed,split` build, the extracted envelope is byte-identical
to `<image>.ide-report`.

## Strip Behavior

The unstripped source image is 42,204,560 bytes. Copies were processed with
GNU `objcopy`:

| Operation | Result bytes | Section | Dynamic symbols | Extraction | Executes |
| --- | ---: | --- | --- | --- | --- |
| none | 42,204,560 | retained | retained | pass | pass |
| `--strip-debug` | 42,075,600 | retained | retained | pass | pass |
| `--strip-all` | 42,011,688 | retained | retained | pass | pass |

Native Image's default-stripped output has the same size as the explicit
`--strip-all` copy and also passes section, symbol, extraction, envelope, and
execution checks. ELF embed storage therefore survives the production-default
strip path.

## macOS Regression

After introducing format dispatch, a fresh macOS/aarch64 minimal
`embed,split` fixture still:

- runs successfully
- loads 41 records through `image:`
- has no used-method inventory
- produces byte-identical embedded and split 1,011-byte envelopes

## Focused Tests

The Python suite now contains 44 passing tests. New coverage includes:

- ELF image extraction and generic object-format dispatch
- missing ELF locator symbols
- writable report sections
- writable containing load segments
- executable report sections
- read-only executable load segments containing non-executable report sections
- preservation of all existing Mach-O extraction behavior

The focused Java suite contains 14 passing tests after rebuilding `SVM_TESTS`.
The ELF test constructs the object backend and checks the allocated-only
section flags plus both global symbol offsets and sizes. It also retains the
Mach-O support check and unsupported-platform diagnostic for PE/COFF.

Repository validation also passes:

- strict Python formatting and bytecode compilation
- strict Eclipse formatting and Checkstyle
- forced ECJ and javac builds with warnings treated as errors for the hosted
  implementation and test projects
- the production SVM distribution build on macOS
- the complete custom `ni-ce` distribution build on Linux

## Reproduction Outline

Build the custom Linux distribution from the `vm` suite:

```bash
mx fetch-jdk labsjdk-ce-latest
mx --java-home=<labsjdk> --env ni-ce build
mx --java-home=<labsjdk> --env ni-ce graalvm-home
```

Run `mx --env ni-ce ide-report-fixture` from the `vm` suite for each storage
configuration. Pass the storage options after `--` and keep them between
`-H:+UnlockExperimentalVMOptions` and `-H:-UnlockExperimentalVMOptions`.
Inspect images with `readelf -SW`, `readelf -sW`, and `readelf -lW`; load the
outputs through `image:` and `split:`; execute every resulting fixture.

## Conclusion

Phase 15 is complete. Split storage remains portable on Linux, while embedded
storage now has a validated ELF implementation whose section, symbols,
extraction, byte-equivalence contract, execution, and strip behavior match the
established Mach-O design. Phase 16 can proceed with final review and PR
preparation.
