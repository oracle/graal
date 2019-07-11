# Toolchain

*The toolchain* is a set of tools and APIs for compiling native project, such as C, C++,
to bitcode that can be executed with the GraalVM LLVM runtime.
Its aim is to simplify the ahead-of-time compilation aspect for both, users
and language implementers, who want to use the LLVM runtime.

## Use Cases

1. **Simplify compilation to bitcode**
  If *GraalVM users* want to run a native project via the LLVM runtime in GraalVM,
  they need to compile their project to LLVM bitcode.
  Although it is possible to do this with standard LLVM tools (`clang`, `llvm-link`, etc.),
  it requires some considerations (for example certain optimizations or manual linking).
  The toolchain aims to provide drop in replacements to compile many native projects for
  the GraalVM LLVM runtime out of the box.

2. **Compilation of native extensions**
  *GraalVM language implementers* use the LLVM runtime to execute native extensions.
  Often, these extensions are installed by the user using some kind of package manager.
  In python, for example, packages are usually added via `pip install`.
  To enable this, languages need a way of compiling native extension on demand.
  The toolchain provides Java API for languages to access the tools that are
  (optionally) bundled with GraalVM.

3. **Compiling to bitcode at build time**
  *GraalVM languages* that integrate with the LLVM runtime usually need to build
  bitcode libraries to integrate with native pieces of their implementation.
  The toolchain can be used as a build-time dependency do achieve this in a
  standardized and compatible way.

## File Format

To be compatible with existing build systems, the toolchain will by default
produce native executables with embedded bitcode (ELF files on Linux, Mach-O
files on MacOS).

## Toolchain Identifier

The GraalVM LLVM runtime can run in different configurations, which require bitcode to be compiled differently.
Users of the toolchain do not need to care about this.
The LLVM runtime knows in which mode it is running and will always provide the right toolchain.
However, since a language implementation might want to store the result of a
toolchain compilation for later use, it need to be able to identify it.
To do so, the toolchain provides an *identifier*.
Conventionally, the identifier is used as a directory name and the results of a
compilation are placed in there.
The internal LLVM runtime library layout follows the same approach.

## Java API

Language implementations can access the toolchain via the [`Toolchain`](../../sulong/projects/com.oracle.truffle.llvm.api/src/com/oracle/truffle/llvm/api/Toolchain.java) service.
The service provides two methods:

* `TruffleFile getToolPath(String tool)`
  The method returns the path to the executable for a given tool.
  Every implementation is free to choose its own set of supported tools.
  The command line interface of the executable is specific to the tool.
  If a tool is not supported or not known, `null` is returned.
  Consult the Javadoc for a list known tools.
* `String getIdentifier()`
  Returns the identifier for the toolchain.
  It can be used to distinguish results produced by different toolchains.
  The identifier can be used as a path suffix to place results in distinct locations,
  therefore it does not contain special characters like slashes or spaces.

The `Toolchain` lives in the `SULONG_API` distribution.
The LLVM runtime will always provide a toolchain that matches its current mode.
The service can be looked-up via the `Env`:

```Java
LanguageInfo llvmInfo = env.getInternalLanguages().get("llvm");
Toolchain toolchain = env.lookup(llvmInfo, Toolchain.class);
TruffleFile toolPath = toolchain.getToolPath("CC");
String toolchainId = toolchain.getIdentifier();
```

## `mx` integration

On the `mx` side, the toolchain can be accessed via the *substituions* `toolchainGetToolPath` and `toolchainGetIdentifier`.
Note that they expect a toolchain name as first argument. See for example the following snippet from a `suite.py` file:

```python
  "buildEnv" : {
    "CC": "<toolchainGetToolPath:native,CC>",
    "CXX": "<toolchainGetToolPath:native,CXX>",
    "PLATFORM": "<toolchainGetIdentifier:native>",
  },
```

## GraalVM Deployment

On the implementation side, _the toolchain_ consists of multiple ingredients:

* The **LLVM.org component** is similar to a regular [LLVM release](https://llvm.org) (clang, lld, llvm-* tools)
  but includes a few patches that are not yet [upstream](https://github.com/llvm/llvm-projec).
  Those patches are general feature improvements that are not specific to GraalVM.
  In GraalVM, the LLVM.org component is located in `$GRAALVM/jre/lib/llvm/`.
  This component is considered as internal and should not be directly used.
  The LLVM.org component might not be install by default. If that is the case, it can be installed via `gu install llvm-toolchain`.
* The **toolchain wrappers** are GraalVM launchers that invoke the tools from the LLVM.org component with special flags
  to produce results that can be executed by the LLVM runtime of GraalVM. The Java and `mx` APIs return paths to those wrappers.
  In GraalVM, the wrappers live in `$GRAALVM/jre/languages/llvm/$TOOLCHAIN_ID/bin/`. The wrappers are shipped with the
  GraalVM LLVM runtime and do not need to be installed separately.
  Those are meant to be drop in replacements for the C/C++ compiler when compiling a native project.
  The goal is to produce a GraalVM LLVM runtime executable result by simply pointing any build system to those wrappers,
  for example via `CC`/`CXX` environment variables or by setting `PATH`.

## Bootstrapping Toolchain

During building, the LLVM.org component is available in the `mxbuild/SULONG_LLVM_ORG` distribution.
Bootstrapping wrappers can be found in the `mxbuild/SULONG_BOOTSTRAP_TOOLCHAIN` distribution.
However, the APIs will take care of providing the right one.
These distributions are for manual usage only and are considered unstable and might change without notice.
Do not depend on them.
