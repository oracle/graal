# Dynamic proxies on Substrate VM
(See also the [guide on assisted configuration of Java dynamic proxies and other dynamic features](CONFIGURE.md))

Java dynamic proxies, implemented by `java.lang.reflect.Proxy`, provide a mechanism which enables object level access control by routing all method invocations through a `java.lang.reflect.InvocationHandler`.
Dynamic proxy classes are generated from a list of interfaces.

Substrate VM doesn't provide machinery for generating and interpreting bytecodes at run time.
Therefore all dynamic proxy classes need to be generated at native image build time.


# Automatic detection
Substrate VM employs a simple static analysis that detects calls to `java.lang.reflect.Proxy.newProxyInstance(ClassLoader, Class<?>[], InvocationHandler)` and `java.lang.reflect.Proxy.getProxyClass(ClassLoader, Class<?>[])` and tries to determine the list of interfaces that define dynamic proxies automatically.
Given the list of interfaces then Substrate VM generates the proxy classes at image build time and adds them to the native image heap.
In addition to generating the dynamic proxy class the constructor of the generated class that takes a `java.lang.reflect.InvocationHandler` argument, i.e., the one reflectively invoked by `java.lang.reflect.Proxy.newProxyInstance(ClassLoader, Class<?>[], InvocationHandler)`, is registered for reflection so that dynamic proxy instances can be allocated at run time.

The analysis is limited to situations where the list of interfaces comes from a constant array or an array that is allocated in the same method.
For example in the code snippets bellow the dynamic proxy interfaces can be determined automatically.

### Static final array:

```
class ProxyFactory {

    private static final Class<?>[] interfaces = new Class<?>[]{java.util.Comparator.class};

    static Comparator createProxyInstanceFromConstantArray() {
        ClassLoader classLoader = ProxyFactory.class.getClassLoader();
        InvocationHandler handler = new ProxyInvocationHandler();
        return (Comparator) Proxy.newProxyInstance(classLoader, interfaces, handler);
    }
}
```
Note: The analysis operates on Graal graphs and not source code.
Therefore the following ways to declare and populate an array are equivalent from the point of view of the analysis:

```
private static final Class<?>[] interfacesArrayPreInitialized = new Class<?>[]{java.util.Comparator.class};
```

```
private static final Class<?>[] interfacesArrayLiteral = {java.util.Comparator.class};
```

```
private static final Class<?>[] interfacesArrayPostInitialized = new Class<?>[1];
static {
    interfacesArrayPostInitialized[0] = java.util.Comparator.class;
}
```
However, in Java there are no immutable arrays.
Even if the array is declared as `static final` its contents can change later on.
The simple analysis that we employ here doesn't track further changes to the array.

### New array:

```
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
```
Class<?>[] interfaces = new Class<?>[]{java.util.Comparator.class};
```

```
Class<?>[] interfaces = new Class<?>[1];
interfaces[0] = Question.class;
```

```
Class<?>[] interfaces = {java.util.Comparator.class};
```

The static analysis covers code patterns most frequently used to define dynamic proxy classes.
For the exceptional cases where the analysis cannot discover the interface array there is also a manual dynamic proxy configuration mechanism.

# Manual configuration
Dynamic proxy classes can be generated at native image build time by specifying the list of interfaces that they implement.
Substrate VM provides two options for this purpose: `-H:DynamicProxyConfigurationFiles=<comma-separated-config-files>` and `-H:DynamicProxyConfigurationResources=<comma-separated-config-resources>`.

These options accept JSON files whose structure is an array of arrays of fully qualified interface names.

Example:

```
[
    ["java.lang.AutoCloseable", "java.util.Comparator"],
    ["java.util.Comparator"],
    ["java.util.List"]
]
```

The `java.lang.reflect.Proxy` API also allows creation of a dynamic proxy that doesn't implement any user provided interfaces.
Therefore the following is a valid configuration:

```
[
    []
]
```

In this case the generated dynamic proxy class only implements `java.lang.reflect.Proxy`.

# Dynamic proxy classes in static initializers

Dynamic proxy classes and instances of dynamic proxy classes that are defined in static initializers can be accessed at runtime without any special handling.
This is possible since the static initializers are executed at native image build time.
For example this will work:

```
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
