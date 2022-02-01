## Native Bridge Annotation Processor

The native bridge annotation processor generates stubs and skeletons for HotSpot - native image interoperability using Java JNI interface. It supports both HotSpot to native and native to HotSpot calls. Both interfaces and classes with an implicit or explicit receiver can be bridged. The supported data types are Java primitive types, `String`, arrays of primitive types, reference types. Support for custom types can be added using marshallers registered by `JNIConfig.Builder`.

### Basic usage

The processor is activated by `@GenerateHotSpotToNativeBridge` or `@GenerateNativeToHotSpotBridge` annotation. The first step is to define an interface that should be bridged. In a single stub, the annotation processor can bridge either a single class or a single interface. In the following example, we generate both HotSpot to native image and native image to HotSpot bridges for a `Calculator` interface.

```java
interface Calculator {
    int add(int a, int b);
    int sub(int a, int b);
}
```

To generate a HotSpot to native image bridge we need to create an abstract base class extending `NativeObject`, implementing the bridged interface `Calculator` and annotate it by `@GenerateHotSpotToNativeBridge`.

```java
@GenerateHotSpotToNativeBridge(jniConfig = ExampleJNIConfig.class)
abstract class NativeCalculator extends NativeObject implements Calculator {

    NativeCalculator(NativeIsolate isolate, long handle) {
        super(isolate, handle);
    }
}
```

Annotation processor generates `NativeCalculatorGen` class with a static `createHotSpotToNative(NativeIsolate isolate, long handle)` factory method creating a `NativeCalculator` instance forwarding `add` and `sub` operations to an object in the native image.

```java
long isolateThreadId = createIsolate();
long isolateId = getIsolateId(isolateThreadId);
long calculatorHandle = createCalculator(isolateThreadId);
NativeIsolate isolate = NativeIsolate.forIsolateId(isolateId, ExampleJNIConfig.getInstance());
NativeCalculator calculator = NativeCalculatorGen.createHotSpotToNative(isolate, calculatorHandle);
calculator.add(calculator.sub(41, 2), 1);
```

Generating native image to HotSpot bridge is very similar. We need to create an abstract base class extending `HSObject`, implementing the bridged interface `Calculator` and annotate it by `@GenerateNativeToHotSpotBridge`.

```java
@GenerateNativeToHotSpotBridge(jniConfig = ExampleJNIConfig.class)
abstract class HSCalculator extends HSObject implements Calculator {

    HSCalculator(JNI.JNIEnv jniEnv, JNI.JObject handle) {
        super(jniEnv, handle);
    }
}
```

Annotation processor generates `HSCalculatorGen` class with a static `createNativeToHotSpot(JNIEnv jniEnv, JObject handle)` factory method creating a `HSCalculator` instance forwarding `add` and `sub` operations to an object in HotSpot.

When it's not possible to use an interface to define operations to bridge the annotation processor also supports base classes. This can be useful if you need to bridge an existing JDK or library type. For classes, all public non-static non-final non-native methods which are not inherited from `java.lang.Object` are bridged. In the following example, we generate bridges for `Calculator` class.

```java
class Calculator {
    public int add(int a, int b) { return a + b; }
    public int sub(int a, int b) { return a - b; }
}
```

To generate a HotSpot to native image bridge we need to create an abstract base class extending `Calculator`. The class must have a package-private field of `NativeObject` type annotated with `@EndPointHandle`. Finally, we need to annotate the class by `@GenerateHotSpotToNativeBridge`.

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

Annotation processor generates `NativeCalculatorGen` class with a static `createHotSpotToNative(NativeObject delegate)` factory method creating a `NativeCalculator` instance forwarding `add` and `sub` operations to an object in the native image.

```java
long isolateThreadId = createIsolate();
long isolateId = getIsolateId(isolateThreadId);
long calculatorHandle = createCalculator(isolateThreadId);
NativeIsolate isolate = NativeIsolate.forIsolateId(isolateId, ExampleJNIConfig.getInstance());
NativeCalculator calculator = NativeCalculatorGen.createHotSpotToNative(new NativeObject(isolate, calculatorHandle));
calculator.add(calculator.sub(41, 2), 1);
```

To generate a native image bridge to HotSpot we need to create an abstract base class extending `Calculator`. The class must have a package-private field of `HSObject` type annotated with `@EndPointHandle`. Finally, we need to annotate the class by `@GenerateNativeToHotSpotBridge`.

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

Annotation processor generates `HSCalculatorGen` class with a static `ccreateNativeToHotSpot(HSObject delegate, JNIEnv jniEnv)` factory method creating a `HSCalculator` instance forwarding `add` and `sub` operations to an object in HotSpot.

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

### JNIConfig

The `JNIConfig` class is used to configure generated classes and the `nativebridge` framework. It configures callbacks to attach/detach thread to isolate, tear down an isolate and clean up objects in the native image heap. In addition, it's also used by generated classes to look up marshallers for custom types that are not directly supported by the annotation processor. In the following example, we create a `JNIConfig` registering needed callbacks and marshallers for the `Complex` number.

```java
public final class ExampleJNIConfig {

    private static final JNIConfig INSTANCE = createJNIConfig();
    
    public static JNIConfig getInstance() {
        return INSTANCE;
    }

    private static JNIConfig createJNIConfig() {
        JNIConfig.Builder builder = JNIConfig.newBuilder();
        if (ImageInfo.inImageCode()) {
            builder.registerNativeMarshaller(Complex.class, new ComplexNativeMarshaller());
        } else {
            builder.registerHotSpotMarshaller(Complex.class, new ComplexHotSpotMarshaller());
            builder.setAttachThreadAction(ExampleJNIConfig::attachThread);
            builder.setDetachThreadAction(ExampleJNIConfig::detachThread);
            builder.setShutDownIsolateAction(ExampleJNIConfig::tearDownIsolate);
            builder.setReleaseNativeObjectAction(ExampleJNIConfig::releaseHandle);
        }
        return builder.build();
    }

    private static native long attachIsolateThread(long isolate);
    private static native int detachIsolateThread(long isolateThread);
    private static native int tearDownIsolate(long isolateThread);
    private static native long releaseHandle(long isolateThread, long handle);

    @CEntryPoint(name = "Java_ExampleJNIConfig_attachIsolateThread", builtin = CEntryPoint.Builtin.ATTACH_THREAD, include = Enabled.class)
    static native IsolateThread attachIsolateThread(JNIEnv jniEnv, JClass clazz, Isolate isolate);

    @CEntryPoint(name = "Java_ExampleJNIConfig_detachIsolateThread", builtin = CEntryPoint.Builtin.DETACH_THREAD, include = Enabled.class)
    static native int detachIsolateThread(JNIEnv jniEnv, JClass clazz, IsolateThread isolateThread);

    @CEntryPoint(name = "Java_ExampleJNIConfig_tearDownIsolate", builtin = CEntryPoint.Builtin.TEAR_DOWN_ISOLATE , include = Enabled.class)
    static native int tearDownIsolate(JNIEnv jniEnv, JClass clazz, IsolateThread isolateThread);

    @CEntryPoint(name = "Java_ExampleJNIConfig_releaseHandle", include = Enabled.class)
    static long releaseHandle(JNIEnv jniEnv, JClass clazz, @CEntryPoint.IsolateThreadContext long isolateId, long objectHandle) {
        try {
            NativeObjectHandles.remove(objectHandle);
            return 0;
        } catch (Throwable t) {
            return -1;
        }
    }
}
```

Both `@GenerateHotSpotToNativeBridge` and `@GenerateNativeToHotSpotBridge` require `jniConfig` attribute set to a class having an accessible static method `getInstance` returning `JNIConfig` instance. The `attachIsolateThread`, `detachIsolateThread` and `tearDownIsolate` native methods correspond to [CEntryPoint.Builtin](https://www.graalvm.org/truffle/javadoc/org/graalvm/nativeimage/c/function/CEntryPoint.Builtin.html)s. The `releaseHandle` native method is executed when `NativeObject` for passed handle becomes weakly reachable and the corresponding object in the native image heap should be freed.

### Custom Marshallers

The annotation processor supports Java primitive types, `String`, arrays of primitive types, and references. For other types, a marshaller must be registered in the `JNIConfig`. In the following example, we will change the `Calculator` interface to work with custom complex numbers.

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

The `Complex` type is unknown to the annotation processor. We need to provide marshallers to convert it into a serial form and recreate it from a serial form. For simplicity, we will use `DataOutputStream` and `DataInputStream` for serial form conversion, but more efficient serialization can be used.

```java
final class ComplexHotSpotToNativeMarshaller implements JNIHotSpotMarshaller<Complex> {

        @Override
        public Object marshall(Complex complex) {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bout)) {
                out.writeInt(complex.re);
                out.writeInt(complex.img);
            } catch (IOException ioe) {
                throw new RuntimeException("Unexpected IOException", ioe);
            }
            return bout.toByteArray();
        }

        @Override
        public Complex unmarshall(Object rawObject) {
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream((byte[])rawObject))) {
                int re = in.readInt();
                int img = in.readInt();
                return new Complex(re, img);
            } catch (IOException ioe) {
                throw new RuntimeException("Unexpected IOException", ioe);
            }
        }
    }

    final class ComplexNativeToHotSpotMarshaller implements JNINativeMarshaller<Complex> {

        @Override
        public JNI.JObject marshall(JNI.JNIEnv env, Complex complex) {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bout)) {
                out.writeInt(complex.re);
                out.writeInt(complex.img);
            } catch (IOException ioe) {
                throw new RuntimeException("Unexpected IOException", ioe);
            }
            return JNIUtil.createHSArray(env, bout.toByteArray());
        }

        @Override
        public Complex unmarshall(JNI.JNIEnv env, JNI.JObject jObject) {
            byte[] rawObject = JNIUtil.createArray(env, (JNI.JByteArray) jObject);
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(rawObject))) {
                int re = in.readInt();
                int img = in.readInt();
                return new Complex(re, img);
            } catch (IOException ioe) {
                throw new RuntimeException("Unexpected IOException", ioe);
            }
        }
    }
```

Finally, marshallers need to be registered in the `JNIConfig`, see [JNIConfig](#JNIConfig).

### Reference types

Reference types can be used when a bridged type returns a reference to another bridged type. Reference types are fully optional and can be replaced by custom marshallers. Following example bridges `Compilation` and `Compiler` interfaces. The `Compiler` interface uses `Compilation` type both as return type and parameter type.

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

Instead of creating and registering a custom marshaller for `Compilation` we annotate `Compilation` usages by `@ByReference` annotation. The annotation processor takes care of reference marshaling.

```java
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

### Arrays

Arrays of primitive types are directly supported by the annotation processor. The array method parameters are by default treated as `in` parameters, the content of the array is copied to the called method. Sometimes it's needed to change this behavior and treat the array parameter as `out` parameter. This can be done using an `@Out` annotation. The following example bridges a `read` method with `out` array parameter.

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

The `@Out` annotation can be combined with `@In` annotation for `in-out` array parameters. For `in-out` array parameters the array content is copied both into the called method and out of the called method.

```java
@Override
public abstract int read(@In @Out byte[] b, int off, int len);
```
