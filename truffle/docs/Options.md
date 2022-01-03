---
layout: docs
toc_group: truffle
link_title: Options
permalink: /graalvm-as-a-platform/language-implementation-framework/Options/
---
# Truffle Options

You can list options from the command line with any language launcher:

```shell
language-launcher --help:expert
```

Or, for options only relevant for Truffle language implementers:

```shell
language-launcher --help:internal
```

In addition, the Graal Compiler options can be listed with:

```shell
language-launcher --jvm --vm.XX:+JVMCIPrintProperties
```
See [graalvm_ce_jdk8_options](https://chriswhocodes.com/graalvm_ce_jdk8_options.html) for a list of Graal Compiler options.

## Default Language Launcher Options

- `--polyglot` : Run with all other guest languages accessible.
- `--native` : Run using the native launcher with limited Java access (default).
- `--jvm` : Run on the Java Virtual Machine with Java access.
- `--vm.[option]` : Pass options to the host VM. To see available options, use '--help:vm'.
- `--log.file=<String>` : Redirect guest languages logging into a given file.
- `--log.[logger].level=<String>` : Set language log level to OFF, SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST or ALL.
- `--help` : Print this help message.
- `--help:vm` : Print options for the host VM.
- `--version:graalvm` : Print GraalVM version information and exit.
- `--show-version:graalvm` : Print GraalVM version information and continue execution.
- `--help:languages` : Print options for all installed languages.
- `--help:tools` : Print options for all installed tools.
- `--help:expert` : Print additional options for experts.
- `--help:internal` : Print internal options for debugging language implementations and tools.

## Expert Engine Options

These are advanced options for controlling the engine.
They are useful to users and language and tool implementers.

{% include_relative expertEngineOptions.txt %}

## Internal Engine Options

These are internal options for debugging language implementations and tools.


{% include_relative internalEngineOptions.txt %}
