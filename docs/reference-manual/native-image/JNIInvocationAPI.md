---
layout: docs
toc_group: native-code-interoperability
link_title: JNI Invocation API
permalink: /reference-manual/native-image/native-code-interoperability/JNIInvocationAPI/
redirect_from: /reference-manual/native-image/ImplementingNativeMethodsInJavaWithSVM/
---

# JNI Invocation API

Native Image can be used to implement low-level system operations in Java and make them available via JNI Invocation API to Java code executing on a standard JVM.
As a result one can use the same language to write the application logic as well as the system calls.

Note that this document describes the opposite of what is commonly done via JNI: usually low-level system operations are implemented in C and invoked from Java using JNI.
If you are interested in how Native Image supports the common use case, see [Java Native Interface (JNI) in Native Image](JNI.md).

## Create a Shared Library

First of all one has to use the `native-image` builder to generate a shared library with some JNI-compatible entry points.
Start with the Java code:
```java
package org.pkg.implnative;

import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.word.Pointer;

public final class NativeImpl {
    @CEntryPoint(name = "Java_org_pkg_apinative_Native_add")
    public static int add(Pointer jniEnv, Pointer clazz, @CEntryPoint.IsolateThreadContext long isolateId, int a, int b) {
        return a + b;
    }
}
```
After being processed by the `native-image` builder, the code [exposes a C function](C-API.md) `Java_org_pkg_apinative_Native_add` (the name follows conventions of JNI that will be handy later) and a Native Image signature typical for JNI methods.
The first parameter is a reference to the `JNIEnv*` value.
The second parameter is a reference to the `jclass` value for the class declaring the method.
The third parameter is a portable (e.g., `long`) identifier of the [Native Image isolatethread](C-API.md).
The rest of the parameters are the actual parameters of the Java `Native.add` method described in the next section. Compile the code with the `--shared` option:
```shell
$JAVA_HOME/bin/native-image --shared -H:Name=libnativeimpl -cp nativeimpl
```
The `libnativeimpl.so` is generated. We are ready to use it from standard Java code.

## Bind a Java Native Method

Now we need another Java class to use the native library generated in the previous step:
```java
package org.pkg.apinative;

public final class Native {
    private static native int add(long isolateThreadId, int a, int b);
}
```
The package name of the class, as well as the name of the method, has to correspond (after the [JNI mangling](https://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/design.html)) to the name of the `@CEntryPoint` introduced previously.
The first argument is a portable (e.g., `long`) identifier of the Native Image isolate thread.
The rest of the arguments match the parameters of the entry point.

## Load the Native Library

The next step is to bind the JDK with the generated `.so` library.
For example, make sure the implementation of the native `Native.add` method is loaded.
Simple `load` or `loadLibrary` calls will do:
```java
public static void main(String[] args) {
    System.loadLibrary("nativeimpl");
    // ...
}
```
This is assuming your `LD_LIBRARY_PATH` environment variable is specified, or the `java.library.path` Java property is properly set.

## Initialize a Native Image Isolate

Before making calls to the `Native.add` method, we need to create a Native Image isolate.
Native Image provides a special built-in to allow that: `CEntryPoint.Builtin.CREATE_ISOLATE`.
Define another method along your other existing `@CEntryPoint` methods.
Let it return `IsolateThread` and take no parameters:
```java
public final class NativeImpl {
    @CEntryPoint(name = "Java_org_pkg_apinative_Native_createIsolate", builtin=CEntryPoint.Builtin.CREATE_ISOLATE)
    public static native IsolateThread createIsolate();
}
```
Native Image then generates default native implementation of the method into the final `.so` library.
The method initializes the Native Image runtime and returns a portable identification, e.g., `long`, to hold an instance of a [Native Image isolatethread](C-API.md).
The isolate thread can then be used for multiple invocations of the native part of your code:
```java
package org.pkg.apinative;

public final class Native {
    public static void main(String[] args) {
        System.loadLibrary("nativeimpl");

        long isolateThread = createIsolate();

        System.out.println("2 + 40 = " + add(isolateThread, 2, 40));
        System.out.println("12 + 30 = " + add(isolateThread, 12, 30));
        System.out.println("20 + 22 = " + add(isolateThread, 20, 22));
    }

    private static native int add(long isolateThread, int a, int b);
    private static native long createIsolate();
}
```
The standard JVM is started. It initializes a Native Image isolate, attaches the current thread to the isolate, and the universal answer `42` is then computed three times inside of the isolate.

## Call JVM from Native Java

There is a detailed [tutorial on the C interface](https://github.com/oracle/graal/blob/master/substratevm/src/com.oracle.svm.tutorial/src/com/oracle/svm/tutorial/CInterfaceTutorial.java) of Native Image.
The following example shows how to make a callback to JVM.

In the classical setup, when C needs to call into JVM, it uses a [jni.h](JNI.md) header file.
The file defines essential JVM structures (like `JNIEnv`) as well as functions one can invoke to inspect classes, access fields, and call methods in the JVM.
In order to call these functions from the `NativeImpl` class in the above example, you need to define appropriate Java API wrappers of the `jni.h` concepts:

```java
@CContext(JNIHeaderDirectives.class)
@CStruct(value = "JNIEnv_", addStructKeyword = true)
interface JNIEnvironment extends PointerBase {
    @CField("functions")
    JNINativeInterface getFunctions();
}

@CPointerTo(JNIEnvironment.class)
interface JNIEnvironmentPointer extends PointerBase {
    JNIEnvironment read();
    void write(JNIEnvironment value);
}

@CContext(JNIHeaderDirectives.class)
@CStruct(value = "JNINativeInterface_", addStructKeyword = true)
interface JNINativeInterface extends PointerBase {
    @CField
    GetMethodId getGetStaticMethodID();

    @CField
    CallStaticVoidMethod getCallStaticVoidMethodA();
}

interface GetMethodId extends CFunctionPointer {
    @InvokeCFunctionPointer
    JMethodID find(JNIEnvironment env, JClass clazz, CCharPointer name, CCharPointer sig);
}

interface JObject extends PointerBase {
}

interface CallStaticVoidMethod extends CFunctionPointer {
    @InvokeCFunctionPointer
    void call(JNIEnvironment env, JClass cls, JMethodID methodid, JValue args);
}

interface JClass extends PointerBase {
}
interface JMethodID extends PointerBase {
}
```

Leaving aside the meaning of `JNIHeaderDirectives` for now, the rest of the interfaces are type-safe representations of the C pointers found in the `jni.h` file. `JClass`, `JMethodID`, and `JObject` are all pointers.
Thanks to the above definitions, you now have Java interfaces to represent instances of these objects in your native Java code in a type-safe way.

The core part of any [JNI](JNI.md) API is the set of functions one can call when talking to the JVM.
There are dozens of them, but in the `JNINativeInterface` definition, you just define wrappers for those few that are needed in the example.
Again, give them proper types, so in your native Java code you can use `GetMethodId.find(...)`, `CallStaticVoidMethod.call(...)`, etc.
In addition, there is another important part missing in the puzzle - the `jvalue` union type wrapping all the possible Java primitive and object types.
Here are definitions of its getters and setters:

```java
@CContext(JNIHeaderDirectives.class)
@CStruct("jvalue")
interface JValue extends PointerBase {
    @CField boolean z();
    @CField byte b();
    @CField char c();
    @CField short s();
    @CField int i();
    @CField long j();
    @CField float f();
    @CField double d();
    @CField JObject l();


    @CField void z(boolean b);
    @CField void b(byte b);
    @CField void c(char ch);
    @CField void s(short s);
    @CField void i(int i);
    @CField void j(long l);
    @CField void f(float f);
    @CField void d(double d);
    @CField void l(JObject obj);

    JValue addressOf(int index);
}
```
The `addressOf` method is a special Native Image construct used to perform C pointer arithmetics.
Given a pointer, one can treat it as the initial element of an array, then, for example, use `addressOf(1)` to access the subsequent element.
With this you have all the API needed to make a callback - redefine the previously introduced `NativeImpl.add` method to accept properly typed pointers, and then use these pointers to invoke a JVM method before computing the sum of `a + b`:

```java
@CEntryPoint(name = "Java_org_pkg_apinative_Native_add")
static int add(JNIEnvironment env, JClass clazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, int a, int b) {
    JNINativeInterface fn = env.getFunctions();

    try (
        CTypeConversion.CCharPointerHolder name = CTypeConversion.toCString("hello");
        CTypeConversion.CCharPointerHolder sig = CTypeConversion.toCString("(ZCBSIJFD)V");
    ) {
        JMethodID helloId = fn.getGetStaticMethodID().find(env, clazz, name.get(), sig.get());

        JValue args = StackValue.get(8, JValue.class);
        args.addressOf(0).z(false);
        args.addressOf(1).c('A');
        args.addressOf(2).b((byte)22);
        args.addressOf(3).s((short)33);
        args.addressOf(4).i(39);
        args.addressOf(5).j(Long.MAX_VALUE / 2l);
        args.addressOf(6).f((float) Math.PI);
        args.addressOf(7).d(Math.PI);
        fn.getCallStaticVoidMethodA().call(env, clazz, helloId, args);
    }

    return a + b;
}
```

The above example seeks a static method `hello` and invokes it with eight `JValue` parameters in an array reserved by `StackValue.get` on the stack.
Individual parameters are accessed by use of the `addressOf` operator and filled with appropriate primitive values before the call happens.
The method `hello` is defined in the class `Native` and prints values of all parameters to verify they are properly propagated from the `NativeImpl.add` caller:

```
public class Native {
    public static void hello(boolean z, char c, byte b, short s, int i, long j, float f, double d) {
        System.err.println("Hi, I have just been called back!");
        System.err.print("With: " + z + " " + c + " " + b + " " + s);
        System.err.println(" and: " + i + " " + j + " " + f + " " + d);
    }
```

There is just one final piece to explain: the `JNIHeaderDirectives`.
The Native Image C interface needs to understand the layout of the C structures.
It needs to know at which offset of `JNINativeInterface` structure it can find the pointer to the `GetMethodId` function.
To do so, it needs `jni.h` and additional files during compilation. One can specify them by `@CContext` annotation and implementation of its `Directives`:

```java
final class JNIHeaderDirectives implements CContext.Directives {
    @Override
    public List<String> getOptions() {
        File[] jnis = findJNIHeaders();
        return Arrays.asList("-I" + jnis[0].getParent(), "-I" + jnis[1].getParent());
    }

    @Override
    public List<String> getHeaderFiles() {
        File[] jnis = findJNIHeaders();
        return Arrays.asList("<" + jnis[0] + ">", "<" + jnis[1] + ">");
    }

    private static File[] findJNIHeaders() throws IllegalStateException {
        final File jreHome = new File(System.getProperty("java.home"));
        final File include = new File(jreHome.getParentFile(), "include");
        final File[] jnis = {
            new File(include, "jni.h"),
            new File(new File(include, "linux"), "jni_md.h"),
        };
        return jnis;
    }
}
```

The good thing is that `jni.h` is inside of every JDK, so one can use the `java.home` property to locate the necessary header files.
The actual logic can, of course, be made more robust and OS-independent.

Implementing any JVM native method in Java and/or making callbacks to the JVM with Native Image should now be as easy as expanding upon the given example and invoking `native-image`.

### Related Documentation

- [Interoperability with Native Code](InteropWithNativeCode.md)
- [Java Native Interface (JNI) in Native Image](JNI.md)
