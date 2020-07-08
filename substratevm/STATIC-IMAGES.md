# Static native-images

**Note: Static linking in the native-image context only makes sense on Linux**

## TLDR
**Make sure to use JDK 11.**
1. Build or obtain musl and add `musl-gcc` to your `PATH`
2. Build or obtain zlib and put it in the same place as musl libraries.
3. Build or obtain libstdc++ and put it in the same place as musl libraries.
4. Use `--static --libc=musl` to build your static native-image.

## What they are
Static native-images are statically linked binaries which can be used without any additional library dependencies. This makes them suitable for use in a `FROM scratch` Docker container.

## How to make a static native-image from your application
You can create a static native-image by passing the `--static` option to `native-image`. However, by default, on all of our currently supported Linux distributions this will link against `glibc`. Due to a number of issues with static linking against `glibc` (certain features that prevent truly static linking like NSS, segfaults), this is strongly discouraged.

To overcome this problem, `native-image` supports static linking against `musl`. `musl` is an alternative libc implementation (https://musl.libc.org/) built from ground-up to support static liking.

In order to create a static native-image linked against musl, pass the following arguments to `native-image`:
`--static --libc=musl`

When `native-image` wants to statically link against `musl`, it will use `musl-gcc` as the target compiler. It will also use the JDK static libraries that were built using `musl`. `musl-gcc` is a small wrapper provided by `musl` that rewrites the `include` and `library` search paths of your host `gcc`.

## How to set up musl and other required libraries
Not all distributions provide `musl` in their standard installable packages. Others like Ubuntu don't provide the other needed libraries in those packages. As such, the recommended approach is to build the dependencies yourself on your system. This document will attempt to guide you through that process. We will try and keep this up-to-date, but if you find that something here no longer works, please let us know.

You will need `gcc`, `make` and `configure`.

Before we start, create a directory that will hold the libraries we build. We will refer to this directory as `${RESULT_DIR}`

### Building musl
 - Hop on https://musl.libc.org/ and fetch the latest release. This document will use `musl-1.2.0`
 - Extract the `tarball` and `cd` into the extracted directory
 - Run `./configure --disable-shared --prefix=${RESULT_DIR}`
 - Run `make`
 - Run `make install`
You can now put `musl-gcc` on `PATH` by running `export PATH=$PATH:${RESULT_DIR}/bin`

### Building zlib
 - Hop on https://zlib.net/ and fetch the latest release. This document will use `zlib-1.2.11`
 - Extract the `tarball` and `cd` into the extracted directory
 - We want to compile zlib and link it against musl so we set `CC` to `musl-gcc`: `export CC=musl-gcc`
 - Run `./configure --static --prefix=${RESULT_DIR}`
 - Run `make`
 - Run `make install`

### Getting libstdc++
`libstdc++` is obtained by building gcc. There are multiple approaches to obtaining it:
 1. Build gcc with `musl-gcc`
 2. Use `libstdc++.a` from your distribution. If you choose this path, please see https://www.musl-libc.org/faq.html, "How do I use the musl-gcc wrapper?":
  >  The existing libstdc++ is actually compatible with musl in most cases and could be used by copying it into the musl library path, but the C++ header files are usually not compatible.
 As we do not need C++ header files, this approach should work but if you run into issues, make sure they are not caused by your ditribution's `libstdc++.a`. 
 3. Take `libstdc++.a` from Alpine
In each case, `libstdc++.a` must be placed in `${RESULT_DIR}/lib`

## Limitations
- **Currently, only JDK11 is supported.**
