# Memory Management

This page looks at memory management models available for native images.

## Memory Management at Image Run Time

A native image, when being executed, does not run on a JVM, but on a runtime
system provided with GraalVM. That runtime includes all necessary components,
like memory management, thread scheduling, etc. The memory management is handled
by GraalVM's garbage collector implementation -- Serial Garbage Collector
(Serial GC). Serial GC is default for Native Image in GraalVM Enterprise and
Community Editions.

When running an executable, it will, by default, use up to
`-H:MaximumHeapSizePercent` of the physical memory size. For example, with the
default value of 80%, and on a machine with 4GB of RAM, it will at most use
3.2GB of RAM. If the same image is executed on a machine that has 32GB of RAM,
it will use up to 25.6GB.

To set options to a native image, use the `-XX` switch. For example, to get some information on garbage collection at run time, run:
```shell
./helloworld -XX:+PrintGC -XX:+VerboseGC
```

In general, there are different ways for specifying the heap size that a native image can use at run time:
* Passing the arguments `-Xms`, `-Xmx`, and/or `-Xmn` when executing the native image, e.g., `./helloworld -Xms2m -Xmx10m -Xmn1m`. This always has the highest priority and overrides any other configured values.
  * `-Xmx` - maximum heap size in bytes. Note that this is not the maximum amount of consumed memory, because during GC the system can request more temporary memory. The value is specified in bytes, suffix `k`, `m`, or `g` can be used for scaling.
  * `-Xms` - minimum heap size in bytes. Heap space that is unused will be retained for future heap usage, rather than being returned to the operating system.
  * `-Xmn` - the size of the young generation in bytes (the amount of memory that can be allocated without triggering a GC).
* Setting an absolute default heap size at image build time: `native-image -R:MinHeapSize=2m -R:MaxHeapSize=10m -R:MaxNewSize=1m HelloWorld`.
* Setting a relative default heap size at image build time: `native-image -R:MaximumHeapSizePercent=80 -R:MaximumYoungGenerationSizePercent=10 HelloWorld`. This variant is used as the fallback if the user does not specify anything during the image build, nor when executing the built image.

As of version 20.1.0 option `-XX:MaxDirectMemorySize=...` was added to allow
controlling the maximum size of direct buffer allocations. Check other related options to the native image builder from the `native-image --expert-options-all` list.

## G1 Garbage Collector Integration

GraalVM Enterprise Edition also offers even more efficient, lower latency garbage collector -- G1 Garbage Collector (G1 GC).
It is integrated from the Java HotSpot's G1. This garbage collection implementation
improves the performance of native images by reducing the stop-the-world pauses.
To enable it, pass the `-H:+UseLowLatencyGC` option to the native image builder, e.g.:
```shell
native-image -H:+UseLowLatencyGC HelloWorld
```
Note: The `-H:+UseLowLatencyGC` option will be depricated in GraalVM  20.3 version in favour of `--gc=G1`.

Currently, the G1 GC can only be used in native images built on Linux for AMD64.
Note: The G1 GC integration is available with **GraalVM Enterprise** only and is experimental.
