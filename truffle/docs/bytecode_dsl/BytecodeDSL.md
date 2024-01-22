# Introduction to Bytecode DSL

Bytecode DSL is a DSL and runtime support component of Truffle that makes it easier to implement bytecode interpreters in Truffle. Just as Truffle DSL abstracts away the tricky and tedious details of AST interpreters, the goal of Bytecode DSL is to abstract away the tricky and tedious details of a bytecode interpreter – the bytecode format, control flow, quickening, and so on – leaving only the language-specific semantics for the language to implement.

Note: At the moment, Bytecode DSL is an **experimental feature**. We encourage you to give it a try, but be forewarned that its APIs are susceptible to change.



## Why a bytecode interpreter?

Though Truffle AST interpreters enjoy excellent peak performance, they can struggle in terms of:

- *Memory footprint*. Trees are not compact data structures. A root node's entire AST, with all of its state (e.g., `@Cached` parameters) must be allocated before it can execute. This allocation is especially detrimental for code that is only executed a handful of times (e.g., bootstrap code).
- *Interpreted performance*. AST interpreters contain many highly polymorphic `execute` call sites that are difficult for the JVM to optimize. These sites pose no problem for runtime-compiled code (where partial evaluation can eliminate the polymorphism), but cold code that runs in the interpreter suffers from poor performance.

Bytecode interpreters enjoy the same peak performance as ASTs, but they can also be encoded with less memory and are more amenable to optimization (e.g., via [host compilation](HostCompilation.md)). Unfortunately, these benefits come at a cost: bytecode interpreters are more difficult and tedious to implement properly. Bytecode DSL simplifies the implementation effort for bytecode interpreters by generating them automatically from AST node-like specifications called "operations".

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

