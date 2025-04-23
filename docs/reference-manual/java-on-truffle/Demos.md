---
layout: docs
toc_group: espresso
link_title: Demo Applications
permalink: /reference-manual/espresso/demos/
redirect_from: /reference-manual/java-on-truffle/demos/
---

# Running Demo Applications

Espresso is an implementation of the Java Virtual Machine Specification, which offers some interesting capabilities in addition to being able to run applications in Java or other JVM languages.
For example, the enhanced [HotSwap capabilities](HotSwap.md) boosts developer productivity by enabling unlimited hot code reloading.
Moreover, to illustrate what Espresso can do, consider the following short examples.

## Mixing AOT and JIT for Java

GraalVM Native Image technology allows compiling applications ahead-of-time (AOT) to executable native binaries which:
* are standalone
* start instantly
* have lower memory usage

The main trade off for using Native Image is that the analysis and compilation of your program happens under the closed world assumption, meaning the static analysis needs to process all bytecode which will ever be executed in the application.
This makes using some language features such as dynamic class loading or reflection tricky.

Espresso is a JVM implementation of a JVM bytecode interpreter, built on the [Truffle framework](../../../truffle/docs/README.md).
It is essentially a Java application, as are the Truffle framework itself and the GraalVM JIT compiler.
All three of them can be compiled ahead-of-time with `native-image`.
Using Espresso for some parts of your application makes it possible to isolate the required dynamic behavior and still use the native executable on the rest of your code.

Consider a canonical Java Shell tool (JShell) as an example command line application.
It is a REPL capable of evaluating Java code and consists of two parts:
* the UI - CLI app handling input-output
* the backend processor for running code you enter into Shell.

This design naturally fits the point we are trying to illustrate. We can build a native executable of the JShell's UI part, and make it include Espresso to run the code dynamically specified at run time.

#### Prerequisites:
* [GraalVM JDK](https://www.graalvm.org/downloads/)
* [Espresso](README.md#getting-started)

Clone the [project](https://github.com/graalvm/graalvm-demos) with the demo applications and navigate to the `espresso-jshell` directory:
```shell
git clone https://github.com/graalvm/graalvm-demos.git
cd graalvm-demos/espresso-jshell
```

The JShell implementation is actually the normal JShell launcher code, which only accepts Espresso implementation of the execution engine.

The "glue" code that binds the part which is AOT compiled with the component that dynamically evaluates the code is located in the `EspressoExecutionControl` class.
It loads the JShell classes within the Espresso context and delegate the input to them:

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
3. Build a native executable

After the build you can observe the resulting binary file (`file` and `ldd` are Linux commands):
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

Experiment with loading new code into JShell and see how Espresso executes it.

Watch a video version of the mixing AOT and JIT compiled code with the Espresso demo.

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


## GraalVM Tools with Espresso

Espresso is a proper part of the GraalVM ecosystem, and like other GraalVM-supported languages gets the support of developer tooling by default. The [Truffle framework](/graalvm-as-a-platform/language-implementation-framework/) integrates with the tools such as the debugger, profiler, memory analyzer, the [Instrumentation API](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/instrumentation/TruffleInstrument.html).
The interpreter for a language needs to mark the AST nodes with some annotations to support those tools.

For example, to be able to use a profiler, a language interpreter needs to mark the root nodes.
For the debugger purposes, the language expressions should be marked as instrumental, the scopes for the variables specified, and so on. The language interpreter does not need to integrate with the tools itself.
As a result, you can profile a Java application on Espresso out of the box using either the CPU Sampler or Memory Tracer tools.

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

Other tools that GraalVM offers are [Chrome Debugger](../../tools/chrome-debugger.md), [Code Coverage](../../tools/code-coverage.md), and [GraalVM Insight](../../tools/insight/README.md).

Having the "out-of-the-box" support for the developer tooling makes Espresso an interesting choice of the JVM.

Watch a short demonstration of GraalVM built-in tools for Espresso.

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
