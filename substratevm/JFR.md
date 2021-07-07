This page covers how to use JDK Flight Recorder (JFR) with Java Native Images

## What is JFR

JFR is a production time profiling system that is now supported for Java Native Images. Users can continue to make use of the existing public Java API (jdk.jfr.*) and JFR itself, with a similar experience to using JFR in the Java HotSpot VM.

## Current limitations

* JFR is only available for native images built on JDK 11
* Only a subset of JFR features are currently available
  * Custom and system events
  * Disk-based recordings


## Compiling with JFR

JFR must first be included at image build time. To do so, build an image with the flag `-H:+AllowVMInspection`.

For example:
```
native-image -H:+AllowVMInspection MyJavaApp
```

## Enabling JFR

For native images with JFR included, the following flags will be available at run time to enable the system, start a recording, and configure logging:


* `-XX:+FlightRecorder` : Used to enable JFR
* `-XX:StartFlightRecording` : Used to start a recording on application start up
* `-XX:FlightRecorderLogging` : Used to configure log output for the JFR system

Use `-XX:+FlightRecorder` and `-XX:StartFlightRecording` together to enable JFR and start a recording.

For example:
```
./myjavaapp -XX:+FlightRecorder -XX:StartFlightRecording="filename=recording.jfr"
```

## Options for -XX:StartFlightRecording

The `StartFlightRecording` flag can optionally be passed a comma separated list of key-value pairs to further configure the recording.

For example:
```
-XX:StartFlightRecording="filename=recording.jfr,dumponexit=true,duration=10s"
```

The following key-value pairs are supported:

| Name | Default Value | Description|
|------|-------------|---------|
|name|none|Name that can be used to identify the recording, e.g. "name=MyRecording"|
|settings|none|Settings file (profile.jfc, default.jfc, etc.), e.g. "settings=myprofile.jfc"|
|delay|none|Delay recording start with (s)econds, (m)inutes), (h)ours), or (d)ays, e.g. "delay=5h"|
|duration|infinite (0)|Duration of recording in (s)econds, (m)inutes, (h)ours, or (d)ays, e.g. "duration=300s"|
|filename|none|Resulting recording filename, e.g. "filename=recording1.jfr"|
|maxage|no limit (0)|Maximum time to keep recorded data (on disk) in (s)econds, (m)inutes, (h)ours, or (d)ays, e.g. 60m, or 0 for no limit, e.g. "maxage=1d"|
|maxsize|no limit (0)|Maximum amount of bytes to keep (on disk) in (k)B, (M)B or (G)B, e.g. 500M, or 0 for no limit, e.g. "maxsize=1G"|
|dumponexit|false|Whether to dump running recording when JVM shuts down, e.g. "dumponexit=true"|

## Options for -XX:FlightRecorderLogging

The JFR system also has a separate flag `-XX:FlightRecorderLogging` that is used to configure logging for the JFR system.

Usage: `-XX:FlightRecorderLogging=[tag1[+tag2...][*][=level][,...]]`

For example:
```
-XX:FlightRecorderLogging=jfr,system=debug
-XX:FlightRecorderLogging=all=trace
-XX:FlightRecorderLogging=jfr*=error
```

* When this option is not set, logging is enabled at a level of WARNING.
* When this option is set to the empty string, logging is enabled at a level of INFO.
* When this option is set to "disable", logging is disabled entirely.

Available log levels: `trace, debug, info, warning, error, off`

Available log tags: `all, jfr, system, event, setting, bytecode, parser, metadata, dcmd`

Otherwise, this option expects a comma separated list of tag combinations, each with an optional wildcard (*) and level.

* A tag combination without a level is given a default level of INFO.
* Messages with tags that match a given tag combination will be logged if they meet the tag combination's level.
* If a tag combination does not have a wildcard, then only messages with exactly the same tags are matched. Otherwise, messages whose tags are a subset of the tag combination are matched.
* If more than one tag combination matches a message's tags, the rightmost one will apply.
* Messages with tags that do not have any matching tag combinations are set to log at a default level of WARNING.
* This option is case insensitive.