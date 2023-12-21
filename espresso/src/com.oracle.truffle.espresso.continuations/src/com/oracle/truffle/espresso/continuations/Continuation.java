package com.oracle.truffle.espresso.continuations;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * <p>A delimited one-shot continuation, which encapsulates a part of the program's execution such that it can be
 * resumed from the point at which it paused.</p>
 *
 * <p>
 * Continuations allow you to mark a region of code in which the program can <i>pause</i>, which passes control
 * flow up the stack to the point at which the continuation was <i>resumed</i>. This implementation is low level
 * and doesn't address common needs, such as passing objects in and out of the continuation as it pauses and
 * resumes.
 * </p>
 *
 * <p>
 * Continuations are not threads. When accessing thread locals they see the values that their hosting thread would
 * see. Continuations are also not thread safe.
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
 * to try deserialize it to obtain a new {@code Continuation} that you can resume again. This is required
 * because the continuation may have mutated the heap in arbitrary ways, and so resuming a continuation more than once
 * could cause extremely confusing and apparently 'impossible' states to occur.
 * </p>
 */
public final class Continuation {
    // We want a compact serialized representation, so use fields judiciously here.

    // This field is set by the VM after a pause.
    public volatile FrameRecord stackFrameHead;

    /**
     * <p>A singly linked list of reified stack frames, from top to bottom. The arrays are of the same length but each
     * slot is either a pointer or a primitive, in which case the same indexed entry in the other array has no
     * meaningful content.</p>
     *
     * <p>The contents of the arrays should be treated as opaque.</p>
     */
    public static final class FrameRecord implements Serializable {
        @Serial
        private static final long serialVersionUID = 0L;
        private final FrameRecord next;
        private final Object[] pointers;
        private final long[] primitives;

        // Invoked by the VM.
        FrameRecord(FrameRecord next, Object[] pointers, long[] primitives) {
            this.next = next;
            this.pointers = pointers;
            this.primitives = primitives;
        }

        /** The next record in the list, or null if this is the last record in the stack. */
        public FrameRecord next() {
            return next;
        }

        /** A mix of nulls and object references representing pointer-typed stack slots. */
        public Object[] pointers() {
            return pointers;
        }

        /** An array of untyped opaque longs holding primitive data from the stack. */
        public long[] primitives() {
            return primitives;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (FrameRecord) obj;
            return Objects.equals(this.next, that.next) &&
                    Arrays.equals(this.pointers, that.pointers) &&
                    Arrays.equals(this.primitives, that.primitives);
        }

        @Override
        public int hashCode() {
            return Objects.hash(next, Arrays.hashCode(pointers), Arrays.hashCode(primitives));
        }

        @Override
        public String toString() {
            return "FrameRecord[" +
                    "next=" + next + ", " +
                    "pointers=" + Arrays.toString(pointers) + ", " +
                    "primitives=" + Arrays.toString(primitives) + ']';
        }
    }

    private final EntryPoint entryPoint;

    /**
     * A point in the lifecycle of a continuation.
     *
     * @see Continuation#getState
     */
    public enum State {
        /** Newly constructed and waiting for a resume. */
        NEW,
        /** Currently executing. */
        RUNNING,
        /** Pause has been called. */
        PAUSED,
        /** Completed successfully. */
        COMPLETED,
        /** An exception propagated out of the entry point. */
        FAILED
    }

    // Avoid the continuation stack having a reference to this controller class.
    private static class StateHolder {
        State state = State.NEW;
    }
    private transient StateHolder stateHolder = new StateHolder();

    /**
     * <p>Creates a new paused continuation.</p>
     *
     * <p>
     * Pass an implementation of {@link EntryPoint}. The new continuation starts in the paused state. To begin execution
     * call the {@link Continuation#resume()} method. The entry point will be passed a capability object allowing it
     * to pause itself. If you don't want to deal with passing capabilities around, just stick it in a global variable
     * that you clear when done, or use a thread local.
     * </p>
     *
     * <p>
     * Be careful with using lambdas as the entry point. It works but you should be careful not to accidentally capture
     * the calling object if you intend to serialize the results.
     * </p>
     */
    public Continuation(EntryPoint entryPoint) {
        this.entryPoint = entryPoint;
    }

    /**
     * A newly constructed continuation starts in {@link State#NEW}. After the first call to {@link #resume()} the
     * state will become {@link State#RUNNING} until either the entry point returns, at which point the state
     * becomes {@link State#COMPLETED}, or until the continuation pauses, at which point it will be
     * {@link State#PAUSED}.
     */
    public State getState() {
        return stateHolder.state;
    }

    @FunctionalInterface
    public interface EntryPoint extends Consumer<PauseCapability> {
    }

    /**
     * An object provided by the system that lets you yield control and return from {@link Continuation#resume()}.
     */
    public static final class PauseCapability {
        // Will be assigned separately to break the cycle that occurs because this object has to be on the entry stack.
        StateHolder stateHolder;

        /**
         * Pauses the continuation, unwinding the stack to the point at which it was previously resumed.
         */
        public void pause() {
            //noinspection UnusedAssignment
            stateHolder.state = State.PAUSED;
            Continuation.pause0();
            stateHolder.state = State.RUNNING;
        }
    }

    /**
     * Runs the continuation until it either completes or calls {@link PauseCapability#pause(Object)}. If the
     * continuation completes (returns from the entry point) then this method returns null. If it pauses then
     * the returned object is whatever was passed to {@code pause}.
     */
    public void resume() {
        // Are we in the special waiting-to-start state?
        if (stateHolder.state == State.NEW) {
            start0();
            return;
        }

        switch (stateHolder.state) {
            case RUNNING -> throw new IllegalStateException("You can't recursively resume an already executing continuation.");
            case PAUSED -> {} // OK
            case COMPLETED -> throw new IllegalStateException("This continuation has already completed successfully.");
            case FAILED -> throw new IllegalStateException("This continuation has failed and must be discarded.");
        }

        assert stackFrameHead != null;
        resume0();
    }

    @SuppressWarnings("unused")
    private void run() {
        // We get here from inside the substituted start0 method.
        var cap = new PauseCapability();
        cap.stateHolder = stateHolder;
        stateHolder.state = State.RUNNING;
        try {
            entryPoint.accept(cap);
            stateHolder.state = State.COMPLETED;
        } catch (Throwable e) {
            stateHolder.state = State.FAILED;
            throw e;
        }
    }

    // region Intrinsics
    private void start0() {
        // Control passes from here to run() via the VM.
        throw notOnEspresso();
    }

    private void resume0() {
        throw notOnEspresso();
    }

    // This is native rather than throwing, because if we throw then IntelliJ can look inside the implementation
    // to determine it always fails and then its static analysis starts flagging non-existent errors in the user's
    // source code.
    private static native void pause0();

    private static UnsupportedOperationException notOnEspresso() {
        // Caller should have been replaced by an intrinsic / substitution.
        return new UnsupportedOperationException("Continuations must be run on the Java on Truffle JVM.");
    }
    // endregion
}
