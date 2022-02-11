---
layout: docs
toc_group: native-image
link_title: Dynamic Proxy on Native Image
permalink: /reference-manual/native-image/DynamicProxy/
---
# Dynamic Proxy in Native Image

Java dynamic proxies, implemented by `java.lang.reflect.Proxy`, provide a mechanism which enables object level access control by routing all method invocations through `java.lang.reflect.InvocationHandler`.
Dynamic proxy classes are generated from a list of interfaces.

Native Image does not provide machinery for generating and interpreting bytecodes at run time.
Therefore all dynamic proxy classes need to be generated at native image build time.

See also the [guide on assisted configuration of Java resources and other dynamic features](BuildConfiguration.md#assisted-configuration-of-native-image-builds).

## Automatic Detection

Native Image employs a simple static analysis that detects calls to `java.lang.reflect.Proxy.newProxyInstance(ClassLoader, Class<?>[], InvocationHandler)` and `java.lang.reflect.Proxy.getProxyClass(ClassLoader, Class<?>[])`, then tries to determine the list of interfaces that define dynamic proxies automatically.
Given the list of interfaces, Native Image generates proxy classes at image build time and adds them to the native image heap.
In addition to generating the dynamic proxy class, the constructor of the generated class that takes a `java.lang.reflect.InvocationHandler` argument, i.e., the one reflectively invoked by `java.lang.reflect.Proxy.newProxyInstance(ClassLoader, Class<?>[], InvocationHandler)`, is registered for reflection so that dynamic proxy instances can be allocated at run time.

The analysis is limited to situations where the list of interfaces comes from a constant array or an array that is allocated in the same method.
For example, in the code snippets bellow the dynamic proxy interfaces can be determined automatically.

#### Static Final Array:

```java
class ProxyFactory {

    private static final Class<?>[] interfaces = new Class<?>[]{java.util.Comparator.class};

    static Comparator createProxyInstanceFromConstantArray() {
        ClassLoader classLoader = ProxyFactory.class.getClassLoader();
        InvocationHandler handler = new ProxyInvocationHandler();
        return (Comparator) Proxy.newProxyInstance(classLoader, interfaces, handler);
    }
}
```
Note: The analysis operates on compiler graphs and not source code.
Therefore the following ways to declare and populate an array are equivalent from the point of view of the analysis:

```java
private static final Class<?>[] interfacesArrayPreInitialized = new Class<?>[]{java.util.Comparator.class};
```

```java
private static final Class<?>[] interfacesArrayLiteral = {java.util.Comparator.class};
```

```java
private static final Class<?>[] interfacesArrayPostInitialized = new Class<?>[1];
static {
    interfacesArrayPostInitialized[0] = java.util.Comparator.class;
}
```
However, there are no immutable arrays in Java.
Even if the array is declared as `static final`, its contents can change later on.
The simple analysis employed here does not track further changes to the array.

#### New Array:

```java
class ProxyFactory {

    static Comparator createProxyInstanceFromNewArray() {
        ClassLoader classLoader = ProxyFactory.class.getClassLoader();
        InvocationHandler handler = new ProxyInvocationHandler();
        Class<?>[] interfaces = new Class<?>[]{java.util.Comparator.class};
        return (Comparator) Proxy.newProxyInstance(classLoader, interfaces, handler);
    }
}
```

Note: Just like with constant arrays, the following ways to declare and populate an array are equivalent from the point of view of the analysis:
```java
Class<?>[] interfaces = new Class<?>[]{java.util.Comparator.class};
```

```java
Class<?>[] interfaces = new Class<?>[1];
interfaces[0] = Question.class;
```

```java
Class<?>[] interfaces = {java.util.Comparator.class};
```

The static analysis covers code patterns most frequently used to define dynamic proxy classes.
For the exceptional cases where the analysis cannot discover the interface array there is also a manual dynamic proxy configuration mechanism.

## Manual Configuration

Dynamic proxy classes can be generated at native image build time by specifying the list of interfaces that they implement.
Native Image provides two options for that: `-H:DynamicProxyConfigurationFiles=<comma-separated-config-files>` and `-H:DynamicProxyConfigurationResources=<comma-separated-config-resources>`. These options accept JSON files whose structure is an array of arrays of fully qualified interface names. For example:

```json
[
 { "interfaces": [ "java.lang.AutoCloseable", "java.util.Comparator" ] },
 { "interfaces": [ "java.util.Comparator" ] },
 { "interfaces": [ "java.util.List" ] }
]
```
Note that the order of the specified proxy interfaces is significant: two requests for a `Proxy` class with the same combination of interfaces but in a different order will result in two distinct behaviours (for more detailed information, refer to [`Proxy Class `javadoc](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/reflect/Proxy.html).

The `java.lang.reflect.Proxy` API also allows creation of a dynamic proxy that does not implement any user provided interfaces.
In this case the generated dynamic proxy class only implements `java.lang.reflect.Proxy`.

## Dynamic Proxy Classes in Static Initializers

Dynamic proxy classes and instances of dynamic proxy classes that are defined in static initializers can be accessed at run time without any special handling.
This is possible since the static initializers are executed at native image build time.
For example, this will work:
```java
private final static Comparator proxyInstance;
private final static Class<?> proxyClass;
static {
    ClassLoader classLoader = ProxyFactory.class.getClassLoader();
    InvocationHandler handler = new ProxyInvocationHandler();
    Class<?>[] interfaces = {java.util.Comparator.class};
    proxyInstance = (Comparator) Proxy.newProxyInstance(classLoader, interfaces, handler);
    proxyClass = Proxy.getProxyClass(classLoader, interfaces);
}
```
