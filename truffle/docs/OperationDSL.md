# Operation DSL

Operation DSL is a DSL and runtime support component of Truffle that makes it easier to implement bytecode-based interpreters in Truffle. Just as Truffle DSL abstracts away the messy details of AST interpreters (e.g., specialization, caching, boxing elimination), the goal of Operation DSL is to abstract away the messy details of a bytecode interpreter --- the bytecode format, control flow, quickening, and so on --- leaving only the language-specific semantics for the language to implement.

[ bytecode vs AST pros and cons ]

## What is an Operation?

An operation in Operation DSL is an atomic unit of language semantics. Each operation can be executed, performing some operation, and optionally returning a value. The operations can then be nested together, to form the program. As an example, the following pseudocode
```python
if 1 == 2:
    print("what")
```
could be represented as an `if-then` operation, that has two nested "children": an `equals` operation, and a `call function` operation. The `equals` operation then has as children two `load constant` operations, with different constant values attributed to each. If we represent our operations as S-expressions, the program can be translated fully:
```lisp
(IfThen
    (Equals
        (LoadConstant 1)
        (LoadConstant 2))
    (CallFunction
        (LoadGlobal (LoadConstant "print"))
        (LoadConstant "what")))
```

Each of these operations can then be individually defined. For example, the `if-then` operation simply executes the first child, and if that returns true, executes the second child.

## Built-in vs custom operations

The operations in Operation DSL are divided into two groups: built-in and custom. Built-in operations come with the DSL itself, and their semantics cannot be changed. They model behaviour that is common across languages, such as control flow (`IfThen`, `While`, ...), constants (`LoadConstant`) and local variable manipulation (`LoadLocal`, `StoreLocal`). Semantics of each operation are defined later.

Custom operations are the ones that each language is responsible to implement. They are supposed to model language-specific behaviour, such as the semantics of operators, value conversions, calls, etc. In our previous example, `Equals`, `CallFunction` and `LoadGlobal` would be custom operations. Custom operations further come in two types: regular and short-circuiting.

## Operation DSL walkthrough

As an example on how to implement a Operation DSL language, we will use a simple example language that can only add integers and concatenate strings, using its singular operator `+`. Some "code" examples, and their results are given below:

```
1 + 2
=> 3

"a" + "b"
=> "ab"

1 + "a"
=> throws exception
```

### Defining the Operations class

The entry-point into Operation DSL is the `@GenerateOperations` annotation. This annotation must be attached to a subclass of `RootNode` and implementation of `OperationRootNode`, along with some other requirements:

```java
@GenerateOperations
public abstract class ExampleOperationRootNode extends RootNode implements OperationRootNode {
    // super-constructor omitted
}
```

We can define our custom operations inside this class. Each operation is structured similarly to a Truffle DSL Node, just it's not a Node subclass, and it needs to have all its specializations `static`. In our example language, our addition could be expressed as the following operation:

```java
// place inside ExampleOperationRootNode
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

Within operations, we can use most of the Truffle DSL, including `@Cached` and `@Bind` parameters, guards, specialization limits, etc. We cannot use features that require node instances, such as `@NodeChild`, `@NodeField`, nor any instance fields or methods.

One limitation for custom operations is that they are restricted in terms of flow control. They can only model eager functions (exceptions are the short-circuiting operations). They cannot perform conditional execution, loops, etc. For those, we have to use the built-in operations, or "desugaring".

From this simple description, the DSL will generate a `ExampleOperationRootNodeGen` class, that will contain the bytecode interpreter definition.

### Converting our program into operations

For this example, let's assume our program is in a parsed AST structure as follows:

```java
class Expr { }
class AddExpr extends Expr { Expr left; Expr right; }
class IntExpr extends Expr { int value; }
class StringExpr extends Expr { String value; }
```
Let's also assume there is a simple visitor pattern implemented over the AST.

The process of converting your langauge's program structure to a OperationRootNode is referred to as "parsing". This is performed by invoking the functions on the `Builder` that correspond to the structure of your program when represented in terms of operations. For example, the program `1 + 2` can be expressed as operations `(Add (LoadConstant 1) (LoadConstant 2))` and thus expressed as the following sequence of builder calls:

```java
b.beginAdd();
b.emitLoadConstant(1);
b.emitLoadConstant(2);
b.endAdd();
```

You can think of the `beginX` and `endX` as opening and closing `<X>` and `</X>` XML tags, while the `emitX` are the empty tag `<X />` used when the operation does not take children (all operations have either the begin/end calls, or the emit call).

We can then write our Visitor that will convert the AST structure into operations:

```java
class ExprOperationVisitor implements ExprVisitor {
    ExprOperationRootNodeGen.Builder b;

    public ExprOperationVisitor(ExprOperationRootNodeGen.Builder b) {
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

Now, we can invoke the `ExprOperationRootNodeGen#create` function, which is the entry-point for parsing. It takes the `OperationConfig` which just defines what we want to parse (see "Reparsing").

The second argument is the parser itself. The parser is an implementation of the `OperationParser<>` functional interface that takes the builder instance, and is expected to perform the builder calls as explained above. It is required that the parser be deterministic, and always perform the same sequence of builder calls if called multiple times. The parser can be called multiple times, even after the `create` function returns, in order to support reparsing (see "Reparsing").

The result is the `OperationNodes<>` instance which acts as a wrapper, grouping together all the individual `OperationRootNode` instances, along with other shared information. The nodes can be extracted using the `getNodes()` method.

We can pack this into the following function:

```java
public static ExprOperationRootNode parseExample(ExampleLanguage language, Expr program) {
    var nodes = ExprOperationRootNodeGen.create(
        OperationConfig.DEFAULT,
        builder -> {
            // Root operation must enclose each function. It is further explained later.
            builder.beginRoot(language);
            
            // Furthermore, our language returns the result of executing the expression,
            // so we are wrapping it in a Return operation.
            builder.beginReturn();
            
            // Invoke the visitor
            program.accept(new ExprOperationVisitor(builder));
            
            // End the Return and Root operations
            builder.endReturn();
            builder.endRoot();
        }
    );

    // Return the first and only Node. If there were multiple Root operations, each would result in one Node here.
    return nodes.getNodes().get(0);
}
```

This method can then be invoked by the language runtime, to obtain the executable RootNode after parsing.

## The Built-in operations explained

In this section, all the built-in operations are explained. Each operation has its arity (how many child operations it must have), and if it returns a value as result.

**Root**
* Arity: 0+
* Returns value: N/A
* `begin` arguments: (TruffleLanguage<?>)

Each Root operation defines one function (i.e. a RootNode). All other operations must be enclosed inside a Root operation. The control flow must never reach the end of the Root operation. The `beginRoot` function takes the language instance as a parameter, which will be used when constructing the RootNode. The `endRoot` function returns the resulting RootNode instance, for use in e.g. nested functions.

**Block**
* Arity: 0+
* Returns value: Only if the last child returns a value itself.

Block is a utility operation that executes all of its children in order, returning the result of the last child (if any). It can be used to group multiple operations together in order to count them as one child, both in value-returning, and non-value-returning contexts. It has a similar role to brace blocks `{ ... }` in Java, but with the addition that they can appear inside "expressions".

**IfThen**
* Arity: 2
* Returns value: no
* First child must return a value

IfThen implements the `if (x) { y }` Java language construct. On execution, it evaluates the first child, and if it results in a `true`, it executes the second child. It does not return a result. Note that only Java booleans are accepted as results of the first operation, and all other values have undefined results.

**IfThenElse**
* Arity: 3
* Returns value: no
* First child must return a value

IfThenElse implements the `if (x) { y } else { z }` Java language construct. On execution, it evaluates the first child, and if it results in `true` it executes the second child, otherwise it executes the third. No value is returned in either case. Note that only Java booleans are accepted as results of the first operation, and all other values have undefined results.

**Conditional**
* Arity: 3
* Returns value: yes
* All children must return a value

Conditional implements the `x ? y : z` Java language construct. On execution, it evaluates the first child, and if it results in `true` it executes the second child and returns its result, otherwise it executes the third child and returns its result. Note that only Java booleans are accepted as results of the first operation, and all other values have undefined results.

**While**
* Arity: 2
* Returns value: no
* First child must return a value

While implements the `while (x) { y }` Java language construct. On execution it evaluates the first child, and if that results in `true` it evaluates the second child and starts from the beginning. No value is returned as the result of the operation. Note that only Java booleans are accepted as results of the first operation, and all other values have undefined results.

**Label**
* Arity: 0
* Returns value: no
* `emit` arguments: (OperationLabel)

Label operation defines the location referenced to by the passed label (see "Defining locals and labels"). It serves a smilar purpose to the label statement in C and similar languages. Each OperationLabel instance must be passed to a Label operation exactly once. The Label operation must be scoped directly inside the same operation the label is created in.

**Branch**
* Arity: 0
* Returns value: no (but N/A)
* `emit` arguments: (OperationLabel)

Branch operation performs an unconditional branch to the passed label. It serves a similar purpose to a `goto` statement in C and similar languages. It is treated as not returning a value, but it does not conform to the regular control-flow rules.

**LoadConstant**
* Arity: 0
* Returns value: yes
* `emit` arguments: (Object)

LoadConstant operation returns the runtime constant value that is provided as its build-time argument. The argument must be immutable, as the value may be shared. On execution, it returns the value provided.

**LoadArgument**
* Arity: 0
* Returns value: yes
* `emit` arguments: (int)

LoadArgument returns the indexed argument from the arguments passed to current Truffle function.

**LoadLocal**
* Arity: 0
* Returns value: yes
* `emit` arguments: (OperationLocal)

LoadLocal reads from the supplied local (see "Defining locals and labels") and returns the currently stored value. Reading from a local that has not been written to yet results in the frame default value being read (which by default is `null`).

**StoreLocal**
* Arity: 1
* Returns value: no
* `begin` arguments: (OperationLocal)

StoreLocal executes its child operation, and stores the resulting value in the provided local. During the child execution, the previous value of the local is still available.

**LoadLocalMaterialized**
* Arity: 1
* Returns value: yes
* Child must return value
* `begin` arguments: (OperationLocal)

LoadLocalMaterialized executes its first child, and then performs the similar operation to LoadLocal, except it reads the value from the frame instance that is the result of executing the child, instead from the current frame. This can be used to read locals from materialized frames, including from frames of enclosing functions (e.g. in nested functions / lambdas).

**StoreLocalMaterialized**
* Arity: 2
* Returns value: no
* All children must return values
* `begin` arguments: (OperationLocal)

StoreLocalMaterialized executes its first and second child, and then performs the similar operation to StoreLocal, except it stores the result of the second child to the frame instance that is the result of the first child. This can be used to store locals to materialized frames, including to frames of enclosing functions (e.g. in nested functions / lambdas).

**Return**
* Arity: 1
* Returns value: yes (but N/A)
* Child must return value

Return operation executes its child, and then returns from the currently executing function with that value as the result. It is treated as returning a value, but it does not conform to the regular control-flow rules.

**Yield**
* Arity: 1
* Returns value: yes
* Child must return value
* Requires `enableYield` feature

Yield operation executes its child, and then returns from the currently executing function with a ContinuationResult containing that value as the result. Upon continuing the continuation, the control continues from Yield operation, with the Yield returning the value passed to the continuation (see "Yielding and coroutines")

**TryExcept**
* Arity: 2
* Returns value: no
* `begin` arguments: (OperationLocal)

TryExcept executes its first child. If any Truffle exception occurrs during that execution, the exception is stored in the local provided, and the second child is executed. This operation models the behavior of `try ... catch` construct in the Java language, but without the option to filter exceptions based on type. It does not return a value in either case.

**FinallyTry**
* Arity: 2
* Returns value: no

FinallyTry executes its second child. When that execution finished (either normally, through an explicit control flow transfer (Return, Branch, ...), or an exception), the first child is executed. This operation models the `try ... finally` construct in the Java language, but the order of children is flipped. If the first child finishes execution normally, the control flow resumes where it would after executing the second child. Otherwise, the control flow continues where it would continue after executing the first child.

**FinallyTryNoExcept**
* Arity: 2
* Returns value: no

FinallyTryNoExcept executes its second child. If that execution finished without an exception (either normally, or through an explicit control flow transfer (Return, Branch, ...)), the first child is executed. This is similar to FinallyTry, but does not handle exceptions.

**Source**
* Arity: 1
* Returns value: Only if the child returns a value
* `begin` arguments: (Source)

Source is the operation used to declare that the enclosed operation is found in the given Source (see "Source information"). Together with SourceSection, it allows for source locations to be preserved in Operation DSL. On execution it just executes its child and returns the result (if any).

**SourceSection**
* Arity: 1
* Returns value: Only if the child returns a value
* `begin` arguments: (int, int)

SourceSection is the operation used to declare that the enclosed operation is found at given offset, and has the given length in the source code. It must be (directly or indirectly) enclosed within the Source operation. On execution it just executes its child and returns the result (if any).

**Tag**
* Arity: 1
* Returns value: Only if the child returns a value
* `begin` arguments: (Class<? extends Tag>)

Tag is the operation used to declare that the enclosed operation should be represented as having the given tag when instrumented (see "Instrumentation"). On execution it just executes its child and returns the result (if any).

## Defining locals and labels

Locals and labels are the abstractions that encapsulate the data storage and control-flow locations. Apart from operations that manipulate them, additional `createLocal` and `createLabel` operations are exposed on the builder that provide you with a unique `OperationLocal` and `OperationLabel` instance.

The location where you call the `create` functions is important, as the construct is considered to be scoped to the operation it is created in. For labels, that operation is further required to be either a Root or a Block operation.

For locals, all loads and stores must be (directly or indirectly) nested within the same operation the local is declared in. Few examples (indented for readability):

```java
// this is allowed
b.beginBlock();
  var local = b.createLocal();
  b.beginStoreLocal(local);
    /* ... */
  b.endStoreLocal();

  b.emitLoadLocal(local); 
b.endBlock();

// this is also allowed (arbitrarily nesting)
b.beginSomeOperation();
  var local = b.createLocal();
  b.beginOtherOperation();
    b.emitLoadLocal(local); // or StoreLocal
  b.endOtherOperation();
b.endSomeOperation();

// this is not allowed
b.beginSomething();
  var local = b.createLocal();
b.endSomething();
b.emitLoadLocal(local);
```

In order to use the local with Load/StoreLocalMaterialized operations, the local must be created directly scoped to the Root operation of the function.

For labels, similar rules apply for Branch operations. The Branch must be (directly or indirectly) nested in the same Block or Root operation. However the Label operation must be directly nested. For eample:

```java
// this is allowed
b.beginBlock();
  var label = b.createLabel();
  b.emitLabel(label);
b.endBlock();

// this is not allowed (nested Label operation)
b.beginBlock();
  var label = b.createLabel();
  b.beginSomething();
    b.emitLabel(label);
  b.endSomething();
b.endBlock();

// this is not allowed (multiple Label operations for same OperationLabel)
b.beginBlock();
  var label = b.createLabel();
  b.emitLabel(label);
  // ...
  b.emitLabel(label);
b.endBlock();
```

Furthermore, reading/writing to locals, as well as branching to labels defined within other RootNodes is not allowed:

```java
b.beginRoot(/* ... */);
  var local = b.createLocal();
  // ...
  
  b.beginRoot(/* ... */);
    b.emitLoadLocal(local); // not allowed
  b.endRoot();
b.endRoot();
```

### Using materialized local reads and writes

If you need to read/write to locals of other functions, Load/StoreLocalMaterialized can be used. Still, nesting must be respected, and the local must be directly nested inside the Root operation.

```java
b.beginRoot(/* ... */);
  var topLevelLocal = b.createLocal();
  // ...
  
  b.beginBlock();
    var nonTopLevelLocal = b.createLocal();

    b.beginRoot(/* ... */);
      // allowed
      b.beginLoadLocalMaterialized(topLevelLocal);
        b.emitProvideMaterializedFrame(); // custom operation
      b.endLoadLocalMaterialized();
      
      // not allowed, the local is not top-level, even if it is in scope
      b.beginLoadLocalMaterialized(nonTopLevelLocal);
        b.emitProvideMaterializedFrame();
      b.endLoadLocalMaterialized();
    b.endRoot();
  b.endBlock();
b.endRoot();

b.beginRoot();
  // not allowed, not in scope
  b.beginLoadLocalMaterialized(topLevelLocal);
    b.emitProvideMaterializedFrame();
  b.endLoadLocalMaterialized();
b.endRoot();
```

In order to properly implement this, your language needs to implement closure calls, which materialize and pass the caller function Frame into the callee (e.g. by passing it as an additional argument), and the operation that returns that materialized frame for use with the materialized load and store operations (here named `ProvideMaterializedFrame`, e.g. by extracting it from the arguments array).

## Source information

The Operation DSL has the option of keeping track of source locations within the code. This is done using Source and SourceSection operations. Source operation defines the source in which the nested operations are found, while the SourceSection, toghether with the nearest enclosing Source operation defines the exact source location.

The source information will only be kept if the OperationConfig includes the corresponding option. This can be achieved by passing the `OperationConfig` that contains `withSource` feature to the initial `create` call, or later by calling `OperationNodes#updateConfiguration`.

The RootNode itself will report as its location the first largest enclosing SourceSection that is defined within it. The source location at any particular point in the code can be extracted by calling the `getSourceSectionAtBci(int)` function of the root node, which for a given bytecode index returns the nearest enclosing source section. The bytecode index can be obtained using the `$bci` pseudovariable in DSL expressions, e.g. by adding a `@Bind("$bci") int bci` parameter to a specialization.

## Instrumentation

The Operation DSL has the option of creating instrumentable nodes, with arbitrary instrumentation tags attached. This is achieved using the Tag operations. Each Tag operation will appear in the instrumentation framework as an instrumentable node, and will properly emit instruemntation events when executed.

Instrumentation can be enabled eagerly by passing an `OperationConfig` that contains the `withInstrumentation` feature to the initial `create` call, or later by calling `OperationNodes#updateConfiguration`. It will otherwise be automatically enabled if requested by the Truffle instrumentation framework.

## Reparsing

In order to lower the memory footprint and speed up parsing, certain features of the Operation DSL (e.g source information and instrumentation) are not eagerly enabled. Instead, they can be *reparsed* when needed. This can be done automatically (e.g. on instrumentation), or manually by calling `OperationNodes#updateConfiguration` with the new expected configuration. The method will then optionally perform the parsing process again, adding all the missing data into the node instances. In order to support this, the parser function must be deterministic, and callable multiple times.

Since parsing and reparsing is slower than just parsing once, features to be included in the initial parse can also be specified. For example, the language may eagerly choose to enable source information for its functions by passing `OperationConfig.WITH_SOURCE` to the `create` call.

## Defining custom operations

Custom operations are defined using Java classes. They can be defined in two ways: by placing them inside the operations class and annotating them with `@Operation`, or by proxying them by annotating the operations class itself with `@OperationProxy` and referencing the operation specification class.

In both cases, the operation class can be one of two things: a Truffle DSL Node that will be converted into an Operation, or an operation implemented from scratch. The first approach is useful if the language is migrating from an AST based to Operation DSL based interpreter, while the second is useful if the language is writing the Operation DSL implementation from scratch.

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

The `begin` or `emit` methods will require one `OperationLocal` argument for each `LocalSetter`, and one `OperationLocal[]` for each `LocalSetterRange` parameter defined on the operation's specializations.

### Variadic operations

Custom operations can be made variadic by adding a `@Variadic`-annotated parameter to all their specializations, after the regular value parameters.

The number of regular value parameters defines the minimum number of children for the operation, while all the remaining ones will be collected into one `Object[]` and passed to the variadic parameter. The length of that array will always be a compilation-time constant.

### Multiple results with LocalSetter

Some custom operations require returning multiple values, or just want to be able to modify local variables as part of their execution. To do this, operations can define `LocalSetter` and `LocalSetterRange` parameters. `LocalSetter` represents one local variable, that can be set from the operation. `LocalSetterRange` represents a range of variables, all of which will be settable from the operation, using an index. This is similar to by-reference parameter semantics of languages such as `C++`, however reading is now allowed (the current value can still be obtained by passing it as a regular value parameter).

## Defining short-circuiting custom operations

One common pattern of language operations is the short-circuiting operations. These include logical short-circuiting operations (e.g. `&&` and `||` in Java, but also null-coalescing operators in some languages, etc.).

Regular custom operations in Operation DSL cannot influence the execution of their children, since they are always eagerly executed. For this reason Operation DSL allows creation of short-circuiting custom operations. The short-circuiting custom operation is defined using a "boolean converter" operation and a "continue when" value.

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

## Translating your language into operations

When writing the Operation DSL parser for a language, your task is to translate the semantics of your language into individual operations. This is a process called "desugaring", as it can be thought as a similar process to removing syntax sugar from a language - translating higher level language constructs into lower, more verbose level. As an example of this process, let's take a simple iterator-style `for` loop (we use `«...»` as metaqotes):

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

Now we need to express this in terms of operations. In general, you can think of Operation DSL's operations as Java with some additional features:
* Custom operations are similar to functions, except they can also take "output" parameters (similar to by-reference C++ parameters, or `out` parameters in C#).
  * Short-circuiting operations are the exception, as different execution order rules apply to them.
* Blocks can appear anywhere in the expression, allowing you to insert statements in the middle of otherwise "expression" contexts (similar to blocks in Rust).
* Currently there are no static types - everything is an `Object`.
* TryExcept does not allow filtering exceptions based on type.

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

These operations can easily be implemented using Operation DSL. For example:


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
  OperationLocal tmpIterator = b.createLocal();
  
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

## Tracing and optimizations

A large benefit of Operation DSL is that automated optimizations, that would otherwise need manual maintenance, can be automatically generated. To help with determining which optimizations are worth implementing (since most optimizations has some drawbacks that need to be considered), an automated system of *tracing* and *decision derivation* is implemented.

### Preparing the interpreter

First step in preparing your Operation DSL interpreter for tracing is to specify a *decisions file path*, i.e. a place where to store the generated decisions. In general, this file should be committed to version control, and updated only when significant changes to the interpreter specification are made.

To specify it, you must add a `decisionsFile = "..."` attribute to the `@GenerateOperations` annotation. The value should be a path to the decisions file, relative to the source file in which it is found. It is recommended to always use the value `"decisions.json"`.

Then we must recompile the Operation DSL interpreter for *tracing*. This will create a modified version of the interpreter, which includes calls to the tracing runtime. To do this, the project must be recompiled with `truffle.dsl.OperationsEnableTracing=true` annotation processor flag. For example, this can be done using `mx` as:

```sh
mx build -f -A-Atruffle.dsl.OperationsEnableTracing=true
```

### Creating the corpus

To properly excercise the interpreter, a representative body of code, known as *corpus* must be executed. A few guidelines when compiling the corpus are:

* The corpus should be representative of the entire body of code written and ran in that language. The corpus should not be a suite of micro-benchmarks, but should instead be composed of real-world applications.
* The Operation DSL will try to optimize for speciffic patterns of use found in the corpus. For this reson, the corpus should cover as much as possible of the styles and paradigms.
* In general, Operation DSL will try to make *the corpus* run as best as it can. It is up to the language to make sure that the benefits can be transfered to all other applications.
* You should use external benchmarks to validate that the speedups are not localized to the corpus. Using a benchmark or applications that is also part of the corpus is not advised.

### Running the corpus

To run the corpus with tracing enabled, you must first create a *state file*. This file is used internally by the tracing runtime, to store data between executions. Here, we will store it in `/tmp`.

```
touch /tmp/state.json
```

Now, you must run the corpus, passing the path to the file as `engine.OperationsTracingState` Polyglot option. You can run multiple programs as a part of your corpus, but they must be run serially (locking the state file is used to prevent concurrent runs). The corpus internally may use multithreading, but any non-determinism is discouraged, as it may make the optimization decisions non-deterministic as well.

If you want to see a summary of optimization decisions, you may set the `engine.OperationsDumpDecisions` Polyglot option to `true`. This will print the resulting decisions to the Polyglot log.

After each corpus execution, the decisions file specified with `decisionsFile` is automatically updated.

### Applying the decisions

To apply the optimization decisions, simply recompile the interpreter without the tracing enabled. For example, with `mx`, just run:

```sh
mx build -f
```

This will regenerate the interpreter without the tracing calls, including taking the generated decisions into account.

### Manually overriding the decisions

If you want to manually modify the decisions generated by the Operation DSL, **do not edit the generated file**. Instead, write a second `json` file in the same format, and reference it in the `decisionOverrideFiles` annotation attribute.

### Decisions file format

TODO
