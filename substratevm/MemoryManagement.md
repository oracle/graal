# Memory Management

This page looks at memory management models availble for Native Image.

## Memory Management at Image Run Time

The memory management when executing a native image is handled by Serial Garbage
Collector (Serial GC) -- GraalVM implementation for memory resources allocation.
Serial GC is default for Native Image in GraalVM Enterprise and Community Editions.

When running the executable, it will, by default, use up to
`-H:MaximumHeapSizePercent` of the physical memory size. For example, with the
default value of 80%, and on a machine with 4GB of RAM, it will at most use
3,2GB of RAM. If the same image is executed on a machine that has 32GB of RAM,
it will use up to 25,6GB.

To set options to a native image, use the `-XX` switch. For example, to get some information on the memory allocation at run time, run:
```
./helloworld -XX:+PrintGC -XX:+VerboseGC
```

In general, there are different ways for specifying the heap size that a native image will use at run time:
* Passing the arguments `-Xms`, `-Xmx`, and/or `-Xmn` when executing the native image, e.g., `./helloworld -Xms2m -Xmx10m -Xmn1m.` This always has the highest priority and overrides any other configured values.
  * `-Xmx` - maximum heap size in bytes. Note that this is not the maximum amount of consumed memory, because during GC the system can request more temporary memory. The value is specified in bytes, suffix `k`, `m`, or `g` can be used for scaling. The default value of `R:MaximumHeapSizePercent` is used if nothing else is specified.
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
```
native-image -H:+UseLowLatencyGC HelloWorld
```

Currently, G1 Garbage Collector can be applied to generate native images on Linux in the AMD64 builds.
Note: The G1 Garbage Collector integration is available with **GraalVM Enterprise** only and is experimental.
