---
layout: docs
toc_group: examples
link_title: Java Stream API Example
permalink: /examples/java-simple-stream-benchmark/
---

# Simple Java Stream Benchmark

This application is a small benchmark of the Java Stream API. It demonstrates how
the GraalVM compiler can achieve better performance for highly
abstracted programs like those using Streams, Lambdas, or other Java features.

### Preparation

This example requires the [Maven](https://maven.apache.org/) build tool.

1&#46; Download or clone the repository and navigate into the `java-simple-stream-benchmark` directory:
```shell
git clone https://github.com/graalvm/graalvm-demos
cd graalvm-demos/java-simple-stream-benchmark
```

2&#46; Build the benchmark. You can manually execute `mvn package`, but there is also
a `build.sh` script included for your convenience:
```shell
./build.sh
```

3&#46; Export the GraalVM home directory as the `$GRAALVM_HOME` and add `$GRAALVM_HOME/bin`
to the path, using a command-line shell for Linux:
```shell
export GRAALVM_HOME=/path/to/graalvm
```
For macOS:
```shell
export GRAALVM_HOME=/path/to/graalvm/Contents/Home
```

Now you are all set to execute the benchmark and compare the results between different JVMs.

### Running the Benchmark

To run the benchmark, you need to execute the `target/benchmarks.jar` file.
You can run it with the following command:
```shell
java -jar target/benchmarks.jar
```
If you would like to run the benchmark on a different JVM, you can run it with
whatever `java` you have. However, if you just want to run it on the same JVM,
but without the GraalVM compiler, you may add the `-XX:-UseJVMCICompiler` option
into the same command:
```shell
java -XX:-UseJVMCICompiler -jar target/benchmarks.jar
```

This way, the GraalVM compiler will not be used as the JVMCI compiler and the JVM will use its default one.

### Note about Results

The benchmark mode is `AverageTime` in nanoseconds per operation, which means
lower numbers are better. Note that the results you see can be influenced by the
hardware you are running this benchmark on, the CPU load, and other factors.
Interpret them responsibly.
