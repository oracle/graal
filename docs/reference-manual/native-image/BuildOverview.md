---
layout: ni-docs
toc_group: build-overview
link_title: Build Overview
permalink: /reference-manual/native-image/overview/Build-Overview/
redirect_from: /$version/reference-manual/native-image/Build-Overview/
---

# Native Image Build Overview

The syntax of the `native-image` command is:

- `native-image [options] <mainclass> [imagename] [options]` to build a native binary from `<mainclass>` class in the current working directory. The classpath may optionally be provided with the `-cp <classpath>` option where `<classpath>` is a colon-separated (on Windows, semicolon-separated) list of paths to directories and jars.
- `native-image [options] -jar jarfile [imagename] [options]` to build native binary from a JAR file.
- `native-image [options] -m <module>/<mainClass> [imagename] [options]` to build a native binary from a Java module.

The options passed to `native-image` are evaluated from left to right.

The options fall into three categories:
 - [Image generation options](BuildOptions.md#native-image-build-options) - for the full list, run `native-image --help`
 - [Macro options](BuildOptions.md#macro-options)
 - [Non-standard options](BuildOptions.md#non-standard-options) - subject to change through a deprecation cycle, run `native-image --help-extra` for the full list.

Find a complete list of options for the `native-image` tool [here](BuildOptions.md).

There are some expert level options that a Native Image developer may find useful or needed, for example, the option to dump graphs of the `native-image` builder or enable assertions at image run time. This information can be found in [Native Image Hosted and Runtime Options](HostedvsRuntimeOptions.md).

### Further Reading

If you are new to GraalVM Native Image or have little experience using it, see the [Native Image Basics](NativeImageBasics.md) to better understand some key aspects before going further.

For more tweaks and how to properly configure the `native-image` tool, see [Build Configuration](BuildConfiguration.md#order-of-arguments-evaluation).

Native Image will output the progress and various statistics when building the native binary. To learn more about the output, and the different build phases, see [Build Output](BuildOutput.md).