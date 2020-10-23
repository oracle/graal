# Profile-Guided Optimizations

GraalVM Enterprise allows to apply profile-guided optimizations (PGO) for additional performance gain and higher throughput of native images.
With PGO you can collect the profiling data in advance and then feed it to the `native-image` builder, which will use this information to optimize the performance of the resulting binary.

Note: This feature is available with **GraalVM Enterprise** only.

To build an optimized native image, you first need to collect the profiling information.
The `--pgo-instrument` builds an instrumented native image with profile-guided optimization data collected from ahead-of-time compiled code in the _default.iprof_ file, if nothing else is specified.
Then you run this instrumented native image, saving the result in _default.iprof_.
Finally, you create the second native image with a `--pgo profile.iprof` flag that should be significantly faster. You can collect multiple profile files and add them to the image build.

Here is an example of building an optimized native image.

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
