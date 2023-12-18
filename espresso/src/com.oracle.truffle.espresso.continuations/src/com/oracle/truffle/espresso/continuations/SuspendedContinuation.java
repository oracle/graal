package com.oracle.truffle.espresso.continuations;

/**
 * <p>A delimited one-shot continuation, which encapsulates a part of the program's execution such that it can be
 * resumed from the point at which it paused.</p>
 *
 * <p>
 * Continuations allow you to mark a region of code in which the program can <i>pause</i>, which passes control
 * flow up the stack to the point at which the continuation was <i>resumed</i>. This implementation lets you pass
 * objects of type {@code IN} to the resume method which are then supplied either to the entry point or as the return
 * value of {@link PauseCapability#pause(Object)}, and objects of type {@code OUT} may be passed to {@code pause}
 * to be returned from {@code resume}. In this way the continuation may communicate via a form of message passing
 * with the object controlling it.
 * </p>
 *
 * <p>
 * Continuations are not threads. When accessing thread locals they see the values that their hosting thread would
 * see.
 * </p>
 *
 * <p>
 * Continuations can be serialized to disk and resumed later in a separate process. Alternatively they can be discarded
 * and left for the GC to clean up.
 * </p>
 *
 * <p>
 * This class implements <i>one shot</i> continuations, meaning you cannot restart a continuation from the same pause
 * point more than once. For that reason this class is mutable, and the act of calling {@code resume} changes its state.
 * If you want to roll back and retry a resume operation you should start by serializing the continuation, then
 * to try deserialize it to obtain a new {@code SuspendedContinuation} that you can resume again. This is required
 * because the continuation may have mutated the heap in arbitrary ways, and so resuming a continuation more than once
 * could cause extremely confusing and apparently 'impossible' states to occur.
 * </p>
 */
public final class SuspendedContinuation<IN, OUT> {
    // We want a compact serialized representation, so use fields judiciously here.

    // The stack frame array is mutated by
    private volatile Object[] stackFrames;

    // Usually null, used for passing objects resume<->pause. Doesn't need to partake in serialization.
    private transient volatile Object exchangePoint;

    private SuspendedContinuation(Object[] stackFrames) {
        this.stackFrames = stackFrames;
    }

    /**
     * The starting point of a new continuation.
     */
    @FunctionalInterface
    public interface EntryPoint<IN, OUT> {
        /**
         * Implement this method to create a new continuation.
         *
         * @param object Whatever was passed to the first call to {@link SuspendedContinuation#resume(Object)}. You can put whatever you
         *               want here.
         * @param pauser A capability that lets you yield control and return from {@link #resume(Object)}.
         */
        void start(SuspendedContinuation.PauseCapability<IN, OUT> pauser, IN object);
    }

    /**
     * An object provided by the system that lets you yield control and return from
     * {@link SuspendedContinuation#resume(Object)}.
     */
    public static final class PauseCapability<IN, OUT> {
        // Will be assigned separately to break the cycle that occurs because this object has to be on the entry stack.
        SuspendedContinuation<IN, OUT> continuation;

        /**
         * Pauses the continuation, unwinding the stack to the point at which it was previously resumed. By passing
         * objects in and out of the resume operation you can communicate with the code hosting the continuation.
         *
         * @param objectToPauseWith The object that will be returned from the last call to {@link #resume(Object)}.
         * @return The object passed into the next call to {@link #resume(Object)}
         */
        public IN pause(OUT objectToPauseWith) {
            return continuation.pause0(objectToPauseWith);
        }
    }

    /**
     * Runs the continuation until it either completes or calls {@link PauseCapability#pause(Object)}. If the
     * continuation completes (returns from the entry point) then this method returns null. If it pauses then
     * the returned object represents the suspended state.
     */
    @SuppressWarnings("unchecked")
    public OUT resume(IN objectToResumeWith) {
        assert exchangePoint == null;
        exchangePoint = objectToResumeWith;

        assert stackFrames != null;
        resume0();
        assert stackFrames != null;
        if (stackFrames.length == 0)
            return null;  // Done.

        // Called pause.
        var o = exchangePoint;
        exchangePoint = null;
        //noinspection unchecked
        return (OUT) o;
    }

    // The VM will read the stackFrames array and map those objects to the real stack before continuing execution.
    private void resume0() {
        throw new UnsupportedOperationException("Continuations must be run on the Java on Truffle JVM.");
    }

    @SuppressWarnings("unchecked")
    private IN pause0(OUT objectToPauseWith) {
        exchangePoint = objectToPauseWith;

        assert stackFrames == null;
        // This writes to the stackFrames array and then unwinds the stack.
        pause1();
        // When we get here, we have our original frames back on the stack and stackFrames is gone.
        assert stackFrames == null;

        var o = exchangePoint;
        exchangePoint = null;
        return (IN) o;
    }

    // The VM will unwind the stack up to the point where resume0 was called and write an array of the frames to
    // the stackFrames variable.
    private native void pause1();

    /**
     * <p>Creates a new paused continuation.</p>
     *
     * <p>
     * Pass an implementation of {@link SuspendedContinuation.EntryPoint}. The returned continuation starts in the paused state.
     * To begin execution call the {@link SuspendedContinuation#resume(Object)} method. The object you provide will be passed to
     * {@link SuspendedContinuation.EntryPoint#start} along with a capability allowing the continuation to pause itself.
     * </p>
     *
     * <p>
     * Be careful with using lambdas as the entry point. It works but you should be careful not to accidentally capture
     * the calling object, if you intend to serialize the results and that isn't what you intended.
     * </p>
     *
     * @param <IN> An arbitrary base type for the objects that can be passed in to the continuation when resuming it.
     * @param <OUT> An arbitrary bsae type for the objects tht can returned from the continuation when pausing it.
     */
    public static <IN, OUT> SuspendedContinuation<IN, OUT> create(SuspendedContinuation.EntryPoint<IN, OUT> entryPoint) {
        // TODO: Build a real stack frame pointing to the entry point.
        var pauser = new PauseCapability<IN, OUT>();
        var entryStack = new Object[] { entryPoint, pauser};
        var continuation = new SuspendedContinuation<IN, OUT>(entryStack);
        pauser.continuation = continuation;
        return continuation;
    }
}
