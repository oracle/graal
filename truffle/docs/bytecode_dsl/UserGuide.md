# Bytecode DSL user guide

This section is a user manual for Bytecode DSL. It should be consulted in combination with the Javadoc when implementing a Bytecode DSL interpreter.

- [Bytecode DSL user guide](#bytecode-dsl-user-guide)
  - [Built-in operations](#built-in-operations)
    - [Basic operations](#basic-operations)
    - [Control flow operations](#control-flow-operations)
    - [Metadata operations](#metadata-operations)
  - [Defining custom operations](#defining-custom-operations)
    - [Specialization parameters](#specialization-parameters)
    - [Variadic operations](#variadic-operations)
    - [Multiple results with LocalSetter](#multiple-results-with-localsetter)
  - [Defining short-circuiting custom operations](#defining-short-circuiting-custom-operations)
  - [Defining locals and labels](#defining-locals-and-labels)
    - [Using materialized local reads and writes](#using-materialized-local-reads-and-writes)
  - [Translating your language into operations](#translating-your-language-into-operations)
  - [Features](#features)
    - [Source information](#source-information)
    - [Instrumentation](#instrumentation)
    - [Bytecode index introspection](#bytecode-index-introspection)
    - [Parsing modes](#parsing-modes)
    - [Reparsing](#reparsing)
    - [Continuations](#continuations)

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

LoadLocal reads the given local from the frame and produces the current value (see [Defining locals and labels](#defining-locals-and-labels)). If a value has not been written to the local, LoadLocal produces the default value as defined by the `FrameDescriptor` (`null` by default).

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

Yield executes `value`, suspends execution at the given point, and returns a `ContinuationResult` containing the result (see [Yielding and coroutines](#yielding-and-coroutines)). At a later time, a caller can resume a `ContinuationResult`, continuing execution after the Yield. When resuming, the caller passes a value that becomes the value produced by the Yield.

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

Label defines a location in the bytecode that can be used as a forward branch target. Its argument is a `BytecodeLabel` allocated by the builder (see [Defining locals and labels](#defining-locals-and-labels)). Each `BytecodeLabel` must be defined exactly once, and it should be defined directly inside the same operation in which it is created.

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
* The value parameters. All specializations within an operation must have the same number of value parameters, but their types can change.
* An optional `@Variadic`-annotated parameter with the type `Object[]` can be the last value parameter (see [Variadic operations](#variadic-operations)). Either all or none of the specializations must have this parameter. 
* Zero or more `LocalSetter` parameters. All specializations within an operation must have the same number of `LocalSetter` parameters (see [Multiple results with LocalSetter](#multiple-results-with-localsetter))
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

### Multiple results with LocalSetter

Each operation can produce a single value (by returning it), but some operations may actually produce multiple results.
Moreover, an operation may wish to modify local variables during execution.
For either use case, an operation can declare `LocalSetter` and `LocalSetterRange` parameters, which allow it to modify the values of local variables.
`LocalSetter` represents one local variable, whereas `LocalSetterRange` represents a contiguous range of variables.


## Defining short-circuiting custom operations

One common pattern of language operations is the short-circuiting operations. These include logical short-circuiting operations (e.g. `&&` and `||` in Java, but also null-coalescing operators in some languages, etc.).

Regular custom operations in Bytecode DSL cannot influence the execution of their children, since they are always eagerly executed. For this reason Bytecode DSL allows creation of short-circuiting custom operations. The short-circuiting custom operation is defined using a "boolean converter" operation and a "continue when" value.

The boolean converter operation is another operation (which may or may not be its own operation as well) that converts a language value into a `boolean` result. In addition to all the requirements outlined above for operations, it must also satisfy the following constraints:

* It must have exactly 1 value parameter, and not be variadic
* It must not have any LocalSetter or LocalSetterRange parameters
* All its specializations must return `boolean`.

Then the short-circuiting operation can be derived: the new operation will be variadic, with minimum of 1 parameter, and return the first value that does **not** satisfy the `continueWhen` condition when converted to boolean using the converter operation. If the execution reaches the last child, it is executed and returned without checking. In pseudocode:

```python
value_1 = child_1.execute()
if BooleanConverter(value_1) != continueWhen:
    return value_1

value_2 = child_2.execute()
if BooleanConverter(value_2) != continueWhen:
    return value_2

# ...

return child_n.execute()
```

The short-circuiting operation is defined by annotating the operations class with `@ShortCircuitOperation` and specifying the name of the operation, the boolean converter definition, and the continueWhen argument.

With this, we can define some common short-circuiting operations:

```java
@ShortCircuitOperation(
    name = "BoolAnd",
    booleanConverter = ToBoolean.class,
    continueWhen = true)
@ShortCircuitOperation(
    name = "BoolOr",
    booleanConverter = ToBoolean.class,
    continueWhen = false)
@ShortCircuitOperation(
    name = "NullCoalesce",
    booleanConverter = IsNull.class,
    continueWhen = true)
```

## Defining locals and labels

Locals and labels are important abstractions that encapsulate local variables and branch targets. The builder defines `createLocal` and `createLabel` methods that can be used to obtain unique `BytecodeLocal` and `BytecodeLabel` instances.

The location where you call the `create` functions is important, as the abstractions are scoped to the operation they are created in. For labels, that operation is further required to be either a Root or a Block operation.

All local accesses must be (directly or indirectly) nested within the local's creating operation. It is undefined behaviour to access a local outside of its creating operation (**the builder does not validate this**). For example:

```java
// allowed
b.beginBlock();
  var local = b.createLocal();
  b.beginStoreLocal(local);
    /* ... */
  b.endStoreLocal();

  b.emitLoadLocal(local);
b.endBlock();

// allowed (arbitrary nesting)
b.beginSomeOperation();
  var local = b.createLocal();
  b.beginOtherOperation();
    b.emitLoadLocal(local); // or StoreLocal
  b.endOtherOperation();
b.endSomeOperation();

// undefined behaviour
b.beginSomething();
  var local = b.createLocal();
b.endSomething();
b.emitLoadLocal(local);
```

Every label must be declared directly in its creating operation; this operation must be a Block or Root. Any branch to a label must be (directly or indirectly) nested in the label's creating operation. For example:

```java
// allowed
b.beginBlock();
  var label = b.createLabel();
  b.emitLabel(label);
b.endBlock();

// not allowed (nested Label operation)
b.beginBlock();
  var label = b.createLabel();
  b.beginSomething();
    b.emitLabel(label);
  b.endSomething();
b.endBlock();

// not allowed (multiple Label declarations)
b.beginBlock();
  var label = b.createLabel();
  b.emitLabel(label);
  // ...
  b.emitLabel(label);
b.endBlock();
```

Furthermore, reading/writing to locals defined by other RootNodes is undefined behaviour; branching to labels within other RootNodes is not allowed.

```java
b.beginRoot(/* ... */);
  var local = b.createLocal();
  var label = b.createLabel();
  // ...

  b.emitLabel(label);

  b.beginRoot(/* ... */);
    b.emitLoadLocal(local); // undefined behaviour
    b.emitBranch(label); // not allowed
  b.endRoot();
b.endRoot();
```

### Using materialized local reads and writes

Load/StoreLocalMaterialized can be used to access the locals of other functions (e.g., to implement lexical scoping).
It is undefined behaviour to access a local using a materialized frame that it does not belong to; be careful to avoid this situation.

```java
b.beginRoot(/* ... */);
  var topLevelLocal = b.createLocal();
  // ...

  b.beginBlock();
    var nonTopLevelLocal = b.createLocal();

    b.beginRoot(/* ... */);
      // correct usage
      b.beginLoadLocalMaterialized(topLevelLocal);
        b.emitProvideTopLevelFrame(); // custom operation
      b.endLoadLocalMaterialized();

      // undefined behaviour
      b.beginLoadLocalMaterialized(nonTopLevelLocal);
        b.emitProvideTopLevelFrame();
      b.endLoadLocalMaterialized();
    b.endRoot();
  b.endBlock();
b.endRoot();
```

## Translating your language into operations

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

This section describes some of the features supported by Bytecode DSL.

### Source information

Bytecode DSL keeps track of source locations using Source and SourceSection operations. The Source operation defines the `Source` of its enclosed operations. The SourceSection operation (together with the nearest enclosing Source operation) defines the precise source location of its enclosed operations.

The RootNode itself will report as its location (via `getSourceSection()`) the first SourceSection defined within it. The source location at any particular point in the code can be extracted by calling the `getSourceSectionAtBci(int)` method with a given bytecode index (see [Bytecode index introspection](#bytecode-index-introspection) for ways to obtain the bytecode index).

### Instrumentation

Bytecode DSL also associates `Tag`s with operations to support instrumentation using the Tag operation.

TODO

### Bytecode index introspection
TODO

 (One way to obtain the bytecode index is to bind the the `$bci` pseudovariable as a parameter to an operation specialization).

### Parsing modes

### Reparsing

When a bytecode node is parsed, the default `BytecodeConfig` excludes source information and instrumentation tags from the parsed result.
This metadata can contribute significantly to the interpreter footprint, so it is often preferable to lazily compute this information instead of storing it eagerly.

Bytecode DSL intepreters achieve this lazy computation using *reparsing*.
When metadata is required (e.g., the source section is requested) or the `BytecodeConfig` changes (via `BytecodeNodes#updateConfiguration`), the generated interpreter will invoke the same parser used to `create` the nodes, except it will retain the additional metadata (i.e., source or instrumentation information) that was requested.

In order to support reparsing, the `BytecodeParser` used to `create` nodes must be deterministic and idempotent.
The parser will be retained in a field, so languages should be considerate of the memory occupied by objects reachable from their parsers (e.g., source file contents or ASTs).

A language may choose to eagerly include some metadata it knows it will always need instead of reparsing; for example, it can use `BytecodeConfig.WITH_SOURCE` to eagerly compute source information.

### Continuations
TODO
