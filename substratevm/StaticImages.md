# Static Native Images

Static native images are statically linked binaries which can be used without any additional library dependencies.
This makes them suitable for use in a Docker container.

## Prerequisites
 - Right now, this only works on Linux AMD64 on Java 11.
 - You will need `gcc`, `make`, and `configure`.
 - Create a directory that will hold the libraries you build. You will refer to this directory as `${RESULT_DIR}`.
 - Download the latest `musl` release [here](https://musl.libc.org/). This document will use `musl-1.2.0`.
 - Download the latest `zlib` release [here](https://zlib.net/). This document will use `zlib-1.2.11`.

 ## Build a Static Native Image

If you have `musl-gcc` on the path, you can build a native image statically linked against `muslc` with the following options: `--static --libc=musl`.
To verify that `musl-gcc` is on the path, run `musl-gcc -v`.

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

#### Building musl
 - Extract the musl release `tarball` and `cd` into the extracted directory.
 - Run `./configure --disable-shared --prefix=${RESULT_DIR}`.
 - Run `make`.
 - Run `make install`.
Other than building `musl` libraries, the build also creates a `gcc` wrapper called `musl-gcc` in the `${RESULT_DIR}/bin` directory.
You should now put this wrapper on your `PATH` by running `export PATH=$PATH:${RESULT_DIR}/bin`.

#### Building zlib
 - Extract the zlib release `tarball` and `cd` into the extracted directory.
 - You need to compile zlib and link it against musl so set `CC` to `musl-gcc`: `export CC=musl-gcc`.
 - Run `./configure --static --prefix=${RESULT_DIR}`.
 - Run `make`.
 - Run `make install`.

#### Getting libstdc++
`libstdc++` is obtained by building gcc. There are multiple approaches to obtaining it:
 1. Build gcc with `musl-gcc`.
 2. Use `libstdc++.a` from your distribution. If you choose this path, check the [FAQs](https://www.musl-libc.org/faq.html) page, "How do I use the musl-gcc wrapper?":
  >  The existing libstdc++ is actually compatible with musl in most cases and could be used by copying it into the musl library path, but the C++ header files are usually not compatible.
 Since do not need C++ header files, this approach should work. If you run into issues, make sure they are not caused by your ditribution's `libstdc++.a`.
 3. Take `libstdc++.a` from Alpine.
In each case, `libstdc++.a` must be placed in `${RESULT_DIR}/lib`.
