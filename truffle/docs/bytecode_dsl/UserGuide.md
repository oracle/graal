# Bytecode DSL user guide <!-- omit in toc -->

This document explains what you can do in a Bytecode DSL interpreter and how to do it. Its goal is to introduce Bytecode DSL topics at a conceptual level. For more concrete technical details, please consult the DSL's [Javadoc](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/package-summary.html) and the generated Javadoc for your interpreter. If you haven't already, we recommend reading the [Getting Started guide](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode.test/src/com/oracle/truffle/api/bytecode/test/examples/GettingStarted.java) first.


- [Bytecode DSL from 10,000 feet](#bytecode-dsl-from-10000-feet)
  - [Phase 1: Generating the interpreter](#phase-1-generating-the-interpreter)
  - [Phase 2: Generating bytecode (parsing)](#phase-2-generating-bytecode-parsing)
  - [Phase 3: Executing the bytecode](#phase-3-executing-the-bytecode)
- [Operations](#operations)
  - [Built-in operations](#built-in-operations)
  - [Custom operations](#custom-operations)
    - [Specializations](#specializations)
    - [Bind parameters](#bind-parameters)
    - [Advanced use cases](#advanced-use-cases)
- [Locals](#locals)
  - [Accessing locals](#accessing-locals)
  - [Scoping](#scoping)
  - [Materialized local accesses](#materialized-local-accesses)
- [Control flow](#control-flow)
  - [Unstructured control flow](#unstructured-control-flow)
- [Exception handling](#exception-handling)
  - [Intercepting exceptions](#intercepting-exceptions)
- [Advanced features](#advanced-features)
  - [Cached and uncached execution](#cached-and-uncached-execution)
  - [Source information](#source-information)
  - [Instrumentation](#instrumentation)
  - [Reparsing metadata](#reparsing-metadata)
  - [Bytecode introspection](#bytecode-introspection)
  - [Reachability analysis](#reachability-analysis)
  - [Interpreter optimizations](#interpreter-optimizations)
  - [Runtime compilation](#runtime-compilation)
  - [Serialization](#serialization)
  - [Continuations](#continuations)
  - [Builtins](#builtins)

## Bytecode DSL from 10,000 feet
At a high level, there are three phases in the development lifecycle of a Bytecode DSL interpreter.
As a developer, it is helpful to keep these phases separate in your mind.

### Phase 1: Generating the interpreter
The interpreter is generated automatically from a Bytecode DSL specification.
This specification takes the form of a class definition annotated with [`@GenerateBytecode`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/GenerateBytecode.java) with some custom operation definitions.
Operations are the semantic building blocks for Bytecode DSL programs (they are discussed in more detail [later](#operations)). Common behaviour is implemented by built-in operations, and language-specific behaviour is implemented by user-defined custom operations.

Below is a very simple example of an interpreter specification with a single custom `Add` operation:
```java
@GenerateBytecode(...)
public abstract static class SampleInterpreter extends RootNode implements BytecodeRootNode {
    @Operation
    public static final class Add {
        @Specialization
        public static int doInts(int a, int b) {
            return a + b;
        }
    }
}
```

When this class is compiled, the Bytecode DSL annotation processor parses the specification and uses it to generate a `SampleInterpreterGen` class.
This class defines an instruction set (including instructions for each custom operations), a `Builder` to generate bytecode, an interpreter to execute bytecode, and various supporting code.
You may find it useful to read through the generated code (it is intended to be human-readable).

### Phase 2: Generating bytecode (parsing)
Now that we have an interpreter, we need to generate some concrete bytecode for it to execute.
This process is called _parsing_.
Each method/function in your guest language is parsed to its own bytecode.

Parsers specify a tree of operations by calling a sequence of methods defined on the generated `Builder` class.
The `Builder` class is responsible for validating the well-formedness of these operations and converting them to low-level bytecodes that implement their behaviour.

Below is a simple example that generates a bytecode program to add two integer arguments together and return the result:
```java
BytecodeParser<SampleInterpreterGen.Builder> parser = (SampleInterpreterGen.Builder b) -> {
    b.beginRoot();
        b.beginReturn();
            b.beginAdd();
                b.emitLoadArgument(0);
                b.emitLoadArgument(1);
            b.endAdd();
        b.endReturn();
    b.endRoot();
}
BytecodeRootNodes<SampleInterpreter> rootNodes = SampleInterpreterGen.create(getLanguage(), BytecodeConfig.DEFAULT, parser);
```

You can typically implement a parser using an AST visitor. See the [Parsing tutorial](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode.test/src/com/oracle/truffle/api/bytecode/test/examples/ParsingTutorial.java) for an example.

### Phase 3: Executing the bytecode
The result of parsing is a [`BytecodeRootNodes`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/BytecodeRootNodes.java) object containing one or more parsed root nodes.
Each root node has bytecode that can be executed by calling the root node:

```java
SampleInterpreter rootNode = rootNodes.getNode(0);
rootNode.getCallTarget().call(40, 2); // produces 42
```

Custom operations and runtime code sometimes need to access information about the program and its execution state at run time.
The current state is encapsulated in a [`BytecodeNode`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/BytecodeNode.java), which contains the bytecode, supporting metadata, and any profiling data.
`BytecodeNode` defines helper methods for accessing local variables, computing source information, introspecting bytecode, and many other use cases.
It is worth familiarizing yourself with its APIs.

The `BytecodeNode` (and the bytecode itself) can change over the execution of a program for various reasons (e.g., [transitioning from uncached to cached](#cached-and-uncached-execution), [reparsing metadata](#reparsing-metadata), [quickening](Optimization.md#quickening)), so you should use `BytecodeRootNode#getBytecodeNode()` to obtain the up-to-date bytecode node each time you need it.
Custom operations can also `@Bind BytecodeNode` in their specializations (see [Bind parameters](#bind-parameters)).

Note that since the bytecode can change, a bytecode index on its own _does not meaningfully identify a location in the program_: it only has meaning in the context of an accompanying `BytecodeNode`.
If you need to persist a location, you can instantiate a [`BytecodeLocation`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/BytecodeLocation.java) using `BytecodeNode#getBytecodeLocation(int)` (or bind it with `@Bind BytecodeLocation`).


## Operations
Operations are the basic unit of language semantics in Bytecode DSL.
Each operation performs some computation and can produce a value.
For example, the `LoadArgument` operation produces the value of a given argument.

An operation can have children that produce inputs to the operation.
For example, an `Equals` operation may have two child operations that produce the operands to compare for equality.
Usually, child operations execute before their parent, and their results are passed as arguments to the parent.

We specify the semantics for a Bytecode DSL program by building a tree of operations.
Consider the following pseudocode:

```python
if x == 42:
  print("success")
```

This code could be represented with the following operation tree:

```lisp
(IfThen
  (Equals
    (LoadLocal x)
    (LoadConstant 42))
  (CallFunction
    (LoadGlobal (LoadConstant "print"))
    (LoadConstant "success")))
```

Note that while we describe a program as a tree of operations, Bytecode DSL interpreters _do not construct or execute ASTs_.
The bytecode builder takes an operation tree specification via a sequence of method calls (e.g., `beginIfThen()`, `endIfThen()`) and automatically synthesizes a bytecode program that implements the operation tree.

Bytecode DSL interpreters have two kinds of operations: built-in and custom.


### Built-in operations

Every Bytecode DSL interpreter comes with a predefined set of built-in operations.
They model common language primitives, such as constant accesses (`LoadConstant`), local variable manipulation (`LoadLocal`, `StoreLocal`), and control flow (`IfThen`, `While`, etc.).
The built-in operations are:


- `Root`: defines a root node
- `Return`: returns a value from the root node
- `Block`: sequences multiple operations; produces the value of the last operation
- Value producers
  - `LoadConstant`: produces a non-`null` constant value
  - `LoadNull`: produces `null`
  - `LoadArgument`: produces the value of an argument
- Local variable operations (see [Locals](#locals))
  - `LoadLocal`
  - `StoreLocal`
  - `LoadLocalMaterialized`
  - `StoreLocalMaterialized`
- Control flow operations (see [Control flow](#control-flow))
  - `IfThen`
  - `IfThenElse`
  - `Conditional`
  - `While`
  - `Label`, `Branch` (see [Unstructured control flow](#unstructured-control-flow))
- Exception handler operations (see [Exception handling](#exception-handling))
  - `TryCatch`
  - `FinallyTry`
  - `FinallyTryCatch`
  - `LoadException`
- Source operations (see [Source information](#source-information))
  - `Source`
  - `SourceSection`
- Instrumentation operations (see [Instrumentation](#instrumentation))
  - `Tag`
- Continuation operations (see [Continuations](#continuations))
  - `Yield`


The built-in operations are described here for discoverability.
Please refer to the Javadoc of the generated `Builder` methods (e.g., `Builder#beginIfThen`, `Builder#emitLoadConstant`) for their precise semantics.

### Custom operations

Custom operations are provided by the language.
They model language-specific behaviour, such as arithmetic operations, value conversions, or function calls.
Here, we discuss regular custom operations that eagerly evaluate their
children; Bytecode DSL also supports [short circuit operations](ShortCircuitOperations.md).

Custom operations are defined using Java classes in one of two ways:

1. Typically, operations are defined as inner classes of the root class annotated with [`@Operation`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/Operation.java).
2. To support migration from an AST interpreter, custom operations can also be *proxies* of existing existing Truffle node classes. To define an operation proxy, the root class should have an [`@OperationProxy`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/OperationProxy.java) annotation referencing the node class, and the node class itself should be marked `@OperationProxy.Proxyable`. Proxied nodes have additional restrictions compared to regular Truffle AST nodes, so making a node proxyable can require some (minimal) refactoring.

The example below defines two custom operations, `OperationA` and `OperationB`:
```java
@GenerateBytecode(...)
@OperationProxy(OperationB.class)
public abstract class MyBytecodeRootNode extends RootNode implements BytecodeRootNode {
    ...
    @Operation
    public static final OperationA {
        @Specialization
        public static int doInt(VirtualFrame frame, int num) { ... }

        @Specialization
        public static Object doObject(Object obj) { ... }
    }
}

@OperationProxy.Proxyable
public abstract OperationB extends Node {
    @Specialization
    public static void doInts(int a, int b) { ... }

    @Specialization
    public static void doStrings(String a, String b) { ... }
}

```

#### Specializations

Operation classes define their semantics using `@Specialization`s just like Truffle DSL nodes.
These specializations can use the same expressive conveniences (caches, bind expressions, etc.).

Specializations can declare an optional `VirtualFrame` parameter as the first parameter, and they may declare Truffle DSL parameters (`@Cached`, `@Bind`, etc.).
The rest of the parameters are called _dynamic operands_.

All specializations must have the same number of dynamic operands and must all be `void` or non-`void`; these attributes make up the _signature_ for an operation.
The value of each dynamic operand is supplied by a child operation; thus, the number of dynamic operands defines the number of child operations.
For example, `OperationA` above has one dynamic operand, so it requires one child operation; `OperationB` has two dynamic operands, so it requires two children.

#### Bind parameters

Specializations can use [`@Bind`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.dsl/src/com/oracle/truffle/api/dsl/Bind.java) to bind and execute expressions.
The values produced by these expressions are passed as specialization arguments.
Bytecode DSL interpreters have special bind variables that can be used to reference interpreter state:

- `$rootNode` evaluates to the bytecode root node
- `$bytecode` evaluates to the current `BytecodeNode`
- `$bytecodeIndex` evaluates to the current bytecode index (as `int`)

To bind certain Bytecode DSL state directly, you can often omit the expression and rely on the default bind expressions for their types:

- `@Bind MyBytecodeRootNode` binds the bytecode root node
- `@Bind BytecodeNode` binds the current `BytecodeNode`
- `@Bind BytecodeLocation` binds the current `BytecodeLocation` (constructing it from the current bytecode index and `BytecodeNode`).
- `@Bind Instruction` binds the `Instruction` introspection object for the current instruction.
- `@Bind BytecodeTier` binds the current `BytecodeTier`.


#### Advanced use cases

An operation can take zero or more values for its last dynamic operand by declaring the last dynamic operand [`@Variadic`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/Variadic.java).
The builder will emit code to collect these values into an `Object[]`.

An operation can also define _constant operands_, which are embedded in the bytecode and produce partial evaluation constant values, by declaring [`@ConstantOperand`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/ConstantOperand.java)s.

An operation may need to produce more than one result, or to modify local variables. For either case, the operation can use [`LocalSetter`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/LocalSetter.java) or [`LocalSetterRange`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/LocalSetterRange.java).

Regular operations eagerly execute their children. There are also [short circuit operations](ShortCircuitOperations.md) to implement short-circuit behaviour.

## Locals

Bytecode DSL supports local variables using its [`BytecodeLocal`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/BytecodeLocal.java) abstraction.
You can allocate a `BytecodeLocal` in the current frame using the builder's `createLocal` method.

### Accessing locals
In bytecode, you can use `LoadLocal` and `StoreLocal` operations to access the local.
The following code allocates a local, stores a value into it, and later loads the value back:
```java
b.beginBlock();
  BytecodeLocal local = b.createLocal();

  b.beginStoreLocal(local);
    // ...
  b.endStoreLocal();

  // ...
  b.emitLoadLocal(local);
b.endBlock();
```

All local accesses must be (directly or indirectly) nested within the operation that created the local.

`LoadLocal` and `StoreLocal` are the preferred way to access locals because they are efficient and can be quickened to [avoid boxing](Optimization.md#boxing-elimination).
You can also access locals using [`LocalSetter`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/LocalSetter.java), [`LocalSetterRange`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/LocalSetterRange.java), or various helper methods on the [`BytecodeNode`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/BytecodeNode.java).

It is undefined behaviour to load a local before a value is stored into it.


### Scoping

By default, interpreters use _local scoping_, in which locals are scoped to the enclosing `Root` or `Block` operation.
When exiting the enclosing operation, locals are cleared and their frame slots are automatically reused.
Since the set of live locals depends on the location in the code, most of the local accessor methods on `BytecodeNode` are parameterized by the current `bytecodeIndex`.

Interpreters can alternatively opt to use _global scoping_, in which all locals get a unique position in the frame and live for the entire extent of the root.
The setting is controlled by the `enableLocalScoping` flag in [`@GenerateBytecode`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/GenerateBytecode.java).

### Materialized local accesses

The plain `LoadLocal` and `StoreLocal` operations access locals from the current frame.
In some cases, you may need to access locals from a different frame; for example, if root nodes are nested, an inner root may need to access locals of the outer root.

The `LoadLocalMaterialized` and `StoreLocalMaterialized` operations are intended for such cases.
They take an extra operand for the frame to read from/write to; this frame must be materialized.
They can only access locals of the current root or an enclosing root.

Below is a simple example where the inner root reads the outer local from the outer root's frame.
```java
b.beginRoot(); // outer root
  b.beginBlock();
    var outerLocal = b.createLocal();
    // ...
    b.beginRoot(); // inner root
      b.beginLoadLocalMaterialized(outerLocal);
        b.emitGetOuterFrame(); // produces materialized frame of outer root
      b.endLoadLocalMaterialized();
    b.endRoot();
  b.endBlock();
b.endRoot();
```

Materialized accesses should be used carefully.
It is undefined behaviour to access an outer local that is not currently in scope.
The bytecode builder endeavours to prevent such errors, but it is not always possible.
It is also undefined behaviour to access a local using a materialized frame that does not contain the local (i.e., the frame of a different root node).


## Control flow

The `IfThen`, `IfThenElse`, `Conditional`, and `While` operations can be used for structured control flow.
Their behaviour is as you would expect.
`Conditional` produces a value; the rest do not.

For example, the code below declares an `IfThenElse` operation that executes different code depending on the value of the first argument:
```java
b.beginIfThenElse();
  b.emitLoadArgument(0); // first child: condition
  b.beginBlock(); // second child: positive branch
    ...
  b.endBlock();
  b.beginBlock(); // third child: negative branch
    ...
  b.endBlock();
b.endIfThenElse();
```

### Unstructured control flow

A limited form of unstructured control flow is also possible in Bytecode DSL interpreters using labels and forward branches.

Parsers can allocate a `BytecodeLabel` using the builder's `createLabel` method.
The label should be emitted using `emitLabel` at some location in the same block, and can be branched to using `emitBranch`.

The following code allocates a label, emits a branch to it, and then emits the label at the location to branch to:
```java
b.beginBlock();
  BytecodeLabel label = b.createLabel();
  // ...
  b.emitBranch(label);
  // ...
  b.emitLabel(label);
b.endBlock();
```

When executed, control will jump from the branch location to the label location.

There are some restrictions on the kinds of branches allowed:

1. Any branch must be (directly or indirectly) nested in the label's creating operation (a `Root` or `Block`). That is, you cannot branch into an operation, only across or out of it.
2. Only forward branches are supported. For backward branches, use `While` operations.

Unstructured control flow is useful for implementing loop breaks, continues, and other more advanced control flow (like `switch`).

## Exception handling

Bytecode DSL interpreters have three built-in exception handler operations.

- `TryCatch` executes a `try` operation (its first child), and if a Truffle exception is thrown, executes a `catch` operation (its second child).

- `FinallyTry` executes a `try` operation (its second child), and ensures a `finally` operation (its first child) is always executed, even if a Truffle exception is thrown. If an exception was thrown, it rethrows the exception afterward.

- `FinallyTryCatch` has the same behaviour as `FinallyTry`, except it has a `catch` operation (its third child) that it executes when a Truffle exception is thrown.

The bytecode for `finally` operations may be emitted multiple times (once for each exit point of `try`, including at early returns).
To support emitting it multiple times, the `finally` operation is defined using a `Runnable` parser that can be repeatedly invoked (it must be idempotent).
The naming of the `FinallyTry` and `FinallyTryCatch` operations reflects the fact that the finally parser is supplied at the beginning of parsing, _not_ that the `finally` operation executes first.


The `LoadException` operation can be used within the `catch` operation of a `TryCatch` or `FinallyTryCatch` to read the current exception.

### Intercepting exceptions

Before an exception handler executes, you may wish to intercept the exception for a variety of reasons, like:
- adding metadata to the exception
- handling control flow exceptions
- converting an internal host exception (e.g., stack overflow) to a guest exception

[`BytecodeRootNode`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/BytecodeRootNode.java) defines a set of hooks (`interceptTruffleException`, `interceptControlFlowException`, and `interceptInternalException`) that you can override.
These hooks will be invoked before the exception is dispatched to a handler.

## Advanced features

This section describes some of the more advanced features supported by Bytecode DSL interpreters.

### Cached and uncached execution

By default, Bytecode DSL interpreters execute _cached_, allocating memory to profile conditional branches, operation specializations, and more.
These profiles allow Truffle compilation to produce highly optimized code.
However, for cold code that will not be compiled (e.g., because it only runs once or twice), the extra memory allocated is wasteful.

Bytecode DSL interpreters support an _uncached_ execution mode that allocates no memory for profiling (see the `enableUncachedInterpreter` flag in [`@GenerateBytecode`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/GenerateBytecode.java)).
When uncached execution is enabled, an interpreter starts executing as uncached.
No profiling data is collected, and each custom operation executes uncached (i.e., no specialization data is recorded).
After a certain number of calls or loop iterations, an uncached interpreter will transition to cached, allocating profiling data and preparing the interpreter for compilation.

It is strongly recommended to enable uncached execution, because it can reduce the footprint of your language and improve start-up times.

Most interpreters support uncached execution without any extra development effort.
However, if your interpreter uses [`@OperationProxy`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/OperationProxy.java), any proxied nodes must be made compatible with uncached execution.
The Bytecode DSL processor will inform you of any changes that need to be made.


### Source information

The `Source` and `SourceSection` operations associate source ranges with each operation in a program.
There are several `getSourceLocation` methods defined by [`BytecodeNode`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/BytecodeNode.java) that can be used to compute source information for a particular bytecode index, frame instance, etc.

It is recommended to enclose the `Root` operation in appropriate `Source` and `SourceSection` operations in order to provide accurate source information for the root node.
The generated root node will override `Node#getSourceSection` to return this information.

Source information is designed to have no performance overhead until it is requested (see [Reparsing metadata](#reparsing-metadata)).


### Instrumentation

The behaviour of a Bytecode DSL interpreter can be non-intrusively observed (and modified) using instrumentation.
For example, you can instrument your code to trace each guest language statement, or add instrumentation to log return values.

Instrumentations are specified during parsing, but disabled by default.
They incur no overhead until they are enabled at a later time (see [Reparsing metadata](#reparsing-metadata)).

Bytecode DSL supports two forms of instrumentation:

1. [`@Instrumentation`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/Instrumentation.java) operations, which are emitted and behave just like custom [`@Operation`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/Operation.java)s. These operations can perform special actions like logging or modifying the value produced by another operation. `@Instrumentation` operations must have no stack effects, so they can either have no children and produce no value, or have one child and produce a value.
2. Tag-based instrumentation associates operations with particular instrumentation [`Tag`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.instrumentation/src/com/oracle/truffle/api/instrumentation/Tag.java)s using `Tag` operations. If these instrumentations are enabled and [`ExecutionEventNode`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.instrumentation/src/com/oracle/truffle/api/instrumentation/ExecutionEventNode.java)s are attached, the bytecode interpreter will invoke the various event callbacks (e.g., `onEnter`, `onReturnValue`) when executing the enclosed operation. Tag-based instrumentation can be enabled using the `enableTagInstrumentation` flag in [`@GenerateBytecode`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/GenerateBytecode.java).

Note: once instrumentations are enabled, they cannot be disabled.

### Reparsing metadata

Source information and instrumentation require root nodes to store additional metadata (e.g., tables mapping bytecode indices to source ranges).
This metadata can contribute significantly to the interpreter footprint, which is especially wasteful if the information is rarely used.
Bytecode DSL allows a language to omit metadata and *reparse* a node when missing metadata is required.

For example, the default parsing mode excludes all metadata; operations like `Source`, `SourceSection`, and `Tag` produce no metadata and have no overhead.
If metadata is required at a later point in time – for example, a source section is requested — the generated interpreter will re-run the parser and store the metadata for subsequent use.
It may make sense to request some metadata (e.g., source information) on first parse if it is frequently used.

In order to support reparsing, [`BytecodeParser`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/BytecodeParser.java)s **must** be deterministic and idempotent.
When extra metadata is requested, the parser is invoked again, and is expected to perform the same series of builder calls.
The parse result (the [`BytecodeRootNodes`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/BytecodeRootNodes.java)) keeps a reference to the parser, so keep in mind that any strong references the parser holds may keep heap memory alive (e.g., source file contents or ASTs).

The metadata to include is specified by a [`BytecodeConfig`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/BytecodeConfig.java).
There are some predefined configurations like `BytecodeConfig.DEFAULT` for convenience, but you can also specify a specific configuration using a `BytecodeConfig.Builder` (use the static `newConfigBuilder` method on the generated class). Metadata is only added; there is no way to "clear" information by requesting less information in a reparse.


### Bytecode introspection
Bytecode DSL interpreters have various APIs that allow you to introspect the bytecode.
These methods, defined on the [`BytecodeNode`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/BytecodeNode.java), include:

- `getInstructions`, which returns the bytecode [`Instruction`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/Instruction.java)s for the node.
- `getLocals`, which returns a list of [`LocalVariable`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/LocalVariable.java) table entries.
- `getExceptionHandlers`, which returns a list of [`ExceptionHandler`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/ExceptionHandler.java) table entries.
- `getSourceInformation`, which returns a list of [`SourceInformation`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/SourceInformation.java) table entries. There is also `getSourceInformationTree`, which encodes the entries as a [`SourceInformationTree`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/SourceInformationTree.java).

Note that the bytecode encoding is an implementation detail, so the APIs and their outputs are subject to change, so introspection should only be used for debugging purposes.


### Reachability analysis
Bytecode DSL performs some basic reachability analysis to avoid emitting bytecode when it can guarantee a location is not reachable, for example, after an explicit `Return` operation. The reachability analysis is confounded by features like branching, exception handling, and instrumentation, so reachability cannot always be precisely determined; in such cases, the builder conservatively assumes a given point in the program is reachable.

### Interpreter optimizations
Bytecode DSL supports techniques like quickening and boxing elimination to improve interpreted (non-compiled) performance.
Refer to the [Optimization guide](Optimization.md) for more details.

### Runtime compilation

Like Truffle AST interpreters, Bytecode DSL interpreters use partial evaluation (PE) to implement runtime compilation.
Runtime compilation is automatically supported, but there are some subtle details to know when implementing your interpreter.
See the [Runtime compilation guide](RuntimeCompilation.md) for more details.

### Serialization
Bytecode DSL interpreters can support serialization, which allows a language to implement bytecode caching (like Python's `.pyc` files). See the [Serialization tutorial](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode.test/src/com/oracle/truffle/api/bytecode/test/examples/SerializationTutorial.java) for more details.

### Continuations
Bytecode DSL supports single-method continuations, whereby a root node is suspended and can be resumed at a later point in time.
Continuations can be used to implement language features like coroutines and generators that suspend the state of the current method. See the [Continuations tutorial](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode.test/src/com/oracle/truffle/api/bytecode/test/examples/ContinuationsTutorial.java) for more details.


### Builtins
Guest language builtins integrate easily with Bytecode DSL. The [Builtins tutorial](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode.test/src/com/oracle/truffle/api/bytecode/test/examples/BuiltinTutorial.java) describes a few different approaches you may wish to use to define your language builtins within Bytecode DSL.
