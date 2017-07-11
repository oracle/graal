# Sulong test cases

Sulong's primary testsuite can be executed using `mx unittest SulongSuite`.
Additionally, Sulong is also tested with selected external testsuites that
can be executed using `mx su-suite`. Optional arguments to this command
specify the testsuites to be executed. `mx su-suite -h` provides a list
of available suites. The most important testsuites (beside the SulongSuite)
are the `gcc38` and `llvm38` suites which consist of selected test cases from
the official GCC 5.2 and LLVM 3.2 testsuites.

To attach a debugger to Sulong tests, run `mx -d unittest SulongSuite` or
`mx -d su-suite gcc38`.
To get a verbose output of all tests that run as part of a suite, run
`mx -v su-suite gcc38`. This also prints names for all individual tests.

You can use the test names to run a single test of a suite.
For example, `test[c/max-unsigned-short-to-float-cast.c]` is part of the
SulongSuite. You can run this single test using
`mx unittest SulongSuite#test[c/max-unsigned-short-to-float-cast.c]`.

The test cases consist of LLVM IR, C, C++, and Fortran files. While
Sulong's Truffle LLVM IR interpreter can directly execute the LLVM IR
files it uses Clang and/or GCC to compile the other source files to LLVM IR
before executing them.

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

`mx su-pulldragonegg` downloads both DragonEgg and Clang. You can set
certain environment variables to tell Sulong where to find the binaries:

- Sulong expects to find Clang 3.2 in `$DRAGONEGG_LLVM/bin`
- Sulong expects to find GCC 4.5, 4.6 or 4.7 in `$DRAGONEGG_GCC/bin`
- Sulong expects to find `dragonegg.so` under `$DRAGONEGG` or in `$DRAGONEGG_GCC/lib`

On some versions of Mac OS X, `gcc46` may fail to install with a segmentation
fault. You can find more details and suggestions on how to fix this here.

However you install GCC on the Mac, you may then need to manually link the
gcc libraries we use into a location where they can be found, as
DYLD_LIBRARY_PATH cannot normally be set on the Mac.

    ln -s /usr/local/Cellar/gcc46/4.6.4/lib/gcc/4.6/libgfortran.3.dylib /usr/local/lib

## Test case options

You can pass VM arguments as the last argument to the test runner. For
example, you can use `mx su-suite llvm38 -Dpolyglot.llvm.debug=true` to get useful
information for debugging the test cases.

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
in order to find newly supported test cases (see `mx su-options`). To
exclude files from this discovery, there are also `.exclude` files in
the suite configuration, which are useful for test cases that crash the
JVM process or which will not be supported by Sulong in the near future.

## External test suites

| test suite       | Class Name             | description                     |
|------------------|------------------------|---------------------------------|
| llvm             | LLVMSuite              | LLVM's test suite               |
| parserTorture    | ParserTortureSuite     | Parser test using GCC suite     |
| interop          | LLVMInteropTest        | Test Truffle interoperability   |
| polyglot         | TestPolyGlotEngine     | Minimal Polyglot engine test    |
| tck              | LLVMTckTest            | Certify Truffle compliance      |
| nwcc             | NWCCSuite              | Test suite of the NWCC compiler |
| assembly         | InlineAssemblyTest     | Inline assembler tests          |
| gcc              | GCCSuite               | GCC's test suite                |
| args             | MainArgsTest           | Tests main args passing         |
| shootout         | ShootoutsSuite         | Language Benchmark game tests   |
| vaargs           | VAArgsTest             | Varargs tests                   |

These testsuites are compiled by any Clang in versions 3.2 or 3.3 that are
available on the system `PATH`. There are also `llvm38`, `nwcc38`, `gcc38` suites
that are compiled by any version of Clang between 3.2 and 4.0 that is available.
