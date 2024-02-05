---
layout: docs
toc_group: pgo
link_title: Basic Usage of Profile-Guided Optimizations
permalink: /reference-manual/native-image/pgo/basic-usage
---

Note: this document builds upon [Profile Guided Optimization for Native Image](PGO.md), so we recommend that you read that page first.

# A Game Of Life example

To understand the usage of PGO in the context of GraalVM native image, let's consider an example application.
This application is an implementation of Conway's Game of Life simulation (https://en.wikipedia.org/wiki/Conway%27s_Game_of_Life) on a 4000 by 4000 grid.
Please note that this a very simple and not-illustrative-of-the-real-world application, but it should serve well as a running example.
The application takes as input a file specifying the inital state of the world,
a file path to output the final state of the world to, and an integer declaring how many iterations of the simulation to run.

The entire source code of the application follows, and it's here as a reference for reproduction of results.
Feel free to skip past it, as there is no need to understand it in detail for now.

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
        for (int l = 0; l < M; l++) {
            for (int m = 0; m < N; m++) {
                applyRules(grid, future, l, m, getAliveNeighbours(grid, l, m));
            }
        }
        return future;
    }

    private static void applyRules(int[][] grid, int[][] future, int l, int m, int aliveNeighbours) {
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
        int aliveNeighbours = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if ((l + i >= 0 && l + i < M) && (m + j >= 0 && m + j < N)) {
                    aliveNeighbours += grid[l + i][m + j];
                }
            }
        }
        // The cell needs to be subtracted from its neighbours as it was counted before
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

We are interested in elapsed time as a measurement for the performance of the application i.e. how well the application was optimized.
The assumption is that the better the optimizations applied to the application the less time the application will take to complete a workload.
We will run the same application in two different ways GraalVM Native Image without PGO and GraalVM Native Image with PGO.

## Build instructions

Assuming that an enviroment variable `GRAALVM_HOME` points to an instalation of GraalVM we can do

```
$ $GRAALVM_HOME/bin/java -version
    java version "21.0.1" 2023-10-17
    Java(TM) SE Runtime Environment Oracle GraalVM 21.0.1+12.1 (build 21.0.1+12-jvmci-23.1-b19)
    Java HotSpot(TM) 64-Bit Server VM Oracle GraalVM 21.0.1+12.1 (build 21.0.1+12-jvmci-23.1-b19, mixed mode, sharing)
```

To confirm the environment variable is set up correctly to a GraalVM version we expect.

Our first step is to compile our `.java` file to a class file.

```
$ $GRAALVM_HOME/bin/javac GameOfLife.java
```

We also need to build a native image of the application, as follows.

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
We do this by adding the `--pgo-instrumented` flag to the native image command as shown below.

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
but just before exiting it will produce a file with the `.iprof` extension, which is the format native image uses to store the run-time profiles.
By default the instrumented build will store the profiles into `default.iprof` but we can specify the exact name/path of the iprof file where we want the profiles saved.
We do this by specifying the `-XX:ProfilesDumpFile` argument when launching the instrumented build of the application.
Below we see how we run the instrumented build of the application, specifying that we want the profile in the `gameoflife.iprof` file.
We also provide the standard expected inputs to the application - the initial state of the world (`input.txt`),
where we want the final state of the world (`output.txt`) and how many iterations of the simulation we want (in this case `10`).

```
$ ./gameoflife-instrumented -XX:ProfilesDumpFile=gameoflife.iprof input.txt output.txt 10
```

Once this run finishes, we have a run-time profile of our application contained in the `gameoflife.iprof` file.
This enables us to finally build the optimized build of the application,
by providing the run-time profile of the application using the `--pgo` flag as shown below.

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

With all this in place we can finally move on the evaluating the run-time performance of our application running in the different modes.

## Evaluation

We will run both of our application executables using the same inputs.
We measure our elapsed time sing the Linux `time` command with a custom output format (`--format=>> Elapsed: %es`).
Note: We fixed the CPU clock 2.5GHz during all the measurements to minimize noise and improve reproducibility.

## 1 iteration

The commands and output of both our application builds is shown below.

```
$ time  ./gameoflife-default input.txt output.txt 1
    >> Elapsed: 1.67s

$ time  ./gameoflife-pgo input.txt output.txt 1
    >> Elapsed: 0.97s
```

Looking at the elapsed time, we see that running the PGO image is substantially faster in terms of percentage.
With that in mind the half a second of difference does not have a huge impact for a single run of this application,
but if this was a serverless application that executes frequently, then the cumulative performance gain would start to add up.

## 100 Iterations

We now move on to running our application for 100 iterations.
Same as before, the executed commands and the time output is shown below.

```
$ time  ./gameoflife-default input.txt output.txt 100
    >> Elapsed: 24.02s

$ time  ./gameoflife-pgo input.txt output.txt 100
    >> Elapsed: 13.25s
```

In both of our example runs (1 and 100 iterations), the PGO build outperforms the default native-image build significantly.
The amount of improvement that PGO provides in this case is of course not representative of the PGO gains for real world applications,
since our Game Of Life application is small and does exactly one thing so the profiles provided are based on the exact same workload we are measuring.
But it illustrates the general point -
profile guided optimizations allow AOT compilers to perform similar tricks that JIT compilers can do in order to improve the performance of the code they generate.

## Image size

As a bonus perk of using PGO for our native-image build, let's look at the size of the executable of the default image as well as the PGO image of our application
We will use the Linux `du` command as shown below.

```
$ du -hs gameoflife-default
    7.9M    gameoflife-default

$ du -hs gameoflife-pgo
    6.7M    gameoflife-pgo
```

As we can see, the PGO build produces a ~15% smaller binary than the default build.
Recall that the PGO version outperformed the default version for both iteration counts we tested with.
Recall also that certain optimizations, such as function inlining that was mentioned earlier, increase the binary size in order to improve performance.
So how can PGO builds produce smaller but better-performing binaries?

This is because the profiles we provided for the optimizing build allow the compiler to differentiate between code important for performance
(i.e. hot code, code where most of the time is spent during run time)
and code that is not important for performance (i.e. cold code, code where we do not spend a lot of time during run time, such as error handling).
With this differentiation available, the compiler can decide to focus more heavily on optimizing the hot code and less or not at all on the cold code.
This is a very similar idea to what a JVM does - identifies the hot parts of the code at run time and compile those parts at run time.
The main difference is that native image PGO does the profiling and the optimizing ahead-of-time.

# Conclusion

In this text we presented an overview of the main ideas behind Profile-Guided Optimization (PGO) with a special focus on the implementation of PGO for Native Image.
We discussed how recording the behaviour of an application at run time (i.e. Profiling) and storing this information for later use
(i.e. the profile stored in an `.iprof` file for native image) can enable an ahead-of-time compiler to have access to information that it normally does not have.
This information can be used to guide decision making in the compiler, which can result in better performance as well as smaller binaries.
We illustrated the benefits of PGO on a toy Game-of-Life example.

It is also important to note that PGO is not a trivial technique to use.
This is because PGO, in order to be beneficial, requires executing the instrumented build with realistic workloads.
So bear in mind that PGO is only as good as the profiles provided to the optimizing build.
This means that profile gathered on a counter-productive workload could be counter-productive,
and workloads that cover only a part of the application's functionality will likely yield a smaller performance gain compared to a realistic workload with a better coverage.

