# Introduction to the Bytecode DSL

The Bytecode DSL is a DSL for automatically generating bytecode interpreters in Truffle. Just as Truffle DSL abstracts away the tricky and tedious details of AST interpreters, the goal of the Bytecode DSL is to abstract away the tricky and tedious details of a bytecode interpreter – the bytecode encoding, control flow, quickening, and so on – leaving only the language-specific semantics for the language to implement.

This document is the starting point for learning about the Bytecode DSL. See the [resources](#resources) section below for more guides and tutorials.

Note: At the moment, the Bytecode DSL is an **experimental feature**. We encourage you to give it a try, but be forewarned that its APIs are still susceptible to change a little bit between releases.

## Why a bytecode interpreter?

Though Truffle AST interpreters enjoy excellent peak performance, they can struggle in terms of:

- *Memory footprint*. Trees are not a compact program representation. A root node's entire AST, with all of its state (e.g., `@Cached` parameters) must be allocated before it can execute. This allocation is especially detrimental for code that is only executed a handful of times (e.g., bootstrap code).
- *Interpreted performance*. Before an AST is hot enough to be runtime-compiled, it runs in the interpreter in plain Java code. Techniques like specialization and boxing avoidance can improve interpreted performance, but optimization opportunities are otherwise limited. The JVM can JIT compile the interpreter code itself, and sometimes it can use type profiles to inline method calls, but AST interpreters often have megamorphic `execute` call sites that prevent inlining.

Bytecode interpreters enjoy the same peak performance as ASTs, but they can be encoded with less memory.
Moreover, there are several techniques available to improve the interpreted performance of bytecode interpreters, including quickening, superinstructions, [host compilation](../HostCompilation.md), and template compilation.

The downside to bytecode interpreters is that they are more difficult to implement properly. The Bytecode DSL reduces the implementation effort by generating a bytecode interpreter automatically from a set of AST node-like specifications called "operations".

## Sample interpreter

Below is the complete Bytecode DSL specification for a small interpreter with a custom `Add` operation.
```java
@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class)
public abstract static class SampleInterpreter extends RootNode implements BytecodeRootNode {

    protected SampleInterpreter(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @Operation
    public static final class Add {
        @Specialization
        public static int doInts(int a, int b) {
            return a + b;
        }

        @Specialization
        public static String doStrings(String a, String b) {
            return a + b;
        }
    }
}
```

From this specification, the Bytecode DSL generates a bytecode interpreter in `SampleInterpreterGen`.

The generated code contains a builder class that automatically generates bytecode from a series of builder calls. We can build a bytecode program that adds two arguments as follows.

```java
var rootNodes = SampleInterpreterGen.create(getLanguage(), BytecodeConfig.DEFAULT, b -> {
    b.beginRoot();
        b.beginReturn();
            b.beginAdd();
                b.emitLoadArgument(0);
                b.emitLoadArgument(1);
            b.endAdd();
        b.endReturn();
    b.endRoot();
});
SampleInterpreter rootNode = rootNodes.getNode(0);
```

The code above generates a bytecode program that we can execute. We can peek into the details by printing the result of `rootNode.dump()`:

```
UninitializedBytecodeNode(name=null)[
    instructions(4) =
          0 [000] 00a load.argument  index(0)
          1 [004] 00a load.argument  index(1)
          2 [008] 01b c.Add          node(null)
          3 [00e] 003 return
    exceptionHandlers(0) = Empty
    locals(0) = Empty
    sourceInformation(-) = Not Available
    tagTree = Not Available
]
```

To execute this bytecode, we simply invoke the call target:

```java
RootCallTarget callTarget = rootNode.getCallTarget();
assertEquals(42, callTarget.call(40, 2));
assertEquals("Hello, world!", callTarget.call("Hello, ", "world!"));
```

## Features

The Bytecode DSL supports a variety of features, including:

- **Expressive specifications**: Operations in the Bytecode DSL are written using the same DSL as AST nodes, supporting many of the same expressive conveniences: specializations, inline caches, bind variables, and so on.

- **Tiered interpretation**: To reduce start-up overhead and memory footprint, the Bytecode DSL can generate an uncached interpreter that automatically switches to a cached (specializing) interpreter when it gets hot, on a per-`RootNode` basis.

- **Optimizations**: To improve interpreted performance, bytecode interpreters support boxing elimination, quickening, and more (see [Optimization](Optimization.md)). They also make use of Truffle's [host compilation](../HostCompilation.md). In the future, the Bytecode DSL will support superintructions and automatic inference of quicken/superinstruction candidates.

- **Continuations**: Bytecode DSL interpreters support single-method continuations, which allow a method to be suspended and resumed at a later time (see the [Continuations tutorial][continuations]).

- **Serialization**: Bytecode DSL interpreters support serialization/deserialization, which enables a language to persist the bytecode for a guest program and reconstruct it without reprocessing the source program (see the [Serialization tutorial][serialization]).

- **Instrumentation**: Bytecode DSL interpreters support special instrumentation operations and tag-based instrumentation (see the [Instrumentation tutorial][instrumentation]).

- **Lazy source and instrumentation metadata**: Source and instrumentation metadata increase the footprint of the interpreter. By default, Bytecode DSL interpreters elide this metadata when building bytecode, so they have no footprint overhead when they are not used. The metadata is recomputed on demand by replaying the builder calls.

## Resources

As a next step, we recommend reading the [Getting Started tutorial](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode.test/src/com/oracle/truffle/api/bytecode/test/examples/GettingStarted.java), which introduces the Bytecode DSL by implementing a simple interpreter.
Afterward, consult the [User guide](UserGuide.md) and [Javadoc](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/package-summary.html) for more technical details about the Bytecode DSL.

In addition, there are several guides and tutorials which may be helpful:
- [Optimization guide](Optimization.md)
- [Short-circuit operations guide](ShortCircuitOperations.md)
- [Runtime compilation guide](RuntimeCompilation.md)
- [Parsing tutorial](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode.test/src/com/oracle/truffle/api/bytecode/test/examples/ParsingTutorial.java)
- [Serialization tutorial][serialization]
- [Continuations tutorial][continuations]
- [Builtins tutorial](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode.test/src/com/oracle/truffle/api/bytecode/test/examples/BuiltinsTutorial.java)

The Bytecode DSL implementation for [SimpleLanguage](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.sl/src/com/oracle/truffle/sl/bytecode/SLBytecodeRootNode.java) is also a useful reference.


[serialization]: https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode.test/src/com/oracle/truffle/api/bytecode/test/examples/SerializationTutorial.java
[continuations]: https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode.test/src/com/oracle/truffle/api/bytecode/test/examples/ContinuationsTutorial.java
[instrumentation]: https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode.test/src/com/oracle/truffle/api/bytecode/test/examples/InstrumentationTutorial.java