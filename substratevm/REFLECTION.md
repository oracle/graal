# Reflection on Substrate VM
(See also the [guide on assisted configuration of Java reflection and other dynamic features](CONFIGURE.md))

Java reflection support (the `java.lang.reflect.*` API) enables Java code to examine its own classes, methods and fields and their properties at runtime.

Substrate VM has partial support for reflection and it needs to know ahead of time the reflectively accessed program elements.
Examining and accessing program elements through `java.lang.reflect.*` or loading classes with `Class.forName(String)` at run time requires preparing additional metadata for those program elements.
(Note: We include here loading classes with `Class.forName(String)` since it is closely related to reflection.)

SubstrateVM tries to resolve the target elements through a static analysis that detects calls to the reflection API.
Where the analysis fails the program elements reflectively accessed at run time must be specified using a manual configuration.

## Automatic detection

The analysis intercepts calls to `Class.forName(String)`, `Class.forName(String, ClassLoader)`, `Class.getDeclaredField(String)`, `Class.getField(String)`, `Class.getDeclaredMethod(String, Class[])`, `Class.getMethod(String, Class[])`, `Class.getDeclaredConstructor(Class[])` and `Class.getConstructor(Class[])`.
If the arguments to these calls can be reduced to a constant we try to resolve the target elements.
If the target elements can be resolved the calls are removed and instead the target elements are embedded in the code.
If the target elements cannot be resolved, e.g., a class is not on the classpath or it doesn't declare a field/method/constructor, then the calls are replaced with a snippet that throws the appropriate exception at run time.
The benefits are two fold.
First, at run time there are no calls to the reflection API.
Second, Graal can employ constant folding and optimize the code further.

The calls are intercepted and processed only when it can be unequivocally determined that the parameters can be reduced to a constant.
For example the call `Class.forName(String)` will be replaced with a `Class` literal only if the `String` argument can be constant folded, assuming that the class is actually on the classpath.
Additionally a call to `Class.getMethod(String, Class[])` will be processed only if the contents of the `Class[]` argument can be determined with certainty.
The last restriction is due to the fact that Java doesn't have immutable arrays.
Therefore all the changes to the array between the time it is allocated and the time it is passed as an argument need to be tracked.
The analysis follows a simple rule: if all the writes to the array happen in linear sections of code, i.e., no control flow splits, then the array is effectively constant for the purpose of analyzing the call.
That is why the analysis doesn't accept `Class[]` arguments coming from static fields since the contents of those can change at any time, even if the fields are final.
Although this may seem too restrictive it covers the most commonly used patterns of reflection API calls.
The only exception to the constant arguments rule is that the `ClassLoader` argument of `Class.forName(String, ClassLoader)` doesn't need to be a constant; it is ignored and instead a class loader that can load all the classes on the class path is used.
The analysis runs to a fix point which means that a chain of calls like `Class.forName(String).getMethod(String, Class[])` will first replace the class constant and then the method effectively reducing this to a `java.lang.reflect.Method`.

Following are examples of calls that can be intercepted and replaced with the corresponding element:

```
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

```
Class<?>[] params0 = new Class<?>[]{String.class, int.class};
Integer.class.getMethod("parseInt", params0);
```

```
Class<?>[] params1 = new Class<?>[2];
params1[0] = Class.forName("java.lang.String");
params1[1] = int.class;
Integer.class.getMethod("parseInt", params1);
```

```
Class<?>[] params2 = {String.class, int.class};
Integer.class.getMethod("parseInt", params2);
```

If a call cannot be processed it is simply skipped.
For these situations a manual configuration as described below can be provided.

## Manual configuration

A configuration that specifies the program elements that will be accessed reflectively can be provided during the image build as follows:

    -H:ReflectionConfigurationFiles=/path/to/reflectconfig

where `reflectconfig` is a JSON file in the following format (use `--expert-options` for more details):

	[
	  {
	    "name" : "java.lang.Class",
	    "allDeclaredConstructors" : true,
	    "allPublicConstructors" : true,
	    "allDeclaredMethods" : true,
	    "allPublicMethods" : true,
	    "allDeclaredClasses" : true,
	    "allPublicClasses" : true
	  },
	  {
	    "name" : "java.lang.String",
	    "fields" : [
	      { "name" : "value", "allowWrite" : true },
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
        "methods" : [
          { "name" : "compare" }
        ]
      }
	]

The image build generates reflection metadata for all classes, methods and fields referenced in that file.
The `allPublicConstructors`, `allDeclaredConstructors`, `allPublicMethods`, `allDeclaredMethods`, `allPublicFields`, `allDeclaredFields`, `allPublicClasses` and `allDeclaredClasses` attributes can be used to automatically include an entire set of members of a class.
However, `allPublicClasses` and `allDeclaredClasses` don't automatically register the inner classes for reflective access.
They just make them available via `Class.getClasses()` and `Class.getDeclaredClasses()` when called on the declaring class.
In order to write a field that is declared `final`, the `allowWrite` attribute must be specified for that field (but is not required for non-final fields).
However, code accessing final fields might not observe changes of final field values in the same way as for non-final fields because of optimizations.

More than one configuration can be used by specifying multiple paths for `ReflectionConfigurationFiles` and separating them with `,`.
Also, `-H:ReflectionConfigurationResources` can be specified to load one or several configuration files from the native image build's class path, such as from a JAR file.

Alternatively, a custom `Feature` implementation can register program elements before and during the analysis phase of the native image build using the `RuntimeReflection` class.
For example:

    @AutomaticFeature
    class RuntimeReflectionRegistrationFeature implements Feature {
      public void beforeAnalysis(BeforeAnalysisAccess access) {
        try {
          RuntimeReflection.register(String.class);
          RuntimeReflection.register(/* finalIsWritable: */ true, String.class.getDeclaredField("value"));
          RuntimeReflection.register(String.class.getDeclaredField("hash"));
          RuntimeReflection.register(String.class.getDeclaredConstructor(char[].class));
          RuntimeReflection.register(String.class.getDeclaredMethod("charAt", int.class));
          RuntimeReflection.register(String.class.getDeclaredMethod("format", String.class, Object[].class));
          RuntimeReflection.register(String.CaseInsensitiveComparator.class);
          RuntimeReflection.register(String.CaseInsensitiveComparator.class.getDeclaredMethod("compare", String.class, String.class));
        } catch (NoSuchMethodException | NoSuchFieldException e) { ... }
      }
    }


## Use during Native Image Generation
Reflection can be used without restrictions during native image generation, for example in static initializers.
At this point, code can collect information about methods and fields and store them in own data structures, which are then reflection-free at run time.

## Unsafe accesses
The `Unsafe` class, although its use is discouraged, provides direct access to memory of Java objects. The `Unsafe.objectFieldOffset()` method provides the offset of a field within a Java object. This information is not available by default, but can be enabled with the `allowUnsafeAccess` attribute in the reflection configuration, for example:
```
"fields" : [ { "name" : "hash", "allowUnsafeAccess" : true }, ... ]
```

Note that offsets that are queried during native image generation can be different to the offsets at runtime.
