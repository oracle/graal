---
layout: docs
toc_group: debugging-and-diagnostics
link_title: JDK Flight Recorder
permalink: /reference-manual/native-image/debugging-and-diagnostics/JFR/
redirect_from: /reference-manual/native-image/JFR/
---

# JDK Flight Recorder (JFR) with Native Image

JDK Flight Recorder (JFR) is an event recorder for capturing information about a JVM, and an application running on the JVM.
GraalVM Native Image supports building a native executable with JFR events, and users can use [`jdk.jfr.Event` API](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.jfr/jdk/jfr/Event.html) with a similar experience to using JFR in the Java HotSpot VM.

## Include JFR Support at Build Time and Record Events at Runtime

JFR support is disabled by default and must be explicitly enabled at build time.

> Note: JFR event recording is not yet available with Native Image on Windows.

To build a native executable with JFR, use the `--enable-monitoring=jfr` option:
```shell
native-image --enable-monitoring=jfr JavaApplication
```
The following options are supported to start a recording, and configure logging at runtime:

* `-XX:StartFlightRecording`: starts a recording on application startup
* `-XX:FlightRecorderLogging`: configures the log output for the JFR

To start a JFR recording, simply use `-XX:StartFlightRecording` at runtime.
For example:
```shell
./javaapplication -XX:StartFlightRecording="filename=recording.jfr"
```

## Configure JFR Recording

Similar to how JFR recordings can be started on HotSpot, you start recording by passing a comma-separated list of key-value pairs to the `-XX:StartFlightRecording` option.
For example:
```shell
-XX:StartFlightRecording="filename=recording.jfr,dumponexit=true,duration=10s"
```

The following key-value pairs are supported:

| Name       | Default Value | Description                                                                                                                                        |
|------------|---------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| name       | none          | Name to identify the recording, for example, `name=MyRecording`                                                                          |
| settings   | none          | Settings file (_profile.jfc_, _default.jfc_, and so on), for example, `settings=myprofile.jfc`                                                                     |
| delay      | none          | Delay recording start with (s)econds, (m)inutes, (h)ours, or (d)ays, for example, `delay=5h`                                                            |
| duration   | infinite (0)  | Duration of recording in (s)econds, (m)inutes, (h)ours, or (d)ays, for example, `duration=300s`                                                           |
| filename   | none          | Resulting recording filename, for example, `filename=recording1.jfr`                                                                                      |
| maxage     | no limit (0)  | Maximum time to keep the recorded data on disk in (s)econds, (m)inutes, (h)ours, or (d)ays, for example, 60m, or 0 for no limit. For example, `maxage=1d` |
| maxsize    | no limit (0)  | Maximum amount of bytes to keep on disk in (k)B, (M)B or (G)B, for example, 500M, or 0 for no limit. For example, `maxsize=1G`                            |
| dumponexit | false         | Whether to dump a running recording when the JVM shuts down, for example, `dumponexit=true`                                                               |

## Configure JFR System Logging

You can configure the logging for the JFR system with a separate flag `-XX:FlightRecorderLogging`.
The usage is: `-XX:FlightRecorderLogging=[tag1[+tag2...][*][=level][,...]]`.
For example:
```shell
-XX:FlightRecorderLogging=jfr+system=debug
-XX:FlightRecorderLogging=all=trace
-XX:FlightRecorderLogging=jfr*=error
```

* When this option is not set, logging is enabled at a level of `WARNING`.
* When this option is set to an empty string, logging is enabled at a level of `INFO`.
* When this option is set to "disable", logging is disabled entirely.

Available log levels are: `trace, debug, info, warning, error, off`.

Available log tags are: `all, jfr, system, event, setting, bytecode, parser, metadata, dcmd`.

Otherwise, this option expects a comma-separated list of tag combinations, each with an optional wildcard (`*`) and level.

* A tag combination without a level is given a default level of `INFO`.
* Messages with tags that match a given tag combination will be logged if they meet the tag combination's level.
* If a tag combination does not have a wildcard, then only messages with exactly the same tags are matched. Otherwise, messages whose tags are a subset of the tag combination are matched.
* If more than one tag combination matches a message's tags, the rightmost one will apply.
* Messages with tags that do not have any matching tag combinations are set to log at a default level of `WARNING`.
* This option is case insensitive.

## Features and Limitations

This section outlines the JFR features that are available in Native Image.

### Method Profiling and Stack Traces

Method profiling in JFR supports two types of sampling: safepoint and asynchronous sampling.
The asynchronous sampler is enabled by default, while the safepoint sampler is used only on demand.
Asynchronous sampling offers the advantage of avoiding safepoint bias, which happens if a profiler does not sample all points in the application with equal probability.
In this scenario, the sampler can only perform sampling at a safepoint, thereby introducing bias into the profiles.

Both samplers periodically produce the event `jdk.ExecutionSample` at specified frequencies.
These samples can be viewed in applications such as JDK Mission Control or VisualVM.
In addition, other JFR events that support stacktraces on HotSpot also support stacktraces in Native Image.
This means you can do interesting things such as viewing flamegraphs of `jdk.ObjectAllocationInNewTLAB` to diagnose where object allocations are frequently happening.

### JFR Event Streaming

[JFR Event Streaming](https://openjdk.org/jeps/349) is available with Native Image.
Event streaming enables you to register callbacks for specific events at the application level.
This introduces more flexibility and control over how recordings are managed.
For example, you may dynamically increase the duration threshold of an event if it is found in the stream beyond a certain number times.
Event streaming also enables the application to get continuous periodic JFR updates that are useful for monitoring purposes.

Currently, stacktraces are not yet available on streamed events.
This means you cannot access the stacktrace of an event inside its callback method.
However, this limitation does not affect stacktraces in the JFR snapshot file (_.jfr_), those will still work as usual.

### Interaction with FlightRecorderMXBean via Remote JMX

You can interact with Native Image JFR from out of a process via a remote JMX connection to `FlightRecorderMXBean`.
This can be done using applications such as JDK Mission Control or VisualVM.
Over JMX you can start, stop, and dump JFR recordings using the `FlightRecorderMXBean` API as an interface.

> Note: Remote JMX connection support needs to be enabled separately at build time and is experimental.

### FlightRecorderOptions

You can fine-tune JFR parameters by using `-XX:FlightRecorderOptions` at runtime.
This is primarily for advanced users, and most people should be fine with the default parameters.

### Leak Profiling

Leak profiling implemented using the `jdk.OldObjectSample` event is partially available.
Specifically, old object tracking is possible, but the path to the GC root information is unavailable.

### Using JFR with JCMD

JFR can be controlled using the Java Diagnostic Command utility (`jcmd`).
To enable this functionality, `jcmd` support must be configured at build time.
The following JFR commands are available with `jcmd`: `JFR.start`, `JFR.stop`, `JFR.check`, and `JFR.dump`.

### Built-In Events

Many of the VM-level built-in events are available in Native Image.
Java-level events implemented by bytecode instrumentation on the HotSpot JVM are not yet available in Native Image.
Such events include file I/O and exception built-in events.

The following table lists JFR Events that can be collected with Native Image.
Some of the events are available with [Serial GC](MemoryManagement.md) only, the default garbage collector in Native Image.

| Event Name                                                    |
|---------------------------------------------------------------|
| `jdk.ActiveRecording`                                         |
| `jdk.ActiveSetting`                                           |
| `jdk.AllocationRequiringGC` <a href="#footnote-1">1)</a>      |
| `jdk.ClassLoadingStatistics`                                  |
| `jdk.ContainerCPUThrottling`                                  |
| `jdk.ContainerCPUUsage`                                       |
| `jdk.ContainerConfiguration`                                  |
| `jdk.ContainerIOUsage`                                        |
| `jdk.ContainerMemoryUsage`                                    |
| `jdk.DataLoss`                                                |
| `jdk.ExecutionSample`                                         |
| `jdk.ExecuteVMOperation`                                      |
| `jdk.GarbageCollection` <a href="#footnote-1">1)</a>          |
| `jdk.GCHeapSummary` <a href="#footnote-1">1)</a>              |
| `jdk.GCPhasePause` <a href="#footnote-1">1)</a>               |
| `jdk.GCPhasePauseLevel1` <a href="#footnote-1">1)</a>         |
| `jdk.GCPhasePauseLevel2` <a href="#footnote-1">1)</a>         |
| `jdk.GCPhasePauseLevel3` <a href="#footnote-1">1)</a>         |
| `jdk.GCPhasePauseLevel4` <a href="#footnote-1">1)</a>         |
| `jdk.InitialEnvironmentVariable`                              |
| `jdk.InitialSystemProperty`                                   |
| `jdk.JavaMonitorEnter`                                        |
| `jdk.JavaMonitorInflate`                                      |
| `jdk.JavaMonitorWait`                                         |
| `jdk.JavaThreadStatistics`                                    |
| `jdk.JVMInformation`                                          |
| `jdk.NativeMemoryUsage` <a href="#footnote-3">3)</a>          |
| `jdk.NativeMemoryUsageTotal` <a href="#footnote-3">3)</a>     |
| `jdk.NativeMemoryUsagePeak` <a href="#footnote-3">3)</a>      |
| `jdk.NativeMemoryUsageTotalPeak` <a href="#footnote-3">3)</a> |
| `jdk.ObjectAllocationSample` <a href="#footnote-1">1)</a>     |
| `jdk.ObjectAllocationInNewTLAB` <a href="#footnote-1">1)</a>  |
| `jdk.OldObjectSample` <a href="#footnote-1">2)</a>            |
| `jdk.OSInformation`                                           |
| `jdk.PhysicalMemory`                                          |
| `jdk.SafepointBegin`                                          |
| `jdk.SafepointEnd`                                            |
| `jdk.SocketRead`                                              |
| `jdk.SocketWrite`                                             |
| `jdk.SystemGC`  <a href="#footnote-1">1)</a>                  |
| `jdk.ThreadAllocationStatistics`                              |
| `jdk.ThreadCPULoad`                                           |
| `jdk.ThreadEnd`                                               |
| `jdk.ThreadPark`                                              |
| `jdk.ThreadSleep`                                             |
| `jdk.ThreadStart`                                             |
| `jdk.VirtualThreadEnd`                                        |
| `jdk.VirtualThreadPinned`                                     |
| `jdk.VirtualThreadStart`                                      |

<p id="footnote-1" style="margin-bottom: 0;"><i>1) Available if Serial GC is used.</i></p>
<p id="footnote-2" style="margin-bottom: 0;"><i>2) Partially available if Serial GC is used.</i></p>
<p id="footnote-3" style="margin-bottom: 0;"><i>3) Available if Native Memory Tracking is used.</i></p>

### Further Reading

- [Build and Run Native Executables with JFR](guides/build-and-run-native-executable-with-jfr.md)
- [Use remote JMX with Native Image](guides/build-and-run-native-executable-with-remote-jmx.md)
- [Java Diagnostic Command (jcmd) with Native Image](JCmd.md)