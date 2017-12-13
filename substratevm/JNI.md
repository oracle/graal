# Java Native Interface (JNI) on Substrate VM

JNI is a native API that enables Java code to interact with native code and vice versa. Supporting JNI in Substrate VM allows us to execute Java code that uses JNI, with few or no changes to that code. Our reason for implementing JNI is to support Truffle Node.JS, which has been implemented using JNI. This page gives an overview over our JNI implementation.

## Integration
The JNI implementation is not enabled and not included in Substrate VM images by default. It must be explicitly enabled with `-H:+JNI`. Usually, it is also necessary to specify `-H:JNIConfigurationFiles` (read below).

## Linking
Java code can load native code from a shared object with `System.loadLibrary()`. Alternatively, native code can load the JVM's native library and spawn or attach to its Java environment using JNI's *invocation API*. We support both approaches.

## Reflection
JNI supports looking up classes by their names, and looking up methods and fields by their names and signatures. This requires keeping the necessary metadata for these lookups around. In Substrate VM, we also need to know beforehand which items will be looked up in case they might not be reachable otherwise and therefore would not be included in the VM image. Moreover, we must generate call wrapper code ahead of time for any method that can be called via JNI, so even when only otherwise reachable items are used via JNI, it makes sense to specify a more concise list of items that actually need to be accessible. We support doing so using the following argument to `mx image`:

    -H:JNIConfigurationFiles=/path/to/jniconfig

where `jniconfig` is a JSON file in the following format (use `-H:+PrintFlags` for more details):

    [
      {
        "name" : "java.lang.String",
        "methods" : [
          { "name" : "substring", "parameterTypes" : ["int", "int"] }
        ],
        "fields" : [
          { "name" : "value" },
          { "name" : "hash" }
        ]
      },
      {
        "name" : "java.lang.String$CaseInsensitiveComparator",
        "methods" : [ { "name" : "compare" } ]
      }
    ]

During the image build, we generate reflection metadata for the classes, methods and fields referenced in that file. More than one JNI configuration can be used by specifying multiple paths for `JNIConfigurationFiles` that are separated with `,`.

Alternatively, a custom `Feature` implementation can register program elements before and during the analysis phase of the native image build using the `JNIRuntimeAccess` class. For example:

    @AutomaticFeature
    class JNIRegistrationFeature implements Feature {
      public void beforeAnalysis(BeforeAnalysisAccess access) {
        try {
          JNIRuntimeAccess.register(String.class);
          JNIRuntimeAccess.register(String.class.getDeclaredMethod("substring", int.class, int.class));
          JNIRuntimeAccess.register(String.class.getDeclaredField("value"));
          JNIRuntimeAccess.register(String.class.getDeclaredField("hash"));
        } catch (NoSuchMethodException | NoSuchFieldException e) { ... }
      }
    }

## Object handles
JNI does not permit direct access to Java objects. Instead, JNI provides word-sized object handles that can be passed to JNI functions to access objects indirectly. Local handles are only valid for the duration of a native call and only in the caller's thread, while global handles remain valid until they are destroyed explicitly. The handle 0 represents NULL.

We implement local handles with a thread-local, growing array of referenced objects, where the index in the array is the handle value. We maintain a "finger" that points to where the next handle will be allocated. Native calls can be nested, so before a native method is invoked, we push the current finger on the stack, and after it returns, we restore the old finger from the stack and nullify all object references from the call in the array. Global handles are implemented using a ConcurrentHashMap with negative decreasing handles for keys and objects as values. Therefore, we can distinguish local and global handles by only looking at their sign. The analysis is not obstructed by object handles because it can observe the entire flow of object references and the handles that are passed to native code are only numeric values.

## Java-to-native method calls
Methods declared with the `native` keyword have a JNI-compliant implementation in native code, but can be called like any other Java method. For example:

    // Java declaration
    native int[] sort0(int[] array);
    // native declaration with JNI name mangling
    jintArray JNICALL Java_org_example_sorter_IntSorter_sort0(JNIEnv *env, jobject this, jintArray array) {

When we encounter a method that is declared native, we generate a Graal graph with a wrapper that performs the transition to native code and back, adds the `JNIEnv*` and `this` arguments, boxes any object arguments in handles and, in case of an object return type, unboxes the returned handle.

The actual native call target can only be determined at runtime. Therefore, we create an extra linkage object in the reflection metadata of native-declared methods. When a native method is called, the call wrapper looks up the matching symbol in all loaded libraries and stores the resolved address in the linkage object for future use. Alternatively, instead of requiring symbols that conform to JNI's name mangling scheme, we also support explicitly providing code addresses via JNI's `RegisterNatives` function.

## JNI Functions
JNI provides a set of functions that native code can use to interact with Java code. We implement these functions using @CEntryPoint, for example:

    @CEntryPoint(...) private static void DeleteGlobalRef(JNIEnvironment env, JNIObjectHandle globalRef) { /* setup; */ JNIGlobalHandles.singleton().delete(globalRef); }

JNI specifies that these functions are provided via function pointers in a C struct that is accessible via the `JNIEnv*` argument. We prepare the automatic initialization of this struct during image build time.

## Native-to-Java method calls
Native code can invoke Java methods by first obtaining a `jmethodID` for the target method, and then using one of the `Call<Type>Method` or `CallStatic<Type>Method` functions for the invocation. For example:

    jmethodID intcomparator_compare_method = (*env)->GetMethodID(env, intcomparator_class, "compare", "(II)I");
    jint result = (*env)->CallIntMethod(env, this, intcomparator_compare_method, a, b);

We generate a call wrapper for each method that can be called via JNI according to the provided configuration. The call wrapper conforms to the signature of the JNI `Call<Type>Method` function that is appropriate for the method. The call wrapper performs the transition to Java code and back, adapts the argument list to the target Java method's signature, unboxes any passed object handles, and if necessary, boxes the return type in an object handle.

We provide the entry point address of a method's call wrapper as its `jmethodID`. Because the call wrapper conforms precisely to the appropriate `Call<Type>Method` signature, we implement the `Call<Type>Method` functions themselves as merely an unconditional jump to the passed `jmethodID`. Moreover, the call wrappers efficiently restore the VMThread register (r15 on amd64) from the JNIEnv* argument, which we ensure is placed at offset 0 of the VMThread structure.
JNI also specifies `Call<Type>MethodA` and `Call<Type>MethodV` functions, which take arguments as an array or `va_list` instead of varargs. We currently do not support these variants, as well as the `CallNonvirtual<Type>Method` variants.

## Field accesses
Native code can access a Java field by obtaining its `jfieldID` and then using one of the `Get<Type>Field`, `Set<Type>Field`, `GetStatic<Type>Field` or `SetStatic<Type>Field` functions. For example:

    jfieldID intsorter_comparator_field = (*env)->GetFieldID(env, intsorter_class, "comparator", "Lorg/example/sorter/IntComparator;");
    jobject value = (*env)->GetObjectField(env, self, intsorter_comparator_field);

For fields that are accessible via JNI, we store their offsets within an object (or within the static field area) in the reflection metadata and provide this offset as their `jfieldID`. We generate accessor methods for each type of field, which perform the transition to Java code and back, and use unsafe loads or stores to directly manipulate the field values.

Because the analysis cannot observe assignments of object fields via JNI, we create a type flow that matches any subtype of the field's declared type for fields that are accessible via JNI.

## Exceptions
JNI specifies that exceptions in Java code that are the result of an operation in native code must be caught and retained. Native code can query and clear a pending exception with the `ExceptionCheck`, `ExceptionOccurred`, `ExceptionDescribe` and `ExceptionClear` functions. Native code can also directly throw exceptions with `Throw`, `ThrowNew` or `FatalError`. An exception that remains pending is rethrown when reentering Java code.
We do not support these functions yet.
