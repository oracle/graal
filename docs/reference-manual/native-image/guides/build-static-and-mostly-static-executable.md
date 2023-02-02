---
layout: ni-docs
toc_group: how-to-guides
link_title: Build a Statically Linked or Mostly-Statically Linked Native Executable
permalink: /reference-manual/native-image/guides/build-static-executables/
---

# Build a Statically Linked or Mostly-Statically Linked Native Executable

GraalVM Native Image by default builds dynamically linked binaries: at build time it loads your application classes and interfaces and hooks them together in a process of dynamic linking.

However, you can create a statically linked or mostly-static linked native executable, depending on your needs. 

**A static native executable** is a statically linked binary that can be used without any additional library dependencies.
A static native executable is easy to distribute and deploy on a slim or distroless container (a scratch container).
You can create a static native executable by statically linking it against [musl-libc](https://musl.libc.org/), a lightweight, fast and simple `libc` implementation.

**A mostly-static native executable** is a binary that links everything (`zlib`, JDK shared libraries) except the standard C library, `libc`. This is an alternative option to staticly linking everything. Also, depending on the user's code, it may link `libstdc+` and `libgcc`.
This approach is ideal for deployment on a distroless container image.

> Note: This currently only works when linked against `libc`.

This guide shows how you can take advantage of Native Image linking options including fully dynamic, fully static, and mostly static (except `libc`) to generate an executable ideal for your deployment scenario.

### Table of Contents

- [Prerequisites](#prerequisites-and-preparation)
- [Build a Static Binary](#build-a-static-native-executable)
- [Build a Mostly Static Binary](#build-a-mostly-static-executable)

## Prerequisites and Preparation

The following prerequisites should be met:

- Linux AMD64 operating system
- GraalVM distribution for Java 11 of higher with [Native Image support](../README.md#install-native-image)
- A 64-bit `musl` toolchain, `make`, and `configure`
- The latest `zlib` library
 
As a preparation step, install the `musl` toolchain, compile and install `zlib` into the toolchain.

1. Download the `musl` toolchain from [musl.cc](https://musl.cc/). (We recommend [this one](https://more.musl.cc/10/x86_64-linux-musl/x86_64-linux-musl-native.tgz)). Extract the toolchain to a directory of your choice. This directory will be referred as `$TOOLCHAIN_DIR`.
2. Download the latest `zlib` library sources from [zlib.net](https://zlib.net/) and extract them. (This documentation uses `zlib-1.2.11`.)
3. Create a new environment variable, named `CC`:
    ```bash
    CC=$TOOLCHAIN_DIR/bin/gcc
    ```
4. Change into the `zlib` directory, and then run the following commands to compile and install `zlib` into the toolchain:
    ```bash
    ./configure --prefix=$TOOLCHAIN_DIR --static
    make
    make install
    ```

## Build a Static Native Executable

Assume you have the following source code saved in the `EnvMap.java` file:

```java
import java.util.Map;

public class EnvMap {
    public static void main (String[] args) {
        var filter = args.length > 0 ? args[0] : "";
        Map<String, String> env = System.getenv();
        for (String envName : env.keySet()) {
            if(envName.contains(filter)) {
                System.out.format("%s=%s%n",
                                envName,
                                env.get(envName));
            }
        }
    }
}
```

This application iterates over your environment variables and prints out the ones that contain the `String` of characters passed as a command line argument.

1. First, ensure the directory named `$TOOLCHAIN_DIR/bin` is present on your `PATH`.
    To verify this, run the following command:
    ```bash
    x86_64-linux-musl-gcc
    ```
    You should see output similar to the following:
    ```bash
    x86_64-linux-musl-gcc: fatal error: no input files
    compilation terminated.
    ```
2. Compile the file:
    ```shell
    javac EnvMap.java
    ```

3. Build a static native executable by running this command:
    ```shell
    native-image --static --libc=musl EnvMap
    ```
    This produces a native executable with statically linked system libraries.
    You can pass other arguments before a class or JAR file.

## Build a Mostly-Static Native Executable

With GraalVM Native Image you can build a mostly-static native executable that statically links everything except `libc`. Statically linking all your libraries except `libc` ensures your application has all the libraries it needs to run on any Linux `libc`-based distribution.

To build a mostly-static native executable, use this command:

```shell
native-image -H:+StaticExecutableWithDynamicLibC [other arguments] <Class>
```

To build a  a mostly-static native executable for the above `EnvMap` demo, run:

```shell
native-image -H:+StaticExecutableWithDynamicLibC EnvMap
```

This produces a native executable that statically links all involved libraries (including JDK shared libraries) except for `libc`. This includes `zlib`. Also, depending on the user's code, it may link `libstdc+` and `libgcc`.
One way to check what dynamic libraries your application depends on is to run `ldd` with the native executable, for example, `ldd helloworld`.

### Frequently Asked Questions

#### What is the recommended base Docker image for deploying a static or mostly-static native executable?

A fully static native executable gives you the most flexibility to choose a base container image - it can run on anything including a `FROM scratch` image.
A mostly-static native executable requires a container image that provides `libc`, but has no additional requirements.
In both cases, choosing the base container image generally depends on your native executable's specific requirements.

### Related Documentation

* [Tiny Java Containers](https://github.com/graalvm/graalvm-demos/tree/master/tiny-java-containers) demo shows how a simple Java application and a simple web server can be compiled to produce very small Docker container images using various lightweight base images.
* [GraalVM Native Image, Spring and Containerisation](https://luna.oracle.com/lab/fdfd090d-e52c-4481-a8de-dccecdca7d68) interactive lab to build a mostly static executable of a Spring Boot application.