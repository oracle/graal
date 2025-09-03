---
layout: ni-docs
toc_group: how-to-guides
link_title: Optimize a Native Executable with PGO
permalink: /reference-manual/native-image/guides/optimize-native-executable-with-pgo/
redirect_from: /reference-manual/native-image/PGO/
---

# Optimize a Native Executable with Profile-Guided Optimization

GraalVM Native Image offers quick startup and less memory consumption for a Java application, running as a native executable, by default. 
You can optimize this native executable even more for additional performance gain and higher throughput by applying Profile-Guided Optimization (PGO).

With PGO you can collect the profiling data in advance, and then feed it to the `native-image` tool, which will use this information to optimize the performance of a native application.
The general workflow is:
1. Build an instrumented native executable by passing the `--pgo-instrument` option to `native-image`. 
2. Run the instrumented executable to generate a profile file. By default, the _default.iprof_ file is generated in the current working directory and on application shutdown.
3. Build an optimized executable. The profile file with the default name and location will be picked up automatically. Alternatively, you can pass it to the `native-image` builder by specifying the file path: `--pgo=myprofile.iprof`.

You can specify where to collect the profiles when running an instrumented native executable by passing the `-XX:ProfilesDumpFile=YourFileName` option at run time. 
You can also collect multiple profile files by specifying different filenames, and pass them to `native-image` at build time.

Note that executing all relevant application code paths and giving the application enough time to collect profiles are essential for having complete profiling information and therefore the best performance.

> Note: PGO is not available in GraalVM Community Edition.

Find more information on this topic in the [Profile-Guided Optimization reference documentation](../PGO.md).

### Run a Demo

For the demo part, you will run a Java application performing queries implemented with the [Java Streams API](https://docs.oracle.com/javase/8/docs/api/java/util/stream/package-summary.html). A user is expected to provide two integer arguments: the number of iterations and the length of the data array. The application creates the data set with a deterministic random seed and iterates 10 times. The time taken for each iteration  and its checksum is printed to the console.

Below is the stream expression to optimize:

```java
Arrays.stream(persons)
   .filter(p -> p.getEmployment() == Employment.EMPLOYED)
   .filter(p -> p.getSalary() > 100_000)
   .mapToInt(Person::getAge)
   .filter(age -> age > 40)
   .average()
   .getAsDouble();
```

Follow these steps to build an optimized native executable using PGO.

### Prerequisite 
Make sure you have installed a GraalVM JDK.
The easiest way to get started is with [SDKMAN!](https://sdkman.io/jdks#graal).
For other installation options, visit the [Downloads section](https://www.graalvm.org/downloads/).

1.  Save [the following code](https://github.com/graalvm/graalvm-demos/blob/master/native-image/optimize-with-pgo/Streams.java) to the file named _Streams.java_:

    ```java
    import java.util.Arrays;
    import java.util.Random;

    public class Streams {

      static final double EMPLOYMENT_RATIO = 0.5;
      static final int MAX_AGE = 100;
      static final int MAX_SALARY = 200_000;

      public static void main(String[] args) {

        int iterations;
        int dataLength;
        try {
          iterations = Integer.valueOf(args[0]);
          dataLength = Integer.valueOf(args[1]);
        } catch (Throwable ex) {
          System.out.println("Expected 2 integer arguments: number of iterations, length of data array");
          return;
        }

        Random random = new Random(42);
        Person[] persons = new Person[dataLength];
        for (int i = 0; i < dataLength; i++) {
          persons[i] = new Person(
              random.nextDouble() >= EMPLOYMENT_RATIO ? Employment.EMPLOYED : Employment.UNEMPLOYED,
              random.nextInt(MAX_SALARY),
              random.nextInt(MAX_AGE));
        }

        long totalTime = 0;
        for (int i = 1; i <= 20; i++) {
          long startTime = System.currentTimeMillis();

          long checksum = benchmark(iterations, persons);

          long iterationTime = System.currentTimeMillis() - startTime;
          totalTime += iterationTime;
          System.out.println("Iteration " + i + " finished in " + iterationTime + " milliseconds with checksum " + Long.toHexString(checksum));
        }
        System.out.println("TOTAL time: " + totalTime);
      }

      static long benchmark(int iterations, Person[] persons) {
        long checksum = 1;
        for (int i = 0; i < iterations; ++i) {
          double result = getValue(persons);

          checksum = checksum * 31 + (long) result;
        }
        return checksum;
      }

      public static double getValue(Person[] persons) {
        return Arrays.stream(persons)
            .filter(p -> p.getEmployment() == Employment.EMPLOYED)
            .filter(p -> p.getSalary() > 100_000)
            .mapToInt(Person::getAge)
            .filter(age -> age >= 40).average()
            .getAsDouble();
      }
    }

    enum Employment {
      EMPLOYED, UNEMPLOYED
    }

    class Person {
      private final Employment employment;
      private final int age;
      private final int salary;

      public Person(Employment employment, int height, int age) {
        this.employment = employment;
        this.salary = height;
        this.age = age;
      }

      public int getSalary() {
        return salary;
      }

      public int getAge() {
        return age;
      }

      public Employment getEmployment() {
        return employment;
      }
    }
    ```

2.  Compile the application:
    ```shell 
    javac Streams.java
    ```
    (Optional) Run the demo application, providing some arguments to observe performance.
    ```shell
    java Streams 100000 200
    ```

3. Build a native executable from the class file, and run it to compare the performance:
    ```shell
    native-image Streams
    ```
    An executable file, _streams_, is created in the current working directory. 
    Now run it with the same arguments to see the performance:
    ```shell
    ./streams 100000 200
    ```
    This version of the program is expected to run slower than on GraalVM's or any regular JDK.

4. Build an instrumented native executable by passing the `--pgo-instrument` option to `native-image`:  
    ```shell
    native-image --pgo-instrument Streams
    ```

5. Run it to collect the code-execution-frequency profiles:
    ```shell
    ./streams 100000 20
    ```

    Notice that you can profile with a much smaller data size.
    Profiles collected from this run are stored by default in the _default.iprof_ file.

6. Finally, build an optimized native executable. The profile file has the default name and location, so it will be picked up automatically:
    ```shell
    native-image --pgo Streams
    ```

7. Run this optimized native executable timing the execution to see the system resources and CPU usage:
    ```
    time ./streams 100000 200
    ```
    You should get the performance comparable to, or faster, than the Java version of the program. For example, on a machine with 16 GB of memory and 8 cores, the `TOTAL time` for 10 iterations reduced from ~2200 to ~270 milliseconds.

This guide showed how you can optimize native executables for additional performance gain and higher throughput.
Oracle GraalVM offers extra benefits for building native executables, such as Profile-Guided Optimization (PGO). 
With PGO you "train" your application for specific workloads and significantly improve the performance.

### Related Documentation

- [Profile-Guided Optimization reference documentation](../PGO.md)
- [Optimize Cloud Native Java Apps with Oracle GraalVM PGO](https://luna.oracle.com/lab/3f0b7c86-6105-4b7a-9a3b-eb73b251a1aa)