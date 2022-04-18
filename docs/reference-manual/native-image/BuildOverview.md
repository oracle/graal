---
layout: docs
toc_group: native-image
link_title: Build Overview
permalink: /reference-manual/native-image/overview/Build-Overview/
redirect_from: /$version/reference-manual/native-image/Build-Overview/
---
# Native Image Build Overview

The syntax of the `native-image` command is:

- `native-image [options] <mainclass> [imagename] [options]` to build an image from `<mainclass>` class in the current working directory. Classpath may optionally be provided with the `-cp <classpath>` option where `<classpath>` is a colon-separated (on Windows, semicolon-separated) list of paths to directories and jars.
- `native-image [options] -jar jarfile [imagename] [options]` to build an image from a JAR file.
- `native-image [options] -m <module>/<mainClass> [imagename] [options]` to build an image from a Java module.

The options passed to `native-image` are evaluated left-to-right.
For more information, see [Native Image Build Configuration](BuildConfiguration.md#order-of-arguments-evaluation).

The options fall into three categories:
 - [Image generation options](NativeImageOptions.md#image-generation-options) - for the full list, run `native-image --help`
 - [Macro options](NativeImageOptions.md#macro-options)
 - [Non-standard options](NativeImageOptions.md#non-standard-options) - subject to change through a deprecation cycle, run `native-image --help-extra` for the full list.

For non-trivial applications reachability metadata should be provided to the image builder.
To learn more about metadata, see [Reachability Metadata](ReachabilityMetadata.md).
To automatically collect metadata for your application, see [Automatic Collection of Metadata](AutomaticMetadataCollection.md).
For further image build tweaks, see [Build Configuration](BuildConfiguration.md).

If your project uses Gradle or Maven, you can leverage the [Native Build Tools](https://github.com/graalvm/native-build-tools) for a smoother Native Image experience.

Native Image can interop with native languages through a custom API.
Using this API, you can specify custom native entry points into your Java application and build it into a shared library.
To learn more, see [Building a Shared Library](SharedLibrary.md)

Native Image can also leverage Truffle to allow code in supported Truffle languages (e.g., Javascript and Python) to be executed in the image. (TODO link?)
To inform `native-image` of a guest language used by an application, specify `--language:<languageId>` for each guest language (e.g., `--language:js`).

Native Image will output the progress and various statistics during the image build. To learn more about the output, and the different image build phases, see [Build Output](BuildOutput.md)

