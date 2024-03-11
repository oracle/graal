---
layout: docs
toc_group: debugging-and-diagnostics
link_title: JDK Flight Recorder
permalink: /reference-manual/native-image/debugging-and-diagnostics/JFR/
redirect_from: /reference-manual/native-image/JFR/
---

# JDK Flight Recorder (JFR) with Native Image

JDK Flight Recorder (JFR) is an event recorder for capturing information about a JVM, and an application running on the JVM. 
GraalVM Native Image supports building a native executable with JFR events, and users can use [`jdk.jfr.Event` API](https://docs.oracle.com/en/java/javase/20/docs/api/jdk.jfr/jdk/jfr/Event.html) with a similar experience to using JFR in the Java HotSpot VM.


## Include JFR Support at Build Time and Record Events at Run Time
To record JFR events when running a native executable, JFR support must be added to the image at build time and a recording must be started at runtime.

To build a native executable with JFR, use the `--enable-monitoring=jfr` flag:
```shell
native-image --enable-monitoring=jfr JavaApplication
```
To start a recording, and configure logging at run time, the following options are supported:

* `-XX:StartFlightRecording`: use to start a recording on application's startup
* `-XX:FlightRecorderLogging`: use to configure the log output for the JFR system

To start a JFR recording upon launching your application, simply use `-XX:StartFlightRecording`. 
For example:
```shell
./javaapplication -XX:StartFlightRecording="filename=recording.jfr"
```

### Run a Demo

Transform this very simple demo application into a native image and see how to use JFR events from it.
Save the following code to the _Example.java_ file. This demo makes use of the JFR Event API to create and emit custom events.

```java
import jdk.jfr.Event;
import jdk.jfr.Description;
import jdk.jfr.Label;

public class Example {

  @Label("Hello World")
  @Description("Helps programmer getting started")
  static class HelloWorldEvent extends Event {
      @Label("Message")
      String message;
  }

  public static void main(String... args) {
      HelloWorldEvent event = new HelloWorldEvent();
      event.message = "hello, world!";
      event.commit();
  }
}
```

## Configure JFR Recording

Similar to normal Java applications, you can configure the JFR recording by passing a comma-separated list of key-value pairs to the `-XX:StartFlightRecording` option.
For example:
```shell
-XX:StartFlightRecording="filename=recording.jfr,dumponexit=true,duration=10s"
```

The following key-value pairs are supported:

| Name       | Default Value | Description                                                                                                                                        |
|------------|---------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| name       | none          | Name that can be used to identify the recording, e.g., “name=MyRecording”                                                                          |
| settings   | none          | Settings file (profile.jfc, default.jfc, etc.), e.g., “settings=myprofile.jfc”                                                                     |
| delay      | none          | Delay recording start with (s)econds, (m)inutes), (h)ours), or (d)ays, e.g., “delay=5h”                                                            |
| duration   | infinite (0)  | Duration of recording in (s)econds, (m)inutes, (h)ours, or (d)ays, e.g., “duration=300s”                                                           |
| filename   | none          | Resulting recording filename, e.g., “filename=recording1.jfr”                                                                                      |
| maxage     | no limit (0)  | Maximum time to keep the recorded data on disk in (s)econds, (m)inutes, (h)ours, or (d)ays, e.g., 60m, or 0 for no limit. For example, “maxage=1d” |
| maxsize    | no limit (0)  | Maximum amount of bytes to keep on disk in (k)B, (M)B or (G)B, e.g., 500M, or 0 for no limit. For example, “maxsize=1G”                            |
| dumponexit | false         | Whether to dump a running recording when the JVM shuts down, e.g., “dumponexit=true”                                                               |

## Configure JFR System Logging

You can configure the logging for the JFR system with a separate flag `-XX:FlightRecorderLogging`. 
The usage is: `-XX:FlightRecorderLogging=[tag1[+tag2...][*][=level][,...]]`. 
For example:
```shell
-XX:FlightRecorderLogging=jfr,system=debug
-XX:FlightRecorderLogging=all=trace
-XX:FlightRecorderLogging=jfr*=error
```

* When this option is not set, logging is enabled at a level of `WARNING`.
* When this option is set to an empty string, logging is enabled at a level of `INFO`.
* When this option is set to "disable", logging is disabled entirely.

Available log levels are: `trace, debug, info, warning, error, off`.

Available log tags are: `all, jfr, system, event, setting, bytecode, parser, metadata, dcmd`.

Otherwise, this option expects a comma separated list of tag combinations, each with an optional wildcard (`*`) and level.

* A tag combination without a level is given a default level of `INFO`.
* Messages with tags that match a given tag combination will be logged if they meet the tag combination's level.
* If a tag combination does not have a wildcard, then only messages with exactly the same tags are matched. Otherwise, messages whose tags are a subset of the tag combination are matched.
* If more than one tag combination matches a message's tags, the rightmost one will apply.
* Messages with tags that do not have any matching tag combinations are set to log at a default level of `WARNING`.
* This option is case insensitive.

### Related Documentation

- [Debugging and Diagnostics](DebuggingAndDiagnostics.md)


## Features and Limitations
Work is ongoing to achieve feature parity with JFR in Java HotSpot VM. This section outlines the JFR features that are supported in Native Image.

### Method Profiling and Stack Traces
(Since GraalVM for JDK 17 / 20).
Method profiling is implemented by handling the SIGPROF signal to periodically collect method samples. 
The event `jdk.ExecutionSample` is supported and its flamegraphs can be viewed in applications such as JDK Mission Control and VisualVM to diagnose hot methods.
In addition, other JFR events that support stacktraces in Java HotSpot VM also support stacktraces in Native Image. This means you can do interesting things like view flamegraphs of `jdk.ObjectAllocationInNewTLAB` to diagnose where object allocations are frequently happening. 


### Event Streaming
(Since GraalVM for JDK 17 / 20).
Event streaming is supported in Native Image. Event Streaming lets you register callbacks for specific events at the application level. This introduces more flexibility and control over how recordings are managed. For example, you may dynamically increase the duration threshold of an event if it is found in the stream beyond a certain number times. Event streaming also allows the application to get continuous periodic JFR updates that are useful for monitoring purposes. 

Currently, stacktraces are not supported on streamed events. This means you cannot access the stacktrace of an event inside its callback method. However, this limitation does not affect stacktraces in the JFR snapshot (.jfr) file, those will still work as usual.   

### Interaction with FlightRecorderMXBean via Remote JMX
(Since GraalVM for JDK 17 / 20).
You can now interact with Native Image JFR from out of process via remote JMX connection to `FlightRecorderMXBean`.  This can be done in using applications such as JDK Mission Control or VisualVM. Over JMX you can start, stop, and dump JFR recordings using the `FlightRecorderMXBean` API as an interface. [This blog post](https://developers.redhat.com/articles/2023/06/13/improvements-native-image-jfr-support-graalvm-jdk-20?source=sso#new_supported_features) provides a step-by-step walk-through of how to start using JFR over remote JMX in JDK Mission Control.
Find more general information on how to use JMX with Native Image in [this guide](guides/build-and-run-native-executable-with-remote-jmx.md).

> Note: Remote JMX support in Native Image is experimental and may be changed in the future. 

### Event Throttling
(Build from source).
Similar to OpenJDK, event throttling is enabled by default for `jdk.ObjectAllocationSample`. This limits the overhead of the event. The unsampled version of this event is `jdk.ObjectAllocationInNewTLAB`. You can specify whether to enable this event as well as the throttling rate within a JFR configuration file (.jfc). 

### FlightRecorderOptions 
(Build from source).
You can fine tune JFR parameters by using `-XX:FlightRecorderOptions` at runtime. This is primarily for advanced users, and most people should be fine with the default parameters. The options are the same as in OpenJDK.

### Leak Profiling
(Build from source).
Leak profiling implemented by the `jdk.OldObjectSample` event is partially implemented. Specifically, old object tracking is possible, but path to GC root information is unavailable. 

### Built-In Events
Many of the VM-level built-in events found in OpenJDK are available in Native Image. Java-level events implemented by bytecode instrumentation in the Java HotSpot VM are not yet supported in Native Image. 
Such events include file IO, exception, and network IO built-in events. Support for them is in progress. See the following table for a list of supported events.

| Event Name                     | Since GraalVM Version |
| ------------------------------ |-----------------------|
| jdk.ActiveRecording            | 22.1                  |
| jdk.ActiveSetting              | 22.1                  |
| jdk.AllocationRequiringGC      | GraalVM for JDK 22    |
| jdk.ClassLoadingStatistics     | 22.1                  |
| jdk.ContainerCPUThrottling     | GraalVM for JDK 17    |
| jdk.ContainerCPUUsage          | GraalVM for JDK 17    |
| jdk.ContainerConfiguration     | GraalVM for JDK 17    |
| jdk.ContainerIOUsage           | GraalVM for JDK 17    |
| jdk.ContainerMemoryUsage       | GraalVM for JDK 17    |
| jdk.DataLoss                   | 22.1                  |
| jdk.ExecutionSample            | GraalVM for JDK 17    |
| jdk.ExecuteVMOperation         | 22.2                  |
| jdk.GarbageCollection          | 22.1                  |
| jdk.GCHeapSummary              | GraalVM for JDK 21    |
| jdk.GCPhasePause               | 22.1                  |
| jdk.GCPhasePauseLevel1         | 22.1                  |
| jdk.GCPhasePauseLevel2         | 22.1                  |
| jdk.GCPhasePauseLevel3         | 22.1                  |
| jdk.GCPhasePauseLevel4         | 22.1                  |
| jdk.InitialEnvironmentVariable | 22.1                  |
| jdk.InitialSystemProperty      | 22.1                  |
| jdk.JavaMonitorEnter           | 22.3                  |
| jdk.JavaMonitorInflate         | GraalVM for JDK 17    |
| jdk.JavaMonitorWait            | 22.3                  |
| jdk.JavaThreadStatistics       | 22.1                  |
| jdk.JVMInformation             | 22.1                  |
| jdk.ObjectAllocationInNewTLAB  | GraalVM for JDK 17    |
| jdk.OSInformation              | 22.1                  |
| jdk.PhysicalMemory             | 22.1                  |
| jdk.SafepointBegin             | 22.1                  |
| jdk.SafepointEnd               | 22.1                  |
| jdk.SystemGC                   | GraalVM for JDK 22    |
| jdk.ThreadAllocationStatistics | GraalVM for JDK 22    |
| jdk.ThreadCPULoad              | GraalVM for JDK 21    |
| jdk.ThreadEnd                  | 22.1                  |
| jdk.ThreadPark                 | GraalVM for JDK 17    |
| jdk.ThreadSleep                | 22.3                  |
| jdk.ThreadStart                | 22.1                  |
| jdk.VirtualThreadEnd           | GraalVM for JDK 21    |
| jdk.VirtualThreadPinned        | GraalVM for JDK 21    |
| jdk.VirtualThreadStart         | GraalVM for JDK 21    |

Note: GraalVM's version naming scheme changed. Version 22.1 is older than 22.2, while 22.3 is older than GraalVM for JDK17.  Version 22.1 - 22.3 supported both JDK 11 and JDK 17.

To see an exhaustive list of JFR events and features supported by Native Image, see [this GitHub issue](https://github.com/oracle/graal/issues/5410).

> Note: the GraalVM distribution for Windows does not include JFR event recording.

### Further Reading

- [Practice how to enable JFR support with Native Image and record events at run time using a demo application](guides/build-and-run-native-executable-with-jfr.md).

- [Create and record your first event with Java](https://docs.oracle.com/en/java/javase/17/jfapi/creating-and-recording-your-first-event.html).
