## JFR Implementation Notes

Implementation is based off of jdk/jdk commit rev

d2816784605b

## JDK Matrix

This branch builds with:
labsjdk-ce-11.0.9-jvmci-20.3-b06

Tarballs can be found at:
https://github.com/graalvm/labs-openjdk-11/releases

For example:
https://github.com/graalvm/labs-openjdk-11/releases/download/jvmci-20.3-b06/labsjdk-ce-11.0.9+10-jvmci-20.3-b06-linux-amd64.tar.gz

## Building a native image with JFR

The JFR implementation is included at compile time via the flag:
`-H:+FlightRecorder`

## Running tests from the jfr-tests repo
Clone the https://github.com/rh-jmc-team/jfr-tests repo to be used in the -cp argument. Follow its readme to compile class files via Maven.

Then compile the classes to a native image, for example:
```
mx build
mx native-image -H:+FlightRecorder --no-fallback -cp /path/to/jfr-tests/target/classes com.redhat.jfr.tests.event.TestConcurrentEvents
./com.redhat.jfr.tests.event.testconcurrentevents
```

See `/tmp` for the jfr file

## Logging

The JFR implementation supports log levels via the runtime flag:
```
-XX:FlightRecorderLogging=<value>
```

Values include:
```
trace, debug, info, warning, error
```

An empty value is treated as `error`

For example:
```
./com.redhat.jfr.tests.event.testconcurrentevents -XX:FlightRecorderLogging=
```
