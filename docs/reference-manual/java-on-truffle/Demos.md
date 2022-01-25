---
layout: docs-experimental
toc_group: espresso
link_title: Demo Applications
permalink: /reference-manual/java-on-truffle/demos/
---

# Running Demo Applications

Java on Truffle is an implementation of the Java Virtual Machine Specification, which offers some interesting capabilities in addition to being able to run applications in Java or other JVM languages.
To illustrate what Java on Truffle can do, please consider the following short examples.

## Enhanced HotSwap Capabilities with Java on Truffle

You can use the built-in enhanced HotSwap capabilities for applications running with Java on Truffle.
You do not have to configure anything specific besides launching your app in debug mode and attaching a standard IDE debugger to gain the advantages of enhanced HotSwap.

### Debugging with Java on Truffle

You can use your favorite IDE debugger to debug Java applications running in the Java on Truffle runtime.
For example, starting a debugger session from IntelliJ IDEA is based on the Run Configurations.
To ensure you attach the debugger to your Java application in the same environment, navigate in the main menu to Run -> Debug… -> Edit Configurations, expand Environment, check the JRE value and VM options values.
It should show GraalVM as project's JRE and VM options should include `-truffle -XX:+IgnoreUnrecognizedVMOptions`. It is necessary to specify `-XX:+IgnoreUnrecognizedVMOptions` because Intellij automatically adds a `-javaagent` argument which is not supported yet.
Press Debug.

This will run the application and start a debugger session in the background.

### Applying Code Changes in a Debugging Session

Once you have your debugger session running, you will be able to apply extensive code changes (HotSwap) without needing to restart the session.
Feel free to try this out on your own applications or by following these instructions:

1. Create a new Java application.
2. Use the following `main` method as a starting point:

    ```java
          public class HotSwapDemo {

              private static final int ITERATIONS = 100;

              public static void main(String[] args) {
                  HotSwapDemo demo = new HotSwapDemo();
                  System.out.println("Starting HotSwap demo with Java on Truffle: 'java.vm.name' = " + System.getProperty("java.vm.name"));
                  // run something in a loop
                  for (int i = 1; i <= ITERATIONS; i++) {
                      demo.runDemo(i);
                  }
                  System.out.println("Completed HotSwap demo with Java on Truffle");
              }

              public void runDemo(int iteration) {
                  int random = new Random().nextInt(iteration);
                  System.out.printf("\titeration %d ran with result: %d\n", iteration, random);
              }
          }
    ```

3. Check that the `java.vm.name` property says you're running on Espresso.
4. Place a line breakpoint on the first line in `runDemo()`.
5. Setup the Run configurations to run with Java on Truffle and press Debug. You will see:

    ![debug-1](/resources/img/java-on-truffle/debug-1.png)

6. While paused at the breakpoint, extract a method from the body of `runDemo()`:

    ![debug-2](/resources/img/java-on-truffle/debug-2.png)

7. Reload the changes by navigating to Run -> Debugging Actions -> Reload Changed Classes:

    ![debug-3](/resources/img/java-on-truffle/debug-3.png)

8. Verify that the change was applied by noticing the `<obsolete>:-1` current frame in the Debug -> Frames view:

    ![debug-4](/resources/img/java-on-truffle/debug-4.png)

9. Place a breakpoint on the first line of the new extracted method and press Resume Program. The breakpoint will hit:

    ![debug-5](/resources/img/java-on-truffle/debug-5.png)

10. Try to change the access modifiers of `printRandom()` from `private` to `public static`. Reload the changes. Press Resume Program to verify the change was applied:

    ![debug-6](/resources/img/java-on-truffle/debug-6.png)

Watch  video version of the enhanced HotSwap capabilities with Java on Truffle demo.

<div class="row">
  <div class="col-sm-12">
    <div class="vlog__video">
      <img src="/resources/img/java-on-truffle/enhanced-hotswap-capabilities-demo.png" alt="video_1">
          <a href="#" data-video="gfuvvV6mplo" class="btn btn-primary btn-primary--filled js-popup">watch video</a>
    </div>
  </div>
</div>

<div id="video-view" class="modal-window">
  <div class="modal-window__content">
    <button type="button" title="Close" id="js-close" class="modal-window__close"><img src="/resources/img/btn-close.svg" alt="close_video"></button>
    <div class="modal-window__video">
      <div id="player"></div>
    </div>
  </div>
</div>
<br>

### Supported Changes

The plan is to support arbitrary code changes when running applications with Java on Truffle.
As of GraalVM 22.0.0 the following changes are supported:

* Add and remove methods
* Add and remove constructors
* Add and remove methods from interfaces
* Change access modifiers of methods
* Change access modifiers of constructors
* Changes to Lambdas
* Add new anonymous inner classes
* Remove anonymous inner classes

The following changes are supported under the new flag `--java.ArbitraryChangesSupport=true`:

* Add and remove fields
* Change field type
* Changes to class access modifiers, e.g. abstract and final modifiers

As of GraalVM 22.0.0, the following limitations remain:

* Changing the superclass
* Changing implemented interfaces
* Changes to Enums

## Mixing AOT and JIT for Java

GraalVM Native Image technology allows compiling applications ahead-of-time (AOT) to executable native binaries which:
* are standalone
* start instantly
* have lower memory usage

The main trade off for using Native Image is that the analysis and compilation of your program happens under the closed world assumption, meaning the static analysis needs to process all bytecode which will ever be executed in the application.
This makes using some language features like dynamic class loading or reflection tricky.

Java on Truffle is a JVM implementation of a JVM bytecode interpreter, built on the [Truffle framework](../../../truffle/docs/README.md).
It is essentially a Java application, as are the Truffle framework itself and the GraalVM JIT compiler.
All three of them can be compiled ahead-of-time with `native-image`.
Using Java on Truffle for some parts of your application makes it possible to isolate the required dynamic behaviour and still use the native image on the rest of your code.

Consider a canonical Java Shell tool (JShell) as an example command line application.
It is a REPL capable of evaluating Java code and consists of two parts:
* the UI - CLI app handling input-output
* the backend processor for running code you enter into Shell.

This design naturally fits the point we are trying to illustrate. We can build a native executable of the JShell's UI part, and make it include Java on Truffle to run the code dynamically specified at run time.

Prerequisites:
* [Latest GraalVM](https://www.graalvm.org/downloads/)
* [Native Image](../native-image/README.md#install-native-image)
* [Java on Truffle](README.md#install-java-on-truffle)

1. Clone the [project](https://github.com/graalvm/graalvm-demos) with the demo applications and navigate to the `espresso-jshell` directory:

```
git clone https://github.com/graalvm/graalvm-demos.git
cd graalvm-demos/espresso-jshell
```

The JShell implementation is actually the normal JShell launcher code, which only accepts Java on Truffle implementation (the project code-name is "Espresso") of the execution engine.

The "glue" code that binds the part which is AOT compiled with the component that dynamically evaluates the code is located in the `EspressoExecutionControl` class.
It loads the JShell classes within the Java on Truffle context and delegate the input to them:

```shell
    protected final Lazy<Value> ClassBytecodes = Lazy.of(() -> loadClass("jdk.jshell.spi.ExecutionControl$ClassBytecodes"));
    protected final Lazy<Value> byte_array = Lazy.of(() -> loadClass("[B"));
    protected final Lazy<Value> ExecutionControlException = Lazy.of(() -> loadClass("jdk.jshell.spi.ExecutionControl$ExecutionControlException"));
    protected final Lazy<Value> RunException = Lazy.of(() -> loadClass("jdk.jshell.spi.ExecutionControl$RunException"));
    protected final Lazy<Value> ClassInstallException = Lazy.of(() -> loadClass("jdk.jshell.spi.ExecutionControl$ClassInstallException"));
    protected final Lazy<Value> NotImplementedException = Lazy.of(() -> loadClass("jdk.jshell.spi.ExecutionControl$NotImplementedException"));
    protected final Lazy<Value> EngineTerminationException = Lazy.of(() -> loadClass("jdk.jshell.spi.ExecutionControl$EngineTerminationException"));
    protected final Lazy<Value> InternalException = Lazy.of(() -> loadClass("jdk.jshell.spi.ExecutionControl$InternalException"));
    protected final Lazy<Value> ResolutionException = Lazy.of(() -> loadClass("jdk.jshell.spi.ExecutionControl$ResolutionException"));
    protected final Lazy<Value> StoppedException = Lazy.of(() -> loadClass("jdk.jshell.spi.ExecutionControl$StoppedException"));
    protected final Lazy<Value> UserException = Lazy.of(() -> loadClass("jdk.jshell.spi.ExecutionControl$UserException"));
```

There is more code to pass the values correctly and transform the exceptions.
To try it out, build the `espresso-jshell` binary using the provided script, which will:
1. Build the Java sources to the bytecode
2. Build the JAR file
3. Build a native image

The most important configuration line in the `native-image` command is `--language:java` which instructs to include the Java on Truffle implementation into the binary.
After the build you can observe the resulting binary file (`file` and `ldd` are Linux commands)
```shell
file ./espresso-jshell
ldd ./espresso-jshell
```

It is indeed a binary file not depending on the JVM, and you can run it noticing how fast it starts:
```shell
./espresso-jshell
|  Welcome to JShell -- Version 11.0.10
|  For an introduction type: /help intro

jshell> 1 + 1
1 ==> 2
```

Experiment with loading new code into JShell and see how Java on Truffle executes it.

Watch a video version of the mixing AOT and JIT compiled code with Java on Truffle demo.

<div class="row">
  <div class="col-sm-12">
    <div class="vlog__video">
      <img src="/resources/img/java-on-truffle/mixing-AOT-and-JIT-demo.png" alt="video_1">
          <a href="#" data-video="Z0Rb6QRyQVw" class="btn btn-primary btn-primary--filled js-popup">watch video</a>
    </div>
  </div>
</div>

<div id="video-view" class="modal-window">
  <div class="modal-window__content">
    <button type="button" title="Close" id="js-close" class="modal-window__close"><img src="/resources/img/btn-close.svg" alt="close_video"></button>
    <div class="modal-window__video">
      <div id="player"></div>
    </div>
  </div>
</div>
<br>


## GraalVM Tools with Java on Truffle

Java on Truffle is a proper part of the GraalVM ecosystem, and like other GraalVM-supported languages gets the support of developer tooling by default. The [Truffle framework](/graalvm-as-a-platform/language-implementation-framework/) integrates with the tools like the debugger, profiler, memory analyser, the [Instrumentation API](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.html).
The interpreter for a language needs to mark the AST nodes with some annotations to support those tools.

For example, to be able to use a profiler, a language interpreter needs to mark the root nodes.
For the debugger purposes, the language expressions should be marked as instrumental, the scopes for the variables specified, and so on. The language interpreter does not need to integrate with the tools itself.
As a result, you can profile a Java on Truffle program out of the box using either the CPU Sampler or Memory Tracer tools.

For example, if we have a class like the following one computing the prime numbers:
```java
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public class Main {

    public static void main(String[] args) {
        Main m = new Main();

        for (int i = 0; i < 100_000; i++) {
            System.out.println(m.random(100));
        }
    }

    private Random r = new Random(41);
    public List<Long> random(int upperbound) {
        int to = 2 + r.nextInt(upperbound - 2);
        int from = 1 + r.nextInt(to - 1);
        return primeSequence(from, to);
    }
    public static List<Long> primeSequence(long min, long max) {
        return LongStream.range(min, max)
                .filter(Main::isPrime)
                .boxed()
                .collect(Collectors.toList());
    }
    public static boolean isPrime(long n) {
        return LongStream.rangeClosed(2, (long) Math.sqrt(n))
                .allMatch(i -> n % i != 0);
    }
}
```

Build this program, and run it with the `--cpusampler` option.
```shell
javac Main.java
java -truffle --cpusampler Main > output.txt
```

At the end of the `output.txt` file you will find the profiler output, the histogram of the methods, and how much time the execution took.
You can also try an experiment with the `--memtracer` option, to see where the allocations in this program are happening.
```shell
java -truffle --experimental-options --memtracer Main > output.txt
```

Other tools that GraalVM offers are [Chrome Debugger](/tools/chrome-debugger/), [Code Coverage](/tools/code-coverage/), and [GraalVM Insight](/tools/graalvm-insight/).

Having the "out-of-the-box" support for the developer tooling makes Java on Truffle an interesting choice of the JVM.

Watch a short demonstration of GraalVM built-in tools for Java on Truffle.

<div class="row">
  <div class="col-sm-12">
    <div class="vlog__video">
      <img src="/resources/img/java-on-truffle/tools-for-Java-on-Truffle.png" alt="video_1">
          <a href="#" data-video="QHajwx7BPyo" class="btn btn-primary btn-primary--filled js-popup">watch video</a>
    </div>
  </div>
</div>

<div id="video-view" class="modal-window">
  <div class="modal-window__content">
    <button type="button" title="Close" id="js-close" class="modal-window__close"><img src="/resources/img/btn-close.svg" alt="close_video"></button>
    <div class="modal-window__video">
      <div id="player"></div>
    </div>
  </div>
</div>
<br>
