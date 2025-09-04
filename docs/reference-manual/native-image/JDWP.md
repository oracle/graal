---
layout: docs
toc_group: debugging-and-diagnostics
link_title: Java Debug Wire Protocol (JDWP) with Native Image
permalink: /reference-manual/native-image/debugging-and-diagnostics/JDWP/
---

# Java Debug Wire Protocol (JDWP) with Native Image

## Overview

This document describes the Java Debug Wire Protocol (JDWP) debugging support for Native Image, a feature that enables debugging of native images using standard Java tooling.

## Installation

The JDWP feature relies on a shared library, which is loaded only when the debugger is actively used.
This library must be built once before building native images with JDWP enabled.
```shell
native-image --macro:svmjdwp-library
```

> Note: This is a one-time setup step. The same library will be used for all native images built with JDWP enabled.

> Note: This library is stored in the GraalVM installation by default.
> If that directory is not writable, provide an alternative destination path with `-o <path/to/writable/directory>/libsvmjdwp`, or on Windows, use `-o <path\\to\\writable\\directory>\\svmjdwp`.

## Usage

> Note: JDWP debugging for Native Image is experimental.

To include JDWP support in a native image, add the `-H:+JDWP` option to your `native-image` command:

```shell
native-image -H:+UnlockExperimentalVMOptions -H:+JDWP ... -cp <class/path> YourApplication ...
```

This command produces:
1. The native executable
2. An `<image-name>.metadata` file
3. The `lib:svmjdwp` (`libsvmjdwp.so`, `libsvmjdwp.dylib` or `svmjdwp.dll`) shared library that will be necessary when debugging is also copied next to those files.

### Launching in Debug Mode

To launch the native image in debug mode, use the `-XX:JDWPOptions=` option, similar to HotSpot's `-agentlib:jdwp=`:

```shell
./your-application -XX:JDWPOptions=transport=dt_socket,server=y,address=8000
```

> Note: Debugging requires the _image-name.metadata_ file generated at build time and the `svmjdwp` shared library in the same directory as the native executable.

For a complete list of supported JDWP options on Native Image, run:

```shell
./your-application -XX:JDWPOptions=help
```

### Additional JDWP Options

Native Image supports additional non-standard JDWP options:

- `mode=native:<path>`: Specifies the path to the `svmjdwp` library. This can be:
  - A direct path to `lib:svmjdwp`
  - A directory containing `lib:svmjdwp`
  - A GraalVM installation containing `lib:svmjdwp` in the `lib` or `bin` directory

   If no path is specified, `lib:svmjdwp` is searched for beside the native executable.

Examples:
- `-XX:JDWPOptions=...,mode=native:<path/to/lib:svmjdwp>`
- `-XX:JDWPOptions=...,mode=native:<path/to/directory/containing/lib:svmjdwp>`
- `-XX:JDWPOptions=...,mode=native:<path/to/java/home>`: Search `lib:svmjdwp` inside `JAVA_HOME`, for example `lib|bin/lib:svmjdwp`.
- `-XX:JDWPOptions=...,mode=native`: Search `lib:svmjdwp` besides the native executable directory.

- `-XX:JDWPOptions=...,vm.options=...`: VM options, separated by whitespaces, passed to the JDWP server isolate/JVM, should not include a `,` character.
- `-XX:JDWPOptions=...,vm.options=@argfile`: Also supports [Java Command-Line Argument Files](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html#java-command-line-argument-files).

Note: If `lib:svmjdwp` cannot be found, the application will terminate with error code 1.

## Goals and Constraints

The JDWP debugging support for Native Image aims to:

1. Expose Native Image through JDWP as-is, maintaining its assumptions and constraints
2. Incur minimal or no performance overhead when not in use
3. Add minimal size overhead to the native binary
4. Be available on all Graal-supported platforms, including Linux, macOS, and Windows, across x64 and AArch64 architectures
5. Provide a debugging experience similar to HotSpot, without requiring additional steps (e.g., setting permissions, environment variables)

## Architecture

The JDWP debugging support is implemented using a Java bytecode interpreter, adapted from [Espresso](https://github.com/oracle/graal/tree/master/espresso) to work with Native Image. Key components include:

1. **Interpreter**: Derived from Espresso and adapted for [SubstrateVM](https://github.com/oracle/graal/tree/master/substratevm/). It does not enable any dynamic features beyond what Native Image already supports.

2. **PLT/GOT Feature**: Used to divert execution to the interpreter. This implementation detail may change for some platforms.

3. **Metadata File**: An external _.metadata_ file produced at build time, containing information required for runtime method interpretation.

4. **JDWP Server**: Implemented as a native library (`lib:svmjdwp`), handling network connections and implementing JDWP commands.

5. **JDWP Resident**: A component within the application providing access to locals, fields, stack traces, and other runtime information.

## Limitations

The JDWP debugger for Native Image is designed to align with Native Image's architecture and principles.
While many limitations are a natural consequence of Native Image's design, others may be due to the current implementation of the debugger itself.
Here are the key limitations to be aware of:

- The debugger follows Native Image closed-world assumptions:
  - Only classes, methods, and fields included in the image are accessible.
  - Some types may not be instantiable at runtime, even if there are instances in the image heap.
  - Some fields cannot be written to.
  - No support for dynamic class loading.
  - No class or method redefinition.
  - There's no runtime class-path `System.getProperty("java.class.path") == null`
- No exception breakpoints.
- No field watchpoints.
- No early return or frame popping.
- Some methods cannot be interpreted by the debugger (see below):
  - Methods that use "System Java".
  - Methods that contain a call to an intrinsic without compiled entry-point.
  - Breakpoints cannot be set in non-interpretable methods.
  - Stepping through non-interpretable methods is not possible, these are effectively treated as if they were Java "native" methods, with no guarantee to break/step on the next executed method, only on the next interpreted method.
- Not all execution paths are executable/interpretable.
  - Interpreting "dead-code" may work, but only on a best-effort basis.
  - Violating compiled-code assumptions, for example, passing a null argument where a non-null was expected, is considered undefined behavior and prone to crashes.
- Cannot write locals of compiled frames.
- Cannot hit breakpoints or stepping events on actively executing compiled methods.
- Step-out operations only work for interpreter frames, not compiled frames.
- Can only debug the first isolate of a native image.
- Step-into does not work for target methods of a `MethodHandle` object, for example, lambdas.

These limitations reflect the current state of JDWP debugging support in Native Image.
Some may be addressed in future iterations of the debugger, while others are fundamental to Native Image's design.

### Further Reading

- Instructions on how to run the JDWP server in HotSpot, so that the debugger can be debugged: [JDWP server](https://github.com/oracle/graal/tree/master/substratevm/src/com.oracle.svm.jdwp.server/README.md)
- Implementation details about the interpreter and the transitions from and to compiled code: [SubstrateVM Interpreter](https://github.com/oracle/graal/tree/master/substratevm/src/com.oracle.svm.interpreter/README.md)
