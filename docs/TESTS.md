# Sulong test cases

You can use `mx su-tests` to run all the test cases. You can selectively
run one or several test suites with `mx su-tests <test suite names>`.
The most important test cases are `mx su-tests sulong` (Sulong's own
test suite which mostly consists of C files), `mx su-tests gcc`
(selected GCC test cases), and `mx su-tests llvm` (selected LLVM test cases).
To display the test suite names and further options, use `mx su-tests -h`.

The test cases consist of LLVM IR, C, C++, and Fortran files. While
Sulong's Truffle LLVM IR interpreter can directly execute the LLVM IR
files it uses Clang and/or GCC to compile the other source files to LLVM IR
before executing them.

## Test case options

You can pass VM arguments as the last argument to the test runner. For
example, you can use `mx su-tests sulong -Dsulong.Debug=true` to get useful
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

## Local vs. remote tests

The test suite contains local and remote tests. Local tests are executed
in the same JVM process as the test suite is started. Remote test cases
are executed in a separate (remote) JVM process.

We start such remote processes to capture `stdout` and `stderr` of the tests.
Sulong can call native functions such as `printf` that do not use Java's
`System.out` or `System.err`, but directly communicate with the operating
system to print the output. By starting the remote process we can capture
the process output and then compare it with our reference output.

Thus, remote test cases compare both the exit values and the process output,
while local test cases only compare the exit value with the reference value.

The table below shows in `type` which test suites are local, and which ones
are remote test cases.

## Test Suites

| test suite | type   | config | description                              |
|------------|--------|--------|------------------------------------------|
| llvm       | remote | yes    | LLVM's test suite                        |
| llvm-bc    | remote | yes    | LLVM's test suite for BitCode parser     |
| interop    | local  | no     | Truffle interoperability test suite      |
| polyglot   | local  | no     | Minimal Polyglot engine test             |
| tck        | local  | no     | Test suite to certify Truffle compliance |
| lifetime   | local  | no     | Tests the lifetime of variables          |
| nwcc       | remote | yes    | Test suite of the NWCC compiler          |
| asm        | local  | no     | Inline assembler tests                   |
| sulong     | local  | no     | Sulong's own primary test suite          |
| gcc        | local  | yes    | GCC's test suite                         |
| gcc-bc     | local  | yes    | GCC's test suite for BitCode parser      |
| main-arg   | local  | no     | Tests arguments to the main function     |
| bench      | local  | no     | Language Benchmark game benchmarks       |
| compile    | local  | yes    | GCC's compile-only test cases            |
| argon2     | local  | no     | Argon2 hash test case                    |
| types      | local  | no     | Type tests for the 80 bit float          |
