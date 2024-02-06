# Introduction to Bytecode DSL

Bytecode DSL is a DSL and runtime support component of Truffle that makes it easier to implement bytecode interpreters in Truffle. Just as Truffle DSL abstracts away the tricky and tedious details of AST interpreters, the goal of Bytecode DSL is to abstract away the tricky and tedious details of a bytecode interpreter – the bytecode format, control flow, quickening, and so on – leaving only the language-specific semantics for the language to implement.


Note: At the moment, Bytecode DSL is an **experimental feature**. We encourage you to give it a try, but be forewarned that its APIs are still susceptible to change a little bit between releases.


## Why a bytecode interpreter?

Though Truffle AST interpreters enjoy excellent peak performance, they can struggle in terms of:

- *Memory footprint*. Trees are not compact data structures. A root node's entire AST, with all of its state (e.g., `@Cached` parameters) must be allocated before it can execute. This allocation is especially detrimental for code that is only executed a handful of times (e.g., bootstrap code).
- *Interpreted performance*. AST interpreters contain many highly polymorphic `execute` call sites that are difficult for the JVM to optimize. These sites pose no problem for runtime-compiled code (where partial evaluation can eliminate the polymorphism), but cold code that runs in the interpreter suffers from poor performance.

Bytecode interpreters enjoy the same peak performance as ASTs, but they can also be encoded with less memory and are more amenable to optimization (e.g., via [host compilation](../HostCompilation.md)). Unfortunately, these benefits come at a cost: bytecode interpreters are more difficult and tedious to implement properly. Bytecode DSL reduces the implementation effort by generating a bytecode interpreter automatically from a set of AST node-like specifications called "operations".

## Bytecode DSL features

Bytecode DSL generates interpreters with all of the bells and whistles. Its features include:

- **Expressive specifications**: Operations in Bytecode DSL are written using the same DSL as AST nodes, supporting many of the same expressive conveniences: specialization, inline caches, bind variables, and so on.

- **Tiered interpretation**: To reduce start-up overhead and memory footprint, Bytecode DSL can generate an uncached interpreter that automatically switches a root node to a cached (specializing) interpreter when it gets hot.

- **Interpreter optimizations**: To improve interpreted performance, interpreters support boxing elimination, quickening, and more (see [Optimization](Optimization.md)). They also make use of Truffle's [host compilation](../HostCompilation.md). In the future, Bytecode DSL will support superintructions and automated tracing to infer quicken/superinstruction candidates.

- **Continuations**: Bytecode DSL interpreters support single-method continuations, which allow a method to be suspended and resumed at a later time (see the [Continuations tutorial][continuations]). 

- **Serialization**: Bytecode DSL interpreters support serialization/deserialization, which enables a language to cache the bytecode for a guest program (see the [Serialization tutorial][serialization]).

- **Lazy source and instrumentation metadata**: Source and instrumentation information increase the footprint of your interpreter. Bytecode DSL interpreters *reparse* guest programs to lazily compute this information only when it is needed.

- **Instrumentation**: Bytecode DSL intepreters support instrumentation operations. These operations can be enabled on demand, and have no performance overhead when not enabled. In the future, Bytecode DSL will support instrumentation on operation entry/exit.

## Resources

As a next step, we recommend reading the [Getting Started guide](GettingStarted.md), which introduces Bytecode DSL by implementing a simple interpreter.

For more technical details about Bytecode DSL, consult the [User guide](UserGuide.md) and [Javadoc](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/package-summary.html).
See also the guides and tutorials on [optimization](Optimization.md), [serialization][serialization], and [continuations][continuations].

The Bytecode DSL implementations for [SimpleLanguage](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.sl/src/com/oracle/truffle/sl/bytecode/SLBytecodeRootNode.java) and [GraalPython](https://github.com/oracle/graalpython/blob/master/graalpython/com.oracle.graal.python/src/com/oracle/graal/python/nodes/bytecode_dsl/PBytecodeDSLRootNode.java) may also serve as useful references.




[serialization]: https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode.test/src/com/oracle/truffle/api/bytecode/test/examples/SerializationTutorial.java
[continuations]: https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode.test/src/com/oracle/truffle/api/bytecode/test/examples/ContinuationsTutorial.java