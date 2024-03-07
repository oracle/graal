---
layout: docs
toc_group: pgo
link_title: Basic Usage of Profile-Guided Optimizations
permalink: /reference-manual/native-image/optimizations-and-performance/PGO/basic-usage/
---

# Basic Usage of Profile-Guided Optimization

To explain the usage of PGO in the context of GraalVM Native Image, let's consider the "Game Of Life" example application.
It is an implementation of [Conway's Game of Life simulation](https://en.wikipedia.org/wiki/Conway%27s_Game_of_Life) on a 4000 by 4000 grid.
The application takes as input a file specifying the initial state of the world, a file path to output the final state, and an integer declaring how many iterations of the simulation to run.
Note that this is a not-illustrative-of-the-real-world application, but it should serve well as an example.

Below is the source code of the application, modified from [this resource](https://www.geeksforgeeks.org/program-for-conways-game-of-life/).

```java
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;

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

Application performance is measured in terms of elapsed time.
The assumption is that applying better optimizations to the application results in the application taking less time to complete a workload.
To see the difference in performance, you can run the application in two different ways: first, without PGO and then with PGO.

## Building the Application

The prerequisite is to install Oracle GraalVM. The easiest way to get started is with [SDKMAN!](https://sdkman.io/jdks#graal).
For other installation options, visit the [Downloads section](https://www.graalvm.org/downloads/).

> Note: PGO is not available in GraalVM Community Edition.

The first step is to compile _GameOfLife.java_ to a class file:
```bash
javac GameOfLife.java
```

Next, build a native image of the application, specifying a unique name with the `-o` option:
```bash
native-image -cp . GameOfLife -o gameoflife-default
```

Now you can move on to building a PGO-enabled native image. 
For that, you first need to build an "instrumented binary" that will produce a profile for the runtime behaviour of the application by adding the `--pgo-instrumented` option and specifying a different name, as follows:
```bash
native-image  --pgo-instrument -cp . GameOfLife -o gameoflife-instrumented
```

Now run that instrumented binary to gather profiles. 
By default, just before exiting, it generates a file with the default name _default.iprof_ in the current working directory, but you can specify a different name and path for the profile by passing the `-XX:ProfilesDumpFile` option when running the instrumented binary.
You should also provide the standard expected inputs to the application: the initial state of the world (_input.txt_), a file to which the application will print the final state of the world (_output.txt_), and the desired number of iterations you want (in this case `10`).
```bash
./gameoflife-instrumented -XX:ProfilesDumpFile=gameoflife.iprof input.txt output.txt 10
```

Having a runtime profile of the application contained in the _gameoflife.iprof file_,  you can finally build an optimized native executable by using the `--pgo` option and providing the gathered profile as shown below.
```bash
native-image -cp . GameOfLife -o gameoflife-pgo --pgo=gameoflife.iprof
```

With all this in place, you can move on to the evaluating the runtime performance of the application running in the different modes.

## Evaluating Performance

To evaluate the performance, run both native executables of the application with the same inputs.
You can measure the elapsed time of the executable via the `time` command with a custom output format (`--format=>> Elapsed: %es`).

> Note: The CPU clock is fixed to 2.5GHz during all the measurements to minimize noise and improve reproducibility.

### Running with a Single Iteration

Run the application as shown below, so that it iterates only once:
```bash
time  ./gameoflife-default input.txt output.txt 1
    >> Elapsed: 1.67s

time  ./gameoflife-pgo input.txt output.txt 1
    >> Elapsed: 0.97s
```

Looking at the elapsed time, you can see that running the PGO-optimized native executable is substantially faster in terms of percentage.
With that in mind, the half a second of difference does not have a huge impact for a single run of this application,
but if this was a serverless application that executes frequently, then the cumulative performance gain would start to add up.

### Run with 100 Iterations

Now move on to running the application with 100 iterations. 
Same as before, the executed commands and the time output is shown below:
```bash
time  ./gameoflife-default input.txt output.txt 100
    >> Elapsed: 24.02s

time  ./gameoflife-pgo input.txt output.txt 100
    >> Elapsed: 13.25s
```

In both evaluation runs, the PGO-optimized native executable significantly outperforms the default native build.
The amount of improvement that PGO provides in this case is not representative of the PGO gains for real world applications, since this application is small and does exactly one thing so the profiles provided are based on the exact same workload that is being measured.
However, it illustrates the general point: Profile-Guided Optimization enables an AOT compiler to perform similar optimizations as a JIT compiler.

## Executable Size

Another advantage of using PGO with GraalVM Native Image is the size of the native executable. 
To measure the size of the files, you can use the Linux `du` command as shown below.
```bash
du -hs gameoflife-default
    7.9M    gameoflife-default

du -hs gameoflife-pgo
    6.7M    gameoflife-pgo
```

As you can see, the PGO-optimized native executable is approximately 15% smaller than the default native build.

This is because the profiles provided for the optimizing build allow the compiler to differentiate between which code is important for performance
(i.e., hot code), and which is not important (i.e., cold code, such as error handling).
With this differentiation available, the compiler can decide to focus more heavily on optimizing the hot code and less, or not at all, on the cold code.
This is a similar approach to what a JVM does - identifies the hot parts of the code at run time and compile those parts at run time.
The main difference is that  Native Image PGO does the profiling and the optimizing ahead-of-time.

### Further Reading

* [Inspecting a Profile in a Build Report](PGO-Build-Report.md)
* [Optimize a Native Executable with Profile-Guided Optimization](guides/optimize-native-executable-with-pgo.md)