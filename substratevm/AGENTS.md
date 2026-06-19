# GraalVM Native Image Community Edition (CE)

This guide applies to the `substratevm` directory.

## Scope

- `substratevm` is an `mx` suite for GraalVM Native Image. It provides the native-image tool that is part of GraalVM.
- Most code lives under `src/`, with one directory per module, for example `src/com.oracle.svm.core`, `src/com.oracle.svm.hosted`, `src/com.oracle.svm.graal`, and `src/com.oracle.svm.test`.
- Java sources are typically under `src/<module>/src/com/oracle/...`.
- Use `docs/` for developer documentation, `ci/` for CI configuration, and `mx.substratevm/` for suite metadata.
- Treat `mxbuild/`, `svmbuild/`, and `sources/` as generated output.

## Build and Development Commands

Run commands from this directory.

- `mx build`: compile the suite; run this before tests or image builds so changed sources are rebuilt.
- `mx checkstyle`: validate style and formatting expectations before sending a change.
- `mx native-unittest [options]`: runs tests for this suite.
- `mx helloworld [options]`: quick smoke test with a basic hello-world application.
- `mx native-image [options] -cp <classpath> <mainClass>`: use this as the `native-image` build-tool when building an image is requested as part of substratevm development workflows.

If a failure looks inconsistent with the checked-out sources, run `mx clean` and then `mx build` before deeper debugging.
Do not run `mx` commands concurrently; parallel runs can produce misleading failures.

## Tests and Validation

- Before treating a test run as validation, check whether the affected test is platform-restricted, for example by annotations, runtime assumptions, or architecture-specific options. A run that skips the affected test because the local machine does not match the required platform does not count as validation.

## Change Hygiene

- Do not use lambdas in runtime code.
- If you touch documented behavior, update `docs/`.
- When adding or changing substitutions, include durable comments that explain when and why
  the substitution intentionally deviates from the original JDK implementation.
- Do not commit generated output from `mxbuild/`, `svmbuild/`, `graal_dumps/`, or `sources/`.
- Be careful when changing `mx native-unittest` / `svmjunit` feature registration. Features added to the shared native test image affect unrelated tests too.
- Do not register production ImageSingletons or other global runtime markers from shared `svmjunit` test features. In particular, avoid making the shared test image look like a different product or launch mode, such as a `libjvm` image.
- If a test needs product-specific global state, prefer a targeted image or test-specific setup over adding that state to the global native-unit-test feature list in `mx.substratevm/mx_substratevm.py`.
- For Crema/libjvm support, prefer expanding `lib_jvm_preserved_packages` in `mx.substratevm/mx_substratevm.py` when runtime-loaded JDK bytecode needs package-level method or field availability. Do not add individual `java.*` analysis roots to hosted features when preserving the owning JDK package expresses the requirement.
