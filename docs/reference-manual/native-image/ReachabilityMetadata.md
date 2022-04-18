---
layout: docs
toc_group: native-image
link_title: Reachability Metadata
permalink: /reference-manual/native-image/ReachabilityMetadata/
redirect_from: /reference-manual/native-image/DynamicProxy/
---
# Reachability Metadata

The dynamic language features of the JVM (e.g., reflection and resource handling) compute the *dynamically-accessed program elements* (e.g., invoked method or resource URLs) at run time. 
In general, [static analysis](ProgrammingModel.md#static-analysis-reachability-and-the-closed-world-assumption) can not determine whether dynamically-accessed program elements are reachable. 
To ensure inclusion of these elements into the image, and hence correctness, the user should provide *reachability metadata* (in further text referred as *metadata*) to the image builder.

Metadata can be provided to the image builder in following ways:
- By [computing metadata in code](#computing-metadata-in-code) at [image-build time](ProgrammingModel.md#image-build-time-vs-image-run-time) and storing required elements into the [initial image heap](ProgrammingModel.md#the-native-image-heap)
- By [providing `json` files](#specifying-metadata-with-json) stored in `META-INF/native-image/<artifact.id>`. To learn how to automatically collect `json` metadata for your application, see [Automatic Collection of Metadata](AutomaticMetadataCollection.md).

## Computing Metadata in Code

Computing metadata in code can be achieved in two ways:
1. By providing constant arguments to functions that dynamically access elements of the JVM. A good example of such function is the `Class.forName`.
   In the following code:
```java
class ReflectiveAccess {
    public Class<Foo> fetchFoo() throws ClassNotFoundException {
        return Class.forName("Foo");
    }
}
```
the `Class.forName("Foo")` will be computed at image-build time into a constant and stored into the [initial image heap](ProgrammingModel.md#the-native-image-heap).
If the class `Foo` does not exist, the call will be transformed into `throw ClassNotFoundException("Foo")`.

2. By [initializing classes at build-time](ClassInitialization.md) and storing dynamically-accessed elements into the [initial image heap](ProgrammingModel.md#the-native-image-heap). For example,
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

When metadata is computed in the code, the dynamically-accessed elements will be included into the image heap only if that part of the heap is reachable through an enclosing method (e.g., `ReflectiveAccess#fetchFoo`) or a static field (e.g., `InitializedAtBuildTime.aClass`).

## Specifying Metadata with JSON

Each dynamic Java feature that requires metadata has a corresponding JSON file named `<feature>-config.json`.
The JSON file consists of entries that tell Native Image what elements need to be included.
For example, reflection metadata is specified in `reflection-config.json`, and a sample entry looks like:
```json
{
  "name": "Foo"
}
```

Each entry in `json`-based metadata should be conditional to avoid unnecessary image-size growth.
A condition is specified in the following way:
```json
{
  "condition": {
    "typeReachable": "<fully-qualified-class-name>"
  },
  <metadata-entry>
}
```
An entry with a `typeReachable` condition is considered only when the `<fully-qualified-class-name>` class is reachable in the image.
Currently, we support only `typeReachable` as a condition.

## Metadata Types

Native Image accepts the following types of reachability metadata:
- [Java reflection](#reflection) (the `java.lang.reflect.*` API) enables Java code to examine its own classes, methods, fields, and their properties at run time.   
- [JNI](#jni) allows native code to access classes, methods, fields and their properties at run time.
- [Resources and Resource Bundles](#resources-and-resource-bundles) allow arbitrary files present in the application to be loaded.
- [Dynamic JDK Proxies](#dynamic-jdk-proxies) create classes on demand that implement a given list of interfaces. 
- [Serialization](#serialization) enables writing and reading Java objects to and from streams.
- [Predefined Classes](#predefined-classes) provide support for dynamically generated classes.

## Reflection
### Computing Reflection Metadata in Code

Some reflection methods are treated specially and are evaluated at build-time when given constant arguments.
These methods, in each of the listed classes, are:
 - `java.lang.Class`: `getField`, `getMethod`, `getConstructor`, `getDeclaredField`, `getDeclaredMethod`, `getDeclaredConstructor`, `forName`, `getClassLoader`
 - `java.lang.invoke.MethodHandles`: `publicLookup`, `privateLookupIn`, `arrayConstructor`, `arrayLength`, `arrayElementGetter`, `arrayElementSetter`, `arrayElementVarHandle`, `byteArrayViewVarHandle`, `byteBufferViewVarHandle`, `lookup`
 - `java.lang.invoke.MethodHandles.Lookup`: `in `, `findStatic `, `findVirtual `, `findConstructor `, `findClass `, `accessClass `, `findSpecial `, `findGetter `, `findSetter `, `findVarHandle `, `findStaticGetter `, `findStaticSetter `, `findStaticVarHandle `, `unreflect `, `unreflectSpecial `, `unreflectConstructor `, `unreflectGetter `, `unreflectSetter `, `unreflectVarHandle`
 - `java.lang.invoke.MethodType`: `methodType`, `genericMethodType`, `changeParameterType`, `insertParameterTypes`, `appendParameterTypes`, `replaceParameterTypes`, `dropParameterTypes`, `changeReturnType`, `erase`, `generic`, `wrap`, `unwrap`, `parameterType`, `parameterCount`, `returnType`, `lastParameterType`


Following are examples of calls that are replaced with the corresponding metadata element:

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

When passing constant arrays, the following ways to declare and populate an array are equivalent from the point of view of the image builder:

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

### Reflection Metadata in JSON

Reflection metadata is specified in the `reflect-config.json` file.
The JSON file is an array of reflection entries:
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
            {"name": "<fieldName>", "allowWrite": true}
        ],
        "allDeclaredMethods": true,
        "allDeclaredFields": true,
        "allDeclaredConstructors": true,
        "allPublicMethods": true,
        "allPublicFields": true,
        "allPublicConstructors": true,
        "queryAllDeclaredMethods": true,
        "queryAllDeclaredConstructors": true,
        "queryAllPublicMethods": true,
        "queryAllPublicConstructors": true,
        "unsafeAllocated": true
    }
]
```

The fields in a reflection entry have the following meaning:
 - `condition`: See [Conditional Metadata Entries](#specifying-metadata-with-json)
 - `name`: Name of the class that will be reflectively looked up. This property is mandatory.
 - `methods`: List class methods that can be looked up and executed reflectively.
 Each method is described by its name and a list of parameter types.
 The parameter types are fully qualified Java class names.
 - `queriedMethods`: List of class methods that can only be looked up.
 The description of each method is identical to the `methods` list.
 - `fields`: List of class fields that can be looked up.
 Each field can optionally contain the `allowWrite` property.
 If `allowWrite` is set, this field can be written to reflectively even if marked as final.
 - `all<access>(Methods/Fields/Constructors)`: Registers all methods/fields/constructors for lookup. Methods and constructors can also be invoked.
 `<access>` refers to different ways of querying these members in Java and can be either `Declared` or `Public`.
 For more information, see `java.lang.Class.getDeclaredMethods()` and `java.lang.Class.getPublicMethods()`.
 - `queryAll<access>(Methods/Constructors)`: Registers all methods/constructors for lookup only.
 - `unsafeAllocated`: Allows objects of this class to be allocated using `Unsafe.allocateInstance`.

## JNI

JNI allows native code to access arbitrary Java types and type members.
Native-image cannot now ahead of time what such native code will lookup, write to or invoke.
To build a native image out of an application that uses JNI, JNI metadata is most likely required.
For example, the given `C` code:
```C
jclass clazz = FindClass(env, "java/lang/String");
```
looks up the `java.lang.String` class, which can then be used to for e.g. invoke different `String` methods.
The generated metadata entry for the above call would look like:
```json
{
  "name": "java.lang.String"
}
```

### Metadata In Code
There is currently no way to specify JNI metadata in code.

### JNI Metadata in JSON
Metadata for JNI is provided in `jni-config.json` files.
The JSON schema of JNI metadata is identical to the [Reflection metadata schema](#reflection-metadata-in-json)

## Resources and Resource Bundles
Java is capable of accessing any resource on the application classpath, or the modulepath for which the requesting code has permission to access.
Resource metadata instructs Native Image to include specified resources and resource bundles in the produced binary.
A consequence of this approach is that some parts of the application that use resources for configuration (e.g. logging) are effectively configured at image build time.

### Code Example
The code below accesses a text file and requires providing resource metadata: 
```java
class Example {
    public void conquerTheWorld() {
        ...
        InputStream plan = Example.class.getResourceAsStream("plans/v2/conquer_the_world.txt");
        ...
    }
}
```

### Metadata In Code
There is currently no way to specify used resources and resource bundles in code.

### Resource Metadata in JSON
Metadata for resources is provided in `resource-config.json` files.
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
    }
  ]
}
```

Native Image will iterate over all resources and match their relative paths against the Java regex specified in `includes`.
If the path matches the regex, the resource is included.
`excludes` instructs Native Image to omit certain included resources that match the given `pattern`.

## Dynamic JDK Proxies
The JDK supports generating proxy classes for a given interface list.
Native Image does not support generating new classes at runtime and requires metadata to properly run code that uses these proxies.

**NOTE**: The order of the interfaces in the interface list used to create a proxy matters.
Creating a proxy with two identical interface lists in which the interfaces are not in the same order creates two distinct proxy classes.

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
### Metadata In Code
The following methods are evaluated at image-build time when called with constant arguments:
 - `java.lang.reflect.Proxy.getProxyClass`
 - `java.lang.reflect.Proxy.newProxyInstance`

### Dynamic Proxy Metadata in JSON
Metadata for dynamic proxies is provided in `proxy-config.json` files.
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
Java can serialize any class that implements the `Serializable` interfaces.
Serialization usually requires reflectively accessing the class of the object being serialized.
The JDK also requires additional information about the class to serialize its object.
Native Image supports serialization with proper metadata.

### Metadata In Code
There is currently no way to register classes used for serialization in code.

### Serialization Metadata in JSON
Metadata for serialization is provided in `serialization-config.json` files.
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
      "name": "<fully-qualified-class-name>",
      "customTargetConstructorClass": "<custom-target-constructor-class>"
    }
  ]
}
```
Each entry in `types` allows serializing and deserializing objects of class given by `name`.
Lambda serialization is also supported, all lambdas declared in the methods of the class given by `name` can be serialized/deserialized.

## Predefined Classes
Native-image needs all classes to be known at image build time (a "closed-world assumption").
However, Java has support for loading new classes at runtime.
To emulate class loading, the [agent](AutomaticMetadataCollection.md) can trace dynamically loaded classes and save their bytecode for later use by the image builder.
At runtime, if there is an attempt to load a class with the same name and bytecodes as one of the classes encountered during tracing, the predefined class will be supplied to the application.

**NOTE:** Predefined Classes metadata is not meant to be manually written.
### Metadata In Code
There is currently no way to specify predefined classes in code.

### Predefined Classes Metadata in JSON
Metadata for predefined classes is provided in `predefined-classes-config.json` files.
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
The JSON schema is accompanied by the `agent-extracted-predefined-classes` directory that contains the bytecodes of the listed classes.
