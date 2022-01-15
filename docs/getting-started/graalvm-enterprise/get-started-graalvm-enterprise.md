---
layout: ohc
permalink: /getting-started/
---

# Get Started with Oracle GraalVM Enterprise Edition

Here you will find information about downloading and installing GraalVM Enterprise, running basic applications with it, and adding support for its accompanying features.
Further, you will learn about the polyglot capabilities of GraalVM Enterprise and see how to build platform-specific native executables of JVM-based applications.

If you are new to GraalVM Enterprise or have little experience using it, we recommend starting with the [GraalVM Enterprise Overview](../../enterprise-overview/architecture-overview.md) page.
There you will find information about GraalVM Enterprise's architecture, the distributions available, supported platforms, licensing and support, core and additional features, and much more.

If you have GraalVM Enterprise already installed and have experience using it, you can skip this getting started guide and proceed to the in-depth [Reference Manuals](../../reference-manual/reference-manuals.md).

## Download GraalVM Enterprise

You can get Oracle GraalVM Enterprise Edition by:
- downloading from [Oracle GraalVM Downloads](https://www.oracle.com/downloads/graalvm-downloads.html) and accepting [Oracle Technology Network License Agreement for GraalVM Enterprise Edition](https://www.oracle.com/downloads/licenses/graalvm-otn-license.html) for developing, testing, prototyping, and demonstrating Your application.
- subscribing to [Oracle Java SE Subscription and Oracle Java SE Desktop Subscription](https://www.oracle.com/uk/java/java-se-subscription/). The subscription includes entitlement to GraalVM Enterprise.
- subscribing to [Oracle Cloud](https://www.oracle.com/cloud). GraalVM Enterprise is free to use, including support, for Oracle Cloud subscribers.

## Install GraalVM Enterprise

Getting GraalVM Enterprise installed and ready-to-go should take a few minutes.
Choose your operating system and proceed to the installation steps for your specific platform:

* [Linux](installation-linux.md)
* [Linux ARM64](installation-linux-aarch64.md)
* [Oracle Linux](oci/compute-instances.md)
* [macOS](installation-macos.md)
* [Windows](installation-windows.md)

## Start Running Applications

For demonstration purposes here, we will use GraalVM Enterprise based on Java 17.

The core distribution of GraalVM Enterprise includes the JVM, the GraalVM compiler, the LLVM runtime, and JavaScript runtime.
Having downloaded and installed GraalVM Enterprise, you can already run Java, JavaScript, and LLVM-based applications.

GraalVM Enterprise's `/bin` directory is similar to that of a standard JDK, but includes a set of additional launchers and utilities:
- **js** -- a JavaScript launcher
- **lli** -- a LLVM bitcode launcher
- **gu** -- the GraalVM Updater tool to install additional language runtimes and utilities

Check the versions of the runtimes provided by default:
```shell
java version "17.0.2" 2022-01-18 LTS
Java(TM) SE Runtime Environment GraalVM EE 22.0.0 (build 17.0.2+5-LTS-jvmci-22.0-b02)
Java HotSpot(TM) 64-Bit Server VM GraalVM EE 22.0.0 (build 17.0.2+5-LTS-jvmci-22.0-b02, mixed mode, sharing)

js -version
GraalVM JavaScript (GraalVM EE Native 22.0.0)

lli --version
LLVM 12.0.1 (GraalVM EE Native 22.0.0)
```

Further below you will find information on how to add other optionally available GraalVM Enterprise runtimes including Node.js, Ruby, R, Python, and WebAssembly.

## Runtime for Different Languages

### Java
The `java` launcher runs the JVM with the GraalVM Enterprise default compiler - the GraalVM compiler.
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
For more information on the GraalVM compiler, go to the [Graal compiler](../../reference-manual/java/compiler.md).
For more extensive documentation on running Java, proceed to [JVM Languages](../../reference-manual/java/README.md).

### JavaScript and Node.js

GraalVM Enterprise can execute plain JavaScript code, both in REPL mode and by executing script files directly:
```shell
$JAVA_HOME/bin/js
> 1 + 2
3
```

GraalVM also supports running Node.js applications.
Node.js support is not installed by default, but can be easily added with GraalVM Updater:
```shell
gu install nodejs
```
```shell
$JAVA_HOME/bin/node -v
v14.18.1
```

More than 100,000 npm packages are regularly tested and are compatible with GraalVM Enterprise, including modules like express, react, async, request, browserify, grunt, mocha, and underscore.
To install a Node.js module, use the `npm` executable from the `<graalvm>/bin` folder, which is installed together with `node`.
The `npm` command is equivalent to the default Node.js command and supports all Node.js APIs.

Install the modules `colors`, `ansispan`, and `express` using `npm install`.
After the modules are installed, you can use them from your application.
```shell
$JAVA_HOME/bin/npm install colors ansispan express
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
$JAVA_HOME/bin/node app.js
```

For more detailed documentation and information on compatibility with Node.js, proceed to [JavaScript and Node.js](../../reference-manual/js/README.md).

### LLVM Languages

The GraalVM Enterprise LLVM runtime can execute C/C++, Rust, and other programming language that can be compiled to LLVM bitcode.
A native program has to be compiled to LLVM bitcode using an LLVM frontend such as `clang`.
The C/C++ code can be compiled to LLVM bitcode using `clang` shipped with GraalVM Enterprise via a prebuilt LLVM toolchain.

To set up the LLVM toolchain support:
1. Install the plugin:
```shell
gu install llvm-toolchain
```
2. Export the `LLVM_TOOLCHAIN` variable to the toolchain location for convenience:
```shell
export LLVM_TOOLCHAIN=$(lli --print-toolchain-path)
```

As an example, put this C code into a file named `hello.c`:
```c
#include <stdio.h>

int main() {
    printf("Hello from GraalVM!\n");
    return 0;
}
```

Then compile `hello.c` to an executable `hello` with embedded LLVM bitcode, and run it as follows:
```shell
$LLVM_TOOLCHAIN/clang hello.c -o hello
lli hello
```

For in-depth documentation and more examples of running LLVM bitcode on GraalVM Enterprise, go to [LLVM Languages](../../reference-manual/llvm/README.md).

### Python

With GraalVM Enterprise you can run Python applications in the Python 3 runtime environment.
The support is not available by default, but you can quickly add it to GraalVM using the [GraalVM Updater](../../reference-manual/graalvm-updater.md) tool:
```shell
gu install python
```

It installs the `graalpython` launcher. Check the version, and you can already run Python programs:
```shell
$JAVA_HOME/bin/graalpython --version
```

```shell
$JAVA_HOME/bin/graalpython
...
>>> 1 + 2
3
>>> quit()
```

More examples and additional information on Python support in GraalVM can be found in the [Python reference manual](../../reference-manual/python/README.md).

### Ruby

GraalVM Enterprise provides a high-performance Ruby runtime environment including the `gem` command that allows you to interact with RubyGems, Ruby Bundler, and much more.
The Ruby runtime is not available by default in GraalVM, but can be easily added using the [GraalVM Updater](../../reference-manual/graalvm-updater.md) tool:
```shell
gu install ruby
```

Once it is installed, Ruby launchers like `ruby`, `gem`, `irb`, `rake`, `rdoc`, and `ri` become available to run Ruby programs:
```shell
$JAVA_HOME/bin/ruby [options] program.rb
```

GraalVM runtime for Ruby uses the [same options as the standard implementation of Ruby](../../reference-manual/ruby/options.md), with some additions.
For example:
```shell
gem install chunky_png
$JAVA_HOME/bin/ruby -r chunky_png -e "puts ChunkyPNG::Color.to_hex(ChunkyPNG::Color('mintcream @ 0.5'))"
#f5fffa80
```

More examples and in-depth documentation can be found in the [Ruby reference manual](../../reference-manual/ruby/README.md).

### R

GraalVM Enterprise provides a GNU-compatible environment to run R programs directly or in the REPL mode.
Although the R language support is not available by default, you can add it to GraalVM Enterprise using the [GraalVM Updater](../../reference-manual/graalvm-updater.md) tool:
```shell
gu install R
```

Once it is installed, you can execute R scripts and use the R REPL:
```shell
R
R version 4.0.3 (FastR)
...

> 1 + 1
[1] 2
```

More examples and in-depth documentation can be found in the [R reference manual](../../reference-manual/r/README.md).

### WebAssembly

With GraalVM Enterprise you can run programs compiled to WebAssembly.
The support is not available by default, but you can add it to GraalVM using the [GraalVM Updater](../../reference-manual/graalvm-updater.md) tool:
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

Compile it using the most recent [Emscripten compiler frontend](https://emscripten.org/docs/tools_reference/emcc.html) version.
It should produce a standalone _floyd.wasm_ file in the current working directory:
```shell
emcc -o floyd.wasm floyd.c
```

Then you can run the compiled WebAssembly binary on GraalVM as follows:
```shell
$JAVA_HOME/bin/wasm --Builtins=wasi_snapshot_preview1 floyd.wasm
```

More details can be found in the [WebAssembly reference manual](../../reference-manual/wasm/README.md).

## Native Images

With GraalVM Enterprise you can compile Java bytecode into a platform-specific, self-contained binary - a native image - to achieve faster startup and a smaller footprint for your application.
The [Native Image](../../reference-manual/native-image/README.md) functionality is not available by default, but can be easily installed with the [GraalVM Updater](../../reference-manual/graalvm-updater.md) tool:
```shell
gu install native-image
```

The `HelloWorld` example from above is used here to demonstrate how to generate a native image:

```java
public class HelloWorld {
  public static void main(String[] args) {
    System.out.println("Hello, World!");
  }
}
```

> Note: For compilation `native-image` depends on the local toolchain. Make sure your system meets the [prerequisites](../../reference-manual/native-image/README.md#prerequisites).

Compile _HelloWorld.java_ to bytecode and then build a native image:
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

## Polyglot Capabilities of Native Images

GraalVM Enterprise makes it possible to use polyglot capabilities when building native images.
Take this example of a JSON pretty-printer Java program that embeds some JavaScript code:

```java
import java.io.*;
import java.util.stream.*;
import org.graalvm.polyglot.*;

public class PrettyPrintJSON {
  public static void main(String[] args) throws java.io.IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    String input = reader.lines()
    .collect(Collectors.joining(System.lineSeparator()));
    try (Context context = Context.create("js")) {
      Value parse = context.eval("js", "JSON.parse");
      Value stringify = context.eval("js", "JSON.stringify");
      Value result = stringify.execute(parse.execute(input), null, 2);
      System.out.println(result.asString());
    }
  }
}
```
Compile it and build a native image for it. The `--language:js` argument ensures
that JavaScript is available in the generated image:

```shell
javac PrettyPrintJSON.java
native-image --language:js --initialize-at-build-time PrettyPrintJSON
```

The native image generatation will take several minutes as it does not just build the `PrettyPrintJSON` class, but also builds JavaScript.
Additionally, the image building requires large amounts of physical memory, especially if you build an image with
the [Truffle language implementation framework](../../../truffle/docs/README.md) included, which is the case here.

The resulting executable can now perform JSON pretty-printing:

```shell
./prettyprintjson <<EOF
{"GraalVM":{"description":"Language Abstraction Platform","supports":["combining languages","embedding languages","creating native images"],"languages": ["Java","JavaScript","Node.js", "Python", "Ruby","R","LLVM"]}}
EOF
```

Here is the JSON output from the native executable:
```json
{
  "GraalVM": {
    "description": "Language Abstraction Platform",
    "supports": [
      "combining languages",
      "embedding languages",
      "creating native images"
    ],
    "languages": [
      "Java",
      "JavaScript",
      "Node.js",
      "Python",
      "Ruby",
      "R",
      "LLVM"
    ]
  }
}
```

The native image runs much faster than running the same code on the JVM directly:
```shell
time java PrettyPrintJSON < test.json > /dev/null
real	0m1.806s
user	0m3.651s
sys	0m0.341s

time ./prettyprintjson < test.json > /dev/null
real	0m0.041s
user	0m0.011s
sys	0m0.013s
```

## Combine Languages

GraalVM Enterprise allows you to call one programming language into another and exchange data between them.
To enable interoperability, GraalVM Enterprise provides the `--polyglot` flag.

For example, running `js --jvm --polyglot example.js` executes `example.js` in a polyglot context.
If the program calls any code in other supported languages, GraalVM Enterprise executes that code in the same runtime as the `example.js` application.
For more information on running polyglot applications, see [Polyglot Programming](../../reference-manual/polyglot-programming.md).

## What to Read Next

### New Users
Since this guide is intended mainly for users new to GraalVM Enterprise, or users
who are familiar with GraalVM Enterprise but may have little experience using it, consider investigating more complex [Example Applications](../../examples/examples.md).

### Oracle Cloud Users
Oracle Cloud users considering GraalVM Enterprise for their cloud workloads are
invited to read [GraalVM Enterprise on OCI](oci/compute-instances.md).
This page focuses on using GraalVM Enterprise with the Oracle Cloud Infrastructure Virtual Machine compute instance.

### Advanced Users
If you are mostly interested in GraalVM Enterprise support for a specific language, or want more in-depth details about GraalVM Enterprise's diverse features, proceed to [Reference Manuals](../../reference-manual/reference-manuals.md).

If you are looking for the tooling support GraalVM Enterprise offers, proceed to [Debugging and Monitoring Tools](../../tools/tools.md).

If you are considering GraalVM Enterprise as a platform for your future language or tool implementation, go to [GraalVM Enterprise as a Platform](../../../truffle/docs/README.md).

You can find information on GraalVM Enterprise's security model in the [Security Guide](../../security/security-guide.md), and rich API documentation in [GraalVM SDK Javadoc](https://docs.oracle.com/en/graalvm/enterprise/22/sdk/index.html).
