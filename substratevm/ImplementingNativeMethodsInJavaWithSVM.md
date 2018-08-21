# Implementing Native Methods in Java with Substrate VM

Substrate VM can be used to implement low-level system operations in Java and
make them available via JNI to Java code executing on a standard JVM. As
a result one can use the same language to write the application logic
as well as the system calls.

Note that this document describes the opposite of what is commonly done via JNI:
Usually low-level system operations are implemented in C and invoked from Java
using JNI. If you are interested in how Substrate VM supports the common use case,
read the documentation about [Substrate VM JNI support](JNI.md) instead.

## Create a Shared Library

First of all one has to use the `native-image` command to generate a shared library
with some JNI-compatible [entry points](README.md#images-and-entry-points).
Let's start with the Java code:
```java
package org.pkg.implnative;

import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.word.Pointer;

public final class NativeImpl {
    @CEntryPoint(name = "Java_org_pkg_apinative_Native_add")
    public static int add(Pointer jniEnv, Pointer clazz, @CEntryPoint.IsolateContext long isolateId, int a, int b) {
        return a + b;
    }
}
```
After being processed by the `native-image` command the code
[exposes a C function](C-API.md) `Java_org_pkg_apinative_Native_add`
(the name follows conventions of JNI that will be handy later) and
a SubstrateVM signature typical for JNI methods. The first parameter
is a reference to `JNIEnv*` value, the second parameter is a reference
to the `jclass` value for the class declaring the method. The third parameter is a
portable (e.g. `long`) identifier of the [SubstrateVM isolate](C-API.md).
The rest of the parameters are the actual parameters of the Java `Native.add`
method described in the next section. Compile the code with shared option on:
```bash
$GRAALVM/bin/native-image --shared -H:Name=libnativeimpl -cp nativeimpl
```
and the `libnativeimpl.so` is generated. We are ready to use it from standard
Java code.

## Bind Java Native Method

Now we need another Java class to use the native library generated in the previous step:
```java
package org.pkg.apinative;

public final class Native {
    private static native int add(long isolateId, int a, int b);
}
```
the package name of the class as well as name of the method has to correspond
(after the [JNI mangling](https://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/design.html))
to the name of the `@CEntryPoint` introduced previously. The first argument is
a portable (e.g. `long`) identifier of the SubstrateVM isolate. The rest of the arguments
matches the parameters of the entry point.

## Loading the Native Library

The next step is to bind the JDK with the generated `.so` library - e.g.
make sure the implementation of the native `Native.add` method is loaded.
Simple `load` or `loadLibrary` calls will do:
```java
public static void main(String[] args) {
    System.loadLibrary("nativeimpl");
    // ...
}
```
under the assumption your `LD_LIBRARY_PATH` environment variable is specified
or `java.library.path` Java property is properly set.

## Initializing the Substrate VM

Before making calls to the `Native.add` method, we need to create a Substrate VM
isolate. Substrate VM provides special built-in to allow that:
`CEntryPoint.Builtin.CreateIsolate`. Define another method along your other
existing `@CEntryPoint` methods. Let it return `long` and take no parameters:
```java
public final class NativeImpl {
    @CEntryPoint(name = "Java_org_pkg_apinative_Native_createIsolate", builtin=CEntryPoint.Builtin.CreateIsolate)
    public static native long createIsolate();
}
```
SubstrateVM then generates default native implementation of the
method into the final `.so` library.
The method initializes the Substrate VM runtime and
returns a portable identification - e.g. `long` to hold
an instance of a [Substrate VM isolate](C-API.md). The isolate can then be used for
multiple invocations of the native part of our code:
```java
package org.pkg.apinative;

public final class Native {
    public static void main(String[] args) {
        System.loadLibrary("nativeimpl");

        long isolate = createIsolate();

        System.out.println("2 + 40 = " + add(isolate, 2, 40));
        System.out.println("12 + 30 = " + add(isolate, 12, 30));
        System.out.println("20 + 22 = " + add(isolate, 20, 22));
    }

    private static native int add(long isolate, int a, int b);
    private static native long createIsolate();
}
```
The standard JVM is started. It initializes a Substrate VM isolate and
the universal answer `42` is then computed three times inside of
the isolate.
