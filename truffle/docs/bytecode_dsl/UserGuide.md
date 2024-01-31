# Bytecode DSL user guide

This section is a user manual for Bytecode DSL. It should be consulted in combination with the Javadoc when implementing a Bytecode DSL interpreter.

## Built-in operations

The DSL defines several built-in operations. Each operation takes a certain number of child operations (e.g., `Op(child1, child2)`), or takes a variadic number of arguments (e.g., `Op(children*)`). Some operations may produce a value.


### Basic operations

`Root(children*)`
* Produces value: N/A
* `beginRoot` arguments: (`TruffleLanguage<?>`)

Each Root operation defines one function (i.e., a `RootNode`). Its children define the body of the function. Root is the only top-level operation; all others must be enclosed inside a Root operation. The control flow must never reach the end of the Root operation (i.e., it should return). The `beginRoot` function takes the language instance as a parameter, which is used to construct the `RootNode`. The `endRoot` function returns the resulting `RootNode`.

To simplify parsing, root operations can be nested. However, the generated nodes are independent (a nested root node does not have access to the outer node's locals).

`Block(children*)`
* Produces value: Only if the last child produces a value.

Block is a grouping operation that executes its children sequentially, producing the result of the last child (if any). It can be used to group multiple operations together in a single operation. It has a similar role to a block `{ ... }` in Java, but it can also produce a value (i.e., blocks can be expressions).

`Return(value)`
* Produces value: N/A
* `value` must produce a value

Return executes `value` and returns the result (ending execution).

`LoadConstant`
* Produces value: yes
* `emitLoadConstant` arguments: (`Object`)

LoadConstant produces the given constant value. The argument must be immutable, since it may be shared across multiple LoadConstant operations.

`LoadArgument`
* Produces value: yes
* `emitLoadArgument` arguments: (`int`)

LoadArgument reads an argument from the frame using the given index and produces its value.

`LoadLocal`
* Produces value: yes
* `emitLoadLocal` arguments: (`BytecodeLocal`)

LoadLocal reads the given local from the frame and produces the current value (see [Locals](#locals)). If a value has not been written to the local, LoadLocal produces the default value as defined by the `FrameDescriptor` (`null` by default).

`StoreLocal(value)`
* Produces value: no
* `value` must produce a value
* `beginStoreLocal` arguments: (`BytecodeLocal`)

StoreLocal executes `value` and then overwrites the given local with the result.

`LoadLocalMaterialized(frame)`
* Produces value: yes
* `frame` must produce a `Frame` value
* `beginLoadLocalMaterialized` arguments: (`BytecodeLocal`)

LoadLocalMaterialized has the same semantics as LoadLocal, except its `frame` child is executed to produce the frame to use for the load. This can be used to read locals from materialized frames, including from frames of enclosing functions (e.g., in nested functions or lambdas).

`StoreLocalMaterialized(frame, value)`
* Produces value: no
* `frame` must produce a `Frame` value
* `value` must produce a value
* `beginStoreLocalMaterialized` arguments: (`BytecodeLocal`)

StoreLocalMaterialized has the same semantics as StoreLocal, except its `frame` child is executed to produce the frame to use for the store. This can be used to store locals into materialized frames, including from frames of enclosing functions (e.g., in nested functions or lambdas).


### Control flow operations

`IfThen(cond, thens)`

* Produces value: no
* `cond` must produce a `boolean` value

IfThen implements the `if (cond) thens` Java language construct. It evaluates `cond`, and if it produces `true`, it executes `thens`. It does not produce a result. Note that only Java booleans are accepted as results of the first operation, and all other values produce undefined behaviour.

`IfThenElse(cond, thens, elses)`
* Produces value: no
* `cond` must produce a `boolean` value

IfThenElse implements the `if (cond) thens else elses` Java language construct. It evaluates `cond`, and if it produces `true`, it executes `thens`; otherwise, it executes `elses`. No value is produced in either case.

`Conditional(cond, thens, elses)`
* Produces value: yes
* `cond` must produce a `boolean` value
* `thens` and `elses` must produce a value

Conditional implements the `cond ? thens : elses` Java language construct. It has the same semantics as IfThenElse, except it produces the value produced by the child that was conditionally executed.

`While(cond, body)`
* Produces value: no
* `cond` must produce a `boolean` value

While implements the `while (cond) body` Java language construct. It evaluates `cond`, and if it produces `true`, it executes `body` and then repeats.

`Yield(value)`
* Produces value: yes
* `value` must produce a value
* Requires `enableYield` feature

Yield executes `value`, suspends execution at the given point, and returns a `ContinuationResult` containing the result (see [Continuations](#continuations)). At a later time, a caller can resume a `ContinuationResult`, continuing execution after the Yield. When resuming, the caller passes a value that becomes the value produced by the Yield.

`TryCatch(body, handler)`
* Produces value: no
* `beginTryCatch` arguments: (`BytecodeLocal`)

TryCatch executes its `body`. If any Truffle exception occurs during the execution, the exception is stored in the given local and `handler` is executed. This operation models the behavior of the `try ... catch ...` construct in the Java language, but without filtering exceptions based on type. It does not produce a value, regardless of whether an exception is caught.

`FinallyTry(handler, body)`
* Produces value: no
* `beginFinallyTry` arguments: (`BytecodeLocal`)

FinallyTry executes its `body`. After the execution finishes (either normally, exceptionally, or via a control flow operation like Return or Branch), the `handler` is executed. If the `body` finished exceptionally, the Truffle exception is stored in the given local; otherwise, the value of the local is `null` when the `handler` executes. After executing the `handler`, if the `body` finished normally or exceptionally, control flow continues after the FinallyTry; otherwise, it continues where the control flow operation would have taken it (e.g., to a label that was branched to).
This operation models the `try ... finally` construct in the Java language.

Note the ordering of the child operations.
The finally handler is the first operation in order to simplify and speed up bytecode generation.

`FinallyTryNoExcept(handler, body)`
* Produces value: no

FinallyTryNoExcept has the same semantics as FinallyTry, except the `handler` is not executed if an exception is thrown.

`Label`
* Produces value: no
* `emitLabel` arguments: (`BytecodeLabel`)

Label defines a location in the bytecode that can be used as a forward branch target. Its argument is a `BytecodeLabel` allocated by the builder (see [Labels](#labels)). Each `BytecodeLabel` must be defined exactly once, and it should be defined directly inside the same operation in which it is created.

`Branch`
* Produces value: N/A
* `emitBranch` arguments: (`BytecodeLabel`)

Branch performs an unconditional forward branch to a label (for conditional and backwards branches, use IfThen and While operations).


### Metadata operations

The following operations are "transparent". They statically encode metadata (e.g., source information) about the program, but have no run time effect; at run time, they execute their `children` operations in sequence.

`Source(children*)`
* Produces value: Only if the last child produces a value.
* `beginSource` arguments: (`Source`)

Source associates the enclosed `children` operations with the given `Source` object (see [Source information](#source-information)). Together with SourceSection, it encodes source locations for a program.

`SourceSection(children*)`
* Produces value: Only if the last child produces a value.
* `beginSourceSection` arguments: (`int, int`)

SourceSection associates the enclosed `children` operations with the given source character offset and length (see [Source information](#source-information)). It must be (directly or indirectly) enclosed within a Source operation.

`Tag(children*)`
* Produces value: Only if the last child produces a value.
* `beginTag` arguments: (`Class<? extends Tag>`)

Tag associates the enclosed `children` operations with the given tag for instrumentation (see [Instrumentation](#instrumentation)).


## Defining custom operations

Custom operations are defined using Java classes in one of two ways:

1. Typically, operations are defined as inner classes of the root class annotated with `@Operation`.
2. To support migration from an AST interpreter, custom operations can also be *proxies* of existing existing Truffle node classes. To define an operation proxy, the root class should have an `@OperationProxy` annotation referencing the node class, and the node class itself should be marked `@OperationProxy.Proxyable`. Proxied nodes have additional restrictions compared to regular Truffle AST nodes, so making a node proxyable can require some (minimal) refactoring.

Both approaches are demonstrated below:
```
@GenerateBytecode(...)
@OperationProxy(MyNode.class)
public abstract class MyBytecodeRootNode extends RootNode implements BytecodeRootNode {
  @Operation
  public static final MyOperation {
    @Specialization
    public static int doInt(int num) { ... }

    @Specialization
    public static Object doObject(Object obj) { ... }
  }
}

@OperationProxy.Proxyable
public abstract MyNode extends Node {
    ...
}

```

Operation classes define `@Specialization`s in much the same way as Truffle DSL nodes, with some additional restrictions.
All specialization methods (and any members referenced by guards/cache initializers) must be static and visible to the generated node (i.e., public or non-private).
Additionally, they cannot define instance members.
For regular (non-proxied) operations, specializations should also be marked `final`, may only extend `Object`, and cannot have explicit constructors.


The semantics of an operation is determined entirely by its `@Specialization`s.
Note that for proxied nodes, any `execute` methods that they define are ignored.
Similarly, node fields annotated with `@NodeChild` are ignored; instead, a child value is supplied by child operations.

### Specialization parameters

Each specialization of an operation can define a certain set of parameters.
The non-frame and non-DSL parameters define a "signature" for the operation; this signature must be consistent across all specialization methods.
Parameters must be declared in the following order:

* An optional `Frame` or `VirtualFrame` parameter.
* The value parameters. All specializations within an operation must have the same number of value parameters, but their types can change. For any `@Fallback` specialization, these parameters must have type `Object`.
* An optional `@Variadic`-annotated parameter with the type `Object[]` can be the last value parameter (see [Variadic operations](#variadic-operations)). Either all or none of the specializations must have this parameter.
* Zero or more `LocalSetter` parameters. All specializations within an operation must have the same number of `LocalSetter` parameters (see [Producing multiple results with LocalSetter](#producing-multiple-results-with-localsetter))
* Zero or more `LocalSetterRange` parameters. Similar to `LocalSetter`, but for a contiguous range of locals.
* Any Truffle DSL parameters, annotated with `@Cached` or `@Bind`. Each specialization can have a different number of these.

All specializations must have either a `void` or non-`void` return type, which determines whether the custom operation produces a value.

If the operation has no value parameters (i.e., no data dependencies), an  `emit` method will be defined for it in the `Builder`.
Otherwise, a pair of `begin` and `end` methods will be defined.
These methods can be used to emit the operation during parsing.

The `begin`/`emit` methods require one `BytecodeLocal` argument for each `LocalSetter`, and one `BytecodeLocal[]` for each `LocalSetterRange` parameter in the operation's signature.

### Variadic operations

Custom operations can be made variadic – that is, they can take zero or more values for their last argument.
To make an operation variadic, each specialization should declare an `Object[]` as the last value parameter and it should be annotated with `@Variadic`.

The number of regular value parameters defines the minimum number of children for the operation; all of the remaining ones will be collected into one `Object[]` and passed for the variadic parameter.
The variadic array's length is always a compilation-time constant (its length is encoded in the bytecode).

### Producing multiple results with LocalSetter

Each operation can produce a single value (by returning it), but some operations may wish to produce multiple results.
Moreover, an operation may wish to modify [local variables](#locals) during execution.
For either use case, an operation can declare `LocalSetter` parameters, which allow operations to store values into local variables using their `set` methods.

When parsing an operation that declares `LocalSetter`s, the language specifies a `BytecodeLocal` for each `LocalSetter`; then, during execution, the operation is implicitly passed `LocalSetter`s that can write values to the locals.

For operations that need to update a contiguous range of locals (i.e., locals that were allocated sequentially), there is also `LocalSetterRange`.
During parsing, the language supplies an array of `BytecodeLocal`s, and then at run time they can be updated by index.

## Defining short-circuiting custom operations

One limitation of regular operations is that they are *eager*: all of the child operations are evaluated before the operation itself evaluates.
Many languages define *short-circuiting* operators (e.g., Java's `&&`) which can evaluate a subset of their operands, terminating early when an operand meets a particular condition (e.g., when it is `true`).

Bytecode DSL allows you to define `ShortCircuitOperation`s to implement short-circuiting behaviour.
A short-circuit operation implements `AND` or `OR` semantics, executing each child operation until the first `false` or `true` value, respectively.
Since operands will not necessarily be `boolean`s (an operation may have its own notion of "truthy" and "falsy" values), each short-circuit operation defines a boolean converter operation that first coerces each operand to `boolean` before it is compared to `true`/`false`.

For example, suppose there exists a `CoerceToBoolean` operation to compute whether a value is "truthy" or "falsy" (e.g., `42` and `3.14f` are truthy, but `""` and `0` are falsy).
We can define an `AND` operation using `CoerceToBoolean` by annotating the root class with `@ShortCircuitOperation`:
```
@GenerateBytecode(...)
@ShortCircuitOperation(
    name = "BoolAnd",
    operator = ShortCircuitOperation.Operator.AND_RETURN_CONVERTED,
    booleanConverter = CoerceToBoolean.class
)
public abstract class MyBytecodeRootNode extends RootNode implements BytecodeRootNode { ... }
```
This specification declares a `BoolAnd` operation that executes its child operations in sequence until an operand coerces to `false`.
It produces the converted `boolean` value of the last operand executed.
In pseudocode:

```python
value_1 = child_1.execute()
cond_1 = CoerceToBoolean(value_1)
if !cond_1:
    return false

value_2 = child_2.execute()
cond_2 = CoerceToBoolean(value_2)
if !cond_2:
    return false

# ...

return CoerceToBoolean(child_n.execute())
```

Observe that the `operator` for `BoolAnd` is `AND_RETURN_CONVERTED`.
This indicates not only that the operation is an `AND` operation, but also that it should produce the converted `boolean` value as its result (`RETURN_CONVERTED`).
This can be used, for example, where a `boolean` value is expected, like `a && b && c` in a Java if-statement.

Short circuit operations can also produce the original operand value that caused the operation to terminate (`RETURN_VALUE`).
For example, to emulate Python's `or` operator, where `a or b or c` evaluates to the first non-falsy operand, we can define a short-circuit operation as follows:

```
@GenerateBytecode(...)
@ShortCircuitOperation(
    name = "FalsyCoalesce",
    operator = ShortCircuitOperation.Operator.OR_RETURN_VALUE,
    booleanConverter = CoerceToBoolean.class
)
public abstract class MyBytecodeRootNode extends RootNode implements BytecodeRootNode { ... }
```

This `FalsyCoalesce` operation behaves like the following pseudocode:

```python
value_1 = child_1.execute()
cond_1 = CoerceToBoolean(value_1)
if cond_1:
    return value_1

value_2 = child_2.execute()
cond_2 = CoerceToBoolean(value_2)
if cond_2:
    return value_2

# ...

return child_n.execute()
```

Observe how the original value is produced instead of the converted `boolean` value.

The `booleanConverter` field of a `@ShortCircuitOperation` is a class – typically an existing operation class.
If the class is not explicitly declared as an operation, Bytecode DSL will implicitly declare it as an operation and ensure it conforms to the requirements for [custom operations](#defining-custom-operations).
A boolean converter operation must also satisfy some additional constraints:

* It must have exactly 1 non-variadic value parameter.
* It must not have any `LocalSetter` or `LocalSetterRange` parameters.
* All of its specializations must return `boolean`.


## Locals

Bytecode DSL defines a `BytecodeLocal` abstraction to support local variables.
Unlike temporary values, local variables persist for the extent of the enclosing operation.
Parsers can allocate locals using the builder's `createLocal` method; the resulting `BytecodeLocal` can be used in `LoadLocal` and `StoreLocal` operations to access the local.

The following code allocates a local, stores a value into it, and later loads the value back:
```java
b.beginBlock();
  var local = b.createLocal();

  b.beginStoreLocal(local);
    // ...
  b.endStoreLocal();

  // ...
  b.emitLoadLocal(local);
b.endBlock();
```


All local accesses must be (directly or indirectly) nested within the operation that creates the local.
It is undefined behaviour to access a local outside of its creating operation, and the builder does not validate this. For example:

```java
b.beginSomeOperation();
  var local = b.createLocal();
  // ...
  b.beginOtherOperation();
    b.emitLoadLocal(local); // allowed (arbitrary nesting)
  b.endOtherOperation();
b.endSomeOperation();

b.beginSomeOperation();
  b.beginOtherOperation();
    var local = b.createLocal();
  b.endOtherOperation();
  // ...
  b.emitLoadLocal(local); // undefined behaviour: local not in scope
b.endSomeOperation();

```

Additionally, reading/writing to locals defined by other root nodes is undefined behaviour:

```java
b.beginRoot(/* ... */);
  var local = b.createLocal();
  // ...

  b.beginRoot(/* ... */);
    b.emitLoadLocal(local); // undefined behaviour
  b.endRoot();
b.endRoot();
```

### Introspecting locals

TODO: getLocalIndex + getLocal, getLocals(Frame)

### Using materialized local reads and writes

Load/StoreLocalMaterialized can be used to access the locals of other functions (e.g., to implement lexical scoping).
It is undefined behaviour to access a local using a materialized frame that it does not belong to; be careful to avoid this situation.

```java
b.beginRoot(/* ... */);
  var outerLocal = b.createLocal();
  // ...

  b.beginBlock();
    var innerLocal = b.createLocal();

    b.beginRoot(/* ... */);
      // correct usage
      b.beginLoadLocalMaterialized(outerLocal);
        b.emitGetOuterFrame();
      b.endLoadLocalMaterialized();

      // undefined behaviour
      b.beginLoadLocalMaterialized(innerLocal);
        b.emitGetOuterFrame();
      b.endLoadLocalMaterialized();
    b.endRoot();
  b.endBlock();
b.endRoot();
```



## Labels

Bytecode DSL defines a `BytecodeLabel` abstraction to represent locations in the bytecode.
Parsers can allocate labels using the builder's `createLabel` method.
The label should then be emitted using `emitLabel` at some location in the program, and can be branched to using `emitBranch`.

The following code allocates a label, emits a branch to it, and then emits the label at the location to branch to:
```java
// allowed
b.beginBlock();
  var label = b.createLabel();
  // ...
  b.emitBranch(label);
  // ...
  b.emitLabel(label);
b.endBlock();
```

Every label must be declared once directly in its creating operation; this operation must be a Block or Root.
Any branch to a label must be (directly or indirectly) nested in the label's creating operation.
Furthermore, only forward branches are supported (for backward branches, prefer While operations).
For example:

```java
// not allowed (emitted in child operation)
b.beginBlock();
  var label = b.createLabel();
  b.beginSomething();
    b.emitLabel(label);
  b.endSomething();
b.endBlock();

// not allowed (multiple declarations)
b.beginBlock();
  var label = b.createLabel();
  b.emitLabel(label);
  // ...
  b.emitLabel(label);
b.endBlock();

// not allowed (backward branch)
b.beginBlock();
  var label = b.createLabel();
  b.emitLabel(label);
  // ...
  b.emitBranch(label);
b.endBlock();
```

Additionally, branching to labels defined by other root nodes is not allowed:

```java
b.beginRoot(/* ... */);
  var label = b.createLabel();
  // ...
  b.beginRoot(/* ... */);
    b.emitBranch(label); // builder error
  b.endRoot();
b.endRoot();
```

## Translating high-level semantics into operations

When writing the Bytecode DSL parser for a language, your task is to translate the semantics of your language into individual operations. This is a process called "desugaring", as it can be thought as a similar process to removing syntax sugar from a language - translating higher level language constructs into lower, more verbose level. As an example of this process, let's take a simple iterator-style `for` loop (we use `«...»` as metaqotes):

```python
for x in «iterable»:
  «body»
```

The semantics of this language construct can be expressed as follows:
* Evaluate the iterable
* Get the iterator from `«iterable»`
* As long as you can get a value from the iterator:
  * Bind the value to the variable `x`
  * Evaluate the `«body»`

Now we need to express this in terms of operations. In general, you can think of Bytecode DSL's operations as Java with some additional features:
* Custom operations are similar to functions, except they can also take "output" parameters (similar to by-reference C++ parameters, or `out` parameters in C#).
  * Short-circuiting operations are the exception, as different execution order rules apply to them.
* Blocks can appear anywhere in the expression, allowing you to insert statements in the middle of otherwise "expression" contexts (similar to blocks in Rust).
* Currently there are no static types - everything is an `Object`.
* TryCatch does not allow filtering exceptions based on type.

Now we can write the previous semantics in this pseudo-Java language. To help us, we will introduce a temporary local, `tmpIterator`.

```csharp
var tmpIterator = GetIterator(«iterable»);
var x;
while (GetNextFromIterator(tmpIterator, out x)) {
  «body»
}
```

To implement this, we need 2 custom operations:

* `GetIterator(iterable)` whilch will take an iterable, and produce the iterator from it.
* `GetNextFromIterator(iterator, out value)` which will take an iterator and then either:
  * Set the `value` to the next element of the iterator, and return `true`, or
  * Return `false` once we reach the end of the iterator.

These operations can easily be implemented using Bytecode DSL. For example:


```java
@Operation
public static final class GetIterator {
  @Specialization
  public MyIterator perform(MyIterable iterable) {
    return iterable.createIterator();
  }
}

@Operation
public static final class GetNextFromIterator {
  @Specialization
  public boolean perform(MyIterator iterator, LocalSetter value) {
    if (iterator.hasNext()) {
      value.setObject(iterator.getNext());
      return true;
    } else {
      return false;
    }
  }
}
```

Then, we need to transform the previously written "desugared" form into individual builder calls in our parser. If we are using a visitor pattern parser, this would look something like this (indented for readability):

```java
// in our ast visitor
public void visit(ForNode node) {
  BytecodeLocal tmpIterator = b.createLocal();

  b.beginStoreLocal(tmpIterator);
    b.beginGetIterator();
      node.iterator.accept(this);
    b.endGetIterator();
  b.endStoreLocal();

  b.beginWhile();
    b.beginGetNextFromIterator(valueLocal);
      b.emitLoadLocal(tmpIterator);
    b.endGetNextFromIterator();

    b.beginBlock();
      // if your language supports destructuring (e.g. `for x, y in ...`)
      // you would do that here as well
      node.body.accept(this);
    b.endBlock();
  b.endWhile();
}
```



## Features

This section describes some of the features supported by Bytecode DSL interpreters.

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
Bytecode DSL supports single-method continuations, whereby a bytecode node is suspended and can be resumed at a later point in time.
Continuations allow languages to implement features like coroutines and generators that require the interpreter to suspend the state of the current method. See the [Continuations guide](Continuations.md) for more details.
