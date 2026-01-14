# Substrate VM Developer Documentation

The documentation and manuals contained in this directory are describing Native Image internals. The information contained in these docs is meant for Native Image developers and contributors, **not** the end-users.

## Running the SubstrateVM test suite

There are multiple ways to run the tests:

1. Run `mx build` to compile. You _must always_ run this first, because `mx native-unittest` on its own only compiles the JARs to a native image and runs it, it doesn't compile the source code to JARs. Thus changing the unit tests won't affect test results until you do this.
2. Run `mx native-unittest`. You _should not_ run `mx unittest`, which doesn't work without extra flags.
3. Use `mx gate` (see below).

It's a good idea to run test commands from the `graal/substratevm` directory. Build and test artifacts are written under the suite output root (mxbuild).

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