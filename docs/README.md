GraalVM is a project based in Oracle Labs developing a new JIT Compiler and Polyglot Runtime for the JVM.
Further details can be found on the [OTN site](http://www.oracle.com/technetwork/oracle-labs/program-languages/overview/index.html).

[Truffle](https://github.com/graalvm/graal/tree/master/truffle) is a framework for implementing languages as simple interpreters and forms the basis of the Polyglot Runtime. The official Truffle documentation is maintained as [Truffle javadoc](http://graalvm.github.io/graal).

[Graal](https://github.com/graalvm/graal/tree/master/compiler) is a dynamic compiler written in Java that integrates with the HotSpot JVM.

[Substrate VM](https://github.com/graalvm/graal/tree/master/substratevm) is a framework that allows ahead-of-time (AOT) compilation of Java applications under closed-world assumption into executable images or shared objects.

## License

[Truffle](https://github.com/graalvm/graal/tree/master/truffle) and its dependency [Graal SDK](https://github.com/graalvm/graal/tree/master/sdk) are licensed under the [GPL 2 with Classpath exception](https://github.com/graalvm/graal/blob/master/truffle/LICENSE.GPL.md).

The [Graal compiler](https://github.com/graalvm/graal/tree/master/compiler) is licensed under the [GPL 2](https://github.com/graalvm/graal/blob/master/compiler/LICENSE.md).

The [Substrate VM](https://github.com/graalvm/graal/tree/master/substratevm) is licensed under the [GPL 2](https://github.com/graalvm/graal/blob/master/substratevm/LICENSE.md).
