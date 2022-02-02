---
layout: docs
toc_group: examples
link_title: Java/Kotlin Native Image Example
permalink: /examples/java-kotlin-aot/
---

# Build a Native Image of a Java and Kotlin Application

This example demonstrates how to compile a Java and Kotlin application ahead-of-time into a native executable, and illustrates the advantages.

## Preparation

1&#46; Download or clone the repository and navigate into the `java-kotlin-aot` directory:

  Note: You can use any JDK for building the application. However, `javac` from GraalVM in the build script is used to simplify the prerequisites so another JDK does not need to be installed.

2&#46; [Download GraalVM](https://www.graalvm.org/downloads/), unzip the archive, export the GraalVM home directory as the `$JAVA_HOME` and add `$JAVA_HOME/bin` to the `PATH` environment variable:
  On Linux:
  ```bash
  export JAVA_HOME=/home/${current_user}/path/to/graalvm
  export PATH=$JAVA_HOME/bin:$PATH
  ```
  On macOS:
  ```bash
  export JAVA_HOME=/Users/${current_user}/path/to/graalvm/Contents/Home
  export PATH=$JAVA_HOME/bin:$PATH
  ```
  On Windows:
  ```bash
  setx /M JAVA_HOME "C:\Progra~1\Java\<graalvm>"
  setx /M PATH "C:\Progra~1\Java\<graalvm>\bin;%PATH%"
  ```
  Note that your paths are likely to be different depending on the download location.

3&#46; [Install Native Image](../reference-manual/native-image/README.md/#install-native-image) by running.
  ```bash
  gu install native-image
  ```

4&#46; Then execute:
  ```shell
  ./build.sh
  ```

Have a look at the `build.sh` script which creates a native executable from a Java class.
The `native-image` utility compiles the application ahead-of-time for faster startup and lower general overhead at runtime.
```shell
$JAVA_HOME/bin/native-image --no-fallback -cp ./target/mixed-code-hello-world-1.0-SNAPSHOT-jar-with-dependencies.jar -H:Name=helloworld -H:Class=hello.JavaHello -H:+ReportUnsupportedElementsAtRuntime
```

It takes a few parameters: the classpath, the main class of the application with `-H:Class=...`, and the name of the resulting executable with `-H:Name=...`.

After executing the `native-image` command, check the directory.
It should have produced the executable file, `helloworld`.

## Running the Application

To run the application, you need to execute the JAR file in the `target` directory.
You can run it as a normal Java application using `java`:
```shell
java -cp ./target/mixed-code-hello-world-1.0-SNAPSHOT-jar-with-dependencies.jar hello.JavaHello
```

Or, since we have a native executable prepared, you can run that directly.
```shell
./helloworld

```

The `run.sh` file executes both, and times them with the `time` utility.
An output close to the following should be produced:
```shell
→ ./run.sh
+ java -cp ./target/mixed-code-hello-world-1.0-SNAPSHOT-jar-with-dependencies.jar hello.JavaHello
Hello from Kotlin!
Hello from Java!

real	0m0.589s
user	0m0.155s
sys	0m0.072s
+ ./helloworld
Hello from Kotlin!
Hello from Java!

real	0m0.053s
user	0m0.006s
sys	0m0.006s
```

The performance gain of the native version is largely due to the faster startup.

### License

This sample application is taken from the JetBrains [Kotlin-examples repository](https://github.com/JetBrains/kotlin-examples/tree/master/maven/mixed-code-hello-world).
It is distributed under the Apache License 2.0.
