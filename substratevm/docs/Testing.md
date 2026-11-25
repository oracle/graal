# Testing Native Image

This guide describes the test projects in the `substratevm` suite and how to run and debug their tests.

## Test Projects

Tests are organized into mx projects based on where and how they run.
These projects are defined in `suite.py` and can contain multiple Java packages.

- Project `com.oracle.svm.test.junit.hosted`: Tests for code that runs on HotSpot, such as image-builder and driver code.
  `@Test` methods run on HotSpot with `mx unittest`.
- Project `com.oracle.svm.test.junit.nativeimage`: Native Image-specific tests.
  `@Test` methods run in Native Image with `mx native-unittest`, including JUnit lifecycle callbacks and rules.
- Project `com.oracle.svm.test.junit.shared`: JUnit tests for behavior that should agree on HotSpot and Native Image.
  `@Test` methods run on HotSpot with `mx unittest` and in Native Image with `mx native-unittest`.

## Main Test Runners

Run commands from the _substratevm_ directory.
Compile the Java sources before running tests, including after changing a test:

```shell
mx build
```

### `mx unittest`

Run `mx unittest` without arguments to execute the `@Test` methods from the `com.oracle.svm.test.junit.hosted` and `com.oracle.svm.test.junit.shared` projects on HotSpot.
To run one test class:

```shell
mx unittest com.oracle.svm.hosted.lambda.LambdaParserTest
```

When run from the `substratevm` suite, `mx unittest` only selects tests from these two projects.
Explicit test names, suite selections, and filters can narrow this selection but cannot include tests from other projects or dependency suites.
The command fails if no tests execute, including when all selected tests are ignored.
For tests that require dedicated setup, see [Standalone Tests](#standalone-tests).

### `mx native-unittest`

Run `mx native-unittest` without arguments to execute the native JUnit tests, including the tests in the `com.oracle.svm.test.junit.nativeimage` and `com.oracle.svm.test.junit.shared` projects.
Explicit selectors can also run native JUnit tests from other suites. For example, the `truffle_unittests` gate uses them to run compiler and Truffle tests in Native Image.
To run one test class:

```shell
mx native-unittest com.oracle.svm.test.junit.nativeimage.NativeImageResourceTest
```

To run a shared test on both HotSpot and Native Image, use both runners:

```shell
mx unittest com.oracle.svm.test.junit.shared.reflection.ArraySetTest
mx native-unittest com.oracle.svm.test.junit.shared.reflection.ArraySetTest
```

#### Features of `mx native-unittest`

`mx native-unittest` groups tests by their required image-build arguments and builds one executable per group.
Tests without custom arguments share one executable.

- **JUnit support:** Tests can use JUnit assertions, lifecycle callbacks, and rules.
- **Image-build options:** Use `@NativeImageBuildArgs` to pass additional options when `mx native-unittest` builds the test image.

The CE configuration uses runtime initialization for classes in the Java package `com.oracle.svm.test` and its subpackages.
Some classes are explicitly configured for build-time initialization.

Tests in the same executable share process state.
Tests must not leave shared state in a condition that could cause other tests to fail.
Any required cleanup can use JUnit lifecycle callbacks or rules.

## Debugging Tests

Start with one failing test to reduce build time and output.
For native executables, building with `-O0` disables compiler optimizations and usually makes stepping through code and inspecting variables easier.

### `mx unittest`

Start the test JVM suspended and listening for a Java debugger on port 8000:

```shell
mx --dbg 8000 unittest com.oracle.svm.hosted.lambda.LambdaParserTest
```

Attach your IDE's Java debugger to that port and set breakpoints in the test or the code under test.

### `mx native-unittest`

Build with debug information and preserve the executable after the test run:

```shell
mx native-unittest --preserve-image com.oracle.svm.test.junit.nativeimage.NativeImageResourceTest --build-args -g -O0
```

The runner prints the executable path in its `Running:` output.
To debug on Linux, change to the executable's directory and run:

```shell
gdb --args ./svmjunit --run-explicit com.oracle.svm.test.junit.nativeimage.NativeImageResourceTest
```

For native debugging commands and breakpoint examples, see [Debug Native Executables with GDB](../../docs/reference-manual/native-image/guides/debug-native-executables-with-gdb.md).

### Image Building

To debug code that runs during image building, such as feature callbacks, use a Java debugger.
Pass `--debug-attach=8000` after `--build-args` to `mx native-unittest`.
The image-builder JVM waits for a debugger to attach on port 8000.

## Standalone Tests

These tests use dedicated mx commands and are excluded from the default `mx unittest` and `mx native-unittest` selections:

- Java agent tests (project `com.oracle.svm.test`). Run with `mx javaagenttest`.
- Class-initialization tests (project `com.oracle.svm.test`). Run with `mx clinittest`.
- Standalone points-to analysis tests (project `com.oracle.graal.pointsto.standalone.test`).
  Run with `mx standalone-pointsto-unittest`.
- Debug-information and GDB tests (project `com.oracle.svm.test.debug`).
  Run with `mx debuginfotest`, `mx layereddebuginfotest`, `mx gdbdebughelperstest`, or `mx runtimedebuginfotest`.
- Java desktop and native library linkage tests (project `com.oracle.svm.test.junit.desktop`).
  Run with `mx gate --tags java_desktop_integration`.
