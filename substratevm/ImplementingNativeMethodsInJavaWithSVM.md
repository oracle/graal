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
    public static int add(Pointer jniEnv, Pointer clazz, @CEntryPoint.IsolateThreadContext long isolateId, int a, int b) {
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
portable (e.g. `long`) identifier of the [SubstrateVM isolatethread](C-API.md).
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
    private static native int add(long isolateThreadId, int a, int b);
}
```
the package name of the class as well as name of the method has to correspond
(after the [JNI mangling](https://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/design.html))
to the name of the `@CEntryPoint` introduced previously. The first argument is
a portable (e.g. `long`) identifier of the SubstrateVM isolate thread. The rest of the arguments
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
`CEntryPoint.Builtin.CREATE_ISOLATE`. Define another method along your other
existing `@CEntryPoint` methods. Let it return `IsolateThread` and take no parameters:
```java
public final class NativeImpl {
    @CEntryPoint(name = "Java_org_pkg_apinative_Native_createIsolate", builtin=CEntryPoint.Builtin.CREATE_ISOLATE)
    public static native IsolateThread createIsolate();
}
```
SubstrateVM then generates default native implementation of the
method into the final `.so` library.
The method initializes the Substrate VM runtime and
returns a portable identification - e.g. `long` to hold
an instance of a [Substrate VM isolatethread](C-API.md). The isolate thread can then be used for
multiple invocations of the native part of our code:
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
The standard JVM is started. It initializes a Substrate VM isolate,
attaches current thread to the isolate and the universal answer `42` is
then computed three times inside of the isolate.

## Calling JVM from Native Java

There is a detailed [tutorial on the C interface](src/com.oracle.svm.tutorial/src/com/oracle/svm/tutorial/CInterfaceTutorial.java)
of Substrate VM. Rather than repeating it here, let's apply some of its
principles to a related topic, the implementation of native methods. Let's make
a callback to JVM!

In the classical setup, when C needs to call into JVM, it uses [jni.h](JNI.md)
header file. The file defines essential JVM structures (like `JNIEnv`) as well as
functions one can invoke to inspect classes, access fields, and call methods
in the JVM. In order to call these functions from our `NativeImpl` class in our
example, we need to define appropriate Java API wrappers of the `jni.h` concepts:

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

If we leave aside the meaning of `JNIHeaderDirectives` for now, the rest
of the interfaces is a type-safe representation of the C pointers found in the
`jni.h` file. `JClass`, `JMethodID`, `JObject` are all pointers. Thanks to
the above definitions we now have Java interfaces to represent
instances of these objects in our native Java code in a type safe way.

The core part of any [JNI](JNI.md) API is the set of functions one can call
when talking to the JVM. There are dozens of them, but in the `JNINativeInterface`
definition we just define wrappers for those few that we need in our example.
Again, we give them proper types, so in our native Java code we can use
`GetMethodId.find(...)`, `CallStaticVoidMethod.call(...)`, etc. In addition,
there is another important part missing in the puzzle - the `jvalue` union type
wrapping all the possible Java primitive and object types. Here comes definition
of its getters and setters:

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
The `addressOf` method is a special Substrate VM construct used to perform
C pointer arithmetics. Given a pointer one can treat it as initial element of
an array, then for example, use `addressOf(1)` to access the subsequent element.
With this we have all the API we need to make a callback: let's redefine
the previously introduced `NativeImpl.add` method to accept properly typed
pointers, and then use these pointers to invoke a JVM method before computing
the sum of `a + b`:

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

The above example seeks a static method `hello` and invokes it with eight
`JValue` parameters in an array reserved by `StackValue.get`
on the stack. Individual parameters are accessed by use of
the `addressOf` operator and filled with appropriate primitive values
before the call happens. The method `hello` is defined in the class `Native`
and just prints values of all parameters to verify they are properly
propagated from the `NativeImpl.add` caller:

```java
public class Native {
    public static void hello(boolean z, char c, byte b, short s, int i, long j, float f, double d) {
        System.err.println("Hi, I have just been called back!");
        System.err.print("With: " + z + " " + c + " " + b + " " + s);
        System.err.println(" and: " + i + " " + j + " " + f + " " + d);
    }
```

There is just one final piece to explain: the `JNIHeaderDirectives`.
Substrate VM C interface needs to understand the layout of the C structures. It
needs to know at which offset of `JNINativeInterface` structure it can find
the pointer to `GetMethodId` function. To do so, it needs `jni.h` and additional
files during compilation. One can specify them by `@CContext` annotation and
implementation of its `Directives`:

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

The good thing is that `jni.h` is inside of every JDK, so one can use the
`java.home` property to locate the necessary header files. The actual logic
can, of course, be made more robust and OS-independent.

Implementing any JVM native method in Java and/or making callbacks to the JVM
with Substrate VM should now be as easy as expanding upon the given example
and invoking `native-image`.
