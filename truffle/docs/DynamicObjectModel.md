---
layout: docs
toc_group: truffle
link_title: Dynamic Object Model
permalink: /graalvm-as-a-platform/language-implementation-framework/DynamicObjectModel/
---
# Dynamic Object Model

This guide demonstrates how to use the [`DynamicObject`](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/object/DynamicObject.html) and node APIs introduced with GraalVM 25.1.
The full documentation can be found in the [Javadoc](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/object/DynamicObject.html).

### Motivation

When implementing a dynamic language, the object layout of user-defined objects/classes often cannot be statically inferred and needs to accommodate dynamically added members and changing types.
This is where the Dynamic Object API comes in: it takes care of the object layout and classifies objects by their shape, i.e., their properties, and the types of their values.
Access nodes can then cache the encountered shapes, forego costly checks and access object properties more efficiently.

## Getting Started

A guest language should have a common base class for all language objects that extends `DynamicObject` and implements `TruffleObject`. For example:

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

Builtin object classes can then extend this base class and export additional messages, and, as usual, extra Java fields and methods:
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

You can access dynamic object members through `DynamicObject` access nodes. To obtain these nodes, cache them using the `@Cached` annotation provided in the Truffle DSL.
Here is an example of how it could be used to implement `InteropLibrary` messages:
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
                    @Cached DynamicObject.GetNode getNode)
                    throws UnknownIdentifierException {
        Object result = getNode.execute(this, name, null);
        if (result == null) {
            /* Property does not exist. */
            throw UnknownIdentifierException.create(name);
        }
        return result;
    }

    @ExportMessage
    void writeMember(String name, Object value,
                    @Cached DynamicObject.PutNode putNode) {
        putNode.execute(this, name, value);
    }

    @ExportMessage
    boolean isMemberReadable(String member,
                    @Cached DynamicObject.ContainsKeyNode containsKeyNode) {
        return containsKeyNode.execute(this, member);
    }
    // ...
}
```

In order to construct instances of these objects, you first need a `Shape` that you can pass to the `DynamicObject` constructor.
This shape is created using [`Shape.newBuilder().build()`](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/object/Shape.Builder.html).
The returned shape describes the initial shape of the object and forms the root of a new shape tree.
As you are adding new properties with `DynamicObject.PutNode#execute`, the object will mutate into other shapes in this shape tree.

Note: You should reuse the same initial shapes because shapes are internally cached per root shape.
It is recommended that you store the initial shapes in the `TruffleLanguage` instance, so they can be shared across contexts of the same engine.
Static shapes should be avoided except for singletons (like a `null` value).

For example:

```java
@TruffleLanguage.Registration(...)
public final class MyLanguage extends TruffleLanguage<MyContext> {

    private final Shape initialObjectShape;
    private final Shape initialArrayShape;

    public MyLanguage() {
        this.initialObjectShape = Shape.newBuilder().layout(ExtendedObject.class).build();
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
Note: You must not access dynamic fields directly. Always use `DynamicObject` nodes for this purpose.

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

To ensure optimal caching, avoid reusing the same cached `DynamicObject` node (`GetNode`, `PutNode`, etc.) for multiple independent operations.
Try to minimize the number of different shapes and property keys that each cached node instance encounters.
When the property keys are known statically (compilation-final), always use a separate `DynamicObject` node for each property key.
For example:
```java
public abstract class MakePairNode extends BinaryExpressionNode {
    @Specialization
    Object makePair(Object left, Object right,
                    @CachedLanguage MyLanguage language,
                    @Cached DynamicObject.PutNode putLeft,
                    @Cached DynamicObject.PutNode putRight) {
        MyObject obj = language.createObject();
        putLeft.execute(obj, "left", left);
        putRight.execute(obj, "right", right);
        return obj;
    }
}
```

<hr/>

## Further Reading

A high-level description of the object model has been published in [**An Object Storage Model for the Truffle Language Implementation Framework**](http://dl.acm.org/citation.cfm?id=2647517).

See [Truffle publications](https://github.com/oracle/graal/blob/master/docs/Publications.md) for more presentations and publications about Truffle and GraalVM.
