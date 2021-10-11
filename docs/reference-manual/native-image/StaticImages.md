---
layout: docs
toc_group: native-image
link_title: Static Native Images
permalink: /reference-manual/native-image/StaticImages/
---
# Static Native Images

Static native images are statically linked binaries which can be used without any additional library dependencies.
This makes them suitable for use in a Docker container. Static native images are created by statically linking against [musl-libc](https://www.musl-libc.org/), an alternative libc implementation.

## Prerequisites
 - Right now, this only works on Linux AMD64 on Java 11.
 - You will need a 64-bit `musl` toolchain, `make`, and `configure`.
 - You can download the musl toolchain from [musl.cc](musl.cc), specifically [this one](http://musl.cc/x86_64-linux-musl-native.tgz)
 - Download the latest `zlib` release sources [here](https://zlib.net/). This document will use `zlib-1.2.11`.

## Preparing The Toolchain And Compiling Zlib
 - Extract the toolchain to a directory of your choice. We will refer to this directory as `$TOOLCHAIN_DIR`.
 - Extract the zlib sources.
 - Set the following environment variable: `CC=$TOOLCHAIN_DIR/bin/gcc`.
 - Move into the zlib directory, and then run the following to compile and install zlib into the toolchain:
```
./configure --prefix=$TOOLCHAIN_DIR --static
make
make install
```
## Build a Static Native Image

First, ensure `$TOOLCHAIN_DIR/bin` is present on your `PATH` variable. To verify this, try running:
```
$ x86_64-linux-musl-gcc
x86_64-linux-musl-gcc: fatal error: no input files
compilation terminated.
```

To build a static native image, use:
```shell
native-image --static --libc=musl [other arguments] Class
```

## Build a Mostly Static Native Image

As of GraalVM version 20.2, you can build a “mostly static” native image which link statically everything except `libc`. Native images built this way are convenient to run in Docker containers, for example, based on
[distroless minimal Linux, glibc-based systems](https://github.com/GoogleContainerTools/distroless/blob/master/base/README.md).

To build a mostly-static native image native image, use:
```shell
native-image -H:+StaticExecutableWithDynamicLibC [other arguments] Class
```

Note that this currently only works for `glibc`.
