# The simple language native image

Truffle language implementations can be AOT compiled using the GraalVM
[native-image](https://www.graalvm.org/docs/reference-manual/aot-compilation/)
tool.  Running `mvn package` in the simplelanguage folder also builds a
`slnative` executable This executable is the full Simple Language
implementation as a native application, and has no need for a JVM to run.
