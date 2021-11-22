---
layout: docs
toc_group: native-image
link_title: Reflection on Native Image
permalink: /reference-manual/native-image/Reflection/
---

* [Automatic Detection](#automatic-detection)
* [Manual Configuration](#manual-configuration)
* [Conditional Configuration](#conditional-configuration)
* [Configuration with Features](#configuration-with-features)

# Reflection Use in Native Images

Java reflection support (the `java.lang.reflect.*` API) enables Java code to examine its own classes, methods, fields and their properties at run time.

Native Image has partial support for reflection and needs to know ahead-of-time the reflectively accessed program elements.
Examining and accessing program elements through `java.lang.reflect.*` or loading classes with `Class.forName(String)` at run time requires preparing additional metadata for those program elements.
(Note: loading classes with `Class.forName(String)` are included here since it is closely related to reflection.)

Native Image tries to resolve the target elements through a static analysis that detects calls to the Reflection API.
Where the analysis fails, the program elements reflectively accessed at run time must be specified using a manual configuration.

See also the [guide on assisted configuration of Java resources and other dynamic features](BuildConfiguration.md#assisted-configuration-of-native-image-builds).

## Automatic Detection

The analysis intercepts calls to `Class.forName(String)`, `Class.forName(String, ClassLoader)`, `Class.getDeclaredField(String)`, `Class.getField(String)`, `Class.getDeclaredMethod(String, Class[])`, `Class.getMethod(String, Class[])`, `Class.getDeclaredConstructor(Class[])`, and `Class.getConstructor(Class[])`.

If the arguments to these calls can be reduced to a constant, Native Image tries to resolve the target elements.
If the target elements can be resolved, the calls are removed and instead the target elements are embedded in the code.
If the target elements cannot be resolved, e.g., a class is not on the classpath or it does not declare a field/method/constructor, then the calls are replaced with a snippet that throws the appropriate exception at run time.
The benefits are twofold.
First, at run time there are no calls to the Reflection API.
Second, GraalVM can employ constant folding and optimize the code further.

The calls are intercepted and processed only when it can be unequivocally determined that the parameters can be reduced to a constant.
For example, the call `Class.forName(String)` will be replaced with a `Class` literal only if the `String` argument can be constant folded, assuming that the class is actually on the classpath.
Additionally, a call to `Class.getMethod(String, Class[])` will be processed only if the contents of the `Class[]` argument can be determined with certainty.
This last restriction is due to the fact that Java does not have immutable arrays.
Therefore, all the changes to the array between the time it is allocated and the time it is passed as an argument need to be tracked.
The analysis follows a simple rule: if all the writes to the array happen in linear sections of code, i.e., no control flow splits, then the array is effectively constant for the purpose of analyzing the call.

That is why the analysis does not accept `Class[]` arguments coming from static fields, since the contents of those can change at any time, even if the fields are final.
Although this may seem too restrictive, it covers the most commonly used patterns of the Reflection API calls.

The only exception to the constant arguments rule is that the `ClassLoader` argument of `Class.forName(String, ClassLoader)` does not need to be a constant; it is ignored and instead a class loader that can load all the classes on the class path is used.
The analysis runs to a fix point which means that a chain of calls like `Class.forName(String).getMethod(String, Class[])` will first replace the class constant and then the method will effectively reduce this to `java.lang.reflect.Method`.

Following are examples of calls that can be intercepted and replaced with the corresponding element:

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

The following ways to declare and populate an array are equivalent from the point of view of the analysis:

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

If a call cannot be processed it is simply skipped.
For these situations a manual configuration as described below can be provided.

## Manual Configuration

A configuration that specifies the program elements that will be accessed reflectively can be provided during the native image build as follows:
```
-H:ReflectionConfigurationFiles=/path/to/reflectconfig
```
Here, `reflectconfig` is a JSON file in the following format (use `--expert-options` for more details):
```json
[
  {
    "name" : "java.lang.Class",
    "queryAllDeclaredConstructors" : true,
    "queryAllPublicConstructors" : true,
    "queryAllDeclaredMethods" : true,
    "queryAllPublicMethods" : true,
    "allDeclaredClasses" : true,
    "allPublicClasses" : true
  },
  {
    "name" : "java.lang.String",
    "fields" : [
      { "name" : "value" },
      { "name" : "hash" }
    ],
    "methods" : [
      { "name" : "<init>", "parameterTypes" : [] },
      { "name" : "<init>", "parameterTypes" : ["char[]"] },
      { "name" : "charAt" },
      { "name" : "format", "parameterTypes" : ["java.lang.String", "java.lang.Object[]"] }
    ]
  },
  {
    "name" : "java.lang.String$CaseInsensitiveComparator",
    "queriedMethods" : [
      { "name" : "compare" }
    ]
  }
]
```

The configuration distinguishes between methods and constructors that can be invoked during execution via `Method.invoke(Object, Object...)` or `Constructor.newInstance(Object...)` and those that can not.
Including a function in the configuration without invocation capabilities helps the static analysis correctly assess its reachability status and results in smaller image sizes.
The function metadata is then accessible at runtime like it would for any other registered reflection method or constructor, but trying to call the function will result in a runtime error.
The configuration fields prefixed by `query` or `queried` only include the metadata, while the other ones (e.g., `methods`) enable runtime invocation.

The native image builder generates reflection metadata for all classes, methods, and fields referenced in that file.
The `queryAllPublicConstructors`, `queryAllDeclaredConstructors`, `queryAllPublicMethods`, `queryAllDeclaredMethods`, `allPublicConstructors`, `allDeclaredConstructors`, `allPublicMethods`, `allDeclaredMethods`, `allPublicFields`, `allDeclaredFields`, `allPublicClasses`, and `allDeclaredClasses` attributes can be used to automatically include an entire set of members of a class.

However, `allPublicClasses` and `allDeclaredClasses` do not automatically register the inner classes for reflective access.
They just make them available via `Class.getClasses()` and `Class.getDeclaredClasses()` when called on the declaring class.
Code may also write non-static final fields like `String.value` in this example, but other code might not observe changes of final field values in the same way as for non-final fields because of optimizations. Static final fields may never be written.

More than one configuration can be used by specifying multiple paths for `ReflectionConfigurationFiles` and separating them with `,`.
Also, `-H:ReflectionConfigurationResources` can be specified to load one or several configuration files from the native image build's class path, such as from a JAR file.

### Conditional Configuration

With conditional configuration, a class configuration entry is applied only if a provided `condition` is satisfied.
The only currently supported condition is `typeReachable`, which enables the configuration entry if the specified type is reachable through other code.
For example, to support reflective access to `sun.misc.Unsafe.theUnsafe` only when `io.netty.util.internal.PlatformDependent0` is reachable, the configuration should look like:

```json
{
  "condition" : { "typeReachable" : "io.netty.util.internal.PlatformDependent0" },
  "name" : "sun.misc.Unsafe",
  "fields" : [
    { "name" : "theUnsafe" }
  ]
}
```

Conditional configuration is the **preferred** way to specify reflection configuration: if code doing a reflective access is not reachable, it is unnecessary to include its corresponding reflection entry.
The consistent usage of `condition` results in *smaller binaries* and *better build times* as the image builder can selectively include reflectively accessed code.

If a `condition` is omitted, the element is always included.
When the same `condition` is used for two distinct elements in two configuration entries, both elements will be included when the condition is satisfied.
When a configuration entry should be enabled if one of several types are reachable, it is necessary to add two configuration entries: one entry for each condition.

When used with [assisted configuration](BuildConfiguration.md#assisted-configuration-of-native-image-builds), conditional entries of existing configuration will not be fused with agent-collected entries.

### Configuration with Features

Alternatively, a custom `Feature` implementation can register program elements before and during the analysis phase of the native image build using the `RuntimeReflection` class. For example:
```java
class RuntimeReflectionRegistrationFeature implements Feature {
  public void beforeAnalysis(BeforeAnalysisAccess access) {
    try {
      RuntimeReflection.register(String.class);
      RuntimeReflection.register(String.class.getDeclaredField("value"));
      RuntimeReflection.register(String.class.getDeclaredField("hash"));
      RuntimeReflection.register(String.class.getDeclaredConstructor(char[].class));
      RuntimeReflection.register(String.class.getDeclaredMethod("charAt", int.class));
      RuntimeReflection.register(String.class.getDeclaredMethod("format", String.class, Object[].class));
      RuntimeReflection.register(String.CaseInsensitiveComparator.class);
      RuntimeReflection.register(String.CaseInsensitiveComparator.class.getDeclaredMethod("compare", String.class, String.class));
    } catch (NoSuchMethodException | NoSuchFieldException e) { ... }
  }
}
```
To activate the custom feature `--features=<fully qualified name of RuntimeReflectionRegistrationFeature class>` needs to be passed to native-image.
[Native Image Build Configuration](BuildConfiguration.md) explains how this can be automated with a `native-image.properties` file in `META-INF/native-image`.

### Use of Reflection during Native Image Generation
Reflection can be used without restrictions during a native image generation, for example, in static initializers.
At this point, code can collect information about methods and fields and store them in their own data structures, which are then reflection-free at run time.

### Unsafe Accesses
The `Unsafe` class, although its use is discouraged, provides direct access to the memory of Java objects.
The `Unsafe.objectFieldOffset()` method provides the offset of a field within a Java object.
Note that the offsets that are queried during native image generation can be different from the offsets at run time.
