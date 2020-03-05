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

## Testing the JFR code

Clone the graalvm-jfr-tests repo somewhere to be used in the -cp argument. Use a JDK 11 to compile into class files.

```
javac /path/to/graalvm-jfr-tests/flat/EventCommit.java


mx clean
mx build
mx native-image --allow-incomplete-classpath "-J-XX:FlightRecorderOptions=retransform=false" --no-fallback -ea -cp /path/to/graalvm-jfr-tests/flat/ EventCommit
```

See `/tmp` for a `my-recording...` jfr file

## Logging

The JFR implementation supports log levels via runtime flag:
```
-XX:FlightRecorderLogging=<value>
```

Values include:
```
trace, debug, info, warning, error
```
