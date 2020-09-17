# Memory Management

There are two types of memory management in regard to Native Image: the memory
used to build a native image, and memory used when executing this native image.


#### Memory Management at Image Build Time
During a native image build a representation of a whole program is created to
figure out which classes and methods will be used at run time. It is a
computationally intensive process. The native image builder uses the memory
management of the HotSpot JVM --  the Parallel Collector, which is enabled with
the `-XX:+UseParallelGC` command-line option.
The defaults for memory usage are:
```
-Xss10M \
-Xms1G \
-Xmx14G \
```
These defaults can be changed by passing `-J + <jvm option for memory>` to the native image builder, e.g., `-J-Xmx28g`.
Also, the default number of CPUs used for image building is limited to 32 threads. Using more that 32 threads has diminishing returns.

#### Memory Management at Image Run Time
When executing the built image, it will, by default, use up to
`-H:MaximumHeapSizePercent` of the physical memory size. For example, with the
default value of 80%, and on a machine with 4GB of RAM, it will at most use
3,2GB of RAM. If the same image is executed on a machine that has 32GB of RAM,
it will use up to 25,6GB.

In general, there are different ways for specifying the heap size that a native image will use at run time:
* Passing the arguments `-Xms`, `-Xmx`, and/or `-Xmn` when executing the native image, e.g., `./helloworld -Xms2m -Xmx10m -Xmn1m.` This always has the highest priority and overrides any other configured values.
  * `-Xmn` - to set the size of the young generation (the amount of memory that can be allocated without triggering a GC). The value is specified in bytes, suffix `k`, `m`, or `g` can be used for scaling.
  * `-Xmx` - maximum heap size in bytes. Note that this is not the maximum amount of consumed memory, because during GC the system can request more temporary memory, for example, `-Xmx16M`. The default value of `R:MaximumHeapSizePercent` is used if nothing else is specified.
  * `-Xms` - minimum heap size in bytes. The value is specified in bytes, suffix `k`, `m`, or `g` can be used for scaling. Heap space that is unused will be retained for future heap usage, rather than being returned to the operating system.
* Setting an absolute default heap size at image build time: `native-image -R:MinHeapSize=2m -R:MaxHeapSize=10m -R:MaxNewSize=1m HelloWorld`.
* Setting a relative default heap size at image build time: `native-image -R:MaximumHeapSizePercent=80 -R:MaximumYoungGenerationSizePercent=10 HelloWorld`. This variant is used as the fallback if the user does not specify anything during the image build, nor when executing the built image.

As of version 20.1.0 option `-XX:MaxDirectMemorySize=...` was added to allow
controlling the maximum size of direct buffer allocations. Check other related options to the native image builder from the `native-image --expert-options-all` list.
