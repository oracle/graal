# Low Latency Garbage Collection

When using GraalVM Enterprise you can enable the experimental option to use a
lower latency garbage collection implementation. A low latency GC should
improve the performance of the native image applications by reducing the
stop-the-world pauses.

Note: This feature is available with **GraalVM Enterprise** only.

To enable it use the `-H:+UseLowLatencyGC` option when building a native image.

Currently, the lower latency GC works on Linux in the AMD64 builds. One can
configure the default run-time heap size at image build time, e.g.,
`-R:MinHeapSize=256m -R:MaxHeapSize=2g -R:MaxNewSize=128m`.

You can use `-XX:+PrintGC`, `-XX:+VerboseGC` to get some information about garbage collections.
