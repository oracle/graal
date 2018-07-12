# Reflection on Substrate VM

Java reflection support (`java.lang.reflect`) enables Java code to examine its own classes, methods and fields and their properties at runtime. One useful application of this feature is the serialization of arbitrary data structures. Substrate VM has partial support for reflection and requires a configuration with those program elements that should be examinable at image runtime.

## Configuration

Examining and accessing program elements through `java.lang.reflect` at runtime requires preparing additional metadata for those program elements. A configuration that specifies those program elements must be provided during the image build as follows:

    -H:ReflectionConfigurationFiles=/path/to/reflectconfig

where `reflectconfig` is a JSON file in the following format (use `--expert-options` for more details):

	[
	  {
	    "name" : "java.lang.Class",
	    "allDeclaredConstructors" : true,
	    "allPublicConstructors" : true
	    "allDeclaredMethods" : true,
	    "allPublicMethods" : true
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
	      { "name" : "format", "parameterTypes" : ["java.lang.String", "java.lang.Object[]"] },
	    ]
	  },
      {
        "name" : "java.lang.String$CaseInsensitiveComparator",
        "methods" : [
          { "name" : "compare" }
        ]
      }
	]

The image build generates reflection metadata for all classes, methods and fields referenced in that file. The `allPublicConstructors`, `allPublicMethods`, `allPublicFields`, `allDeclaredConstructors`, `allDeclaredMethods` and `allDeclaredFields` attributes can be used to automatically include an entire set of members of a class. More than one configuration can be used by specifying multiple paths for `ReflectionConfigurationFiles` and separating them with `,`. Also, `-H:ReflectionConfigurationResources` can be specified to load one or several configuration files from the native image build's class path, such as from a JAR file.

Alternatively, a custom `Feature` implementation can register program elements before and during the analysis phase of the native image build using the `RuntimeReflection` class. For example:

    @AutomaticFeature
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


## Limitations at Runtime
Dynamic class loading using `Class.forName()` is not available due to the ahead-of-time image generation model of Substrate VM.

See also our [list of general limitations](#LIMITATIONS.md).

## Use during Native Image Generation
Reflection can be used without restrictions during native image generation, for example in static initializers. At this point, code can collect information about methods and fields and store them in own data structures, which are then reflection-free at run time.
