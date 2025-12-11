# Substrate VM Developer Documentation

The documentation and manuals contained in this directory are describing Native Image internals. The information contained in these docs is meant for Native Image developers and contributors, **not** the end-users.

## Running SubstrateVM tests

Build and test artifacts are written under the suite output root (mxbuild).

Invocation conventions:

- Ensure mx is on your PATH and that a suitable JDK (25+ with JVMCI) is available. You can pass it explicitly with: --java-home /path/to/labsjdk
- Recommended: run test commands from the graal/substratevm directory.
- From the graal/ repo root, some tasks can be run with: mx --primary-suite=vm ... but native-unittest is typically invoked from graal/substratevm.

Quick verification (native):

```bash
# From graal/substratevm
mx gate
```
You can discover the available gate tasks and options like this:

```bash
mx gate --dry-run
mx gate --tags "some task name listed in the output"
```

You can pass `--java-home /path/to/labsjdk` to point to a specific JDK to use for the build. The command accepts the same test selectors as mx unittest (package prefixes, class names).

You can also run `mx native-unittests` to run tests that require compiling a junit suite to a native image and running it. These verify SubstrateVM specific APIs. 

Artifacts (including any JFR dumps) are placed under `mxbuild/svmbuild/<os-arch>/junit/`. 

Consulting the CI configurations in ci/ci.jsonnet may also help.