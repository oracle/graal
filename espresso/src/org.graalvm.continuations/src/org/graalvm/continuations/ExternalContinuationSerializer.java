package org.graalvm.continuations;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.function.Consumer;

/**
 * This class provides methods to cooperate with non-jdk serialization frameworks.
 *
 * <p>
 * This class handles serialization of all continuation-related classes. These classes are
 * {@link Continuation}, and {@link SuspendCapability}.
 */
public final class ExternalContinuationSerializer {
    private ExternalContinuationSerializer() {
    }

    /**
     * Serialize the given continuation-related to the provided {@link ObjectOutput}.
     *
     * <p>
     * This method may be used to better cooperate with non-jdk serialization frameworks.
     */
    public static <T extends ContinuationExternalSerializable> void writeObjectExternal(T object, ObjectOutput out) throws IOException {
        object.writeObjectExternal(out);
    }

    /**
     * Deserialize a continuation-related object from the provided {@link ObjectInput}.
     *
     * <p>
     * This method is provided to cooperate with non-jdk serialization frameworks.
     *
     * @param type The type of the object to deserialize. Can be either {@link Continuation} or
     *            {@link SuspendCapability}.
     * @param loader The class loader that will be used to deserialize the stack record. If
     *            {@code null}, the {@link Thread#getContextClassLoader() current thread context
     *            class loader} will be used.
     * @param registerFreshObject A callback to the serialization framework. Intended to be used to
     *            register the in-construction object, so the framework may handle object graph
     *            cycles.
     */
    @SuppressWarnings("unchecked")
    public static <T extends ContinuationExternalSerializable> T readObjectExternal(Class<T> type, ObjectInput in, ClassLoader loader, Consumer<T> registerFreshObject)
                    throws IOException, ClassNotFoundException {
        if (Continuation.class.isAssignableFrom(type)) {
            return (T) ContinuationImpl.readObjectExternal(in, loader, (Consumer<Continuation>) registerFreshObject);
        } else if (SuspendCapability.class.isAssignableFrom(type)) {
            return (T) SuspendCapability.readObjectExternal(in, (Consumer<SuspendCapability>) registerFreshObject);
        } else {
            throw new FormatVersionException("Could not deserialize a continuation object.");
        }
    }
}
