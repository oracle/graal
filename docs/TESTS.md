# Sulong test cases

You can use `mx su-suite` to run all the test cases. You can selectively
run one or several test suites with `mx su-suite <test suite names>`.
The most important test cases are `mx su-suite sulong` (Sulong's own
test suite which mostly consists of C files), `mx su-suite gcc`
(selected GCC test cases), and `mx su-suite llvm` (selected LLVM test cases).

The `mx su-suite` command is a conventient command that translates to
`mx unittest <test suite class name>` and ensures that all path and options are set.
For example, `mx su-suite sulong` corresponds to `mx unittest SulongSuite`.
If you are in Sulongs root directory, you can use `mx unittest` as a direct
replacement.

To attach a debugger to Sulong tests, run `mx -d unittest SulongSuite` or
`mx -d su-suite sulong`.
To get a verbose output of all tests that run as part of a suite, run
`mx -v su-suite sulong`. This also prints names for all individual tests.
You can use the test names to run a single test of a suite.
For example, `test[c/max-unsigned-short-to-float-cast.c]` is part of the
SulongSuite. You can run this single test using
`mx unittest SulongSuite#test[c/max-unsigned-short-to-float-cast.c]`

The test cases consist of LLVM IR, C, C++, and Fortran files. While
Sulong's Truffle LLVM IR interpreter can directly execute the LLVM IR
files it uses Clang and/or GCC to compile the other source files to LLVM IR
before executing them.

## Test case options

You can pass VM arguments as the last argument to the test runner. For
example, you can use `mx su-suite sulong -Dsulong.Debug=true` to get useful
information for debugging the test cases. With `mx su-options` you can
get a complete list of options that you can use with Sulong.

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

## Test Suites

| test suite       | Class Name             | description                     |
|------------------|------------------------|---------------------------------|
| llvm             | LLVMSuite              | LLVM's test suite               |
| parserTorture    | ParserTortureSuite     | Parser test using GCC suite     |
| interop          | LLVMInteropTest        | Test Truffle interoperability   |
| polyglot         | TestPolyGlotEngine     | Minimal Polyglot engine test    |
| tck              | LLVMTckTest            | Certify Truffle compliance      |
| nwcc             | NWCCSuite              | Test suite of the NWCC compiler |
| assembly         | InlineAssemblyTest     | Inline assembler tests          |
| sulong           | SulongSuite            | Sulong's own primary test suite |
| gcc              | GCCSuite               | GCC's test suite                |
| arguments        | MainArgsTest           | Tests main args passing         |
| shootout         | ShootoutsSuite         | Language Benchmark game tests   |
| sulongcpp38      | SulongCPPSuite         | C++ Exception Handling tests    |

These testsuites are compiled by either the Clang provided by mx or any Clang
in versions 3.2 or 3.3 that are available on the system `PATH`. There are also
`llvm38`, `nwcc38`, `sulong38`, `gcc38` suites which correspond to their
counterparts without version number but are compiled by Clang in a version
between 3.8 and 4.0. The `sulongcpp38` suite depends on such a Clang version
since certain required features are not available in earlier versions.
