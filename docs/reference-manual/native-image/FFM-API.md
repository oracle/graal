---
layout: docs
toc_group: native-code-interoperability
link_title: Foreign Function and Memory API
permalink: /reference-manual/native-image/native-code-interoperability/ffm-api/
redirect_from: /reference-manual/native-image/dynamic-features/ffm-api/
---

# Foreign Function and Memory API in Native Image

The Foreign Function and Memory (FFM) API is an interface that enables Java code to interact with native code and vice versa.
It was finalized in JDK 22 with [JEP 454](https://openjdk.org/jeps/454){:target="_blank"}.
This page gives an overview of the FFM API support in Native Image.

Support for the Foreign Function and Memory API in Native Image is enabled by default starting with GraalVM for JDK 25. It can be disabled (for example, to reduce binary size) using the `-H:-ForeignAPISupport` option, along with `-H:+UnlockExperimentalVMOptions`.
Modules that are permitted to perform _restricted_ native operations (including creating handles for calls to or from native code) must be specified using the `--enable-native-access=` option.

## Foreign Memory

Native Image supports all foreign memory features.

## Foreign Functions

The FFM API enables Java code to call _down_ to native functions, and conversely allows native code to call _up_ to invoke Java code via method handles.
These two kinds of calls are referred to as "downcalls" and "upcalls", respectively, and are collectively referred to as "foreign calls".

> Currently supported platforms for downcalls and upcalls are:
>
> * Linux/x64
> * macOS/x64
> * Windows/x64
> * macOS/AArch64
> * Linux/AArch64

### Looking Up Native Functions

The FFM API provides the `SymbolLookup` interface to find functions in native libraries by name.
Native Image supports all available symbol lookup methods, which are `SymbolLookup.loaderLookup()`, `SymbolLookup.libraryLookup()`, and `Linker.defaultLookup()`.
The default lookup (`Linker.defaultLookup()`) is currently not supported in static executables (built with option `-H:+StaticExecutable`).

### Registering Foreign Calls

To call native code at run time, the `native-image` builder must generate supporting code at build time.
You need to provide the builder with descriptors that describe the functions for downcalls or upcalls at run time.

For upcalls, it is recommended to register a specific static method as an upcall target by providing its declaring class and the method name.
This allows `native-image` to create specialized upcall code that can be orders of magnitude faster than an upcall registered only by a function descriptor.
Use this approach whenever possible.

You can specify the required descriptors for downcalls and upcalls by providing the appropriate configuration in the _reachability-metadata.json_ file. See [Using the Configuration File](#using-the-configuration-file).

#### Downcalls

Assume you want to call a C function:

```
int rand(void);
```

To do so, create a downcall handle in Java:

```java
Linker linker = Linker.nativeLinker();
SymbolLookup stdlib = linker.defaultLookup();
ValueLayout intLayout = linker.canonicalLayouts().get("int");

MethodHandle rand = linker.downcallHandle(
        stdlib.find("rand").orElseThrow(),
        FunctionDescriptor.of(intLayout));
```

To use the downcall handle in Native Image at run time, you must provide the following configuration at build time:

```json
{
  "foreign": {
    "downcalls": [
      {
        "returnType": "int",
        "parameterTypes": []
      }
    ]
  }
}
```

The return type `int` denotes a C data type and is one of several _canonical types_.
The following canonical types are guaranteed to be available on all platforms (see also the documentation for [`java.lang.foreign.Linker`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/foreign/Linker.html)):

* `bool`
* `char`
* `short`
* `int`
* `long`
* `long long`
* `float`
* `double`
* `size_t`
* `wchar_t`
* `void*` (for any pointer type)

All platforms that Native Image supports also provide canonical layouts for JNI types:

* `jboolean`
* `jchar`
* `jbyte`
* `jshort`
* `jint`
* `jlong`
* `jfloat`
* `jdouble`

The FFM API also lets you call native functions and pass structured types (such as C structs) by value.
Consider the following C struct `Point` and the C function `vector_length`:

```
struct Point {
    double x;
    double y;
};

double vector_length(struct Point x);
```

In Java, you can create the appropriate downcall handle like this:

```java
SymbolLookup lookup = SymbolLookup.libraryLookup(/* ... */);
MemoryLayout cDouble = linker.canonicalLayouts().get("double");
MemoryLayout pointLayout = MemoryLayout.structLayout(cDouble, cDouble);
MethodHandle vectorLength = linker.downcallHandle(
        lookup.find("vector_length").orElseThrow(),
        FunctionDescriptor.of(cDouble, pointLayout));
```

To call `vector_length`, you need this configuration file:

```json
{
  "foreign": {
    "downcalls": [
      {
        "returnType": "double",
        "parameterTypes": ["struct(double, double)"]
      }
    ]
  }
}
```

In this example, the parameter type `struct(double, double)` describes a structure consisting of two `double` members. The structure is compatible with the C type `struct Point` above.
Note that the C compiler automatically _aligns_ struct members which is not the case for Java.
If a structure consists of members with different sizes, it may be necessary to use a padding layout in order to align the members.

You can also describe more complex layouts:

| Layout Type     | Description | Code Snippet | Example |
| --------------- | ----------- | ------------ | ------- |
| Sequence layout | Corresponds to a fixed-size C array | `sequence(<length>, <elementLayout>)` | C array `int array[10]` becomes `sequence(10, int)` |
| Union layout | Corresponds to C union types | `union(<layout_0>, <layout_1>, ..., <layout_n>)` | C union `union { int i; long l; }` becomes `union(int, long)` |
| Padding layout | Specifies extra space for aligning member layouts around word boundaries. This fulfills alignment constraints in struct layouts (explained in ["Using Foreign Configuration Files on Different Platforms"](#using-foreign-configuration-files-on-different-platforms)) | `padding(<bytes>)` | - |

You can nest these layouts. Consider the following C example:

```
union Foo {
    long l;
    double d;
};

struct Bar {
    char x;
    const char *z;
    union Foo z[10];
};
```

In the foreign configuration file, express this as:

```
struct(char, padding(7), void*, sequence(10, union(long, double)))
```

The full syntax for specifying memory layouts is described in the ["Syntax (EBNF) for Specifying a Memory Layout"](#syntax-ebnf-for-specifying-a-memory-layout) section.

#### Linker Options

The FFM API can capture the error state of the native run time library (like `errno`).
For example, if you want to call this C library function:

```
double sin(double x);
```

According to the documentation, it may set `errno` if the argument is an infinity.
To capture the value of variable `errno` after calling the function `sin`, you need to specify a linker option when creating the downcall handle in Java:

```java
Linker linker = Linker.nativeLinker();
SymbolLookup stdlib = linker.defaultLookup();
MemoryLayout cDouble = linker.canonicalLayouts().get("double");

MethodHandle sin = linker.downcallHandle(
        stdlib.find("sin").orElseThrow(),
        FunctionDescriptor.of(cDouble, cDouble), Linker.Option.captureCallState("errno"));
```

In the configuration file, you also need to specify that you want to capture a call state:

```json
{
  "foreign": {
    "downcalls": [
      {
        "returnType": "double",
        "parameterTypes": ["double"],
        "options": {
          "captureCallState": true
        }
      }
    ]
  }
}
```

> Note: While it is necessary to specify the exact variable to capture (for example, `errno` in the above example) in Java, it is not necessary to do that in the configuration file.

In the configuration file, the linker options are specified as properties of a JSON object.
You can specify two additional linker options, `critical` and `firstVariadicArg`.
For a description of those options, please refer to the documentation of [`java.lang.foreign.Linker.Option`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/foreign/Linker.Option.html).

In Java, the linker option `critical` takes an argument which specifies if the called native function may access heap objects during execution:

```java
Linker.Option criticalWithHeapAccess = Linker.Option.critical(true);
```

The corresponding configuration file entry looks as follows:

```json
{
  "options": {
    "critical": {
      "allowHeapAccess": true
    }
  }
}
```

The `firstVariadicArg` linker option expects an `int` argument that specifies the index of the first variadic argument.
Consider the `printf` function from the standard C library:

```
int printf(const char *fmt, ...);
```

It has one explicit argument and then the variadic arguments start.
Therefore, the first variadic argument is at position `1` (note: counting starts at `0`).
In the configuration file, you specify this as follows:

```json
{
  "options": {
    "firstVariadicArg": 1
  }
}
```

#### Upcalls

You specify upcalls similarly to downcalls.
As an example, you want to use function `qsort` of the standard C library in order to sort a native array of Java integers:

```
void qsort(void *arr, size_t nmemb, size_t size,
           int (*compar)(const void *elem1, const void *elem2));
```

The fourth argument of function `qsort` is a function pointer of the element compare function.
The following Java class uses `qsort` to sort an array of Java `int` elements.

First, define the class `Qsort` with the method `qsortCompare` as the compare function for the native function `qsort`.
Since `qsort` only provides the addresses of the elements to compare, method `qsortCompare` expects two parameters of type `MemorySegment`.
The method then reads the integers from the provided addresses and compares them.

```java
    public static class Qsort {
        public static int qsortCompare(MemorySegment elem1, MemorySegment elem2) {
            return Integer.compare(elem1.get(ValueLayout.JAVA_INT, 0), elem2.get(ValueLayout.JAVA_INT, 0));
        }
    }
```

Next, create the downcall handle for function `qsort`.
Note that the fourth parameter (the function pointer for the compare function) is also a pointer.
So, you use `C_POINTER` in the function descriptor:

```java
    private static final SymbolLookup STDLIB = Linker.nativeLinker().defaultLookup();
    private static final MemoryLayout C_SIZE_T = Linker.nativeLinker().canonicalLayouts().get("size_t");
    private static final AddressLayout C_POINTER = (AddressLayout) Linker.nativeLinker().canonicalLayouts().get("void*");

    private static final MethodHandle QSORT = Linker.nativeLinker().downcallHandle(
        STDLIB.find("qsort").orElseThrow(),
        FunctionDescriptor.ofVoid(C_POINTER, C_SIZE_T, C_SIZE_T, C_POINTER)
    );
```

Next, create the function descriptor for the compare function.
The compare function always returns a C `int`, so use layout `C_INT`.
The parameters are the addresses of the elements to compare, which is represented by the memory layout `C_POINTER`.
However, you are also _dereferencing_ those pointers (since you read the values), so you need to specify the target layout of the pointer.
In this case, you want to read Java `int`, so you use `ValueLayout.JAVA_INT`:

```java
    private static final MemoryLayout C_INT = Linker.nativeLinker().canonicalLayouts().get("int");

    private static final FunctionDescriptor QSORT_COMPARE_DESC = FunctionDescriptor.of(
        C_INT,
        C_POINTER.withTargetLayout(ValueLayout.JAVA_INT),
        C_POINTER.withTargetLayout(ValueLayout.JAVA_INT)
    );
```

In the static constructor, create a method handle to the Java method used as the compare function, then create the upcall stub using the method handle and the previously created function descriptor.
The result of the call to `Linker.upcallStub(...)` is itself a `MemorySegment`, because the stub needs to be a callable native function pointer.

```java
    private static final MemorySegment QSORT_COMPARE_STUB;

    static {
        try {
            MethodHandle ch = MethodHandles.lookup().findStatic(
                    Qsort.class,
                    "qsortCompare",
                    MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class)
            );
            // Create function pointer for qsortCompare
            QSORT_COMPARE_STUB = Linker.nativeLinker().upcallStub(ch, QSORT_COMPARE_DESC, Arena.ofAuto());
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
```

Now you can write a Java method `invokeQsort` that calls the C function `qsort` using the downcall handle `QSORT`.
The method `invokeQsort` takes one parameter, the Java `int` array to sort.
However, the C function `qsort` cannot directly access a Java array.
You need to create a memory arena and copy the elements of the Java array into off-heap memory.
Then call `qsort`, passing the off-heap `int` array, its length, the size of each element, and the pointer to the compare function.
After that, transfer the sorted off-heap array back into a Java `int` array.

```java
    public static int[] invokeQsort(int[] unsortedArray) {
        try (Arena arena = Arena.ofConfined()) {
            // Allocate off-heap memory and store unsortedArray in it
            MemorySegment array = arena.allocateFrom(ValueLayout.JAVA_INT, unsortedArray);

            // Call qsort
            QSORT.invoke(array, (long) unsortedArray.length, ValueLayout.JAVA_INT.byteSize(), QSORT_COMPARE_STUB);

            // Access off-heap memory
            return array.toArray(ValueLayout.JAVA_INT);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
```

To create the downcall and upcall at run time, you need to provide a foreign configuration file that:

1. Specifies a downcall stub for function `qsort`
2. Specifies an upcall stub for the compare function

```json
{
  "foreign": {
    "downcalls": [
      {
        "returnType": "void",
        "parameterTypes": ["void*", "size_t", "size_t", "void*"]
      }
    ],
    "upcalls": [
      {
        "returnType": "int",
        "parameterTypes": ["void*", "void*"]
      }
    ]
  }
}
```

If the _target method_ of an upcall is a static method, use a `directUpcall` for better performance.
This creates an optimized variant of the upcall stub that's usually much faster and should be preferred.

```json
{
  "foreign": {
    "downcalls": [
      {
        "returnType": "void",
        "parameterTypes": ["void*", "size_t", "size_t", "void*"]
      }
    ],
    "directUpcalls": [
      {
        "class": "org.example.QSortInvoke$Qsort",
        "method": "qsortCompare",
        "returnType": "int",
        "parameterTypes": ["void*", "void*"]
      }
    ]
  }
}
```

For completeness, here is the full example:

```java
package org.example;

// imports ...

public class InvokeQsort {

    public static class Qsort {
        public static int qsortCompare(MemorySegment elem1, MemorySegment elem2) {
            return Integer.compare(elem1.get(ValueLayout.JAVA_INT, 0), elem2.get(ValueLayout.JAVA_INT, 0));
        }
    }

    private static final SymbolLookup STDLIB = Linker.nativeLinker().defaultLookup();
    private static final MemoryLayout C_SIZE_T = Linker.nativeLinker().canonicalLayouts().get("size_t");
    private static final AddressLayout C_POINTER = (AddressLayout) Linker.nativeLinker().canonicalLayouts().get("void*");

    private static final MethodHandle QSORT = Linker.nativeLinker().downcallHandle(
            STDLIB.find("qsort").orElseThrow(),
            FunctionDescriptor.ofVoid(C_POINTER, C_SIZE_T, C_SIZE_T, C_POINTER)
    );

    private static final MemoryLayout C_INT = Linker.nativeLinker().canonicalLayouts().get("int");

    // Function descriptor of the qsort compare function
    private static final FunctionDescriptor QSORT_COMPARE_DESC = FunctionDescriptor.of(
            C_INT,
            C_POINTER.withTargetLayout(ValueLayout.JAVA_INT),
            C_POINTER.withTargetLayout(ValueLayout.JAVA_INT)
    );

    private static final MemorySegment QSORT_COMPARE_STUB;

    static {
        try {
            MethodHandle ch = MethodHandles.lookup().findStatic(
                    Qsort.class,
                    "qsortCompare",
                    MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class)
            );
            // Create function pointer for qsortCompare
            QSORT_COMPARE_STUB = Linker.nativeLinker().upcallStub(ch, QSORT_COMPARE_DESC, Arena.ofAuto());
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static int[] invokeQsort(int[] unsortedArray) {
        try (Arena arena = Arena.ofConfined()) {
            // Allocate off-heap memory and store unsortedArray in it
            MemorySegment array = arena.allocateFrom(ValueLayout.JAVA_INT, unsortedArray);

            // Call qsort
            QSORT.invoke(array, (long) unsortedArray.length, ValueLayout.JAVA_INT.byteSize(), QSORT_COMPARE_STUB);

            // Access off-heap memory
            return array.toArray(ValueLayout.JAVA_INT);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
```

#### Using the Configuration File

The foreign configuration is part of the _reachability-metadata.json_ file and is often combined with other metadata for reflection, JNI, and more.
You can specify the configuration file to `native-image` in two ways:

* **Via command line**: Use `-H:ConfigurationFileDirectories=/path/to/config-dir/` where the directory directly contains _reachability-metadata.json_
* **Via classpath**: Put the file in the _META-INF/native-image/_ directory on the classpath
  * The builder automatically picks it up at build time
  * The builder also searches subdirectories of _META-INF/native-image/_ for files named _reachability-metadata.json_

Please also refer to the general description of [Reachability Metadata](ReachabilityMetadata.md) and to [Specify Configuration Files as Argument](AutomaticMetadataCollection.md#specify-configuration-files-as-arguments) for more information.

#### Automatic Metadata Collection Using the Tracing Agent

The FFM API configuration can be collected automatically using the [Tracing Agent](AutomaticMetadataCollection.md) that is part of GraalVM.
The agent tracks all usages of FFM API that creates upcalls or downcalls during application execution on a regular Java VM.
When the application completes and the JVM exits, the agent writes configuration to the _reachability-metadata.json_ file in the specified output directory.

#### Register Stubs in a Custom Feature

As an alternative to writing a foreign configuration file, you can implement a custom `Feature` to register descriptors and target methods during the image build setup phase using the `RuntimeForeignAccess` class.
This class provides three methods: `registerForDowncall`, `registerForUpcall`, and `registerForDirectUpcall`.
Each of those methods registers a single stub corresponding to one entry in the foreign configuration file sections `downcalls`, `upcalls`, and `directUpcalls`, respectively.

Referring to the examples in sections [Downcalls](#downcalls) and [Upcalls](#upcalls), the following custom feature would replace the foreign configuration file:

```java
package com.example;

// imports ...

public class CustomFeature implements Feature {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final MemoryLayout C_INT = LINKER.canonicalLayouts().get("int");
    private static final MemoryLayout C_DOUBLE = LINKER.canonicalLayouts().get("double");
    private static final MemoryLayout C_POINTER = LINKER.canonicalLayouts().get("void*");
    private static final MemoryLayout C_INT_POINTER = C_POINTER.withTargetLayout(ValueLayout.JAVA_INT);
    private static final MemoryLayout C_SIZE_T = LINKER.canonicalLayouts().get("size_t");
    private static final MemoryLayout C_LONG_LONG = LINKER.canonicalLayouts().get("long long");
    private static final MemoryLayout C_WCHAR_T = LINKER.canonicalLayouts().get("wchar_t");
    private static final MemoryLayout POINT_LAYOUT = MemoryLayout.structLayout(C_DOUBLE, C_DOUBLE);
    private static final int PADDING_SIZE =  C_LONG_LONG.byteSize() - C_WCHAR_T.byteSize();
    private static final MemoryLayout NEEDS_PADDING = MemoryLayout.structLayout(C_WCHAR_T.withName("x"),
                                                                                MemoryLayout.padding(PADDING_SIZE),
                                                                                C_LONG_LONG.withName("y"));

    @Override
    public void duringSetup(DuringSetupAccess access) {
        // required for C function 'int rand(void)'
        RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.of(C_INT));

        // required for C function 'double vector_length(struct Point x)'
        RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.of(C_DOUBLE, POINT_LAYOUT));

        // required for C function 'double sin(double x)'; capture "errno"
        RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.of(C_DOUBLE, C_DOUBLE), Linker.Option.captureCallState("errno"));

        // required for C function 'void qsort(void *arr, size_t nmemb, size_t size, int (*compar)(const void *e0, const void *e1))'
        RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.ofVoid(C_POINTER, C_SIZE_T, C_SIZE_T, C_POINTER));
        try {
            FunctionDescriptor qsortCompareDesc = FunctionDescriptor.of(C_INT, C_INT_POINTER, C_INT_POINTER);
            MethodHandle target = MethodHandles.lookup().findStatic(InvokeQSort.QSort.class, "compare", qsortCompareDesc.toMethodType());
            RuntimeForeignAccess.registerForDirectUpcall(target, qsortCompareDesc);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        // required for C function 'void foo(struct NeedsPadding arg)'
        RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.ofVoid(NEEDS_PADDING));
  }
}
```

To activate the custom feature, pass the `--features=com.example.CustomFeature` option (the fully-qualified name of the feature class) to `native-image`.
It is recommended to do so [with a _native-image.properties_ file](BuildConfiguration.md#embed-a-configuration-file).

#### Using Foreign Configuration Files on Different Platforms

The descriptors in foreign configuration files describe the signature of native functions.
Therefore, the used type names (for example, `long`) correspond to C types.
When writing foreign configuration files for use on different platforms, you need to be aware that native data types may have different byte size on different platforms.
For example, C `long` has a size of 8 bytes on most platforms, but has a size of only 4 bytes on Windows/x64.
Therefore, the C `long` data type will be mapped to different Java types depending on the platform.
In case of 8 bytes, it will be mapped to Java `long` and in case of 4 bytes, it will be mapped to Java `int`.

Such differences may explain why a certain downcall or upcall stub is not found at run time.
Since the data types used in foreign configuration files are resolved using `Linker.nativeLinker().canonicalLayouts().get(...)`, it is recommended to use the `canonicalLayouts` also in Java code instead of constants like `java.lang.foreign.ValueLayout.JAVA_INT`.
This ensures that they map to the same Java type at run time.

For upcalls, you may need to create multiple overloads for the same upcall target method.
For example, consider the following C function type declaration:

```
long (*upcall_fun_t)(long);
```

To create an upcall that satisfies this C function type, you need to register this stub in a foreign configuration file:

```json
{
  "foreign": {
    "upcalls": [
      {
        "returnType": "long",
        "parameterTypes": ["long"]
      }
    ]
  }
}
```

Depending on the platform, you need different Java methods to be able to create an upcall stub at run time.
This is because the method type (Java class `MethodType`) of the method handle for the upcall target needs to be compatible with the method type of the function descriptor.
For instance, to use the above foreign configuration file:

```java
public class UpcallMethods {
    // required on, for example, Linux/x64
    public static long upcallTarget(long x) {
        return x + 1;
    }

    // required on, for example, Windows/x64
    public static int upcallTarget(int x) {
        return x + 1;
    }
}
```

If possible, use fixed-size data types, such as the JNI types `jboolean`, `jbyte`, `jchar`, `jshort`, `jint`, `jlong`, `jfloat` and `jdouble` in C code as well.

These always reliably map to the same Java layout on all platforms.
For instance, `jlong` always maps to Java `long` on all platforms:

```
jlong (*upcall_fun_t)(jlong);
```

You can then use the JNI type in the configuration file:

```json
{
  "foreign": {
    "upcalls": [
      {
        "returnType": "jlong",
        "parameterTypes": ["jlong"]
      }
    ]
  }
}
```

Structured data types may also be problematic if used on different platforms.
For example:

```
struct NeedsPadding {
    wchar_t x;
    long long y;
}

void foo(struct NeedsPadding arg) {
    /* ... */
}
```

C compilers usually _align_ structure members automatically.
This is done because aligned memory accesses are more efficient and for portability.
Some architectures don't support misaligned memory accesses, as this reduces hardware complexity.
To align the members of a structure, C compilers insert padding between them.

When you create a struct layout to describe a C data structure, the members are not aligned automatically.
You need to insert the padding manually by creating padding layouts.

For example, since the members of struct `NeedsPadding` have a different byte size, you must explicitly specify the padding.
For Linux/x64 this would be done as follows:

```java
MemoryLayout cWcharT = LINKER.canonicalLayouts().get("wchar_t");
MemoryLayout cLongLong = LINKER.canonicalLayouts().get("long long");
MemoryLayout needsPadding = MemoryLayout.structLayout(cWcharT.withName("x"), MemoryLayout.padding(4), cLongLong.withName("y"));
```

However, the memory layout `needsPadding` cannot be constructed on Windows/x64 because the byte size of `wchar_t` is 2 and this results in an invalid alignment of member `y` (it would start at byte offset 6).

If you specify a downcall stub in the foreign configuration file, this would look as follows:

```json
{
  "foreign": {
    "downcalls": [
      {
        "returnType": "void",
        "parameterTypes": ["struct(wchar_t, padding(4), long long)"]
      }
    ]
  }
}
```

Unfortunately, this foreign configuration file cannot be used on Windows/x64 and there is currently no way to express such a difference in the configuration file.

There are currently two options to solve that problem:

* Write separate configuration files for each platform.
* Implement a [custom feature class](#register-stubs-in-a-custom-feature) to programmatically register the stubs.

If you implement a custom feature class, you can determine the necessary padding programmatically based on the actual size of the members.
For structure `NeedsPadding`, you compute the necessary padding between member `x` and `y` by calculating the difference of their byte sizes in method `computePadding`:

```java
package com.example;

// imports ...

public class ComputePaddingFeature implements Feature {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final MemoryLayout C_LONG_LONG = LINKER.canonicalLayouts().get("long long");
    private static final MemoryLayout C_WCHAR_T = LINKER.canonicalLayouts().get("wchar_t");
    private static final MemoryLayout NEEDS_PADDING = MemoryLayout.structLayout(C_WCHAR_T.withName("x"),
            computePadding(),
            C_LONG_LONG.withName("y"));

    private static MemoryLayout computePadding() {
        assert C_LONG_LONG.byteSize() > C_WCHAR_T.byteSize();
        return MemoryLayout.padding(C_LONG_LONG.byteSize() - C_WCHAR_T.byteSize());
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        // required for C function 'void foo(struct NeedsPadding arg)'
        RuntimeForeignAccess.registerForDowncall(FunctionDescriptor.ofVoid(NEEDS_PADDING));
    }
}
```

The padding will be 4 bytes on Linux/x64 and 6 bytes on Windows/x64.

#### Syntax (EBNF) for Specifying a Memory Layout

The following EBNF defines the syntax for specifying memory layouts in foreign configuration files:

```
Layout ::= Alignment | StructLayout | UnionLayout | ValueLayout | SequenceLayout | PaddingLayout
Alignment ::= 'align' '(' Int ',' Layout ')'
StructLayout ::= 'struct' '(' [ Layout { ',' Layout } ] ')'
UnionLayout ::=  'union' '(' [ Layout { ',' Layout } ] ')'
SequenceLayout ::= 'sequence' '(' Int ',' Layout ')'
ValueLayout ::=  canonical layout (for example, 'int', 'long', ...)
PaddingLayout ::= 'padding' '(' Int ')'
Int ::= a positive (decimal) integer
```

### Initializing Downcalls and Upcalls During Image Build

In general, it is not possible to initialize classes at build time if they are creating downcall handles or upcall stubs in the static initializer.
This is because, in both cases, the image build would _freeze_ native addresses as constants into the resulting native image.

These native addresses are:

* Addresses of native functions
* Addresses of the call stubs created by the image builder VM

However, when you run the native image, those addresses will most likely be different and no longer valid.

This is also why the `native-image` tool currently disallows native `MemorySegment` instances from being included in the resulting image heap.

## Related Documentation

* [Interoperability with Native Code](InteropWithNativeCode.md)
* [Collect Metadata with the Tracing Agent](AutomaticMetadataCollection.md)
* [Reachability Metadata](ReachabilityMetadata.md)
* [reachability-metadata-schema-v1.1.0.json](https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/assets/reachability-metadata-schema-v1.1.0.json)
