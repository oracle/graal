# Sulong test cases

Sulong is tested using both self-maintained testsuites and select tests
from external suites. You can run all available testsuites using `mx gate`.
Please note that this command aborts as soon as one testsuite has failed.


## Testsuites

| Tag         | Class name                              | Description                             |
|-------------|-----------------------------------------|-----------------------------------------|
| sulong      | SulongSuite                             | Sulong's internal tests                 |
| interop     | com.oracle.truffle.llvm.tests.interop.* | Truffle Language interoperability tests |
| debug       | LLVMDebugTest                           | Debug support test suite                |
| llvm        | LLVMSuite                               | LLVM 3.2 test suite                     |
| parser      | ParserTortureSuite                      | Parser test using GCC suite             |
| nwcc        | NWCCSuite                               | Test suite of the NWCC compiler v0.8.3  |
| assembly    | InlineAssemblyTest                      | Inline assembler tests                  |
| gcc_c       | GCCSuite                                | GCC 5.2 test suite (C tests)            |
| gcc_cpp     | GCCSuite                                | GCC 5.2 test suite (C++ tests)          |
| gcc_fortran | GCCSuite                                | GCC 5.2 test suite (Fortran tests)      |
| args        | MainArgsTest                            | Tests main args passing                 |
| benchmarks  | ShootoutsSuite                          | Language Benchmark game tests           |
| vaargs      | VAArgsTest                              | Varargs tests                           |
| pipe        | CaptureOutputTest                       | Test output capturing                   |
| callback    | CallbackTest                            | Test calling native functions           |
| type        | -                                       | Test floating point arithmetic          |

The test cases consist of LLVM IR, C, C++, and Fortran files. While
Sulong's Truffle LLVM IR runtime can directly execute the LLVM IR
files it uses Clang and/or GCC to compile the other source files to LLVM IR
before executing them.

### Testgate

You can run specific testsuites by invoking `mx gate --tags [tags]`. For Sulong's
external test suites this command will download all neccessary files to and compile
them using Clang. The downloaded and unpacked files will be in `~/.mx/cache`, the
compiled files are in `mxbuild` subdirectories.

You can find the sources of the internal tests in
`<Sulong base dir>/tests/com.oracle.truffle.llvm.tests.*` and the compiled files
in `<Sulong base dir>/mxbuild/<os>-<arch>/SULONG_TEST_SUITES`.

For easier use there are also some compound tags to execute smaller testsuites together.

| Tag          | Contained tags                                               |
|--------------|--------------------------------------------------------------|
| sulongBasic  | sulong, interop, debug                                       |
| sulongMisc   | benchmarks, type, pipe, assembly, args, callback, vaargs     |

The full `mx gate` command also performs various code quality checks.

| Tag          | Description                                                  |
|--------------|--------------------------------------------------------------|
| fullbuild    | Run Findbugs and create a clean build of Sulong using ECJ    |
| style        | Use various static anylsis tools to ensure code quality      |

#### Options

In order to pass polyglot options to Sulong one needs to specify them as JVM
arguments since `mx gate` does not support passing them directly.

    mx -A-Dpolyglot.llvm.<option> gate

### Unittests

The testsuites can also be executed using `mx unittest <classname>`. This
command expects the selected testsuites to have already been compiled.
The easiest way to build the testsuites is to run `mx gate --tags build_<tag>`.
For example, `mx gate --tags build_sulongBasic` will build all tests that would
be run by `mx gate --tags sulongBasic`.

`mx unittest` also supports running only selected tests of a specific suite. For
example, `test[c/max-unsigned-short-to-float-cast]` is part of the SulongSuite.
You can run only this test using
`mx unittest SulongSuite#test[c/max-unsigned-short-to-float-cast]`.

#### Options

For some testsuites it is necessary to specify required libraries, e.g. `libgfortran.so.3`
which is needed by the fortan tests of the `gcc_fortran` testsuite, manually using the
`polyglot.llvm.libraries` option. Since the `gate` command also uses `unittest`
to actually run the compiled tests you can find the full command it uses by using mx
in verbose mode (`mx -v gate ...`).

Another useful option to `unittest` is `--very-verbose`. This always prints the
test's name to the screen before it is started which can be a great tool to
identify tests are stuck.

Options need to be specified before the selected tests.

    mx <mx options> unittest <unittest/polyglot options> <tests>

### Debugging

To attach a debugger to Sulong tests, run `mx` with the `-d` argument, e.g.
`mx -d unittest SulongSuite` or `mx -d gate --tags sulong`.


## Fortran

Some of our tests are Fortran files. Make sure you have GCC, G++, and GFortran
in version 4.5, 4.6 or 4.7 available.

On the Mac you can use Homebrew:

    brew tap homebrew/versions
    brew install gcc46 --with-fortran
    brew link --force gmp4

For the Fortran tests you also need to provide
[DragonEgg](http://dragonegg.llvm.org/) 3.2 and Clang 3.2.

[DragonEgg](http://dragonegg.llvm.org/) is a GCC plugin with which we
can use GCC to compile a source language to LLVM IR. Sulong uses
DragonEgg in its test cases to compile Fortran files to LLVM IR.
Sulong also uses DragonEgg for the C/C++ test cases besides Clang to get
additional "free" test cases for a given C/C++ file. DragonEgg requires
a GCC in the aforementioned versions.

- Sulong expects to find Clang 3.2 in `$DRAGONEGG_LLVM/bin`
- Sulong expects to find GCC 4.5, 4.6 or 4.7 in `$DRAGONEGG_GCC/bin`
- Sulong expects to find `dragonegg.so` under `$DRAGONEGG` or in `$DRAGONEGG_GCC/lib`


## Reference output

For most test cases, Sulong obtains the reference output (return value and/or
process output) by compiling the source file (or its LLVM IR version)
to machine code. An exception is the LLVM test suite, which offers
`.reference_output` files that we use as reference output.


## Configuration files

Some of the test suites have configuration files that include test cases
contained in a directory of that test suite. Sulong uses these configuration
files (ending with `.include`) mainly for external test suites, for which
not all test cases are supported, to select only a subset of these tests.
The table below shows in `config` which test suites use such configuration
files.


## Test case discovery

The test suites which have configuration files usually offer an option
to execute all test cases that are not included in the `.include` files,
in order to find newly supported test cases. To
exclude files from this discovery, there are also `.exclude` files in
the suite configuration, which are useful for test cases that crash the
JVM process or which will not be supported by Sulong in the near future.
