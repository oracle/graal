---
layout: docs
toc_group: metadata
link_title: Reachability Metadata
permalink: /reference-manual/native-image/metadata/
---

# Reachability Metadata

The dynamic language features of the JVM (including reflection and resource handling) compute the *dynamically-accessed program elements* such as invoked methods or resource URLs at runtime. 
The `native-image` tool performs [static analysis](NativeImageBasics.md#static-analysis-reachability-and-closed-world-assumption) while building a native binary to determine those dynamic features, but it cannot always exhaustively predict all uses.
To ensure inclusion of these elements into the native binary, you should provide **reachability metadata** (in further text referred as *metadata*) to the `native-image` builder. 
Providing the builder with reachability metadata also ensures seamless compatibility with third-party libraries at runtime.

Metadata can be provided to the `native-image` builder in following ways:
- By [computing metadata in code](#computing-metadata-in-code) [when the native binary is built](NativeImageBasics.md#image-build-time-vs-image-run-time) and storing required elements into the [initial heap of the native binary](NativeImageBasics.md#native-image-heap).
- By [providing JSON files](#specifying-metadata-with-json) stored in the `META-INF/native-image/<group.id>/<artifact.id>` project directory. For more information about how to collect metadata for your application automatically, see [Collecting Metadata Automatically](AutomaticMetadataCollection.md).

### Table of Contents

* [Computing Metadata in Code](#computing-metadata-in-code)
* [Specifying Metadata with JSON](#specifying-metadata-with-json)
* [Metadata Types](#metadata-types)
* [Reflection](#reflection)
* [Java Native Interface](#java-native-interface)
* [Resources and Resource Bundles](#resources-and-resource-bundles)
* [Dynamic Proxy](#dynamic-proxy)
* [Serialization](#serialization)
* [Predefined Classes](#predefined-classes)

## Computing Metadata in Code

Computing metadata in code can be achieved in two ways:

1. By providing constant arguments to functions that dynamically access elements of the JVM. A good example of such a function is the `Class.forName` method. In the following code:

    ```java
    class ReflectiveAccess {
        public Class<Foo> fetchFoo() throws ClassNotFoundException {
            return Class.forName("Foo");
        }
    }
    ```
  the `Class.forName("Foo")` will be computed into a constant when native binary is built and stored in its [initial heap](NativeImageBasics.md#native-image-heap).
  If the class `Foo` does not exist, the call will be transformed into `throw ClassNotFoundException("Foo")`.

2. By [initializing classes at build time](ClassInitialization.md) and storing dynamically accessed elements into the initial heap of the native executable. For example:

    ```java
    class InitializedAtBuildTime {
        private static Class<?> aClass;
        static {
            try {
                aClass = Class.forName(readFile("class.txt"));
            } catch (ClassNotFoundException e) {
                throw RuntimeException(e);
            }
        }

        public Class<?> fetchClass() {
            return aClass;
        }
    }
    ```

  When metadata is computed in code, the dynamically accessed elements will be included into the native executable's heap only if that part of the heap is reachable through an enclosing method (for example, `ReflectiveAccess#fetchFoo`) or a static field (for example, `InitializedAtBuildTime.aClass`).

## Specifying Metadata with JSON

Each dynamic Java feature that requires metadata has a corresponding JSON file named `<feature>-config.json`.
The JSON file consists of entries that tell Native Image the elements to include.
For example, Java reflection metadata is specified in `reflect-config.json`, and a sample entry looks like:
```json
{
  "name": "Foo"
}
```

Each entry in `json`-based metadata should be conditional to avoid unnecessary growth in the size of the native binary.
A condition is specified in the following way:
```json
{
  "condition": {
    "typeReachable": "<fully-qualified-class-name>"
  },
  <metadata-entry>
}
```
An entry with a `typeReachable` condition is considered only when the fully-qualified class is reachable.
Currently, we support only `typeReachable` as a condition.

Find more examples of the configuration files in the [GraalVM Reachability Metadata repository](https://github.com/oracle/graalvm-reachability-metadata).

## Metadata Types

Native Image accepts the following types of reachability metadata:
- [Java reflection](#reflection) (the `java.lang.reflect.*` API) enables Java code to examine its own classes, methods, fields, and their properties at run time.   
- [JNI](#java-native-interface) allows native code to access classes, methods, fields and their properties at run time.
- [Resources and Resource Bundles](#resources-and-resource-bundles) allow arbitrary files present in the application to be loaded.
- [Dynamic JDK Proxies](#dynamic-proxy) create classes on demand that implement a given list of interfaces. 
- [Serialization](#serialization) enables writing and reading Java objects to and from streams.
- [Predefined Classes](#predefined-classes) provide support for dynamically generated classes.

## Reflection
### Computing Reflection Metadata in Code

Some reflection methods are treated specially and are evaluated at build time when given constant arguments.
These methods, in each of the listed classes, are:
 - `java.lang.Class`: `getField`, `getMethod`, `getConstructor`, `getDeclaredField`, `getDeclaredMethod`, `getDeclaredConstructor`, `forName`, `getClassLoader`
 - `java.lang.invoke.MethodHandles`: `publicLookup`, `privateLookupIn`, `arrayConstructor`, `arrayLength`, `arrayElementGetter`, `arrayElementSetter`, `arrayElementVarHandle`, `byteArrayViewVarHandle`, `byteBufferViewVarHandle`, `lookup`
 - `java.lang.invoke.MethodHandles.Lookup`: `in `, `findStatic `, `findVirtual `, `findConstructor `, `findClass `, `accessClass `, `findSpecial `, `findGetter `, `findSetter `, `findVarHandle `, `findStaticGetter `, `findStaticSetter `, `findStaticVarHandle `, `unreflect `, `unreflectSpecial `, `unreflectConstructor `, `unreflectGetter `, `unreflectSetter `, `unreflectVarHandle`
 - `java.lang.invoke.MethodType`: `methodType`, `genericMethodType`, `changeParameterType`, `insertParameterTypes`, `appendParameterTypes`, `replaceParameterTypes`, `dropParameterTypes`, `changeReturnType`, `erase`, `generic`, `wrap`, `unwrap`, `parameterType`, `parameterCount`, `returnType`, `lastParameterType`


Below are examples of calls that are replaced with the corresponding metadata element:

```java
Class.forName("java.lang.Integer")
Class.forName("java.lang.Integer", true, ClassLoader.getSystemClassLoader())
Class.forName("java.lang.Integer").getMethod("equals", Object.class)
Integer.class.getDeclaredMethod("bitCount", int.class)
Integer.class.getConstructor(String.class)
Integer.class.getDeclaredConstructor(int.class)
Integer.class.getField("MAX_VALUE")
Integer.class.getDeclaredField("value")
```

When passing constant arrays, the following approaches to declare and populate an array are equivalent from the point of view of the `native-image` builder:

```java
Class<?>[] params0 = new Class<?>[]{String.class, int.class};
Integer.class.getMethod("parseInt", params0);
```

```java
Class<?>[] params1 = new Class<?>[2];
params1[0] = Class.forName("java.lang.String");
params1[1] = int.class;
Integer.class.getMethod("parseInt", params1);
```

```java
Class<?>[] params2 = {String.class, int.class};
Integer.class.getMethod("parseInt", params2);
```

### Specifying Reflection Metadata in JSON

Reflection metadata should be specified in a _reflect-config.json_ file and conform to the JSON schema defined in 
[reflect-config-schema-v1.0.0.json](https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/assets/reflect-config-schema-v1.0.0.json).
The schema also includes further details and explanations how this configuration works. Here is the example of the reflect-config.json:
```json
[
    {
        "condition": {
            "typeReachable": "<condition-class>"
        },
        "name": "<class>",
        "methods": [
            {"name": "<methodName>", "parameterTypes": ["<param-one-type>"]}
        ],
        "queriedMethods": [
            {"name": "<methodName>", "parameterTypes": ["<param-one-type>"]}
        ],
        "fields": [
            {"name": "<fieldName>"}
        ],
        "allDeclaredClasses": true,
        "allDeclaredMethods": true,
        "allDeclaredFields": true, 
        "allDeclaredConstructors": true,
        "allPublicClasses": true,
        "allPublicMethods": true,
        "allPublicFields": true,
        "allPublicConstructors": true,
        "allRecordComponents": true,
        "allNestMembers": true,
        "allSigners": true,
        "allPermittedSubclasses": true,
        "queryAllDeclaredMethods": true,
        "queryAllDeclaredConstructors": true,
        "queryAllPublicMethods": true,
        "queryAllPublicConstructors": true,
        "unsafeAllocated": true
    }
]
```

## Java Native Interface

Java Native Interface (JNI) allows native code to access arbitrary Java types and type members.
Native Image cannot predict what such native code will lookup, write to or invoke.
To build a native binary for a Java application that uses JNI, JNI metadata is most likely required.
For example, the given `C` code:
```C
jclass clazz = FindClass(env, "java/lang/String");
```
looks up the `java.lang.String` class, which can then be used, for example, to invoke different `String` methods.
The generated metadata entry for the above call would look like:
```json
{
  "name": "java.lang.String"
}
```

### JNI Metadata In Code
It is not possible to specify JNI metadata in code.

### JNI Metadata in JSON

JNI metadata should be specified in a _jni-config.json_ file and conform to the JSON schema defined in
[jni-config-schema-v1.0.0.json](https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/assets/jni-config-schema-v1.0.0.json).
The schema also includes further details and explanations how this configuration works. The example of jni-config.json is the same
as the example of reflect-config.json described above.

## Resources and Resource Bundles
Java is capable of accessing any resource on the application class path, or the module path for which the requesting code has permission to access.
Resource metadata instructs the `native-image` builder to include specified resources and resource bundles in the produced binary.
A consequence of this approach is that some parts of the application that use resources for configuration (such as logging) are effectively configured at build time.

### Resource Metadata In Code
Native Image will detect calls to `java.lang.Class#getResource` and `java.lang.Class#getResourceAsStream` in which:
 - The class on which these methods are called is constant
 - The first parameter, `name`, is a constant
and automatically register such resources.

The code below will work out of the box, because:
 - We are using a class literal (`Example.class`)
 - We are using a string literal as the `name` parameter
```java
class Example {
    public void conquerTheWorld() {
        ...
        InputStream plan = Example.class.getResourceAsStream("plans/v2/conquer_the_world.txt");
        ...
    }
}
```

### Resource Metadata in JSON

Resource metadata should be specified in a _resource-config.json_ file and conform to the JSON schema defined in
[resource-config-schema-v1.0.0.json](https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/assets/resource-config-schema-v1.0.0.json).
The schema also includes further details and explanations how this configuration works. Here is the example of the resource-config.json:
```json
{
  "resources": {
    "includes": [
      {
        "condition": {
          "typeReachable": "<condition-class>"
        },
        "pattern": ".*\\.txt"
      }
    ],
    "excludes": [
      {
        "condition": {
          "typeReachable": "<condition-class>"
        },
        "pattern": ".*\\.txt"
      }
    ]
  },
  "bundles": [
    {
      "condition": {
        "typeReachable": "<condition-class>"
      },
      "name": "fully.qualified.bundle.name",
      "locales": ["en", "de", "sk"]
    },
    {
      "condition": {
        "typeReachable": "<condition-class>"
      },
      "name": "fully.qualified.bundle.name",
      "classNames": [
        "fully.qualified.bundle.name_en",
        "fully.qualified.bundle.name_de"
      ]
    }
  ]
}
```

## Dynamic Proxy

The JDK supports generating proxy classes for a given interface list.
Native Image does not support generating new classes at runtime and requires metadata to properly run code that uses these proxies.

> Note: The order of interfaces in the interface list used to create a proxy matters. Creating a proxy with two identical interface lists in which the interfaces are not in the same order, creates two distinct proxy classes.

### Code Example
The following code creates two distinct proxies:

```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

interface IA {
}

interface IB {
}

class Example {
    public void doWork() {
        InvocationHandler handler;
        ...
        Object proxyOne = Proxy.newProxyInstance(Example.class.getClassLoader(), new Class[]{IA.class, IB.class}, handler);
        Object proxyTwo = Proxy.newProxyInstance(Example.class.getClassLoader(), new Class[]{IB.class, IA.class}, handler);
        ...
    }
}
```

### Dynamic Proxy Metadata In Code

The following methods are evaluated at build time when called with constant arguments:
 - `java.lang.reflect.Proxy.getProxyClass`
 - `java.lang.reflect.Proxy.newProxyInstance`

### Dynamic Proxy Metadata in JSON

Dynamic proxy metadata should be specified in a _proxy-config.json_ file and conform to the JSON schema defined in
[proxy-config-schema-v1.0.0.json](https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/assets/proxy-config-schema-v1.0.0.json).
The schema also includes further details and explanations how this configuration works. Here is the example of the proxy-config.json:
```json
[
  {
    "condition": {
      "typeReachable": "<condition-class>"
    },
    "interfaces": [
      "IA",
      "IB"
    ]
  }
]
```

## Serialization
Java can serialize any class that implements the `Serializable` interface.
Native Image supports serialization with proper serializaiton metadata registration. This is necessary as serialization usually
requires reflectively accessing the class of the object that is being serialized.

### Serialization Metadata Registration In Code

Native Image detects calls to `ObjectInputFilter.Config#createFilter(String pattern)` and if the `pattern` argument is constant, the exact classes mentioned in the pattern will be registered for serialization. 
For example, the following pattern will register the class `pkg.SerializableClass` for serialization:
```java
  var filter = ObjectInputFilter.Config.createFilter("pkg.SerializableClass;!*;")
  objectInputStream.setObjectInputFilter(proof);
```
Using this pattern has a positive side effect of improving security on the JVM as only `pkg.SerializableClass` can be received by the 
`objectInputStream`.

Wildcard patterns do the serialization registration only for lambda-proxy classes of an enclosing class. For example, to register lambda serialization in an enclosing class `pkg.LambdaHolder` use:
```java
  ObjectInputFilter.Config.createFilter("pkg.LambdaHolder$$Lambda*;")
```

Patterns like `"pkg.**"` and `"pkg.Prefix*"` will not perform serialization registration as they are too general and would increase image size significantly. 

For calls to the `sun.reflect.ReflectionFactory#newConstructorForSerialization(java.lang.Class)` and `sun.reflect.ReflectionFactory#newConstructorForSerialization(java.lang.Class, )` native image detects calls to these functions when all arguments and the receiver are constant. For example, the following call will register `SerializlableClass` for serialization: 
```java
  ReflectionFactory.getReflectionFactory().newConstructorForSerialization(SerializableClass.class);
```
To create a custom constructor for serialization use:
```java
  var constructor = SuperSuperClass.class.getDeclaredConstructor();
  var newConstructor = ReflectionFactory.getReflectionFactory().newConstructorForSerialization(BaseClass.class, constructor);
```
Proxy classes can only be registered for serialization via the JSON files. 

### Serialization Metadata in JSON

Serialization metadata should be specified in a _serialization-config.json_ file and conform to the JSON schema defined in
[serialization-config-schema-v1.0.0.json](https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/assets/serialization-config-schema-v1.0.0.json).
The schema also includes further details and explanations how this configuration works. Here is the example of the serialization-config.json:
```json
{
  "types": [
    {
      "condition": {
        "typeReachable": "<condition-class>"
      },
      "name": "<fully-qualified-class-name>",
      "customTargetConstructorClass": "<custom-target-constructor-class>"
    }
  ],
  "lambdaCapturingTypes": [
    {
      "condition": {
        "typeReachable": "<condition-class>"
      },
      "name": "<fully-qualified-class-name>"
    }
  ],
 "proxies": [
   {
     "condition": {
       "typeReachable": "<condition-class>"
     },
     "interfaces": ["<fully-qualified-interface-name-1>", "<fully-qualified-interface-name-n>"]
   }
 ]
}
```

## Predefined Classes

Native Image requires all classes to be known at build time (a "closed-world assumption").

However, Java has support for loading new classes at runtime.
To emulate class loading, the [agent](AutomaticMetadataCollection.md) can trace dynamically loaded classes and save their bytecode for later use by the `native-image` builder.
At runtime, if there is an attempt to load a class with the same name and bytecodes as one of the classes encountered during tracing, the predefined class will be supplied to the application.

> Note: Predefined classes metadata is not meant to be manually written.

### Predefined Classes Metadata In Code

It is not possible to specify predefined classes in code.

### Predefined Classes Metadata in JSON

Predefined classes metadata should be specified in a _predefined-classes-config.json_ file and conform to the JSON schema defined in
[predefined-classes-config-schema-v1.0.0.json](https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/assets/predefined-classes-config-schema-v1.0.0.json).
The schema also includes further details and explanations how this configuration works. Here is the example of the predefined-classes-config.json:
```json
[
  {
    "type": "agent-extracted",
    "classes": [
      {
        "hash": "<class-bytecodes-hash>",
        "nameInfo": "<class-name"
      }
    ]
  }
]
```

### Further Reading

* [Metadata Collection with the Tracing Agent](AutomaticMetadataCollection.md)
* [Native Image Compatibility Guide](Compatibility.md)
* [GraalVM Reachability Metadata Repository](https://github.com/oracle/graalvm-reachability-metadata)
