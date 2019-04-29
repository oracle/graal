# Java Native Interface (JNI) on Substrate VM
(See also the [guide on assisted configuration of JNI and other dynamic features](CONFIGURE.md))

JNI is a native API that enables Java code to interact with native code and vice versa.
This page gives an overview over the JNI implementation in Substrate VM.

## Integration
JNI support is enabled by default and built into Substrate VM images. Individual classes, methods, and fields that should be accessible via JNI must be specified during native image generation in a configuration file (read below).

## Linking
Java code can load native code from a shared object with `System.loadLibrary()`.
Alternatively, native code can load the JVM's native library and attach to its Java environment using JNI's *invocation API*.
The Substrate VM JNI implementation supports both approaches.

## Reflection
JNI supports looking up classes by their names, and looking up methods and fields by their names and signatures.
This requires keeping the necessary metadata for these lookups around.
The image build must know beforehand which items will be looked up in case they might not be reachable otherwise and therefore would not be included in the native image.
Moreover, the image build must generate call wrapper code ahead of time for any method that can be called via JNI.
Therefore, specifying a concise list of items that need to be accessible via JNI guarantees their availability and allows for a smaller footprint.
Such a list can be specified with the following image build argument:

    -H:JNIConfigurationFiles=/path/to/jniconfig

where `jniconfig` is a JSON configuration file.
The syntax is the same as for reflection configuration files, which are described in the [documentation on reflection](REFLECTION.md).

The image build generates JNI reflection metadata for all classes, methods and fields referenced in the configuration file.
More than one JNI configuration can be used by specifying multiple paths for `JNIConfigurationFiles` and separating them with `,`.
Also, `-H:JNIConfigurationResources` can be specified to load one or several configuration files from the image build's class path, such as from a JAR file.

Alternatively, a custom `Feature` implementation can register program elements before and during the analysis phase of the native image build using the `JNIRuntimeAccess` class.
For example:

    @AutomaticFeature
    class JNIRegistrationFeature implements Feature {
      public void beforeAnalysis(BeforeAnalysisAccess access) {
        try {
          JNIRuntimeAccess.register(String.class);
          JNIRuntimeAccess.register(String.class.getDeclaredField("value"));
          JNIRuntimeAccess.register(String.class.getDeclaredField("hash"));
          JNIRuntimeAccess.register(String.class.getDeclaredConstructor(char[].class));
          JNIRuntimeAccess.register(String.class.getDeclaredMethod("charAt", int.class));
          JNIRuntimeAccess.register(String.class.getDeclaredMethod("format", String.class, Object[].class));
          JNIRuntimeAccess.register(String.CaseInsensitiveComparator.class);
          JNIRuntimeAccess.register(String.CaseInsensitiveComparator.class.getDeclaredMethod("compare", String.class, String.class));
        } catch (NoSuchMethodException | NoSuchFieldException e) { ... }
      }
    }

## Object handles
JNI does not permit direct access to Java objects.
Instead, JNI provides word-sized object handles that can be passed to JNI functions to access objects indirectly.
Local handles are only valid for the duration of a native call and only in the caller's thread, while global handles are valid across threads and remain valid until they are destroyed explicitly.
The handle 0 represents `NULL`.

Substrate VM implements local handles with a thread-local, growing array of referenced objects, where the index in the array is the handle value.
A "finger" points to where the next handle will be allocated.
Native calls can be nested, so before a native method is invoked, the call stub pushes the current finger on a stack, and after it returns, it restores the old finger from the stack and nullifies all object references from the call in the array.
Global handles are implemented using a variable number of object arrays in which referenced objects are inserted and nullified using atomic operations.
A global handle's value is a negative integer that is determined from the index of the containing array and the index within the array.
Therefore, the JNI code can distinguish local and global handles by only looking at their sign.
The analysis is not obstructed by object handles because it can observe the entire flow of object references and the handles that are passed to native code are only numeric values.

## Java-to-native method calls
Methods declared with the `native` keyword have a JNI-compliant implementation in native code, but can be called like any other Java method.
For example:

    // Java declaration
    native int[] sort0(int[] array);
    // native declaration with JNI name mangling
    jintArray JNICALL Java_org_example_sorter_IntSorter_sort0(JNIEnv *env, jobject this, jintArray array) {

When the image build encounters a method that is declared native, it generates a Graal graph with a wrapper that performs the transition to native code and back, adds the `JNIEnv*` and `this` arguments, boxes any object arguments in handles and, in case of an object return type, unboxes the returned handle.

The actual native call target address can only be determined at runtime.
Therefore, the image build also creates an extra linkage object in the reflection metadata of native-declared methods.
When a native method is called, the call wrapper looks up the matching symbol in all loaded libraries and stores the resolved address in the linkage object for future calls.
Alternatively, instead of requiring symbols that conform to JNI's name mangling scheme, Substrate VM also supports the `RegisterNatives` JNI function to explicitly provide code addresses for native methods.

## JNI Functions
JNI provides a set of functions that native code can use to interact with Java code.
Substrate VM implements these functions using `@CEntryPoint`, for example:

    @CEntryPoint(...) private static void DeleteGlobalRef(JNIEnvironment env, JNIObjectHandle globalRef) { /* setup; */ JNIGlobalHandles.singleton().delete(globalRef); }

JNI specifies that these functions are provided via function pointers in a C struct that is accessible via the `JNIEnv*` argument.
The automatic initialization of this struct is prepared during the image build.

## Native-to-Java method calls
Native code can invoke Java methods by first obtaining a `jmethodID` for the target method, and then using one of the `Call<Type>Method`, `CallStatic<Type>Method` or `CallNonvirtual<Type>Method` functions for the invocation.
Each of these `Call...` functions is also available in a `Call...MethodA` and a `Call...MethodV` variant, which take arguments as an array or as a `va_list` instead of variadic arguments.
For example:

    jmethodID intcomparator_compare_method = (*env)->GetMethodID(env, intcomparator_class, "compare", "(II)I");
    jint result = (*env)->CallIntMethod(env, this, intcomparator_compare_method, a, b);

The image build generates call wrappers for each method that can be called via JNI according to the provided JNI configuration.
The call wrappers conform to the signature of the JNI `Call...` functions that are appropriate for the method.
The wrappers perform the transition to Java code and back, adapt the argument list to the target Java method's signature, unbox any passed object handles, and if necessary, box the return type in an object handle.

Each method that can be called via JNI has a reflection metadata object.
The address of this object is used as the method's `jmethodID`.
The metadata object contains the addresses of all of the method's generated call wrappers.
Because each call wrapper conforms precisely to the corresponding `Call...` function signature, the `Call...` functions themselves are nothing more than an unconditional jump to the appropriate call wrapper based on the passed `jmethodID`.
As another optimization, the call wrappers are able to efficiently restore the current thread's Java context from the `JNIEnv*` argument.

## Object creation
JNI provides two ways of creating Java objects, either by calling `AllocObject` to allocate memory and then using `CallVoidMethod` to invoke the constructor, or by using `NewObject` to create and initialize the object in a single step (or variants `NewObjectA` or `NewObjectV`).
For example:

    jclass calendarClass = (*env)->FindClass(env, "java/util/GregorianCalendar");
    jmethodID ctor = (*env)->GetMethodID(env, calendarClass, "<init>", "(IIIIII)V");
    jobject firstObject = (*env)->AllocObject(env, calendarClass);
    (*env)->CallVoidMethod(env, obj, ctor, year, month, dayOfMonth, hourOfDay, minute, second);
    jobject secondObject = (*env)->NewObject(env, calendarClass, ctor, year, month, dayOfMonth, hourOfDay, minute, second);

Substrate VM supports both approaches.
The constructor must be included in the JNI configuration with a method name of `<init>`.
Instead of generating additional call wrappers for `NewObject`, the regular `CallVoidMethod` wrapper is reused and detects when it is called via `NewObject` because it is passed the `Class` object of the target class.
In that case, the call wrapper allocates a new instance before invoking the actual constructor.

## Field accesses
Native code can access a Java field by obtaining its `jfieldID` and then using one of the `Get<Type>Field`, `Set<Type>Field`, `GetStatic<Type>Field` or `SetStatic<Type>Field` functions.
For example:

    jfieldID intsorter_comparator_field = (*env)->GetFieldID(env, intsorter_class, "comparator", "Lorg/example/sorter/IntComparator;");
    jobject value = (*env)->GetObjectField(env, self, intsorter_comparator_field);

For a field that is accessible via JNI, its offset within an object (or within the static field area) is stored in the reflection metadata and is used as its `jfieldID`.
The image build generates accessor methods for for fields of all primitive types and for object fields.
These accessor methods perform the transition to Java code and back, and use unsafe loads or stores to directly manipulate the field value.
Because the analysis cannot observe assignments of object fields via JNI, it assumes that any subtype of the field's declared type can occur in a field that is accessible via JNI.

JNI also permits writing fields that are declared `final`, which must be enabled for individual fields with an `allowWrite` property in the configuration file.
However, code accessing final fields might not observe changes of final field values in the same way as for non-final fields because of optimizations.

## Exceptions
JNI specifies that exceptions in Java code that are the result of a call from native code must be caught and retained.
In Substrate VM, this is done in the native-to-Java call wrappers and in the implementation JNI functions.
Native code can then query and clear a pending exception with the `ExceptionCheck`, `ExceptionOccurred`, `ExceptionDescribe` and `ExceptionClear` functions.
Native code can also throw exceptions with `Throw`, `ThrowNew` or `FatalError`.
When an exception remains unhandled in native code or the native code itself throws an exception, the Java-to-native call wrapper rethrows that exception upon reentering Java code.

## Monitors
JNI declares the functions `MonitorEnter` and `MonitorExit` to acquire and release the intrinsic lock of an object.
Substrate VM provides implementations of these functions.
However, the native image build directly assigns intrinsic locks only to objects of classes which the analysis can observe as being used in Java `synchronized` statements and with `wait()`, `notify()` and `notifyAll()`.
For other objects, synchronization falls back on a slower mechanism which uses a map to store lock associations, which itself needs synchronization.
For that reason, it can be beneficial to wrap synchronization in Java code.

## java.lang.reflect Support
The JNI functions `FromReflectedMethod` and `ToReflectedMethod` can be used to obtain the corresponding `jmethodID` to a `java.lang.reflect.Method`, or to a `java.lang.reflect.Constructor` object, and vice versa.
The functions `FromReflectedField` and `ToReflectedField` convert between `jfieldID` and `java.lang.reflect.Field`.
In order to use these functions, [reflection support](REFLECTION.md) must be enabled and the methods and fields in question must be included in the reflection configuration, which is specified with `-H:ReflectionConfigurationFiles=`.
