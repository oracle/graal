# Static native-images

## What they are
Static native-images are statically linked binaries which can be used without any additional library dependencies.
This makes them suitable for use in a `FROM scratch` Docker container.

## Prerequisites
 - Right now, this only works on Linux AMD64 on Java 11
 - You will need `gcc`, `make` and `configure`.
 - Create a directory that will hold the libraries we build. We will refer to this directory as `${RESULT_DIR}`
 - Download the latest `musl` release from https://musl.libc.org/. This document will use `musl-1.2.0`  
 - Download the latest `zlib` release from https://zlib.net/. This document will use `zlib-1.2.11` 

### Building musl
 - Extract the musl release `tarball` and `cd` into the extracted directory
 - Run `./configure --disable-shared --prefix=${RESULT_DIR}`
 - Run `make`
 - Run `make install`
Other than building `musl` libraries, the build also creates a `gcc` wrapper called `musl-gcc` in the `${RESULT_DIR}/bin` directory.
You should now put this wrapper on your `PATH` by running `export PATH=$PATH:${RESULT_DIR}/bin`

### Building zlib 
 - Extract the zlib release `tarball` and `cd` into the extracted directory
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

### Creating a static native-image
 - Verify that `musl-gcc` is on the path by running `musl-gcc -v`. If you get an error saying that `musl-gcc` is not found, something went wrong.
 - Run `native-image --static --libc=musl <...other native-image arguments...>` to create a static native-image.
 