---
layout: docs
toc_group: build-overview
link_title: Build Overview
permalink: /reference-manual/native-image/overview/Build-Overview/
redirect_from: /reference-manual/native-image/Build-Overview/
---

# Native Image Build Overview

The syntax of the `native-image` command is:

- `native-image [options] <mainclass> [imagename] [options]` to build a native binary from the main class in the current working directory. The classpath may optionally be provided with the `-cp <classpath>` option where `<classpath>` is a colon-separated (on Windows, semicolon-separated) list of paths to directories and JAR files.
- `native-image [options] -jar jarfile [imagename] [options]` to build a native binary from a JAR file.
- `native-image [options] -m <module>/<mainClass> [imagename] [options]` to build a native binary from a Java module.

The options passed to `native-image` are evaluated from left to right.
For an overview of options that can be passed to `native-image`, see [here](BuildOptions.md).

### Further Reading

If you are new to GraalVM Native Image or have little experience using it, see the [Native Image Basics](NativeImageBasics.md) to better understand some key aspects before going further.

For more tweaks and how to properly configure the `native-image` tool, see [Build Configuration](BuildConfiguration.md#order-of-arguments-evaluation).

Native Image outputs the progress and various statistics when building a native binary. To learn more about the output, and the different build phases, see [Build Output](BuildOutput.md).