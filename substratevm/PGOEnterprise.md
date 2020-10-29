# Profile-Guided Optimizations

GraalVM Enterprise allows to apply profile-guided optimizations (PGO) for additional performance gain and higher throughput of native images.
With PGO you can collect the profiling data in advance and then feed it to the native image builder, which will use this information to optimize the performance of the resulting binary.

Note: This feature is available with **GraalVM Enterprise** only.

Here is how you can build an optimized native image, using the _OptimizedImage.java_ example program.

1&#46; Save this Java program that iterates over `ArrayList` using a lambda expression to a file and compile it:
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
```shell
javac OptimizedImage.java
```

2&#46; Build an instrumented native image by appending the `--pgo-instrument` option, whose execution will collect the code-execution-frequency profiles:
```shell
native-image --pgo-instrument OptimizedImage
```

3&#46; Run this instrumented image, saving the result in a _profile.iprof_ file, if nothing else is specified:
```shell
./optimizedimage
```

4&#46; Lastly, create the second native image by specifying the path to the _profile.iprof_ file and execute it.
```shell
native-image --pgo=profile.iprof OptimizedImage
./optimizedimage
```

You can collect multiple profile files, by specifying different names, and add them to the image build.
