Substrate VM Java Limitations
=============================

Substrate VM does not support all features of Java to keep the implementation small and concise, and also to allow aggressive ahead-of-time optimizations. This page documents the limitations. If you are not sure if a feature is supported, ask christian.wimmer@oracle.com so that this list can be updated.

Dynamic Class Loading / Unloading
---------------------------------

### What:
Loading classes with `Class.forName()` or subclassing `java.lang.ClassLoader` to dynamically generate new bytecodes and load them; relying on classes being unloaded at run time.

### Support Status: Not supported
Dynamic class loading is not supported, and cannot be supported by our execution model.

### Why:
During native image generation, we run an aggressive static analysis that requires a closed-world assumption. At this point, we need to know all classes and all bytecodes that are ever reachable. The static analysis finds out which parts are required by the application and performs ahead-of-time compilation for these parts.

Reflection
----------

### What:
Listing methods and fields of a class; invoking methods and accessing fields via reflectively; most classes in the package `java.lang.reflect`.

### Support Status: Partially supported

Specific kinds of reflection access can be enabled for individual classes, methods and fields by providing one or several configuration files via the `-H:ReflectionConfigurationFiles=` option during native image generation. Elements that are not included in a configuration cannot be accessed reflectively.

### During native image generation:
Reflection can be used without restrictions during native image generation, e.g., in static initializers. At this point, you can collect information about methods and fields and fill your own data structures, which are then reflection-free at run time. For example, you can collect a list of field names during native image generation, and store the names in a list so that you have the names available at run time.

Implicit Exceptions
-------------------

### What:
Exceptions that are thrown implicitly by bytecodes, such as: `NullPointerException`, `ArrayIndexOutOfBoundsException`, `ClassCastException`, `ArrayStoreException`, `ArithmeticException` (division by 0), ...

### Support Status: Mostly supported
The only restriction is that implicit exceptions cannot be caught in the same method that they are thrown in, because that would make the control flow within a method too complicated.

Stacktrace Printing
-------------------

### What:
Printing Java stack traces via the `Throwable.printStackTrace` methods.

### Support Status: Supported

InvokeDynamic Bytecode and Method Handles
-----------------------------------------

### What:
The `invokedynamic` bytecode and the method handle classes introduced with Java 7.

### Support Status: Not supported
(Except for special cases like Lambda expressions)

### Why:
The static analysis and our closed-world assumption require that we know all methods that are called and their call sites, while `invokedynamic` can introduce calls at runtime.

Lambda Expressions
------------------

### What:
The Lambda expressions introduced with Java 8.

### Support Status: Supported

### How:
Lambda expressions use the `invokedynamic` bytecode, but only for the bootstrapping process (which dynamically creates new classes). All the bootstrapping runs during native image generation, so that no reflective or dynamic method invocation is necessary at run time.

JNI
---

### What:
The Java Native Interface (JNI) enables Java code to interact with native code and vice versa.

### Support Status: Partially supported
JNI integration must be enabled using `-H:+JNI` during native image generation. Individual classes, methods and fields that should be accessible via JNI must be specified in a configuration via the `-H:JNIConfigurationFiles=` option. Alternatively, a `Feature` can be used to register accessible program elements during the native image build. For more details, read our [JNI implementation documentation](JNI.md).

### Alternatives:
The Substrate VM defines its own native interface, which is much simpler than JNI. It allows calls between Java and C, and access of C data structures from Java code. However, it does not allow access of Java data structures from C code.

Synchronized, wait, and notify
------------------------------

### What:
Java offers the `synchronized` keyword for methods and blocks. Every object and every class has an intrinsic lock. The base class `java.lang.Object` provides wait and notify methods for conditional waiting.

### Support Status: Mostly supported

Synchronization on objects is generally supported with the exception of arrays of any type. When using `java.lang.Class` objects for synchronization, the class object must always be specified as compile-time constant synchronization target, such as in `synchronized(Vector.class)`, or implicitly such as for `static synchronized` methods. The method `Thread.holdsLock()` does not work on class objects.

### How:
The implementation of synchronization uses `java.util.concurrent.locks`. There are no optimizations such as biased locking that reduce the overhead of synchronized, so code that uses unnecessary synchronization is significantly slower on Substrate VM compared to the Java HotSpot VM.

Finalizers
----------

### What:
The Java base class `java.lang.Object` defines the method `finalize()`. It is called by the garbage collector on an object when garbage collection determines that there are no more references to the object. A subclass overrides the `finalize()` method to dispose of system resources or to perform other cleanup.

### Support Status: Not supported
Finalizers are not supported at all, and there are no plans to ever support it. This means that no `finalize()` method will ever be called.

### Why:
Finalizers are an ancient relict of the early days of Java that are complicated to implement, and have very badly designed semantics. For example, the finalizer can make the object reachable again by storing it in a static field.

### Alternatives:
Weak references and reference queues

Weak references
---------------

### What:
The package `java.lang.ref` defines the base class `Reference`, as well as subclasses for weak, soft, and phantom references. The object that the reference refers to can be deallocated, in which case the reference is updated to contain the value null. With the help of a `ReferenceQueue`, user code can be executed when a reference gets deallocated.

### Support Status: Supported
We have our own Feeble References (exposed as `java.lang.ref.Reference`) similar to Java's weak references. However, we do not distinguish between weak, soft, and phantom references.

Threads
-------

### What:
Starting new threads; Support for `java.lang.Thread`

### Support Status: Mostly supported
Nearly full support for `java.lang.Thread`. Only long deprecated methods, such as `Thread.stop()`, will not be supported. However, we discourage explicit usage of threads. Use higher level constructs from `java.util.concurrent` instead.

Identity Hash Code
------------------

### What:
`java.lang.Object.hashCode()` and `java.lang.System.identityHashCode()` return a random but fixed-per-object integer value. Successive calls to `identityHashCode()` for the same object yield the same result.

### Support Status: Supported
Identity hash codes are fully supported. The hash code is stored in an injected int field in all classes that might access it. The identity hash code of hosted objects during native image generation is the same as the identity hash code at run time, so hash maps that are built during native image generation can be used at run time.

Annotations
-----------

### What:
Annotations of classes, methods, fields, ... that can be queried at run time.

### Support Status: Mostly supported
Annotations on classes, i.e., `Class.getAnnotation()`, is fully supported. Annotations are objects in the native image heap, and annotation attributes are fields of these objects. To use annotations on methods and fields, make them accessible (as `java.lang.reflect.Method` and `java.lang.reflect.Field`) via reflection configuration file (see [Reflection](#Reflection) above).

Static Initializers
-------------------

### What:
Static class initialization blocks, pre-initialized static variables.

### Support Status: Mostly supported
All static class initialization is done during native image construction. This has the advantage that possibly expensive initializations do not slow down the startup, and large static data structures are pre-allocated. However, it also means that instance-specific initializations (such as opening and initializing native libraries, opening files or socket connections, ...) cannot be done in static initializers.

### Why:
Static initializers run in the host VM during native image generation, and it is not possible to prevent or intercept that.

### Alternatives:
Write your own initialization methods and call them explicitly from your main entry point.

JVMTI, JMX, other native VM interfaces
--------------------------------------

### What:
Management and debugging interfaces that Java offers.

### Support Status: Not supported

Shutdown Hooks
--------------

### What:
The JVM shutdown hooks are implemented as threads that are invoked on the VM shutdown.

### Support Status: Mostly Supported
We use the `Runnable` interface and invoke all shutdown hooks in the main thread. Errors during shutdown are reported similarly to the multi-threaded implementation.
