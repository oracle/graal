# Dynamic Object Model Tutorial

This tutorial demonstrates how to get started with using the [DynamicObject](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/object/DynamicObject.html) and [DynamicObjectLibrary](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/object/DynamicObjectLibrary.html) API introduced with GraalVM 20.2.0.
The full documentation can be found in the [Javadoc](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/object/DynamicObjectLibrary.html).

## Motivation

When implementing a dynamic language, the object layout of user-defined objects/classes can often not be statically inferred and needs to accommodate dynamically added members and changing types.
This is where the Dynamic Object API comes in: it takes care of the object layout and classifies objects by their shape, i.e., their properties and the types of their values.
Access nodes can then cache the encountered shapes, forego costly checks, and access object properties more efficiently.

## Getting Started

Guest language should have a common base class for all language objects that extends `DynamicObject` and implements `TruffleObject`, like so:

```java
@ExportLibrary(InteropLibrary.class)
public class BasicObject extends DynamicObject implements TruffleObject {

    public BasicObject(Shape shape) {
        super(shape);
    }

    @ExportMessage
    boolean hasLanguage() {
        return true;
    }
    // ...
}
```
It makes sense to also export common `InteropLibrary` messages in this class.

Built-in object classes can then extend this base class and export additional messages, and as usual, extra Java fields and methods:
```java
@ExportLibrary(InteropLibrary.class)
public class Array extends BasicObject {

    private final Object[] elements;

    public Array(Shape shape, Object[] elements) {
        super(shape);
        this.elements = elements;
    }

    @ExportMessage
    boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    long getArraySize() {
        return elements.length;
    }
    // ...
}
```

Members can be accessed using the DynamicObjectLibrary, which can be obtained using the `@CachedLibrary` annotation of the Truffle DSL and `DynamicObjectLibrary.getFactory()` + `getUncached()`, `create(DynamicObject)`, and `createDispatched(int)`.
Here's an example of how it could be used to implement InteropLibrary messages:
```java
@ExportLibrary(InteropLibrary.class)
public class SimpleObject extends BasicObject {

    public UserObject(Shape shape) {
        super(shape);
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    Object readMember(String name,
                    @CachedLibrary("this") DynamicObjectLibrary objectLibrary)
                    throws UnknownIdentifierException {
        Object result = objectLibrary.getOrDefault(this, name, null);
        if (result == null) {
            /* Property does not exist. */
            throw UnknownIdentifierException.create(name);
        }
        return result;
    }

    @ExportMessage
    void writeMember(String name, Object value,
                    @CachedLibrary("this") DynamicObjectLibrary objectLibrary) {
        objectLibrary.put(this, name, value);
    }

    @ExportMessage
    boolean isMemberReadable(String member,
                    @CachedLibrary("this") DynamicObjectLibrary objectLibrary) {
        return objectLibrary.containsKey(this, member);
    }
    // ...
}
```

In order to construct instances of these objects, you first need a `Shape` that you can pass to the `DynamicObject` constructor.
This shape is created using [`Shape.newBuilder().build()`](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/object/Shape.Builder.html).
The returned shape describes the initial shape of the object and forms the root of a new shape tree.
As you are adding new properties with `DynamicObjectLibrary#put`, the object will mutate into other shapes in this shape tree.

Note that you should reuse the same initial shapes because shapes are internally cached per root shape.
It is recommended that you store the initial shapes in the `TruffleLanguage` instance, so they can be shared across contexts of the same engine.
Static shapes should be avoided except for singletons (like a `null` value).

Example:

```java
@TruffleLanguage.Registration(...)
public final class MyLanguage extends TruffleLanguage<MyContext> {

    private final Shape initialObjectShape;
    private final Shape initialArrayShape;

    public MyLanguage() {
        this.initialObjectShape = Shape.newBuilder(ExtendedObject.class).build();
        this.initialArrayShape = Shape.newBuilder().build();
    }

    public createObject() {
        return new MyObject(initialObjectShape);
    }
    //...
}
```

## Extended Object Layout

You can extend the default object layout with extra _dynamic fields_ that you hand over to the dynamic object model by adding `@DynamicField`-annotated field declarations of type `Object` or `long` in your subclasses, and specifying the _layout class_ with `Shape.newBuilder().layout(ExtendedObject.class).build();`.
Dynamic fields declared in this class and its superclasses will then automatically be used to store dynamic object properties and allow faster access to properties that fit into this reserved space.
Note: you must not access dynamic fields directly, always use `DynamicObjectLibrary` for this purpose.

```java
@ExportLibrary(InteropLibrary.class)
public class ExtendedObject extends SimpleObject {

    @DynamicField private Object _obj0;
    @DynamicField private Object _obj1;
    @DynamicField private Object _obj2;
    @DynamicField private long _long0;
    @DynamicField private long _long1;
    @DynamicField private long _long2;

    public ExtendedObject(Shape shape) {
        super(shape);
    }
}
```

## Caching Considerations

In order to ensure optimal caching, avoid reusing the same cached `DynamicObjectLibrary` for multiple, independent operations (`get`, `put`, etc.) and try to minimize the number of different shapes and property keys seen by each cached library instance.
When the property keys are known statically (compilation-final), always use a separate `DynamicObjectLibrary` for each property key.
Use dispatched libraries (`@CachedLibrary(limit=...)`) when putting multiple properties in succession.
Example:
```java
public abstract class MakePairNode extends BinaryExpressionNode {
    @Specialization
    Object makePair(Object left, Object right,
                    @CachedLanguage MyLanguage language,
                    @CachedLibrary(limit = "3") putLeft,
                    @CachedLibrary(limit = "3") putRight) {
        MyObject obj = language.createObject();
        putLeft.put(obj, "left", left);
        putRight.put(obj, "right", right);
        return obj;
    }
}
```

<hr/>

### Further Reading

A high-level description of the object model has been published in [**An Object Storage Model for the Truffle Language Implementation Framework**](http://dl.acm.org/citation.cfm?id=2647517).

See [Truffle docs](https://github.com/oracle/graal/tree/master/truffle/docs) and [Publications.md](https://github.com/oracle/graal/blob/master/docs/Publications.md) for more tutorials, presentations, and publications about Truffle and GraalVM.
