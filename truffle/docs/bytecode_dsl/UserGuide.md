# Bytecode DSL user guide <!-- omit in toc -->

This document explains what you can do in a Bytecode DSL interpreter and how to do it. Its goal is to introduce Bytecode DSL topics at a conceptual level. For more concrete technical details, please consult the DSL's [Javadoc](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/package-summary.html) and the generated Javadoc for your interpreter. If you haven't already, we recommend reading the [Getting Started guide](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode.test/src/com/oracle/truffle/api/bytecode/test/examples/GettingStarted.java) first.


- [Operations](#operations)
  - [Built-in operations](#built-in-operations)
  - [Custom operations](#custom-operations)
    - [Specializations](#specializations)
    - [Advanced use cases](#advanced-use-cases)
- [Bytecode nodes](#bytecode-nodes)
- [Locals](#locals)
  - [Accessing locals](#accessing-locals)
  - [Scoping](#scoping)
  - [Materialized local accesses](#materialized-local-accesses)
- [Control flow](#control-flow)
  - [Unstructured control flow](#unstructured-control-flow)
- [Exception handling](#exception-handling)
- [Features](#features)
  - [Cached and uncached execution](#cached-and-uncached-execution)
  - [Source information](#source-information)
  - [Instrumentation](#instrumentation)
  - [Bytecode index introspection](#bytecode-index-introspection)
  - [Reparsing metadata](#reparsing-metadata)
  - [Serialization](#serialization)
  - [Continuations](#continuations)


## Operations
Operations are the basic unit of language semantics in Bytecode DSL.
Each operation performs some computation and can produce a value.
For example, the `LoadArgument` operation produces the value of a given argument.

Operations can have children.
For example, an `Equals` operation may have two child operations that produce its operands.
Usually, child operations execute before their parent, and their results are passed as arguments to the parent.

A Bytecode DSL program is conceptually a "tree" of operations.
Consider the following pseudocode:

```
if x == 42:
  print("success")
```

This code could be represented with the following operation tree:

```
(IfThen
  (Equals
    (LoadLocal x)
    (LoadConstant 42))
  (CallFunction
    (LoadGlobal (LoadConstant "print"))
    (LoadConstant "success")))
```

Note that while we describe a program as a tree of operations, Bytecode DSL interpreters _do not construct or execute ASTs_.
The bytecode builder takes an operation tree specification via a sequence of method calls and automatically synthesizes a bytecode program that implements the operation tree.

Bytecode DSL interpreters have two kinds of operations: built-in and custom.


### Built-in operations

Every Bytecode DSL interpreter comes with a predefined set of built-in operations.
They model common language primitives, such as constant accesses (`LoadConstant`), local variable manipulation (`LoadLocal`, `StoreLocal`), and control flow (`IfThen`, `While`, etc.).

The built-in operations are listed below.
The precise semantics of these operations are described in the bytecode builder Javadoc.


- `Root`: defines a root node
- `Return`: returns a value from the root node
- `Block`: sequences multiple operations
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


### Custom operations

Custom operations are provided by the language.
They model language-specific behaviour, such as arithmetic operations, value conversions, or function calls.
Here, we discuss regular custom operations that eagerly evaluate their
children; Bytecode DSL also supports [short circuit operations](ShortCircuitOperations.md).

Custom operations are defined using Java classes in one of two ways:

1. Typically, operations are defined as inner classes of the root class annotated with [`@Operation`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode./src/com/oracle/truffle/api/bytecode/Operation.java).
2. To support migration from an AST interpreter, custom operations can also be *proxies* of existing existing Truffle node classes. To define an operation proxy, the root class should have an [`@OperationProxy`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode./src/com/oracle/truffle/api/bytecode/OperationProxy.java) annotation referencing the node class, and the node class itself should be marked `@OperationProxy.Proxyable`. Proxied nodes have additional restrictions compared to regular Truffle AST nodes, so making a node proxyable can require some (minimal) refactoring.

The example below defines two custom operations, `OperationA` and `OperationB`:
```
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

Specializations can declare an optional frame parameter as the first parameter, and they may declare Truffle DSL parameters (`@Cached`, `@Bind`, etc.).
The rest of the parameters are called _dynamic operands_.

All specializations must have the same number of dynamic operands and must all be `void` or non-`void`; these attributes make up the _signature_ for an operation.
The value of each dynamic operand is supplied by a child operation; thus, the number of dynamic operands defines the number of child operations.
For example, `OperationA` above has one dynamic operand, so it requires one child operation; `OperationB` has two dynamic operands, so it requires two children.

#### Advanced use cases

An operation can take zero or more values for its last dynamic operand by declaring a [`@Variadic`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode./src/com/oracle/truffle/api/bytecode/Variadic.java)` Object[]` as the final dynamic operand.

An operation can also define _constant operands_, which are embedded in the bytecode and produce partial evaluation constant values, by declaring [`@ConstantOperand`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode./src/com/oracle/truffle/api/bytecode/ConstantOperand.java)s.

An operation may need to produce more than one result, or to modify local variables. For either case, the operation can use [`LocalSetter`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode./src/com/oracle/truffle/api/bytecode/LocalSetter.java) or [`LocalSetterRange`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode./src/com/oracle/truffle/api/bytecode/LocalSetterRange.java).

Regular operations eagerly execute their children. There are also [short circuit operations](ShortCircuitOperations.md) to implement short-circuit behaviour.

## Bytecode nodes

The state of a Bytecode DSL interpreter is encapsulated in a [`BytecodeNode`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode./src/com/oracle/truffle/api/bytecode/BytecodeNode.java).
This class contains the bytecode and supporting metadata.
It defines several helper methods for most things languages need to do, like introspect bytecode, access local variables, or compute source information; it is worth familiarizing yourself with its APIs.
The current bytecode node can be obtained with `BytecodeRootNode#getBytecodeNode()`.

The bytecode node and the bytecode itself can change during the execution of a program for various reasons like [transitioning from uncached to cached](#cached-and-uncached-execution), [reparsing metadata](#reparsing-metadata), or [quickening](Optimization.md#quickening).
Consequently, a bytecode index on its own does not meaningfully identify a location in the program: it only has meaning in the context of an accompanying `BytecodeNode`. The [`BytecodeLocation`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode./src/com/oracle/truffle/api/bytecode/BytecodeLocation.java) abstraction comprises the bytecode index and bytecode node.

## Locals

Bytecode DSL supports local variables using its [`BytecodeLocal`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode./src/com/oracle/truffle/api/bytecode/BytecodeLocal.java) abstraction.
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

`LoadLocal` and `StoreLocal` are the preferred way to access locals because they are efficient and can be [quickened](Optimization.md#quickening).
You can also access locals using [`LocalSetter`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode./src/com/oracle/truffle/api/bytecode/LocalSetter.java), [`LocalSetterRange`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode./src/com/oracle/truffle/api/bytecode/LocalSetterRange.java), or various helper methods on the [`BytecodeNode`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode./src/com/oracle/truffle/api/bytecode/BytecodeNode.java).


### Scoping

By default, interpreters use _local scoping_, in which locals are scoped to the enclosing `Root` or `Block` operation.
When exiting the enclosing operation, locals are cleared and their frame slots are automatically reused.
Since the set of live locals depends on the location in the code, most of the local accessor methods mentioned above are parameterized by the current `bytecodeIndex`.

Interpreters can alternatively opt to use _global scoping_, in which all locals get a unique position in the frame and live for the entire extent of the root.
The setting is controlled by the `enableLocalScoping` flag in [`@GenerateBytecode`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode./src/com/oracle/truffle/api/bytecode/GenerateBytecode.java).

### Materialized local accesses

The plain `LoadLocal` and `StoreLocal` operations access locals from the current frame.
In some cases, you may need to access locals from a different frame; for example, if root nodes are nested, an inner root may need to access locals of the outer root.

The `LoadLocalMaterialized` and `StoreLocalMaterialized` operations are intended for such cases.
They take an extra operand for the frame to read from/write to; this frame must be materialized.
They can only access locals of the current root or an enclosing root.

Below is a simple example where the inner root reads the outer local from the outer root's frame.
```java
b.beginRoot(/* ... */); // outer root
  b.beginBlock();
    var outerLocal = b.createLocal();
    // ...
    b.beginRoot(/* ... */); // inner root
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
It is also undefined behaviour to access a local using a materialized frame that it does not belong to.


## Control flow

The `IfThen`, `IfThenElse`, `Conditional`, and `While` operations can be used for structured control flow.
Their behaviour is as you would expect.

For example, the code below declares an `IfThenElse` operation that executes different code depending on the value of argument `0`:
```
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

Unstructured control flow is useful for implementing loop breaks, continues, and other more advanced control flow.

## Exception handling

TODO

## Features

This section describes some of the features supported by Bytecode DSL interpreters.

### Cached and uncached execution

TODO

### Source information

Bytecode DSL keeps track of source locations using Source and SourceSection operations. The Source operation defines the `Source` of its enclosed operations. The SourceSection operation (together with the nearest enclosing Source operation) defines the source range corresponding to the enclosed operations.

**TODO**: this changes with the bci rework

The RootNode itself will report as its location (via `getSourceSection()`) the first SourceSection defined within it. The source location at any particular point in the code can be extracted by calling the `getSourceSectionAtBci(int)` method with a given bytecode index (see [Bytecode index introspection](#bytecode-index-introspection) for ways to obtain the bytecode index).

### Instrumentation

Bytecode DSL also associates `Tag`s with operations to support instrumentation using the Tag operation.

TODO: fill in after instrumentation is supported

### Bytecode index introspection
TODO: fill in after bci changes

### Reparsing metadata

In order to support source position computations and instrumentation, Truffle interpreters store extra metadata (e.g., source position information) on each root node in the program.
This metadata can contribute significantly to the interpreter footprint, which is especially wasteful if the information is rarely used.
Bytecode DSL allows a language to omit all (or some) metadata from the nodes and *reparse* a node when missing metadata is required.

When a bytecode node is parsed, it takes a parsing mode that determines what metadata is retained.
The default configuration, `BytecodeConfig.DEFAULT` excludes all metadata.
In many cases, this metadata is not used on the fast path, so it is often preferable to lazily compute it instead of storing it eagerly.
When metadata is required at a later point in time – for example, a source section is requested — the generated interpreter will invoke the same parser used to `create` the nodes.
When it is reparsed, the extra metadata will be computed and stored on the node for subsequent access.

In order to support reparsing, `BytecodeParser`s **must** be deterministic and idempotent.
Furthermore, the parser will be kept alive in the heap, so languages should be mindful of the memory retained by the parser (e.g., source file contents or ASTs).

A language implementation may choose to eagerly compute metadata that will likely be needed instead of reparsing it.
For example, if a language makes frequent use of source information, it may make sense to create nodes with `BytecodeConfig.WITH_SOURCE` to eagerly parse source information.

### Serialization
Bytecode DSL interpreters can support serialization, which allows a language to implement bytecode caching (à la Python's `.pyc` files). See the [Serialization tutorial](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode.test/src/com/oracle/truffle/api/bytecode/test/examples/SerializationTutorial.java) for more details.

### Continuations
Bytecode DSL supports single-method continuations, whereby a root node is suspended and can be resumed at a later point in time.
Continuations allow languages to implement features like coroutines and generators that require the interpreter to suspend the state of the current method. See the [Continuations guide](Continuations.md) for more details.
