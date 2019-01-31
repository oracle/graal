# Truffle Library Tutorial

*tl;dr* Truffle Libraries allow language implementations to use polymorphic dispatch for receiver types with support for implementation specific caching/profiling and also allowing uncached dispatch. Truffle Libraries enable modularity and encapsulation for representation types in Truffle language implementations. Use with care and read the documentation first.

## Getting Started 

This section is intended to explain the basic usage of Truffle Libraries to language implementers. The full API documentation can be found in the [javadoc](TODO). This document assumes aknowledge of Truffle APIs and the use of `@Specialization` with the `@Cached` annotation.

## Motivating Example

When implementing arrays it is often more efficient to have multiple representations. For example, if the array is constructed from a sequence of integers then it is best represented using the `start`, `stride` and `length`  instead of materializing the full array. Of course, when an array element is written then the array needs to be materialized. In this example we are going to implement an array implementation with two representations:

* Buffer: represents a buffered array representation that can be efficiently appended to.
* Sequence: represents a sequence of numbers represented by `start`, `stride` and `length`.

To keep the example simple we will only support `int` values and we will also ignore index bounds error handling. We will also just implement the read operation and not the typically more complicated write operation.

To make the example more interesting, we will implement an optimization that will us to constant fold sequenced array accesses even if the array is not constant. Lets assume we have the following code snippet `range(start, stride, length)[2]`. In this snippet the variables `start` and `stride` are not known to be constant values therefore we would need to run the following code: `start + 

In the dynamic array implementation of Graal.js we use 20 different representations. There are representations for constant, zero-based, contiguous, holes and sparse arrays. Some representations are further specialized for the types `byte`, `int`, `double`, `JSObject` and `Object`. The source code can be found [here](https://github.com/graalvm/graaljs/tree/master/graal-js/src/com.oracle.truffle.js.runtime/src/com/oracle/truffle/js/runtime/array/dyn).

In the following sections we describe multiple implementation strategies for the array representations, ultimately describing how to use Truffle Libraries.

### Strategy 1: Specialization per representation

For this strategy we will start by declaring classes for the two representations `BufferArray` and `SequenceArray`.

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
These classes are straight forward. The `BufferArray` has a mutable buffer and length and is used as the generic array representation. The sequence array specifies final fields for `start`, `stride` and `length`.

Now lets specify our the basic read operation like this:

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
    int doSequence(SequenceArray array, int index) {
        return array.start + array.stride * index;
    }
}
```
The array read node specifies two specializations for the buffer version and the sequence. AS mentioned before we are going to ignore error bounds checks for simplicitly in our example.

Now lets try to make the array read specialize on the constant-ness of values of the sequence in order to allow the `range(start, stride, length)[2]` example fold if start and stride are constant, but it is not generally known by the compiler. In order to profile the value of the seen start and stride value we can cache both values by adding another specialization:

```java
@NodeChild @NodeChild
class ReadArrayNode extends ExpressionNode {
    /*...*/
    @Specialization(guards = {"array.stride == cachedStride", 
                               array.start  == cachedStart}, limit = "1")
    int doSequenceCached(SequenceArray array, int index, 
             @Cached("array.start")  int cachedStart,
             @Cached("array.stride") int cachedStride) {
        boundsCheck(array.length, index);
        return cachedStart + cachedStride * index;
    }
}
```

If the speculation guards succeed the start and stride are effectively constant, e.g. with the values `3` and `2`, the compiler would see `3 + 2 * 2` which is trivially `7`. The limit is set to `1` to only try this speculation only for one combination of values. In this case, it is likely to not be efficient to add more than one combination as this would introduce additional control flow. If the speculation does not succeed, the node observers multiple start and stride values we want to fallback to the generic sequence specialization. Therfore we change the `doSequence` specialization to this:

```java
@NodeChild @NodeChild
class ReadArrayNode extends ExpressionNode {
    /*...*/
    @Specialization(replaces = "doSequenceCached")
    int doSequence(SequenceArray array, int index) {
        return array.start + array.stride * index;
    }
}
```

That is it. We achieved the goal of implementing our array representations including additional profiling.

On the plus side of this strategy:

* The operation is easy to read and all cases are fully enumerated.
* The implementation of the read node can be very space efficient, requiring only a single bit per specialization to remember which representation type was observed at runtime.

On the negative side:

* Changing representation requires to modify many operations. There are potentially many operations specializating for the representations.
* Representation types need to expose most implementation details. 
* There is no convenient way to use array operations from slow-paths. One would need to create a node each time it is invoked or use `@GenerateUncached` and lookup the uncached version of the node.
* New representations cannot be loaded dynamically, they need to be statically known.

### Strategy 2: Java Interfaces

Lets try to address add support for loading new representations by abstracting the array using Java interfaces. Lets start by defining inter


```java
interface Array { 
    int read(int index); 
}

final class BufferArray implements Array {
    private int length;
    private int[] buffer;
    /*...*/
    @Override public int read(int index) {
        return buffer[index];
    }
}

final class SequenceArray implements Array {
    private final int length;
    private final int start;
    private final int stride;
    /*...*/
    @Override public int read(int index) {
        return start + (stride * index);
    }
}
@NodeChild @NodeChild
abstract class ArrayReadNode extends ExpressionNode {
    @Specialization 
   int doDefault(Array array, int index) {
        return array.read(index);
    }
}
```

In this solution the `ArrayReadNode` default specialization has the problem that the partial evaluator does not know which concrete type the array parameter has. It therefore needs to stop partial evaluation and emits a slow interface call for the `read` method call. This is not what we want, but we can introduce a polymorphic type cache to resolve this:

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

Now we solved the problem of partially evaluating the implementation, but there is no way to express the extra specialization for the constant stride and start index of the array without again extending. 

On the positive side:

* Interfaces are existing well-known concept for polymorphism.
* New interface implementations can be loaded.
* Convenient way to use the operations from slow-paths.
* Representation types can encapsulate implementation details. 

On the negative side:

* No representation specific profiling / caching can be performed.
* Requires a polymorphic class cache on every call-site.


### Strategy 3 : Truffle Libraries

Truffle Libraries work similar to the Java interfaces. Instead of a Java interface an abstract class extending `Library` class annotated with `@GenerateLibrary` is specified. We create abstract methods like with the interface but we insert a receiver argument in the beginning, in our case of type `Object`. Instead of performing interface type checks we use an explicit abstract method in the library typically named `is${Type}`.

Lets apply this for our example:

```java
@GenerateLibrary
public abstract class ArrayLibrary extends Library {

    public abstract boolean isArray(Object receiver);

    public abstract int read(Object receiver, int index);
}
```

This `ArrayLibrary` specifies two messages `isArray` and `read`.

Libraries are exported using the `@ExportLibrary` annotation by the receiver type.
Message exports are specified directly in the representation type and can therefore omit the receiver argument.

The first representation we implement is the BufferArray representation:

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

The BufferArray implementation can now export the required messages. The implementation is very similar to the interface version, but in addition we need to specify the `isArray` message. An annotation processor takes care of all the boilerplate code in the background by generating the necessary Java code to register and load that implementation.

Next, we implement the sequence representation. We start by implementating without speculating for the start and stride value.

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

Lets implement our stride and start speculation using this mechanism:

```java
@ExportLibrary(ArrayLibrary.class)
final class SequenceArray {
    final int start;
    final int stride;
    final int length;
    /*...*/
    
    @ExportMessage static class Read {
        @Specialization(guards = {"array.stride == cachedStride", 
                                   array.start  == cachedStart}, limit = "1")
        static int doSequenceCached(SequenceArray array, int index, 
                 @Cached("array.start")  int cachedStart,
                 @Cached("array.stride") int cachedStride) {
            boundsCheck(array.length, index);
            return cachedStart + (cachedStride * index);
        }
    
        @Specialization(replaces = "doSequenceCached")
        static int doSequence(SequenceArray array, int index) {
            return doSequenceCached(array, index, array.start, array.stride);
        }
    }
}
```

Since the message is declared using an inner class we need to the specify the receiver type.
Compared to normal nodes, this class must not extend `Node` and its methods must be `static` to allow the annotation processor to generate efficient code for the cached library node.


Last, we need to use the array library in our operation. The Truffle Library API provides an annotation called `@CachedLibrary` that does the heavy lifting of using libraries. The first argument provided to the annotation specifies the value the library is cached on.


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

Instead of using the `Array` type in the specialization we now need to explicitly invoke `isArray` on the `arrays` library. As a naming convention we use the plural of the parameter name, e.g. a library for an `array` value is called `arrays`. Using a `@CachedLibrary` annotation in a specialization requires us to specify the limit. The limit specifies when the specialization is rewriting itself to a version that uses the uncached version of the operation.

In strategy 2 the array read operation was simple to use from slow-paths by just invoking the interface method because it did not perform any caching/profiling. With Truffle Libraries support profiling and therfore need to account for that for slow-path calls. Every exported message generates two separeate implementations: a cached and an uncached version. The cached version includes profiling/caching while the uncached version uses the semantics of `@GenerateUncached` to automatically generate the uncached version. The uncached version will be used for 


On the positive side:

* New exported implementations can be loaded dynamically enabling modularity.
* Allows to cache and profile specific to the exported implementation.
* Convenient way to use the operations from slow-paths using uncached calls.
* Representation types can encapsulate implementation details. 

On the negative side:

* No representation specific profiling / caching can be performed.
* Requires a polymorphic class cache on every call-site.

This finalizes the introduction to Truffle Libraries. Next we will discuss when to use it.


## Conclusion?

_When to use Libraries?_

* If the representations are modular and cannot be enumerated for an operation. (e.g. Truffle Interoperability)
* If there is more than one representations of a type and one of the representation needs profiling/caching. (e.g. see the motivating example)
* If there is a need for a way to proxy all values of a language. (e.g. for dynamic taint tracking)

_When not to use Libraries?_

* For basic types that only have one representation.
* For primitive representations that that require boxing elimination to speed up the interpreter. Boxing elimination is not supported with Truffle Libraries at the moment. 


## Whats Next?

* Have a look at advanced examples [here]().

* Read the interop migration guide, that uses Truffle libraries [here]().

* Get started on the reference javadoc documentation [here]().



The description of Truffle Libraries is split into three parts:

* _Libraries_ define the set of available messages i.e. the protocol. 
* _Exports_ implement a library for a receiver type. 
* _Dispatches_ call a library message with a concrete receiver and additional parameters. 

Please follow the links to the corresponding javadoc for further details.

### Advanced Topics

* _Reflection_ allows to reflectively export and call messages without binary dependency to a library. It also allows to implement library agnostic proxies.

* _Dynamic Dispatch_ allows to dynamically define the exports to use for a receiver type. This is use-ful if you have a final class and the library implementation is different depending on a value of the receiver.

### Examples 

Case Study: Export Interop Messages

Case Study: Use Interop Messages

Case Study: Simplified Vector Abstraction


The following sections will describe these concepts in more detail.

### Terminology

First, we need to explain the basic terminology that will be used throughout this section.

* *Library*:
* *Message*:
* *Exports*:
* *Cached Dispatch*:
* *Uncached Dispatch*:
* *External Dispatch*:
* *Internal Dispatch*:

### Libraries

Libraries are specified with `public` and `abstract` Java classes that extend `Library` and are annotated by `@GenerateLibrary`. A library consists of a set of messages, that are specified using public Java methods. The methods may be abstract or use a default implementations. The first parameter of every method is the receiver parameter, which is mandatory and must be a subtype of `java.lang.Object` and the same across all messages of a library. There are no restrictions on the return type or argument types of a message. Every method that specifies a message must have a name that is unique for a library. Final or private methods are ignored. Parameter type overloading is currently not support for messages. Generic type arguments local to messages are generally supported, but generic type arguments on the library type are not yet supported. 

The following example specifies a basic library for arrays:

```java
@GenerateLibrary
public abstract class ArrayLibrary extends Library {

    public abstract boolean isArray(Object receiver);

    public abstract int read(Object receiver, int index);
}
```

These messages will throw an `AbstractMethodError` if they are not exported for a given receiver type. In order to customize the abstract message behavior, default implementations can be provided. For example:

```java
@GenerateLibrary
public abstract class ArrayLibrary extends Library {

    public boolean isArray(Object receiver) {
        return false;
    }

    public int read(Object receiver, int index) {
        throw new UnsupportedOperationException();
    }
}
```

In this example a receiver that does _not_ export the `ArrayLibrary` will return `false` for `isArray` and throw an `UnsupportedOperationException` for `read` calls.

If messages should be abstract and have a default implementation the `@GenerateLibrary.Abstract` annotation can be used. This is useful to require certain messages to be implemented only if the library is exported. The abstract annotation can also be specified depending on whether other messages are already exported.


### Assertions

Assertion wrappers may be specified to verify pre and post conditions of a library. Assertion wrappers are only inserted when assertions (-ea) are enabled. It is required that assertion wrappers don't introduce additional side-effects and call the delegate methods exactly once. 

For example:

```java
@GenerateLibrary(assertions = ArrayAssertions.class)
public abstract class ArrayLibrary extends Library {

    public boolean isArray(Object receiver) {
        return false;
    }

    public int read(Object receiver, int index) {
        throw new UnsupportedOperationException();
    }

	 static class ArrayAssertions extends ArrayLibrary {
	
	    @Child private ArrayLibrary delegate;
	
	    ArrayAssertions(ArrayLibrary delegate) {
	        this.delegate = delegate;
	    }
	
	    @Override public boolean isArray(Object receiver) {
	        return delegate.isArray(receiver);
	    }
	
	    @Override public int read(Object receiver, int index) {
	        int result = super.read(receiver, index);
	        assert delegate.isArray(receiver) : 
	                   "if a read was successful the receiver must be an array";
	        return result;
	    }
	
	    @Override public boolean accepts(Object receiver) {
	        return delegate.accepts(receiver);
	    }
	}
}

```

In this example we verify the post condition that the receiver must be an array if an array read was successful. Assertion wrappers provide a zero-overhead way to verify the contracts of the library.

## Exports

### Default Exports

### Default Exports


## Dispatching

### Dispatching using nodes

### Dispatching using slow-paths.



## Advanced Features

### Reflection

### Proxies

### Dynamic Dispatch

### Assertions





