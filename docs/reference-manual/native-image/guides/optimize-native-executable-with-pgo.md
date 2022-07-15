---
layout: ni-docs
toc_group: how-to-guides
link_title: Optimize a Native Executable with PGO
permalink: /reference-manual/native-image/guides/optimize-native-executable-with-pgo/
---

# Optimize a Native Executable with Profile-Guided Optimizations

GraalVM Native Image offers quick startup and less memory consumption for a Java application, running as a native executable, by default. 
You can optimize this native executable even more for additional performance gain and higher throughput by applying Profile-Guided Optimizations (PGO).

With PGO you can collect the profiling data in advance and then feed it to the `native-image` tool, which will use this information to optimize the performance of the resulting binary.

> Note: PGO is available with GraalVM Enterprise Edition only.

This guide shows how to apply PGO and transform your Java application into an optimized native executable.

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

1.  Save [the following code](https://github.com/graalvm/graalvm-demos/blob/master/streams/Streams.java) to the file named _Streams.java_:

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
    $JAVA_HOME/bin/javac Streams.java
    ```
    (Optional) Run the demo application, providing some arguments to observe performance.
    ```shell
    $JAVA_HOME/bin/java Streams 100000 200
    ```

3. Build a native executable from the class file, and run it to compare the performance:
    ```shell
    $JAVA_HOME/bin/native-image Streams
    ```
    An executable file, `streams`, is created in the current working directory. 
    Now run it with the same arguments to see the performance:

    ```shell
    ./streams 100000 200
    ```
    This version of the program is expected to run slower than on GraalVM's or any regular JDK.
    

4. Build an instrumented native executable by passing the `--pgo-instrument` option to `native-image`:
    
    ```shell
    $JAVA_HOME/bin/native-image --pgo-instrument Streams
    ```
5. Run it to collect the code-execution-frequency profiles:

    ```shell
    ./streams 100000 20
    ```

    Notice that you can profile with a much smaller data size.
    Profiles collected from this run are stored by default in the _default.iprof_ file.

   > Note: You can specify where to collect the profiles when running an instrumented native executable by passing the `-XX:ProfilesDumpFile=YourFileName` option at run time. 

6. Finally, build an optimized native executable by specifying the path to the collected profiles:

    ```shell
    $JAVA_HOME/bin/native-image --pgo=default.iprof Streams
    ```
  > Note: You can also collect multiple profile files, by specifying different filenames, and pass them to the `native-image` tool at build time.

    Run this optimized native executable timing the execution to see the system resources and CPU usage:
    ```
    time ./streams 100000 200
    ```
    You should get the performance comparable to, or faster, than the Java version of the program. For example, on a machine with 16 GB of memory and 8 cores, the `TOTAL time` for 10 iterations reduced from ~2200 to ~270 milliseconds.

This guide showed how you can optimize native executables for additional performance gain and higher throughput.
GraalVM Enterprise Edition offers extra benefits for building native executables, such as  Profile-Guided Optimizations (PGO). 
With PGO you "train" your application for specific workloads and significantly improve the performance.

### Related Documentation

- [Improving performance of GraalVM native images with profile-guided optimizations](https://medium.com/graalvm/improving-performance-of-graalvm-native-images-with-profile-guided-optimizations-9c431a834edb)