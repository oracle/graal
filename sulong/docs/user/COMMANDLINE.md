# LLI Command Options

```
$GRAALVM_HOME/bin/lli [options] <bitcode file> [program args]
```

`--llvm.managed` enable a managed execution mode for LLVM IR code, which means memory
allocations from LLVM bitcode are done on the managed heap (EE only).
[This article](https://medium.com/graalvm/safe-and-sandboxed-execution-of-native-code-f6096b35c360)
explains the managed execution in every detail.

`--print-toolchain-path`: print the path of the LLVM toolchain bundled with GraalVM.
This directory contains compilers and tools that can be used to compile C/C++ programs
to LLVM bitcode for execution with GraalVM (see [COMPILING](COMPILING.md))

`--print-toolchain-api-tool <tool>`: print the path of a tool from the LLVM toolchain.
Valid values for `<tool>` are `CC`, `CXX`, `LD`, `AR`, `NM`, `OBJCOPY`, `OBJDUMP`,
`RANLIB`, `READELF`, `READOBJ` or `STRIP`.

`--print-toolchain-api-paths <path>`: print a search path for the LLVM toolchain.
Valid values for `<path>` are `PATH` and `LD_LIBRARY_PATH`.

`--print-toolchain-api-identifier`: print a unique identifier of the LLVM toolchain.
Different modes of the LLVM runtime (e.g. `--llvm.managed`) might require compilation
of bitcode with a different LLVM toolchain. This identifier can be used as a stable
directory name to store build outputs for different modes.

`-L <path>`/`--llvm.libraryPath=<path>`: a list of paths where GraalVM will search for
library dependencies. Paths are delimited by `:`.

`--lib <libs>`/`--llvm.libraries=<libs>`: a list of libraries to load in addition to
the dependencies of the main binary. Files with a relative path are looked up relative
to `llvm.libraryPath`. Entries are delimited by `:`.

`--version` prints the version and exit.

`--version:graalvm` prints GraalVM version information and exit.

## Expert and Diagnostic Options

Use `--help` and `--help:<topic>` to get a full list of options.
