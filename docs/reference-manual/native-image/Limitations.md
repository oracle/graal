---
layout: docs
toc_group: native-image
link_title: Native Image Compatibility and Optimization Guide
permalink: /reference-manual/native-image/Limitations/
---
# Native Image Compatibility and Optimization Guide

Native Image uses a different way of compiling a Java application than the traditional Java virtual machine (VM).
It distinguishes between *executable build time* and *executable runtime*.
At executable build time, the native image builder performs static analysis to find all the methods that are reachable from the entry point of an application.
The builder then compiles these (and only these) methods into a native executable.
Because of this different compilation model, a Java application can behave somewhat differently when compiled into a native executable.

Native Image acts as an optimization to reduce the memory footprint and startup time of an application.
This approach relies on a "closed-world assumption" in which all code is known at executable build time. That is, no new code is loaded at executable runtime.
As with most optimizations, not all applications are amenable to this approach.
If the native image building cis unable to optimize an application, it generates a so-called "fallback file" that requires a Java VM to run.

## Class Metadata Features (Require Configuration)

To use the closed-world optimization, the following Java features generally require configuration at executable build time.
This configuration ensures that native executable uses the minimum amount of space necessary.

If one of the following features is used without suitable configuration at build time, the native image builder produces a fallback file.

### Dynamic Class Loading
Any class to be accessed by name at executable runtime must be known at executable build time.
For example, a call to `Class.forName("myClass")` requires `myClass` to be present in a [configuration file](BuildConfiguration.md).
If a configuration file is present but does not include a class that is required for dynamic class loading, a `ClassNotFoundException` will be thrown at runtime, as if the class was not found on the class path or was inaccessible.

### Reflection
This category includes the following Java features:
* listing methods and fields of a class
* invoking methods and accessing fields reflectively
* using other classes in the package `java.lang.reflect`.

Individual classes, methods, and fields that should be accessible via reflection must be known to the `native-image` tool at build time.
Native Image tries to resolve these program elements by using static analysis to detect calls to the Reflection API.
If the analysis fails, the program elements reflectively accessed at runtime must be specified at native image build time in a [configuration file](BuildConfiguration.md) or by using [`RuntimeReflection`](http://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/hosted/RuntimeReflection.html) from a [`Feature`](http://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/hosted/Feature.html).
For more details, see the [Reflection support](Reflection.md) guide.

Reflection can be used without restriction at build time, for example, in class initializers.

### Dynamic Proxy
This category includes the use of dynamic proxy classes and allocating instances of dynamic proxy classes via the `java.lang.reflect.Proxy` API.
Dynamic class proxies are supported by the closed-world optimization if their bytecodes are generated ahead-of-time, that is, before build time.
This means that the list of interfaces that define dynamic proxies needs to be known to the `native-image` tool at build time.
Native Image performs a simple static analysis that intercepts calls to the methods `java.lang.reflect.Proxy.newProxyInstance(ClassLoader, Class<?>[], InvocationHandler)` and `java.lang.reflect.Proxy.getProxyClass(ClassLoader, Class<?>[])`. From this analysis it determines the list of interfaces.
If the analysis fails, the list of interfaces can be specified in a [configuration file](BuildConfiguration.md).
For more details, see the [Dynamic Proxies support](DynamicProxy.md) guide.

### JNI (Java Native Interface)
Native code may access Java objects, classes, methods and fields by name, in a similar way to using the reflection API in Java code.
Java artifacts accessed by name via JNI must be specified to the `native-image` tool at build time in a [configuration file](BuildConfiguration.md).
For more details, read the [JNI Implementation](JNI.md) guide.

As an alternative, and in addition to JNI, Native Image provides its own native interface that is much simpler than JNI and with lower overhead.
It allows calls between Java and C, and access of C data structures from Java code.
However, it does not allow access of Java data structures from C code.
For more details, see the [JavaDoc of the package `org.graalvm.nativeimage.c` and its subpackages](http://www.graalvm.org/sdk/javadoc/).

### Serialization
Java serialization requires class metadata information and this must be specified to the `native-image` tool at build time in a [configuration file](BuildConfiguration.md).
However, Java serialization has been a persistent source of security vulnerabilities.
The Java architects have announced that the existing serialization mechanism will be replaced with a new mechanism avoiding these problems in the near future.

## Features Incompatible with Closed-World Optimization

Some Java features are not yet supported within the closed-world optimization, and if used, result in a fallback file.

### `invokedynamic` Bytecode and Method Handles
Under the closed-world assumption, all methods that are called and their call sites must be known.
`invokedynamic` and method handles can introduce calls at runtime or change the method that is invoked.

Note that `invokedynamic` use cases generated by `javac` for, e.g., Java lambda expressions and string concatenation are supported because they do not change called methods at executable runtime.

### Security Manager
The Java security manager is no longer recommended as a way to isolate less trusted code from more trusted code in the same process.
This is because almost all typical hardware architectures are susceptible to side-channel attacks to access data that is restricted via the security manager.
Using separate processes is now recommended for these cases.

## Features That May Operate Differently in a Native Executable

Native Image implements some Java features in a different way than the Java VM.

### Signal Handlers

Registering a signal handler requires a new thread to start that handles the signal and invokes shutdown hooks.
By default, no signal handlers are registered when building a native executable, unless they are registered explicitly by the user.
For example, it is not recommended to register the default signal handlers when building a shared library, but it is desirable to include signal handlers when building native executables for containerized environments, such as Docker containers.

To register the default signal handlers, pass the `--install-exit-handlers` option to the `native-image` builder.
This option gives you the same signal handlers as a Java VM.

### Class Initializers
By default, classes are initialized at executable runtime.
This ensures compatibility, but limits some optimizations.
For faster startup and better peak performance, it is preferable to initialize classes at build time.
Class initialization behavior can be specified using the options `--initialize-at-build-time` or `--initialize-at-run-time` for specific classes and packages or for all classes.
See `native-image --help` for details. 
Classes that are members of the JDK class libraries are handled for you and require no special consideration from the user.

Native Image users should be aware that class initialization at build time may break specific assumptions in existing code.
For example, files loaded in a class initializer may not be in the same place at build time as at runtime.
Also, certain objects such as a file descriptors or running threads must not be stored into a native executable.
If such objects are reachable at build time, the native image builder fails with an error.

For more information, see [Class Initialization in Native Image](ClassInitialization.md).

### Finalizers
The Java base class `java.lang.Object` defines the method `finalize()`.
It is called by the garbage collector on an object when garbage collection determines that there are no more references to the object.
A subclass overrides the `finalize()` method to dispose of system resources or to perform other cleanup.

Finalizers have been deprecated since Java SE 9.
They are complicated to implement, and have badly designed semantics.
For example, a finalizer can make an object reachable again by storing a reference to it in a static field.
Therefore, finalizers are not invoked.
It is recommended to replace finalizers with weak references and reference queues for use in any Java VM.

### Threads
Native Image does not implement long-deprecated methods in `java.lang.Thread` such as `Thread.stop()`.

### Unsafe Memory Access
Fields that are accessed using `sun.misc.Unsafe` need to be marked as such for the static analysis if classes are initialized at build time.
In most cases, that happens automatically: field offsets stored in `static final` fields are automatically rewritten from the hosted value (the field offset for the VM on which the native image builder is running) to the native executable value, and as part of that rewrite the field is marked as `Unsafe`-accessed.
For non-standard patterns, field offsets can be recomputed manually using the annotation `RecomputeFieldValue`.

### Debugging and Monitoring
Java has some optional specifications that a Java implementation can use for debugging and monitoring Java programs, including JVMTI.
They help you monitor the VM at runtime for events such as compilation, for example, which do not occur in most native executables.
These interfaces are built on the assumption that Java bytecodes are available at runtime, which is not the case for native executables built with the closed-world optimization.
Because the native image builder generates a native executable, users must use native debuggers and monitoring tools (such as GDB or VTune) rather than tools targeted for Java.
JVMTI and other bytecode-based tools are not supported with Native Image.

## Related Documentation
* [Class Initialization in Native Image](ClassInitialization.md)
* [Native Image Build Configuration](BuildConfiguration.md)
* [Reflection Use in Native Executables](Reflection.md)

