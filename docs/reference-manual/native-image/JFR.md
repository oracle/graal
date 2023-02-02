---
layout: ni-docs
toc_group: debugging-and-diagnostics
link_title: JDK Flight Recorder
permalink: /reference-manual/native-image/debugging-and-diagnostics/JFR/
redirect_from: /$version/reference-manual/native-image/JFR/
---

# JDK Flight Recorder (JFR) with Native Image

JDK Flight Recorder (JFR) is an event recorder for capturing information about a JVM, and an application running on the JVM. 
GraalVM Native Image supports building a native executable with JFR events, and users can use [`jdk.jfr.Event`](https://docs.oracle.com/en/java/javase/11/docs/api/jdk.jfr/jdk/jfr/Event.html) API with a similar experience to using JFR in the Java HotSpot VM.

To record JFR events when running a native executable, JFR support and JFR recording must be enabled. 

## Add JFR Support and Record Events at Run Time

To build a native executable with the JFR events support, you first need to include JFR at build time, then enable the system, start a recording, and configure logging at native executable run time.

To build a native executable with JFR, use the `--enable-monitoring=jfr` flag:
```shell
native-image --enable-monitoring=jfr JavaApplication
```
To enable the system, start a recording, and configure logging at run time, the following options are supported:

* `-XX:+FlightRecorder`: use to enable JFR
* `-XX:StartFlightRecording`: use to start a recording on application's startup
* `-XX:FlightRecorderLogging`: use to configure the log output for the JFR system

To enable JFR and start a recording, simply use `-XX:StartFlightRecording`. 
For example:
```shell
./javaapplication -XX:StartFlightRecording="filename=recording.jfr"
```

### Run a Demo

Transform this very simple demo application into a native image and see how to use JFR events from it.
Save the following code to the _Example.java_ file.

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

You can further configure the JFR recording or enable logging.

## Configure JFR Recording

You can configure the JFR recording by passing a comma-separated list of key-value pairs to the `-XX:StartFlightRecording` option.
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

## Current Limitations

The JFR support is still limited, for example, most VM-internal events and advanced features such as stack traces or memory leak detection are still missing. A subset of JFR features are currently available: custom and system events and disk-based recordings.
Note that: 
- JFR events recording is not supported on GraalVM distribution for Windows. 
- JFR is only supported with native executables built on GraalVM JDK 11.

### Further Reading

- [Practice how to enable JFR support with Native Image and record events at run time using a demo application](/guides/build-and-run-native-executable-with-jfr.md).

- [Create and record your first event with Java](https://docs.oracle.com/en/java/javase/17/jfapi/creating-and-recording-your-first-event.html).
