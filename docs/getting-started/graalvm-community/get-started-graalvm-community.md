---
layout: docs
toc_group: getting-started
link_title: Getting Started
permalink: /docs/getting-started/
---

# Get Started with GraalVM

Get started with GraalVM -- is a high-performance JDK designed to accelerate Java application performance while consuming fewer resources.
GraalVM offers two ways to run Java applications: on the HotSpot JVM with Graal just-in-time (JIT) compiler or as an ahead-of-time (AOT) compiled native executable.
Besides Java, it provides runtimes for JavaScript, Ruby, Python, and a number of other popular languages.
GraalVM's polyglot capabilities make it possible to mix programming languages in a single application while eliminating any foreign language call costs.

Here you will find information about installing GraalVM Community Edition, running basic applications with it, and adding support for accompanying features.
Further, you will learn about the polyglot capabilities of GraalVM and see how to build platform-specific native executables of Java applications.

If you are new to GraalVM, we recommend starting with [Introduction to GraalVM](../../introduction.md), where you will find information about GraalVM's architecture, distributions available, supported platforms, core and additional features, and much more.

If you have GraalVM already installed and have experience using it, you can skip this getting started guide and proceed to the in-depth [Reference Manuals](../../reference-manual/reference-manuals.md).

## Install GraalVM

Getting GraalVM installed and ready-to-go should take a few minutes.
Choose the operating system and proceed to the installation steps:

* [Linux](linux.md)
* [macOS](macos.md)
* [Windows](windows.md)
* [Docker Container](container-images/graalvm-ce-container-images.md)

## Start Running Applications

The core distribution of GraalVM includes the JVM and the GraalVM compiler.
Having downloaded and installed GraalVM, you can already run any Java application unmodified.

Other languages support can be installed on request, using **gu** -- the GraalVM Updater tool to install additional language runtimes and utilities.
Further below you will find information on how to add other optionally available GraalVM runtimes including JavaScript, Node.js, LLVM, Ruby, R, Python, and WebAssembly.

## Run Java

The `java` launcher runs the JVM with the GraalVM default compiler - Graal.
Check the Java version upon the installation:
```shell
$GRAALVM_HOME/bin/java -version
```

Take a look at this typical `HelloWorld` class:
```java
public class HelloWorld {
  public static void main(String[] args) {
    System.out.println("Hello, World!");
  }
}
```

Run the following commands to compile this class to bytecode and then execute it:
```shell
javac HelloWorld.java
java HelloWorld
Hello World!
```

You can find a collection of larger Java examples on the [Examples Applications](../../examples/examples.md) page.
For more information on the GraalVM
compiler, go to [Compiler](../../reference-manual/java/compiler.md).
For more extensive documentation on running Java, proceed to [JVM Languages](../../reference-manual/java/README.md).

## Run JavaScript and Node.js

GraalVM supports running JavaScript applications. 
The JavaScript runtime is optionally available and can be installed with this command:
```shell
gu install js
```

It installs the `js` launcher in the `$GRAALVM_HOME/bin` directory.
With the JavaScript runtime installed, you can execute plain JavaScript code, both in REPL mode and by executing script files directly:
```shell
$GRAALVM_HOME/bin/js
> 1 + 2
3
```

GraalVM also supports running Node.js applications.
The Node.js support is not installed by default, but can be easily added with this command:
```shell
gu install nodejs
```

Both `node` and  `npm` launchers then become available in the `$GRAALVM_HOME/bin` directory.

```shell
$GRAALVM_HOME/bin/node -v
$GRAALVM_HOME/bin/npm show <package name> version
```

More than 100,000 npm packages are regularly tested and are compatible with GraalVM, including modules like express, react, async, request, browserify, grunt, mocha, and underscore.
To install a Node.js module, use the `npm` executable from `$GRAALVM_HOME/bin`, which is installed together with `node`.
The `npm` command is equivalent to the default Node.js command and supports all Node.js APIs.

Install the modules `colors`, `ansispan`, and `express` using `npm install`.
After the modules are installed, you can use them from your application.
```shell
$GRAALVM_HOME/bin/npm install colors ansispan express
```

Use the following code snippet and save it as the `app.js` file in the same directory where you installed the Node.js modules:
```js
const http = require("http");
const span = require("ansispan");
require("colors");

http.createServer(function (request, response) {
    response.writeHead(200, {"Content-Type": "text/html"});
    response.end(span("Hello Graal.js!".green));
}).listen(8000, function() { console.log("Graal.js server running at http://127.0.0.1:8000/".red); });

setTimeout(function() { console.log("DONE!"); process.exit(); }, 2000);
```

Run _app.js_ on GraalVM Enterprise using the `node` command:
```shell
$GRAALVM_HOME/bin/node app.js
```

For more detailed documentation and information on compatibility with Node.js, proceed to [JavaScript and Node.js](../../reference-manual/js/README.md).

## Run LLVM Languages

The GraalVM LLVM runtime can execute C/C++, Rust, and other programming languages that can be compiled to LLVM bitcode.

The LLVM runtime is optionally available and can be installed with this command:
```shell
$GRAALVM_HOME/bin/gu install llvm
```

It installs the GraalVM implementation of `lli` in the `$GRAALVM_HOME/bin` directory.
Check the version upon the installation:

```shell
$GRAALVM_HOME/bin/lli --version
```

With the LLVM runtime installed, you can execute programs in LLVM bitcode format on GraalVM.
To compile a native program to LLVM bitcode, you use some LLVM frontend, for example `clang`.

Besides the LLVM runtime, GraalVM also provides the LLVM frontend (toolchain) that you can set up as follows:

```shell
gu install llvm-toolchain
export LLVM_TOOLCHAIN=$(lli --print-toolchain-path)
```

Then the C/C++ code can be compiled to LLVM bitcode using `clang` shipped with GraalVM.
For example, put this C code into a file named `hello.c`:
```c
#include <stdio.h>

int main() {
    printf("Hello from GraalVM!\n");
    return 0;
}
```

Compile `hello.c` to an executable `hello` with embedded LLVM bitcode and run it:
```shell
$LLVM_TOOLCHAIN/clang hello.c -o hello
lli hello
```

For in-depth documentation about the GraalVM LLVM runtime, go to [LLVM Languages](../../reference-manual/llvm/README.md).

## Run Python

With GraalVM you can run Python applications in the Python 3 runtime environment.
The support is not available by default, but you can quickly add it to GraalVM with this command:
```shell
gu install python
```

It installs the `graalpy` launcher. Check the version, and you can already run Python programs:
```shell
$GRAALVM_HOME/bin/graalpy --version
```

```shell
$GRAALVM_HOME/bin/graalpy
...
>>> 1 + 2
3
>>> exit()
```

More examples and additional information on Python support in GraalVM can be found in the [Python reference manual](../../reference-manual/python/README.md).

## Run Ruby

GraalVM provides a high-performance Ruby runtime environment including the `gem` command that allows you to interact with RubyGems, Ruby Bundler, and much more.
The Ruby runtime is not available by default in GraalVM, but can be easily added with this command:
```shell
gu install ruby
```

Once it is installed, Ruby launchers like `ruby`, `gem`, `irb`, `rake`, `rdoc`, and `ri` become available to run Ruby programs:
```shell
$GRAALVM_HOME/bin/ruby [options] program.rb
```

GraalVM runtime for Ruby uses the [same options as the standard implementation of Ruby](../../reference-manual/ruby/options.md), with some additions.
For example:
```shell
gem install chunky_png
$GRAALVM_HOME/bin/ruby -r chunky_png -e "puts ChunkyPNG::Color.to_hex(ChunkyPNG::Color('mintcream @ 0.5'))"
#f5fffa80
```

More examples and in-depth documentation can be found in the [Ruby reference manual](../../reference-manual/ruby/README.md).

## Run R

GraalVM provides a GNU-compatible environment to run R programs directly or in the REPL mode.
Although the R language support is not available by default, you can add it to GraalVM with this command:
```shell
gu install R
```

When the language is installed, you can execute R scripts and use the R REPL:
```shell
$GRAALVM_HOME/bin/R
...

> 1 + 1
[1] 2
```

More examples and in-depth documentation can be found in the [R reference manual](../../reference-manual/r/README.md).

## Run WebAssembly

With GraalVM you can run programs compiled to WebAssembly.
The support is not available by default, but you can add it to GraalVM with this command:
```shell
gu install wasm
```
Then the `wasm` launcher, that can run compiled WebAssembly binary code, becomes available.

For example, put the following C program in a file named _floyd.c_:
```c
#include <stdio.h>

int main() {
  int number = 1;
  int rows = 10;
  for (int i = 1; i <= rows; i++) {
    for (int j = 1; j <= i; j++) {
      printf("%d ", number);
      ++number;
    }
    printf(".\n");
  }
  return 0;
}
```

Compile it using the most recent [Emscripten compiler frontend](https://emscripten.org/docs/tools_reference/emcc.html) version. It should produce a standalone _floyd.wasm_ file in the current working directory:
```shell
emcc -o floyd.wasm floyd.c
```

Then you can run the compiled WebAssembly binary on GraalVM as follows:
```shell
$GRAALVM_HOME/bin/wasm --Builtins=wasi_snapshot_preview1 floyd.wasm
```

More details can be found in the [WebAssembly reference manual](../../reference-manual/wasm/README.md).

## Combine Languages

GraalVM allows you to call one programming language into another and exchange data between them.
To enable interoperability, GraalVM provides the `--polyglot` flag.

For example, running `js --jvm --polyglot example.js` executes `example.js` in a polyglot context.
If the program calls any code in other supported languages, GraalVM executes that code in the same runtime as the `example.js` application.
For more information on running polyglot applications, see [Polyglot Programming](../../reference-manual/polyglot-programming.md).

## Native Image

With GraalVM you can compile Java bytecode into a platform-specific, self-contained, native executable to achieve faster startup and smaller footprint for your application.
The [Native Image](../../reference-manual/native-image/README.md) functionality is not available by default, but can be easily installed with the [GraalVM Updater](../../reference-manual/graalvm-updater.md) tool:
```shell
gu install native-image
```

The `HelloWorld` example from above is used here to demonstrate how to generate a native executable:
```java
public class HelloWorld {
  public static void main(String[] args) {
    System.out.println("Hello, World!");
  }
}
```

> Note: For compilation the `native-image` tool depends on the local toolchain. Make sure your system meets the [prerequisites](../../reference-manual/native-image/README.md#prerequisites).

Compile _HelloWorld.java_ to bytecode and then build a native executable:
```shell
javac HelloWorld.java
native-image HelloWorld
```

The last command generates an executable file named `helloworld` in the current working directory.
Invoking it executes the natively compiled code of the `HelloWorld` class as follows:
```shell
./helloworld
Hello, World!
```

More detailed documentation on this innovative technology is available in the [Native Image reference manual](../../reference-manual/native-image/README.md).

## What to Read Next

### New Users
Since this guide is intended mainly for users new to GraalVM, or users who are familiar with GraalVM but may have little experience using it, please consider investigating more complex [Example Applications](../../examples/examples.md).
We also recommend checking our [GraalVM Team Blog](https://medium.com/graalvm).

### Advanced Users
If you are mostly interested in GraalVM support for a specific language, or want more in-depth details about GraalVM's diverse technologies, proceed to [Reference Manuals](../../reference-manual/reference-manuals.md).

If you are looking for the tooling support GraalVM offers, proceed to [Debugging and Monitoring Tools](../../tools/tools.md).

If you are considering GraalVM as a platform for your future language or tool implementation, go to [GraalVM as a Platform](../../../truffle/docs/README.md).

You can find information on GraalVM's security model in the [Security Guide](../../security/security-guide.md), and rich API documentation in [GraalVM SDK Javadoc](https://www.graalvm.org/sdk/javadoc/) and [Truffle Javadoc](https://www.graalvm.org/truffle/javadoc/).
