# Bytecode DSL user guide <!-- omit in toc -->

This document explains what you can do in a Bytecode DSL interpreter and how to do it.
It should be treated as a reference.
If you haven't already, we recommend reading the [Introduction](BytecodeDSL.md) and [Getting Started guide](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode.test/src/com/oracle/truffle/api/bytecode/test/examples/GettingStarted.java) before this one.

This guide presents the conceptual details of the Bytecode DSL; for more concrete technical information, consult the DSL's [Javadoc](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/bytecode/package-summary.html), the generated Javadoc for your interpreter, and the provided [code tutorials](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode.test/src/com/oracle/truffle/api/bytecode/test/examples).


- [The Bytecode DSL from 10,000 feet](#the-bytecode-dsl-from-10000-feet)
  - [Phase 1: Generating the interpreter](#phase-1-generating-the-interpreter)
  - [Phase 2: Generating bytecode (parsing)](#phase-2-generating-bytecode-parsing)
  - [Phase 3: Executing the bytecode](#phase-3-executing-the-bytecode)
- [Operations](#operations)
  - [Built-in operations](#built-in-operations)
  - [Custom operations](#custom-operations)
    - [Specializations](#specializations)
    - [Expressions in operations](#expressions-in-operations)
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
  - [Reparsing](#reparsing)
  - [Bytecode introspection](#bytecode-introspection)
  - [Reachability analysis](#reachability-analysis)
  - [Interpreter optimizations](#interpreter-optimizations)
  - [Runtime compilation](#runtime-compilation)
  - [Serialization](#serialization)
  - [Continuations](#continuations)
  - [Builtins](#builtins)

## The Bytecode DSL from 10,000 feet
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
This class defines an instruction set (including one or more instructions for each operation), a `Builder` to generate bytecode, an interpreter to execute bytecode, and various supporting code.
You may find it useful to read through the generated code (it is intended to be human-readable).

### Phase 2: Generating bytecode (parsing)
Now that we have an interpreter, we need to generate some concrete bytecode for it to execute.
We refer to the the process of converting a source program to bytecode as _parsing_.
Each method/function in the guest language is parsed to its own bytecode using a `BytecodeParser`.

A `BytecodeParser` specifies a tree of operations by calling a sequence of methods defined on the generated `Builder` class.
The `Builder` class is responsible for validating the well-formedness of these operations and converting them to low-level bytecodes that implement their behaviour.

Below is a simple `BytecodeParser` that generates bytecode to add two integer arguments together and return the result:
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

This `BytecodeParser` hard-codes a sequence of builder calls.
A real parser will typically be implemented using an AST visitor; see the [Parsing tutorial](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode.test/src/com/oracle/truffle/api/bytecode/test/examples/ParsingTutorial.java) for an example.

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

The `BytecodeNode` (and the bytecode itself) can change over the execution of a program for various reasons (e.g., [transitioning from uncached to cached](#cached-and-uncached-execution), [reparsing metadata](#reparsing), [quickening](Optimization.md#quickening)), so you should use `BytecodeRootNode#getBytecodeNode()` to obtain the up-to-date bytecode node each time you need it.
Custom operations can also `@Bind BytecodeNode` in their specializations (more about special Bind parameters [here](#expressions-in-operations)).

Because the bytecode may change, a bytecode index (obtained using `@Bind("$bytecodeIndex")`) must be paired with a `BytecodeNode` to meaningfully identify a program location.
You can also instantiate a [`BytecodeLocation`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/BytecodeLocation.java), which logically represents the bytecode node and index, using `BytecodeNode#getBytecodeLocation(int)` or `@Bind BytecodeLocation`.

## Operations
Operations are the basic unit of language semantics in the Bytecode DSL.
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


- `Root`: Defines a root node.
- `Return`: Returns a value from the root node.
- `Block`: Sequences multiple operations, producing the value of the last operation. `Block` operations are a build-time construct (unlike Truffle `BlockNode`s, they do not affect run time behaviour).
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
  - `TryFinally`
  - `TryCatchOtherwise`
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
children; the Bytecode DSL also supports [short circuit operations](ShortCircuitOperations.md).

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

#### Expressions in operations

Like Truffle nodes, Bytecode DSL operations encode the behaviour of specialization guards, [`@Cached`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.dsl/src/com/oracle/truffle/api/dsl/Cached.java) initializers, [`@Bind`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.dsl/src/com/oracle/truffle/api/dsl/Bind.java) expressions, and more using Truffle DSL _expressions_.
Expressions used in operations can access special variables that capture the current interpreter state:

- `$rootNode` evaluates to the bytecode root node
- `$bytecode` evaluates to the current `BytecodeNode`
- `$bytecodeIndex` evaluates to the current bytecode index (as an `int`)

When using [`@Bind`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.dsl/src/com/oracle/truffle/api/dsl/Bind.java) to bind interpreter state, you can often omit the expressions and rely on default bind expressions:

- `@Bind MyBytecodeRootNode` binds the bytecode root node
- `@Bind BytecodeNode` binds the current `BytecodeNode`
- `@Bind BytecodeLocation` binds the current `BytecodeLocation` (constructing it from the current bytecode index and `BytecodeNode`).
- `@Bind Instruction` binds the `Instruction` introspection object for the current instruction.
- `@Bind BytecodeTier` binds the current `BytecodeTier`.

These values are partial evaluation constants.

It is also possible to access additional helper methods/fields from expressions using [`@ImportStatic`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.dsl/src/com/oracle/truffle/api/dsl/ImportStatic.java).
These static imports can be declared on the root node and on individual operations (operation imports take precedence over root node imports).

#### Advanced use cases

This section discussed regular operations. There are also [short circuit operations](ShortCircuitOperations.md) to implement short-circuit behaviour, and special [`@Prolog`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/Prolog.java), [`@EpilogReturn`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/EpilogReturn.java), and [`@EpilogExceptional`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/EpilogExceptional.java) operations to guarantee certain behaviour happens on entry/exit.


An operation can take zero or more values for its last dynamic operand by declaring the last dynamic operand [`@Variadic`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/Variadic.java).
The builder will emit code to collect these values into an `Object[]`.

An operation can also define _constant operands_, which are embedded in the bytecode and produce partial evaluation constant values, by declaring [`@ConstantOperand`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/ConstantOperand.java)s.

An operation may need to produce more than one result, or to modify local variables. For either case, the operation can use [`LocalAccessor`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/LocalAccessor.java) or [`LocalRangeAccessor`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/LocalRangeAccessor.java).


## Locals

The Bytecode DSL supports local variables using its [`BytecodeLocal`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/BytecodeLocal.java) abstraction.
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

The `LoadLocal` and `StoreLocal` operations are the preferred way to access locals because they are efficient and can be quickened to [avoid boxing](Optimization.md#boxing-elimination).
Some behaviour cannot be easily implemented using only these operations, in which case an operation can declare a [`LocalAccessor`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/LocalAccessor.java) or [`LocalRangeAccessor`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/LocalRangeAccessor.java) operand to perform local accesses.
For example, an operation producing multiple values cannot "return" both values, and may instead use a local accessor to write one of the values back to a local.
The [`BytecodeNode`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/BytecodeNode.java) class also declares a variety of helper methods for accessing locals.
These helpers often have extra indirection, so the built-in operations and accessors are preferred.

Local reads/writes should always use these abstractions; **you should not directly read from or write to the frame**.

Loading a local before a value is stored into it throws a [`FrameSlotTypeException`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api/src/com/oracle/truffle/api/frame/FrameSlotTypeException.java).
You can specify a `defaultLocalValue` in [`@GenerateBytecode`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/GenerateBytecode.java) to instead give uninitialized locals a default value.


### Scoping

By default, interpreters use _block scoping_, in which locals are scoped to the enclosing `Block`/`Root` operation.
When exiting the enclosing `Block` operation, locals are cleared and their frame slots are automatically reused (locals are not cleared when exiting the `Root`).
Since the set of live locals depends on the location in the code, most of the local accessor methods on `BytecodeNode` are parameterized by the current `bytecodeIndex`.

Interpreters can alternatively opt to use _root scoping_, in which all locals get a unique position in the frame and live for the entire extent of the root.
The setting is controlled by the `enableBlockScoping` flag in [`@GenerateBytecode`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/GenerateBytecode.java).

### Materialized local accesses

The plain `LoadLocal` and `StoreLocal` operations access locals from the current frame.
In some cases, you may need to access locals from a different frame; for example, if root nodes are nested, an inner root may need to access locals of the outer root.

Materialized local accesses are intended for such use cases (see the `enableMaterializedLocalAccesses` flag in [`@GenerateBytecode`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/GenerateBytecode.java)).
When materialized local accesses are enabled, the interpreter defines `LoadLocalMaterialized` and `StoreLocalMaterialized` operations that behave analogously to `LoadLocal` and `StoreLocal`.
They can only access locals of the current root or an enclosing root.
When materialized accesses are enabled, you can also use [`MaterializedLocalAccessor`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/MaterializedLocalAccessor.java) to access locals of a materialized frame from a custom operation.

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

When using materialized accesses with outer locals, you should be careful to only call the inner root when the outer local is live; otherwise, the access could produce unexpected values.
The bytecode builder statically checks that the local is in scope when emitting a materialized access, but the interpreter cannot easily check that the access occurs at that same point in execution.
The interpreter _will_ validate the access when it is configured to store the bytecode index in the frame (see the `storeBytecodeIndexInFrame` flag in [`@GenerateBytecode`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/GenerateBytecode.java)), but for performance reasons this flag is `false` by default.
Consider enabling the flag temporarily if you encounter unexpected behaviour with materialized local values.


## Control flow

The `IfThen`, `IfThenElse`, `Conditional`, and `While` operations can be used for structured control flow.
They take a `boolean` condition for their first child, and conditionally execute their other child operation(s) as you would expect.
`Conditional` produces a value; the rest do not.

For example, the following if-then block:
```python
if arg0:
  return 42
else:
  return 123
```
can be implemented using an `IfThenElse` operation:
```java
b.beginIfThenElse();
  b.emitLoadArgument(0); // first child: condition
  b.beginReturn(); // second child: positive branch
    b.emitLoadConstant(42);
  b.endReturn();
  b.beginReturn(); // third child: negative branch
    b.emitLoadConstant(123);
  b.endReturn();
b.endIfThenElse();
```

### Unstructured control flow

A limited form of unstructured control flow is also possible in Bytecode DSL interpreters using labels and forward branches.

Parsers can allocate a `BytecodeLabel` using the builder's `createLabel` method when inside a `Root` or `Block` operation.
The label should be emitted at some location in the same `Root` or `Block` using `emitLabel`, and can be branched to using `emitBranch`.

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

1. Any branch must be (directly or indirectly) nested in the `Root` or `Block` where `createLabel` was called. That is, you cannot branch into an operation, only across or out of it.
2. Only forward branches are supported. For backward branches, use `While` operations.

Unstructured control flow is useful for implementing loop breaks, continues, and other more advanced control flow (like `switch`).

## Exception handling

Bytecode DSL interpreters have three built-in exception handler operations.

The first handler operation, `TryCatch`, executes a `try` operation (its first child), and if a Truffle exception is thrown, executes a `catch` operation (its second child).

For example, the following try-catch block:
```python
try:
  A
catch:
  B
```
can be implemented using a `TryCatch` operation:
```java
b.beginTryCatch();
  b.emitA(); // first child (try block)
  b.emitB(); // second child (catch block)
b.endTryCatch();
```

The second handler operation, `TryFinally`, executes a `try` operation (its first child), and ensures a `finally` operation is always executed, even if a Truffle exception is thrown or the `try` returns/branches out.
If an exception was thrown, it rethrows the exception afterward.

The bytecode for `finally` is emitted multiple times (once for each exit point of `try`, including at early returns), so it is specified using a `Runnable` generator that can be repeatedly invoked.
This generator must be idempotent.

For example, the following try-finally block:
```python
try:
  A
finally:
  B
```
can be implemented using a `TryFinally` operation:
```java
b.beginTryFinally(() -> b.emitB() /* finally block */);
  b.emitA(); // first child (try block)
b.endTryCatch();
```

As another example, the following try-catch-finally block:
```python
try:
  A
catch:
  B
finally:
  C
```
can be implemented with a combination of `TryFinally` and `TryCatch` operations:
```java
b.beginTryFinally(() -> b.emitC() /* finally block */);
  b.beginTryCatch(); // first child of TryFinally (try block)
    b.emitA(); // first child of TryCatch (try block)
    b.emitB(); // second child of TryCatch (catch block)
  b.endTryCatch();
b.endTryCatch();
```

The last handler operation, `TryCatchOtherwise`, is a combination of the previous two.
It executes a `try` operation (its first child); if an exception is thrown, it then executes its `catch` operation (its second child), otherwise it executes its `otherwise` operation (even if `try` returns/branches out).
Effectively, it implements `TryFinally` with a specialized handler for when an exception is thrown.

The bytecode for `otherwise` is emitted multiple times (once for each non-exceptional exit point of `try`), so it is specified using a `Runnable` generator that can be repeatedly invoked.
This generator must be idempotent.

Note that `TryCatchOtherwise` has different semantics from a Java try-catch-finally block.
Whereas a try-catch-finally always executes the `finally` operation even if the `catch` block executes, the `TryCatchOtherwise` operation executes *either* its `catch` or `otherwise` operation (not both).
It is typically useful to implement try-finally semantics with different behaviour for exceptional exits.

For example, the following try-finally block:
```python
try:
  A
finally:
  if exception was thrown:
    B
  else:
    C
```
can be implemented with a `TryCatchOtherwise` operation:
```java
b.beginTryCatchOtherwise(() -> b.emitC() /* otherwise block */);
    b.emitA(); // first child (try block)
    b.emitB(); // second child (catch block)
b.endTryCatch();
```

The `LoadException` operation can be used within the `catch` operation of a `TryCatch` or `TryCatchOtherwise` to read the current exception.

### Intercepting exceptions

Before an exception handler executes, you may wish to intercept the exception for a variety of reasons, like handling control flow exceptions, converting internal host exceptions (e.g., stack overflows) to guest exceptions, or adding metadata to exceptions.

[`BytecodeRootNode`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/BytecodeRootNode.java) defines `interceptControlFlowException`, `interceptInternalException`, and `interceptTruffleException` hooks that can be overridden.
When an exception is thrown, the interpreter will invoke the appropriate hook(s) before dispatching to a bytecode exception handler.
The hooks are invoked at most once for each throw, and may be invoked sequentially (in the order listed above); for example a control flow exception gets intercepted by `interceptControlFlowException`, which could produce an internal exception that gets intercepted by `interceptInternalException`, which could  produce a Truffle exception that gets intercepted by `interceptTruffleException`.

## Advanced features

This section describes some of the more advanced features supported by Bytecode DSL interpreters.

### Cached and uncached execution

By default, Bytecode DSL interpreters execute _cached_, allocating memory to profile conditional branches, operation specializations, and more.
These profiles allow Truffle compilation to produce highly optimized code.
However, for cold code that will not be compiled (e.g., because it only runs once or twice), the extra memory allocated is wasteful.

Bytecode DSL interpreters support an _uncached_ execution mode that allocates no memory for profiling (see the `enableUncachedInterpreter` flag in [`@GenerateBytecode`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/GenerateBytecode.java)).
When uncached execution is enabled, an interpreter starts executing as uncached.
No profiling data is collected, and each custom operation executes uncached (i.e., no specialization data is recorded).
After a predefined number of calls or loop iterations (see the `defaultUncachedThreshold` attribute in [`@GenerateBytecode`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/GenerateBytecode.java)), an uncached interpreter will transition to cached, allocating profiling data and preparing the interpreter for compilation.

It is strongly recommended to enable uncached execution, because it can reduce the footprint of your language and improve start-up times.

To support uncached execution, all operations must support [uncached execution](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/dsl/GenerateUncached.html).
When `enableUncachedInterpreter` is set to `true`, the Bytecode DSL processor will verify that each operation supports uncached, and it will emit descriptive error messages if there are changes that need to be made.
If an operation cannot easily support uncached execution, it can instead force the interpreter to transition to cached before it executes (see the `forceCached` field of [`@Operation`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/Operation.java) and the other operation annotations).
Bear in mind that declaring an operation with `forceCached` may limit the usefulness of the uncached interpreter, depending on how common the operation is.


### Source information

The `Source` and `SourceSection` operations associate source ranges with each operation in a program.
There are several `getSourceLocation` methods defined by [`BytecodeNode`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/BytecodeNode.java) that can be used to compute source information for a particular bytecode index, frame instance, etc.

It is recommended to enclose the `Root` operation in appropriate `Source` and `SourceSection` operations in order to provide accurate source information for the root node.
The generated root node will override `Node#getSourceSection` to return this information.

Source information is designed to have no performance overhead until it is requested (see [Reparsing metadata](#reparsing)).
Take extra care if accessing source information in [compiled code](RuntimeCompilation.md#source-information).

### Instrumentation

The behaviour of a Bytecode DSL interpreter can be non-intrusively observed (and modified) using instrumentation.
For example, you can instrument your code to trace each guest language statement, or add instrumentation to log return values (see the [Instrumentation tutorial](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode.test/src/com/oracle/truffle/api/bytecode/test/examples/InstrumentationTutorial.java) for more details).

Instrumentations are specified during parsing, but disabled by default.
They incur no overhead until they are enabled at a later time (see [Reparsing metadata](#reparsing)).

The Bytecode DSL supports two forms of instrumentation:

1. [`@Instrumentation`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/Instrumentation.java) operations, which are emitted and behave just like custom [`@Operation`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/Operation.java)s. These operations can perform special actions like logging or modifying the value produced by another operation. `@Instrumentation` operations must have no stack effects, so they can either have no children and produce no value, or have one child and produce a value (which allows you to modify the result of an instrumented operation).
2. Tag-based instrumentation associates operations with particular instrumentation [`Tag`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.instrumentation/src/com/oracle/truffle/api/instrumentation/Tag.java)s using `Tag` operations. If these instrumentations are enabled, the bytecode will include instructions that invoke the various event callbacks on any attached [`ExecutionEventNode`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.instrumentation/src/com/oracle/truffle/api/instrumentation/ExecutionEventNode.java)s (e.g., `onEnter`, `onReturnValue`) when executing the enclosed operation. Tag-based instrumentation can be enabled using the `enableTagInstrumentation` flag in [`@GenerateBytecode`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/GenerateBytecode.java).

Note: once instrumentation instructions are added, they cannot be removed from the bytecode. However, in tag-based instrumentation you can still disable the instruments so that the instrumentation instructions have no effect.

### Reparsing

Bytecode parsing does not materialize metadata or instructions for `Source`, `SourceSection`, `Tag`, and `@Instrumentation` operations by default.
Instead, the Bytecode DSL will *reparse* nodes to materialize the metadata/instructions when it is requested.
Reparsing allows Bytecode DSL interpreters to reduce their footprint for metadata that is infrequently used, and also allows you to dynamically enable instrumentation.

To specify what metadata/instructions to materialize, parse and reparse requests take a [`BytecodeConfig`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/BytecodeConfig.java) parameter.
There are some pre-defined configurations for convenience (`BytecodeConfig.DEFAULT`, `BytecodeConfig.WITH_SOURCE`, and `BytecodeConfig.COMPLETE`), or you can use the static `newConfigBuilder` method on the generated class to build a specific configuration.
It may make sense to request some metadata (e.g., source information) on first parse if it is frequently used.
Note that metadata/instructions are only added; there is no way to "clear" them by requesting less information in a reparse.

To support reparsing, [`BytecodeParser`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/BytecodeParser.java)s **must** be deterministic and idempotent.
When a reparse is requested, the parser is invoked again and is expected to perform the same series of builder calls.

Since the parser is retained for reparsing, any data structures (e.g., parse trees) captured by the parser will be kept alive in the heap.
To reduce footprint, it is recommended for the parser to parse directly from source code instead of keeping these data structures alive (see the [SimpleLanguage parser](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.sl/src/com/oracle/truffle/sl/parser/SLBytecodeParser.java) for an example).

Reparsing updates the [`BytecodeNode`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/BytecodeNode.java) for a given root node.
When the bytecode instructions change, any compiled code for the root node is invalidated, and the old bytecode is invalidated in order to transition active (on-stack) invocations to the new bytecode.
Note that source information updates [do _not_ invalidate compiled code](RuntimeCompilation.md#source-information).


### Bytecode introspection
Bytecode DSL interpreters have various APIs that allow you to introspect the bytecode.
These methods, defined on the [`BytecodeNode`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/BytecodeNode.java), include:

- `getInstructions`, which returns the bytecode [`Instruction`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/Instruction.java)s for the node.
- `getLocals`, which returns a list of [`LocalVariable`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/LocalVariable.java) table entries.
- `getExceptionHandlers`, which returns a list of [`ExceptionHandler`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/ExceptionHandler.java) table entries.
- `getSourceInformation`, which returns a list of [`SourceInformation`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/SourceInformation.java) table entries. There is also `getSourceInformationTree`, which encodes the entries as a [`SourceInformationTree`](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode/src/com/oracle/truffle/api/bytecode/SourceInformationTree.java).

Note that the bytecode encoding is an implementation detail, so the APIs and their outputs are subject to change, and introspection should only be used for debugging purposes.


### Reachability analysis
The Bytecode DSL performs some basic reachability analysis to avoid emitting bytecode when it can guarantee a location is not reachable, for example, after an explicit `Return` operation. The reachability analysis is confounded by features like branching, exception handling, and instrumentation, so reachability cannot always be precisely determined; in such cases, the builder conservatively assumes a given point in the program is reachable.

### Interpreter optimizations
The Bytecode DSL supports techniques like quickening and boxing elimination to improve interpreted (non-compiled) performance.
Refer to the [Optimization guide](Optimization.md) for more details.

### Runtime compilation

Like Truffle AST interpreters, Bytecode DSL interpreters use partial evaluation (PE) to implement runtime compilation.
Runtime compilation is automatically supported, but there are some subtle details to know when implementing your interpreter.
See the [Runtime compilation guide](RuntimeCompilation.md) for more details.

### Serialization
Bytecode DSL interpreters can support serialization, which allows a language to implement bytecode caching (like Python's `.pyc` files). See the [Serialization tutorial](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode.test/src/com/oracle/truffle/api/bytecode/test/examples/SerializationTutorial.java) for more details.

### Continuations
The Bytecode DSL supports single-method continuations, whereby a root node is suspended and can be resumed at a later point in time.
Continuations can be used to implement language features like coroutines and generators that suspend the state of the current method. See the [Continuations tutorial](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode.test/src/com/oracle/truffle/api/bytecode/test/examples/ContinuationsTutorial.java) for more details.


### Builtins
Guest language builtins integrate easily with the Bytecode DSL. The [Builtins tutorial](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.bytecode.test/src/com/oracle/truffle/api/bytecode/test/examples/BuiltinsTutorial.java) describes a few different approaches you may wish to use to define your language builtins within the Bytecode DSL.
