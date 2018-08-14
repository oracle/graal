Substrate VM Java Limitations
=============================

Substrate VM does not support all features of Java to keep the implementation small and concise, and also to allow aggressive ahead-of-time optimizations. This page documents the limitations.

| What | Support Status|
| ---------- | ----------|
| [Dynamic Class Loading / Unloading](#dynamic-class-loading--unloading) | Not supported|
| [Reflection](#reflection) | Mostly supported|
| [Dynamic Proxy](#dynamic-proxy) | Mostly supported|
| [Java Native Interface (JNI)](#java-native-interface--jni) | Mostly supported|
| [Unsafe Memory Access](#unsafe-memory-access) | Mostly supported |
| [Static Initializers](#static-initializers) | Partially supported|
| [InvokeDynamic Bytecode and Method Handles](#invokedynamic-bytecode-and-method-handles) | Not supported|
| [Lambda Expressions](#lambda-expressions) | Supported|
| [Synchronized, wait, and notify](#synchronized-wait-and-notify) | Supported|
| [Finalizers](#finalizers) | Not supported|
| [References](#references) | Mostly supported|
| [Threads](#threads) | Supported|
| [Identity Hash Code](#identity-hash-code) | Supported|
| [Security Manager](#security-manager) | Not supported|
| [JVMTI, JMX, other native VM interfaces](#jvmti-jmx-other-native-vm-interfaces) | Not supported|


Dynamic Class Loading / Unloading
---------------------------------

**Support Status: Not supported**

What: Loading new classes that were not available at native image build time; dynamically generating new bytecodes and loading them; relying on classes being unloaded at run time.

Dynamic class loading is not supported, and cannot be supported by our execution model. During native image generation, we run an aggressive static analysis that requires a closed-world assumption. For that, we need to know all classes and all bytecodes that are ever reachable. The static analysis finds out which parts are required by the application and performs ahead-of-time compilation for these parts.


Reflection
----------

**Support Status: Mostly supported**

What: Calling `Class.forName()`; listing methods and fields of a class; invoking methods and accessing fields reflectively; most classes in the package `java.lang.reflect`.

Individual classes, methods, and fields that should be accessible via reflection need to be known ahead-of-time. SubstrateVM tries to resolve these elements through a static analysis that detects calls to the reflection API. Where the analysis fails the program elements reflectively accessed at run time must be specified during native image generation in a configuration file via the option `-H:ReflectionConfigurationFiles=`, or by using [`RuntimeReflection`](http://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/RuntimeReflection.html) from a [`Feature`](http://www.graalvm.org/sdk/javadoc/org/graalvm/nativeimage/Feature.html). For more details, read our [documentation on reflection](REFLECTION.md).

During native image generation, reflection can be used without restrictions during native image generation, for example in static initializers.

Dynamic Proxy
----------

**Support Status: Mostly supported**

What: Generating dynamic proxy classes and allocating instances of dynamic proxy classes using the `java.lang.reflect.Proxy` API.

Dynamic class proxies are supported as long as the bytecodes are generated ahead-of-time. This means that the list of interfaces that define dynamic proxies needs to be known at image build time. SubstrateVM employs a simple static analysis that intercepts calls to `java.lang.reflect.Proxy.newProxyInstance(ClassLoader, Class<?>[], InvocationHandler)` and `java.lang.reflect.Proxy.getProxyClass(ClassLoader, Class<?>[])` and tries to determine the list of interfaces automatically. Where the analysis fails the lists of interfaces can be specified via configuration files. For more details, read our [documentation on dynamic proxies](DYNAMIC_PROXY.md).

Java Native Interface (JNI)
---------------------------

**Support Status: Mostly supported**

What: The Java Native Interface (JNI) enables Java code to interact with native code and vice versa.

Individual classes, methods, and fields that should be accessible via JNI must be specified during native image generation in a configuration file via the option `-H:JNIConfigurationFiles=`. For more details, read our [JNI implementation documentation](JNI.md).

Alternatives: In addition to JNI, we provide our own native interface that is much simpler than JNI. It allows calls between Java and C, and access of C data structures from Java code. However, it does not allow access of Java data structures from C code. For more details, read our [JavaDoc of the package `org.graalvm.nativeimage.c` and its subpackages](http://www.graalvm.org/sdk/javadoc/).


Unsafe Memory Access
--------------------

**Support Status: Mostly supported**

What: The memory access methods of `sun.misc.Unsafe`.

Fields that are accessed using `sun.misc.Unsafe` need to be marked as such for the static analysis. In most cases, that happens automatically: field offsets stored in `static final` fields are automatically rewritten from the hosted value (the field offset for the VM that the image generator is running on) to the Substrate VM value, and as part of that rewrite the field is marked as `Unsafe`-accessed. For non-standard patterns, field offsets can be recomputed manually using the annotation `RecomputeFieldValue`.


Static Initializers
-------------------

**Support Status: Partially supported**

What: Static class initialization blocks, pre-initialized static variables.

All static class initialization is done during native image construction. This has the advantage that possibly expensive initializations do not slow down the startup of the generated image, and large static data structures are pre-allocated. However, it also means that instance-specific initializations (such as opening and initializing native libraries, opening files or socket connections, starting threads ...) cannot be done in static initializers.

Why: Static initializers run in the host VM during native image generation, and it is not possible to prevent or intercept that.

Alternatives: Write your own initialization methods and call them explicitly from your main entry point.


InvokeDynamic Bytecode and Method Handles
-----------------------------------------

**Support Status: Not supported**

What: The `invokedynamic` bytecode and the method handle classes introduced with Java 7.

The static analysis and our closed-world assumption require that we know all methods that are called and their call sites, while `invokedynamic` can introduce calls at runtime or change the method that is invoked.

Only special use cases of `invokedynamic` are supported: when the `invokedynamic` can be reduced to a single virtual call or field access during native image generation. This is sufficient for full support of Java 8 Lambda expressions. 


Lambda Expressions
------------------

**Support Status: Supported**

What: The Lambda expressions introduced with Java 8.

Lambda expressions use the `invokedynamic` bytecode, but only for the bootstrapping process (which dynamically creates new classes). All the bootstrapping runs during native image generation, so that no reflective or dynamic method invocation is necessary at run time.


Synchronized, wait, and notify
------------------------------

**Support Status: Supported**

What: Java offers the `synchronized` keyword for methods and blocks. Every object and every class has an intrinsic lock. The base class `java.lang.Object` provides wait and notify methods for conditional waiting.

The implementation of synchronization uses `java.util.concurrent.locks`. There are no optimizations such as biased locking that reduce the overhead of `synchronized`, so code that uses unnecessary synchronization (synchronization on temporary objects that do not escape a single thread) is slower on Substrate VM compared to the Java HotSpot VM.


Finalizers
----------

**Support Status: Not supported**

What: The Java base class `java.lang.Object` defines the method `finalize()`. It is called by the garbage collector on an object when garbage collection determines that there are no more references to the object. A subclass overrides the `finalize()` method to dispose of system resources or to perform other cleanup.

Finalizers are not supported at all, and there are no plans to support it. This means that no `finalize()` method will ever be called. Finalizers are an ancient relict of the early days of Java that are complicated to implement, and have very badly designed semantics. For example, the finalizer can make the object reachable again by storing it in a static field.

Alternatives: Use weak references and reference queues.


References
---------------

**Support Status: Mostly supported**

What: The package `java.lang.ref` defines the base class `Reference`, as well as subclasses for weak, soft, and phantom references. The object that the reference refers to can be deallocated, in which case the reference is updated to contain the value null. With the help of a `ReferenceQueue`, user code can be executed when a reference gets deallocated.

We have our own Feeble References (exposed as `java.lang.ref.Reference`) similar to Java's weak references. However, we do not distinguish between weak, soft, and phantom references.

We do not support `Reference.enqueue()` or `Reference.isEnqueued()`.


Threads
-------

**Support Status: Supported**


What: Starting new threads; Support for `java.lang.Thread`.

We have nearly full support for `java.lang.Thread`. Only deprecated methods, such as `Thread.stop()`, are not supported. Starting threads in a static initializer is not allowed. See the [Static Initializers](#static-initializers) section for details.

It is possible to build single-threaded applications using the option `-H:-MultiThreaded`. This removes the dependency on the OS thread library such as `pthreads`. In a single-threaded application, starting new threads is not allowed and synchronization operations are implemented as no-ops.


Identity Hash Code
------------------

**Support Status: Supported**

What: `java.lang.Object.hashCode()` and `java.lang.System.identityHashCode()` return a random but fixed-per-object integer value. Successive calls to `identityHashCode()` for the same object yield the same result.

Identity hash codes are fully supported. The identity hash code of hosted objects during native image generation is the same as the identity hash code at run time, so hash maps that are built during native image generation can be used at run time.


Security Manager
----------------

**Support Status: Not supported**

What: `java.lang.SecurityManager`

Since there is no dynamic class loading, there is also no need to sandbox "untrusted" code. The method `System.getSecurityManager` alsways returns `null`, i.e., there are no runtime security manager checks performed.


JVMTI, JMX, other native VM interfaces
--------------------------------------

**Support Status: Not supported**

What: Management and debugging interfaces that Java offers.

These interfaces require access to Java bytecodes, which are no longer available at run time. They also allow dynamic instrumentation of bytecodes and interception of VM events.
