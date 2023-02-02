# Quick Start

List all available gate tasks:
```
mx gate --summary --dry-run
```

Run the gate tasks of interest:

```
mx gate --tags sulongBasic,llvm,nwcc
```

The example above runs a basic collection of sulong tests and tests from the LLVM and the NWCC test suites:

# Sulong Gate

Most of the continuous integration testing of Sulong is driven by `mx gate`.
With `mx gate`, actions are organized in _tasks_ with a unique name.
Usually, a task calls out to other tools (e.g. JUnit, Spotbugs, etc.) to do the actual work.
Tasks can be associated with an arbitrary number of tags.
The `--tags` option restricts the tasks ran by `mx gate`.
To run all available tasks use `mx gate`.
Please note that this command aborts as soon as one test has failed.
You can get a summary of gate jobs without executing anything:

```
mx gate --summary --dry-run
```

The Sulong gate contains general tasks which are common for many `mx` suites,
such as building the sources and various style checks, and tasks that test functionality of Sulong.
These Sulong specific tasks start with `Test`.
The task filter (`-t`/`--task-filter`) can be used to only show these:

```
mx gate --summary --dry-run -t Test
```

This will print a list including the _task name_, a _description_ and the _tags_ for the gate task:

```
Gate task summary:
...
   TestSulong    Sulong's internal tests  (JUnit SulongSuite)     [sulong, sulongBasic, sulongCoverage, run_sulong, run_sulongBasic, run_sulongCoverage]
...
```

### Test Sources

Usually, a Sulong test task executes LLVM Bitcode and verifies the result.
Most of the test cases exist in the form of LLVM Assembly, C, C++, and Fortran files,
which need to be translated to Bitcode before the test can run them.
Building the test sources is not done as part of the test task, but in a separate build task.
Those build tasks start with `Build_`. Again, the task filter is useful:

```
mx gate --summary --dry-run -t Build_
...
Gate task summary:
...
   Build_SULONG_STANDALONE_TEST_SUITES  Build SULONG_STANDALONE_TEST_SUITES  [sulong, sulongBasic, sulongCoverage, build_sulong, build_sulongBasic, build_sulongCoverage]
...
```

The compiled test sources are organized in [_distributions_](https://github.com/graalvm/mx/blob/master/docs/layout-distributions.md),
and live in `<Sulong base dir>/mxbuild/<os>-<arch>/<TEST_DISTRIBUTION_NAME>`.
The `mx paths` command helps to find the exact location (in the example for the distribution `SULONG_STANDALONE_TEST_SUITES`):

```
mx paths --output SULONG_STANDALONE_TEST_SUITES
```

Sulong is tested using both self-maintained test suites and selected tests from external suites.
The build task takes care of downloading all necessary sources for external test suites.
The downloaded external sources will be stored in the `~/.mx/cache` directory.

### Test Harness

The test harness runs the test Bitcode via Sulong and verifies that the result is what we expect.
All our test harnesses are based on [JUnit](https://junit.org) test classes.
There are two categories of JUnit tests, **standalone tests** and **embedded tests**.

#### Standalone Tests

For standalone tests, the test harness executes a Bitcode program via Sulong and compares the
result (standard output, standard error, return code) against a reference program that is executed natively.
These tests can also be executed without the test harness by simply executing the Bitcode via the `lli` launcher.
Usually, standalone tests execute all eligible files found in the _test distribution_.
Thus, the JUnit test class does not need to be modified when creating a new test.
Adding the new test to the _test distribution_ is sufficient.

#### Embedded Tests

For embedded tests, there is no native reference executable because they cover functionality that is specific
to [GraalVM embedding](https://www.graalvm.org/reference-manual/llvm/Interoperability/).
For these tests, the JUnit test class takes care of verifying the result.
Therefore, new tests are not picked up automatically by the test class.
In addition to adding the new Bitcode to the _test distribution_, the test class needs to be modified
to pick up the new file, execute it and verify the result.

### Running the Gate

Running all of `mx gate` is quite time-consuming and not even supported on all platforms.
You can run specific tags by invoking `mx gate --tags [tags]`.

For example to run the `TestSulong` task, execute the following:

```
mx gate --tag sulong
```

The _tags_ that correspond to the _task name_ (e.g. tag `sulong` and task `TestSulong`),
will take care of building the required test sources.
Again, `--dry-run` is useful for checking what would be done:

```
mx gate --summary --dry-run --tag sulong
...
    Build_SULONG_STANDALONE_TEST_SUITES  Build SULONG_STANDALONE_TEST_SUITES           [sulong, sulongBasic, sulongCoverage, build_sulong, build_sulongBasic, build_sulongCoverage]
    TestSulong                           Sulong's internal tests  (JUnit SulongSuite)  [sulong, sulongBasic, sulongCoverage, run_sulong, run_sulongBasic, run_sulongCoverage]
```

For easier use there are also some compound tags to execute multiple task together:

* `sulongBasic`: tasks that test basic functionality that is expected to run on all platforms in all configurations.
* `sulongMisc`: specific features that might require special setup or only make sense on certain platforms (most likely linux/amd64).

The full `mx gate` command also performs various code quality checks.

* `style`: Use various static analysis tools to ensure code quality, including code formatting and copyright header checks.
* `fullbuild`: Build Sulong with ECJ as well as with Javac and run Spotbugs.

### Running JUnit Tests without `mx gate`

The Sulong test tasks execute JUnit tests by calling the `mx unittest` command.
The description of the `mx gate --summary --dry-run` table mentions which JUnit classes will be executed.
Sometimes it is easier to execute a test directly via `mx unittest`:

```
mx unittest SulongSuite
```

`mx unittest` also supports running only selected tests of a specific test class.
For example, `test[c/max-unsigned-short-to-float-cast.c.dir]` is part of the `SulongSuite` JUnit test class.
You can run only this test via the following command line:
```
mx unittest SulongSuite#test[c/max-unsigned-short-to-float-cast.c.dir]
```
The gate calls `mx unittest` with the `--very-verbose` option, which will print the individual test names.
Another useful `mx unittest` flag is `--color`, which makes the output easier to read.


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

## Test Exclusion

Not all tests work under all circumstances. Especially external test suites,
which are not under our control, contain many tests that are not relevant for us (e.g. because they test the C parser).
Other tests only work on certain platforms. To solve this, we maintain `.exclude` files to skip such tests.
Tests that should be excluded when executing a JUnit test class `MyTestClass` should be listed in a file in `tests/configs/MyTestClass/*.exclude`.
The file name does not matter as long as it ends with `.exclude`.
To support platform specific excludes, the `os_arch/<os>/<arch>` subdirectories are only processed for the specific
operating system and architecture. For both, `<os>` and `<arch>`, `others` can be used to implement the "else" case.

### Exclusion Format

The exclusion file should contain one entry per line. Lines that start with `#` are ignored.
The precise format of the exclude entry depends on the test style.

#### Standalone Tests

For _standalone tests_, the entry corresponds to the test folder that contains the Bitcode and reference executable.
If executed with `mx gate` or via `mx unittest --very-verbose`, test folder is printed in square brackets:

```
$ mx unittest --very-verbose SulongSuite
MxJUnitCore
JUnit version 4.12
Using TestEngineConfig service Native
com.oracle.truffle.llvm.tests.SulongSuite started (1 of 1)
  test[bitcode/anon-struct.ll.dir]: Passed 603.9 ms
  test[bitcode/selectConstant.ll.dir]: Passed 36.2 ms
...
```

Thus, the exclude file should contain a line with `bitcode/anon-struct.ll.dir` to exclude the first test.

#### Embedded Tests

For _embedded tests_, the exclude file should contain the _test method_ (e.g., the Java method
annotated with `@Test`). Example:

```
$ mx unittest --very-verbose CxxMethodsTest
MxJUnitCore
JUnit version 4.12
Using TestEngineConfig service Native
com.oracle.truffle.llvm.tests.interop.CxxMethodsTest started (1 of 1)
  testGettersAndSetters: Passed 21.2 ms
  testInheritedMethodsFromSuperclass: Passed 3.2 ms
  testWrongArity: Passed 4.6 ms
  testOverloadedMethods: Passed 2.1 ms
  testMemberFunction: Passed 5.8 ms
  testNonExistingMethod: Passed 0.5 ms
  testAllocPoint: Passed 0.1 ms
  testConstructor: Passed 0.3 ms
com.oracle.truffle.llvm.tests.interop.CxxMethodsTest finished 50.9 ms
```

To exclude the first test, the exclude file should contain a line with `testGettersAndSetters`.

_Note:_ embedded JUnit test classes must be annotated in order to make the exclusion mechanism work.
```java
@RunWith(CommonTestUtils.ExcludingTruffleRunner.class)
```
