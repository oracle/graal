---
layout: docs
toc_group: debugging-and-diagnostics
link_title: Native Memory Tracking
permalink: /reference-manual/native-image/debugging-and-diagnostics/NMT/
redirect_from: /reference-manual/native-image/NMT/
---

# Native Memory Tracking (NMT) with Native Image

Native Memory Tracking (NMT) is a serviceability feature that records off-heap memory usage of your application. The terminology “off-heap memory” is sometimes used interchangeably with “native memory” or “unmanaged memory.” This essentially means any memory that is not managed by the garbage collector.

Unlike traditional Java applications, Native Image uses Substrate VM instead of Java HotSpot VM to provide the runtime components.
Unlike HotSpot, Substrate VM mostly uses memory on the collected heap managed by its garbage collector.
However, there are still many places where native memory is used by Substrate VM to avoid allocations on the managed heap.
Some examples include JFR, the garbage collector, and heap dumping.
Native memory can also be directly requested at the application level with `Unsafe#allocateMemory(long)`.

## Enabling Native Memory Tracking

NMT support is disabled by default and must be explicitly enabled at build time.
 
To build a native executable with NMT, use the `--enable-monitoring=nmt` option. If NMT is included at build time, it will always be enabled at runtime. This is different than OpenJDK which allows for enabling/disabling at runtime.
```shell
native-image --enable-monitoring=nmt YourApplication
```

Adding `-XX:+PrintNMTStatistics` when starting your application from a native executable tells NMT to write a report to standard output when the application completes.
```shell
./yourapplication -XX:+PrintNMTStatistics"
```

## Limitations

In OpenJDK, NMT has two modes: summary and detailed. Currently in Native Image, only NMT summary mode is supported. Detailed mode, which enables callsite tracking, is not available. Capturing baselines are also not yet possible. If you are interested in support for these additional features, please make a request on the GraalVM Github project. 

Only malloc tracking is available in GraalVM for JDK 23. Virtual memory tracking is available in early access releases. 

A limitation Native Image NMT shares with OpenJDK is that it can only track allocations at the VM-level and those made with `Unsafe#allocateMemory(long)`. For example, if library code or application code calls malloc directly, that call will bypass the NMT accounting and be untracked.

## Performance
In most cases, both the CPU and memory overhead of NMT will be quite minimal. There is an overhead of 16B per malloc allocation (to accommodate malloc headers) that is reclaimed once the memory is freed. In practice this does not amount to very much since SubstrateVM does not make many allocations. One can usually expect less than a thousand allocations at any point in time. This overhead scales with allocation count, not size, so if `Unsafe#allocateMemory(long)` is frequently used at the application level, the overhead may become noticeable. However, this case is unlikely.

There is also minimal CPU overhead since not much work needs to be done internally to track allocations. Synchronization is also minimal apart from atomic counters.

In comparison to other serviceability features such as JFR, NMT has relatively very little overhead.

## JFR Events for NMT
The OpenJDK JFR events `jdk.NativeMemoryUsage` and `jdk.NativeMemoryUsageTotal` are supported in Native Image.

There are also two Native Image specific JFR events that you can access: `jdk.NativeMemoryUsagePeak` and `jdk.NativeMemoryUsageTotalPeak`. These Native Image specific events have been created to expose peak usage data otherwise not exposed through the JFR events ported over from the OpenJDK. It should be noted that these new are marked as experimental. You may need to enable experimental events in software like JDK Mission control in order to view them.

Below is example of what the new events look like viewed using the `jfr` command line tool:
```
jfr print --events jdk.NativeMemoryUsagePeak recording.jfr 

jdk.NativeMemoryUsagePeak {
  startTime = 13:18:50.605 (2024-04-30)
  type = "Threading"
  peakReserved = 424 bytes
  peakCommitted = 424 bytes
  countAtPeak = 4
  eventThread = "JFR Shutdown Hook" (javaThreadId = 63)
}

jdk.NativeMemoryUsagePeak {
  startTime = 13:18:50.605 (2024-04-30)
  type = "Unsafe"
  peakReserved = 14.0 kB
  peakCommitted = 14.0 kB
  countAtPeak = 2
  eventThread = "JFR Shutdown Hook" (javaThreadId = 63)
}
```
To use these JFR events for NMT, JFR must be included as a feature at build time and started at runtime. 

### Further Reading

- [Build and Run Native Executables with JFR](guides/build-and-run-native-executable-with-jfr.md)