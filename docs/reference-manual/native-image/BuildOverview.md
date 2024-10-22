---
layout: docs
toc_group: build-overview
link_title: Build Overview
permalink: /reference-manual/native-image/overview/Build-Overview/
redirect_from: /reference-manual/native-image/Build-Overview/
---

# Native Image Build Overview

The syntax of the `native-image` command is:

- `native-image [options] <mainclass> [imagename] [options]` to build a native binary from the main class in the current working directory. The class path may optionally be provided with the `-cp <classpath>` option where `<classpath>` is a colon-separated (on Windows, semicolon-separated) list of paths to directories and JAR files.
- `native-image [options] -jar jarfile [imagename] [options]` to build a native binary from a JAR file.
- `native-image [options] -m <module>/<mainClass> [imagename] [options]` to build a native binary from a Java module.

The options passed to `native-image` are evaluated from left to right.
For an overview of options that can be passed to `native-image`, see [here](BuildOptions.md).

## Getting Notified When the Build Process Is Done

Depending on the size of your application and the available resources of your build machine, it can take a few minutes to compile your Java application into a native executable.
If you are building your application in the background, consider using a command that notifies you when the build process is completed.
Below, example commands are listed per operating system:

#### Linux
```bash
# Ring the terminal bell
native-image -jar App.jar ... ; printf '\a'

# Use libnotify to create a desktop notification
native-image -jar App.jar ... ; notify-send "GraalVM Native Image build completed with exit code $?"

# Use Zenity to open an info dialog box with text
native-image -jar App.jar ... ; zenity --info --text="GraalVM Native Image build completed with exit code $?"
```

#### macOS
```bash
# Ring the terminal bell
native-image -jar App.jar ... ; printf '\a'

# Use Speech Synthesis
native-image -jar App.jar ... ; say "GraalVM Native Image build completed"
```

#### Windows
```bash
# Ring the terminal bell (press Ctrl+G to enter ^G)
native-image.exe -jar App.jar & echo ^G

# Open an info dialog box with text
native-image.exe -jar App.jar & msg "%username%" GraalVM Native Image build completed
```

### Further Reading

If you are new to GraalVM Native Image or have little experience using it, see the [Native Image Basics](NativeImageBasics.md) to better understand some key aspects before going further.

For more tweaks and how to properly configure the `native-image` tool, see [Build Configuration](BuildConfiguration.md#order-of-arguments-evaluation).

Native Image outputs the progress and various statistics when building a native binary. To learn more about the output, and the different build phases, see [Build Output](BuildOutput.md).

For more detailed information about the build process, its phases, and the contents of a produced native binary, see [Build Report](BuildReport.md).