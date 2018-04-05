GraalVM is a project based in Oracle Labs developing a new JIT Compiler and Polyglot Runtime for the JVM.
Further details can be found on the [OTN site](http://www.oracle.com/technetwork/oracle-labs/program-languages/overview/index.html).

* [Graal SDK](../sdk) contains long term supported APIs of GraalVM.

* [Graal](../compiler) is a dynamic compiler written in Java that integrates with the HotSpot JVM.

* [Truffle](../truffle) is a framework for implementing languages and instruments that use Graal as a dynamic compiler.

* [Tools](../tools) contains a set of tools for Truffle guest languages implemented using the instrumentation framework.

* [Substrate VM](../substratevm) is a framework that allows ahead-of-time (AOT) compilation of Java applications under closed-world assumption into executable images or shared objects.

* [TRegex](../regex) is an implementation of regular expressions which leverages Graal and Truffle for efficient compilation of automata.

## License

[Truffle](../truffle) and its dependency [Graal SDK](../sdk) are licensed under the [GPL 2 with Classpath exception](../truffle/LICENSE.GPL.md).

The [Tools](../tools) project is licensed under the [GPL 2 with Classpath exception](../tools/LICENSE.GPL.md).

The [TRegex](../regex) project is licensed under the [GPL 2 with Classpath exception](../regex/LICENSE.GPL.md).

The [Graal compiler](../compiler) is licensed under the [GPL 2](../compiler/LICENSE.md).

The [Substrate VM](../substratevm) is licensed under the [GPL 2](../substratevm/LICENSE.md).
