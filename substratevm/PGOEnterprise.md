# Profile-Guided Optimizations

GraalVM Enterprise allows to apply profile-guided optimizations (PGO)
for additional performance gain and higher throughput of native images. With
PGO you can collect the profiling data in advance and then feed it to the `native-image` builder, which will use this information to optimize the performance of the resulting binary.

Note: This feature is available with **GraalVM Enterprise** only.

One approach is to gather the execution profiles at one run
and then use them to optimize subsequent compilation(s). In other words, you create a native image with the `--pgo-instrument` option to collect the
profile information. The `--pgo-instrument` builds an instrumented native image
with profile-guided optimization data collected from ahead-of-time compiled code
in the _default.iprof_ file, if nothing else is specified. Then you run an
example program, saving the result in _default.iprof_. Finally, you create a
second native image with a `--pgo profile.iprof` flag that should be significantly
faster. You can collect multiple profile files and add them to the image build.

Here is how you can build an optimized native image following the first approach:

1. Save this Java program that iterates over `ArrayList` using a lambda expression to an _OptimizedImage.java_ file:
```java
import java.util.ArrayList;

class OptimizedImage {
  public static void main(String[] args) {
    ArrayList<String> languages = new ArrayList<>();

    languages.add("JavaScript");
    languages.add("Python");
    languages.add("Ruby");

    System.out.print("ArrayList: ");

    languages.forEach((e) -> {
      System.out.print(e + ", ");
    });
  }
}
```
2. Compile it and build an instrumented native image with the `--pgo-instrument` option:
```
javac OptimizedImage.java
native-image --pgo-instrument OptimizedImage
./optimizedimage
```
2. Build the second native image specifying the path to the _profile.iprof_ file and execute it:
```
native-image --pgo profile.iprof OptimizedImage
./optimizedimage
```

Another approach is to collect profiles while running your
application in just-in-time mode and then use this information to generate
a highly-optimized native binary.

Taking the above Java program, here is how you can build an optimized native image following the second approach:

1. Compile _OptimizedImage.java_ and run it on the JVM with a `-Dgraal.PGOInstrument` flag to gather the profiling information:
```
javac OptimizedImage.java
java -Dgraal.PGOInstrument=optimizedimage.iprof OptimizedImage
```
2. Use the collected data to generate a native image and execute it:
```
native-image --pgo=optimizedimage.iprof OptimizedImage
./optimizedimage
```
