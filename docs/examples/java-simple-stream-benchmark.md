---
layout: docs
toc_group: examples
link_title: Java Stream API Example
permalink: /examples/java-simple-stream-benchmark/
---

# Simple Java Stream Benchmark

This application is a small benchmark of the Java Stream API. It demonstrates how the Graal compiler can achieve better performance for highly abstracted programs like those using Streams, Lambdas, or other Java features.

## Preparation

1. [Download GraalVM](https://www.graalvm.org/downloads/), unzip the archive, export the GraalVM Home directory as the `$JAVA_HOME` and add `$JAVA_HOME/bin` to the `PATH` environment variable:
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

2. Download or clone the repository and navigate into the `java-simple-stream-benchmark` directory:
    ```shell
    git clone https://github.com/graalvm/graalvm-demos
    cd graalvm-demos/java-simple-stream-benchmark
    ```

3. Build the benchmark. You can manually execute `mvn package`, but there is also a `build.sh` script included for your convenience:
    ```shell
    ./build.sh
    ```
    
Now you are all set to execute the benchmark and compare the results between different JVMs.

## Running the Benchmark

To run the benchmark, you need to execute the `target/benchmarks.jar` file.
You can run it with the following command:
```shell
java -jar target/benchmarks.jar
```
If you would like to run the benchmark on a different JVM, you can run it with whatever `java` you have.
However, if you just want to run it on the same JVM, but without the Graal compiler, you may add the `-XX:-UseJVMCICompiler` option into the same command:
```shell
java -XX:-UseJVMCICompiler -jar target/benchmarks.jar
```

This way, the Graal compiler will not be used as the JVMCI compiler and the JVM will use its default one.

### Note about Results

The benchmark mode is `AverageTime` in nanoseconds per operation, which means lower numbers are better.
Note that the results you see can be influenced by the hardware you are running this benchmark on, the CPU load, and other factors.
Interpret them responsibly.
