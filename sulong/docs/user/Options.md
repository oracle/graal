# LLI Command Options

The syntax to execute programs in LLVM bitcode format with GraalVM is:
```shell
lli [LLI options] [GraalVM options] [polyglot options] <bitcode file> [program args]
```
Here, `<bitcode file>` is a compiled program with embedded LLVM bitcode.

The following options to `lli` are available:

* `--llvm.managed`: enable a managed execution mode for LLVM IR code, which means memory
allocations from LLVM bitcode are done on the managed heap. Learn more from [Limitations and Differences to Native Execution](native-execution.md). Note: Managed execution mode for LLVM bitcode is possible with GraalVM Enterprise only.

* `--print-toolchain-path`: print the path of the LLVM toolchain bundled with GraalVM.
This directory contains compilers and tools that can be used to compile C/C++ programs
to LLVM bitcode for execution on GraalVM.

* `--print-toolchain-api-tool <tool>`: print the path of a tool from the LLVM toolchain.
Valid values for `<tool>` are `CC`, `CXX`, `LD`, `AR`, `NM`, `OBJCOPY`, `OBJDUMP`,
`RANLIB`, `READELF`, `READOBJ`, or `STRIP`.

* `--print-toolchain-api-paths <path>`: print a search path for the LLVM toolchain.
Valid values for `<path>` are `PATH` and `LD_LIBRARY_PATH`.

* `--print-toolchain-api-identifier`: print a unique identifier of the LLVM toolchain.
Different modes of the LLVM runtime (e.g., `--llvm.managed`) might require compilation
of bitcode with a different LLVM toolchain. This identifier can be used as a stable
directory name to store build outputs for different modes.

* `-L <path>`/`--llvm.libraryPath=<path>`: a list of paths where GraalVM will search for
library dependencies. Paths are delimited by `:`.

* `--lib <libs>`/`--llvm.libraries=<libs>`: a list of libraries to load in addition to
the dependencies of the main binary. Files with a relative path are looked up relative
to `llvm.libraryPath`. Entries are delimited by `:`.

* `--version`: print the version and exit.

* `--version:graalvm`: print the GraalVM version information and exit.

## Expert and Diagnostic Options
Use `--help` and `--help:<topic>` to get a full list of options.
