# Mandrel

Mandrel is [a downstream distribution of the GraalVM community edition](https://developers.redhat.com/blog/2020/06/05/mandrel-a-community-distribution-of-graalvm-for-the-red-hat-build-of-quarkus/).
Mandrel's main goal is to provide a `native-image` release specifically to support [Quarkus](https://quarkus.io).
The aim is to align the `native-image` capabilities from GraalVM with OpenJDK and Red Hat Enterprise Linux libraries to improve maintainability for native Quarkus applications.
Mandrel can best be described as a distribution of a regular OpenJDK with a specially packaged GraalVM Native Image builder (`native-image`).

## How Does Mandrel Differ From Graal

Mandrel releases are built from a code base derived from the upstream GraalVM code base, with only minor changes but some significant exclusions. 
A full distribution of GraalVM is much more than `native-image`: it has polyglot support, the Truffle framework which allows for efficient implementation of interpreters, an LLVM compiler back end for native image, the libgraal JIT compiler as a replacement for Hotspot’s C2 server compiler and much more.
Mandrel is the small subset of that functionality we support for the `native-image` use-case.

Mandrel's `native-image` also doesn't include the following features:
* The experimental image-build server, i.e., the `--experimental-build-server` option.
* The LLVM backend, i.e., the `-H:CompilerBackend=llvm` option.
* The musl libc implementation, i.e., the `--libc=musl` option.
* Support for generating static native images, i.e., the `--static` option.
* Support for non JVM-based languages and polyglot, i.e., the `--language:<languageId>` option.

Mandrel is also built slightly differently to GraalVM, using the standard OpenJDK project release of jdk11u.
This means it does not profit from a few small enhancements that Oracle have added to the version of OpenJDK used to build their own GraalVM downloads.
Most of these enhancements are to the JVMCI module that allows the Graal compiler to be run inside OpenJDK.
The others are small cosmetic changes to behaviour.
These enhancements may in some cases cause minor differences in the progress of native image generation.
They should not cause the resulting images themselves to execute in a noticeably different manner.

## Communication Channels

* [Slack](https://www.graalvm.org/slack-invitation) - Join `#mandrel` channel at graalvm's slack workspace
* [graalvm-dev@oss.oracle.com](mailto:graalvm-dev@oss.oracle.com?subject=[MANDREL]) mailing list - Subscribe [here](https://oss.oracle.com/mailman/listinfo/graalvm-dev)
* [GitHub issues](https://github.com/graalvm/mandrel/issues) for bug reports, questions, or requests for enhancements.

Please report security vulnerabilities according to the [Reporting Vulnerabilities guide](https://www.oracle.com/corporate/security-practices/assurance/vulnerability/reporting.html).

## Getting Started

Mandrel distributions can be downloaded from [the repository's releases](https://github.com/graalvm/mandrel/releases)
and container images are available at [quay.io](https://quay.io/repository/quarkus/ubi-quarkus-mandrel?tag=latest&tab=tags).

### Prerequisites

Mandrel's `native-image` depends on the following packages:
* freetype-devel
* gcc
* glibc-devel
* libstdc++-static
* zlib-devel

On Fedora/CentOS/RHEL they can be installed with:
```bash
dnf install glibc-devel zlib-devel gcc freetype-devel libstdc++-static
```

**Note**: The package might be called `glibc-static` or `libstdc++-devel` instead of `libstdc++-static` depending on your system.
If the system is missing stdc++, `gcc-c++` package is needed too.

On Ubuntu-like systems with:
```bash
apt install g++ zlib1g-dev libfreetype6-dev
```

## Building Mandrel From Source

For building Mandrel from source please see [mandrel-packaging](https://github.com/graalvm/mandrel-packaging)
and consult [Repository Structure in CONTRIBUTING.md](CONTRIBUTING.md#repository-structure) regarding which branch of Mandrel to use.

