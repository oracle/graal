---
layout: docs
toc_group: optimizations-and-performance
link_title: Memory Management
permalink: /reference-manual/native-image/optimizations-and-performance/MemoryManagement/
redirect_from: /reference-manual/native-image/MemoryManagement/
---

# Memory Management

A native image, when being executed, does not run on the Java HotSpot VM but on the runtime system provided with GraalVM.
That runtime includes all necessary components, and one of them is the memory management.

Java objects that a native image allocates at run time reside in the area called "the Java heap".
The Java heap is created when the native image starts up, and may increase or decrease in size while the native image runs.
When the heap becomes full, a garbage collection is triggered to reclaim memory of objects that are no longer used.

For managing the Java heap, Native Image provides different garbage collector (GC) implementations:
* The **Serial GC** is the default GC in GraalVM Native Image.
It is optimized for low memory footprint and small Java heap sizes.
* The **G1 GC** is a multi-threaded GC that is optimized to reduce stop-the-world pauses and therefore improve latency, while achieving high throughput.
To enable it, pass the option `--gc=G1` to the `native-image` builder.
Currently, G1 Garbage Collector can be used with Native Image on the Linux AMD64 and AArch64 architectures. (Not available in GraalVM Community Edition.)
* The **Epsilon GC** (available with GraalVM 21.2 or later) is a no-op garbage collector that does not do any garbage collection and therefore never frees any allocated memory.
The primary use case for this GC are very short running applications that only allocate a small amount of memory.
To enable the Epsilon GC, specify the option `--gc=epsilon` at image build time.

## Performance Considerations

The primary metrics for garbage collection are throughput, latency, and footprint:
* *Throughput* is the percentage of total time not spent in garbage collection considered over long periods of time.
* *Latency* is the responsiveness of an application.
Garbage collection pauses negatively affect the responsiveness.
* *Footprint* is the working set of a process, measured in pages and cache lines.

Choosing settings for the Java heap is always a trade-off between these metrics.
For example, a very large young generation may maximize throughput, but does so at the expense of footprint and latency.
Young generation pauses can be minimized by using a small young generation at the expense of throughput.

By default, Native Image automatically determines values for the Java heap settings that are listed below.
The exact values may depend on the system configuration and the used GC.

* The *maximum Java heap size* defines the upper limit for the size of the whole Java heap.
If the Java heap is full and the GC is unable reclaim sufficient memory for a Java object allocation, the allocation will fail with the `OutOfMemoryError`.
Note: The maximum heap size is only the upper limit for the Java heap and not necessarily the upper limit for the total amount of consumed memory, as Native Image places some data such as thread stacks, just-in-time compiled code (for Truffle runtime compilation), and internal data structures in memory that is separate from the Java heap.
* The *minimum Java heap size* defines how much memory the GC may always assume as reserved for the Java heap, no matter how little of that memory is actually used.
* The *young generation size* determines the amount of Java memory that can be allocated without triggering a garbage collection.

## Serial Garbage Collector

The *Serial GC* is optimized for low footprint and small Java heap sizes.
If no other GC is specified, the Serial GC will be used implicitly as the default on GraalVM.
It is also possible to explicitly enable the Serial GC by passing the option `--gc=serial` to the native image builder.

```shell
# Build a native image that uses the serial GC with default settings
native-image --gc=serial HelloWorld
```

### Overview

In its core, the Serial GC is a simple (non-parallel, non-concurrent) stop and copy GC.
It divides the Java heap into a young and an old generation.
Each generation consists of a set of equally sized chunks, each a contiguous range of virtual memory.
Those chunks are the GC-internal unit for memory allocation and memory reclamation.

The young generation contains recently created objects and is divided into the _eden_ and _survivor_ regions.
New objects are allocated in the eden region, and when this region is full, a young collection is triggered.
Objects that are alive in the eden region will be moved to the survivor region, and alive objects in the survivor region stay in that region until they reach a certain age (have survived a certain number of collections), at which time they are moved to the old generation.
When the old generation becomes full, a full collection is triggered that reclaims the space of unused objects in both the young and old generations.
Typically, a young collection is much faster than a full collection, however doing full collections is important for keeping the memory footprint low.
By default, the Serial GC tries to find a size for the generations that provides good throughput, but to not increase sizes further when doing so gives diminishing returns.
It also tries to maintain a ratio between the time spent in young collections and in full collections to keep the footprint small.

If no maximum Java heap size is specified, a native image that uses the Serial GC will set its maximum Java heap size to 80% of the physical memory size.
For example, on a machine with 4GB of RAM, the maximum Java heap size will be set to 3.2GB.
If the same image is executed on a machine that has 32GB of RAM, the maximum Java heap size will be set to 25.6GB.
Note that this is just the maximum value.
Depending on the application, the amount of actually used Java heap memory can be much lower.
To override this default behavior, either specify a value for `-XX:MaximumHeapSizePercent` or explicitly set the maximum [Java heap size](#java-heap-size).

Note that GraalVM releases up to (and including) 21.3 use a different default configuration for the Serial GC with no survivor regions, a young generation that is limited to 256 MB, and a default collection policy that balances the time that is spent in young collections and old collections.
This configuration can be enabled with: `-H:InitialCollectionPolicy=BySpaceAndTime`

Be mindful that the GC needs some extra memory when performing a garbage collection (2x of the maximum heap size is the worst case, usually, it is significantly less).
Therefore, the resident set size, RSS, can increase temporarily during a garbage collection which can be an issue in any environment with memory constraints (such as a container).

### Performance Tuning

For tuning the GC performance and the memory footprint, the following options can be used:
* `-XX:MaximumHeapSizePercent` - the percentage of the physical memory size that is used as the maximum Java heap size if the maximum Java heap size is not specified otherwise.
* `-XX:MaximumYoungGenerationSizePercent` - the maximum size of the young generation as a percentage of the maximum Java heap size.
* `-XX:±CollectYoungGenerationSeparately` (since GraalVM 21.0) - determines if a full GC collects the young generation separately or together with the old generation.
If enabled, this may reduce the memory footprint during full GCs.
However, full GCs may take more time.
* `-XX:MaxHeapFree` (since GraalVM 21.3) - maximum total size (in bytes) of free memory chunks that remain reserved for allocations after a collection and are therefore not returned to the operating system.
* `-H:AlignedHeapChunkSize` (can only be specified at image build time) - the size of a heap chunk in bytes.
* `-H:MaxSurvivorSpaces` (since GraalVM 21.1, can only be specified at image build time) - the number of survivor spaces that are used for the young generation, that is, the maximum age at which an object will be promoted to the old generation.
With a value of 0, objects that survive a young collection are directly promoted to the old generation.
* `-H:LargeArrayThreshold` (can only be specified at image build time) - the size at or above which an array will be allocated in its own heap chunk.
Arrays that are considered as large are more expensive to allocate but they are never copied by the GC, which can reduce the GC overhead.

```shell
# Build and execute a native image that uses a maximum heap size of 25% of the physical memory
native-image --gc=serial -R:MaximumHeapSizePercent=25 HelloWorld
./helloworld

# Execute the native image from above but increase the maximum heap size to 75% of the physical memory
./helloworld -XX:MaximumHeapSizePercent=75
```

The following options are available with `-H:InitialCollectionPolicy=BySpaceAndTime` only:

* `-XX:PercentTimeInIncrementalCollection` - determines how much time the GC should spend doing young collections.
  With the default value of 50, the GC tries to balance the time spent on young and full collections.
  Increasing this value will reduce the number of full GCs, which can improve performance but may worsen the memory footprint.
  Decreasing this value will increase the number of full GCs, which can improve the memory footprint but may decrease performance.

## G1 Garbage Collector

Oracle GraalVM also provides the Garbage-First (G1) garbage collector, which is based on the G1 GC from the Java HotSpot VM.
Currently, G1 Garbage Collector can be used with Native Image on the Linux AMD64 and AArch64 architectures. (Not available in GraalVM Community Edition.)

To enable it, pass the option `--gc=G1` to the `native-image` builder.
```shell
# Build a native image that uses the G1 GC with default settings
native-image --gc=G1 HelloWorld
```

Note: In GraalVM 20.0, 20.1, and 20.2, the G1 GC was called low-latency GC and could be enabled via the experimental option `-H:+UseLowLatencyGC`.

### Overview

G1 is a generational, incremental, parallel, mostly concurrent, stop-the-world, and evacuating GC.
It aims to provide the best balance between latency and throughput.

Some operations are always performed in stop-the-world pauses to improve throughput.
Other operations that would take more time with the application stopped, such as whole-heap operations like global marking, are performed in parallel and concurrently with the application.
The G1 GC tries to meet set pause-time targets with high probability over a longer time.
However, there is no absolute certainty for a given pause.

G1 partitions the heap into a set of equally sized heap regions, each a contiguous range of virtual memory.
A region is the GC-internal unit for memory allocation and memory reclamation.
At any given time, each of these regions can be empty, or assigned to a particular generation.

If no maximum Java heap size is specified, a native image that uses the G1 GC will set its maximum Java heap size to 25% of the physical memory size.
For example, on a machine with 4GB of RAM, the maximum Java heap size will be set to 1GB.
If the same image is executed on a machine that has 32GB of RAM, the maximum Java heap size will be set to 8GB.
To override this default behavior, either specify a value for `-XX:MaxRAMPercentage` or explicitly set the maximum [Java heap size](#java-heap-size).

### Performance Tuning

The G1 GC is an adaptive garbage collector with defaults that enable it to work efficiently without modification.
However, it can be tuned to the performance needs of a particular application.
Here is a small subset of the options that can be specified when doing performance tuning:

* `-H:G1HeapRegionSize` (can only be specified at image build time) - the size of a G1 region.
* `-XX:MaxRAMPercentage` - the percentage of the physical memory size that is used as the maximum heap size if the maximum heap size is not specified otherwise.
* `-XX:MaxGCPauseMillis` - the goal for the maximum pause time.
* `-XX:ParallelGCThreads` - the maximum number of threads used for parallel work during garbage collection pauses.
* `-XX:ConcGCThreads` - the maximum number of threads used for concurrent work.
* `-XX:InitiatingHeapOccupancyPercent` - the Java heap occupancy threshold that triggers a marking cycle.
* `-XX:G1HeapWastePercent` - the allowed unreclaimed space in the collection set candidates. G1 stops the space-reclamation phase if the free space in the collection set candidates is lower than that.

```shell
# Build and execute a native image that uses the G1 GC with a region size of 2MB and a maximum pause time goal of 100ms
native-image --gc=G1 -H:G1HeapRegionSize=2m -R:MaxGCPauseMillis=100 HelloWorld
./helloworld

# Execute the native image from above and override the maximum pause time goal
./helloworld -XX:MaxGCPauseMillis=50
```

## Memory Management Options

This section describes the most important memory management command-line options that are independent of the used GC.
For all numeric values the suffix `k`, `m`, or `g` can be used for scaling.
Further options to the native image builder can be listed using `native-image --expert-options-all`.

### Java Heap Size

When executing a native image, suitable Java heap settings will be determined automatically based on the system configuration and the used GC.
To override this automatic mechanism and to explicitly set the heap size at run time, the following command-line options can be used:
* `-Xmx` - maximum heap size in bytes
* `-Xms` - minimum heap size in bytes
* `-Xmn` - the size of the young generation in bytes

It is also possible to preconfigure default heap settings at image build time.
The specified values will then be used as the default values at run time:
* `-R:MaxHeapSize` (since GraalVM 20.0) - maximum heap size in bytes
* `-R:MinHeapSize` (since GraalVM 20.0) - minimum heap size in bytes
* `-R:MaxNewSize` (since GraalVM 20.0) - size of the young generation in bytes

```shell
# Build a native image with the default heap settings and override the heap settings at run time
native-image HelloWorld
./helloworld -Xms2m -Xmx10m -Xmn1m

# Build a native image and "bake" heap settings into the image. The specified values will be used at run time
native-image -R:MinHeapSize=2m -R:MaxHeapSize=10m -R:MaxNewSize=1m HelloWorld
./helloworld
```

## Compressed References

Oracle GraalVM supports compressed references to Java objects that use 32-bit instead of 64-bit.
Compressed references are enabled by default and can have a large impact on the memory footprint.
However, they limit the maximum Java heap size to 32 GB of memory.
If more than 32 GB are needed, compressed references need to be disabled.

* `-H:±UseCompressedReferences` (can only be specified at image build time) - determines if 32-bit instead of 64-bit references to Java objects are used.

## Native Memory

Native Image may also allocate memory that is separate from the Java heap.
One common use-case is a `java.nio.DirectByteBuffer` that directly references native memory.

* `-XX:MaxDirectMemorySize` - the maximum size of direct buffer allocations.

## Printing Garbage Collections

When executing a native image, the following options can be be used to print some information on garbage collection.
Which data is printed in detail depends on the used GC.
* `-XX:+PrintGC` - print basic information for every garbage collection
* `-XX:+VerboseGC` - can be added to print further garbage collection details

```shell
# Execute a native image and print basic garbage collection information
./helloworld -XX:+PrintGC

# Execute a native image and print detailed garbage collection information
./helloworld -XX:+PrintGC -XX:+VerboseGC
```

### Further Reading

* [Memory Configuration for Native Image Build](BuildConfiguration.md#memory-configuration-for-native-image-build)
