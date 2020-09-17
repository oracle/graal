# Native Image Approach to Garbage Collection

As mentioned in the [Memory Management](MemoryManagement.md) guide, the native image builder uses the memory management of the HotSpot JVM --  the G1 Garbage Collector (G1).

The Garbage-First (G1) collector is defined as a server-style garbage collector,
targeted for multi-processor machines with large memories. It G1 offers more
predictable GC pause duration which adds to the better throughput performance.
To learn more about Java HotSpot's G1 and how it functions internally, check
[this tutorial][https://www.oracle.com/technetwork/tutorials/tutorials-1876574.html].

The `-XX:+PrintGC`, `-XX:+VerboseGC` command-line options can help getting some
information about Garbage Collection in Java HotSpot. The usual Java HotSpot G1
command line options apply to Native Image. For example, to configure the
default run-time heap size at image build time, use `-R:MinHeapSize=256m -R:MaxHeapSize=2g -R:MaxNewSize=128m`.

## G1 Garbage Collector Integration

GraalVM Enterprise provides its own lower latency garbage collection implementation by integrating
the Java HotSpot's G1 Garbage Collector (G1 GC). This garbage collection implementation
improves the performance of the native images by reducing the stop-the-world pauses.
To enable it, pass the `-H:+UseLowLatencyGC` option to the native image builder, e.g.:
```
native-image -H:+UseLowLatencyGC HelloWorld
```

Currently, G1 Garbage Collector can be applied to generate native images on Linux in the AMD64 builds.
Note: The G1 Garbage Collector integration is available with **GraalVM Enterprise** only and is experimental.
