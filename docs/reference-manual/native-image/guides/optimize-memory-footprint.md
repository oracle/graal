---
layout: ni-docs
toc_group: how-to-guides
link_title: Optimize Memory Footprint of a Native Executable
permalink: /reference-manual/native-image/guides/optimize-memory-footprint/
---

# Optimize Memory Footprint of a Native Executable

Choosing an appropriate garbage collector and tailoring the garbage collection configuration can reduce GC times and memory footprint.
When running a native image, Java heap settings are determined based on the system configuration and GC.
You can override the default configuration to further improve your use case on the relevant metrics.

This guide demonstrates how to optimize an application in the area of memory consumption and trade off between GC pause times, memory footprint, and performance.

### Prerequisite 

Make sure you have installed Oracle GraalVM for JDK 23 or later.
The easiest way to get started is with [SDKMAN!](https://sdkman.io/jdks#graal){:target="_blank"}.
For other installation options, visit the [Downloads](https://www.graalvm.org/downloads/){:target="_blank"} section.

## 1. Prepare the Application 

A Java application that does some heavy text processing, like log analysis, where large strings are frequently concatenated, split, or manipulated, is a good approach to stress test the garbage collector.

The application you will use generates a significant number of temporary strings putting pressure on the GC.

1. Save the following Java code in a file named _StringManipulation.java_:
    ```java
    import java.util.ArrayDeque;

    public class StringManipulation {

        public static void main(String[] args) {
            System.out.println("Starting string manipulation GC stress test...");

            // Parse arguments
            int iterations = 1000000;
            int numKeptAliveObjects = 100000;
            if (args.length > 0) {
                iterations = Integer.parseInt(args[0]);
            }
            if (args.length > 1) {
                numKeptAliveObjects = Integer.parseInt(args[1]);
            }

            ArrayDeque<String[]> aliveData = new ArrayDeque<String[]>(numKeptAliveObjects + 1);
            for (int i = 0; i < iterations; i++) {
                // Simulate log entry generation and log entry splitting. The last n entries are kept in memory.
                String base = "log-entry";
                StringBuilder builder = new StringBuilder(base);

                for (int j = 0; j < 100; j++) {
                    builder.append("-").append(System.nanoTime());
                }

                String logEntry = builder.toString();
                String[] parts = logEntry.split("-");

                aliveData.addLast(parts);
                if (aliveData.size() > numKeptAliveObjects) {
                    aliveData.removeFirst();
                }

                // Periodically log progress
                if (i % 100000 == 0) {
                    System.out.println("Processed " + i + " log entries");
                }
            }

            System.out.println("String manipulation GC stress test completed: " + aliveData.hashCode());
        }
    }
    ```
    At run time, you specify on the command line how long this application should run (the 1st argument, number of iterations) and how much memory it should keep alive (the 2nd argument).

2. Compile and run the application on HotSpot, timing the results:
    ```bash
    javac StringManipulation.java
    ```
    ```bash
    /usr/bin/time java StringManipulation 500000 50000
    ```

    On a machine with 48GB of memory, 8 CPUs, and the default G1 GC on HotSpot, the results should be similar, displaying the user and elapsed time, system CPU usage, and the maximum memory usage required to execute this request:
    ```
    Starting string manipulation GC stress test...
    Processed 0 log entries
    Processed 100000 log entries
    Processed 200000 log entries
    Processed 300000 log entries
    Processed 400000 log entries
    String manipulation GC stress test completed: 1791741888
    6.61user 0.57system 0:03.35elapsed 214%CPU (0avgtext+0avgdata 4046128maxresident)k
    0inputs+64outputs (8major+39776minor)pagefaults 0swaps
    ```
    The results show a wall-clock time of 3.35 seconds, a total CPU time of 6.61 seconds + 0.57 seconds (indicating actual CPU usage), and a maximum memory usage of 3.85GB (resident set size, RSS).

## 2. Build a Native Image with Default GC

Now compile this application ahead of time with the default garbage collector in Native Image which is [Serial GC](../MemoryManagement.md#serial-garbage-collector).
Serial GC is a non-parallel, stop and copy GC optimized for low memory footprint and small Java heap sizes.

1. Build with `native-image`:
    ```bash
    native-image -o testgc-serial StringManipulation
    ```
    The `-o` option defines the name of the output file to be generated.

    The build output prints the GC information at the [Initialization stage](../BuildOutput.md#build-stages) which is:
    ```
    [1/8] Initializing...
    ...
    Garbage collector: Serial GC (max heap size: 80% of RAM)
    ...
    ```

2. Run the native executable with the same arguments, timing the results:
    ```bash
    /usr/bin/time ./testgc-serial 500000 50000
    ```
    The resources usage is now different:
    ```
    Starting string manipulation GC stress test...
    ...
    8.82user 1.24system 0:10.10elapsed 99%CPU (0avgtext+0avgdata 611272maxresident)k
    0inputs+0outputs (0major+854664minor)pagefaults 0swaps
    ```

    When using the default GC, this benchmark shows higher elapsed time but a lower maximum resident set size compared to the HotSpot run above.

3. Get more insights for this GC by passing `-XX:+PrintGC` at run time to print the logs:
    ```bash
    /usr/bin/time ./testgc-serial 500000 50000 -XX:+PrintGC
    ```
    Notice that pause times are high with Serial GC, which can be a problem for applications where latency is important. For example:
    ```
    [9.301s] GC(55) Pause Full GC (Collect on allocation) 400.19M->214.69M 318.384ms
    ```
    Here, the GC paused the application for 318.384ms.

## 3. Build a Native Image with G1 GC

The next step is to change the garbage collector. 
Native Image supports the [G1 garbage collector](../MemoryManagement.md#g1-garbage-collector) by passing `--gc=G1` to the `native-image` builder. 
G1 GC is a generational, incremental, parallel, mostly concurrent, stop-the-world GC, recommended for improving latency and throughput of the application.

> G1 GC is available with Oracle GraalVM and supported on Linux only.

> We recommend using G1 GC in combination with [Profile-Guided Optimization (PGO)](optimize-native-executable-with-pgo.md) for the best application performance. However, PGO is not applied in this guide to keep the instructions straightforward.

1. Build the second native executable with G1 GC, specifing a different name for the output file, so the executables will not overwrite each other:
    ```bash
    native-image --gc=G1 -o testgc-g1 StringManipulation
    ```

    The build output now prints a different GC information:
    ```
    [1/8] Initializing...
    ...
    Garbage collector: G1 GC (max heap size: 25.0% of RAM)
    ```

2. Run this native executable with the same arguments, passing also `-XX:+PrintGC` to get more insights into pause times, and compare the results:
    ```bash
    /usr/bin/time ./testgc-g1 500000 50000 -XX:+PrintGC
    ```
    ```
    ...
    Processed 300000 log entries
    [2.705s][info][gc] GC(16) Pause Young (Normal) (G1 Evacuation Pause) 2301M->1690M(4840M) 25.144ms
    Processed 400000 log entries
    [3.322s][info][gc] GC(17) Pause Young (Normal) (G1 Evacuation Pause) 2715M->1870M(4840M) 20.364ms
    String manipulation GC stress test completed: 305943342
    5.77user 0.47system 0:03.85elapsed 161%CPU (0avgtext+0avgdata 3707920maxresident)k
    0inputs+0outputs (0major+12980minor)pagefaults 0swaps
    ```

    G1 GC is significantly faster than Serial GC, so the wall-clock time drops from 10.1s to 3.85s.
    Pause times are much better!
    However, memory usage with G1 GC is higher than with Serial GC.
    
    When compared with the HotSpot execution above (which also uses the G1 GC), performance is on the same level, while memory usage is lower (3.68GB versus 3.85GB) because objects are more compact in Native Image than on HotSpot.
    The total CPU time is lower as well.

## 4. Build a Native Image with Epsilon GC

There is one more garbage collector supported by Native Image: [Epsilon GC](../MemoryManagement.md).
Epsilon GC is a no-op garbage collector that does not do any garbage collection and therefore never frees any allocated memory.
The primary use case for this GC are very short running applications that only allocate a small amount of memory.

Epsilon GC should only be used in very specific cases.
We recommend always comparing Epsilon GC against the default GC (Serial GC) to determine if Epsilon GC really provides an actual benefit for your application.

1. To enable the Epsilon GC, pass `--gc=epsilon` at image build time:
    ```bash
    native-image --gc=epsilon -o testgc-epsilon StringManipulation
    ```

    The build output reports about Epsilon GC being used:
    ```
    [1/8] Initializing...
    ...
    Garbage collector: Epsilon GC (max heap size: 80% of RAM)
    ```

2. Run this native image but increase the number of iterations:
    ```bash
    /usr/bin/time ./testgc-epsilon 3200000 50000
    ```
    ```
    Starting string manipulation GC stress test...
    ...
    Processed 3100000 log entries
    Exception in thread "main" 
    Exception: java.lang.OutOfMemoryError thrown from the UncaughtExceptionHandler in thread "main"
    PlatformThreads.ensureCurrentAssigned() failed during shutdown: java.lang.OutOfMemoryError: Could not allocate an aligned heap chunk because the heap address space is exhausted. Consider re-building the image with compressed references disabled ('-H:-UseCompressedReferences').
    Command exited with non-zero status 1
    21.07user 13.11system 0:34.25elapsed 99%CPU (0avgtext+0avgdata 33556824maxresident)k
    0inputs+0outputs (0major+8387698minor)pagefaults 0swaps
    ```
    The `OutOfMemoryError` exception happens because Epsilon GC does not do any garbage collection, and the heap is full at some point.
    You need to reduce how long this application should run.

    The usage results are not comparable to the ones in the previous steps because more work was performed (more iterations).

## 5. Build a Native Image Setting the Maximum Heap Size

By default, a native image will set its maximum Java heap size to 80% of the physical memory when using Serial or Epsilon GC, and to 25% when using G1 GC.
For example, on a machine with 16GB of RAM, the maximum heap size will be set to 12.8GB with Serial or Epsilon GC.
However, if you run on Oracle GraalVM with compressed references support enabled, the maximum Java heap cannot be larger than 32GB.
This information can be found in the output for each build.

To override the default behavior, you can explicitly set the maximum heap size.
There are two ways to do that.

### 5.1. Set the Maximum Heap Size at Run Time

The first and recommended way is to build a native image with the default heap settings, and then override the maximum heap size in bytes at run time using `-Xmx`.
Test this option with both Serial G1 and G1 GC native images.

1. Serial GC:
    ```bash
    /usr/bin/time ./testgc-serial -Xmx512m 500000 50000
    ```
    ```
    Starting string manipulation GC stress test...
    ...
    9.53user 1.40system 0:10.99elapsed 99%CPU (0avgtext+0avgdata 590404maxresident)k
    0inputs+0outputs (0major+953535minor)pagefaults 0swaps
    ```

2. G1 GC:
    ```bash
    /usr/bin/time ./testgc-g1 -Xmx512m 500000 50000
    ```
    ```
    Starting string manipulation GC stress test...
    ...
    14.99user 0.41system 0:05.13elapsed 300%CPU (0avgtext+0avgdata 554004maxresident)k
    0inputs+0outputs (0major+5622minor)pagefaults 0swaps
    ```

### 5.2. Define the Maximum Heap Size at Build Time

The second way is to build a native image and set a new default value for the maximum heap size using the `-R:MaxHeapSize` option.
This default will then be used at run time, unless it is explicitly overridden at run time by passing the `-X...` or `-XX:...` options.

1. Create a new native executable:
    ```bash
    native-image --gc=G1 -R:MaxHeapSize=512m -o testgc-maxheapset-g1 StringManipulation
    ```
    Notice the updated GC information:
    ```
    [1/8] Initializing...
    ...
    Garbage collector: G1 GC (max heap size: 512.00MB)
    ```

2. Run it with the same load:
    ```bash
    /usr/bin/time ./testgc-maxheapset-g1 500000 50000
    ```
    On this test machine, the results should match the previous numbers at step 5.1:
    ```
    Starting string manipulation GC stress test...
    ...
    14.87user 0.44system 0:05.33elapsed 287%CPU (0avgtext+0avgdata 552292maxresident)k
    0inputs+0outputs (0major+5694minor)pagefaults 0swaps
    ```

> Besides `-Xmx`, there are plenty of other GC-specific options that experts can use for performance tuning, for example, `-XX:MaxGCPauseMillis` to set target maximum pause times. Find a full list of performance tuning options in the [reference documentation](../MemoryManagement.md#performance-tuning).

### Summary 

Selecting the right garbage collector and configuring a suitable garbage collection configuration can significantly reduce GC pauses and improve overall application responsiveness.
You can achieve more predictable memory usage, helping your native application run more efficiently under varying workloads.
This guide provides insights into choosing the best GC strategy depending on your application goals: low latency, minimal memory overhead, or optimal performance.

### Related Documentation

- [Memory Management in Native Image](../MemoryManagement.md)