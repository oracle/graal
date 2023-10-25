---
layout: docs
toc_group: dynamic-features
link_title: Dynamic Proxy
permalink: /reference-manual/native-image/dynamic-features/DynamicProxy/
redirect_from: /reference-manual/native-image/DynamicProxy/
---

# Dynamic Proxy in Native Image

Java dynamic proxies, implemented by `java.lang.reflect.Proxy`, provide a mechanism which enables object level access control by routing all method invocations through `java.lang.reflect.InvocationHandler`.
Dynamic proxy classes are generated from a list of interfaces.

Native Image does not provide machinery for generating and interpreting bytecode at run time.
Therefore all dynamic proxy classes need to be generated at image build time.

## Automatic Detection

Native Image employs a simple static analysis that detects calls to `java.lang.reflect.Proxy.newProxyInstance(ClassLoader, Class<?>[], InvocationHandler)` and `java.lang.reflect.Proxy.getProxyClass(ClassLoader, Class<?>[])`, then tries to determine the list of interfaces that define dynamic proxies automatically.
Given the list of interfaces, Native Image generates proxy classes at image build time and adds them to the native image heap.
In addition to generating the dynamic proxy class, the constructor of the generated class that takes a `java.lang.reflect.InvocationHandler` argument, i.e., the one reflectively invoked by `java.lang.reflect.Proxy.newProxyInstance(ClassLoader, Class<?>[], InvocationHandler)`, is registered for reflection so that dynamic proxy instances can be allocated at run time.

The analysis is limited to situations where the list of interfaces comes from a constant array or an array that is allocated in the same method.
For example, in the code snippets bellow the dynamic proxy interfaces can be determined automatically.

### Static Final Array:

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

Note that the analysis operates on compiler graphs and not source code.
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

### New Array:

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


### Related Documentation

- [Configure Dynamic Proxies Manually](guides/configure-dynamic-proxies.md)
- [Reachability Metadata: Dynamic Proxy](ReachabilityMetadata.md#dynamic-proxy)
