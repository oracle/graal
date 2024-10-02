---
layout: docs
toc_group: debugging-and-diagnostics
link_title: Native Memory Tracking
permalink: /reference-manual/native-image/debugging-and-diagnostics/NMT/
---

# Native Memory Tracking (NMT) with Native Image

Native Memory Tracking (NMT) is a serviceability feature that records off-heap memory usage of your application.
The terminology "off-heap memory" is sometimes used interchangeably with "native memory" or "unmanaged memory". 
This essentially means any memory that is not managed by the garbage collector.

Unlike the HotSpot JVM, Native Image mostly uses memory on the collected heap managed by its garbage collector.
However, there are still many places where native memory is used by Native Image to avoid allocations on the managed heap.
Some examples include JFR, the garbage collector, and heap dumping.
Native memory can also be directly requested at the application level with `Unsafe#allocateMemory(long)`.

## Enabling Native Memory Tracking

NMT support is disabled by default and must be explicitly enabled at build time.
 
To build a native executable with NMT, use the `--enable-monitoring=nmt` option.
If NMT is included at build time, it will always be enabled at runtime.
This is different than on HotSpot which allows for enabling/disabling NMT at runtime.
```shell
native-image --enable-monitoring=nmt YourApplication
```

Adding `-XX:+PrintNMTStatistics` when starting your application from a native executable tells NMT to write a report to standard output when the application completes.
```shell
./yourapplication -XX:+PrintNMTStatistics
```

## Performance

On Native Image, both the CPU and memory consumption of NMT are quite minimal. 
In comparison to other serviceability features such as JFR, NMT has relatively very little overhead.

## JFR Events for NMT

The OpenJDK JFR events `jdk.NativeMemoryUsage` and `jdk.NativeMemoryUsageTotal` are supported in Native Image.

There are also two Native Image specific JFR events that you can access: `jdk.NativeMemoryUsagePeak` and `jdk.NativeMemoryUsageTotalPeak`.
These Native Image specific events have been created to expose peak usage data otherwise not exposed through the JFR events ported over from the OpenJDK.
These new events are marked as experimental.
You may need to enable experimental events in software like JDK Mission Control to view them.

To use these JFR events for NMT, enable the JFR monitoring by passing the `--enable-monitoring=jfr,nmt` option when invoking the `native-image` tool, and then start JFR recording at runtime. 
(Learn more in [JDK Flight Recorder (JFR) with Native Image](JFR.md)).

See below the example of what the new events look like when viewed using the `jfr` command line tool:
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

## Limitations

On HotSpot, NMT has two modes: summary and detailed.
In Native Image, only NMT summary mode is currently supported.
The detailed mode, which enables callsite tracking, is not available.
Capturing baselines is also not yet possible.
If you are interested in support for these additional features, file a request to the [GraalVM project on GitHub](https://github.com/oracle/graal).

Malloc tracking is the only feature currently available (as of GraalVM for JDK 23).

Native Image, same as HotSpot, can only track allocations at the VM-level and those made with `Unsafe#allocateMemory(long)`.
For example, if a library code or application code calls malloc directly, that call will bypass the NMT accounting and be untracked.

### Further Reading

- [Build and Run Native Executables with JFR](guides/build-and-run-native-executable-with-jfr.md)
- [Debugging and Diagnostics](DebuggingAndDiagnostics.md)