# SimpleLanguage

A simple demonstration language built using Truffle for GraalVM.

SimpleLanguage is heavily documented to explain the how and why of writing a
Truffle language. A good way to find out more is to read the source with
comments. Start reading [here](https://github.com/graalvm/simplelanguage/blob/master/language/src/main/java/com/oracle/truffle/sl/SLLanguage.java).
We also like to encourage people to clone the repository and start hacking.

This repository is licensed under the permissive UPL licence. Fork it to begin
your own Truffle language.

For instructions on how to get started please refer to [our website](http://www.graalvm.org/docs/graalvm-as-a-platform/implement-language/)

# Building for a JVM

Build the project with `mvn package`.
To run simple language using a JDK from JAVA_HOME run `./sl`.

# Building native

Build the project with `mvn package -Pnative`.
To run simple language using a JDK from JAVA_HOME run `./slnative`.
