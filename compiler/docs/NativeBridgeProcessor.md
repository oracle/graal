## Native bridge annotation processor

The native bridge annotation processor generates code to simplify making calls between code executing in two disjoint JVM runtimes (e.g. Native Image and HotSpot) by generating stubs for calling foreign methods using a JNI interface. The processor can bridge interfaces, classes, and classes with custom dispatch. A single stub can bridge either a single class or a single interface. The supported data types are Java primitive types, `String`, foreign reference types, and arrays of primitive types or foreign reference types. Custom types are supported using marshallers registered in the `JNIConfig.Builder`. The processor processes the [@GenerateHotSpotToNativeBridge](https://github.com/oracle/graal/blob/master/compiler/src/org.graalvm.nativebridge/src/org/graalvm/nativebridge/GenerateHotSpotToNativeBridge.java) and [@GenerateNativeToHotSpotBridge](https://github.com/oracle/graal/blob/master/compiler/src/org.graalvm.nativebridge/src/org/graalvm/nativebridge/GenerateNativeToHotSpotBridge.java) annotations.

### Bridging an interface

In the following example, we generate both HotSpot to Native Image and Native Image to HotSpot bridges for a `Calculator` interface.

```java
interface Calculator {
    int add(int a, int b);
    int sub(int a, int b);
}
```

To generate a bridge from a HotSpot VM to a Native Image, create an abstract base class extending `NativeObject` that implements the interface `Calculator` and annotate it using `@GenerateHotSpotToNativeBridge`.

```java
@GenerateHotSpotToNativeBridge(jniConfig = ExampleJNIConfig.class)
abstract class NativeCalculator extends NativeObject implements Calculator {

    NativeCalculator(NativeIsolate isolate, long handle) {
        super(isolate, handle);
    }
}
```

The annotation processor generates class `NativeCalculatorGen` with a static factory method `createHSToNative(NativeIsolate isolate, long handle)`. The method creates an instance of `NativeCalculator` that forwards `add` and `sub` methods from the HotSpot VM to an object in the native image heap. The `jniConfig` attribute will be explained in the [JNIConfig](#JNIConfig) section.

To use the generated stubs we need to first create an isolate and obtain a foreign object handle before we can call into the generated interface.

```java
long isolateThreadId = ExampleJNIConfig.createIsolate();
long isolateId = ExampleJNIConfig.getIsolateId(isolateThreadId);
long calculatorHandle = ExampleJNIConfig.initializeCalculator(isolateThreadId);
NativeIsolate isolate = NativeIsolate.forIsolateId(isolateId, ExampleJNIConfig.getInstance());
NativeCalculator calculator = NativeCalculatorGen.createHSToNative(isolate, calculatorHandle);
calculator.add(calculator.sub(41, 2), 1);
```

The lifetime of a foreign object referenced by the `calculatorHandle` is bound to the lifetime of the `calculator` instance. At some point, after a `calculator` is garbage collected, a call is made to release the object in the native image heap referenced by the `calculatorHandle`.

The steps to create a bridge from a Native Image to a HotSpot VM are similar. Create an abstract base class that extends `HSObject`, implement the bridged interface `Calculator` and annotate it using `@GenerateNativeToHotSpotBridge`.

```java
@GenerateNativeToHotSpotBridge(jniConfig = ExampleJNIConfig.class)
abstract class HSCalculator extends HSObject implements Calculator {

    HSCalculator(JNI.JNIEnv jniEnv, JNI.JObject handle) {
        super(jniEnv, handle);
    }
}
```

The annotation processor generates class `HSCalculatorGen` with a static factory method `createNativeToHS(JNIEnv jniEnv, JObject handle)`. The method creates an instance of `HSCalculator` that forwards `add` and `sub` methods from a Native Image to an object in a HotSpot VM.

### Bridging a class

When it's not possible to use an interface to define operations to bridge the annotation processor also supports base classes. This can be useful if you need to bridge an existing JDK or library type. For classes, all public non-static non-final non-native methods which are not inherited from `java.lang.Object` are bridged. In the following example, we generate bridges for the `Calculator` class.

```java
class Calculator {
    public int add(int a, int b) { return a + b; }
    public int sub(int a, int b) { return a - b; }
}
```

To generate a bridge from a HotSpot VM to a Native Image, create an abstract base class extending `Calculator` with a package-private field of `NativeObject` type annotated with `@EndPointHandle` and annotate it using `@GenerateHotSpotToNativeBridge`.

```java
@GenerateHotSpotToNativeBridge(jniConfig = ExampleJNIConfig.class)
abstract class NativeCalculator extends Calculator {

    @EndPointHandle
    final NativeObject delegate;

    NativeCalculator(NativeObject delegate) {
        this.delegate = delegate;
    }
}
```

The annotation processor generates class `NativeCalculatorGen` with a static `createHSToNative(NativeIsolate isolate, long handle)` factory method creating a `NativeCalculator` instance forwarding `add` and `sub` methods from the HotSpot VM to an object in the native image heap. The `jniConfig` attribute will be explained in the [JNIConfig](#JNIConfig) section.

To use the generated stubs we need to first create an isolate and obtain a foreign object handle before we can call into the generated interface.

```java
long isolateThreadId = ExampleJNIConfig.createIsolate();
long isolateId = ExampleJNIConfig.getIsolateId(isolateThreadId);
long calculatorHandle = ExampleJNIConfig.initializeCalculator(isolateThreadId);
NativeIsolate isolate = NativeIsolate.forIsolateId(isolateId, ExampleJNIConfig.getInstance());
NativeCalculator calculator = NativeCalculatorGen.createHSToNative(new NativeObject(isolate, calculatorHandle));
calculator.add(calculator.sub(41, 2), 1);
```

To generate a bridge from a Native Image to a HotSpot VM, create an abstract base class extending `Calculator` with a package-private field of `HSObject` type annotated with `@EndPointHandle` and annotate it using `@GenerateNativeToHotSpotBridge`.

```java
@GenerateNativeToHotSpotBridge(jniConfig = ExampleJNIConfig.class)
abstract class HSCalculator extends Calculator {

    @EndPointHandle
    final HSObject delegate;

    HSCalculator(HSObject delegate) {
        this.delegate = delegate;
    }
}
```

The annotation processor generates class `HSCalculatorGen` with a static `createNativeToHS(HSObject delegate, JNIEnv jniEnv)` factory method creating an `HSCalculator` instance forwarding `add` and `sub` methods from a Native Image to an object in a HotSpot VM.

### Bridging a class with a custom dispatch

Classes with a custom dispatch are final classes with a `receiver` and `dispatch` fields delegating all the operations to the `dispatch` instance. The `dispatch` methods take the `receiver` as the first parameter. The following example shows a `Language` class with a custom dispatch.

```java
final class Language {

    final AbstractLanguageDispatch dispatch;
    final Object receiver;

    Language(AbstractLanguageDispatch dispatch, Object receiver) {
        this.dispatch = Objects.requireNonNull(dispatch);
        this.receiver = Objects.requireNonNull(receiver);
    }

    String getId() {
        return dispatch.getId(receiver);
    }

    String getName() {
        return dispatch.getName(receiver);
    }

    String getVersion() {
        return dispatch.getVersion(receiver);
    }
}

abstract class AbstractLanguageDispatch {

    abstract String getId(Object receiver);

    abstract String getName(Object receiver);

    abstract String getVersion(Object receiver);
}
```

To generate a bridge from a HotSpot VM to a Native Image, create an abstract base class extending `AbstractLanguageDispatch` and annotate it using `@GenerateHotSpotToNativeBridge`. The base class needs to provide methods to get the `receiver` and `dispatch` instances from the `Language` and a method to create a new `Language` instance for a `receiver`.

```java
@GenerateHotSpotToNativeBridge(jniConfig = ExampleJNIConfig.class)
abstract class NativeLanguageDispatch extends AbstractLanguageDispatch {

    private static final NativeLanguageDispatch INSTANCE = NativeLanguageDispatchGen.createHSToNative();

    @CustomDispatchAccessor
    static AbstractLanguageDispatch getDispatch(Language language) {
        return language.dispatch;
    }

    @CustomReceiverAccessor
    static Object getReceiver(Language language) {
        return language.receiver;
    }

    @CustomDispatchFactory
    static Language createLanguage(Object receiver) {
        return new Language(INSTANCE, receiver);
    }
}
```

The annotation processor generates class `NativeLanguageDispatchGen` with a static `createHSToNative()` factory method creating a `NativeLanguageDispatch` instance forwarding methods from the HotSpot VM to an object in the native image heap. The `jniConfig` attribute will be explained in the [JNIConfig](#JNIConfig) section.

To use the generated stubs we need to first create an isolate and obtain a foreign object handle before we can call into the generated interface.

```java
long isolateThreadId = ExampleJNIConfig.createIsolate();
long isolateId = ExampleJNIConfig.getIsolateId(isolateThreadId);
long languageHandle = ExampleJNIConfig.initializeLanguage(isolateThreadId);
NativeIsolate isolate = NativeIsolate.forIsolateId(isolateId, ExampleJNIConfig.getInstance());
Language language = NativeLanguageDispatch.createLanguage(new NativeObject(isolate, languageHandle));
language.getName();
```

### Excluding methods from generation

Sometimes it's necessary to exclude a method from being bridged. This can be done by overriding a method in the annotated class and making it `final`. The following example excludes `sub` method from being bridged.

```java
@GenerateHotSpotToNativeBridge(jniConfig = ExampleJNIConfig.class)
abstract class NativeCalculator extends Calculator {

    @EndPointHandle
    final NativeObject delegate;

    NativeCalculator(NativeObject delegate) {
        this.delegate = delegate;
    }

    @Override
    public final int sub(int a, int b) {
        return super.sub(a, b);
    }
}
```

### Custom marshallers

The annotation processor supports Java primitive types, `String`, arrays of primitive types, and foreign references. For other types, a marshaller must be registered in the `JNIConfig`. In the following example, we will change the `Calculator` interface to work with a custom complex number type.

```java
final class Complex {
    final int re;
    final int img;
    
    Complex(int re, int img) {
        this.re = re;
        this.img = img;
    }
}

interface Calculator {
    Complex add(Complex a, Complex b);
    Complex sub(Complex a, Complex b);
}
```

The `Complex` type is unknown to the annotation processor. We need to provide a marshaller to convert it into a serial form and recreate it from a serial form.

```java
final class ComplexMarshaller implements BinaryMarshaller<Complex> {

    @Override
    public Complex read(BinaryInput input) {
        int re = input.readInt();
        int img = input.readInt();
        return new Complex(re, img);
    }

    @Override
    public void write(BinaryOutput output, Complex complex) {
        output.writeInt(complex.re);
        output.writeInt(complex.img);
    }

    @Override
    public int inferSize(Complex complex) {
        return Integer.BYTES * 2;
    }
}
```

Finally, the marshaller needs to be registered in the `JNIConfig`, see [JNIConfig](#JNIConfig) section for information on how to register a marshaller.

### Foreign reference types

The foreign reference is used when a bridged type returns a reference to another bridged type.
Following example bridges `Compilation` and `Compiler` interfaces. The `Compiler` interface uses `Compilation` type both as a return type and a parameter type.

```java
interface Compilation extends Closeable {

    @Override void close();

    String getId();
}

interface Compiler {

    Compilation openCompilation();
    byte[] compile(Compilation compilation, byte[] source);
}
```

To marshall the `Compilation` as a foreign object reference, annotate the use of `Compilation` with the `@ByReference` annotation. The processor takes care of reference marshaling.

```java
@GenerateHotSpotToNativeBridge(jniConfig = ExampleJNIConfig.class)
abstract class NativeCompilation extends NativeObject implements Compilation {

    NativeCompilation(NativeIsolate isolate, long handle) {
        super(isolate, handle);
    }
}

@GenerateHotSpotToNativeBridge(jniConfig = ExampleJNIConfig.class)
abstract class NativeCompiler extends NativeObject implements Compiler {

    NativeCompiler(NativeIsolate isolate, long handle) {
        super(isolate, handle);
    }

    @Override
    @ByReference(NativeCompilation.class)
    public abstract Compilation openCompilation();

    @Override
    public abstract byte[] compile(@ByReference(NativeCompilation.class) Compilation compilation, byte[] source);
}
```

More foreign reference examples can be found in the [bridge processor tests](https://github.com/oracle/graal/tree/master/compiler/src/org.graalvm.nativebridge.processor.test/src/org/graalvm/nativebridge/processor/test/references/).

### Arrays

Arrays of primitive types or foreign reference types are directly supported by the annotation processor. The array method parameters are by default treated as `in` parameters, the content of the array is copied to the called method. Sometimes it's needed to change this behavior and treat the array parameter as an `out` parameter. This can be done using an `@Out` annotation. The following example bridges a `read` method with an `out` array parameter.

```java
interface Reader {
    int read(byte[] buffer, int offset, int length);
}

@GenerateHotSpotToNativeBridge(jniConfig = ExampleJNIConfig.class)
abstract class NativeReader extends NativeObject implements Reader {

    NativeReader(NativeIsolate isolate, long handle) {
        super(isolate, handle);
    }

    @Override
    public abstract int read(@Out byte[] buffer, int offset, int length);
}
```

The `@Out` annotation attributes can be used to improve the performance and copy only the needed part of the array.

```java
@Override
public abstract int read(@Out(arrayOffsetParameter = "off", arrayLengthParameter = "len", trimToResult = true) byte[] b, int off, int len);
```

The `@Out` annotation can be combined with the `@In` annotation for `in-out` array parameters. For `in-out` array parameters the array content is copied both into the called method and out of the called method.

```java
@Override
public abstract int read(@In @Out byte[] b, int off, int len);
```

To pass an array of foreign references, you must annotate the array using the `@ByReference` annotation. The following example bridges a `register` method taking an array of HotSpot object references.

```java
interface EventConsumer {
    void consumeEvent(String event);
}

interface EventSource {
    void register(EventConsumer[] consumers);
}

@GenerateNativeToHotSpotBridge(jniConfig = ExampleJNIConfig.class)
abstract class HSEventConsumer extends HSObject implements EventConsumer {
    HSEventConsumer(JNIEnv jniEnv,  JObject reference) {
        super(jniEnv, reference);
    }
}

@GenerateHotSpotToNativeBridge(jniConfig = ExampleJNIConfig.class)
abstract class NativeEventSource extends NativeObject implements EventSource {

    NativeEventSource(NativeIsolate isolate, long handle) {
        super(isolate, handle);
    }

    @Override
    public abstract void register(@ByReference(HSEventConsumer.class) EventConsumer[] consumers);
}
```

More foreign references array examples can be found in the [bridge processor tests](https://github.com/oracle/graal/tree/master/compiler/src/org.graalvm.nativebridge.processor.test/src/org/graalvm/nativebridge/processor/test/references/).

### JNIConfig

The `JNIConfig` class is used to configure generated classes and the native bridge framework. It configures callbacks to attach/detach thread to an isolate, tear down an isolate and clean up objects in the native image heap. In addition, it's used by generated classes to look up marshallers for custom types that are not directly supported by the annotation processor. The following example shows a `JNIConfig` registering needed callbacks and marshaller for the `Complex` type. See the [Custom marshallers](#Custom-marshallers) section for information on `Complex` and `ComplexMarshaller`.

```java
final class ExampleJNIConfig {

    private static final JNIConfig INSTANCE = createJNIConfig();
    
    static JNIConfig getInstance() {
        return INSTANCE;
    }

    private static JNIConfig createJNIConfig() {
        JNIConfig.Builder builder = JNIConfig.newBuilder();
        builder.setAttachThreadAction(ExampleJNIConfig::attachThread);
        builder.setDetachThreadAction(ExampleJNIConfig::detachThread);
        builder.setShutDownIsolateAction(ExampleJNIConfig::tearDownIsolate);
        builder.setReleaseNativeObjectAction(ExampleJNIConfig::releaseHandle);
        builder.registerMarshaller(Complex.class, new ComplexMarshaller());
        return builder.build();
    }

    static native long createIsolate();
    static native long getIsolateId(long isolateThread);
    private static native long attachIsolateThread(long isolate);
    private static native int detachIsolateThread(long isolateThread);
    private static native int tearDownIsolate(long isolateThread);
    private static native long releaseHandle(long isolateThread, long handle);

    static native long initializeCalculator(long isolateThread);
    static native long initializeLanguage(long isolateThread);

    @CEntryPoint(name = "Java_ExampleJNIConfig_createIsolate", builtin = CEntryPoint.Builtin.CREATE_ISOLATE)
    static native IsolateThread createIsolate(JNIEnv jniEnv, JClass clazz);

    @CEntryPoint(name = "Java_ExampleJNIConfig_getIsolateId", builtin = CEntryPoint.Builtin.GET_ISOLATE)
    static native Isolate getIsolateId(JNIEnv jniEnv, JClass clazz, IsolateThread isolateThread);

    @CEntryPoint(name = "Java_ExampleJNIConfig_attachIsolateThread", builtin = CEntryPoint.Builtin.ATTACH_THREAD)
    static native IsolateThread attachIsolateThread(JNIEnv jniEnv, JClass clazz, Isolate isolate);

    @CEntryPoint(name = "Java_ExampleJNIConfig_detachIsolateThread", builtin = CEntryPoint.Builtin.DETACH_THREAD)
    static native int detachIsolateThread(JNIEnv jniEnv, JClass clazz, IsolateThread isolateThread);

    @CEntryPoint(name = "Java_ExampleJNIConfig_tearDownIsolate", builtin = CEntryPoint.Builtin.TEAR_DOWN_ISOLATE)
    static native int tearDownIsolate(JNIEnv jniEnv, JClass clazz, IsolateThread isolateThread);

    @CEntryPoint(name = "Java_ExampleJNIConfig_releaseHandle")
    static long releaseHandle(JNIEnv jniEnv, JClass clazz, @CEntryPoint.IsolateThreadContext long isolateId, long objectHandle) {
        try {
            NativeObjectHandles.remove(objectHandle);
            return 0;
        } catch (Throwable t) {
            return -1;
        }
    }

    @CEntryPoint(name = "Java_ExampleJNIConfig_initializeCalculator")
    static long initializeCalculator(JNIEnv jniEnv, JClass clazz, @CEntryPoint.IsolateThreadContext long isolateId) {
        try {
            return NativeObjectHandles.create(new CalculatorImpl());
        } catch (Throwable t) {
            return 0;
        }
    }

    @CEntryPoint(name = "Java_ExampleJNIConfig_initializeLanguage")
    static long initializeLanguage(JNIEnv jniEnv, JClass clazz, @CEntryPoint.IsolateThreadContext long isolateId) {
        try {
            Language language = getLanguage();
            return NativeObjectHandles.create(language);
        } catch (Throwable t) {
            return 0;
        }
    }
}
```

Both `@GenerateHotSpotToNativeBridge` and `@GenerateNativeToHotSpotBridge` require `jniConfig` attribute set to a class having an accessible static method `getInstance` returning `JNIConfig` instance. The `attachIsolateThread`, `detachIsolateThread` and `tearDownIsolate` native methods correspond to [CEntryPoint.Builtin](https://www.graalvm.org/truffle/javadoc/org/graalvm/nativeimage/c/function/CEntryPoint.Builtin.html)s. The `releaseHandle` native method is executed when a `NativeObject` becomes weakly reachable and the corresponding object in the native image heap should be freed.
