package org.graalvm.continuations;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serial;
import java.io.Serializable;
import java.util.function.Consumer;

/**
 * An object provided by the system that lets you yield control and return from
 * {@link Continuation#resume()}.
 */
public final class SuspendCapability implements Serializable {
    @Serial private static final long serialVersionUID = 4790341975992263909L;

    private ContinuationImpl continuation;

    // region API

    static SuspendCapability create(ContinuationImpl continuation) {
        return new SuspendCapability(continuation);
    }

    /**
     * Suspends the continuation, unwinding the stack to the point at which it was previously
     * resumed.
     *
     * <p>
     * If successful, execution will continue at after the call to {@link Continuation#resume()}.
     *
     * @throws IllegalContinuationStateException if trying to suspend outside a continuation, or if
     *             there are native frames on the stack, or if the thread is inside a synchronized
     *             block.
     */
    public void suspend() {
        continuation.trySuspend();
    }

    /**
     * Serialize this suspend capability to the provided {@link ObjectOutput}.
     *
     * <p>
     * This method may be used to better cooperate with non-jdk serialization frameworks.
     */
    public void writeObjectExternal(ObjectOutput out) throws IOException {
        out.writeObject(continuation);
    }

    /**
     * Deserialize a suspend capability from the provided {@link ObjectInput}.
     *
     * <p>
     * This method is provided to cooperate with non-jdk serialization frameworks.
     *
     * <p>
     * {@code registerFreshObject} will be called once a fresh suspend capability has been created,
     * but before it has been fully deserialized. It is intended to be a callback to the
     * serialization framework, so it can register the in-construction suspend capability so the
     * framework may handle object graph cycles.
     */
    public static SuspendCapability readObjectExternal(ObjectInput in, Consumer<Object> registerFreshObject) throws IOException, ClassNotFoundException {
        if (!Continuation.isSupported()) {
            throw new UnsupportedOperationException("This VM does not support continuations.");
        }
        SuspendCapability suspend = new SuspendCapability();
        registerFreshObject.accept(suspend);
        Object obj = in.readObject();
        if (obj instanceof ContinuationImpl) {
            suspend.continuation = (ContinuationImpl) obj;
            return suspend;
        }
        throw new FormatVersionException("Erroneous deserialization of SuspendCapability.\n" +
                        "read object was not a continuation:" + obj);
    }

    // endregion API

    // region internals

    private SuspendCapability(ContinuationImpl continuation) {
        this.continuation = continuation;
    }

    private SuspendCapability() {
    }

    // endregion internals
}
