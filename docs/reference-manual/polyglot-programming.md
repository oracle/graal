---
layout: docs
toc_group: reference-manual
link_title: Polyglot Programming
permalink: /reference-manual/polyglot-programming/
---

# Polyglot Programming

* [Running Polyglot Applications](#running-polyglot-applications)
* [Polyglot Launcher](#polyglot-launcher)
* [Polyglot Options](#polyglot-options)
* [Passing Options for Language Launchers](#passing-options-for-language-launchers)
* [Passing Options Programmatically](#passing-options-programmatically)
* [Passing Options Using JVM Arguments](#passing-options-using-jvm-arguments)

GraalVM allows users to write polyglot applications that seamlessly pass values from one language to another by means of the [Truffle language implementation framework](../../truffle/docs/README.md) (henceforth "Truffle").

Truffle is a Java library for building programming languages implementations as interpreters for self-modifying Abstract Syntax Trees.
When writing a language interpreter with Truffle, it will automatically use the Graal compiler as a just-in-time compiler for the language.
By having access to this framework, a Ruby application, for example, can run on the same JVM as a Java application.
Also, a host JVM-based language and a guest language can directly interoperate with each other and pass data back and forth in the same memory space.

In order to provide foreign polyglot values in the languages implemented with Truffle, the so-called _polyglot interoperability protocol_ has been developed.
This interoperability protocol consists of a set of standardized messages that every language implements and uses for foreign polyglot values.
The protocol allows GraalVM to support interoperability between any combination of languages without requiring them to know of each other.
For more details, proceed to the [High-Performance Cross-Language Interoperability in a Multi-Language Runtime](http://dx.doi.org/10.1145/2816707.2816714) paper.

Throughout this section you learn how to combine multiple languages using GraalVM Polyglot APIs.

## Running Polyglot Applications

The following examples are designed to get you started with a basic polyglot application.
Select a section for your *Start Language* and then select a tab for the *Target Language*.

Ensure you set up GraalVM before you begin.

The below examples work:
* on a JVM, by passing `--polyglot --jvm`.
* on native launchers with `--polyglot` (e.g., `js --polyglot`).
  It might be required to [rebuild images](graalvm-updater.md#rebuild-images) to access languages installed with `gu`.
* with native executables (e.g., `native-image --language:js`).

For native launchers and native executables using Java as a Target Language and accessing classes other than Java arrays, it is required to recompile the image and provide a [reflection configuration file](native-image/Reflection.md).

Note: To start an application with LLVM as a Target Language, make sure to precompile the _polyglot.c_ file provided below.

### Start from JavaScript / Node.js

Create the file `polyglot.js`:

{%
include snippet-tabs title="Target Language"
tab1type="javascript" tab1id="js_to_R" tab1name="R" tab1path="polyglot_ref/js_to_R.js"
tab2type="javascript" tab2id="js_to_ruby" tab2name="Ruby" tab2path="polyglot_ref/js_to_ruby.js"
tab3type="javascript" tab3id="js_to_python" tab3name="Python" tab3path="polyglot_ref/js_to_python.js"
tab4type="javascript" tab4id="js_to_java" tab4name="Java" tab4path="polyglot_ref/js_to_java.js"
tab5type="javascript" tab5id="js_to_llvm" tab5name="LLVM" tab5path="polyglot_ref/js_to_llvm.js"
%}


Run:

```shell
js --polyglot --jvm polyglot.js
42
node --polyglot --jvm polyglot.js
42
```

### Start Language R

Create the file `polyglot.R`:

{%
include snippet-tabs title="Target Language"
tab1type="r" tab1id="R_to_js" tab1name="JS" tab1path="polyglot_ref/R_to_js.R"
tab2type="r" tab2id="R_to_ruby" tab2name="Ruby" tab2path="polyglot_ref/R_to_ruby.R"
tab3type="r" tab3id="R_to_python" tab3name="Python" tab3path="polyglot_ref/R_to_python.R"
tab4type="r" tab4id="R_to_java" tab4name="Java" tab4path="polyglot_ref/R_to_java.R"
tab5type="r" tab5id="R_to_llvm" tab5name="LLVM" tab5path="polyglot_ref/R_to_llvm.R"
%}

Run:

```shell
Rscript --polyglot --jvm polyglot.R
[1] 42
```

### Start Language Ruby

Create the file `polyglot.rb`:

{%
include snippet-tabs title="Target Language"
tab1type="ruby" tab1id="ruby_to_js" tab1name="JS" tab1path="polyglot_ref/ruby_to_js.rb"
tab2type="ruby" tab2id="ruby_to_R" tab2name="R" tab2path="polyglot_ref/ruby_to_R.rb"
tab3type="ruby" tab3id="ruby_to_python" tab3name="Python" tab3path="polyglot_ref/ruby_to_python.rb"
tab4type="ruby" tab4id="ruby_to_java" tab4name="Java" tab4path="polyglot_ref/ruby_to_java.rb"
tab5type="ruby" tab5id="ruby_to_llvm" tab5name="LLVM" tab5path="polyglot_ref/ruby_to_llvm.rb"
%}

Run:

```shell
ruby --polyglot --jvm polyglot.rb
42
```

### Start Language Python

Create the file `polyglot.py`:

{%
include snippet-tabs title="Target Language"
tab1type="python" tab1id="python_to_js" tab1name="JS" tab1path="polyglot_ref/python_to_js.py"
tab2type="python" tab2id="python_to_R" tab2name="R" tab2path="polyglot_ref/python_to_R.py"
tab3type="python" tab3id="python_to_ruby" tab3name="Ruby" tab3path="polyglot_ref/python_to_ruby.py"
tab4type="python" tab4id="python_to_java" tab4name="Java" tab4path="polyglot_ref/python_to_java.py"
tab5type="python" tab5id="python_to_llvm" tab5name="LLVM" tab5path="polyglot_ref/python_to_llvm.py"
%}

Run:

```shell
graalpython --polyglot --jvm polyglot.py
42
```

### Start Language Java

Create the file `Polyglot.java`:

{%
include snippet-tabs title="Target Language"
tab1type="java" tab1id="java_to_js" tab1name="JS" tab1path="polyglot_ref/java_to_js.java"
tab2type="java" tab2id="java_to_R" tab2name="R" tab2path="polyglot_ref/java_to_R.java"
tab3type="java" tab3id="java_to_ruby" tab3name="Ruby" tab3path="polyglot_ref/java_to_ruby.java"
tab4type="java" tab4id="java_to_python" tab4name="Python" tab4path="polyglot_ref/java_to_python.java"
tab5type="java" tab5id="java_to_llvm" tab5name="LLVM" tab5path="polyglot_ref/java_to_llvm.java"
%}

Run:

```shell
javac Polyglot.java
java Polyglot
42
```

### Start Language C

Create the file `polyglot.c`:

{%
include snippet-tabs title="Target Language"
tab1type="c" tab1id="c_to_js" tab1name="JS" tab1path="polyglot_ref/c_to_js.c"
tab2type="c" tab2id="c_to_R" tab2name="R" tab2path="polyglot_ref/c_to_R.c"
tab3type="c" tab3id="c_to_ruby" tab3name="Ruby" tab3path="polyglot_ref/c_to_ruby.c"
tab4type="c" tab4id="c_to_python" tab4name="Python" tab4path="polyglot_ref/c_to_python.c"
tab5type="c" tab5id="c_to_java" tab5name="Java" tab5path="polyglot_ref/c_to_java.c"
%}

The example C code has to be compiled to LLVM bitcode using the LLVM frontend such as `clang`.
A user can use `clang` shipped with GraalVM by installing a pre-built LLVM toolchain support:

```shell
gu install llvm-toolchain
export LLVM_TOOLCHAIN=$(lli --print-toolchain-path)
```
Run:

```shell
$LLVM_TOOLCHAIN/clang polyglot.c -lgraalvm-llvm -o polyglot
lli --polyglot polyglot
42
```

## Polyglot Launcher

With polyglot applications it is often impossible to decide what the primary language of an application is.
Therefore, an experimental new launcher, called `polyglot`, has been added to GraalVM.
For the moment, this launcher runs code for JavaScript, Ruby, and R without requiring the selection of a primary language.
The polyglot launcher does not require the `--polyglot` option; it is enabled by default.

This is how you can run a polyglot application by using the examples from above:

```shell
polyglot --jvm polyglot.js polyglot.R polyglot.rb
```

We have also included a basic experimental shell for multiple languages called the _Polyglot Shell_.
It is useful to quickly test the interactivity of languages implemented with the [Truffle framework](../../truffle/docs/README.md).
This is how you can start it:

```shell
polyglot --jvm --shell
```

If you have installed all optional languages packs to the core GraalVM installation, then the Polyglot Shell will look like:
```shell
GraalVM MultiLanguage Shell 22.0.0
Copyright (c) 2013-2021, Oracle and/or its affiliates
  Java version 22.0.0
  JavaScript version 22.0.0
  Python version 3.8.5
  R version 4.0.3
  Ruby version 3.0.2
Usage:
  Use Ctrl+n to switch language and Ctrl+d to exit.
  Enter -usage to get a list of available commands.
js>
```

> Note: The `polyglot` launcher and the _Polyglot Shell_ are experimental features in GraalVM.

## Polyglot Options

You can configure a language engine for better throughput or startup.

* `--engine.Mode=default` configures the execution mode of the engine. The execution mode automatically tunes the polyglot engine towards latency or throughput.
    * `throughput` collects the maximum amount of profiling information and compiles using the
    maximum number of optimizations. This mode results in slower application startup
    but better throughput. This mode uses the compiler configuration `community` or
    `enterprise` if not specified otherwise.
    * `default` uses a balanced engine configuration. This mode uses the compiler configuration `community` or `enterprise` if not specified otherwise.
    * `latency` collects only minimal profiling information and compiles as fast as possible
    with less optimal-generated code. This mode results in faster application
    startup but less optimal throughput. This mode uses the compiler configuration
    `economy` if not specified otherwise.

## Passing Options for Language Launchers

Every language launcher has been extended with a set of so called _polyglot options_.
Polyglot options allow users of any language launcher to access the options of other languages supported by GraalVM (implemented with the Truffle language implementation framework).
The format is: `--<languageID>.<property>=<value>`.
For example, the `R` launcher also supports the `--js.atomics=true` JavaScript option.

Allowed values for the `languageID` are:
- `js`: options for JavaScript
- `python`: options for Python
- `r`: options for R
- `ruby`: options for Ruby
- `llvm`: options for LLVM

Use `--help:languages` to find out which options are available.

Options for polyglot tools work in the same way with the following format: `--<toolID>.<property>=<value>`.

Allowed values for `<toolID>` are:
- `inspect`: allows debugging with Chrome DevTools
- `cpusampler`: collects data about CPU usage
- `cputracer`: captures trace information about CPU usage
- `memtracer`: captures trace information about memory usage

Use `--help:tools` to find out which options are available.

## Passing Options Programmatically

Options can also be passed programmatically using the Java polyglot API.

Create a file called `OptionsTest.java`:
```java
import org.graalvm.polyglot.*;

class OptionsTest {

    public static void main(String[] args) {
        Context polyglot = Context.newBuilder()
            .allowExperimentalOptions(true)
            .option("js.shared-array-buffer", "true")
            .build();
        // the use of shared array buffer requires the 'js.shared-array-buffer' option to be 'true'
        polyglot.eval("js", "new SharedArrayBuffer(1024)");
    }
}
```

Run:
```shell
javac OptionsTest.java
java OptionsTest
```

> Note: Tools options can be passed in the same way. Options cannot be modified after the context was created.

## Passing Options Using JVM Arguments

Every polyglot option can also be passed as a Java system property.
Each available option translates to a system property with the `polyglot.` prefix.
For example, `-Dpolyglot.js.strict=true` sets the default value for a strict interpretation for all JavaScript code that runs in the JVM.
Options that were set programmatically take precedence over Java system properties.
For languages the following format can be used: `-Dpolyglot.<languageID>.<property>=<value>` and for tools it is: `-Dpolyglot.<toolID>.<property>=<value>`.

Create a file called `SystemPropertiesTest.java`:
```java
import org.graalvm.polyglot.*;

class SystemPropertiesTest {

    public static void main(String[] args) {
        Context polyglot = Context.newBuilder()
        .allowExperimentalOptions(true)
        .build();
        // the use of shared array buffer requires the 'js.shared-array-buffer' option to be 'true'
        polyglot.eval("js", "new SharedArrayBuffer(1024)");
    }
}
```

Run:
```shell
javac SystemPropertiesTest.java
java -Dpolyglot.js.strict=true SystemPropertiesTest
```

> Note: System properties are read once when the polyglot context is created. Subsequent changes have no effect.
