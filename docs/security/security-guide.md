---
layout: docs
title: Security Guide
link_title: Security Guide
permalink: /security-guide/
redirect_from: /docs/security-guide/
toc_group: security-guide
---

# Security Guide

This security guide provides developers and embedders with information on the security model and features of GraalVM for developers and embedders who seek to build a secure application on top of it.
It assumes that readers are familiar with the [GraalVM architecture](/docs/introduction/).
This guide does not replace but rather supplements the Java security documentation with aspects unique to GraalVM.
It also provides security researchers with information on GraalVM's security model.

* [Security Model](#security-model)
* [Truffle Language Implementation Framework](#truffle-language-implementation-framework)
* [Guest Applications](#guest-applications)
* [Managed Execution of Native Code](#managed-execution-of-native-code)
* [Native Image](#native-image)
* [Security Manager and Untrusted Code](#security-manager-and-untrusted-code)
* [GraalVM Enterprise to GraalVM Community Downgrade](#graalvm-enterprise-to-graalvm-community-downgrade)

## Security Model
GraalVM is a shared runtime. It accepts instructions in a higher-level
programming language (or an intermediate representation thereof) as input, which is executed later.
Developers that implement security controls for their applications (such as access control) in code that is being run by GraalVM can rely on the correct execution of instructions.
Incorrect execution of security-critical code running on top of GraalVM that allows to bypass such a security control is regarded a security vulnerability.

Debug features should only be used in a trusted environment as they provide privileged access to an application, allowing to inspect and change its state and behavior.
They may further open network sockets to allow debug clients to connect.

Experimental and early-adopter features in GraalVM are not for production use and may have security limitations not covered in the Security Guide.

We appreciate reports of bugs that break the security model via the process
outlined in the [Reporting Vulnerabilities guide](https://www.oracle.com/corporate/security-practices/assurance/vulnerability/reporting.html).

## Truffle Language Implementation Framework

Using the [Truffle language implementation framework](/graalvm-as-a-platform/language-implementation-framework/), interpreters for guest languages can be implemented to execute guest applications written in languages such as Javascript, Python, Ruby, R or WebAssembly (Wasm) on top of GraalVM.
Whereas the execution context for these applications can be created with restricted privileges, this mechanism is only fully supported for Javascript and should not be used to execute untrusted code.

For every language implemented with the [Truffle framework](/graalvm-as-a-platform/language-implementation-framework/), and shipped with GraalVM, a launcher, e.g., interactive shell, is provided.
These launchers behave in the same way and come with the same security guarantees as their "original" counterparts.
The languages implemented with the Truffle framework are henceforth referenced as **guest languages**.

## Guest Applications

GraalVM allows a host application written in a JVM-based language to create an execution context to run code written in one or more guest languages.
When creating a context, the host application can control which resources the guest can access.
By default, access to all managed resources is denied and needs to be granted explicitly. Note that this is currently only fully supported by Javascript.

### File I/O
Access to files can be controlled via two means.

1. The `allowIO` privilege grants
the guest application unrestricted access to the host file system:
```java
Context context = Context.newBuilder().allowIO(true).build();
```
2. Alternatively, the Truffle framework virtual file system can be installed that all guest file I/O will be routed through:
```java
Context context = Context.newBuilder().fileSystem(FileSystem fs).build();
```

### Threading
A guest application can only create new threads if the context is created with the corresponding privilege:
```java
Context context = Context.newBuilder().allowCreateThread(true).build()
```

### Native Access
The Truffle native interface allows access to privileged native code.
It needs to be granted to a guest application context via:
```java
Context context = Context.newBuilder().allowNativeAccess(true).build()
```

### Host Interoperability
GraalVM allows exchanging objects between the host and the guest application.
Since the guest application is potentially less trusted than the host application, multiple controls exist to tune the degree of interoperability between the guest and the host:

* `allowHostAccess(policy)` -- configures which of the host's public constructors, methods, or fields of public classes can be accessed by the guest.
* `allowHostClassLookup(Predicate<String> classFilter)` -- allows the guest application to look up the host application classes specified in the classFilter via `Java.type`. For example, a Javascript context can create a Java ArrayList, provided that ArrayList is whitelisted by the `classFilter` and access is permitted by the host access policy: `context.eval("js", "var array = Java.type('java.util.ArrayList')")`
* `allowHostClassLoading(true/false)` - allows the guest application to access the host's class loader to load new classes. Classes are only accessible if access to them is granted by the host access policy.

The host access policy has three different options:

* `ALL` - all public constructors, methods or fields of public classes of the host can be accessed by the guest.
* `NONE` - no constructors, methods or fields of the host can be accessed by the guest.
* `EXPLICIT` - only public constructors, methods, and fields of public classes that are annotated with `@HostAccess.Export` can be accessed by the guest.

The following example demonstrates how these configuration options work together:
```java
 public class MyClass {
     @HostAccess.Export
     public int accessibleMethod() {
         return 42;
     }

     public static void main(String[] args) {
         try (Context context = Context.newBuilder() //
                         .allowHostClassLookup(c -> c.equals("myPackage.MyClass")) //
                         .build()) {
             int result = context.eval("js", "" +
                             "var MyClass = Java.type('myPackage.MyClass');" +
                             "new MyClass().accessibleMethod()").asInt();
             assert result == 42;
         }
     }
 }
```

This Java/JavaScript example:
* Creates a new context with the permission to look up the class `myPackage.MyClass` in the guest application.
* Evaluates a JavaScript code snippet that accesses the Java class `myPackage.MyClass` using the `Java.type` builtin provided by the JavaScript language implementation.
* Creates a new instance of the Java class `MyClass` by using the JavaScript `new` keyword.
* Calls the method `accessibleMethod()` which returns "42". The method is accessible to the guest application because the enclosing class and the declared method are public, as well as annotated with the `@HostAccess.Export` annotation.

The guest can also pass objects back to the host.
This is implemented by functions that return a value. For example:
```java
Value a = Context.create().eval("js", "21 + 21");
```
This returns a guest object representing the value "42".
When executing less trusted guest code, application developers need to take care when processing objects returned from the guest application -- the host application should treat them as less trusted input and sanitize accordingly.

### Sharing Execution Engines
Application developers may choose to share execution engines among execution contexts for performance reasons.
While the context holds the state of the executed code, the engine holds the code itself.
Sharing of an execution engine among multiple contexts needs to be set up explicitly and can increase performance in scenarios where a number of contexts execute the same code. In scenarios where contexts that share an execution engine for common code also execute sensitive (i.e., private) code, the corresponding source objects can opt out from code sharing with:
```java
Source.newBuilder(…).cached(false).build()
```

### Computational Resource Limits

> Note: Available with GraalVM Enterprise.

With GraalVM Enterprise you can control certain computational resources used by guest applications, such as CPU time, a number of threads that can be concurrently used by a context, etc.
Those can be restricted with the [sandbox options](/en/graalvm/enterprise/21/docs/reference-manual/embed-languages/sandbox/).

### ScriptEngine Compatibility
For reasons of backward compatibility, certain guest languages also support Java's ScriptEngine interface.
For example, this allows GraalVM JavaScript to be used as a drop-in replacement for Nashorn.
However, to maintain compatibility, the Nashorn GraalVM JavaScript ScriptEngine interface will create a context with **all privileges** granted to the script and **should be used with extreme caution** and only for trusted code.

## Managed Execution of Native Code

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
The native image builder generates a snapshot of an application after startup and bundles it in a binary executable.

By default, the native image builder executes the static initializers of classes at build time and persists the state in the native image heap.
This means that any information that is obtained or computed in static initializers becomes part of the native image executable.
This can lead to unintentionally including properties of the build environment, such as environment variables in the image heap.
This can either result in sensitive data ending up in the snapshot or fixing initialization data that is supposed to be obtained at startup, such as random number seeds.

Developers can request static initializers that process sensitive information to be instead executed at [runtime](/reference-manual/native-image/ClassInitialization/) by either specifying the `--initialize-at-run-time` CLI parameter when building a native image, or making use of the `RuntimeClassInitialization` API.

In addition, developers can run the native image builder in a dedicated environment, such as a container, that does not contain any sensitive information in the first place.

### Serialization in Native Image

Native Image supports Serialization to help users deserialize the constructors for classes, contained in a native image in the first place.
These classes should be whitelisted in an additional specific configuration file, as other classes cannot be deserialized.
Deserialization support also adds optional object checksums, and only classes with the same checksum can be deserialized at runtime.
The checksum mechanism must not be used for security purposes and the deserialization of untrusted data is not supported.

## Security Manager and Untrusted Code
The OpenJDK vulnerability group strongly discourages to running untrusted code under a security manager.
This also applies to GraalVM, which does not support untrusted code execution in Java.
While GraalVM's ability to restrict the execution of guest language applications to a certain extent is not dependent on a security manager, it is not suited to be used as a sandbox for running untrusted code.

Native Image does not support a security manager in general. Attempting to set a security manager will trigger a runtime error.

The Truffle framework needs to be invoked with all permissions to make full use of its functionality - it provides its own controls to manage resources.

If untrusted and potentially malicious code is to be executed, we recommend GraalVM customers who have an immediate requirement to execute untrusted and potentially adversarial code, adopt the appropriate isolation primitives to ensure the confidentiality and integrity of their application data.

## GraalVM Enterprise to GraalVM Community Downgrade

> Note: Managed execution of native code is available with GraalVM Enterprise.

When downgrading to GraalVM Community, native code execution is only available with the `allowNativeAccess` privilege.
This also applies to languages implemented with Truffle that allow for native code extensions, such as Python and Ruby.
