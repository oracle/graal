---
layout: docs
toc_group: espresso
link_title: Continuation API with Serialization
permalink: /reference-manual/espresso/continuations/serialization/
---

# Serialization of Continuations

See below a Java application using the [Continuation API](continuations.md) with serialization.
This application persists its state by saving it to a file. Each time it runs, the program increments a counter, displays the updated value, and then exits.

```java
package com.oracle.truffle.espresso.test.continuations;

import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.graalvm.continuations.Continuation;
import org.graalvm.continuations.ContinuationEntryPoint;
import org.graalvm.continuations.SuspendCapability;

/**
 * Application that persists its state by saving it to a file.
 * <p>
 * Each time the program is run, it increments the counter and prints out the new value before quitting.
 * <p>
 * By default, the state is persisted to a file named "state.serial.bin" in the current working directory,
 * but it can be changed by specifying a new path with the {@code "-p <path>"} option.
 * <p>
 * By default, standard Java serialization is used, but "Kryo" can be selected with the {@code "-s kryo"} option.
 * <p>
 * The continuation payload must implement `ContinuationEntryPoint`.
 * This class is also `Serializable` to work with Java serialization.
 */
public class PersistentApp implements ContinuationEntryPoint, Serializable {
    /**
     * An interface for serializing/deserializing a continuation to the file system.
     * Two implementations are showcased later: one for `Java` and one for `Kryo`.
     */
    public interface MySerializer {
        Continuation load(Path storagePath) throws IOException, ClassNotFoundException;

        void saveTo(Continuation continuation, Path storagePath) throws IOException;
    }

    private static final String DEFAULT_PATH = "state.serial.bin";

    int counter = 0;

    /**
     * Anything reachable from the stack in this method is persisted, including 'this'.
     * <p>
     * Suspending a continuation requires access to this “suspend capability” object.
     * By controlling who gets access to it, you can work out where a suspension might occur.
     * If you do not want this, the capability can be stored it in a static `ThreadLocal` and let anything suspend.
     */
    @Override
    public void start(SuspendCapability suspendCapability) {
        while (true) {
            counter++;
            System.out.println("The counter value is now " + counter);

            doWork(suspendCapability);
        }
    }

    private static void doWork(SuspendCapability suspendCapability) {
        // Do something ...
        /*
         * The call to `suspend` causes control flow to return from the call to resume below.
         * The state of the application will be written to the file system and it will carry on when the user starts the application again.
         */
        suspendCapability.suspend();
        // Do something else ...
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        checkSupported();

        Path storagePath = getStoragePath(args);
        MySerializer ser = getSerializer(args);

        Continuation continuation = loadOrInit(storagePath, ser);
        /*
         * Control flow will either begin at `start` for the first program execution,
         * or jump to after the call to `suspend` above for later executions.
         */
        continuation.resume();
        ser.saveTo(continuation, storagePath);
    }

    private static void checkSupported() {
        try {
            if (!Continuation.isSupported()) {
                System.err.println("Ensure you are running on an Espresso VM with the flags '--experimental-options --java.Continuum=true'.");
                System.exit(1);
            }
        } catch (NoClassDefFoundError e) {
            System.err.println("Please make sure you are using a VM that supports the Continuation API");
            System.exit(1);
        }
    }

    /////////////////////////////////////////////////////////////
    // Code to load, save and resume the state of the program. //
    /////////////////////////////////////////////////////////////

    private static Path getStoragePath(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String s = args[i];
            if (s.equals("-p") && (args.length > i + 1)) {
                return Paths.get(args[i + 1]);
            }
        }

        return Paths.get(DEFAULT_PATH);
    }

    private static Continuation loadOrInit(Path storagePath, MySerializer ser) throws IOException, ClassNotFoundException {
        Continuation continuation;
        if (!Files.exists(storagePath)) {
            /*
             * First execution of the program with the specified path: use a fresh continuation.
             */
            continuation = Continuation.create(new PersistentApp());
        } else {
            /*
             * Program had been executed at least once with the specified path: restore the continuation from file.
             */
            continuation = ser.load(storagePath);
        }
        return continuation;
    }

    private static MySerializer getSerializer(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String s = args[i];
            if (s.equals("-s") && (args.length > i + 1)) {
                String key = args[i + 1];
                if (key.equals("java")) {
                    return new MyJavaSerializer();
                }
                if (key.equals("kryo")) {
                    return new MyKryoSerializer();
                }
            }
        }
        return new MyJavaSerializer();
    }
}
```

Note the `MySerializer` interface. We will be implementing that interface to showcase two different ways to serialize a continuation:

- One using the standard Java serialization (using the built-in `ObjectInputStream` and `ObjectOutputStream`);
- The other using the popular and fast [Kryo](https://github.com/EsotericSoftware/kryo) library.

## Java

Here is the implementation of `MySerializer` that uses standard Java serialization:

:warning: Java object serialization requires everything reachable from the captured stack to implement the `Serializable` interface, and (ideally) be whitelisted by a serialization filter.
This is less convenient than Kryo which will happily serialize anything.
The format is also more verbose than Kryo's, yielding continuations about double the size.
However, it avoids the need for a separate dependency.


```java
import static java.nio.file.StandardOpenOption.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.graalvm.continuations.Continuation;

class MyJavaSerializer implements PersistentApp.MySerializer {
    @Override
    public Continuation load(Path storagePath) throws IOException, ClassNotFoundException {
        try (var in = new ObjectInputStream(Files.newInputStream(storagePath, READ))) {
            return (Continuation) in.readObject();
        }
    }

    @Override
    public void saveTo(Continuation continuation, Path storagePath) throws IOException {
        // Will overwrite previously existing file if any.
        try (var out = new ObjectOutputStream(Files.newOutputStream(storagePath, CREATE, TRUNCATE_EXISTING, WRITE))) {
            out.writeObject(continuation);
        }
    }
}
```

## Kryo

Here is the implementation of `MySerializer` that uses the Kryo library:

```java
import static java.nio.file.StandardOpenOption.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.graalvm.continuations.Continuation;
import org.graalvm.continuations.ContinuationSerializable;
import org.objenesis.strategy.StdInstantiatorStrategy;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.KryoObjectInput;
import com.esotericsoftware.kryo.io.KryoObjectOutput;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy;

class MyKryoSerializer implements PersistentApp.MySerializer {
    // We set up the Kryo engine here.
    private static final Kryo kryo = setupKryo();

    private static Kryo setupKryo() {
        var kryo = new Kryo();
        // The heap will have cycles, and Kryo requires us to opt in to support for that.
        kryo.setReferences(true);
        // We do not want to manually register everything, as heap contents are dynamic.
        kryo.setRegistrationRequired(false);
        // Be able to create objects even if they lack a no-arg constructor.
        kryo.setInstantiatorStrategy(
                new DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
        /*
         * Register a custom serializer for continuation objects.
         * All serialization-relevant classes in the Continuation API will extend the `ContinuationSerializable` class.
         */
        kryo.addDefaultSerializer(ContinuationSerializable.class, new ContinuationSerializer());
        return kryo;
    }
    
    /**
     * A custom Kryo `Serializer` for continuation objects.
     */
    static class ContinuationSerializer extends Serializer<ContinuationSerializable> {
        public ContinuationSerializer() {
            super(false, false);
        }

        @Override
        public void write(Kryo kryo, Output output, ContinuationSerializable object) {
            try {
                ContinuationSerializable.writeObjectExternal(object, new KryoObjectOutput(kryo, output));
            } catch (IOException e) {
                throw new KryoException(e);
            }
        }

        @Override
        public ContinuationSerializable read(Kryo kryo, Input input, Class<? extends ContinuationSerializable> type) {
            try {
                /*
                 * The continuation deserialization mechanism will use this classloader to load the classes present on the heap.
                 * Kryo requires awareness of created objects in order to handle cycles in the serialized object graph.
                 * Let Kryo know about the deserialized objects using kryo::reference.
                 */
                return ContinuationSerializable.readObjectExternal(type, new KryoObjectInput(kryo, input),
                        kryo.getClassLoader(),
                        kryo::reference);
            } catch (IOException | ClassNotFoundException e) {
                throw new KryoException(e);
            }
        }
    }

    @Override
    public Continuation load(Path storagePath) throws IOException {
        try (var in = new Input(Files.newInputStream(storagePath, READ))) {
            return kryo.readObject(in, Continuation.class);
        }
    }

    @Override
    public void saveTo(Continuation continuation, Path storagePath) throws IOException {
        try (var out = new Output(Files.newOutputStream(storagePath, CREATE, TRUNCATE_EXISTING, WRITE))) {
            kryo.writeObject(out, continuation);
        }
    }
}
```

### Further Reading
* [Continuation API](continuations.md)

