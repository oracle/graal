GraalVM is a project based in Oracle Labs developing a new JIT Compiler and Polyglot Runtime for the JVM.
Further details can be found on the [OTN site](http://www.oracle.com/technetwork/oracle-labs/program-languages/overview/index.html).

[Truffle](../truffle) is a framework for implementing languages as simple interpreters and forms the basis of the Polyglot Runtime. The official Truffle documentation is maintained as [Truffle javadoc](http://graalvm.github.io/graal).

[Graal](../compiler) is a dynamic compiler written in Java that integrates with the HotSpot JVM.

[Substrate VM](../substratevm) is a framework that allows ahead-of-time (AOT) compilation of Java applications under closed-world assumption into executable images or shared objects.

## License

[Truffle](../truffle) and its dependency [Graal SDK](../sdk) are licensed under the [GPL 2 with Classpath exception](../truffle/LICENSE.GPL.md).

The [Graal compiler](../compiler) is licensed under the [GPL 2](../compiler/LICENSE.md).

The [Substrate VM](../substratevm) is licensed under the [GPL 2](../substratevm/LICENSE.md).
