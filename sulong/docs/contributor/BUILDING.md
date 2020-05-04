# Building Sulong from Source

Sulong is implemented mostly in Java, with some C/C++ code. It is part of GraalVM.

## Build Dependencies

GraalVM is built using the [mx](https://github.com/graalvm/mx) build tool.
For running mx, a Python runtime is required.

The C/C++ code is built using the LLVM toolchain. GraalVM comes with a bundled LLVM
toolchain that will also be used to build Sulong (see [Toolchain](TOOLCHAIN.md)).

In addition, system tools such as a linker, `make` and `cmake` as well
as system headers are needed.

### Linux

On a Linux-based operating system you can usually use the package
manager to install these requirements. For example, on Debian based system,
installing the `build-essential` and the `cmake` package should be sufficient.

### MacOS

While on MacOS most dependencies are provided by Xcode,
`cmake` is not included and needs to be installed manually.
A version for MacOS can be downloaded from the [cmake homepage](https://cmake.org/download/).
Make sure to put `cmake` on the PATH as well, this is not done automatically by
the installer.

## Runtime Dependencies

LLVM is only needed for compiling the bitcode files. For running compiled
bitcode files, there are no special runtime dependencies, but additional
libraries might be needed if the user code has external dependencies.

## Getting the Source

It is recommended to create a separate directory for GraalVM development:

```
mkdir graalvm-dev && cd graalvm-dev
```

First we need to get mx, the build tool used by GraalVM:

```
git clone https://github.com/graalvm/mx
export PATH=$PWD/mx:$PATH
```

Next, use git to clone the graal repository:

```
git clone https://github.com/oracle/graal
```

Next, you need to download a recent
[JVMCI-enabled JDK 8](https://github.com/graalvm/openjdk8-jvmci-builder/releases).

Set the `JAVA_HOME` environment variable to point to the extracted JDK from above.
The `sulong/mx.sulong/env` file can be used to store environment variables for use with `mx`:

```
echo JAVA_HOME=... >> graal/sulong/mx.sulong/env
```

## Building Sulong from Source

Sulong can be built with this command:

```
cd graal/sulong
mx build
```

## Running Sulong

The `lli` launcher can also be started using `mx` from the source directory:

```
mx lli testprogram
```

The `mx lli` command accepts the same options as the standard `lli` launcher in a GraalVM.

Note that this by default runs without the compiler enabled. To enable the compiler, use
this command to build and run Sulong:

```
mx --dynamicimport /compiler build
mx --dynamicimport /compiler --jdk jvmci lli ...
```

## Debugging

See [debugging](../user/DEBUGGING.md) for information how to debug C programs or
LLVM bitcode running inside GraalVM. Note that for debugging options to be available from
`mx`, the tools suite needs to be built and imported (`mx --dynamicimport /tools lli --inspect ...`).

To debug the Java code of Sulong itself, you can use any regular Java debugger.
You can start the `lli` launcher with debugging enabled using the `-d` flag to mx:

```
$ mx -d lli ...
Listening for transport dt_socket at address: 8000
```

Now you can attach a Java debugger to that process.


## Building a GraalVM including Sulong

The regular `mx build` command in the sulong directory will already build a minimal
GraalVM containing just Sulong and its dependencies. The resulting GraalVM can be found
in `graal/sdk/latest_graalvm_home`. That symlink will always point to the latest built GraalVM.

Note that a GraalVM built like this will only contain the bare minimum, that is, it does
not contain a compiler or a debugger. To add additional components, use the `--dynamicimport`
flag for mx. For example, to include tools (e.g. the debugger) and the compiler:

```
mx --dynamicimport /tools,/compiler build
```

Alternatively, a full GraalVM can be built from the `graal/vm` directory. For example,
to build a GraalVM with Sulong, SubstrateVM and tools, run:

```
cd graal/vm && mx --dynamicimport /sulong,/substratevm,/tools build
```

The built GraalVM in `graal/sdk/latest_graalvm_home` is a regular GraalVM, containing
the `lli` launcher. See the [user documentation](../user/README.md) for more how to use it.


## IDE Setup

If you want to use the project from within Eclipse, use the following
command to generate the IDE project files:

```
mx ideinit
```

That will generate IDE files for Eclipse, IntelliJ and Netbeans. You can also
use `mx eclipseinit`, `mx netbeansinit` or `mx intellijinit` to generate
the IDE file of your choice.
