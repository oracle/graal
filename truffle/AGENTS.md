# Truffle Agent Reference

## Build & Test

Read `mx.truffle/suite.py` to familiarize yourself with the project structure and distribution names.
Read `mx.truffle/mx_truffle.py` for Truffle-specific mx commands and gate task definitions.

```
# Build (run from truffle/ dir)
mx --primary-suite-path . build

# Build a single distribution only (much faster for targeted changes)
mx --primary-suite-path . build --dependencies TRUFFLE_API

# Build including the compiler suite (required for compilation/deopt tests)
# Must be run with compiler as the primary suite to avoid an import cycle
mx --primary-suite-path ../compiler build

# Run tests by class name (substring match)
mx --primary-suite-path . unittest TransitionInterceptionTest

# Run a single test method (only one class#method at a time)
mx --primary-suite-path . unittest TransitionInterceptionTest#testBytecodeUpdateTransition

# Filter which test classes to run via regex on the class name
mx --primary-suite-path . unittest --regex "Bytecode" com.oracle.truffle.api.bytecode.test

# Run all tests in a package
mx --primary-suite-path . unittest com.oracle.truffle.api.bytecode.test

# Run tests WITH runtime compilation enabled (requires compiler build above)
# Use the /compiler suite as the primary suite so the optimizing compiler is active
mx --primary-suite-path ../compiler unittest BytecodeDSLCompilationTest

# Run all Truffle tests with compilation enabled
mx --primary-suite-path ../compiler unittest --suite truffle

# List all gate tasks without running them (use this to discover task names)
mx --primary-suite-path . gate --dry-run

# Run a single gate task (-t is substring match, -T is exact match)
mx --primary-suite-path . gate -o -t "Truffle UnitTests"
mx --primary-suite-path . gate -o -t "Truffle Signature Tests"
mx --primary-suite-path . gate -o -t "SpotBugs"
mx --primary-suite-path . gate -o -t "Truffle Javadoc"
```

## Hints

* Always preserve Eclipse-style formatting.
* Generated code lives in `mxbuild/jdk<N>/com.oracle.truffle.*/src_gen/` — never edit it manually.
* The current development version can be extracted from `mx.truffle/suite.py`:
  ```
  python3 -c "import re; print(re.search(r'\"version\"\s*:\s*\"(\d+\.\d+)', open('mx.truffle/suite.py').read()).group(1))"
  ```
* Add `-o` to `gate` to skip cleaning (faster when already built). Add `--dry-run` to preview which tasks will run.
* `--regex` filters test *class* names, not method names. Use `Class#method` for single method runs.
* `--disable-truffle-optimized-runtime` runs tests against the fallback (interpreter-only) runtime. Only relevant if you run with `/compiler`.

## Public API

When adding or renaming a public Truffle API method:

* Add `@since <current-version>` to the Javadoc (currently `25.1`; derive from `mx.truffle/suite.py` using the command above).
* Add an entry in the `## Version <current-version>` section (top of the file) in `CHANGELOG.md` (at the truffle/ root) for every non-trivial user-visible change.
  ```markdown
  * GR-XXXXX: Added `ClassName.methodName()` to do X.
  ```
* Never introduce binary or source incompatible changes. Ask the user for permission if you absolutely have to.
* Verify API compatibility before updating the snapshot file:
  `mx --primary-suite-path . sigtest --check binary`
* Update the `snapshot.sigtest` of the relevant module once the API is finalised. Entries must stay in alphabetical order within each `CLSS` block. Regenerate with:
  `mx --primary-suite-path . sigtest --generate`

## Key Development Areas

Source projects and their primary test packages:

| Area | Source project | Test package |
|------|---------------|-------------|
| Core API | `src/com.oracle.truffle.api` | `com.oracle.truffle.api.test` |
| Bytecode DSL | `src/com.oracle.truffle.api.bytecode` | `com.oracle.truffle.api.bytecode.test` |
| Truffle DSL | `src/com.oracle.truffle.api.dsl` | `com.oracle.truffle.api.dsl.test` |
| Library API | `src/com.oracle.truffle.api.library` | `com.oracle.truffle.api.library.test` |
| Instrumentation | `src/com.oracle.truffle.api.instrumentation` | `com.oracle.truffle.api.instrumentation.test` |
| Debugger | `src/com.oracle.truffle.api.debug` | `com.oracle.truffle.api.debug.test` |
| Strings | `src/com.oracle.truffle.api.strings` | `com.oracle.truffle.api.strings.test` |
| NFI | `src/com.oracle.truffle.nfi` | `com.oracle.truffle.nfi.test` |
| Static Object | `src/com.oracle.truffle.api.staticobject` | `com.oracle.truffle.api.staticobject.test` |
| Object API | `src/com.oracle.truffle.api.object` | `com.oracle.truffle.api.object.test` |
| Interop API | `src/com.oracle.truffle.api.interop` | — (tested via `com.oracle.truffle.api.test`) |
| DSL Processor | `src/com.oracle.truffle.dsl.processor` | — (annotation processor, run `mx --primary-suite-path . build -f com.oracle.truffle.api.dsl.test` for testing) |

The DSL processor generates source into `mxbuild/jdk<N>/com.oracle.truffle.*/src_gen/` — changes there trigger a rebuild of the affected project.

## SimpleLanguage and SimpleTool

SimpleLanguage (`sl`) and SimpleTool (`st`) are the canonical example implementations.
For important API changes, add a showcase change in SimpleLanguage or SimpleTool.

| Purpose | Source project | Distribution |
|---------|---------------|--------------|
| SL runtime | `src/com.oracle.truffle.sl` | `TRUFFLE_SL` |
| SL launcher | `src/com.oracle.truffle.sl.launcher` | `TRUFFLE_SL_LAUNCHER` |
| SL tests | `src/com.oracle.truffle.sl.test` | `TRUFFLE_SL_TEST` |
| SL TCK | `src/com.oracle.truffle.sl.tck` | `TRUFFLE_SL_TCK` |
| ST runtime | `src/com.oracle.truffle.st` | `TRUFFLE_ST` |
| ST tests | `src/com.oracle.truffle.st.test` | `TRUFFLE_ST_TEST` |

* A Bytecode DSL variant lives in `src/com.oracle.truffle.sl/src/com/oracle/truffle/sl/bytecode/SLBytecodeRootNode.java`.
* Test programs (.sl files): `src/com.oracle.truffle.sl.test/src/tests/`.

```
# Run a SimpleLanguage program
mx --primary-suite-path . sl src/com.oracle.truffle.sl.test/src/tests/HelloWorld.sl

# Build and run a native SL image (requires GraalVM with native-image).
# If your default JDK is not GraalVM, pass --java-home <graalvm-location>.
mx --java-home <graalvm-location> --primary-suite-path . slnative -- src/com.oracle.truffle.sl.test/src/tests/HelloWorld.sl

# Run the TCK (default, compile, debugger configurations)
mx --primary-suite-path . tck
mx --primary-suite-path . tck --tck-configuration compile

# Run SL / ST unit tests
mx --primary-suite-path . unittest com.oracle.truffle.sl.test
mx --primary-suite-path . unittest com.oracle.truffle.st.test

# Run Truffle unit tests in native image mode (expensive, usually much slower).
# Run only when you expect JVM vs native-image behavior differences.
mx --java-home <graalvm-location> --primary-suite-path . gate -o --tags truffle-native-lite
```

## Essential Docs (High-Level)

Use these docs as the primary entry points, and dive deeper only as needed.

- Start here / overview: high-level orientation to Truffle architecture and repository layout. Use when starting a task or onboarding.
  - `README.md`
  - `docs/README.md`
- Language implementation: practical starting points for implementing or extending a Truffle language. Use when changing parser/runtime language semantics.
  - `docs/LanguageTutorial.md`
  - `docs/Languages.md`
- DSL and Bytecode DSL: design and implementation guidance for Truffle DSL and bytecode interpreters. Use when working on DSL nodes, bytecode roots, or interpreter generation.
  - `docs/DSLGuidelines.md`
  - `docs/bytecode_dsl/BytecodeDSL.md`
  - `docs/bytecode_dsl/UserGuide.md`
- Interop and libraries: interoperability protocol and library-dispatch patterns. Use when touching cross-language values, messages, or library exports.
  - `docs/TruffleLibraries.md`
  - `docs/InteropMigration.md`
- Performance and runtime behavior: profiling/optimization workflow and compilation behavior. Use when diagnosing regressions, deopts, inlining, interpreter/runtime compilation behavior, or compilation outcomes.
  - `docs/Profiling.md`
  - `docs/Optimizing.md`
  - `docs/Inlining.md`
  - `docs/HostCompilation.md`
  - `docs/OnStackReplacement.md`
  - `docs/bytecode_dsl/Optimization.md`
  - `docs/bytecode_dsl/RuntimeCompilation.md`
- Testing and compatibility: language/tool compatibility and specialization validation. Use when adding features, changing semantics, or validating behavior across languages.
  - `docs/TCK.md`
  - `docs/SpecializationTesting.md`
- Native interop: Truffle native function interface model and constraints. Use when calling native code or adjusting FFI behavior.
  - `docs/NFI.md`
- Advanced runtime topics: monomorphization/splitting and runtime coordination details. Use for deep runtime tuning, scalability, or execution-model changes.
  - `docs/splitting/Monomorphization.md`
  - `docs/splitting/Splitting.md`
  - `docs/Safepoints.md`

## Finalizing Steps

Before committing make sure you have performed the following steps:

* Run `mx --primary-suite-path . spotbugs --primary` to detect code smells (limit to primary suite).
* Run `mx --primary-suite-path . checkstyle --primary` to check code style.
* Run `mx --primary-suite-path . build -c --jdt builtin --warning-as-error` to fail on Java compiler warnings.
* Run `mx --primary-suite-path . eclipseformat --primary` to normalize Java formatting after any Java change.
* Keep copyright headers up-to-date by inserting the current year whenever a file is touched with a non-trivial change.
* For non-trivial Javadoc changes run `mx --primary-suite-path . javadoc --projects <project-name>` and make sure it produces no warnings.
* If `mx.truffle/suite.py` or `mx.truffle/mx_truffle.py` were changed run `mx --primary-suite-path . pyformat` to enforce Python style.
* If public API was added or changed, run `mx --primary-suite-path . sigtest --check binary` to verify compatibility.
* Make sure unittests are passing.
