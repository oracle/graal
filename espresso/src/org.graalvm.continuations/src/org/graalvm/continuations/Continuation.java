package org.graalvm.continuations;

import java.io.Serial;
import java.io.Serializable;

/**
 * A delimited one-shot continuation, which encapsulates a part of the program's execution such that
 * it can be resumed from the point at which it was suspended.
 *
 * <p>
 * Continuations allow you to mark a region of code in which the program can <i>suspend</i>, which
 * passes control flow up the stack to the point at which the continuation was <i>resumed</i>. This
 * implementation is low level and doesn't address common needs, such as passing objects in and out
 * of the continuation as it suspends and resumes.
 *
 * <p>
 * Continuations are not threads. When accessing thread locals they see the values that their
 * hosting thread would see. Continuations are also not thread safe.
 *
 * <p>
 * Exceptions thrown from the entry point propagate out of {@link #resume()} and then mark the
 * continuation as failed. Resuming the exception after that point will fail with
 * {@link IllegalContinuationStateException}. If you want to retry a failed continuation you must
 * have a clone from before the failure (see below).
 *
 * <h1>Serialization</h1>
 *
 * <p>
 * Continuations can be serialized to disk and resumed later in a separate process. Alternatively
 * they can be discarded and left for the GC to clean up.
 *
 * <p>
 * For a continuation to be serialized, the given {@link EntryPoint} itself must be serializable.
 *
 * <p>
 * Continuation deserialization is <b>not secure</b>. You should only deserialize continuations you
 * yourself suspended, as resuming a malicious continuation can cause arbitrary undefined behaviour,
 * i.e. is equivalent to handing control of the JVM to the attacker.
 *
 * <p>
 * This class implements <i>one shot</i> continuations, meaning you cannot restart a continuation
 * from the same suspend point more than once. For that reason this class is mutable, and the act of
 * calling {@code resume} changes its state. If you want to roll back and retry a resume operation
 * you should start by serializing the continuation, then trying to deserialize it to obtain a new
 * {@code Continuation} that you can resume again. This is required because the continuation may
 * have mutated the heap in arbitrary ways, and so resuming a continuation more than once could
 * cause extremely confusing and apparently 'impossible' states to occur.
 */
public abstract class Continuation implements Serializable {
    @Serial private static final long serialVersionUID = 4269758961568150833L;
    private static final boolean IS_SUPPORTED = supported();

    /**
     * Returns {@code true} if this VM supports the continuations feature, {@code false} otherwise.
     */
    public static boolean isSupported() {
        return IS_SUPPORTED;
    }

    /**
     * Creates a new suspended continuation, taking in an {@link EntryPoint}.
     *
     * <p>
     * To begin execution call the {@link Continuation#resume()} method. The entry point will be
     * passed a {@link SuspendCapability capability object} allowing it to suspend itself.
     *
     * <p>
     * The continuation will be serializable so long as the given {@link EntryPoint} is
     * serializable.
     *
     * @throws UnsupportedOperationException If this VM does not support continuation.
     */
    public static Continuation create(EntryPoint entryPoint) {
        if (!isSupported()) {
            throw new UnsupportedOperationException("This VM does not support continuations.");
        }
        return new ContinuationImpl(entryPoint);
    }

    /**
     * Returns {@code true} if this continuation is able to be {@link #resume() resumed}.
     * <p>
     * A continuation is resumable if it is freshly created, or if it has been resumed then
     * suspended.
     * <p>
     * A continuation that has {@link #isCompleted() finished executing} is not resumable.
     * <p>
     * A continuation that is being serialized will return {@code false}, until serialization
     * completes.
     */
    public abstract boolean isResumable();

    /**
     * Returns {@code true} if execution of this continuation has completed, whether because the
     * {@link EntryPoint} has returned normally, or if an uncaught exception has propagated out of
     * the {@link EntryPoint}.
     * <p>
     * A continuation that is being serialized will return {@code false}, until serialization
     * completes.
     */
    public abstract boolean isCompleted();

    /**
     * Runs the continuation until it either completes or calls {@link SuspendCapability#suspend()}.
     * A continuation may not be resumed if it's already {@link #isCompleted() completed}, nor if it
     * is already running.
     *
     * <p>
     * If an exception is thrown by the continuation and escapes the entry point, it will be
     * rethrown here. The continuation is then no longer usable and must be discarded.
     * 
     * @return {@code true} if the continuation was {@link SuspendCapability#suspend() suspended},
     *         or {@code false} if execution of the continuation has completed normally.
     * 
     * @throws IllegalContinuationStateException if the continuation is not {@link #isResumable()
     *             resumable}.
     * @throws IllegalMaterializedRecordException if the VM rejects the continuation. This can
     *             happen, for example, if a continuation was obtained by deserializing a malformed
     *             stream.
     *
     */
    public abstract boolean resume();

    // region internals

    private static boolean supported() {
        try {
            return isSupported0();
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    private static native boolean isSupported0();

    // endregion internals

    // Disallow subclassing outside this package.
    Continuation() {
    }
}
