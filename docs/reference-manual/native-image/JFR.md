---
layout: docs
toc_group: native-image
link_title: JDK Flight Recorder with Native Image
permalink: /reference-manual/native-image/JFR/
---

# JDK Flight Recorder (JFR) with Native Image

JDK Flight Recorder (JFR) is a production-time profiling system that is now supported by GraalVM Native Image.

Basically, native images that are built with `-H:+AllowVMInspection` support JFR events written in Java, and users can continue to make use of the  [`jdk.jfr.Event`](https://docs.oracle.com/en/java/javase/11/docs/api/jdk.jfr/jdk/jfr/Event.html) API and JFR itself, with a similar experience to using JFR in the Java HotSpot VM.
However, to record JFR events at run time, JFR support and JFR recording must be enabled, and this page covers how to start using JFR with native images.

### Current limitations

At the moment, the JFR support is still limited, i.e., most VM-internal events and advanced features such as stack traces or memory leak detection are still missing.
A subset of JFR features are currently available: custom and system events and disk-based recordings.
Currently JFR is only supported with native images built on GraalVM JDK 11.

## Build and Run Native Images with JFR

To build a native image with the JFR events support, you first need to include JFR at image build time.
To do so, build an image with the `-H:+AllowVMInspection` flag:
```shell
native-image -H:+AllowVMInspection JavaApplication
```

For the native image with JFR included, next step is to enable the system, start a recording, and configure logging at run time.
For that the following flags are available:

* `-XX:+FlightRecorder`: use to enable JFR
* `-XX:StartFlightRecording`: use to start a recording on application's startup
* `-XX:FlightRecorderLogging`: use to configure the log output for the JFR system

To enable JFR and start a recording, use `-XX:+FlightRecorder` and `-XX:StartFlightRecording` together.
For example:
```shell
./javaapplication -XX:+FlightRecorder -XX:StartFlightRecording="filename=recording.jfr"
```

## Run a Demo

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

The application consists of a simple class and some JDK library classes.
It creates an event, labelled with the `@Label` annotation from the `jdk.jfr.*` package.
If we run that application, it does not print anything and just runs that event.

1. Compile the Java file:
  ```shell
  javac Example.java
  ```

2. Build the application into a native image with the VM inspection enabled:
  ```shell
  native-image -H:+AllowVMInspection Example
  ```
  The `-H:+AllowVMInspection` option enables optional features such as JFR that can be used to inspect the VM.

3. Run the executable and start recording:
  ```shell
  ./example -XX:+FlightRecorder -XX:StartFlightRecording="filename=recording.jfr"
  ```
  The `-XX:+FlightRecorder` flag enables the built-in Flight Recorder and starts recording to a specified file. The `recording.jfr` file is a binary.

4. Start VisualVM. Go to **File** > **Add JFR Snapshot**, browse the generated file, _recording.jfr_, and open it.

Once opened, there is a bunch of options you can check: Monitoring, Threads, Exceptions, etc., but you should be mostly interested in the events browsing. It will look something like this:

![](/img/jfr.png)

In the follow-up sections learn how to further configure the recording or enable logging.

## Configure the Recording

You can pass a comma-separated list of key-value pairs to the `-XX:StartFlightRecording` option to further configure the recording.
For example:
```shell
-XX:StartFlightRecording="filename=recording.jfr,dumponexit=true,duration=10s"
```

The following key-value pairs are supported:

| Name | Default Value | Description|
|------|-------------|---------|
|name|none|Name that can be used to identify the recording, e.g., "name=MyRecording"|
|settings|none|Settings file (profile.jfc, default.jfc, etc.), e.g., "settings=myprofile.jfc"|
|delay|none|Delay recording start with (s)econds, (m)inutes), (h)ours), or (d)ays, e.g., "delay=5h"|
|duration|infinite (0)|Duration of recording in (s)econds, (m)inutes, (h)ours, or (d)ays, e.g., "duration=300s"|
|filename|none|Resulting recording filename, e.g., "filename=recording1.jfr"|
|maxage|no limit (0)|Maximum time to keep the recorded data on disk in (s)econds, (m)inutes, (h)ours, or (d)ays, e.g., 60m, or 0 for no limit. For example, "maxage=1d"|
|maxsize|no limit (0)|Maximum amount of bytes to keep on disk in (k)B, (M)B or (G)B, e.g., 500M, or 0 for no limit. For example, "maxsize=1G"|
|dumponexit|false|Whether to dump a running recording when the JVM shuts down, e.g., "dumponexit=true"|

## Configure JFR System Logging

The JFR system also has a separate flag `-XX:FlightRecorderLogging` to configure the logging for the JFR system.
The usage is: `-XX:FlightRecorderLogging=[tag1[+tag2...][*][=level][,...]]`.

For example:
```shell
-XX:FlightRecorderLogging=jfr,system=debug
-XX:FlightRecorderLogging=all=trace
-XX:FlightRecorderLogging=jfr*=error
```

* When this option is not set, logging is enabled at a level of `WARNING`.
* When this option is set to the empty string, logging is enabled at a level of `INFO`.
* When this option is set to "disable", logging is disabled entirely.

Available log levels are: `trace, debug, info, warning, error, off`.

Available log tags are: `all, jfr, system, event, setting, bytecode, parser, metadata, dcmd`.

Otherwise, this option expects a comma separated list of tag combinations, each with an optional wildcard (*) and level.

* A tag combination without a level is given a default level of `INFO`.
* Messages with tags that match a given tag combination will be logged if they meet the tag combination's level.
* If a tag combination does not have a wildcard, then only messages with exactly the same tags are matched. Otherwise, messages whose tags are a subset of the tag combination are matched.
* If more than one tag combination matches a message's tags, the rightmost one will apply.
* Messages with tags that do not have any matching tag combinations are set to log at a default level of `WARNING`.
* This option is case insensitive.
