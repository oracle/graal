- [Introduction to the Bytecode DSL](#introduction-to-the-bytecode-dsl)
  - [Why a bytecode interpreter?](#why-a-bytecode-interpreter)
  - [Operations](#operations)
    - [Built-in vs custom operations](#built-in-vs-custom-operations)
  - [Simple example](#simple-example)
    - [Defining the Bytecode class](#defining-the-bytecode-class)
    - [Converting a program to bytecode](#converting-a-program-to-bytecode)
- [Usage details](#usage-details)
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
    - [Reparsing](#reparsing)
    - [Continuations](#continuations)
  - [Tracing and optimizations](#tracing-and-optimizations)
    - [Preparing the interpreter](#preparing-the-interpreter)
    - [Creating the corpus](#creating-the-corpus)
    - [Running the corpus](#running-the-corpus)
    - [Applying the decisions](#applying-the-decisions)
    - [Manually overriding the decisions](#manually-overriding-the-decisions)
    - [Decisions file format](#decisions-file-format)
    - [Yielding and coroutines](#yielding-and-coroutines)

# Introduction to the Bytecode DSL

Bytecode DSL is a DSL and runtime support component of Truffle that makes it easier to implement bytecode-based interpreters in Truffle. Just as Truffle DSL abstracts away the tricky and tedious details of AST interpreters (e.g., specialization, caching, boxing elimination), the goal of Bytecode DSL is to abstract away the tricky and tedious details of a bytecode interpreter – the bytecode format, control flow, quickening, and so on – leaving only the language-specific semantics for the language to implement.

Note: At the moment, Bytecode DSL is an **experimental feature**. We encourage you to give it a try, but be forewarned that its APIs are susceptible to change.



## Why a bytecode interpreter?

Though Truffle AST interpreters enjoy excellent peak performance, they can struggle in terms of:

- *Memory footprint*. Trees are not compact data structures. A root node's entire AST, with all of its state (e.g., `@Cached` parameters) must be allocated before it can execute. This allocation is especially detrimental for code that is only executed a handful of times (e.g., bootstrap code).
- *Interpreted performance*. AST interpreters contain many highly polymorphic `execute` call sites that are difficult for the JVM to optimize. These sites pose no problem for runtime-compiled code (where partial evaluation can eliminate the polymorphism), but cold code that runs in the interpreter suffers from poor performance.

Bytecode interpreters enjoy the same peak performance as ASTs, but they can also be encoded with less memory and are more amenable to optimization (e.g., via [host compilation](HostCompilation.md)). Unfortunately, these benefits come at a cost: bytecode interpreters are more difficult and tedious to implement properly. The Bytecode DSL simplifies the implementation effort for bytecode interpreters by generating them automatically from AST node-like specifications called "operations".

## Operations

An operation in Bytecode DSL is an atomic unit of language semantics. Each operation can be executed, performing some computation and optionally returning a value. Operations can be nested together to form a program. As an example, the following pseudocode
```python
if 1 == 2:
    print("what")
```
could be represented as an `IfThen` operation with two nested "children": an `Equals` operation and a `CallFunction` operation. The `Equals` operation would have two `LoadConstant` child operations, with different constant values attributed to each. If we represent our operations as S-expressions, the whole program might look something like:
```lisp
(IfThen
    (Equals
        (LoadConstant 1)
        (LoadConstant 2))
    (CallFunction
        (LoadGlobal (LoadConstant "print"))
        (LoadConstant "what")))
```

Each of these operations has its own execution semantics. For example, the `IfThen` operation executes its first child, and if the result is `true`, it executes its second child.

### Built-in vs custom operations

The operations in Bytecode DSL are divided into two groups: built-in and custom.

- Built-in operations come with the DSL itself, and their semantics cannot be changed. They model behaviour that is common across languages, such as control flow (`IfThen`, `While`, etc.), constant accesses (`LoadConstant`) and local variable manipulation (`LoadLocal`, `StoreLocal`). We describe the precise semantics of the built-in operations later in [Built-in Operations](#built-in-operations).

- Custom operations are provided by the language. They model language-specific behaviour, such as the semantics of operators, value conversions, calls, etc. In our previous example, `Equals`, `CallFunction` and `LoadGlobal` are custom operations. There are two kinds of custom operations: regular (eager) operations and short-circuiting operations.

## Simple example

As an example, let us implement a Bytecode DSL interpreter for a simple language that can only add integers and concatenate strings using its singular operator `+`. Some code examples and their results are given below:

```
1 + 2
=> 3

"a" + "b"
=> "ab"

1 + "a"
=> throws exception
```

### Defining the Bytecode class

The entry-point to a Bytecode DSL interpreter is the `@GenerateBytecode` annotation. This annotation must be attached to a class that `extends RootNode` and `implements BytecodeRootNode`:

```java
@GenerateBytecode
public abstract class ExampleBytecodeRootNode extends RootNode implements BytecodeRootNode {
    public ExampleBytecodeRootNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        ...
    }
}
``````
The class must have a two-argument constructor that takes a `TruffleLanguage<?>` and a `FrameDescriptor` (or `FrameDescriptor.Builder`). This constructor is used by the generated code to instantiate root nodes, so any other instance fields must be initialized separately.

Inside the bytecode class we define custom operations. Each operation is structured similarly to a Truffle DSL node, except it does not need to be a subclass of `Node` and all of its specializations should be `static`. In our example language, the `+` operator can be expressed with its own operation:

```java
// place inside ExampleBytecodeRootNode
@Operation
public static final class Add {
    @Specialization
    public static int doInt(int lhs, int rhs) {
        return lhs + rhs;
    }

    @Specialization
    public static String doString(String lhs, String rhs) {
        return lhs.toString() + rhs.toString();
    }

    // fallback omitted
}
```

Within operations, we can use most of the Truffle DSL, including `@Cached` and `@Bind` parameters, guards, and specialization limits. We cannot use features that require node instances, such as `@NodeChild`, `@NodeField`, nor any instance fields or methods.

One limitation of custom operations is that they eagerly evaluate all of their operands. They cannot perform conditional execution, loops, etc. For those use-cases, we have to use the built-in operations or define custom short-circuiting operations.

From this simple description, the DSL will generate a `ExampleBytecodeRootNodeGen` class that contains a full bytecode interpreter definition.

### Converting a program to bytecode

In order to execute a guest program, we need to convert it to the bytecode defined by the generated interpreter.
We refer to this process as "parsing" the bytecode root node.
<!-- We refer to the process of converting a guest program to bytecode (and thereby creating a `BytecodeRootNode`) as parsing. -->

To parse a program to a bytecode root node, we encode the program in terms of operations.
We invoke methods on the generated `Builder` class to construct these operations; the builder translates these method calls to a sequence of bytecodes that can be executed by the generated interpreter.


For this example, let's assume the guest program has already been parsed to an AST as follows:

```java
class Expr { }
class AddExpr extends Expr { Expr left; Expr right; }
class IntExpr extends Expr { int value; }
class StringExpr extends Expr { String value; }
```
Let's also assume there is a simple visitor pattern implemented over the AST.

The expression `1 + 2` can be expressed as operations `(Add (LoadConstant 1) (LoadConstant 2))`. It can be parsed using the following sequence of builder calls:

```java
b.beginAdd();
b.emitLoadConstant(1);
b.emitLoadConstant(2);
b.endAdd();
```

You can think of the `beginX` and `endX` as opening and closing `<X>` and `</X>` XML tags, while `emitX` is the empty tag `<X/>` used when the operation does not take children. Each operation has either `beginX` and `endX` methods or an `emitX` method.

We can then write a visitor to construct bytecode from the AST representation:

```java
class ExampleBytecodeVisitor implements ExprVisitor {
    ExampleBytecodeRootNodeGen.Builder b;

    public ExampleBytecodeVisitor(ExampleBytecodeRootNodeGen.Builder b) {
        this.b = b;
    }

    public void visitAdd(AddExpr ex) {
        b.beginAdd();
        ex.left.accept(this); // visitor pattern `accept`
        ex.right.accept(this);
        b.endAdd();
    }

    public void visitInt(IntExpr ex) {
        b.emitLoadConstant(ex.value);
    }

    public void visitString(StringExpr ex) {
        b.emitLoadConstant(ex.value);
    }
}
```

Now that we have a visitor, we can define a `parse` method. This method converts an AST to a `ExampleBytecodeRootNode`, which can then be executed by the language runtime:

```java
public static ExampleBytecodeRootNode parseExample(ExampleLanguage language, Expr program) {
    var nodes = ExampleBytecodeRootNodeGen.create(
        BytecodeConfig.DEFAULT,
        builder -> {
            // Root operation must enclose each function. It is further explained later.
            builder.beginRoot(language);

            // This root node returns the result of executing the expression,
            // so wrap the result in a Return operation.
            builder.beginReturn();

            // Invoke the visitor
            program.accept(new ExampleBytecodeVisitor(builder));

            // End the Return and Root operations
            builder.endReturn();
            builder.endRoot();
        }
    );

    // Return the root node. If there were multiple Root operations, there would be multiple root nodes.
    return nodes.getNode(0);
}
```

We first invoke the `ExampleBytecodeRootNodeGen#create` function, which is the entry-point for parsing. Its first argument is a `BytecodeConfig`, which defines a parsing mode. `BytecodeConfig.DEFAULT` will suffice for our purposes (there are other modes that include source positions and/or instrumentation info; see [Reparsing](#reparsing)).

The second argument is the parser. The parser is an implementation of the `BytecodeParser` functional interface, which is responsible for parsing a program using a given `Builder` parameter.
In this example, the parser uses the visitor to parse `program`, wrapping the operations within `Root` and `Return` operations.
The parser must be deterministic (i.e., if invoked multiple times, it should invoke the same sequence of `Builder` methods), since it may be called more than once to implement reparsing (see [Reparsing](#reparsing)).

The result is a `BytecodeNodes` instance, which acts as a wrapper class for the `BytecodeRootNode`s produced by the parse (along with other shared information). The nodes can be extracted using the `getNode()` or `getNodes()`.

And that's it! During parsing, the builder generates a sequence of bytecode for each root node. The generated bytecode interpreter executes this bytecode sequence when a root node is executed.

# Usage details

This section is a user manual for the Bytecode DSL. It should be consulted in combination with the Javadoc when implementing a Bytecode DSL interpreter.

## Built-in operations

The DSL defines several built-in operations. Each operation takes a certain number of child operations (e.g., `Op(child1, child2)`), or takes a variadic number of arguments (e.g., `Op(children*)`). Some operations may produce a value.


### Basic operations

`Root(children*)`
* Produces value: N/A
* `beginRoot` arguments: (`TruffleLanguage<?>`)

Each Root operation defines one function (i.e. a `RootNode`). Its children define the body of the function. Root is the only permitted top-level operation; all others must be enclosed inside a Root operation. The control flow must never reach the end of the Root operation (i.e., it should return). The `beginRoot` function takes the language instance as a parameter, which is used to construct the `RootNode`. The `endRoot` function returns the resulting `RootNode`.

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

Yield executes `value`, suspends execution at the given point, and returns a `ContinuationResult` containing the result. At a later time, a caller can resume a `ContinuationResult`, continuing execution after the Yield (see [Yielding and coroutines](#yielding-and-coroutines)). When resuming, the caller passes a value that becomes the value produced by the Yield.

`TryCatch(body, handler)`
* Produces value: no
* `beginTryCatch` arguments: (`BytecodeLocal`)

TryCatch executes its `body`. If any Truffle exception occurs during the execution, the exception is stored in the given local and `handler` is executed. This operation models the behavior of the `try ... catch ...` construct in the Java language, but without filtering exceptions based on type. It does not produce a value, regardless of whether an exception is caught.

`FinallyTry(handler, body)`
* Produces value: no
* `beginFinallyTry` arguments: (`BytecodeLocal`)

FinallyTry executes its `body`. After the execution finishes (either normally, exceptionally, or via a control flow operation like Return or Branch), the `handler` is executed. If the `body` finished exceptionally, the Truffle exception is stored in the given local; otherwise, the value of the local is null when the `handler` executes. After executing the `handler`, if the `body` finished normally or exceptionally, control flow continues after the FinallyTry; otherwise, it continues where the control flow operation would have taken it (e.g., to a label that was branched to).
This operation models the `try ... finally` construct in the Java language, but the user provides the finally handler as the first operation in order to simplify and speed up bytecode generation.

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

Custom operations are defined using Java classes. They can be defined in two ways: by placing them inside the operations class and annotating them with `@Operation`, or by proxying them by annotating the operations class itself with `@OperationProxy` and referencing the operation specification class.

In both cases, the operation class can be one of two things: a Truffle DSL Node that will be converted into an Operation, or an operation implemented from scratch. The first approach is useful if the language is migrating from an AST based to Bytecode DSL based interpreter, while the second is useful if the language is writing the Bytecode DSL implementation from scratch.

In case of the Node implementation, semantics equivalent to Truffle DSL can be expected, with the restriction of having all specialization be declared static. In case of the non-Node operation definition, the class must not have instance members, must be non-nested and `final`, must only extend `Object` and must not have explicit constructors.

The semantics of the operation are then defined using the @Specialization annotated methods. Note that in the Node case, any `execute` methods are ignored, and the semantics is purely derived from the specializations. Aditionally, any existing NodeChild annotations are ignored, and instead the semantics of nesting operations explained above is used.

### Specialization parameters

Each specialization method has parameters that define the semantics of it, as well as the operation as a whole. They must be in the following order:

* An optional `Frame` or `VirtualFrame` parameter.
* The value parameters. All specializations within an operation must have the same number of value parameters, but their types can change.
* An optional `@Variadic`-annotated parameter, with the type `Object[]`. Either all or none of the specializations must have this parameter. If present, the operation is considered variadic.
* Optional `LocalSetter` parameters. All specializations within an operation must have the same number of `LocalSetter` parameters (see "Multiple results with LocalSetter")
* Optional `LocalSetterRange` parameters. Similar to `LocalSetter`.
* Any Truffle DSL parameters, annotated with `@Cached` or `@Bind`. Each specialization can have a different number of these.

Furthermore, either all or none of the specializations must be declared as returning `void`. This will define if the custom operation is considere to be returning a value or not.

If the operation is non-variadic and has no value parameters, the `emit` method will be defined for it in the Builder. Otherwise, a pair of `begin` and `end` methods will be defined.

The `begin` or `emit` methods will require one `BytecodeLocal` argument for each `LocalSetter`, and one `BytecodeLocal[]` for each `LocalSetterRange` parameter defined on the operation's specializations.

### Variadic operations

Custom operations can be made variadic by adding a `@Variadic`-annotated parameter to all their specializations, after the regular value parameters.

The number of regular value parameters defines the minimum number of children for the operation, while all the remaining ones will be collected into one `Object[]` and passed to the variadic parameter. The length of that array will always be a compilation-time constant.

### Multiple results with LocalSetter

Some custom operations require returning multiple values, or just want to be able to modify local variables as part of their execution. To do this, operations can define `LocalSetter` and `LocalSetterRange` parameters. `LocalSetter` represents one local variable, that can be set from the operation. `LocalSetterRange` represents a range of variables, all of which will be settable from the operation, using an index. This is similar to by-reference parameter semantics of languages such as `C++`, however reading is now allowed (the current value can still be obtained by passing it as a regular value parameter).

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

This section describes some of the features supported by the Bytecode DSL.

### Source information

The Bytecode DSL keeps track of source locations using Source and SourceSection operations. The Source operation defines the `Source` of its enclosed operations. The SourceSection operation (together with the nearest enclosing Source operation) defines the precise source location of its enclosed operations.

The RootNode itself will report as its location (via `getSourceSection()`) the first SourceSection defined within it. The source location at any particular point in the code can be extracted by calling the `getSourceSectionAtBci(int)` method with a given bytecode index (see [Bytecode index introspection](#bytecode-index-introspection) for ways to obtain the bytecode index).

### Instrumentation

The Bytecode DSL also associates `Tag`s with operations to support instrumentation using the Tag operation.

TODO

### Bytecode index introspection
TODO

 (One way to obtain the bytecode index is to bind the the `$bci` pseudovariable as a parameter to an operation specialization).

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


## Tracing and optimizations

A large benefit of Bytecode DSL is that automated optimizations, that would otherwise need manual maintenance, can be automatically generated. To help with determining which optimizations are worth implementing (since most optimizations has some drawbacks that need to be considered), an automated system of *tracing* and *decision derivation* is implemented.

### Preparing the interpreter

First step in preparing your Bytecode DSL interpreter for tracing is to specify a *decisions file path*, i.e. a place where to store the generated decisions. In general, this file should be committed to version control, and updated only when significant changes to the interpreter specification are made.

To specify it, you must add a `decisionsFile = "..."` attribute to the `@GenerateBytecode` annotation. The value should be a path to the decisions file, relative to the source file in which it is found. It is recommended to always use the value `"decisions.json"`.

Then we must recompile the Bytecode DSL interpreter for *tracing*. This will create a modified version of the interpreter, which includes calls to the tracing runtime. To do this, the project must be recompiled with `truffle.dsl.BytecodeEnableTracing=true` annotation processor flag. For example, this can be done using `mx` as:

```sh
mx build -f -A-Atruffle.dsl.BytecodeEnableTracing=true
```

### Creating the corpus

To properly excercise the interpreter, a representative body of code, known as *corpus* must be executed. A few guidelines when compiling the corpus are:

* The corpus should be representative of the entire body of code written and ran in that language. The corpus should not be a suite of micro-benchmarks, but should instead be composed of real-world applications.
* The Bytecode DSL will try to optimize for speciffic patterns of use found in the corpus. For this reson, the corpus should cover as much as possible of the styles and paradigms.
* In general, Bytecode DSL will try to make *the corpus* run as best as it can. It is up to the language to make sure that the benefits can be transfered to all other applications.
* You should use external benchmarks to validate that the speedups are not localized to the corpus. Using a benchmark or applications that is also part of the corpus is not advised.

### Running the corpus

To run the corpus with tracing enabled, you must first create a *state file*. This file is used internally by the tracing runtime, to store data between executions. Here, we will store it in `/tmp`.

```
touch /tmp/state.json
```

Now, you must run the corpus, passing the path to the file as `engine.BytecodeTracingState` Polyglot option. You can run multiple programs as a part of your corpus, but they must be run serially (locking the state file is used to prevent concurrent runs). The corpus internally may use multithreading, but any non-determinism is discouraged, as it may make the optimization decisions non-deterministic as well.

If you want to see a summary of optimization decisions, you may set the `engine.BytecodeDumpDecisions` Polyglot option to `true`. This will print the resulting decisions to the Polyglot log.

After each corpus execution, the decisions file specified with `decisionsFile` is automatically updated.

### Applying the decisions

To apply the optimization decisions, simply recompile the interpreter without the tracing enabled. For example, with `mx`, just run:

```sh
mx build -f
```

This will regenerate the interpreter without the tracing calls, including taking the generated decisions into account.

### Manually overriding the decisions

If you want to manually modify the decisions generated by the Bytecode DSL, **do not edit the generated file**. Instead, write a second `json` file in the same format, and reference it in the `decisionOverrideFiles` annotation attribute.

### Decisions file format

### Yielding and coroutines

TODO
