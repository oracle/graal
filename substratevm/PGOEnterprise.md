# Profile-Guided Optimizations

GraalVM Enterprise allows to apply profile-guided optimizations (PGO)
for additional performance gain and higher throughput of native images. With
PGO you can collect the profiling data in advance and then feed it to the `native-image` builder, which will use this information to optimize the performance of the resulting binary.

Note: This feature is available with **GraalVM Enterprise** only.

One approach is to gather the execution profiles at one run
and then use them to optimize subsequent compilation(s). In other words, you create a native image with the `--pgo-instrument` option to collect the
profile information. The `--pgo-instrument` builds an instrumented native image
with profile-guided optimization data collected of AOT compiled code
in the _default.iprof_ file, if nothing else is specified. Then you run an
example program, saving the result in _default.iprof_. Finally, you create a
second native image with `--pgo profile.iprof` flag that should be significantly
faster. You can collect multiple profile files and add them to the image build.

1. Compile a Java program, build a native image of it with the `--pgo-instrument` option to collect the
profiling information and run the resulting binary:
```
javac MyClass.java
native-image --pgo-instrument MyClass
./myclass
```
2. Build the second native image specifying the path to the _profile.iprof_ file containing profile-guided optimization data and run the binary:
```
native-image --pgo profile.iprof MyClass
./myclass
```

Another approach is to collect profiles while running your
application in JIT mode and then use this information to generate
a highly-optimized native binary.

1. Run a Java program in JIT mode with a `-Dgraal.PGOInstrument` flag to gather the profiling information:
```
java -Dgraal.PGOInstrument=myclass.iprof MyClass
```
2. Use the collected data to generate a native image:
```
native-image --pgo=myclass.iprof MyClass
```
3. Run the resulting binary:
```
./myclass
```
