---
layout: docs
toc_group: truffle
link_title: Truffle DSL Node Object Inlining
permalink: /graalvm-as-a-platform/language-implementation-framework/DSLNodeObjectInlining/
---

# Truffle DSL Node Object Inlining

In 23.0, we have introduced a new annotation called `@GenerateInline`. This annotation instructs the Truffle DSL annotation processor to generate an inlinable version of a node. This works analogously to `@GenerateCached` and `@GenerateUncached`, which generate a cached or uncached node version.
By default, the DSL does not generate an inlined version of a node.
Node inlining provides a simple way to reduce the memory footprint of nodes but often also improves interpreter execution speed.

### Basic Usage

Let us assume we have a node with specializations that computes the sum of the absolute value of two values.
For simplicity, we will only look at the `long` typed specializations in this example.

A runnable but slightly more advanced version of this example can be found in the Truffle unit tests.
* [NodeInliningExample1_1.java](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.dsl.test/src/com/oracle/truffle/api/dsl/test/examples/NodeInliningExample1_1.java) shows an example without any inlining.
* [NodeInliningExample1_2.java](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.dsl.test/src/com/oracle/truffle/api/dsl/test/examples/NodeInliningExample1_2.java) shows an example without partial inlining.
* [NodeInliningExample1_3.java](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.dsl.test/src/com/oracle/truffle/api/dsl/test/examples/NodeInliningExample1_3.java) shows an example with full inlining.


Consider the following examples that specify two regular nodes it specializations. One node computes the sum of two values, and one computes the absolute number of a number. The `AbsNode` is then reused in the `AddAbsNode` to share the implementation.

```java
public abstract class AddAbsNode extends Node {

    abstract long execute(Object left, Object right);

    @Specialization
    long add(long left, long right,
                    @Cached AbsNode leftAbs,
                    @Cached AbsNode rightAbs) {
        return leftAbs.execute(left) + rightAbs.execute(right);
    }
    // ...
}

public abstract class AbsNode extends Node {

    abstract long execute(long value);

    @Specialization(guards = "v >= 0")
    long doInt(long v) {
        return v;
    }

    @Specialization(guards = "v < 0")
    long doLong(long v) {
        return -v;
    }
}
```

The compressed memory footprint for `AbsNode` and `AddAbsNode` after one execution are computed as follows:

```
AbsNodeGen = object header
   + Node field for Node.parent
   + int field for state

AddAbsNodeGen = object header
   + Node field for Node.parent
   + int  field for state
   + Node field for @Cached AbsNode leftAbs
   + Node field for @Cached AbsNode rightAbs

Footprint = headerCount * 12 + pointerCount * 4 + primitiveByteSize
Footprint = 3 * 12 + 5 * 4 + 12 = 68 bytes
```

Therefore, we use `68` bytes to represent a single operation with nodes.

With 23.0, the Truffle DSL annotation processor will produce the following warning for the `AbsNode` class:

```
This node is a candidate for node object inlining. The memory footprint is estimated to be reduced from 20 to 1 byte(s). Add @GenerateInline(true) to enable object inlining for this node or @GenerateInline(false) to disable this warning. Also, consider disabling cached node generation with @GenerateCached(false) if all usages will be inlined. This warning may be suppressed using @SuppressWarnings("truffle-inlining").
```

Following the recommendation of this warning, we modify our example as follows by adding the `@GenerateInline` annotation:

```java
@GenerateInline
public abstract class AbsNode extends Node {

    abstract long execute(long value);

    @Specialization(guards = "v >= 0")
    long doInt(long v) {
        return v;
    }

    @Specialization(guards = "v < 0")
    long doLong(long v) {
        return -v;
    }

}
```

Now the DSL reports a compile error for `AbsNode`:

```
Error generating code for @GenerateInline: Found non-final execute method without a node parameter execute(long). Inlinable nodes
 must use the Node type as the first parameter after the optional frame for all non-final execute methods. A valid signature for an
 inlinable node is execute([VirtualFrame frame, ] Node node, ...).
```

For inlinable nodes, we must pass a node parameter to the execute method as the first parameter.
This is necessary as inlined nodes become singletons and no longer have their own state, but instead, it is passed as a parameter to the execute method.

Again, we follow the error and modify our example as follows:

```java
@GenerateInline
public abstract class AbsNode extends Node {

    abstract long execute(Node node, long value);

    @Specialization(guards = "v >= 0")
    static long doInt(long v) {
        return v;
    }

    @Specialization(guards = "v < 0")
    static long doLong(long v) {
        return -v;
    }

}
```

Note that the node parameter is optional for specialization methods, but they are typically needed if transitively inlined nodes are used.


Next, we also need to modify `AddAbsNode` to pass `this` as a node parameter to the new execute signature:

```java
public abstract static class AddAbsNode extends Node {

    abstract long execute(long left, long right);

    @Specialization
    long add(long left, long right,
                    @Cached AbsNode leftAbs,
                    @Cached AbsNode rightAbs) {
        return leftAbs.execute(this, left) + rightAbs.execute(this, right);
    }
    // ...
}
```

The DSL now produces a warning for each of the `@Cached AbsNode` parameters:

```
The cached type 'AbsNode' supports object-inlining. The footprint is estimated to be reduced from 36 to 1 byte(s). Set @Cached(..., inline=true|false) to determine whether object-inlining should be performed. Alternatively, @GenerateCached(alwaysInlineCached=true) can be used to enable inlining for an entire class or in combination with the inherit option for a hierarchy of node classes. This warning may be suppressed using @SuppressWarnings("truffle-inlining").
```

We follow the recommendation in this message and enable object inlining:

```java
public abstract static class AddAbsNode extends Node {

    abstract long execute(long left, long right);

    @Specialization
    long add(long left, long right,
                    @Cached(inline = true) AbsNode leftAbs,
                    @Cached(inline = true) AbsNode rightAbs) {
        return leftAbs.execute(this, left) + rightAbs.execute(this, right);
    }
    // ...
}
```

Now we have achieved object-inlining of `AbsNode` into  `AddAbsNode`.
The new memory footprint computes as follows:

```
AddAbsNodeGen = object header
   + Node field for Node.parent
   + int  field for state

Footprint = headerCount * 12 + pointerCount * 4 + primitiveByteSize
Footprint = 1 * 12 + 1 * 4 + 4 = 20 bytes
```

The footprint has gone down from `68` bytes to only `20` bytes for each instance of `AddAbsNodeGen`.

But we are still going. Since all cached nodes are inlined we can also make the `AddAbsNode` inlinable for its usages.
The DSL helps us again by detecting such cases and prints a warning for `AddAbsNode` now:

```
This node is a candidate for node object inlining. The memory footprint is estimated to be reduced from 20 to 1 byte(s). Add @GenerateInline(true) to enable object inlining for this node or @GenerateInline(false) to disable this warning. Also consider disabling cached node generation with @GenerateCached(false) if all usages will be inlined. This warning may be suppressed using @SuppressWarnings("truffle-inlining").
```

Again, we follow the guide and add a `@GenerateInline` annotation to `AddAbsNode`. Just like before, we also add a `Node` parameter to the execute method:

```java
@GenerateInline
public abstract static class AddAbsNode extends Node {

    abstract long execute(Node node, long left, long right);

    @Specialization
    static long add(Node node, long left, long right,
                    @Cached AbsNode leftAbs,
                    @Cached AbsNode rightAbs) {
        return leftAbs.execute(node, left) + rightAbs.execute(node, right);
    }
    // ...
}
```

We also need to use the `Node` parameter in the specialization method and pass it on to the child nodes.
Again, we want all specializations to be `static` to avoid accidentally passing `this`.
In addition, the DSL complained about the `inline=true` attribute, which is now always implied as the parent node uses the `@GenerateInline` annotation.


To measure the overhead of our new inlinable `AddAbsNode` node, we declare a new operation called `Add4AbsNode` that adds four numbers using our `AddAbsNode` operation:


```java
@GenerateCached(alwaysInlineCached = true)
public abstract static class Add4AbsNode extends Node {

    abstract long execute(long v0, long v1, long v2, long v3);

    @Specialization
    long doInt(long v0, long v1, long v2, long v3,
                    @Cached AddAbsNode add0,
                    @Cached AddAbsNode add1,
                    @Cached AddAbsNode add2) {
        long v;
        v = add0.execute(this, v0, v1);
        v = add1.execute(this, v, v2);
        v = add2.execute(this, v, v3);
        return v;
    }

}
```

This time, instead of specifying `@Cached(inline=true)`, we auto-enable inlining wherever possible using `@GenerateCached(alwaysInlineCached = true)`.
Depending on the use case, it can hinder readability to repeat individual inlining commands for every cached node.

Computing the overhead now becomes more tricky. We need to understand how many state bits each node requires to keep track of active specializations.
That computation is generally implementation specific and subject to change. However, a good rule of thumb is that the DSL requires one bit per declared specialization.
Implicit casts, replace rules, `@Fallback` and specializations with multiple instances may further increase the number of required state bits.

For this example, each `AddAbsNode` requires 5 bits. 2 bits for each of the `AbsNode` usages and one bit for the `AddAbsNode` specializations.
The `Add4AbsNode` uses three instances of `AddAbsNode`, has one specialization, and therefore needs `3 * 5 + 1` state bits in total.
Since the number of bits is below 32, we can assume that we need a single `int` field in the generated code.
The memory footprint of an executed `Add4AbsNode` is therefore computed as follows:

```
Footprint = 1 * 12 + 1 * 4 + 4 = 20 bytes
```

As you can see, this is the same memory footprint a single `AddAbsNode` had.
If we use the same formula to compute the memory footprint of an `Add4AbsNode` without any object inlining

```
Footprint = 1 * 12 + 4 * 4 + 4 + 3 * 68 = 236 bytes
```

We have reduced the overhead from `236` bytes to `20` bytes.

In addition to the memory footprint advantages, interpreter-only execution may be faster, as we save the reads for the node fields and benefit from better CPU cache locality due to smaller memory consumption.
After compilation using partial evaluation, both cached and uncached versions are expected to perform the same.

There is a last thing we should do. Since our `AddAbsNode` and `AbsNode` are no longer used in their cached version, we can turn off cached generation using `@GenerateCached(false)` to save Java code footprint.
After doing this we can omit the `alwaysInlineCached` property in the `@GenerateCached` annotation as nodes are automatically inlined if only an inlined version is available.

This is the final example:

```java
@GenerateInline
@GenerateCached(false)
public abstract static class AbsNode extends Node {

    abstract long execute(Node node, long value);

    @Specialization(guards = "v >= 0")
    static long doInt(long v) {
        return v;
    }

    @Specialization(guards = "v < 0")
    static long doLong(long v) {
        return -v;
    }

}

@GenerateInline
@GenerateCached(false)
public abstract static class AddAbsNode extends Node {

    abstract long execute(Node node, long left, long right);

    @Specialization
    static long add(Node node, long left, long right,
                    @Cached AbsNode leftAbs,
                    @Cached AbsNode rightAbs) {
        return leftAbs.execute(node, left) + rightAbs.execute(node, right);
    }
    // ...
}

@GenerateCached(alwaysInlineCached = true)
@GenerateInline(false)
public abstract static class Add4AbsNode extends Node {

    abstract long execute(long v0, long v1, long v2, long v3);

    @Specialization
    long doInt(long v0, long v1, long v2, long v3,
                    @Cached AddAbsNode add0,
                    @Cached AddAbsNode add1,
                    @Cached AddAbsNode add2) {
        long v;
        v = add0.execute(this, v0, v1);
        v = add1.execute(this, v, v2);
        v = add2.execute(this, v, v3);
        return v;
    }
}
```

Note that the DSL again informed us that `Add4AbsNode` could use `@GenerateInline` by emitting the following warning:

```
This node is a candidate for node object inlining. The memory footprint is estimated to be reduced from 20 to 2 byte(s). Add @GenerateInline(true) to enable object inlining for this node or @GenerateInline(false) to disable this warning. Also consider disabling cached node generation with @GenerateCached(false) if all usages will be inlined. This warning may be suppressed using @SuppressWarnings("truffle-inlining").
```

This time we suppressed the warning by explicitly specifying `@GenerateInline(false)`.


### Advanced Inline Cache Usage

The following example explains how specialization unrolling and new inlinable cache classes can be helpful in reducing the memory footprint of nodes with specializations that have multiple instances.

Examples:
* [NodeInliningExample2_1.java](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.dsl.test/src/com/oracle/truffle/api/dsl/test/examples/NodeInliningExample2_1.java) shows an example without any inlining.
* [NodeInliningExample2_2.java](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.dsl.test/src/com/oracle/truffle/api/dsl/test/examples/NodeInliningExample2_2.java) shows an example without partial inlining.
* [NodeInliningExample2_3.java](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.dsl.test/src/com/oracle/truffle/api/dsl/test/examples/NodeInliningExample2_3.java) shows an example with full inlining.


### Passing along Nodes correctly

The usage of inlined nodes requires to access and pass the correct node to execute methods of the respective inlined nodes.
It is a common mistake to pass the wrong node to execute methods.
Typically such mistakes fail with an error at runtime, but the DSL also emits warnings and errors depending on the situation at compile time.


_Inlined Nodes_

For inlined nodes that use themselves inlined nodes it is sufficient to pass a long the `Node` dynamic parameter.
For example. in the previous section we used `AddAbsNode` with a similar pattern:

```java
@GenerateInline
@GenerateCached(false)
public abstract static class AddAbsNode extends Node {

    abstract long execute(Node node, long left, long right);

    @Specialization
    static long add(Node node, long left, long right,
                    @Cached AbsNode leftAbs,
                    @Cached AbsNode rightAbs) {
        return leftAbs.execute(node, left) + rightAbs.execute(node, right);
    }
    // ...
}
```

_Cached Nodes with Multiple Instances_

For nodes with specializations that may have multiple instances a `@Bind("this") Node node` parameter must be used to access the inline target node.
This is simliar to the `SumArrayNode` node in the advanced usage example.

```java
@ImportStatic(AbstractArray.class)
public abstract static class SumArrayNode extends Node {

    abstract int execute(Object v0);

    @Specialization(guards = {"kind != null", "kind.type == array.getClass()"}, limit = "2", unroll = 2)
    static int doDefault(Object array,
                    @Bind("this") Node node,
                    @Cached("resolve(array)") ArrayKind kind,
                    @Cached GetStoreNode getStore) {
        Object castStore = kind.type.cast(array);
        int[] store = getStore.execute(node, castStore);
        int sum = 0;
        for (int element : store) {
            sum += element;
            TruffleSafepoint.poll(node);
        }
        return sum;
    }

    static Class<?> getCachedClass(Object array) {
        if (array instanceof AbstractArray) {
            return array.getClass();
        }
        return null;
    }
}
```

_Exported Library Messages_

For exported library messages the `this` keyword is already reserved for the receiver value, so `$node` can be used instead.

For example:

```java
    @ExportLibrary(ExampleArithmeticLibrary.class)
    static class ExampleNumber {

        final long value;

        /* ... */

        @ExportMessage
        final long abs(@Bind("$node") Node node,
                       @Cached InlinedConditionProfile profile) {
            if (profile.profile(node, this.value >= 0)) {
                return  this.value;
            } else {
                return  -this.value;
            }
        }

    }
```



### Limitations

Node object inlining supports arbitrary deep nestings. However, there are some limitations to using `@GenerateInline`.

* There must not be any instance fields on the node class or a parent class.
* The node must not use `@NodeField` or `@NodeChild`.
* The usage of inlined nodes must not be recursive.

### Manually implementing Inlinable Nodes and Profiles

Nodes or profiles that can be inlined in the DSL can also be implemented manually.
The class must implement a static method called `inline`.
For example, most inlinable Truffle profiles use custom inlining.
Extra care must be taken when implementing such inlinable classes and if possible, a DSL generated node should be used instead.
See [InlinedBranchProfile](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.profiles/src/com/oracle/truffle/api/profiles/InlinedBranchProfile.java) or [InlinedIntValueProfile](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.profiles/src/com/oracle/truffle/api/profiles/InlinedIntValueProfile.java) class as an example on how to implement the inline method.

### API Compatibility for Inlinable Nodes

The `TruffleString` API extensively uses DSL nodes like in the above example.
However, allowing nodes to be inlined makes every change to the specializations of that node an incompatible API change.
This is because the signature of the static `inline` method changes depending on the required state bits of the specializations.

In order to support inlining across stable API boundaries, it is recommended to manually specify an inline method that forwards to the generated inline method.

As an example, consider the following node:

```
@GenerateInline
@GenerateUncached
@GeneratePackagePrivate
public abstract static class APINode extends Node {

    abstract long execute(Node node, long value);

    @Specialization(guards = "v >= 0")
    static long doInt(long v) {
        return v;
    }

    @Specialization(guards = "v < 0")
    static long doLong(long v) {
        return -v;
    }

    public static APINode inline(@RequiredField(value = StateField.class, bits = 32) InlineContext context) {
        return APINodeGen.inline(context);
    }

    public static APINode create() {
        return APINodeGen.create();
    }

    public static APINode getUncached() {
        return APINodeGen.getUncached();
    }
}
```

We use `@GeneratePackagePrivate` in order not to expose any generated code as public.
We specify a manual `inline` method that specifies the required bits for this node.
If the specializations of a node require more bits or more additional fields other than specified, then the annotation processor fails with an error.
If the node requires fewer bits, then this does not cause any compiler error.
This allows API to use node inlining across stable API boundaries as long as the reserved field capacity is not exceeded.

A change is compatible if:
* There was previously no `inline` method for this node before.
* If the required bit space is reduced and all other fields are changed.

A change is incompatible if:
* A new `@RequiredField` annotation to an existing `inline` method was added or removed.
* The required bits were increased.

The DSL validates whether the required fields are matching to the state specification of the parent node and emits a warning if it is not compatible to the node specification.

### Lazy Initialized Nodes with DSL Inlining

*Full source code of the example: [NodeInliningAndLazyInitExample.java](https://github.com/oracle/graal/blob/master/truffle/src/com.oracle.truffle.api.dsl.test/src/com/oracle/truffle/api/dsl/test/examples/NodeInliningAndLazyInitExample.java).*

DSL inlining can be used to provide lazy initialization for otherwise cached node that is only used in code blocks that
are protected by conditions that trigger rarely. Consider this example:
```java
@GenerateInline(false)
@GenerateUncached
public abstract static class RaiseErrorNode extends Node {
    abstract void execute(Object type, String message);

    // ...
}

@GenerateInline(false)
@GenerateUncached(false)
public abstract static class LazyInitExampleBefore extends Node {
    abstract void execute(Object value);

    @Specialization
    void doIt(Object value,
              @Cached RaiseErrorNode raiseError) {
        Object result = doSomeWork(value);
        if (result == null) {
            raiseError.execute(value, "Error: doSomeWork returned null");
        }
    }
}
```
`RaiseErrorNode` is always instantiated even-though we do not need it if `doSomeWork` always returns
non `null` result at runtime. Before DSL inlining, this issue was usually solved by lazy-initialized
`@Child` node:
```java
@GenerateInline(false)
@GenerateUncached(false)
public abstract static class LazyInitExampleBefore2 extends Node {
    @Child RaiseErrorNode raiseError;

    abstract void execute(Object value);

    @Specialization
    void doIt(Object value) {
        Object result = doSomeWork(value);
        if (result == null) {
            if (raiseError == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseError = insert(RaiseErrorNodeGen.create());
            }
            raiseError.execute(value, "Error: doSomeWork returned null");
        }
    }
}
```
However `@Child` nodes have some drawbacks. Most notably, the `@Specialization` cannot be `static` and we
cannot generate uncached variant of the node.

With DSL inlining, one should either make the `RaiseErrorNode` inlineable if beneficial, or if it is a node that:

* has a lot of specializations with multiple instances, or
* cannot currently be inlined, or
* has a lot of cached fields that cannot be inlined

then one can create an inlinable wrapper node that initializes the `RaiseErrorNode` on demand:
```java
@GenerateInline
@GenerateUncached
@GenerateCached(false)
public abstract static class LazyRaiseNode extends Node {
    public final RaiseErrorNode get(Node node) {
        return execute(node);
    }

    abstract RaiseErrorNode execute(Node node);

    @Specialization
    static RaiseErrorNode doIt(@Cached(inline = false) RaiseErrorNode node) {
        return node;
    }
}

@GenerateInline(false)
@GenerateUncached
public abstract static class LazyInitExample extends Node {
    abstract void execute(Object value);

    @Specialization
    void doIt(Object value,
              @Cached LazyRaiseNode raiseError) {
        Object result = doSomeWork(value);
        if (result == null) {
            raiseError.get(this).execute(value, "Error: doSomeWork returned null");
        }
    }
}
```
Unless `LazyRaiseNode.execute` gets called, the cost of the wrapper is single reference field
and one bit from the bitset of `LazyInitExample` node. Except for the extra bit, it is the same as
with the lazy initialized `@Child` node field.

Note that, at the moment, the lazy initialization pattern cannot be fully inlined by
[host inlining](https://github.com/oracle/graal/blob/master/truffle/docs/HostCompilation.md),
and it is therefore not recommended to be used on interpreter hot code-paths.