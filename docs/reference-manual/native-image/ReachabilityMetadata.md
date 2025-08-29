---
layout: docs
toc_group: metadata
link_title: Reachability Metadata
permalink: /reference-manual/native-image/metadata/
redirect_from: 
- /reference-manual/native-image/dynamic-features/Resources/
- /reference-manual/native-image/dynamic-features/Reflection/
- /reference-manual/native-image/dynamic-features/DynamicProxy/
---

# Reachability Metadata

The dynamic language features of the JVM (for example, reflection and resource handling) compute the *dynamically-accessed program elements* such as fields, methods, or resource URLs at run time. 
On HotSpot this is possible because all class files and resources are available at run time and can be loaded by the runtime. 
Availability of all classes and resources, and their loading at run time, comes with an extra overhead in memory and startup time.

To make native binaries small, the `native-image` builder performs [static analysis](NativeImageBasics.md#static-analysis-reachability-and-closed-world-assumption) at build time to determine only the necessary program elements that are needed for the correctness of the application. 
Small binaries allow fast application startup and low memory footprint, however they come at a cost: determining dynamically-accessed application elements via static analysis is infeasible as reachability of those elements depends on data that is available *only* at run time.

To ensure inclusion of necessary dynamically-accessed elements into the native binary, the `native-image` builder requires **reachability metadata** (hereinafter referred to as *metadata*). 
Providing the builder with correct and exhaustive reachability metadata guarantees application correctness and ensures compatibility with third-party libraries at runtime. 

Metadata can be provided to the `native-image` builder in the following ways:
- By [computing metadata in code](#computing-metadata-in-code) [when the native binary is built](NativeImageBasics.md#image-build-time-vs-image-run-time) and storing required elements into the [initial heap of the native binary](NativeImageBasics.md#native-image-heap).
- By [providing the _reachability-metadata.json_ file(s)](#specifying-metadata-with-json) stored in the _META-INF/native-image/&lt;group.Id&gt;/&lt;artifactId&gt;/_ directory on the classpath. For more information about how to collect metadata for your application automatically, see [Collecting Metadata Automatically](AutomaticMetadataCollection.md).
- For more advanced use cases, where classpath scanning or build-time initialization is needed.

> Note: Native Image is migrating to the more user-friendly implementation of reachability metadata that shows problems early on and allows easy debugging.
>
> To enable the new user-friendly reachability-metadata mode for your application, pass the option `--exact-reachability-metadata` at build time. To enable the user-friendly mode only for concrete packages, pass `--exact-reachability-metadata=<comma-separated-list-of-packages>`.
> 
> To get an overview of all places in your code where missing registrations occur, without committing to the exact behavior, you can pass `-XX:MissingRegistrationReportingMode=Warn` when starting the application.
>
> To detect places where the application accidentally ignores a missing registration error (with `catch (Throwable t)` blocks), pass `-XX:MissingRegistrationReportingMode=Exit` when starting the application. The application will then unconditionally print the error message with the stack trace and exit immediately. This behavior is ideal for running application tests to guarantee all metadata is included.
> 
> The user-friendly implementation for reflection will become the default in future releases of GraalVM so the timely adoption is important to avoid project breakage.

### Table of Contents

* [Computing Metadata in Code](#computing-metadata-in-code)
* [Specifying Metadata with JSON](#specifying-metadata-with-json)
* [Metadata Types](#metadata-types)
* [Reflection (Including Dynamic Proxies)](#reflection)
* [Java Native Interface](#java-native-interface)
* [Foreign Function and Memory API](#foreign-function-and-memory-api)
* [Resources](#resources)
* [Resource Bundles](#resource-bundles)
* [Serialization](#serialization)
* [Sample Reachability Metadata](#sample-reachability-metadata)
* [Defining Classes at Run Time](#defining-classes-at-run-time)

## Computing Metadata in Code

Computing metadata in code can be achieved in two ways:

1. By providing *constant* arguments to functions that dynamically access elements of the JVM. See, for example, `Class#forName` in the following code:

    ```java
    class ReflectiveAccess {
        public Class<Foo> fetchFoo() throws ClassNotFoundException {
            return Class.forName("Foo");
        }
    }
    ```
    Here, `Class.forName("Foo")` is evaluated into a constant at build time. When the native binary is built, this value is stored in its [initial heap](NativeImageBasics.md#native-image-heap).
    If the class `Foo` does not exist, the call to `Class#forName` will be transformed into `throw ClassNotFoundException("Foo")`.
  
    The *constant* is defined as:
      * A literal (for example, `"Foo"` or `1`).
      * Access to a static field that is [initialized at build time](ClassInitialization.md).
      * Access to an effectively final variable.
      * Defining an array that whose lenght is constant, and all values are constant.
      * Simple computations on other constants (for example, `"F"` + `"oo"`, or an indexing into an array).

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
 
     Note that Native Image currently aggressively computes constants, and therefore it is not possible to specify exactly what is a constant at build time.

2. By [initializing classes at build time](ClassInitialization.md) and storing dynamically accessed elements into the initial heap of the native executable. 
   This way of providing metadata is suited for cases when specifying metadata with constants or in JSON is not possible. 
   This is necessary in cases when:
   * The user code needs to generate new class bytecode.
   * The user code needs to traverse the classpath to compute the dynamically accessed program elements necessary for the application.
   
   In the following example:

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

        public Class<?> fetchFoo() {
            return aClass;
        }
    }
    ```

  The dynamically accessed elements will be included into the native executable's heap only if that part of the heap is reachable through an enclosing method (for example, `InitializedAtBuildTime#fetchFoo`) or a static field (for example, `InitializedAtBuildTime.aClass`).

## Specifying Metadata with JSON

All metadata specified in the _reachability-metadata.json_ file that is located in any of the classpath entries at _META-INF/native-image/\<group.Id>\/\<artifactId>\/_.
The JSON schema for the reachability metadata is defined in [reachability-metadata-schema-v1.1.0.json](https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/assets/reachability-metadata-schema-v1.1.0.json).

A sample _reachability-metadata.json_ file can be found [in the sample section](#sample-reachability-metadata).
The _reachability-metadata.json_ configuration contains a single object with one field for each type of metadata. Each field in the top-level object contains an array of *metadata entries*:
```json
{
  "reflection":[],
  "resources":[]
}
```

For example, Java reflection metadata is specified under `reflection`, and an example entry looks like:
```json
{
  "reflection": [
    {
      "type": "Foo"
    }
  ]
}
```
 
### Conditional Metadata Entries

Each entry in JSON-based metadata should be *conditional* to avoid unnecessary growth of the native binary size.
A conditional entry is specified by adding a `condition` field to the entry in the following way:
```json
{
  "condition": {
    "typeReached": "<fully-qualified-class-name>"
  },
  <metadata-entry>
}
```

A metadata entry with a `typeReached` condition is considered available at run time, only when the specified fully-qualified type is _reached_ at run time. 
Before that, all dynamic accesses to the element represented with the `metadata-entry` will behave as if the `metadata-entry` does not exist.
This means that those dynamic accesses will throw a missing-registration error.

A type is reached at run time, right before the [class-initialization routine](https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html#jvms-5.5) starts for that type (class or interface), or any of the type's subtypes are reached.
For `"typeReached": "ConditionType"` that guards a metadata entry in the following example, the type is considered reached:
```java
class SuperType {
    static {
        // ConditionType reached (subtype reached) => metadata entry available
    }
}
class ConditionType extends SuperType {
    static {
        // ConditionType reached (before static initializer) => metadata entry available
    }
    static ConditionType singleton() {
        // ConditionType reached (already initialized) => metadata entry available
    }
}
public class App {
    public static void main(String[] args) {
        // ConditionType not reached => metadata entry not available
        ConditionType.class;
        // ConditionType not reached (ConditionType.class doesn't start class initialization) => metadata entry not available  
        ConditionType.singleton();
        // ConditionType reached (already initialized) => metadata entry available
    }
}
```

A type is also reached, if it is marked as `initialize-at-build-time` or any of its subtypes are marked as `initialize-at-build-time` and they exist on the classpath. 

Array types are never marked as reached and as such cannot be used in conditions.

A conditional metadata entry is included into the image when the fully-qualified type is reachable at build time. 
This entry affects the image size, and it will be available at run time only when the condition is reached at run time.

You can find more examples of the metadata files in the [GraalVM Reachability Metadata repository](https://github.com/oracle/graalvm-reachability-metadata).

## Metadata Types

Native Image accepts the following types of reachability metadata:
- [Java reflection](#reflection) (the `java.lang.reflect.*` API) enables Java code to examine its own classes, methods, fields, and their properties at run time.   
- [JNI](#java-native-interface) allows native code to access classes, methods, fields and their properties at run time.
- [Resources](#resources) allow arbitrary files present on the classpath to be dynamically accessed in the application.
- [Resource Bundles](#resource-bundles) Java localization support (`java.util.ResourceBundle`) that enables Java code to load L10N resources.  
- [Serialization](#serialization) enables writing (and reading) Java objects to (and from) streams.
- (Experimental) [Predefined Classes](#predefined-classes) provide support for dynamically generated classes.

## Reflection
 
For all methods in this section Native Image will compute reachability at build time given that all the call arguments are constant. 
Providing constant arguments in code is a preferred way to provide metadata as it requires no duplication of information in external JSON files.  

Reflection in Java starts with `java.lang.Class` that allows fetching further reflective elements such as methods and fields. 
The class can be fetched reflectively via the following static functions on `java.lang.Class`:
* `java.lang.Class forName(java.lang.String) throws java.lang.ClassNotFoundException`
* `java.lang.Class forName(java.lang.String, boolean, java.lang.ClassLoader) throws java.lang.ClassNotFoundException`
* `java.lang.Class forName(java.lang.Module, java.lang.String)`
* `java.lang.Class arrayType()` - requires metadata for the array type.
The classes can be also fetched reflectively loading a class from a name with `java.lang.ClassLoader#loadClass(String)`.

To provide metadata for the calls that fetch a `Class` reflectively, the following entry must be added to the `reflection` array in _reachability-metadata.json_:
```json
{
  "type": "FullyQualifiedReflectivelyAccessedType"
}
```

For proxy classes, the `java.lang.Class` is fetched with the following methods on `java.lang.reflect.Proxy`:
* `java.lang.Class getProxyClass(java.lang.ClassLoader, java.lang.Class[]) throws java.lang.IllegalArgumentException`
* `java.lang.Object newProxyInstance(java.lang.ClassLoader, java.lang.Class[], java.lang.reflect.InvocationHandler)`

Metadata, for proxy classes, is in the form an ordered collection of interfaces that defines a proxy:
```json
{
  "type": {
    "proxy": ["FullyQualifiedInterface1", "...", "FullyQualifiedInterfaceN"]
  }
}
```

To provide metadata for a lambda class, the following metadata must be added to the `reflection` array in
_reachability-metadata.json_

```json
{
  "type": {
    "lambda": {
      "declaringClass": "FullyQualifiedLambdaDeclaringType",
      "declaringMethod": {
        "name": "declaringMethodName",
        "parameterType": [
          "FullyQualifiedParameterType1",
          "...",
          "FullyQualifiedParameterType2"
        ]
      },
      "interfaces": [
        "FullyQualifiedLambdaInterface1",
        "...",
        "FullyQualifiedLamdbaInterface2"
      ]
    }
  }
}
```

The `"declaringClass"` field specifies in which class, and the optional `"declaringMethod"` field specifies in which
method the lambda is defined.
If `"declaringMethod"` is not specified, the lambda class is searched through all methods of the specified declaring
class.
The `"interfaces"` field specifies which interfaces are implemented by the lambda class.
Such a definition can match multiple lambda classes. If that is the case, the registration entry applies to all those
classes.

Invocation of methods above without the provided metadata will result in throwing `MissingReflectionRegistrationError` which extends `java.lang.Error` and
should not be handled. Note that even if a type does not exist on the classpath, the methods above will throw a `MissingReflectionRegistrationError`.

The following methods on `java.lang.Class` will throw a `MissingRegistrationError` if the metadata is not provided for a given type:
* `Constructor getConstructor(Class[]) throws NoSuchMethodException,SecurityException`
* `Constructor getDeclaredConstructor(Class[]) throws NoSuchMethodException,SecurityException`
* `Constructor[] getConstructors() throws SecurityException`
* `Constructor[] getDeclaredConstructors() throws SecurityException`
* `Method getMethod(String,Class[]) throws NoSuchMethodException,SecurityException`
* `Method getDeclaredMethod(String,Class[]) throws NoSuchMethodException,SecurityException`
* `Method[] getMethods() throws SecurityException`
* `Method[] getDeclaredMethods() throws SecurityException`
* `Field getField(String) throws NoSuchFieldException,SecurityException`
* `Field getDeclaredField(String) throws NoSuchFieldException,SecurityException`
* `Field[] getFields() throws SecurityException`
* `Field[] getDeclaredFields() throws SecurityException`
* `RecordComponent[] getRecordComponents()`  
* `Class[] getPermittedSubclasses()`
* `Object[] getSigners()`
* `Class[] getNestMembers()`
* `Class[] getClasses()`
* `Class[] getDeclaredClasses() throws SecurityException`
  
Furthermore, all reflective lookups via `java.lang.invoke.MethodHandles.Lookup` will also require metadata for the type to be present, or they will throw `MissingReflectionRegistrationError`.

Note that for lambda-proxy classes, metadata can not be provided.
This is a [known issue](https://github.com/oracle/graal/issues/7476) that will be addressed in the future releases of GraalVM.

### Reflective Method Invocation

To reflectively invoke methods, the method signature must be added to the `type` metadata:
```json
{
  "type": "TypeWhoseMethodsAreInvoked",
  "methods": [
    {"name": "<methodName1>", "parameterTypes": ["<param-type1>", "<param-typeI>", "<param-typeN>"]},
    {"name": "<methodName2>", "parameterTypes": ["<param-type1>", "<param-typeI>", "<param-typeN>"]}
  ]
}
```

As a convenience, one can allow method invocation for groups of methods by adding the following in _reachability-metadata.json_:
```json
{
  "type": "TypeWhoseMethodsAreInvoked",
  "allDeclaredConstructors": true,
  "allPublicConstructors": true,
  "allDeclaredMethods": true,
  "allPublicMethods": true
}
```
`allDeclaredConstructors` and `allDeclaredMethods` allow calls invocations of methods declared on a given type. 
`allPublicConstructors` and `allPublicMethods` allow invocations of all public methods defined on a type and all of its supertypes. 

In case the method-invocation metadata is missing, the following methods will throw a `MissingReflectionRegistrationError`:
* `java.lang.reflect.Method#invoke(Object, Object...)`
* `java.lang.reflect.Constructor#newInstance(Object...)`
* `java.lang.invoke.MethodHandle#invokeExact(Object...)`
* `java.lang.invoke.MethodHandle#invokeWithArguments` (all overloaded versions)

### Reflective Field-Value Access

To reflectively access (get or set) field values, metadata about field names must be added to the type:
```json
{
  "type": "TypeWhoseFieldValuesAreAccessed",
  "fields": [{"name": "<fieldName1>"}, {"name": "<fieldNameI>"}, {"name": "<fieldNameN>"}]
}
```

As a convenience one can allow field-value access for all fields by adding the following in _reachability-metadata.json_: 
```json
{
  "type": "TypeWhoseFieldValuesAreAccessed",
  "allDeclaredFields": true,
  "allPublicFields": true
}
```
`allDeclaredFields` allow access to all fields declared on a given type, and `allPublicFields` allows access to all public fields of the given type and all of its supertypes. 

In case the field-value-access metadata is missing, the following methods will throw a `MissingReflectionRegistrationError`:
* `java.lang.reflect.Field#get(Object)`
* `java.lang.reflect.Field#set(Object, Object)`
* All accessor methods on `java.lang.reflect.VarHandle`.

### Unsafe Allocation of a Type

For unsafe allocation of a type via `sun.misc.Unsafe#allocateInstance(Class<?>)`, or from native code via `AllocObject(jClass)`, we must provide the following metadata:
```json
{
  "type": "FullyQualifiedUnsafeAllocatedType",
  "unsafeAllocated": true
}
```
Otherwise, these methods will throw a `MissingReflectionRegistrationError`.

### Reflection Metadata Summary

The overall definition of a type in JSON can have the following values:
```json
{
  "condition": {
    "typeReached": "<condition-class>"
  },
  "type": "<class>|<proxy-interface-list>",
  "fields": [
    {"name": "<fieldName>"}
  ],
  "methods": [
    {"name": "<methodName>", "parameterTypes": ["<param-type>"]}
  ],
  "allDeclaredConstructors": true,
  "allPublicConstructors": true,
  "allDeclaredMethods": true,
  "allPublicMethods": true,
  "allDeclaredFields": true,
  "allPublicFields": true,
  "unsafeAllocated": true
}
```

## Java Native Interface

Java Native Interface (JNI) allows native code to access arbitrary Java types and type members.
Native Image cannot predict what such native code will lookup, write to or invoke.
To build a native binary for a Java application that uses JNI to access Java values, JNI metadata is required.

For example, the following `C` code:
```C
jclass clazz = FindClass(env, "jni/accessed/Type");
```
looks up the `jni.accessed.Type` class, which can then be used to instantiate `jni.accessed.Type`, invoke its methods or access its fields.

The metadata entry for the above call can *only* be provided via _reachability-metadata.json_. Specify
the `jniAccessible` field in the `type` entry in the `reflection` section:
```json
{
  "reflection": [
    {
      "type": "jni.accessed.Type",
      "jniAccessibleType": true
    }
  ]
}
```

Adding the metadata for a type does not allow to fetch all of its fields and methods with `GetFieldID`, `GetStaticFieldID`, `GetStaticMethodID`, and `GetMethodID`.

To access field values, we need to provide field names:
```json
{
  "type": "jni.accessed.Type",
  "jniAccessible": true,
  "fields": [{"name": "value"}]
}
```
To access all fields one can use the following attributes:
```json
{
  "type": "jni.accessed.Type",
  "jniAccessible": true,
  "allDeclaredFields": true,
  "allPublicFields": true
}
```
`allDeclaredFields` allow access to all fields declared on a given type, and `allPublicFields` allows access to all public fields of the given type and all of its supertypes.

To call Java methods from JNI, we must provide metadata for the method signatures:
```json
{
  "type": "jni.accessed.Type",
  "jniAccessible": true,
  "methods": [
    {"name": "<methodName1>", "parameterTypes": ["<param-type1>", "<param-typeI>", "<param-typeN>"]},
    {"name": "<methodName2>", "parameterTypes": ["<param-type1>", "<param-typeI>", "<param-typeN>"]}
  ]
}
```
As a convenience, one can allow method invocation for groups of methods by adding the following:
```json
{
  "type": "jni.accessed.Type",
  "jniAccessible": true,
  "allDeclaredConstructors": true,
  "allPublicConstructors": true,
  "allDeclaredMethods": true,
  "allPublicMethods": true
}
```
`allDeclaredConstructors` and `allDeclaredMethods` allow calls invocations of methods declared on a given type.
`allPublicConstructors` and `allPublicMethods` allow invocations of all public methods defined on a type and all of its supertypes.

To allocate objects of a type with `AllocObject`, the `unsafeAllocated` field must be set, but the `jniAccessible` field
is not required:
```json
{
  "reflection": [
    {
      "type": "jni.accessed.Type",
      "unsafeAllocated": true
    }
  ]
}
```

Failing to provide metadata for an element that is dynamically accessed from native code will result in an exception (`MissingJNIRegistrationError`).

> Note that most libraries that use JNI do not handle exceptions properly, so to see which elements are missing `--exact-reachability-metadata` in combination with `-XX:MissingRegistrationReportingMode=Warn` must be used.   

## Foreign Function and Memory API

The [Foreign Function and Memory (FFM) API](FFM-API.md) is an interface that enables Java code to interact with native code and vice versa.

In particular, it allows you to create _downcall handles_ and _upcall stubs_.
* A downcall handle is a method handle that refers to a native function. Invoking it results in a call to the native function.
* An upcall stub is executable code generated at run time that can be passed as a function pointer to native code. Calling this function pointer results in the execution of a Java method handle.

To perform downcalls or upcalls at run time, supporting code must be generated at image build time.
Therefore, the `native-image` builder must be provided with descriptors that characterize the functions with which downcalls or upcalls can be performed at run time.

If the necessary metadata is not provided, a `MissingForeignRegistrationError` will be thrown at run time.

## Resources

Java is capable of accessing any resource on the application class path, or the module path for which the requesting code has permission to access.
Resource metadata instructs the `native-image` builder to include specified resources and resource bundles in the produced binary.
A consequence of this approach is that some parts of the application that use resources for configuration (such as logging) are effectively configured at build time.

Native Image will detect calls to `java.lang.Class#getResource` and `java.lang.Class#getResourceAsStream` in which:
 - The class on which these methods are called is constant
 - The first argument (`name`) is a constant
and automatically register such resources.

The code below will work out of the box, because:
 - It uses a class literal (`Example.class`) as the receiver
 - It uses a string literal as the `name` parameter
```java
class Example {
    public void conquerTheWorld() {
        InputStream plan = Example.class.getResourceAsStream("plans/v2/conquer_the_world.txt");
    }
}
```

### Resource Metadata in JSON

Resource metadata is specified in the `resources` field of the _reachability-metadata.json_ file. 
Here is the example of resource metadata:
```json
{
  "resources": [
    {
      "glob": "path1/level*/**"
    }
  ]
}
```

The `glob` field uses a subset of [glob-pattern](https://en.wikipedia.org/wiki/Glob_(programming)) rules for specifying resources. 
There are several rules to be observed when specifying a resource path:
* The `native-image` tool supports only _star_ (`*`) and _globstar_ (`**`) wildcard patterns.
    * Per definition, _star_ can match any number of any characters on one level while _globstar_ can match any number of characters at any level.
    * If there is a need to treat a star literally (without special meaning), it can be escaped using `\` (for example, `\*`).
* In the glob, a _level_ represents a part of the pattern separated with `/`.
* When writing glob patterns the following rules must be observed:
    * Glob cannot be empty (for example, `""` )
    * Glob cannot end with a trailing slash (`/`) (for example, `"foo/bar/"`)
    * Glob cannot contain more than two consecutive (non-escaped) `*` characters on one level (for example, `"foo/***/"` )
    * Glob cannot contain empty levels (for example, `"foo//bar"`)
    * Glob cannot contain two consecutive globstar wildcards (example, `"foo/**/**"`)
    * Glob cannot have other content on the same level as globstar wildcard (for example, `"foo/**bar/x"`)

Given the following project structure:
```
app-root
└── src
    └── main
        └── resources
            ├── Resource0.txt
            └── Resource1.txt
```
You can:
* Include all resources with glob `**/Resource*.txt` (`{ "glob":}`)
* Include _Resource0.txt_ with glob `**/Resource0.txt`
* Include _Resource0.txt_ and _Resource1.txt_ with globs `**/Resource0.txt` and `**/Resource1.txt`

### Resources in Java Modules

For every resource or resource bundle, it is possible to specify the module from which the resource or resource bundle should be taken.
You can specify a module name in the separate `module` field in each entry.
For example:
```json
{
   "resources": [
      {
        "module:": "library.module",
        "glob": "resource-file.txt" 
      }
   ]
}
```

This will cause the `native-image` tool to only include _resource-file.txt_ from the Java module `library.module`.
If other modules or the classpath containing resources that match the pattern _resource-file.txt_, only the one in _library-module_ is registered to be included into a native executable.
Native Image will also ensure that the modules are guaranteed to be accessible at runtime.

Take the following code pattern:
```java
InputStream resource = ModuleLayer.boot().findModule("library.module").getResourceAsStream(resourcePath);
```
It will always work as expected for resources registered as described above (even if the module does not contain any code that is considered reachable by static analysis).


### Embedded Resources Information

There are two ways to see which resources were included in a native executable:
1. Use the option `--emit build-report` to generate a build report for your native executable.
   There you can find information about all included resources under the `Resources` tab.
2. Use the option `-H:+GenerateEmbeddedResourcesFile` to generate a JSON file  _embedded-resources.json_, listing all included resources.

For each registered resource you get:
* **Module** (or `unnamed` if a resource does not belong to any module)
* **Name** (resource path)
* **Origin** (location of the resource on the system)
* **Type** (whether the resource is a file, directory, or missing)
* **Size** (actual resource size)

> Note: The size of a resource directory represents only the size of the names of all directory entries (not a sum of the content sizes).

## Resource Bundles
Java localization support (`java.util.ResourceBundle`) enables to load L10N resources and show messages localized for a specific _locale_.
Native Image needs knowledge of the resource bundles that your application uses so that it can include appropriate resources and program elements to the application.

A simple bundle can be specified in the `resources` section of _reachability-metadata.json_:

```json
{
  "resources": [
    {
      "bundle": "your.pkg.Bundle"
    }
  ]
}
```

To request a bundle from a specific module:
```json
{
  "resources": [
    {
      "module": "app.module"
      "bundle": "your.pkg.Bundle"
    }
  ]
}
```

Resource bundles are included for all locales that are [included into the image](#locales).

### Locales

It is also possible to specify which locales should be included in a native executable and which should be the default.
For example, to switch the default locale to Swiss German and also include French and English, use the following options:
```shell
native-image -Duser.country=CH -Duser.language=de -H:IncludeLocales=fr,en
```
The locales are specified using [language tags](https://docs.oracle.com/javase/tutorial/i18n/locale/matching.html).
You can include all locales via `-H:+IncludeAllLocales`, but note that it increases the size of the resulting executable.

## Serialization

Java can serialize (or deserialize) any class that implements the `Serializable` interface.
Native Image supports serialization (or deserialization) with proper serialization metadata registration.
This is necessary because serialization usually requires reflective accesses to the object that is being serialized.

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
Serialization metadata is specified in the `reflection` section of _reachability-metadata.json_.
 
To specify a regular `serialized.Type` use 
```json
{
  "reflection": [
    {
      "type": "serialized.Type",
      "serializable": true
    }
  ]
}
```

To specify a proxy class for serialization, use the following entry:
```json 
{
  "reflection": [
    {
      "type": {
        "proxy": ["FullyQualifiedInterface1", "...", "FullyQualifiedInterfaceN"],
        "serializable": true
      }
    }
  ]
}
```

In rare cases an application might explicitly make calls to:
```java
    ReflectionFactory.newConstructorForSerialization(Class<?> cl, Constructor<?> constructorToCall);
```
The specified `constructorToCall` differs from the one that would be automatically used during regular serialization of `cl`.
When a class is registered for run-time serialization, all potential custom constructors are automatically registered.
As a result, this use case does not require any additional metadata.

## Sample Reachability Metadata

See below is a sample reachability metadata configuration that you can use in _reachabilty-metadata.json_:

```json
{
  "reflection": [
    {
      "type": "reflectively.accessed.Type",
      "fields": [
        {
          "name": "field1"
        }
      ],
      "methods": [
        {
          "name": "method1",
          "parameterTypes": ["<param-type1>", "<param-typeI>", "<param-typeN>"] 
        }
      ],
      "allDeclaredConstructors": true,
      "allPublicConstructors": true,
      "allDeclaredFields": true,
      "allPublicFields": true,
      "allDeclaredMethods": true,
      "allPublicMethods": true,
      "unsafeAllocated": true,
      "serializable": true
    }
  ],
  "jni": [
    {
      "type": "jni.accessed.Type",
      "fields": [
        {
          "name": "field1"
        }
      ],
      "methods": [
        {
          "name": "method1",
          "parameterTypes": ["<param-type1>", "<param-typeI>", "<param-typeN>"]
        }
      ],
      "allDeclaredConstructors": true,
      "allPublicConstructors": true,
      "allDeclaredFields": true,
      "allPublicFields": true,
      "allDeclaredMethods": true,
      "allPublicMethods": true
    }
  ],
  "resources": [
    {
      "module": "optional.module.of.a.resource",
      "glob": "path1/level*/**"
    },
    {
      "bundle": "fully.qualified.bundle.name"
    }
  ],
  "foreign": {
    "downcalls": [
      {
        "returnType": "<return-type>",
        "parameterTypes": ["<param-type1>", "<param-typeI>", "<param-typeN>"]
      }
    ],
    "upcalls": [
      {
        "returnType": "<return-type>",
        "parameterTypes": ["<param-type1>", "<param-typeI>", "<param-typeN>"]
      }
    ],
    "directUpcalls": [
      {
        "class": "org.example.SomeClass",
        "method": "method1",
        "returnType": "<return-type>",
        "parameterTypes": ["<param-type1>", "<param-typeI>", "<param-typeN>"]
      }
    ]
  }
}
```

## Defining Classes at Run Time

Java has support for loading new classes from bytecode at run time, which is not possible in Native Image as all classes must be known at build time (the "closed-world assumption").
To overcome this issue there are the following options:
1. Modify or reconfigure your application (or a third-party library) so that it does not generate classes at runtime or load them via non-built-in class loaders.
2. If the classes must be generated, try to generate them at build time in a static initializer of a dedicated class.
The generated java.lang.Class objects should be stored in static fields and the dedicated class initialized by passing `--initialize-at-build-time=<class_name>` as the build argument.
3. If none of the above is applicable, use the [Native Image Agent](AutomaticMetadataCollection.md) to run the application and collect predefined classes with
`java -agentlib:native-image-agent=config-output-dir=<config-dir>,experimental-class-define-support <application-arguments>`.
At runtime, if there is an attempt to load a class with the same name and bytecode as one of the classes encountered during tracing, the predefined class will be supplied to the application.

Predefined classes metadata is specified in a _predefined-classes-config.json_ file and conform to the JSON schema defined in
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
> Note: Predefined classes metadata is not meant to be manually written.
> Note: Predefined classes are the best-effort approach for legacy projects, and they are not guaranteed to work.

## Further Reading

* [Metadata Collection with the Tracing Agent](AutomaticMetadataCollection.md)
* [Native Image Compatibility Guide](Compatibility.md)
* [GraalVM Reachability Metadata Repository](https://github.com/oracle/graalvm-reachability-metadata)
