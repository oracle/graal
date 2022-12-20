---
layout: docs
toc_group: security-guide
link_title: Security Guide
permalink: /security-guide/
redirect_from:
- /docs/security-guide/
- /$version/docs/security-guide/
---

# Security Guide

This security guide provides developers and embedders with information on the security model and features of GraalVM for developers and embedders who seek to build a secure application on top of it.
It assumes that readers are familiar with the GraalVM architecture.
This guide does not replace but rather supplements the Java security documentation with aspects unique to GraalVM.
It also provides security researchers with information on GraalVM's security model.

* [Security Model](#security-model)
* [Language Launchers](#language-launchers)
* [Guest Applications](#guest-applications)
* [Native Image](#native-image)
* [Security Manager and Untrusted Code](#security-manager-and-untrusted-code)
* [GraalVM Enterprise to GraalVM Community Downgrade](#graalvm-enterprise-to-graalvm-community-downgrade)

## Security Model

GraalVM is a shared runtime. It accepts instructions in a higher-level
programming language (or an intermediate representation thereof) as input, which is executed later.
Developers that implement security controls for their applications (such as access control) in code that is being run by GraalVM can rely on the correct execution of instructions.
Incorrect execution of security-critical code running on top of GraalVM that allows to bypass such a security control is regarded a security vulnerability.

GraalVM does not support execution of untrusted code.
If untrusted and potentially malicious code is to be executed, we recommend GraalVM customers who have an immediate requirement to execute untrusted and potentially adversarial code, adopt the appropriate external isolation primitives to ensure the confidentiality and integrity of their application data.

Debug features should only be used in a trusted environment as they provide privileged access to an application, allowing to inspect and change its state and behavior.
They may further open network sockets to allow debug clients to connect.

Experimental features in GraalVM are not for production use and may have security limitations not covered in the Security Guide.

We appreciate reports of bugs that break the security model via the process
outlined in the [Reporting Vulnerabilities guide](https://www.oracle.com/corporate/security-practices/assurance/vulnerability/reporting.html).

## Language Launchers

For every language implemented with the Truffle framework, and shipped with GraalVM, a launcher, e.g., interactive shell, is provided.
These launchers behave in the same way and come with the same security guarantees as their "original" counterparts.

## Guest Applications

GraalVM allows a host application written in a JVM-based language to execute guest applications written in a Truffle language via the [Polyglot API](../reference-manual/embedding/embed-languages.md).
When creating a context, the host application can control which resources the guest can access.
This mechanism is only fully supported for Javascript.
By default, access to all managed resources is denied and needs to be granted explicitly, following the principle of least privilege.

### Host Interoperability

GraalVM allows exchanging objects between the host and the guest application.
By default only methods of host classes that are explicitly annotated by the embedder are exposed to guest applications.

By exposing security critical host methods, access restrictions can be bypassed.
For example, a guest application in a context that is created with `allowIO=false` cannot perform IO operations via the guest language's native API.
However, exposing a host method to the context that allows writing to arbitrary files effectively bypasses this restriction.

### Sharing Execution Engines

Application developers may choose to share execution engines among execution contexts for performance reasons.
While the context holds the state of the executed code, the engine holds the code itself.
Sharing of an execution engine among multiple contexts needs to be set up explicitly and can increase performance in scenarios where a number of contexts execute the same code. In scenarios where contexts that share an execution engine for common code also execute sensitive (i.e., private) code, the corresponding source objects can opt out from code sharing with:
```java
Source.newBuilder(…).cached(false).build()
```

### Computational Resource Limits

> Note: Available with GraalVM Enterprise.

GraalVM Enterprise allows restricting certain computational resources used by guest applications, such as CPU time, heap memory or the number of threads that can be concurrently used by a context.
These [sandboxing options](../reference-manual/embedding/sandbox-options.md) are also available via the Polyglot embedding API.

### ScriptEngine Compatibility

For reasons of backward compatibility, certain guest languages also support Java's ScriptEngine interface.
For example, this allows GraalVM JavaScript to be used as a drop-in replacement for Nashorn.
However, to maintain compatibility, the Nashorn GraalVM JavaScript ScriptEngine interface will create a context with **all privileges** granted to the script and **should be used with extreme caution** and only for trusted code.

### Managed Execution of Native Code

> Note: Available with GraalVM Enterprise.

The Truffle framework also supports the LLVM intermediate representation (IR) as a guest language. Several native system programming languages, above all C/C++, can be compiled to LLVM IR with the LLVM compiler toolchain. Typically, these
languages are not memory-safe by themselves and it must be remembered that violations of memory safety are a frequent cause of security vulnerabilities.

In managed mode, all ties to the native level are abstracted and routed through GraalVM Enterprise. In particular this means that:

* In regards to temporal and spatial memory safety, memory is allocated from the Java heap. This means that memory allocations are managed objects and all accesses are performed in a memory-safe manner (no arbitrary pointer arithmetics and no unchecked out-of-bounds accesses).
* Regarding type safety, it is not possible to reinterpret a data pointer into a function pointer and execute arbitrary instructions (since these are distinct pointer types for LLVM runtime).
* System calls are intercepted and routed to the corresponding Truffle
APIs. For example, file IO is mapped to the Truffle `FileSystem` API.
The set of currently supported system calls is very limited -- only syscalls that can safely be mapped to the Truffle API level are available. Since LLVM Runtime in managed mode always runs bitcode compiled for Linux/x86, it only needs to implement system calls for this platform.
* All dependent libraries are executed in managed mode as well, removing all references to natively executed system libraries. This includes libraries that are provided by the LLVM Runtime, such as muslibc.

Managed mode can be selected when creating a context `(Context.create())` or when calling the `bin/lli` binary by specifying the `--llvm.managed` option. A "managed" context will adhere to any restrictions (e.g., `allowIO`) passed during context creation and does not need the `allowNativeAccess` privilege.

## Native Image

The `native-image` builder generates a snapshot of an application after startup and bundles it in a binary executable.

By default, the `native-image` builder executes the static initializers of classes at build time and persists the state in the image heap.
This means that any information that is obtained or computed in static initializers becomes part of a native executable.
This can lead to unintentionally including properties of the build environment, such as environment variables in the image heap.
This can either result in sensitive data ending up in the snapshot or fixing initialization data that is supposed to be obtained at startup, such as random number seeds.

Developers can request static initializers that process sensitive information to be instead executed at runtime by either specifying the `--initialize-at-run-time` CLI parameter when building a native executable, or making use of the `RuntimeClassInitialization` API.

Native-image provides multiple ways to specify the certificate file used to define the default TrustStore.
While the default behavior for native-image is to capture and use the default TrustStore from the buildtime host environment, this can be changed at runtime by setting the "javax.net.ssl.trustStore\*" system properties.
See the [documentation](../reference-manual/native-image/CertificateManagement.md) for more details.

In addition, developers can run the `native-image` builder in a dedicated environment, such as a container, that does not contain any sensitive information in the first place.

### Serialization in Native Image

Native Image supports Serialization to help users deserialize the constructors for classes, contained in a native executable in the first place.
These classes should be whitelisted in an additional specific configuration file, as other classes cannot be deserialized.
Deserialization support also adds optional object checksums, and only classes with the same checksum can be deserialized at runtime.
The checksum mechanism must not be used for security purposes and the deserialization of untrusted data is not supported.

## Security Manager and Untrusted Code

The OpenJDK vulnerability group strongly discourages running untrusted code under a security manager.
This also applies to GraalVM, which does not support untrusted code execution in Java.
While GraalVM's ability to restrict the execution of guest language applications to a certain extent is not dependent on a security manager, it is not suited to be used as a sandbox for running untrusted code.

Note that security manager deprecation is an option in [JEP-411](https://openjdk.java.net/jeps/411).

Native Image does not support a security manager in general. Attempting to set a security manager will trigger a runtime error.

The Truffle framework needs to be invoked with all permissions to make full use of its functionality - it provides its own controls to manage resources.

## GraalVM Enterprise to GraalVM Community Downgrade

> Note: Managed execution of native code is available with GraalVM Enterprise.

When downgrading to GraalVM Community, native code execution is only available with the `allowNativeAccess` privilege.
This also applies to languages implemented with Truffle that allow for native code extensions, such as Python and Ruby.

Computational resource limit options are not recognized by GraalVM Community.
