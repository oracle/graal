---
layout: ni-docs
toc_group: how-to-guides
link_title: Build a Native Shared Library
permalink: /reference-manual/native-image/guides/build-native-shared-library/
---

# Build a Native Shared Library

To build a native shared library, pass the command-line argument `--shared` to the `native-image` tool, as follows:

```shell
native-image <class name> --shared
```

To build a native shared library from a JAR file, use the following syntax:
```shell
native-image -jar <jarfile> --shared
```

The resulting native shared library will have the `main()` method of the given Java class as its entrypoint method.

If your library does not include a `main()` method, use the `-o` command-line option to specify the library name, as follows:

```shell
native-image --shared -o <libraryname> <class name>
native-image --shared -jar <jarfile> -o <libraryname>
```

GraalVM makes it easy to use C to call into a native shared library. 
There are two primary mechanisms for calling a method (function) embedded in a native shared library: the [Native Image C API](../C-API.md) and the [JNI Invocation API](https://docs.oracle.com/en/java/javase/22/docs/specs/jni/invocation.html).

This guide describes how to use the **Native Image C API**. It consists of the following steps:
1. Create and compile a Java class library containing at least one entrypoint method.
2. Use the `native-image` tool to create a shared library from the Java class library.
3. Create and compile a C application that calls that entrypoint method in the shared library.

### Tips and Tricks

The shared library must have at least one entrypoint method.
By default, only a method named `main()`, originating from a `public static void main()` method, is identified as an entrypoint and callable from a C application.

To export any other Java method:

* Declare the method as static.
* Annotate the method with `@CEntryPoint` (`org.graalvm.nativeimage.c.function.CEntryPoint`).
* Make one of the method's parameters of type `IsolateThread` or `Isolate`, for example, the first parameter (`org.graalvm.nativeimage.IsolateThread`) in the method below. This parameter provides the current thread's execution context for the call.
* Restrict your parameter and return types to non-object types. These are Java primitive types including pointers, from the `org.graalvm.nativeimage.c.type` package.
* Provide a unique name for the method. If you give two exposed methods the same name, the `native-image` builder will fail with the `duplicate symbol` message. If you do not specify the name in the annotation, you must provide the `-o <libraryName>` option at build time.

Below is an example of the entrypoint method:

```java
@CEntryPoint(name = "function_name")
static int add(IsolateThread thread, int a, int b) {
    return a + b;
}
```

When the `native-image` tool builds a native shared library, it also generates a C header file.
The header file contains declarations for the [Native Image C API](../C-API.md) (which enables you to create isolates and attach threads from C code) as well as declarations for each entrypoint in the shared library.
This is the C header declaration for the example above:
```c
int add(graal_isolatethread_t* thread, int a, int b);
```

A native shared library can have an unlimited number of entrypoints, for example to implement callbacks or APIs.

### Run a Demo

In the following example, you create a small Java class library (containing one class), use `native-image` to create a shared library from the class library, and then create a small C application that uses that shared library.
The C application takes a string as its argument, passes it to the shared library, and prints environment variables that contain the argument.

1. Make sure you have installed GraalVM.
The easiest way to install it is with [SDKMAN!](https://sdkman.io/jdks#graal).
For other installation options, visit the [Downloads page](https://www.graalvm.org/downloads/).

2. Save the following Java code to a file named _LibEnvMap.java_:

    ```java
    import java.util.Map;
    import org.graalvm.nativeimage.IsolateThread;
    import org.graalvm.nativeimage.c.function.CEntryPoint;
    import org.graalvm.nativeimage.c.type.CCharPointer;
    import org.graalvm.nativeimage.c.type.CTypeConversion;

    public class LibEnvMap {
        //NOTE: this class has no main() method

        @CEntryPoint(name = "filter_env")
        private static int filterEnv(IsolateThread thread, CCharPointer cFilter) {
            String filter = CTypeConversion.toJavaString(cFilter);
            Map<String, String> env = System.getenv();
            int count = 0;
            for (String envName : env.keySet()) {
                if(!envName.contains(filter)) continue;
                System.out.format("%s=%s%n",
                                envName,
                                env.get(envName));
                count++;
            }
            return count;
        }
    }
    ```
    Notice how the method `filterEnv()` is identified as an entrypoint using the `@CEntryPoint` annotation and the method is given a name as a argument to the annotation. 

3. Compile the Java code and build a native shared library, as follows:
    ```shell
    $JAVA_HOME/bin/javac LibEnvMap.java
    ```
    ```shell
    $JAVA_HOME/bin/native-image -o libenvmap --shared 
    ```

    It produces the following artifacts:
    ```
    --------------------------------------------------
    Produced artifacts:
    /demo/graal_isolate.h (header)
    /demo/graal_isolate_dynamic.h (header)
    /demo/libenvmap.dylib (shared_lib)
    /demo/libenvmap.h (header)
    /demo/libenvmap_dynamic.h (header)
    ==================================================
    ```

    If you work with C or C++, use these header files directly. For other languages, such as Java, use the function declarations in the headers to set up your foreign call bindings. 

4. Create a C application, _main.c_, in the same directory containing the following code:

    ```c
    #include <stdio.h>
    #include <stdlib.h>

    #include "libenvmap.h"

    int main(int argc, char **argv) {
    if (argc != 2) {
        fprintf(stderr, "Usage: %s <filter>\n", argv[0]);
        exit(1);
    }

    graal_isolate_t *isolate = NULL;
    graal_isolatethread_t *thread = NULL;

    if (graal_create_isolate(NULL, &isolate, &thread) != 0) {
        fprintf(stderr, "initialization error\n");
        return 1;
    }

    printf("Number of entries: %d\n", filter_env(thread, argv[1]));

    graal_tear_down_isolate(thread);
    }
    ```
    
    The statement `#include "libenvmap.h"` loads the native shared library.


5. Compile _main.c_ using the `clang` compiler available on your system:
    ```shell
    clang -I ./ -L ./ -l envmap -Wl,-rpath ./ -o main main.c 
    ```
    It creates an executable file `main`.

6. Run the C application by passing a string as an argument. For example:
    ```shell
    ./main USER
    ```
    It correctly prints out the name and value of the matching environment variable(s). 

The advantage of using the Native Image C API is that you can determine what your API will look like. 
The restriction is that your parameter and return types must be non-object types.
If you want to manage Java objects from C, you should consider [JNI Invocation API](../JNI.md). 

### Related Documentation

* [Embedding Truffle Languages](https://nirvdrum.com/2022/05/09/truffle-language-embedding.html)-- a blog post by Kevin Menard where he compares both mechanisms for exposing Java methods.
* [Interoperability with Native Code](../InteropWithNativeCode.md)
* [Java Native Interface (JNI) in Native Image](../JNI.md)
* [Native Image C API](../C-API.md)
