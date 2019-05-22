# Truffle Library Tutorial

*tl;dr* Truffle Libraries allow language implementations to use polymorphic dispatch for receiver types with support for implementation-specific caching/profiling and automatic support for uncached dispatch. Truffle Libraries enable modularity and encapsulation for representation types in Truffle language implementations. Read this tutorial first before using it.

## Getting Started

This tutorial provides a trace through a use-case on how to use Truffle Libraries. The full API documentation can be found in the [javadoc](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/library/package-summary.html). This document assumes prior knowledge of Truffle APIs and the use of `@Specialization` with the `@Cached` annotation.

## Motivating Example

When implementing arrays in Truffle Languages it is often necessary to use multiple representations for efficiency. For example, if the array is constructed from an arithmetic sequence of integers (e.g., `range(from: 1, step: 2, length: 3)`), then it is best represented using the `start`, `stride` and `length` instead of materializing the full array. Of course, when an array element is written then the array needs to be materialized. In this example we are going to implement an array implementation with two representations:

* Buffer: represents a materialized array representation backed by a Java array.
* Sequence: represents an arithmetic sequence of numbers represented by `start`, `stride` and `length`: `[start, start + 1 * stride, ..., start + (length - 1) * stride]`.

To keep the example simple we will only support `int` values and we will ignore index bounds error handling. We will also just implement the read operation and not the typically more complicated write operation.

To make the example more interesting, we will implement an optimization that will let the compiler allow to constant fold sequenced array accesses even if the array receiver value is not constant. Let's assume we have the following code snippet `range(start, stride, length)[2]`. In this snippet, the variables `start` and `stride` are not known to be constant values, therefore, equivalent code to `start + stride * 2` gets compiled. However, if the `start` and `stride` values are known to always be the same then the compiler could constant-fold the entire operation. This optimization requires the use of caching, we will see later how this works.

In the dynamic array implementation of Graal.js, we use 20 different representations. There are representations for constant, zero-based, contiguous, holes and sparse arrays. Some representations are further specialized for the types `byte`, `int`, `double`, `JSObject` and `Object`. The source code can be found [here](https://github.com/graalvm/graaljs/tree/master/graal-js/src/com.oracle.truffle.js/src/com/oracle/truffle/js/runtime/array/dyn). Note that currently, JS arrays don't use Truffle Libraries yet.

In the following sections, we discuss multiple implementation strategies for the array representations, ultimately describing how Truffle Libraries can be used to achieve this.

### Strategy 1: Specialization per representation

For this strategy, we will start by declaring classes for the two representations `BufferArray` and `SequenceArray`.

```java
final class BufferArray {
    int length;
    int[] buffer;
    /*...*/
}

final class SequenceArray {
    final int start;
    final int stride;
    final int length;
    /*...*/
}
```

The `BufferArray` implementation has a mutable buffer and length and is used as the materialized array representation. The sequence array is represented by the final fields `start`, `stride` and `length`.

Now, let's specify the basic read operations like this:

```java
abstract class ExpressionNode extends Node {
    abstract Object execute(VirtualFrame frame);
}

@NodeChild @NodeChild
abstract class ArrayReadNode extends ExpressionNode {

    @Specialization
    int doBuffer(BufferArray array, int index) {
        return array.buffer[index];
    }

    @Specialization
    int doSequence(SequenceArray seq, int index) {
        return seq.start + seq.stride * index;
    }
}
```
The array read node specifies two specializations for the buffer version and the sequence. As mentioned before we are going to ignore error bounds checks for simplicity.

Now let's try to make the array read specialize on the constant-ness of values of the sequence in order to allow the `range(start, stride, length)[2]` example fold if start and stride are constant. To find out whether start and stride are constants we need to profile their value. To profile these values we need to add another specialization to the array read operation like this:

```java
@NodeChild @NodeChild
class ArrayReadNode extends ExpressionNode {
    /* doBuffer() */
    @Specialization(guards = {"seq.stride == cachedStride",
                              "seq.start  == cachedStart"}, limit = "1")
    int doSequenceCached(SequenceArray seq, int index,
             @Cached("seq.start")  int cachedStart,
             @Cached("seq.stride") int cachedStride) {
        return cachedStart + cachedStride * index;
    }
    /* doSequence() */
}
```

If the speculation guards of this specialization succeed then the start and stride are effectively constant, e.g. with the values `3` and `2`, the compiler would see `3 + 2 * 2` which is `7`. The limit is set to `1` to only try this speculation once. It is likely to not be efficient to increase the limit as this would introduce additional control flow to the compiled code. If the speculation does not succeed, i.e. if the operation observes multiple start and stride values we want to fall back to the normal sequence specialization. To achieve this we change the `doSequence` specialization by adding `replaces = "doSequenceCached"` like this:

```java
@NodeChild @NodeChild
class ArrayReadNode extends ExpressionNode {
    /* doSequenceCached() */
    @Specialization(replaces = "doSequenceCached")
    int doSequence(SequenceArray seq, int index) {
        return seq.start + seq.stride * index;
    }
}
```

Now we have achieved the goal of implementing our array representations including additional profiling. The runnable source code for strategy 1 can be found [here](https://github.com/oracle/graal/tree/master/truffle/src/com.oracle.truffle.api.library.test/src/com/oracle/truffle/api/library/test/examples/ArrayStrategy1.java).
This strategy has some nice properties:

* The operation is easy to read and all cases are fully enumerated.
* The generated code of the read node only requires a single bit per specialization to remember which representation type was observed at runtime.

We would already be done with this tutorial if there wouldn't be some problems with this:

* New representations cannot be loaded dynamically, they need to be statically known, making the separation of representation types from operations impossible.
* Changing or adding representation types often requires to modify many operations.
* Representation classes need to expose most implementation details to operations (no encapsulation).

These problems are the primary motivations for Truffle Libraries.


### Strategy 2: Java Interfaces

Let's try to address these problems by using Java interfaces. We start by defining an array interface:


```java
interface Array {
    int read(int index);
}
```

The implementations can now implement the `Array` interface and implement the read method in the representation class.

```java
final class BufferArray implements Array {
    private int length;
    private int[] buffer;
    /*...*/
    @Override public int read(int index) {
        return buffer[index];
    }
}

final class SequenceArray implements Array {
    private final int start;
    private final int stride;
    private final int length;
    /*...*/
    @Override public int read(int index) {
        return start + (stride * index);
    }
}
```

Finally lets specify the operation node:

```java
@NodeChild @NodeChild
abstract class ArrayReadNode extends ExpressionNode {
    @Specialization
   int doDefault(Array array, int index) {
        return array.read(index);
    }
}
```

This operation implementation has the problem that the partial evaluator does not know which concrete type the array receiver has. It, therefore, needs to stop partial evaluation and emit a slow interface call for the `read` method call. This is not what we want, but we can introduce a polymorphic type cache to resolve it like this:

```java
class ArrayReadNode extends ExpressionNode {
    @Specialization(guards = "array.getClass() == arrayClass", limit = "2")
    int doCached(Array array, int index,
           @Cached("array.getClass()") Class<? extends Array> arrayClass) {
        return arrayClass.cast(array).read(index);
    }

    @Specialization(replaces = "doCached")
    int doDefault(Array array, int index) {
        return array.read(index);
    }
}
```

Now we solved the problem of partially evaluating the implementation, but there is no way to express the extra specialization for the constant stride and start index optimization in this solution.

This is what we solved so far:

* Interfaces are existing well-known concept for polymorphism in Java.
* New interface implementations can be loaded enabling modularity.
* A convenient way to use the operations from slow-paths.
* Representation types can encapsulate implementation details.

But we have introduced new problems:

* No representation specific profiling / caching can be performed.
* Every interface call requires a polymorphic class cache on the call-site.

The runnable source code for strategy 2 can be found [here](https://github.com/oracle/graal/tree/master/truffle/src/com.oracle.truffle.api.library.test/src/com/oracle/truffle/api/library/test/examples/ArrayStrategy2.java).

### Strategy 3: Truffle Libraries

Truffle Libraries work similar to the Java interfaces. Instead of a Java interface, we create an abstract class extending the `Library` class and annotate it with `@GenerateLibrary`. We create abstract methods like with the interface but we insert a receiver argument in the beginning, in our case of type `Object`. Instead of performing interface type checks we use an explicit abstract method in the library typically named `is${Type}`.

Let's do this for our example:

```java
@GenerateLibrary
public abstract class ArrayLibrary extends Library {

    public boolean isArray(Object receiver) {
        return false;
    }

    public abstract int read(Object receiver, int index);
}
```

This `ArrayLibrary` specifies two messages: `isArray` and `read`. At compile time, the annotation processor generates a package protected class `ArrayLibraryGen`. Unlike generated nodes classes, you never need to refer to this class.

Instead of implementing a Java interface, we export the library using the `@ExportLibrary` annotation on the representation type. Message exports are specified using instance methods on the representation and can, therefore, omit the receiver argument of the library.

The first representation we implement this way is the BufferArray representation:

```java
@ExportLibrary(ArrayLibrary.class)
final class BufferArray {
    private int length;
    private int[] buffer;
    /*...*/
    @ExportMessage boolean isArray() {
      return true;
    }
    @ExportMessage int read(int index) {
      return buffer[index];
    }
}
```

This implementation is very similar to the interface version, but in addition, we specify the `isArray` message. Again, the annotation processor generates the boilerplate code that implements the library abstract class.

Next, we implement the sequence representation. We start by implementing it without the optimization for the start and stride value.

```java
@ExportLibrary(ArrayLibrary.class)
final class SequenceArray {
    private final int start;
    private final int stride;
    private final int length;
    /*...*/
    @ExportMessage int read(int index) {
        return start + stride * index;
    }
}
```

So far this was equivalent to the interface implementation, but with Truffle Libraries we can now also use specializations in our representations by exporting a message using a class instead of a method. The convention is that the class is named exactly like the exported message, but with the first letter upper-case.

Let's implement our stride and start specialization using this mechanism:

```java
@ExportLibrary(ArrayLibrary.class)
final class SequenceArray {
    final int start;
    final int stride;
    final int length;
    /*...*/

    @ExportMessage static class Read {
        @Specialization(guards = {"seq.stride == cachedStride",
                                  "seq.start  == cachedStart"}, limit = "1")
        static int doSequenceCached(SequenceArray seq, int index,
                 @Cached("seq.start")  int cachedStart,
                 @Cached("seq.stride") int cachedStride) {
            return cachedStart + cachedStride * index;
        }

        @Specialization(replaces = "doSequenceCached")
        static int doSequence(SequenceArray seq, int index) {
            return doSequenceCached(seq, index, seq.start, seq.stride);
        }
    }
}
```

Since the message is declared using an inner class we need to specify the receiver type.
Compared to normal nodes, this class must not extend `Node` and its methods must be `static` to allow the annotation processor to generate efficient code for the library subclass.

Last, we need to use the array library in our read operation. The Library API provides an annotation called `@CachedLibrary` that is responsible for dispatching to libraries. The array read operation now looks like this:

```java
@NodeChild @NodeChild
class ArrayReadNode extends ExpressionNode {
    @Specialization(guards = "arrays.isArray(array)", limit = "2")
    int doDefault(Object array, int index,
                  @CachedLibrary("array") ArrayLibrary arrays) {
        return arrays.read(array, index);
    }
}
```
Similar to the type cache we have seen in strategy 2 we specialize the library to a particular value. The first attribute of `@CachedLibrary`, `"array"` specifies the value the library is specialized for. A specialized library can only be used for values they were specialized for. If they are used with other values then the framework will fail with an assertion error.

Instead of using the `Array` type as the parameter type, we use the `isArray` message in the guard. Using a specialized library requires us to specify the limit on the specialization. The limit specifies how many specializations of a library can be instantiated until the operation should rewrite itself to use an uncached version of the library. In the array example we have only implemented two array representations, therefore it is impossible that the limit is exceeded. In real array implementations, we are likely to use much more representations. The limit should be set to a value that is unlikely to be exceeded in representative applications, but at the same time does not produce too much code.

The uncached or slow-path version of a library can be reached by exceeding the limit of the specialization, but it can also be used manually. For example, if the array operation needs to be invoked when no node is available. This is usually the case for parts of the language implementation that are invoked infrequently. With the interface strategy (Strategy 2) the array read operation could be used by just invoking the interface method.

With Truffle libraries, we need to lookup an uncached version of the library first. Every use of `@ExportLibrary` generates a cached but also an uncached / slow-path library subclass. The uncached version of the exported library use the same semantics as `@GenerateUncached`. Typically, as with our example, the uncached version can be derived automatically. The DSL shows an error if it needs further details on how to generate the uncached version. The uncached version of the library can be invoked like this:

```java
ArrayLibrary arrays = LibraryFactory.resolve(ArrayLibrary.class).getUncached();
arrays.read(array, index);
```

In order to decrease the verbosity of this example, it is recommended that the library class provides the following optional static utilities:

```java
@GenerateLibrary
public abstract class ArrayLibrary extends Library {
    /*...*/
    public static LibraryFactory<ArrayLibrary> getFactory() {
        return FACTORY;
    }

    public static ArrayLibrary getUncached() {
        return FACTORY.getUncached();
    }

    private static final LibraryFactory<ArrayLibrary> FACTORY =
               LibraryFactory.resolve(ArrayLibrary.class);
}
```

The verbose example from above can now be simplified as:

```java
ArrayLibrary.getUncached().readArray(array, index);
```

The runnable source code for strategy 3 can be found [here](https://github.com/oracle/graal/tree/master/truffle/src/com.oracle.truffle.api.library.test/src/com/oracle/truffle/api/library/test/examples/ArrayStrategy3.java).

## Conclusion

In this tutorial, we have learned that with Truffle Libraries we no longer need to compromise the modularity of representation types by creating a specialization per representation (Strategy 1) and the profiling is no longer blocked by interface calls (Strategy 2). With Truffle Libraries we now support polymorphic dispatch with type encapsulation but don't lose the capability of using profiling/caching techniques in representation types.


## What to do next?

* Run and debug all the examples [here](https://github.com/oracle/graal/tree/master/truffle/src/com.oracle.truffle.api.library.test/src/com/oracle/truffle/api/library/test/examples/).

* Read the interop migration guide, as an example of Truffle libraries usage [here](https://github.com/oracle/graal/blob/master/truffle/docs/InteropMigration.md).

* Read the Truffle Library reference documentation [here](http://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/library/package-summary.html).


## FAQ

### Are there known limitations?

* Library exports currently cannot explicitly invoke their `super` implementation. This makes reflective implementations currently infeasible. See the example [here](https://github.com/oracle/graal/tree/master/truffle/src/com.oracle.truffle.api.library.test/src/com/oracle/truffle/api/library/test/examples/ReflectiveCallExample.java).
* Boxing elimination for return values is currently not supported. A message can only have one generic return type. Support for this is planned.
* Reflection without static dependencies on the `Library` class is currently not supported. Support for full dynamic reflection is planned.

### When should I use Truffle Libraries?

_When to use?_

* If the representations are modular and cannot be enumerated for an operation. (e.g. Truffle Interoperability)
* If there are more than one representations of a type and one of the representation needs profiling/caching. (e.g. see the motivating example)
* If there is a need for a way to proxy all values of a language. (e.g. for dynamic taint tracking)

_When not to use?_

* For basic types that only have one representation.
* For primitive representations that require boxing elimination to speed up the interpreter. Boxing elimination is not supported with Truffle Libraries at the moment.

### I decided to use a Truffle Library to abstract the language specific types of my language. Should those be exposed to other languages and tools?

All libraries are accessible to other languages and tools via the `ReflectionLibrary`. It is recommended that the language implementation documentation specifies which libraries and messages are intended for external use, and which ones may be subject to breaking changes.

### What happens when a new method is added to a library but a dynamically loaded implementation hasn't been updated for it?

If the library method was specified `abstract` then an `AbstractMethodError` will be thrown. Otherwise the default implementation specified by the library method body will be called. This allows to customize the error in case an abstract method is used. For example, for Truffle interoperability we often throw an `UnsupportedMessageException` instead of an `AbstractMethodError`.
