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
  See [docs/INTEROP.md](docs/INTEROP.md) and [polyglot.h](projects/com.oracle.truffle.llvm.libraries.bitcode/include/polyglot.h)
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
