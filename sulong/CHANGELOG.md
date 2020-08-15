# Version 20.3.0

Changes:

* Use the version of the LLVM toolchain as the LLVM language version (see `lli --version`).

# Version 20.2.0

Changes:

New features:

* Support reading multi-byte values from polyglot arrays explicitly typed as i8 arrays.

* Fail when a function is called over interop with fewer arguments than it expects.

* Support `InteropLibrary.isIdentical` and related messages.

* Added the `--print-toolchain-api-identifier`,  `--print-toolchain-api-tool <tool-name>`
  and `--print-toolchain-api-paths <path-name>` arguments to the `lli` launcher.
  These can be used to query the [Toolchain API](docs/contributor/TOOLCHAIN.md)
  from the command line.

* The [Java API of the Toolchain](projects/com.oracle.truffle.llvm.api/src/com/oracle/truffle/llvm/api/Toolchain.java)
  now supports requesting locations via the `#getPaths()` method. For example
  the location of the toolchain executables or libraries. See the JavaDoc for
  more details.

* Added `llvm/api/toolchain.h` header for accessing the [Toolchain](docs/contributor/TOOLCHAIN.md)
  from C code.

Removed:

* Removed the `--llvm.sourcePath` option (deprecated since 19.0), use
  `--inspect.SourcePath` instead.

* Removed support for the application/x-llvm-ir-bitcode-base64 mime-type
  (deprecated since 19.0).

# Version 20.1.0

Changes:

* `libc++` and `libc++abi` are now shipped as bitcode libraries. For building
  those `cmake` is required to be installed.

* C++ support (libsulong++) is no longer loaded by default. Instead, it is
  enabled if `libc++` is loaded. Executables (ELF, Mach-O) compiled with
  the toolchain usually have a dependency on this library and will continue
  to work.  Plain bitcode files, which do not allow to specify dependencies,
  may fail with an error similar to the following:
  ```
  Global variable _ZNSt3__15ctypeIcE2idE is declared but not defined.
  ```
  This can be solved by either compiling it into a proper executable, or by
  specifying the dependency on the command line (e.g., `lli --lib libc++.so.1 ...`
  on Linux or `lli --lib libc++.1.dylib` on macOS).

* Module initialization order is now only based on the dependencies recorded in the
  ELF/Mach-O file instead of looking at imported symbols. Consequently, the order
  plain bitcode files are initialized might change since they do not allow recording
  dependencies. If the initialization order is important, the suggested approach to
  use ELF/Mach-O files. The [Toolchain](docs/contributor/TOOLCHAIN.md) can help creating those.

* Cover more cases when calling functions that receive structs by value across interop
  boundaries.

* Bitcode execution now supports on-stack-replacement of loops, i.e., compiling
  long-running loops before the surrounding function is compiled.

# Version 20.0.0

Changes:

* The [Toolchain](docs/contributor/TOOLCHAIN.md) is now based on LLVM 9.0.0.

New features:

* Support accessing `llvm-ar`, `llvm-nm`, `llvm-objcopy`,
  `llvm-objdump`, `llvm-ranlib`, `llvm-readelf`, `llvm-readobj` and
  `llvm-strip` via the [toolchain](docs/contributor/TOOLCHAIN.md).

# Version 19.3.0

Changes:

* The [Toolchain](docs/contributor/TOOLCHAIN.md) is no longer experimental.

New features:

* Added `mx extract-bitcode` command for extracting embedded bitcode.
  Usage: `mx extract-bitcode inputFile.so outputFile.bc`, where `inputFile` can
  be can be a file of any supported format such as wrapped bitcode or ELF/Mach-O
  with an embedded bitcode sections.

* Added preliminary support for parsing bitcode produced by LLVM 9.

* We are now shipping native `libc++` and `libc++abi` libraries with the LLVM runtime,
  so users no longer need to install them manually.

* We added linker support to the LLVM toolchain. A path to the linker can be requested
  via the `LD` tool name.

* Added the `--print-toolchain-path` command line flag to `lli` to print the location
  of the toolchain on the standard output.

* The toolchain Java API can be used to query the toolchain path using the `PATH` name.

* Added intrinsics for pthread thread management functions.

Improvements:

* Clang and other LLVM tools are no longer required to be installed for building
  the GraalVM LLVM runtime. Instead, the LLVM distribution bundled with the
  [Toolchain](docs/contributor/TOOLCHAIN.md) is used.

# Version 19.2.0

New features:

* Support locating dynamic libraries relatively using (`rpath`).
* Preliminary support for compiling to bitcode using the LLVM toolchain.
  See [docs/contributor/TOOLCHAIN.md](docs/contributor/TOOLCHAIN.md) for more details.
  *WARNING*: The toolchain is experimental. Functionality may be added,
  changed or removed without prior notice.
* Support for simple pointer arithmetics with foreign objects.
  In addition to offset arithmetics, GraalVM now supports "negated" pointers and
  simple bitmask operations (typically used for alignment operations).

Improvements:

* Improved display of pointers to foreign objects in the LLVM debugger.
  When inspecting pointer values that point somewhere inside a foreign object,
  the debugger now allows inspecting the original foreign object, not just the
  contents of the pointer itself.

# Version 19.1.0

Fixes:

* Calling exit(...) in an embedded context is now a catchable PolyglotException.
* Skip source path entries that we're not allowed to access.
  Previously, when running in an embedded context with restricted access to the
  file system, the LLVM engine threw an exception when it could not read the
  source files, even if no debugger was attached. Now it will still run. Only
  when a debugger is attached, an error is reported when the source file can't
  be accessed.

# Version 19.0.0

Changes:

* Moved `polyglot.h` into the `include` subdirectory.
* Remove language version from LLVMLanguage.
  The LLVM engine in GraalVM is always released in sync with GraalVM, no need for
  a separate version number.

Fixes:

* Don't use host interop for LLVM engine internals.
  This means the LLVM engine now works correctly with minimal permissions. In particular,
  the host interop permission is not needed anymore.

# Version 1.0.0 RC15

New features:

* Preliminary support for bitcode produced by LLVM 8.

# Version 1.0.0 RC14

Changes:

* Various bug fixes.

# Version 1.0.0 RC13

New features:

* Support for embedded bitcode in Mach-O files.
  We support bitcode in the `__bitcode` section of Mach-O object files,
  as well as bitcode files in an embedded xar archive in the `__bundle` section of
  executables or dylibs.

Changes:

* Update libc++/libc++abi imports to 5.0.2 (no actual code changes).

# Version 1.0.0 RC12

Removed:

* Removed deprecated `truffle_*` builtin functions.
* Dropped binary compatibility to bitcode compiled with very old `polyglot.h` versions (1.0.0-RC2 or older).

Bugfixes:

* Read-only globals are now allocated in read-only memory.

# Version 1.0.0 RC11

Changes:

* Stack traces report line numbers even if the original source file can not
  be found.
* Don't allow `polyglot_eval` for internal languages.

Deprecations:

* Deprecate `--llvm.sourcePath` option in favour of the more general
  `--inspect.SourcePath` option.

# Version 1.0.0 RC10

New features:

* New option `--llvm.sourcePath` to specify search path for source files when
  debugging.
* Make debugging of internal functions possible. This is disabled by default,
  enable with the `--inspect.Internal` option.

Changes:

* Uncaught exceptions are now output on stderr (previously stdout).
* Hide internal functions (e.g. `_start` or `__cxa_throw`) in stack traces.

# Version 1.0.0 RC9

No changes.

# Version 1.0.0 RC8

New features:

* Expert-level option `--llvm.loadC++Libraries=false` to disable automatic
  loading of C++ standard libraries.

# Version 1.0.0 RC7

New features:

* New polyglot builtin `polyglot_has_member`.

Changes:

* Removed support for implicit polyglot types for local variables
  as the availability of type information is not guaranteed.
  Explicit polyglot casts are now strictly required (`polyglot_as_typed`).
  See [docs/contributor/INTEROP.md](docs/contributor/INTEROP.md) and [polyglot.h](projects/com.oracle.truffle.llvm.libraries.bitcode/include/polyglot.h)
  for more details.
* Support for IR-level tracing.
* Preliminary support for LLVM 7.

# Version 1.0.0 RC6

New features:

* Support for IR-level debugging.
* New polyglot cast functions for primitive array types.
* Support for function pointer members in `polyglot_as_typed`.

# Version 1.0.0 RC5

New features:

* Support the `__builtin_debugtrap` function based on LLVM's `@llvm.debugtrap`
  intrinsic

Improvements:

* Support "zero-length array at end of struct" pattern when accessing polyglot
  values as structs.
* Improved performance of global variable access.
* Improved support for vectorized bitcode operations.

# Version 1.0.0 RC4

No changes.

# Version 1.0.0 RC3

New features:

* Dynamic polyglot cast functions `polyglot_from_typed` and `polyglot_as_typed`.

# Version 1.0.0 RC2

New features:

* Use dynamic linker semantics when loading multiple bitcode files.
* Support ELF files with embedded LLVM bitcode.
* Pointers to bitcode functions can now be called from other languages.

New polyglot builtins:

* `polyglot_eval_file`
* `polyglot_java_type`
* `polyglot_remove_member`
* `polyglot_remove_array_element`
* `polyglot_can_instantiate`
* `polyglot_new_instance`

Improvements:

* Support polyglot values in all pointer operations.

# Version 1.0.0 RC1

New features:

* New API for conversion of user-defined native structs from and to polyglot
  values.

# Version 0.33

New features:

* Support for bitcode of LLVM version 6.0.
* New API for accessing polyglot values from C (`polyglot.h`).

Changes:

* The `libc++` dependency is now optional and only required for running C++
  code. `libc++abi` is no longer required.
