# Substrate VM Developer Documentation

The documentation and manuals contained in this directory are describing Native Image internals. The information contained in these docs is meant for Native Image developers and contributors, **not** the end-users.

## Running Tests

See [Testing Native Image](Testing.md) for selecting a test project and running and debugging its tests.
Run build and test commands from the _substratevm_ directory.

## Running the gates

Gates are heavy-duty integration tests identified by a task name (tag). In CI all the gates are run. Locally, you should
always pick a small subset of gates targeted to your change.

Prerequisites:

1. `mx` _must_ be on your PATH for the gates to pass.
2. You _must_ run `mx` from inside a Python virtualenv with the `jsonschema` module installed for `mx gate` to pass.
3. You need `gdb` and `mvn` installed and on your path.

You can discover the available gate tasks and options like this:

```bash
mx gate --dry-run
mx gate --tags "some task name listed in the output,another task name"
```

Examine `mx_substratevm.py` to learn more about what the tags do.

`mx gate` on its own will clean the build tree before compiling and running tests. It's slow, so don't use this during
regular iterative development: use `mx native-unittest` when possible. Note that some tests only run on Linux, but these
are unlikely to affect you unless you're working on container related code.

Gate output artifacts (including any JFR dumps) are placed under `mxbuild/svmbuild`.  

Consulting the CI configurations in ci/ci.jsonnet may help understand how `mx gate` is invoked by GraalVM's CI system.

## Custom Runtimes

- [Building a Custom Runtime with Embedded Java Libraries](custom-runtimes.md): embed picocli in a JVM library and load an application at run time.

## Project Terminus

- [Project Terminus](project-terminus.md): overview and design direction for self-hosting Native Image.
