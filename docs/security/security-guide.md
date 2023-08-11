---
layout: docs
toc_group: security-guide
link_title: Security Guide
permalink: /security-guide/
redirect_from:
- /docs/security-guide/
---

# Security Guide

This security guide provides developers and embedders with information on the security model and features of GraalVM for developers and embedders who seek to build a secure application on top of it.
It assumes that readers are familiar with the GraalVM architecture.
This guide does not replace but rather supplements the Java security documentation such as the [Secure Coding Guidelines for Java SE](https://www.oracle.com/java/technologies/javase/seccodeguide.html) with aspects unique to GraalVM.

## Security Model

GraalVM is a shared runtime. It accepts instructions in a higher-level
programming language (or an intermediate representation thereof) as input, which is executed later.
Developers that implement security controls for their applications (such as access control) in code that is being run by GraalVM can rely on the correct execution of instructions.
Incorrect execution of security-critical code running on top of GraalVM that allows to bypass such a security control is regarded a security vulnerability.

Debug features should only be used in a trusted environment as they provide privileged access to an application, allowing to inspect and change its state and behavior.
They may further open network sockets to allow debug clients to connect.

Experimental features in GraalVM are not for production use and may have security limitations not covered in the Security Guide.

GraalVM enables execution of untrusted code in an appropriately configured polyglot execution context (see [Polyglot Sandboxing](polyglot-sandbox.md)).

We appreciate reports of bugs that break the security model via the process
outlined in the [Reporting Vulnerabilities guide](https://www.oracle.com/corporate/security-practices/assurance/vulnerability/reporting.html).

## Polyglot Languages

For every Polyglot language shipped with GraalVM, a launcher, e.g., interactive shell, is provided.
These launchers behave in the same way and come with the same security guarantees as their "original" counterparts.

### Polyglot Sandboxing

Polyglot sandboxing can establish a security boundary between privileged host code and unprivileged guest code.
For further information please refer to the [Polyglot Sandboxing guide](polyglot-sandbox.md).

### ScriptEngine Compatibility

For reasons of backward compatibility, certain Polyglot languages also support the [Java Scripting API](https://docs.oracle.com/javase/9/scripting/java-scripting-api.htm).
For example, this allows the GraalVM Javascript runtime to be used as a drop-in replacement for Nashorn.
However, to maintain compatibility, the Nashorn GraalVM JavaScript ScriptEngine interface will create a context with all privileges granted to the script and should be used with extreme caution and only for trusted code.

### Managed Execution of Native Code

Polyglot embedding also supports LLVM intermediate representation (IR) guest code.
Several native system programming languages, above all C/C++, can be compiled to LLVM IR with the LLVM compiler toolchain.
Typically, these languages are not memory-safe unless using managed execution and it must be remembered that violations of memory safety are a frequent cause of security vulnerabilities.

In managed mode, all access to unmanaged code including the operating system is mediated by the language runtime. In particular this means that:

* In regards to temporal and spatial memory safety, memory is allocated from the Java heap. This means that memory allocations are managed objects and all accesses are performed in a memory-safe manner (no arbitrary pointer arithmetics and no unchecked out-of-bounds accesses).
* Regarding type safety, it is not possible to reinterpret a data pointer into a function pointer and execute arbitrary instructions (since these are distinct pointer types for LLVM runtime).
* System calls are intercepted and routed to the corresponding Truffle APIs. For example, file IO is mapped to the Truffle `FileSystem` API.
The set of currently supported system calls is very limited -- only syscalls that can safely be mapped to the Truffle API level are available. Since LLVM Runtime in managed mode always runs bitcode compiled for Linux/x86, it only needs to implement system calls for this platform.
* All dependent libraries are executed in managed mode as well, removing all references to natively executed system libraries. This includes libraries that are provided by the LLVM Runtime, such as muslibc.

Managed mode can be selected when creating a context `(Context.create())` or when calling the `bin/lli` binary by specifying the `--llvm.managed` option. A "managed" context will adhere to any restrictions (e.g., `allowIO`) passed during context creation and does not need the `allowNativeAccess` privilege.

## Native Image

With GraalVM native image, an application's state is captured after startup and all reachable code is compiled ahead of time to be bundled as a native executable.
For further information please refer to the [native image security guide](native-image.md).

## Security Manager

Security manager has been deprecated in [JEP-411](https://openjdk.java.net/jeps/411).
GraalVM does not support untrusted code execution in Java.

## GraalVM Community Edition Downgrade

Polyglot sandboxing is not available in GraalVM Community Edition.
Managed execution of native code is not available with GraalVM Community Edition.

When downgrading to GraalVM Community Edition, native code execution is only possible with the `allowNativeAccess` privilege.
This also applies to languages implemented with Truffle that allow for native code extensions, such as Python and Ruby.

### Related Documentation

- [Polyglot Sandboxing](polyglot-sandbox.md)
- [Security Considerations in Native Image](native-image.md)
