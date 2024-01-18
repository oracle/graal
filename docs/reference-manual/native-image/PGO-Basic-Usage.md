---
layout: docs
toc_group: pgo
link_title: Basic Usage of Profile-Guided Optimizations
permalink: /reference-manual/native-image/pgo/basic-usage
---

# Profile Guided Optimization for Native Image

One of the biggest advantages that JIT compilers have over AOT compilers is the ability to make use of the runtime observing the run time behaviour of the application they are compiling.
For example HotSpot keeps track of how many times each branch of an if statement in hot methods is executed.
Such information is passed on to the tier 2 JIT compiler (i.e. Graal) in the form of what we call a "profile" - a summary of how a method to be JIT compiled has been behaving so far during run time.
The JIT compiler then assumes that the method will continue to behave in the same manner and uses this "profile" as an additional source of information to better optimize that method.

AOT compilers typically don't have such information and are usually limited to a static view of the code they are compiling.
This means that, barring heuristics, an AOT compiler sees each branch of every if statement as equally likely to happen at run time, 
each method is as likely to be invoked as every other and each loop will repeat an equal number of times.
This puts the AOT compiler at a big disadvantage - lacking "profile" information makes it hard for the AOT compiler to reach the same quality of output as a JIT compiler.

Profile Guided Optimization described in this document is a technique for bringing profile information to an AOT compiler to improve the quality of it's output in terms of performance and size.

## What exactly is a "profile"?

As mentioned before, in the JIT compilation world, a profile is a summary of the behaviour of a particular method during run time.
The same holds for AOT compilers with the caveat that we have no runtime to provide this information to the compiler since compilation happens ahead-of-time - i.e. ahead-of-run-time.
This makes gathering the profile more challenging, but at a high level the content of the profile is very similar.
In practice, the profile is a summarised log of how many times certain events happened during run time.
These events are chosen based on what information will be useful for the compiler to make better decisions.
Examples of such events are:
- How many times was this method called?
- How many times did this if take the true branch? The false branch?
- How many times did this method allocate an object?
- How many times was `String` the run-time type of the value for the interface-typed argument of this method?

## How do I get a "profile"?

When running an application on a runtime with a JIT compiler the profiling of the application is handled by said runtime, with no extra steps for the developer.
While this is undoubtedly simpler the profiling that the runtime does is not free - in introduces overhead in execution of the code being profiled - 
both in terms of execution time and memory usage. 
This results in warmup issues - 
the application reaches predictable peak performance only after sufficient time has passed for the key parts of the application to be profiled and JIT compiled.
For long running applications this overhead usually pays for itself with the extra performance boost that comes after. 
On the other hand, for short lived applications and applications that need to start with good, predictable performance as soon as possible - this is a waste.

Gathering a profile for an AOT compiled application is more involved i.e. requires extra steps by the developer, but introduces no overhead to the final application.
Profiles need to be gathered by observing the application while it's running.
This is commonly done by building a special version of the application which includes in it counters for events of interest.
We call this an "instrumented build" because the process of adding said counters is called "instrumentation".
Naturally, the instrumented building of the application will not perform as good as the default build due to all the overhead of the counters, 
so it is not recommended to run instrumented images in production.
But, executing synthetic representative workloads on the instrumented build allows us to gather a representative profile of the application 
(just as the runtime would do for the JIT compiler).
Using that profile to help the AOT compiler produce what we call an "optimized build" of the application - 
a build where the compiler had both the static view and the dynamic profile of the application - which should preform better than the default build.

## How does a profile "guide" optimization?

Compiler optimizations often have to make decisions during compilation.
For example, the function inlining phase would, given the following method, have to decide which call site to inline and which not to.

```
private int run(String[] args) {
    if (args.length < 3) {
        return handleNotEnoughArguments(args);
    } else {
        return doActualWork(args);
    }
}
```

For illustrative purposes let's imagine that the inlining phase can only inline one of the calls.
Looking only at the static view of the code being compiled, both the `doActualWork` and `handleNotEnoughArguments` invocations look pretty much indistinguishable.
Barring any heuristics the phase would have to guess which is the better choice to inline.
Making the wrong choice can lead to a worse output from the compiler. 
Let's assume `run` is most commonly called with the right number of arguments at run time.
Then inlining `handleNotEnoughArguments` would increase the code size of the compilation unit without giving any performance benefit since the call to `doActualWork` needs to still be made every time.

Having a run-time profile of the application can give the compiler data with which differentiating between which call should be inlined is trivial.
For example, if our run-time profile recorded this if condition as being `false` 100 times and `true` 3 times - we probably should inline `doActualWork`.
This is the essence of PGO - using information from the profile i.e. from the run time behaviour of the application being compiled, 
to give the compiler grounding in data when making decisions.
The actual decisions and the actual events the profile records vary from phase to phase, but this illustrated the general idea.

Notice here that PGO expects a representative workload to be run on the instrumented build of the application.
Providing a counter-productive profile - i.e. a profile that records the exact opposite of the actual run-time behaviour of the app - will be counter-productive.
In our example this would be running the instrumented build with a workload that invokes the run method with too few arguments,
while the actual application does not.
This would lead to the inlining phase choosing to inline `handleNotEnoughArguments` reducing the performance of the optimized build.

Hence, the goal is to gather profiles on workload that match the production workloads as much as possible.
The gold standard for this is to run the exact same workloads we expect to run in production on the instrumented build.
Unfortunately, this is not always a simple thing to do, and running representative workloads is a big challenge in using PGO.

## A Game Of Life example

To understand the usage of PGO in the context of GraalVM native image, let's consider an example application.
This application is an implementation of Conway's Game of Life simulation (https://en.wikipedia.org/wiki/Conway%27s_Game_of_Life) on a 4000 by 4000 grid.
Please note that this a very simple and not-illustrative-of-the-real-world application, but it should serve well as a running example.
The application takes as input a file specifying the inital state of the world, 
a file to output the final state of the world to and an integer declaring how many iterations of the simulation to run.

The entire source code of the application follows, and it's here as a reference for reproduction of results, feel free to not read or understand it for now.

```java
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;

// A simple Java program to implement Game of Life
// Modified from https://www.geeksforgeeks.org/program-for-conways-game-of-life/
public class GameOfLife {

    private static final int M = 4000;
    private static final int N = 4000;

    public static void main(String[] args) {
        new GameOfLife().run(args);
    }

    private void run(String[] args) {
        if (args.length < 3) {
            System.err.println("Too few arguments, need input file, output file and number of generations");
            System.exit(1);
        }

        String input = args[0];
        String output = args[1];
        int generations = Integer.parseInt(args[2]);

        int[][] grid = loadGrid(input);
        for (int i = 1; i <= generations; i++) {
            grid = nextGeneration(grid);
        }
        saveGrid(grid, output);
    }

    static int[][] nextGeneration(int[][] grid) {
        int[][] future = new int[M][N];
        // Loop through every cell
        for (int l = 0; l < M; l++) {
            for (int m = 0; m < N; m++) {
                applyRules(grid, future, l, m, getAliveNeighbours(grid, l, m));
            }
        }
        return future;
    }

    private static void applyRules(int[][] grid, int[][] future, int l, int m, int aliveNeighbours) {
        // Implementing the Rules of Life

        if ((grid[l][m] == 1) && (aliveNeighbours < 2)) {
            // Cell is lonely and dies
            future[l][m] = 0;
        } else if ((grid[l][m] == 1) && (aliveNeighbours > 3)) {
            // Cell dies due to over population
            future[l][m] = 0;
        } else if ((grid[l][m] == 0) && (aliveNeighbours == 3)) {
            // A new cell is born
            future[l][m] = 1;
        } else {
            // Remains the same
            future[l][m] = grid[l][m];
        }
    }

    private static int getAliveNeighbours(int[][] grid, int l, int m) {
        // finding no Of Neighbours that are alive
        int aliveNeighbours = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if ((l + i >= 0 && l + i < M) && (m + j >= 0 && m + j < N)) {
                    aliveNeighbours += grid[l + i][m + j];
                }
            }
        }
        // The cell needs to be subtracted from
        // its neighbours as it was counted before
        aliveNeighbours -= grid[l][m];
        return aliveNeighbours;
    }

    private static void saveGrid(int[][] grid, String output) {
        try (FileWriter myWriter = new FileWriter(output)) {
            for (int i = 0; i < M; i++) {
                for (int j = 0; j < N; j++) {
                    if (grid[i][j] == 0)
                        myWriter.write(".");
                    else
                        myWriter.write("*");
                }
                myWriter.write(System.lineSeparator());
            }
        } catch (Exception e) {
            throw new IllegalStateException();
        }
    }

    private static int[][] loadGrid(String input) {
        try (BufferedReader reader = new BufferedReader(new FileReader(input))) {
            int[][] grid = new int[M][N];
            for (int i = 0; i < M; i++) {
                String line = reader.readLine();
                for (int j = 0; j < N; j++) {
                    if (line.charAt(j) == '*') {
                        grid[i][j] = 1;
                    } else {
                        grid[i][j] = 0;
                    }
                }
            }
            return grid;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
```

We are interested in 3 run-time metrics this application:
- Elapsed time - A proxy for the performance of the application i.e. how well the application was optimized.
- CPU Usage - A proxy for the overhead of the runtime (since our application if fully single-threaded, CPU usage over 100% is a proxy for overhead)
- Max RSS size - A proxy for the memory usage of the application and runtime

We will run the same application in three different ways:
- GraalVM as a JVM
- GraalVM native-image without PGO 
- GraalVM native-image with PGO 

### Build instructions

Assuming that an enviroment variable `GRAALVM_HOME` points to an instalation of GraalVM we can do

```
$ $GRAALVM_HOME/bin/java -version
    java version "21.0.1" 2023-10-17
    Java(TM) SE Runtime Environment Oracle GraalVM 21.0.1+12.1 (build 21.0.1+12-jvmci-23.1-b19)
    Java HotSpot(TM) 64-Bit Server VM Oracle GraalVM 21.0.1+12.1 (build 21.0.1+12-jvmci-23.1-b19, mixed mode, sharing)
```

To confirm the enviroment variable is set up correctly to a GraalVM version we expect.

Our first step is to compile our `.java` file to a class file.

```
$ $GRAALVM_HOME/bin/javac GameOfLife.java
```

This is all we need to do to run the application on the JVM, but we also want to build a native image of the application, as follows.

```
$ $GRAALVM_HOME/bin/native-image -cp . GameOfLife -o gameoflife-default
	========================================================================================================================
	GraalVM Native Image: Generating 'gameoflife-default' (executable)...
	========================================================================================================================
	For detailed information and explanations on the build output, visit:
	https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/BuildOutput.md
	------------------------------------------------------------------------------------------------------------------------
	[1/8] Initializing...                                                                                    (3.5s @ 0.14GB)
	 Java version: 21.0.1+12, vendor version: Oracle GraalVM 21.0.1+12.1
	 Graal compiler: optimization level: 2, target machine: x86-64-v3, PGO: ML-inferred
	...
```

Now we can move on to building a PGO enabled native image.
As outlined before, the first step is to build an instrumented image that will produce a profile for the run-time behaviour of our application.
We do this by adding the `--pgo-instrumented` flag to the native image command as shown bellow.

```
$ $GRAALVM_HOME/bin/native-image -cp . GameOfLife -o gameoflife-instrumented --pgo-instrument
	========================================================================================================================
	GraalVM Native Image: Generating 'gameoflife-instrumented' (executable)...
	========================================================================================================================
	For detailed information and explanations on the build output, visit:
	https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/BuildOutput.md
	------------------------------------------------------------------------------------------------------------------------
	[1/8] Initializing...                                                                                    (3.6s @ 0.14GB)
	 Java version: 21.0.1+12, vendor version: Oracle GraalVM 21.0.1+12.1
	 Graal compiler: optimization level: 2, target machine: x86-64-v3, PGO: instrument
	...
```

This will result in the `gameoflife-instrumented` executable which is in fact the instrumented build of out application.
It will do everything our application normally does, 
but just before exiting it will produce a `.iprof` file which is the format native image uses to store the run-time profiles.
By default the instrumented build will store the profiles into `default.iprof` but we can specify the exact name/path of the iprof file where we want the profiles saved.
We do this by specifying the `-XX:ProfilesDumpFile` argument when launching the instrumented build of the application.
Bellow we see how we run the instrumented build of the application, specifying we want the profile in the `gameoflife.iprof` file.
We also provide the standard expected inputs to the application - the initial state of the world (`input.txt`), 
where we want the final state of the world (`output.txt`) and how many iterations of the simulation we want (in this case `10`).

```
$ ./gameoflife-instrumented -XX:ProfilesDumpFile=gameoflife.iprof input.txt output.txt 10
```

Once this run has finished we have a run-time profile of our application contained in the `gameoflife.iprof` file.
This enables us to finally build the optimized build of the application, 
by providing the run-time profile of the application using the `--pgo` flag as shown bellow.


```
$ $GRAALVM_HOME/bin/native-image -cp . GameOfLife -o gameoflife-pgo --pgo=gameoflife.iprof
	========================================================================================================================
	GraalVM Native Image: Generating 'gameoflife-pgo' (executable)...
	========================================================================================================================
	For detailed information and explanations on the build output, visit:
	https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/BuildOutput.md
	------------------------------------------------------------------------------------------------------------------------
	[1/8] Initializing...                                                                                    (3.6s @ 0.14GB)
	 Java version: 21.0.1+12, vendor version: Oracle GraalVM 21.0.1+12.1
	 Graal compiler: optimization level: 3, target machine: x86-64-v3, PGO: user-provided
	...
```

With all this in place we can finally move on the evaluating the run-time metrics of our application running in the different modes.


### Evaluation

We will run our application in all three ways (JVM, no PGO native image, PGO native image) using the same inputs.
Note that we will be running the JIT mode using the serial garbage collector, as this is the default GC for native image.
This also ensures that the application runs fully single-threaded allowing us to approximate the CPU usage overhead in JIT mode.
We will also increase the order of magnitude for the number of iterations to get a feel of how the length of the run time impacts the strengths and weaknesses of each execution mode.
We measure all 3 of our metrics using the Linux `time` command with a custom output format (`--format=>> Elapsed: %es, CPU Usage: %P, MAX RSS: %MkB`).
Note: We fixed the CPU clock 2.5GHz during all the measurements in an attempt to minimize noise.


#### 1 iteration

The commands and output of or application in all three modes is shown bellow.

```
$ time  $GRAALVM_HOME/bin/java -XX:+UseSerialGC GameOfLife input.txt output.txt 1
	>> Elapsed: 1.43s, CPU Usage: 185%, MAX RSS: 569424kB


$ time  ./gameoflife-default input.txt output.txt 1
	>> Elapsed: 1.67s, CPU Usage: 99%, MAX RSS: 211804kB


$ time  ./gameoflife-pgo input.txt output.txt 1
	>> Elapsed: 1.00s, CPU Usage: 99%, MAX RSS: 209868kB
```

Looking at the elapsed time we see that running on the JVM is only slightly faster than the default native-image build, but the PGO build is substantially faster in terms of precentage.
With that in mind the half a second of difference does not have a huge imact for a single run of this application, 
but if this was a serverless application that would execute frequently the cumulative performance gain would start to add up.

The CPU Usage tells another interesting story - both native image builds have a 99% CPU usage, while the JVM reports 185%.
This is primarily because the JIT compiler runs in a separate thread and compiles hot code at run time.
Since we are running only one iteration and the JIT compiled code is discontinued once the application finishes - the extra CPU usage goes to waste.
Similarly, the max RSS size of both native image builds is roughly 200MB, while the same application running on the JVM has a max RSS of more than 500MB.
This is in partly because of the memory used by the JIT compiler (including the run-time profiles which are a small part of that memory).

#### 10 Iterations

Let's repeat the same thing increasing the iteration count to 10.
The commands and output follow.

```
$ time  $GRAALVM_HOME/bin/java -XX:+UseSerialGC GameOfLife input.txt output.txt 10
	>> Elapsed: 2.56s, CPU Usage: 156%, MAX RSS: 695796kB


$ time  ./gameoflife-default input.txt output.txt 10
	>> Elapsed: 3.92s, CPU Usage: 99%, MAX RSS: 430728kB


$ time  ./gameoflife-pgo input.txt output.txt 10
	>> Elapsed: 2.33s, CPU Usage: 99%, MAX RSS: 452812kB
```

Similarly to the one iteration example, we can see that running on the JVM is still faster than the default build of native image, while consuming more resources (CPU Usage and max RSS).
The PGO build of the application manages to just beat the JVM in elapsed time, while consuming significantly less resources.
It also clearly outperforms the default native image build.

#### 100 Iterations

We now move on to running our application for 100 iterations.
Same as before, the executed commands and the time output is shown bellow.

```
$ time  $GRAALVM_HOME/bin/java -XX:+UseSerialGC GameOfLife input.txt output.txt 100
	>> Elapsed: 8.78s, CPU Usage: 116%, MAX RSS: 1203196kB


$ time  ./gameoflife-default input.txt output.txt 100
	>> Elapsed: 24.13s, CPU Usage: 99%, MAX RSS: 796288kB


$ time  ./gameoflife-pgo input.txt output.txt 100
	>> Elapsed: 13.39s, CPU Usage: 99%, MAX RSS: 896060kB
```

We can now see that the story changes slightly.
One hunderd iterations on the JVM actually takes less time than both the native image builds.
This is because the application is running long enough that the effort the JVM invests in profiling and optimizing the code 
(including speculative optimizations that AOT compilers can't do) starts paying off.
On the other hand, the max RSS of the JVM is still significantly higher than both native image builds, and the CPU usage is higher.

But the more interesting part of this comparison is comparing at the default native image build and the PGO native image build.
In all three of our example runs (1, 10 and 100 iterations) the PGO build outperformes the default native image build significantly.
The amount of improvement that PGO provides in this case is offcourse not representative of the PGO gains for real world applications,
since our Game Of Life application is small and does exactly one thing so the profiles provded are based on the exact same workload we are measuring.
But it illustrates the general point - 
profile guided optimizations allow AOT compilers to perform similar tricks that JIT compilers can do in order to improve the performance of the code they generate.


#### Image size

As a bonus perk of using PGO for our native-image build let's look at the size of the executable of the default build as well as the PGO build of our application.
We will use the Linux `du` command as shown bellow.

```
$ du -hs gameoflife-default
	7.9M	gameoflife-default


$ du -hs gameoflife-pgo
	6.7M	gameoflife-pgo
```

As we can see, the PGO build produces a ~15% smaller binary than the default build.
Recall that the PGO version outperformed the default version for all three iterations counts we tested with.
Recall also that certain optimizations, such as function inlining we mentioned, increase the binary size in order to improve performance.
So how can PGO builds produce smaller but higher-performing binaries?

This is because the profiles we provided for the optimizing build allow the compiler to differentiate between code important for performance 
(i.e. hot code, code where most of the time is spent during run time) 
and code not important for performance (i.e. cold code, code where we don't spend a lot of time during run time, e.g. error handling).
With this differentiation available the compiler can decide to focus more heavily on optimizing the hot code and less or not at all on the cold code.
This is a very similar idea to what a JVM does - identifies the hot parts of the code at run time and compile those parts at run time.
The main difference is that native image PGO does the profiling and the optimizing ahead-of-time.

## Conclusion

In this text we presented an overview of the main ideas behind Profile Guided Optimization (PGO) with a special focus on the implementation of PGO for Native Image.
We've discussed how recoding the behaviour of an application at run time (i.e. Profiling) and storing this information for later use 
(i.e. The profile stored in an `.iprof` file for native image) can enable an Ahead-of-time compiler to have access to information it normally does not have.
This information can be used to guide decision making in the compiler which can result in better performance as well as smaller binaries.
We illustrated the benefits of PGO on a toy Game Of Life example,
and discussed the comparison with running the application on a JVM which requires more resources to profile and optimize and run time.

It is also important to note that PGO is not a trivial technique to use.
This is because PGO, in order to be beneficial, requires executing the instrumented build with realistic workloads, which is not always trivial to achieve.
So bear in mind that PGO is only as good as the profiles provided to the optimizing build.
This means that profile gathered on a counter-productive workload could be counter-productive, 
and realistic workloads covering a part of the application's functionality will likely give less performance gain compared to realistic workloads with better coverage.
