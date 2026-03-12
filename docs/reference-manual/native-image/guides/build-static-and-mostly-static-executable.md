---
layout: ni-docs
toc_group: how-to-guides
link_title: Build a Statically Linked or Mostly-Statically Linked Native Executable
permalink: /reference-manual/native-image/guides/build-static-executables/
redirect_from: /reference-manual/native-image/StaticImages/
---

# Build a Statically Linked or Mostly-Statically Linked Native Executable

GraalVM Native Image by default builds dynamically linked binaries: at build time it first loads your application classes and interfaces, and then hooks them together in a process of dynamic linking.

However, you can create a statically linked or mostly-statically linked native executable, depending on your needs.

**A static native executable** is a statically linked binary that you can use without any additional library dependencies.
A static native executable is easy to distribute and deploy on a slim or distroless container (a scratch container).
You can create a static native executable by statically linking it against [musl-libc](https://musl.libc.org/), a lightweight, fast and simple `libc` implementation.
Statically linking against `glibc` is not supported and will trigger an error at build time.

**A mostly-static native executable** is a binary that links all the shared libraries on which the native executable relies (`zlib`, JDK-shared static libraries) except the standard C library, `libc`. This is an alternative option to statically linking everything. Also, depending on the user's code, it may link `libstdc+` and `libgcc`.
This approach is useful for deployment on a distroless container image.

This guide shows how you can take advantage of Native Image linking options including fully dynamic, fully static, and mostly-static (except `libc`) to generate an executable ideal for your deployment scenario.

## Prerequisites and Preparation

- Linux x64 operating system
- GraalVM distribution for Java 17 or higher
- A 64-bit `musl` toolchain, `make`, and `configure`
- The latest `zlib` library

The easiest way to install GraalVM is with [SDKMAN!](https://sdkman.io/jdks#graal).
For other installation options, visit the [Downloads section](https://www.graalvm.org/downloads/).

To create statically linked applications with Native Image, you need a `musl` toolchain that includes the `zlib` library.
Download the recommended musl toolchain: [musl-toolchain-1.2.5-oracle-00001-linux-amd64.tar.gz](https://gds.oracle.com/download/bfs/archive/musl-toolchain-1.2.5-oracle-00001-linux-amd64.tar.gz).

Download the archive, extract it, and add the toolchain's `bin` directory to your system path:
```shell
curl -SLO https://gds.oracle.com/download/bfs/archive/musl-toolchain-1.2.5-oracle-00001-linux-amd64.tar.gz
tar xzf musl-toolchain-1.2.5-oracle-00001-linux-amd64.tar.gz
export PATH="$(pwd)/musl-toolchain/bin:$PATH"
```

With the requirements set up, create the demo.

## Build a Static Native Executable

1. Save the following source code in a file named _EnvMap.java_:
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

2. Compile the application:
    ```shell
    javac EnvMap.java
    ```

3. Build a static native executable by running this command:
    ```shell
    native-image --static --libc=musl EnvMap
    ```
    This produces a native executable with statically linked system libraries.
    Run it with `./envmap`.

    You can confirm the application is fully statically linked using the `ldd` command:
    ```shell
    ldd envmap
    ```
    The output should be "not a dynamic executable".

## Build a Mostly-Static Native Executable

With GraalVM Native Image you can build a mostly-static native executable that statically links everything except `libc`.
Statically linking all your libraries except `libc` ensures your application has all the libraries it needs to run on any Linux `libc`-based distribution.

To build a mostly-static native executable, use this command:
```shell
native-image --static-nolibc [other arguments] <Class>
```

To build a mostly-static native executable for the above `EnvMap` demo, run:
```shell
native-image --static-nolibc EnvMap
```

This produces a native executable that statically links all involved libraries (including JDK-shared static libraries) except for `libc`.
This includes `zlib`.
Also, depending on the user's code, it may link `libstdc+` and `libgcc`.
One way to check what dynamic libraries your application depends on is to run `ldd` with the native executable, for example, `ldd envmap`.

### Frequently Asked Questions

#### What is the recommended base container image for deploying a static or mostly-static native executable?

A fully static native executable gives you the most flexibility to choose a base container image&mdash;it can even run on a `scratch` image.
A mostly-static native executable requires a container image that provides `libc`, specifically `glibc`, but has no additional requirements.
In both cases, choosing the base container image generally depends on your native executable's specific requirements.

### Related Documentation

* [Tiny Java Containers](https://github.com/graalvm/graalvm-demos/tree/master/native-image/tiny-java-containers) demo shows how a simple Java application and a simple web server can be compiled to produce very small Docker container images using various lightweight base images.
* [From JIT to Native: Efficient Java Containers with GraalVM and Spring Boot](https://github.com/graalvm/workshops/tree/main/native-image/spring-boot-webserver) workshop demonstrates how to build efficient, size-optimized native applications, and deploy them in various containers.