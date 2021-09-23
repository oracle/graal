# Static Object Model

This guide demonstrates how to get started with using the [StaticShape](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/staticobject/StaticShape.html) and [StaticProperty](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/staticobject/StaticProperty.html) APIs introduced with GraalVM 21.3.0.
The full documentation can be found in the [Javadoc](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/staticobject/package-summary.html).

### Motivation

The Static Object Model provides abstractions to represent the layout of objects that, once defined, do not change the number and the type of their properties.
It is particularly well suited for, but not limited to, the implementation of the object model of static programming languages.
Its APIs define the object layout ([StaticShape](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/staticobject/StaticShape.html)), execute property accesses ([StaticProperty](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/staticobject/StaticProperty.html)), and allocate static objects ([DefaultStaticObjectFactory](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/staticobject/DefaultStaticObjectFactory.html)).
The implementation is efficient and executes safety checks on property accesses that can be disabled if they are already executed by the language implementation, for example by a verifier.

The Static Object Model does not provide constructs to model the visibility of properties and does not distinguish between static and instance properties.
Its APIs are not compatible with those of the [Dynamic Object Model](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/object/package-summary.html), which is more suited for dynamic languages.

## Getting Started

In this first example, let's assume that:
1. `language` is an instance of the [TruffleLanguage](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/TruffleLanguage.html) that we are implementing.
2. We want to represent an object with the following static layout:
   - An `int` property named `property1`.
   - An `Object` property named `property2` which can be stored as a final field. Later we will see in detail what this implies.

Here is how to use the Static Object Model to represent this layout:

```java
public class GettingStarted {
    public void simpleShape(TruffleLanguage<?> language) {
        StaticShape.Builder builder = StaticShape.newBuilder(language);
        StaticProperty p1 = new DefaultStaticProperty("property1");
        StaticProperty p2 = new DefaultStaticProperty("property2");
        builder.property(p1, int.class, false);
        builder.property(p2, Object.class, true);
        StaticShape<DefaultStaticObjectFactory> shape = builder.build();
        Object staticObject = shape.getFactory().create();
        ...
    }
}
```

We start by creating a [StaticShape.Builder](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/staticobject/StaticShape.Builder.html) instance, passing a reference to the language that we are implementing.
Then, we create [DefaultStaticProperty](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/staticobject/DefaultStaticProperty.html) instances that represent the properties that we want to add to the static object layout.
The String id passed as argument must be unique within a builder.
After creating the properties we register them to the builder instance:
- The first argument is the [StaticProperty](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/staticobject/StaticProperty.html) that we register.
- The second argument is the type of the property. It can be a primitive class or `Object.class`.
- The third argument is a boolean value that defines if the property can be stored as a final field.
  This gives the compiler the opportunity to perform additional optimizations.
  For example, reads to this property might be constant-folded.
  It's important to note that the Static Object Model does not check if a property stored as final is not assigned more than once and that it is assigned before it is read.
  Doing so might lead to wrong behavior of the program, and it is up to the user to enforce that this cannot happen.
We then create a new static shape calling `builder.build()`.
To allocate the static object, we retrieve the [DefaultStaticObjectFactory](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/staticobject/DefaultStaticObjectFactory.html) from the shape, and we invoke its `create()` method.

Now that we have our static object instance, let's see how to use the static properties to perform property accesses.
Expanding the example above:
```java
public class GettingStarted {
    public void simpleShape(TruffleLanguage<?> language) {
        ...
        p1.setInt(staticObject, 42);
        p2.setObject(staticObject, "42");
        assert p1.getInt(staticObject) == 42;
        assert p2.getObject(staticObject).equals("42");
    }
}
```

## Shape Hierarchies

It is possible to create a shape hierarchy by declaring that a new shape should extend an existing one.
This is done by passing the parent shape as argument to [StaticShape.Builder.build(StaticShape)](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/staticobject/StaticShape.Builder.html) when creating the child shape.
Properties of the parent shape can then be used to access values stored in static objects of the child shape.

In the following example we create a parent shape identical to the one discussed in [the previous section](#getting-started), then we extend it with a child shape that hides one of the properties of the parent shape.
Finally, we demonstrate how the various properties can be accessed.

```java
public class Subshapes {
    public void simpleSubShape(TruffleLanguage<?> language) {
        // Create a shape
        StaticShape.Builder b1 = StaticShape.newBuilder(language);
        StaticProperty s1p1 = new DefaultStaticProperty("property1");
        StaticProperty s1p2 = new DefaultStaticProperty("property2");
        b1.property(s1p1, int.class, false).property(s1p2, Object.class, true);
        StaticShape<DefaultStaticObjectFactory> s1 = b1.build();

        // Create a sub-shape
        StaticShape.Builder b2 = StaticShape.newBuilder(language);
        StaticProperty s2p1 = new DefaultStaticProperty("property1");
        b2.property(s2p1, int.class, false);
        StaticShape<DefaultStaticObjectFactory> s2 = b2.build(s1); // passing a shape as argument builds a sub-shape

        // Create a static object for the sub-shape
        Object o2 = s2.getFactory().create();

        // Perform property accesses
        s1p1.setInt(o2, 42);
        s1p2.setObject(o2, "42");
        s2p1.setInt(o2, 24);
        assert s1p1.getInt(o2) == 42;
        assert s1p2.getObject(o2).equals("42");
        assert s2p1.getInt(o2) == 24;    }
}
```

## Extending custom base classes

To reduce memory footprint, the language implementor might want static objects to extend the class that represents guest-level objects.
This is complicated by the fact that [StaticShape.getFactory()](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/staticobject/StaticShape.html) must return an instance of the factory class that allocates static objects.
To achieve this, we first need to declare an interface that:
* Defines a method for each visible constructor of the static object super class that we want to invoke.
* The arguments of each method must match those of the corresponding constructor.
* The return type of each method must be assignable from the static object super class.

For example, if the static objects should extend this class:
```java
public abstract class MyStaticObject {
    final String arg1;
    final Object arg2;

    public MyStaticObject(String arg1) {
        this(arg1, null);
    }

    public MyStaticObject(String arg1, Object arg2) {
        this.arg1 = arg1;
        this.arg2 = arg2;
    }
}
```

We need to declare the following factory interface:
```java
public interface MyStaticObjectFactory {
    MyStaticObject create(String arg1);
    MyStaticObject create(String arg1, Object arg2);
}
```

Finally, this is how to allocate the custom static objects:
```java
public void customStaticObject(TruffleLanguage<?> language) {
    StaticProperty property = new DefaultStaticProperty("arg1");
    StaticShape<MyStaticObjectFactory> shape = StaticShape.newBuilder(language).property(property, Object.class, false).build(MyStaticObject.class, MyStaticObjectFactory.class);
    MyStaticObject staticObject = shape.getFactory().create("arg1");
    property.setObject(staticObject, "42");
    assert staticObject.arg1.equals("arg1"); // fields of the custom super class are directly accessible
    assert property.getObject(staticObject).equals("42"); // static properties are accessible as usual
}
```

As you can see from the example above, fields and methods of the custom parent class are directly accessible and are not hidden by the static properties of the static object.


## Reducing memory footprint

Reading the Javadoc, you might have noticed that [StaticShape](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/staticobject/StaticShape.html) does not provide an API to access the associated static properties.
This reduces memory footprint in case the language implementation already has a way to store this information.
For example, an implementation of the Java language might want to store the static shape in the class that represents a Java class, and a static property in the class that represents a Java field.
In this case, the class representing a Java class should already have a way to retrieve the Java fields associated to it, hence the static properties associated to the shape.
To further reduce memory footprint, the language implementor might want the class representing a Java field to extend [StaticProperty](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/staticobject/StaticProperty.html).

Instead of storing the static property in the class that represents fields:
```java
class MyField {
    final StaticProperty p;

    MyField(StaticProperty p) {
        this.p = p;
    }
}

new MyField(new DefaultStaticProperty("property1"));
```

The class that represents fields can extend [StaticProperty](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/staticobject/StaticProperty.html):
```java
class MyField extends StaticProperty {
    final Object name;

    MyField(Object name) {
        this.name = name;
    }

    @Override
    public String getId() {
        return name.toString(); // this string must be a unique identifier within a Builder
    }
}

new MyField("property1");
```

## Safety Checks

On property access, the Static Object Model performs two types of safety checks:
1. That the [StaticProperty](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/staticobject/StaticProperty.html) method matches the type of the static property.

Example of wrong access:
```java
public void wrongMethod(TruffleLanguage<?> language) {
    StaticShape.Builder builder = StaticShape.newBuilder(language);
    StaticProperty property = new DefaultStaticProperty("property");
    Object staticObject = builder.property(property, int.class, false).build().getFactory().create();

    property.setObject(staticObject, "wrong access type"); // throws IllegalArgumentException
```

2. That the object passed to the accessor method matches the shape generated by the builder to which the property is associated, or one of its child shapes.

Example of wrong access:
```java
public void wrongShape(TruffleLanguage<?> language) {
    StaticShape.Builder builder = StaticShape.newBuilder(language);
    StaticProperty property = new DefaultStaticProperty("property");;
    Object staticObject1 = builder.property(property, Object.class, false).build().getFactory().create();
    Object staticObject2 = StaticShape.newBuilder(language).build().getFactory().create();

    property.setObject(staticObject2, "wrong shape"); // throws IllegalArgumentException
}
```

While these checks are often useful, they might be redundant if the language implementation already performs them, for example using a verifier.
While the first type of checks (on property type) is very efficient and cannot be disabled, the second type of checks (on the shape) is computationally expensive and can be disabled via a command line argument:
```
--experimental-options --engine.RelaxStaticObjectSafetyChecks=true
```

or when creating the [Context](https://www.graalvm.org/truffle/javadoc/org/graalvm/polyglot/Context.html):
```java
Context context = Context.newBuilder() //
                         .allowExperimentalOptions(true) //
                         .option("engine.RelaxStaticObjectSafetyChecks", "true") //
                         .build();
```

It is highly discouraged to relax safety checks in absence of other equivalent checks.
If the assumption on the correctness of the shape of the static objects is wrong, the VM is likely to crash.
