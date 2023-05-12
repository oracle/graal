---
layout: ohc
permalink: /getting-started/
---

# Get Started with Oracle GraalVM

Here you will find information about downloading and installing Oracle GraalVM, running basic applications with it, and adding support for its accompanying features.
You will also learn about the polyglot capabilities of Oracle GraalVM and see how to build a platform-specific native executable from a JVM-based application.

If you are new to Oracle GraalVM or have little experience using it, we recommend you to start with the [Oracle GraalVM Overview](../../enterprise-overview/architecture-overview.md) page.
It provides information about Oracle GraalVM's architecture, available distributions, supported platforms, licensing and support, core and additional features, and much more.

If you have already installed Oracle GraalVM and have experience using it, you can skip this getting started guide and proceed to the in-depth [Reference Manuals](../../reference-manual/reference-manuals.md).

## Download Oracle GraalVM

You can get Oracle GraalVM by:
- downloading from [Oracle GraalVM Downloads](https://www.oracle.com/downloads/graalvm-downloads.html).
- subscribing to [Oracle Java SE Subscription and Oracle Java SE Desktop Subscription](https://www.oracle.com/uk/java/java-se-subscription/). The subscription includes entitlement to Oracle GraalVM.
- subscribing to [Oracle Cloud](https://www.oracle.com/cloud). Oracle GraalVM is free to use, including support, for Oracle Cloud subscribers.

## Install Oracle GraalVM

Choose your operating system and proceed to the installation steps for your specific platform:

* Oracle Cloud (OCI) 
  * [Code Editor](oci/code-editor.md)
  * [Cloud Shell](oci/cloud-shell.md)
  * [Compute with Oracle Linux 7/8](oci/installation-compute-instance-with-OL.md)
  * [DevOps Build Pipelines](oci/installation-devops-build-pipeline.md)
* [Linux](installation-linux.md)
* [macOS](installation-macos.md)
* [Windows](installation-windows.md)
* [Container Images](container-images/graalvm-ee-container-images.md)

## Running Applications

The core distribution of GraalVM includes the JDK, the Graal compiler, and Native Image.
Having installed GraalVM, you can already run any Java application unmodified.

Other features can be installed on request, using **gu**&emdash;the GraalVM Updater tool to install additional language runtimes and utilities.
Further below you will find information on how to add other optionally available language runtimes including JavaScript and Node.js.

### Java

The `java` launcher runs the JVM with Graal as the last-tier compiler.
Check the installed Java version:
```shell
$JAVA_HOME/bin/java -version
```

Take a look at this typical `HelloWorld` class:
```java
public class HelloWorld {
  public static void main(String[] args) {
    System.out.println("Hello, World!");
  }
}
```

Run the following commands to compile this class to bytecode and then run it:
```shell
javac HelloWorld.java
java HelloWorld
Hello World!
```

You can find many larger Java examples in [GraalVM Demos on GitHub](https://github.com/graalvm/graalvm-demos).
For more information on the Graal compiler, see the [compiler documentation](../../reference-manual/java/compiler.md).
For more extensive documentation on running Java, check [this reference documentation](../../reference-manual/java/README.md).

### Native Image

With [Native Image](../../reference-manual/native-image/README.md) you can compile Java bytecode into a platform-specific, self-contained, native executable to achieve faster startup and a smaller footprint for your application.

The `HelloWorld` example from above is used here to demonstrate how to generate a native executable:
```java
public class HelloWorld {
  public static void main(String[] args) {
    System.out.println("Hello, World!");
  }
}
```

> Note: For compilation `native-image` depends on the local toolchain. Make sure your system meets the [prerequisites](../../reference-manual/native-image/README.md#prerequisites).

Compile _HelloWorld.java_ to bytecode and then build a native executable:
```shell
javac HelloWorld.java
native-image HelloWorld
```

The last command generates an executable file named _helloworld_ in the current working directory.
Invoking it runs the natively compiled code of the `HelloWorld` class as follows:
```shell
./helloworld
Hello, World!
```

More detailed documentation on this innovative technology is available in the [Native Image reference manual](../../reference-manual/native-image/README.md).

### JavaScript and Node.js

GraalVM supports running JavaScript applications.
The JavaScript runtime is optionally available and can be installed with this command:
```shell
gu install js
```

It installs the `js` launcher in the `$JAVA_HOME/bin` directory.
With the JavaScript runtime installed, you can run plain JavaScript code, both in REPL mode and by running script files directly:
```shell
$JAVA_HOME/bin/js
> 1 + 2
3
```

GraalVM also supports running Node.js applications.
The Node.js runtime is not installed by default, but can be easily added with this command:
```shell
gu install nodejs
```

Both `node` and  `npm` launchers then become available in the `$JAVA_HOME/bin` directory.

```shell
$JAVA_HOME/bin/node -v
$JAVA_HOME/bin/npm show <package name> version
```

More than 100,000 npm packages are regularly tested and are compatible with Oracle GraalVM, including modules such as express, react, async, request, browserify, grunt, mocha, and underscore.
To install a Node.js module, use the `npm` executable from the _$JAVA_HOME/bin_ directory, which is installed together with `node`.
The `npm` command is equivalent to the default Node.js command and supports all Node.js APIs.

Install the modules `colors`, `ansispan`, and `express` using `npm install`.
After the modules are installed, you can use them from your application.
```shell
$JAVA_HOME/bin/npm install colors ansispan express
```

Copy the following code snippet and save it as a file named _app.js_ in the same directory where you installed the Node.js modules:
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

Run _app.js_ on Oracle GraalVM using the `node` command:

```shell
$JAVA_HOME/bin/node app.js
```

For more detailed documentation and information on compatibility with Node.js, see [JavaScript and Node.js](../../reference-manual/js/README.md).

## Combine Languages

Oracle GraalVM allows you to call one programming language into another and exchange data between them.
For example, running `js --jvm --polyglot example.js` runs `example.js` in a polyglot context.
If the program calls any code in other supported languages, Oracle GraalVM runs that code in the same runtime as the `example.js` application.
For more information on running polyglot applications, see [Polyglot Programming](../../reference-manual/polyglot-programming.md).

## What to Read Next

### New Users
Since this guide is intended mainly for users new to Oracle GraalVM, or users who are familiar with Oracle GraalVM but may have little experience using it, consider investigating more complex [example applications](https://github.com/graalvm/graalvm-demos).

### Oracle Cloud Users
Oracle Cloud users considering Oracle GraalVM for their cloud workloads are invited to read [Oracle GraalVM on OCI](oci/installation-compute-instance-with-OL.md).
This page focuses on using Oracle GraalVM with an Oracle Cloud Infrastructure Compute instance.

### Advanced Users
If you are mostly interested in Oracle GraalVM support for a specific language, or want more in-depth details about Oracle GraalVM's diverse features, proceed to [Reference Manuals](../../reference-manual/reference-manuals.md).

If you are considering Oracle GraalVM as a platform for your future language or tool implementation, go to [Oracle GraalVM as a Platform](../../../truffle/docs/README.md).

You can find information on Oracle GraalVM's security model in the [Security Guide](../../security/security-guide.md), and rich API documentation in [GraalVM SDK Javadoc](https://docs.oracle.com/en/graalvm/enterprise/22/sdk/index.html).
