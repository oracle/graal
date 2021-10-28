---
layout: docs
toc_group: native-image
link_title: Static Native Images
permalink: /reference-manual/native-image/StaticImages/
---
# Static and Mostly Static Images

With GraalVM Native Image you can create static or mostly static images, depending on the purposes.

**Static native images** are statically linked binaries which can be used without any additional library dependencies.
This makes them easier to distribute and to deploy on slim or distroless container images.
They are created by statically linking against [musl-libc](https://musl.libc.org/), a lightweight, fast and simple `libc` implementation.

**Mostly static native images** statically link against all libraries except `libc`.
This approach is ideal for deploying such native images on distroless container images.
Note that it currently only works when linking against `glibc`.

## Prerequisites

- Linux AMD64 operating system
- GraalVM distribution for Java 11 with [Native Image support](README.md#install-native-image)
- A 64-bit `musl` toolchain, `make`, and `configure`
- The latest `zlib` library

## Preparation

You should get the `musl` toolchain first, and then compile and install `zlib` into the toolchain.

1. Download the `musl` toolchain from [musl.cc](https://musl.cc/). [This one](http://musl.cc/x86_64-linux-musl-native.tgz) is recommended. Extract the toolchain to a directory of your choice. This directory will be referred as `$TOOLCHAIN_DIR`.
2. Download the latest `zlib` library sources from [here](https://zlib.net/) and extract them. This guide uses `zlib-1.2.11`.
3. Set the following environment variable:
    ```bash
    CC=$TOOLCHAIN_DIR/bin/gcc
    ```
4. Change into the `zlib` directory, and then run the following commands to compile and install `zlib` into the toolchain:
    ```bash
    ./configure --prefix=$TOOLCHAIN_DIR --static
    make
    make install
    ```

## Build Static Native Image

1. First, ensure `$TOOLCHAIN_DIR/bin` is present on your `PATH` variable.
    To verify this, run:
    ```bash
    x86_64-linux-musl-gcc
    ```
    You should get a similar output printed:
    ```bash
    x86_64-linux-musl-gcc: fatal error: no input files
    compilation terminated.
    ```

2. Build a static native image by using this command:
    ```shell
    native-image --static --libc=musl [other arguments] Class
    ```

## Build Mostly Static Native Image

As of GraalVM version 20.2, you can build a “mostly static” native image which statically links everything except `libc`.
Statically linking all your libraries except `glibc` ensures your application has all the libraries it needs to run on any Linux `glibc`-based distribution.

To build a mostly-static native image native image, use this command:
```shell
native-image -H:+StaticExecutableWithDynamicLibC [other arguments] Class
```

> Note: This currently only works for `glibc`.

## Frequently Asked Questions

### What is the recommended base Docker image for deploying a static or mostly static native image?

A fully static native image gives you the most flexibility to choose a base image - it can run on anything including a `FROM scratch` image.
A mostly static native image requires a container image that provides `glibc`, but has no additional requirements.
In both cases, choosing the base image mostly depends on what your particular native image needs without having to worry about run-time library dependencies.
