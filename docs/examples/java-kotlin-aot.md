---
layout: docs
toc_group: examples
link_title: Java/Kotlin Native Image Example
permalink: /examples/java-kotlin-aot/
---

# Build a Native Image of a Java and Kotlin Application

This example demonstrates how to compile a Java and Kotlin application
ahead-of-time into a native executable, and illustrates the advantages.

### Preparation

This example requires the [Maven](https://maven.apache.org/) build tool.

1&#46; Download or clone the repository and navigate into the `java-kotlin-aot` directory:
```shell
git clone https://github.com/graalvm/graalvm-demos
cd graalvm-demos/java-kotlin-aot
```
This is a simple Java and Kotlin application showing how easy it is to
interoperate between JVM-based languages. A Java method accesses a String from
Kotlin and calls a Kotlin function, which later accesses a String from a Java
class. Before running this example, you need to build the application.

Note: You can use any JDK for building the application. However, `javac` from GraalVM
in the build script is used to simplify the prerequisites so another JDK does not need to be installed.

2&#46; Having installed GraalVM, export the home directory as `$GRAALVM_HOME` and add `$GRAALVM_HOME/bin`
to the path, using a command-line shell for Linux:
```shell
export GRAALVM_HOME=/home/${current_user}/path/to/graalvm
```
For macOS, use:
```shell
export GRAALVM_HOME=/Users/${current_user}/path/to/graalvm/Contents/Home
```
Note that your paths are likely to be different depending on the download location.

3&#46; [Install Native Image](../reference-manual/native-image/README.md/#install-native-image) to make use of the `native-image` utility.

4&#46; Then execute:
```shell
./build.sh
```

Have a look at the `build.sh` script which creates a native executable from a Java class.
The `native-image` utility compiles the application ahead-of-time for faster startup and lower general overhead at runtime.
```shell
$GRAALVM_HOME/bin/native-image -cp ./target/mixed-code-hello-world-1.0-SNAPSHOT.jar -H:Name=helloworld -H:Class=hello.JavaHello -H:+ReportUnsupportedElementsAtRuntime --allow-incomplete-classpath
```

It takes a few parameters: the classpath, the main class of the application with
`-H:Class=...`, and the name of the resulting executable with `-H:Name=...`.

After executing the `native-image` command, check the directory. It should have
produced the executable file, `helloworld`.

### Running the Application

To run the application, you need to execute the JAR file in the `target` dir.
You can run it as a normal Java application using `java`.
Or, since we have a native executable prepared, you can run that directly.
The `run.sh` file executes both, and times them with the `time` utility:
```shell
java -cp ./target/mixed-code-hello-world-1.0-SNAPSHOT-jar-with-dependencies.jar hello.JavaHello
./helloworld

```

An output close to the following should be produced:
```shell
→ ./run.sh
+ java -cp ./target/mixed-code-hello-world-1.0-SNAPSHOT-jar-with-dependencies.jar hello.JavaHello
Hello from Kotlin!
Hello from Java!

real	0m0.129s
user	0m0.094s
sys	0m0.034s
+ ./helloworld
Hello from Kotlin!
Hello from Java!

real	0m0.010s
user	0m0.003s
sys	0m0.004s
```

The performance gain of the native version is largely due to the faster startup.

### License

This sample application is taken from the JetBrains [Kotlin-examples repository](https://github.com/JetBrains/kotlin-examples/tree/master/maven/mixed-code-hello-world).
It is distributed under the Apache License 2.0.
