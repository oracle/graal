---
layout: docs-experimental
toc_group: espresso
link_title: Interoperability with Truffle Languages
permalink: /reference-manual/java-on-truffle/interoperability/
---

# Interoperability with Truffle Languages

Java on Truffle allows you to interface other "Truffle" languages (languages which interpreters are implemented with the [Truffle framework](../../../truffle/docs/README.md)) to create polyglot programs -- programs written in more than one language.

This guide describes how to load code written in foreign languages, how to export and import objects between languages, how to use Java on Truffle objects from a foreign language, how to use foreign objects from Java on Truffle, and how to embed in host Java.

To avoid confusion, the terms *host* and *guest* are used to differentiate the different layers where Java is executed. Java on Truffle refers to the guest layer.

You pass polyglot options to the `java -truffle` launcher.
If you are using the native configuration, you will need to use the `--polyglot` flag to get access to other languages.

Foreign objects must "inhabit" a guest Java type when flowing into Java on Truffle.
How this type is attached to foreign objects is an implementation detail.

## Polyglot

Java on Truffle provides a guest Java Polyglot API, described in `polyglot.jar`.
This JAR is automatically injected on guest Java contexts but can be excluded with `--java.Polyglot=false`.

You can import the `Polyglot` class to interact with other guest languages:

```java
// guest java
import com.oracle.truffle.espresso.polyglot.Polyglot;

int two = Polyglot.eval(int.class, "js", "1+1");
```

You can determine if an object is foreign:
```java
// guest java
Object foreign = Polyglot.eval("js", "[2, 0, 2, 1]");
Object local = new int[]{2, 0, 2, 1};
System.out.println(Polyglot.isForeignObject(foreign)); // prints true
System.out.println(Polyglot.isForeignObject(local));   // prints false
```

You can cast foreign objects to guest Java types:
```java
// guest java
Object foreignArray = Polyglot.eval("js", "['a string', 42, 3.14159, null]");
Object[] objects = Polyglot.cast(Object[].class, foreignArray);

assert objects.length == 4;
String elem0 = Polyglot.cast(String.class, objects[0]);   // eager conversion
Integer elem1 = Polyglot.cast(Integer.class, objects[1]); // preserves identity
int elem1_ = Polyglot.cast(int.class, objects[1]);        // eager conversion

double elem2 = Polyglot.cast(double.class, objects[2]);   // eager conversion
Object elem3 = objects[3];
assert elem3 == null;
```

The `Polyglot.cast(targetClass, obj)` method is an augmented Java cast, e.g., `targetClass.cast(obj)`:
 - Java cast succeeds &rArr; `Polyglot.cast` succeeds.
 - Java cast does not succeeds, `Polyglot.cast` can "re-type" foreign objects, e.g., to cast to `Integer`, the foreign object must `fitsInInt`.
 - If `Polyglot.cast` fails, it will throw `ClassCastException` similar to `Class#cast`.

`Polyglot.cast` supports a natural mapping from common interop "kinds" to Java types, summarized below:

| Interop "kind" | Allowed types | Preserves identity |
|-|-|-|
| isBoolean | Boolean/boolean | Yes* (boxed type) |
| fitsInByte | Byte/byte | Yes* (boxed type) |
| fitsInShort | Short/short | Yes* (boxed type) |
| fitsInInt | Integer/int | Yes* (boxed type) |
| fitsInLong | Long/long | Yes* (boxed type) |
| fitsInFloat | Float/float | Yes* (boxed type) |
| fitsInDouble | Double/double | Yes* (boxed type) |
| isString & 1-character | Character/char | Yes* (boxed type) |
| isString | String | No (eager conversion) |
| isException & Polyglot.isForeignObject | ForeignException | Yes |
| hasArrayElements | Object[] | Yes |
| isNull | * | Yes |
| * | Object | Yes |

You can access the polyglot bindings:
```
// guest java
Object foreignObject = Polyglot.importObject("foreign_object");

// Also typed imports
String userName = Polyglot.importObject("user_name", String.class);
int year = Polyglot.importObject("year", int.class);

// And exports
Polyglot.exportObject("data", new double[]{56.77, 59.23, 55.67, 57.50, 64.44, 61.37);
Polyglot.exportObject("message", "Hello, Espresso!");
```

## Interop Protocol

Java on Truffle provides an explicit guest API to access the [Interop protocol](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html).
It contains methods mimicking the interop protocol messages.
This API can be used on guest Java objects as well.

```java
// guest java
import com.oracle.truffle.espresso.polyglot.Interop;

Object foreignArray = Polyglot.eval("js", "[2, 0, 2, 1]");
System.out.println(Interop.hasArrayElements(foreignArray)); // prints true
System.out.println(Interop.getArraySize(foreignArray));     // prints 4

Object elem0 = Interop.readArrayElement(foreignArray, 0);
System.out.println(Interop.fitsInInt(elem0)); // prints true
System.out.println(Interop.asInt(elem0));     // prints 2
```

## Embedding in Host Java

Java on Truffle is embedded via the [Polyglot API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/package-summary.html), which is part of GraalVM.

```java
// host java
import org.graalvm.polyglot.*;

class Embedding {
    public static void main(String[] args) {
        Context polyglot = Context.newBuilder().allowAllAccess(true).build();

        // Class loading is exposed through language bindings, with class
        // names using the same format as Class#forName(String).
        Value intArray = polyglot.getBindings("java").getMember("[I");
        Value objectArray = polyglot.getBindings("java").getMember("[Ljava.lang.Object;")

        Value java_lang_Math = polyglot.getBindings("java").getMember("java.lang.Math");
        double sqrt2 = java_lang_Math.invokeMember("sqrt", 2).asDouble();
        double pi = java_lang_Math.getMember("PI").asDouble();
        System.out.println(sqrt2);
        System.out.println(pi);
    }
}
```

A number of useful context option can be set with `contextBuilder.option(key, value)`:
* Java properties can be added by settings `java.Properties.property.name` to the desired value (in this case this would set the `property.name`).
* `java.Properties.java.class.path` can be used to set the classpath of the Java on Truffle context.
* `java.Properties.java.library.path` can be used to set the native library path of the Java on Truffle context.
* `java.EnableAssertions` can be set to `true` to enable assertions.
* `java.EnableSystemAssertions` can be set to `true` to enable assertions in the Java standard library.
* `java.Verify` can be set to `none`, `remove`, or `all` to control whether bytecode verification does not happen, only happens on user code, or happens for all classes.
* `java.JDWPOptions` can be set to setup and enable debugging over JDWP. For example, it could be set to `transport=dt_socket,server=y,address=localhost:8000,suspend=y`.
* `java.Polyglot` can be set to `true` or `false` to allow or deny access to the polyglot features from the `com.oracle.truffle.espresso.polyglot` package.

***Java on Truffle does not support evaluation (`.eval`) of Java sources.**

In Java, methods can be overloaded, e.g., several methods can share the same name, with different signatures.
To remove ambiguity, Java on Truffle allows to specify the [method descriptor](https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-MethodDescriptor) in the `methodName/methodDescriptor` form:

```java
// host java
Value java_lang_String = polyglot.getBindings("java").getMember("java.lang.String");
// String#valueOf(int)
String valueOf = String.format("%s/%s", "valueOf", "(I)Ljava/lang/String;");
Value fortyTwo = java_lang_String.invokeMember(valueOf, 42);
assert "42".equals(fortyTwo.asString());
```

#### Class<?> instance vs. static class accessor (Klass):
The static class accessor allows to access (public) static fields and call (public) static methods.

```java
// Class loading through language bindings return the static class accessor.
Value java_lang_Number = polyglot.getBindings("java").getMember("java.lang.Number");
Value java_lang_Class = polyglot.getBindings("java").getMember("java.lang.Class");

// Class#forName(String) returns the Class<Integer> instance.
Value integer_class = java_lang_Class.invokeMember("forName", "java.lang.Integer");

// Static class accessor to Class<?> instance and viceversa.
assert integer_class.equals(java_lang_Integer.getMember("class"));
assert java_lang_Integer.equals(integer_class.getMember("static"));

// Get Integer super class.
assert java_lang_Number.equals(java_lang_Integer.getMember("super"));
```

## Multithreading

Java on Truffle is designed to be a multi-threaded language and much of the ecosystem expects threads to be available.
This may be incompatible with other Truffle languages which do not support threading, so you can disable the creation of multiple threads with the option `--java.MultiThreaded=false`.

When this option is enabled, finalizers will not run, neither the `ReferenceQueue` notification mechanism.
Both these features would require starting new threads. Note that the garbage-collection of weakly reachable objects remains unaffected.
