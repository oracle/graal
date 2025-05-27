## Native Bridge Annotation Processor

The Native Bridge annotation processor generates code to simplify communication between code executing in disjoint JVM runtimes (e.g., Native Image and HotSpot, or across different processes).
It generates stubs for calling foreign methods using a JNI interface or UNIX domain sockets.

The processor can bridge:

* Interfaces
* Classes
* Classes with custom dispatch

Each generated stub bridges a single class or interface. Supported data types include:

* Java primitive types
* `String`
* Foreign reference types
* Arrays of primitive types, foreign reference types, and custom types
* Custom types are supported via user-defined marshallers.

The processor recognizes the following annotations:

* [`@GenerateHotSpotToNativeBridge`](https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativebridge/src/org/graalvm/nativebridge/GenerateHotSpotToNativeBridge.java)
* [`@GenerateNativeToNativeBridge`](https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativebridge/src/org/graalvm/nativebridge/GenerateNativeToNativeBridge.java)
* [`@GenerateNativeToHotSpotBridge`](https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativebridge/src/org/graalvm/nativebridge/GenerateNativeToHotSpotBridge.java)
* [`@GenerateProcessToProcessBridge`](https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativebridge/src/org/graalvm/nativebridge/GenerateProcessToProcessBridge.java)

To use bridge annotations, isolate entry points must be generated using the corresponding factory annotations:

* [`@GenerateHotSpotToNativeFactory`](https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativebridge/src/org/graalvm/nativebridge/GenerateHotSpotToNativeFactory.java)
* [`@GenerateNativeToNativeFactory`](https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativebridge/src/org/graalvm/nativebridge/GenerateNativeToNativeFactory.java)
* [`@GenerateProcessToProcessFactory`](https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativebridge/src/org/graalvm/nativebridge/GenerateProcessToProcessFactory.java)

### Generating Isolate Entry Points

The annotation processor generates code for spawning isolates and initializing a service within them. Supported factory annotations include:

* `@GenerateHotSpotToNativeFactory`: Spawns a Native Image isolate from a HotSpot VM host
* `@GenerateNativeToNativeFactory`: Spawns a Native Image isolate from a Native Image host
* `@GenerateProcessToProcessFactory`: Spawns an isolate in a separate OS process

The `marshallers` attribute in these annotations specifies the marshaller configuration for custom types. For more information,
see the [Custom Types](#custom-types) section later in this document.

**Example:**

```java
@GenerateHotSpotToNativeFactory(marshallers = CalculatorMarshallerConfig.class, initialService = ForeignCalculator.class)
@GenerateNativeToNativeFactory(marshallers = CalculatorMarshallerConfig.class, initialService = ForeignCalculator.class)
@GenerateProcessToProcessFactory(marshallers = CalculatorMarshallerConfig.class, initialService = ForeignCalculator.class)
final class ForeignCalculatorFactory {}
```

This generates a `ForeignCalculatorFactoryGen` class with methods:

* `ForeignCalculator create(NativeIsolateConfig config)`: Spawn a Native Image isolate returning foreing reference to `Calculator` implementation allocated in the Native Image heap.
* `ForeignCalculator create(ProcessIsolateConfig config)`: Spawn an isolate in s sparate OS process returning foreing reference to `Calculator` implementation allocated in the separate process.
* `void listen(ProcessIsolateConfig isolateConfig)`: An entry point for an external process isolate.

### Bridging an Interface

Given the interface:

```java
interface Calculator {
    int add(int a, int b);
    int sub(int a, int b);
}
```

Create an abstract base class implementing both the interface and `ForeignObject`, and annotate it with the bridge annotations. The `factory` attribute refers to the factory definition class providing the configuration such as custom type marshallers. For initial services, set the `implementation` attribute to a class that should be instantiated in the isolate as an initial service. For non-initial bridge definitions, leave the implementation attribute unspecified.

```java
@GenerateHotSpotToNativeBridge(factory = ForeignCalculatorFactory.class, implementation = CalculatorImpl.class)
@GenerateNativeToNativeBridge(factory = ForeignCalculatorFactory.class, implementation = CalculatorImpl.class)
@GenerateNativeToHotSpotBridge(factory = ForeignCalculatorFactory.class)
@GenerateProcessToProcessBridge(factory = ForeignCalculatorFactory.class, implementation = CalculatorImpl.class)
abstract class ForeignCalculator implements ForeignObject, Calculator {}
```

The annotation processor generates `ForeignCalculator` implementations that forwards `add` and `sub` methods to the foreign object.
The reference to the foreign calculator is obtained using the `ForeignCalculatorFactoryGen#create` methods. If only a specific type of isolate delegation is required, the definition class can be annotated with the corresponding annotation. For example, to generate a `ForeignCalculator` implementation solely for HotSpot to Native Image isolate delegation, use:

```java
@GenerateHotSpotToNativeBridge(factory = ForeignCalculatorFactory.class, implementation = CalculatorImpl.class)
abstract class ForeignCalculator implements ForeignObject, Calculator {}
```

**Example Usage (Native Image):**

```java
NativeIsolateConfig config = NativeIsolateConfig.newBuilder(isolateLibrary).build();
ForeignCalculator calculator = ForeignCalculatorFactoryGen.create(config);
calculator.add(calculator.sub(41, 2), 1);
```

**Example Usage (Separate Process):**

```java
Builder builder = ProcessIsolateConfig.newInitiatorBuilder(isolateLauncher, hostSocket);
builder.launcherArgument(isolateLibrary);
builder.launcherArgument(hostSocket);
ProcessIsolateConfig config = builder.build();
ForeignCalculator calculator = ForeignCalculatorFactoryGen.create(config);
calculator.add(calculator.sub(41, 2), 1);
```

The lifetime of a foreign object referenced by `calculator` is bound to the `calculator` reference in the hosting VM. Once the `calculator` reference becomes unreachable and is garbage collected, the framework automatically releases the underlying foreign object in the foreign isolate (either native image or external process). Disposal is handled by cleaners associated with the generated `ForeignCalculator` implementations. This ensures that memory and other resources allocated in the foreign isolate are properly reclaimed, preventing resource leaks across runtime boundaries.

### Bridging a Class

Bridging classes works similarly. Extend the class, implement `ForeignObject`, and annotate accordingly.

```java
class Calculator {
    public int add(int a, int b) { return a + b; }
    public int sub(int a, int b) { return a - b; }
}

@GenerateHotSpotToNativeBridge(factory = ForeignCalculatorFactory.class, implementation = CalculatorImpl.class)
@GenerateNativeToNativeBridge(factory = ForeignCalculatorFactory.class, implementation = CalculatorImpl.class)
@GenerateNativeToHotSpotBridge(factory = ForeignCalculatorFactory.class)
@GenerateProcessToProcessBridge(factory = ForeignCalculatorFactory.class, implementation = CalculatorImpl.class)
abstract class ForeignCalculator extends Calculator implements ForeignObject {}
```

Obtaining a reference is the same as with interfaces, using the `ForeignCalculatorFactoryGen#create` methods.


### Bridging a Class with a Custom Dispatch

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

To generate a bridge for a dispatch class, create an abstract base class extending `AbstractLanguageDispatch` and annotate it with the appropriate bridge annotations. This subclass must also declare static accessor methods annotated with:
 * `@CustomDispatchAccessor`: Retrieves the dispatch instance from the custom dispatch class.
 * `@CustomReceiverAccessor`: Retrieves the receiver instance from the custom dispatch class.
 * `@CustomDispatchFactory` : Constructs a new custom dispatch class instance using a given receiver.

```java
@GenerateHotSpotToNativeBridge(factory = ForeignServiceFactory.class)
@GenerateNativeToNativeBridge(factory = ForeignServiceFactory.class)
@GenerateNativeToHotSpotBridge(factory = ForeignServiceFactory.class)
@GenerateProcessToProcessBridge(factory = ForeignServiceFactory.class)
abstract class ForeignLanguageDispatch extends AbstractLanguageDispatch {

    private static final ClassValue<ForeignLanguageDispatch> DISPATCH = new ClassValue<>() {
        @Override
        protected ForeignLanguageDispatch computeValue(Class<?> type) {
            if (Peer.class.isAssignableFrom(type)) {
                return ForeignLanguageDispatchGen.create((Class<? extends Peer>) type);
            } else {
                throw new IllegalArgumentException(type.getName());
            }
        }
    };

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
        Peer peer = ((ForeignObject) receiver).getPeer();
        return new Language(DISPATCH.get(peer.getClass()), receiver);
    }
}
```

The annotation processor generates the `ForeignLanguageDispatchGen` class with a static `create(Class<? extends Peer> peerType)` factory method. This method returns a `ForeignLanguageDispatch` instance that forwards dispatch calls to the foreign isolate using the communication model implied by `peerType`.

### Excluding Methods from Generation

Sometimes it's necessary to exclude a method from being bridged. This can be done by overriding a method in the annotated class and making it `final`. The following example excludes `sub` method from being bridged.

```java
@GenerateHotSpotToNativeBridge(factory = ForeignCalculatorFactory.class, implementation = CalculatorImpl.class)
@GenerateNativeToNativeBridge(factory = ForeignCalculatorFactory.class, implementation = CalculatorImpl.class)
@GenerateNativeToHotSpotBridge(factory = ForeignCalculatorFactory.class)
@GenerateProcessToProcessBridge(factory = ForeignCalculatorFactory.class, implementation = CalculatorImpl.class)
abstract class ForeignCalculator extends Calculator implements ForeignObject {

    @Override
    public final int sub(int a, int b) {
        return super.sub(a, b);
    }
}
```

### Foreign Reference Types
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

To marshall the `Compilation` as a foreign object reference, annotate the use of `Compilation` with the `@ByRemoteReference` annotation. The processor takes care of reference marshaling.

```java
@GenerateHotSpotToNativeBridge(factory = ForeignCompilerFactory.class)
@GenerateNativeToNativeBridge(factory = ForeignCompilerFactory.class)
@GenerateNativeToHotSpotBridge(factory = ForeignCompilerFactory.class)
@GenerateProcessToProcessBridge(factory = ForeignCompilerFactory.class)
abstract class ForeignCompilation implements ForeignObject, Compilation {
}

@GenerateHotSpotToNativeBridge(factory = ForeignCompilerFactory.class, implementation = CompilerImpl.class)
@GenerateNativeToNativeBridge(factory = ForeignCompilerFactory.class, implementation = CompilerImpl.class)
@GenerateNativeToHotSpotBridge(factory = ForeignCompilerFactory.class)
@GenerateProcessToProcessBridge(factory = ForeignCompilerFactory.class, implementation = CompilerImpl.class)
abstract class ForeignCompiler implements ForeignObject, Compiler {

    @Override
    @ByRemoteReference(ForeignCompilation.class)
    public abstract Compilation openCompilation();

    @Override
    public abstract byte[] compile(@ByRemoteReference(ForeignCompilation.class) Compilation compilation, byte[] source);
}


@GenerateHotSpotToNativeFactory(marshallers = ForeignCalculatorFactory.class, initialService = ForeignCompiler.class)
@GenerateNativeToNativeFactory(marshallers = ForeignCalculatorFactory.class, initialService = ForeignCompiler.class)
@GenerateProcessToProcessFactory(marshallers = ForeignCalculatorFactory.class, initialService = ForeignCompiler.class)
final class ForeignCalculatorFactory {

    private static final MarshallerConfig INSTANCE = MarshallerConfig.newBuilder().build();

    static MarshallerConfig getInstance() {
        return INSTANCE;
    }
}
```
To pass a local object reference instead of unbox foreign object handle, use the `@ByLocalReference` annotation.
More foreign reference examples can be found in the [bridge processor tests](https://github.com/oracle/graal/tree/master/sdk/src/org.graalvm.nativebridge.processor.test/src/org/graalvm/nativebridge/processor/test/references/).

### Arrays

Arrays of primitive types, foreign reference types, and custom types are directly supported by the annotation processor. The array method parameters are by default treated as `in` parameters, meaning the content of the array is copied to the called method. Sometimes it is needed to change this behavior and treat the array parameter as an `out` parameter. This can be done using the `@Out` annotation. The following example bridges a `read` method with an `out` array parameter.

```java
interface Reader {
    int read(byte[] buffer, int offset, int length);
}

@GenerateHotSpotToNativeBridge(factory = ForeignServicesFactory.class)
abstract class ForeignReader implements ForeignObject, Reader {
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

To bridge arrays of foreign object references, annotate the array with `@ByRemoteReference` or `@ByLocalReference` depending on locality.
The following example bridges a `register` method taking an array of local object references.

```java
interface EventConsumer {
    void consumeEvent(String event);
}

interface EventSource {
    void register(EventConsumer[] consumers);
}

@GenerateNativeToHotSpotBridge(factory = ForeignServicesFactory.class)
@GenerateProcessToProcessBridge(factory = ForeignServicesFactory.class)
abstract class ForeignEventConsumer implements ForeignObject, EventConsumer {
}

@GenerateHotSpotToNativeBridge(factory = ForeignServicesFactory.class)
@GenerateProcessToProcessBridge(factory = ForeignServicesFactory.class)
abstract class ForeignEventSource implements ForeignObject, EventSource {

    @Override
    public abstract void register(@ByLocalReference(ForeignEventConsumer.class) EventConsumer[] consumers);
}
```

More foreign references array examples can be found in the [bridge processor tests](https://github.com/oracle/graal/tree/master/sdk/src/org.graalvm.nativebridge.processor.test/src/org/graalvm/nativebridge/processor/test/references/).


### Custom Types

The annotation processor supports Java primitive types, `String`, arrays of primitive types, and foreign references. For other types passed by value, users must register `BinaryMarshaller` instances responsible for marshalling/unmarshalling the types to/from byte streams. Arrays of custom types are supported by a marshaller for the array component type, and the annotation processor automatically generates a loop for marshalling and unmarshalling the array. These marshallers are registered using `MarshallerConfig.Builder`. The class that provides `MarshallerConfig` must expose it via a static `getInstance()` method and be referenced in the factory annotations. (See [Generating Isolate Entry Points](#generating-isolate-entry-points).)

**Example: Registering a Marshaller for `ComplexNumber`:**

```java
record ComplexNumber(int re, int img);

final class ComplexNumberMarshaller implements BinaryMarshaller<ComplexNumber> {

    @Override
    public void write(BinaryOutput out, ComplexNumber complex) {
        out.writeInt(complex.re());
        out.writeInt(complex.img());
    }

    @Override
    public ComplexNumber read(Isolate<?> isolate, BinaryInput in) {
        int re = in.readInt();
        int img = in.readInt();
        return new ComplexNumber(re, img);
    }

    @Override
    public int inferSize(ComplexNumber object) {
        return 2 * Integer.BYTES;
    }
}

final class CalculatorMarshallerConfig {

    private static final MarshallerConfig INSTANCE = createConfig();

    private CalculatorMarshallerConfig() {}

    static MarshallerConfig getInstance() {
        return INSTANCE;
    }

    private static MarshallerConfig createConfig() {
        return MarshallerConfig.newBuilder()
            .registerMarshaller(ComplexNumber.class, new ComplexNumberMarshaller())
            .build();
    }
}
```


The annotation processor also supports `out` parameters of custom types. To support `out` parameters, the marshaller must also implement the `writeUpdate`, `readUpdate`, and `inferUpdateSize` methods. The following example bridges a `split` method with two `out` parameters of type `List<Integer>`.

```java
interface Numbers {
    void split(int pivot, List<Integer> numbers, List<Integer> belowOrEqual, List<Integer> above);
}

@GenerateHotSpotToNativeBridge(factory = ForeignNmbersFactory.class, implementation = NumbersImpl.class)
abstract class ForeignNumbers implements ForeignObject, Numbers {
    public abstract void split(int pivot, List<Integer> numbers, @Out List<Integer> belowOrEqual, @Out List<Integer> above);
}
```

The `List<Integer>` type is unknown to the annotation processor. A marshaller must be provided to convert it into a serial form and recreate it from a serial form. The marshaller must also implement the `out` parameter methods.

```java
final class IntListMarshaller implements BinaryMarshaller<List<Integer>> {
    @Override
    public List<Integer> read(BinaryInput input) {
        int len = input.readInt();
        List<Integer> result = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
            result.add(input.readInt());
        }
        return result;
    }

    @Override
    public void write(BinaryOutput output, List<Integer> object) {
        output.writeInt(object.size());
        for (Integer i : object) {
            output.writeInt(i);
        }
    }

    @Override
    public int inferSize(List<Integer> object) {
        return Integer.BYTES + Integer.BYTES * object.size();
    }

    @Override
    public void writeUpdate(BinaryOutput output, List<Integer> object) {
        write(output, object);
    }

    @Override
    public void readUpdate(BinaryInput input, List<Integer> object) {
        object.clear();
        int len = input.readInt();
        for (int i = 0; i < len; i++) {
            object.add(input.readInt());
        }
    }

    @Override
    public int inferUpdateSize(List<Integer> object) {
        return inferSize(object);
    }
}
```