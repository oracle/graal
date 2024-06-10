package org.graalvm.continuations;

import java.io.Serial;
import java.io.Serializable;

/**
 * An object provided by the system that lets you yield control and return from
 * {@link Continuation#resume()}.
 */
public final class SuspendCapability implements Serializable {
    @Serial private static final long serialVersionUID = 4790341975992263909L;

    private final ContinuationImpl continuation;

    // Do not expose SuspendCapability creation.
    SuspendCapability(ContinuationImpl continuation) {
        this.continuation = continuation;
    }

    /**
     * Suspends the continuation, unwinding the stack to the point at which it was previously
     * resumed.
     *
     * @throws IllegalContinuationStateException if trying to suspend outside a continuation, or if
     *             there are native frames on the stack, or if the thread is inside a synchronized
     *             block.
     */
    public void suspend() {
        continuation.trySuspend();
    }
}
