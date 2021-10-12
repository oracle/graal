---
layout: docs
toc_group: native-image
link_title: Static Native Images
permalink: /reference-manual/native-image/StaticImages/
---
# Static Native Images

Static native images are statically linked binaries which can be used without any additional library dependencies, which makes them suitable for use in containers.
They are created by statically linking against [musl-libc](https://musl.libc.org/), an alternative `libc` implementation.

<!-- Note: Currently, you can build static native images on Linux AMD64 on Java 11 only. -->

## Prerequisites

- Linux AMD64 operating system
- GraalVM distribution for Java 11 with [Native Image support](README.md#install-native-image)
- A 64-bit `musl` toolchain, `make`, and `configure`
- The latest `zlib` library

## Preparation

You should get the `musl` toolchain first, and then compile and install `zlib` into the toolchain.

1. Download the `musl` toolchain from [musl.cc](musl.cc). [This one](http://musl.cc/x86_64-linux-musl-native.tgz) is recommended. Extract the toolchain to a directory of your choice. This directory will be referred as `$TOOLCHAIN_DIR`.
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

## Build a Static Native Image

First, ensure `$TOOLCHAIN_DIR/bin` is present on your `PATH` variable.
To verify this, run:

```bash
x86_64-linux-musl-gcc
```
You should get a similar output printed:
```bash
x86_64-linux-musl-gcc: fatal error: no input files
compilation terminated.
```

To build a static native image, use this command:
```shell
native-image --static --libc=musl [other arguments] Class
```

## Build a Mostly Static Native Image

As of GraalVM version 20.2, you can build a “mostly static” native image which link statically everything except `libc`.
Native images built this way are convenient to run in Docker containers, for example, based on
[distroless minimal Linux, glibc-based systems](https://github.com/GoogleContainerTools/distroless/blob/master/base/README.md).

To build a mostly-static native image native image, use this command:
```shell
native-image -H:+StaticExecutableWithDynamicLibC [other arguments] Class
```

> Note: This currently only works for `glibc`.

## Frequently Asked Questions

### What Docker image type is recommended to build a static native image?
There are different docker images types you can convert a Java application into:
* Slim
* Distroless
* Alpine
* Scratch

Whatever base image you choose is determined by what you want to run in container.
Basically, with a static native image statically linked against `muslc`, you can dockerize into anything: from scratch, Alpine, Ubuntu, Oracle Linux, etc.
With mostly static native image (distroless) linking statically everything except `libc`, you can dockerize into a distribution with the matching `libc` (currently `glibc` only), so Ubuntu, Oracle Linux,etc.
