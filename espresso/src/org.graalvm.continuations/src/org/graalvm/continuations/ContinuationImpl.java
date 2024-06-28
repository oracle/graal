/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.continuations;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Implementation of the {@link Continuation} class.
 *
 * <h1>Suspend</h1>
 *
 * <p>
 * The stack of a resume/suspend cycle has this form:
 *
 * <h2>Suspend</h2>
 *
 * <pre>
 *     .                                .
 *     .                                .
 *     +--------------------------------+
 *     |                                |
 *     |    ContinuationImpl.resume()   |
 *     |                                |
 *     +--------------------------------+
 *     |                                |
 *     |    ContinuationImpl.start0()   | <-- For the first resume
 *     |            OR                  |
 *     |    ContinuationImpl.resume0()  | <-- For continuations that have been suspended at least once before.
 *     |                                |
 *     |================================|
 *     |                                |
 *     |    VM code                     |
 *     |                                |
 *     |================================| <--+
 *     |                                |    |
 *     |    ContinuationImpl.run()      |    |
 *     |                                |    |
 *     |--------------------------------|    |
 *     |    EntryPoint.start()          |    |
 *     |--------------------------------|    |
 *     |    Java Frame                  |    |
 *     |--------------------------------|    |
 *     |    Java Frame                  |    |
 *     +--------------------------------+    |
 *     .                                .    |
 *     .        ...                     .     \__ Continuation frames to be recorded
 *     .                                .     /
 *     +--------------------------------+    |
 *     |    Java Frame                  |    |
 *     |--------------------------------|    |
 *     |                                |    |
 *     |    ContinuationImpl.suspend()  |    |
 *     |                                |    |
 *     |--------------------------------| <--+
 *     |                                |
 *     |    ContinuationImpl.suspend0() |
 *     |                                |
 *     |================================|
 *     |                                |
 *     |    VM Code                     |
 *     |                                |
 *     +--------------------------------+
 * </pre>
 *
 * <p>
 * Recorded frame may not:
 * <ul>
 * <li>Be Non-Java frames (in particular, no native method), except for
 * {@link ContinuationImpl#resume0()} / {@link ContinuationImpl#start0()} and
 * {@link ContinuationImpl#suspend0()}.</li>
 * <li>Hold any lock (neither a monitor, nor any kind of standard lock from
 * {@link java.util.concurrent}).</li>
 * </ul>
 *
 * After suspension, the stack will be the following, and so without any java-side observable frame
 * popping or bytecode execution:
 * 
 * <pre>
 *     .                               .
 *     .                               .
 *     +-------------------------------+
 *     |                               |
 *     |    ContinuationImpl.resume()  |
 *     |                               |
 *     +-------------------------------+
 * </pre>
 *
 * Control is then returned to the caller.
 *
 * <h2>Resume</h2>
 *
 * Resuming takes a stack of the form
 *
 * <pre>
 *     .                                .
 *     .                                .
 *     +--------------------------------+
 *     |                                |
 *     |    ContinuationImpl.resume()   |
 *     |                                |
 *     +--------------------------------+
 *     |                                |
 *     |    ContinuationImpl.resume0()  |
 *     |                                |
 *     |================================|
 *     |                                |
 *     |    VM code                     |
 *     |                                |
 *     +================================+
 * </pre>
 *
 * Back to
 *
 * <pre>
 *     .                                .
 *     .                                .
 *     +--------------------------------+
 *     |                                |
 *     |    ContinuationImpl.resume()   |
 *     |                                |
 *     +--------------------------------+
 *     |                                |
 *     |    ContinuationImpl.resume0()  |
 *     |                                |
 *     |================================|
 *     |                                |
 *     |    VM code                     |
 *     |                                |
 *     |================================| <--+
 *     |                                |    |
 *     |    ContinuationImpl.run()      |    |
 *     |                                |    |
 *     |--------------------------------|    |
 *     |    EntryPoint.start()          |    |
 *     |--------------------------------|    |
 *     |    Java Frame                  |    |
 *     |--------------------------------|    |
 *     |    Java Frame                  |    |
 *     +--------------------------------+    |
 *     .                                .    |
 *     .        ...                     .     \__ Recorded frames
 *     .                                .     /
 *     +--------------------------------+    |
 *     |    Java Frame                  |    |
 *     |--------------------------------|    |
 *     |                                |    |
 *     |    ContinuationImpl.suspend()  |    |
 *     |                                |    |
 *     +--------------------------------+ <--+
 * </pre>
 *
 * Then, control is handed back to {@link #suspend()}, which can then continue and complete
 * normally, effectively allowing to resume execution in the caller of {@link #suspend()}
 *
 * <h1>Record</h1>
 *
 * <p>
 * The recorded Java Frames are handled internally by the VM, and only exposed to the Java world
 * when {@link #ensureMaterialized()} is called, at which point a {@link FrameRecord Java
 * representation} of the stack record is stored in {@link #stackFrameHead}, and the VM may discard
 * its own internal record.
 * 
 * <p>
 * This Java record can then be used to serialize the continuation. Note that the contents of the
 * Java record is VM-dependent, and no assumptions should be made of its contents.
 *
 * <p>
 * The Java record can be brought back to the VM internals through {@link #ensureDematerialized()}.
 * Sanity checks may be performed to ensure the VM can recover from these frames.
 */
final class ContinuationImpl extends Continuation {
    @Serial private static final long serialVersionUID = -5833405097154096157L;

    private static final VarHandle STATE_HANDLE;
    private static final boolean ASSERTIONS_ENABLED = areAssertionsEnabled();

    static final int FORMAT_VERSION = 2;
    private static final int FORMAT_SHIFT = 4;
    private static final int FORMAT_MASK = 0xFF;

    static {
        try {
            STATE_HANDLE = MethodHandles.lookup().findVarHandle(ContinuationImpl.class, "state", State.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    // We want a compact serialized representation, so use fields judiciously here.

    // The entry point as provided in the constructor.
    @SuppressWarnings("serial") // handled by read/writeExternal
    private ContinuationEntryPoint entryPoint;

    // This field is initialized after calling ensureMaterialized().
    @SuppressWarnings("serial") // handled by read/writeExternal
    private volatile FrameRecord stackFrameHead;

    // Bookkeeping
    private transient Thread exclusiveOwner;
    private volatile State state;

    // region Suspended state

    /**
     * A point in the lifecycle of a continuation.
     *
     * @see ContinuationImpl#getState
     */
    public enum State {
        // Note: If you change this enum, bump the format version and ensure correct deserialization
        // of old continuations.

        /** Pending deserialization, and/or deserialization failed. */
        INCOMPLETE,
        /** Locked for complex state capture or change (e.g., serialization/deserialization). */
        LOCKED,
        /** Newly constructed and waiting for a resume. */
        NEW,
        /** Currently executing. */
        RUNNING,
        /** Suspend has been called. */
        SUSPENDED,
        /** Completed successfully. */
        COMPLETED,
        /** An exception propagated out of the entry point. */
        FAILED
    }

    private boolean updateState(State expectedState, State newState) {
        return STATE_HANDLE.compareAndSet(this, expectedState, newState);
    }

    private State forceState(State newState) {
        return (State) STATE_HANDLE.getAndSet(this, newState);
    }

    private State lock() {
        State previousState = state;
        while (true) {
            if (previousState == State.RUNNING) {
                return State.RUNNING;
            }
            State newState = (State) STATE_HANDLE.compareAndExchange(this, previousState, State.LOCKED);
            if (newState == previousState) {
                return newState;
            }
            previousState = newState;
        }
    }

    private void unlock(State newState) {
        boolean updated = updateState(State.LOCKED, newState);
        assert updated : state;
    }

    private void setExclusiveOwner() {
        assert exclusiveOwner == null;
        exclusiveOwner = Thread.currentThread();
    }

    private void clearExclusiveOwner() {
        assert exclusiveOwner == Thread.currentThread();
        exclusiveOwner = null;
    }

    // endregion State

    // region API Implementation

    @Override
    public boolean isResumable() {
        State s = getState();
        return s == State.NEW || s == State.SUSPENDED;
    }

    @Override
    public boolean isCompleted() {
        State s = getState();
        return s == State.COMPLETED || s == State.FAILED;
    }

    /**
     * Runs the continuation until it either completes or calls {@link SuspendCapability#suspend()}.
     * The difference between the two reasons for returning is visible in {@link #getState()}. A
     * continuation may not be resumed if it's already {@link State#COMPLETED} or
     * {@link State#FAILED}, nor if it is already {@link State#RUNNING}.
     *
     * <p>
     * If an exception is thrown by the continuation and escapes the entry point, it will be wrapped
     * in {@link ContinuationExecutionException}, then rethrown here. The continuation is then no
     * longer usable and must be discarded.
     *
     * @throws IllegalContinuationStateException if the {@link #getState()} is not
     *             {@link State#SUSPENDED}.
     * @throws IllegalMaterializedRecordException if the VM rejects the frames recorded in
     *             {@link #stackFrameHead}.
     */
    @Override
    public boolean resume() {
        if (!isSupported()) {
            throw new UnsupportedOperationException("Continuations must be run on the Java on Truffle JVM with the java.Continuum option set to true.");
        }

        // Are we in the special waiting-to-start state?
        if (updateState(State.NEW, State.RUNNING)) {
            // Enable the use of suspend capabilities.
            setExclusiveOwner();
            try {
                return start0();
            } finally {
                clearExclusiveOwner();
            }
        } else if (updateState(State.SUSPENDED, State.RUNNING)) {
            setExclusiveOwner();
            try {
                // We are ready to resume, make sure the VM has the most up-to-date frames
                ensureDematerialized();
                assert stackFrameHead == null;
                return resume0();
            } finally {
                clearExclusiveOwner();
            }
        } else {
            // illegal state for resume: neither suspended nor new
            switch (state) {
                case RUNNING -> throw new IllegalContinuationStateException("You can't resume an already executing continuation.");
                case COMPLETED ->
                    throw new IllegalContinuationStateException("This continuation has already completed successfully.");
                case FAILED -> throw new IllegalContinuationStateException("This continuation has failed and must be discarded.");
                case LOCKED -> throw new IllegalContinuationStateException("You can't resume a continuation while it is being serialized or deserialized.");
                // this is racy so ensure we have a general error message in those case
                default -> throw new IllegalContinuationStateException("Only new or suspended continuation can be resumed");
            }
        }
    }

    /**
     * Called from {@link SuspendCapability#suspend()}. Suspends this continuation. If successful,
     * execution resumes back at {@link #resume()}.
     */
    void trySuspend() {
        if (exclusiveOwner != Thread.currentThread()) {
            throw new IllegalContinuationStateException("Suspend capabilities can only be used inside a continuation.");
        }
        if (!updateState(ContinuationImpl.State.RUNNING, ContinuationImpl.State.SUSPENDED)) {
            throw new IllegalContinuationStateException("Suspend capabilities can only be used inside a running continuation.");
        }
        try {
            suspend();
        } catch (IllegalContinuationStateException e) {
            if (!updateState(ContinuationImpl.State.SUSPENDED, ContinuationImpl.State.RUNNING)) {
                // force failed state and maybe assert
                ContinuationImpl.State badState = forceState(ContinuationImpl.State.RUNNING);
                if (ContinuationImpl.ASSERTIONS_ENABLED) {
                    AssertionError assertionError = new AssertionError(badState.toString());
                    assertionError.addSuppressed(e);
                    throw assertionError;
                }
            }
            throw e;
        }
        // set in #resume()
        assert state == ContinuationImpl.State.RUNNING : state;
    }

    // endregion API Implementation

    // region Serialization

    ContinuationImpl() {
        state = State.INCOMPLETE;
    }

    /**
     * <p>
     * A singly linked list of reified stack frames, from top to bottom. The two arrays,
     * {@link #pointers} and {@link #primitives} are a representation of the Stack and Local
     * variables for this frame at the point where the continuation was suspended. They are
     * inherently VM-dependant, so no assumptions should be made about their content.
     *
     * <p>
     * On {@link #resume() resuming}, All declaring classes of methods in the recorded frames are
     * initialized if they were not already initialized, and this before the rewinding happens.
     *
     * <p>
     * Note that the VM ensures on {@link #ensureDematerialized() dematerialization} that the record
     * is valid. Here are the requirements for a record to be considered valid by the VM:
     * <ul>
     * <li>{@link #bci} must point to an invoke opcode in {@link #method}.</li>
     * <li>If this frame record is the last on the stack, then {@link #method} is
     * {@link ContinuationImpl#suspend() Continuation.suspend()}.</li>
     * <li>Otherwise, {@link #next}.{@link #method} had the same Name and Signature than the
     * {@code CONSTANT_Methodref_info} reference constant pointed to by the invoke opcode at
     * {@link #bci}. Furthermore, a {@code loading constraint} is recorded for the return type.</li>
     * <li>The stack and locals information recorded in {@link #pointers} and {@link #primitives} is
     * consistent with {@code verification types} for the given {@link #bci}.</li>
     * </ul>
     */
    static final class FrameRecord {
        /**
         * The next frame in the stack.
         */
        FrameRecord next;   // Set by the VM

        /**
         * Pointer stack and local slots. Note that not every slot is used.
         */
        final Object[] pointers;

        /**
         * Primitive stack and local slots. Note that not every slot is used.
         */
        final long[] primitives;

        /**
         * The method of this stack frame.
         */
        final Method method;

        /**
         * The bci at which to resume the frame.
         */
        final int bci;

        FrameRecord(Object[] pointers, long[] primitives, Method method, int bci) {
            this.pointers = pointers;
            this.primitives = primitives;
            this.method = method;
            this.bci = bci;
        }
    }

    /**
     * Serializes the continuation using an internal format. The {@link ObjectOutputStream} will
     * receive some opaque bytes followed by writes of the objects pointed to by the stack. It's up
     * to the serialization engine to recursively serialize everything that's reachable.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        writeObjectExternal(out);
    }

    /**
     * Initializes the continuation from the given {@link ObjectInputStream}.
     *
     * @throws IllegalContinuationStateException if the continuation is in state
     *             {@link State#RUNNING}.
     * @throws FormatVersionException if the header read from the stream doesn't match the expected
     *             version number.
     * @throws IOException if there is a problem reading the stream, or if the stream appears to be
     *             corrupted.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        /*
         * We read the context classloader here because we need the classloader that holds the
         * user's app. If we use Class.forName() in this code we get the platform classloader
         * because this class is provided by the VM, and thus can't look up methods of user classes.
         * If we use the classloader of the entrypoint it breaks for Generator and any other classes
         * we might want to ship with the VM that use this API. So we need the user's app class
         * loader. We could walk the stack to find it just like ObjectInputStream does, but we go
         * with the context classloader here to make it easier for the user to control.
         */
        state = State.INCOMPLETE;
        readObjectExternalImpl(in, Thread.currentThread().getContextClassLoader());
    }

    @Override
    void writeObjectExternal(ObjectOutput out) throws IOException {
        State currentState = lock();
        if (currentState == State.RUNNING) {
            throw new IllegalContinuationStateException("You cannot serialize a continuation whilst it's running, as this would have unclear semantics. Please suspend first.");
        }
        try {
            ensureMaterialized();
            // We start by writing out a header byte. The high nibble contains a major version. Old
            // libraries will refuse to deserialize continuations with a higher version than what
            // they recognize. New libraries may choose to continue supporting the old formats. The
            // low nibble contains flags.
            int header = FORMAT_VERSION << FORMAT_SHIFT;

            out.writeByte(header);

            out.writeObject(currentState);
            out.writeObject(entryPoint);

            if (currentState == State.SUSPENDED) {
                FrameRecordSerializer.forOut(FORMAT_VERSION, out).writeRecord(stackFrameHead);
            }
        } finally {
            unlock(currentState);
        }
    }

    /**
     * Deserialize a continuation from the provided {@link ObjectInput}.
     *
     * <p>
     * This method is provided to cooperate with non-jdk serialization frameworks.
     *
     * <p>
     * {@code registerFreshObject} will be called once a fresh continuation has been created, but
     * before it has been fully deserialized. It is intended to be a callback to the serialization
     * framework, so it can register the in-construction continuation so the framework may handle
     * object graph cycles.
     */
    static Continuation readObjectExternal(ObjectInput in, ClassLoader loader, Consumer<Continuation> registerFreshObject) throws IOException, ClassNotFoundException {
        if (!isSupported()) {
            throw new UnsupportedOperationException("This VM does not support continuations.");
        }
        ContinuationImpl continuation = new ContinuationImpl();
        registerFreshObject.accept(continuation);
        continuation.readObjectExternalImpl(in, loader);
        return continuation;
    }

    void readObjectExternalImpl(ObjectInput in, ClassLoader loader) throws IOException, ClassNotFoundException {
        State currentState = lock();
        if (currentState == State.RUNNING) {
            throw new IllegalContinuationStateException("You cannot serialize a continuation whilst it's running, as this would have unclear semantics. Please suspend first.");
        }
        try {
            // At this point, nothing is initialized
            int header = in.readByte();
            int version = (header >> FORMAT_SHIFT) & FORMAT_MASK;
            if (version != FORMAT_VERSION) {
                throw new FormatVersionException(version, FORMAT_VERSION);
            }

            currentState = (State) in.readObject();
            if (currentState == State.RUNNING) {
                throw new IllegalContinuationStateException("Illegal serialized continuation is in running state.");
            }
            entryPoint = (ContinuationEntryPoint) in.readObject();

            if (currentState == State.SUSPENDED) {
                stackFrameHead = FrameRecordSerializer.forIn(version, in) //
                                .withLoader(loader == null ? Thread.currentThread().getContextClassLoader() : loader) //
                                .readRecord();
            }
            unlock(currentState);
        } catch (Throwable e) {
            // If any error occurs, leave the continuation as incomplete.
            unlock(State.INCOMPLETE);
            throw e;
        }
    }

    // endregion Serialization

    // region Implementation

    ContinuationImpl(ContinuationEntryPoint entryPoint) {
        this.state = State.NEW;
        this.entryPoint = entryPoint;
    }

    /**
     * Invoked by the VM. This is the first frame in the continuation.
     */
    @SuppressWarnings("unused")
    private void run() {
        assert state == State.RUNNING : state; // set in #resume()
        SuspendCapability cap = SuspendCapability.create(this);
        try {
            try {
                entryPoint.start(cap);
            } catch (Throwable e) {
                if (!updateState(State.RUNNING, State.FAILED)) {
                    // force failed state and maybe assert
                    State badState = forceState(State.FAILED);
                    if (ASSERTIONS_ENABLED) {
                        AssertionError assertionError = new AssertionError(badState.toString());
                        assertionError.addSuppressed(e);
                        throw assertionError;
                    }
                }
                throw new ContinuationExecutionException(e);
            }
            if (!updateState(State.RUNNING, State.COMPLETED)) {
                // force completed state and maybe assert
                State badState = forceState(State.COMPLETED);
                if (ASSERTIONS_ENABLED) {
                    throw new AssertionError(badState.toString());
                }
            }
        } finally {
            stackFrameHead = null;
        }
    }

    /**
     * A newly constructed continuation starts in {@link State#NEW}. After the first call to
     * {@link #resume()} the state will become {@link State#RUNNING} until either the entry point
     * returns, at which point the state becomes {@link State#COMPLETED}, or until the continuation
     * suspends, at which point it will be {@link State#SUSPENDED}.
     * <p>
     * If an exception escapes from {@link #resume()}, the state becomes {@link State#FAILED}. A
     * continuation being {@link #ensureMaterialized() materialized}, {@link #ensureDematerialized()
     * dematerialized} or in a serialization/deserialization process will be {@link State#LOCKED}.
     */
    private State getState() {
        return state;
    }

    /**
     * Ensures that any VM-specific state is materialized in this object. If this continuation has
     * been {@link #suspend() suspended}, then on a successful return, {@link #stackFrameHead} will
     * be non-null. On an unsuspended continuation, this call has no visible effect.
     * <p>
     * {@link #ensureDematerialized()} will need to be called for the VM to be aware of the new
     * frames.
     */
    private void ensureMaterialized() {
        if (stackFrameHead != null) {
            // frame already materialized.
            return;
        }
        State current = lock();
        if (current == State.RUNNING) {
            return;
        }
        try {
            if (stackFrameHead != null) {
                return;
            }
            materialize0();
        } finally {
            unlock(current);
        }
    }

    /**
     * Communicates to the VM that it should consume {@link #stackFrameHead} for resuming. If the
     * call completes, {@link #stackFrameHead} will be null.
     *
     * @throws IllegalMaterializedRecordException If the recorded frames cannot be dematerialized.
     *             This can happen, for example, if the recorded frames contain an unexpected object
     *             that the VM does not expect to see where it should resume.
     */
    private void ensureDematerialized() {
        if (stackFrameHead == null) {
            // No frame to dematerialize
            return;
        }

        State previousState = lock();
        if (previousState == State.RUNNING) {
            dematerialize0();
        } else {
            try {
                dematerialize0();
            } finally {
                unlock(previousState);
            }
        }
        if (stackFrameHead != null) {
            throw new IllegalMaterializedRecordException("Failed to dematerialize continuation frames.");
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Continuation in state ");
        sb.append(getState());
        sb.append("\n");
        if (stackFrameHead != null) {
            ContinuationImpl.FrameRecord cursor = stackFrameHead;
            while (cursor != null) {
                sb.append("Frame: ");
                sb.append(cursor.method);
                sb.append("\n");
                sb.append("  Current bytecode index: ");
                sb.append(cursor.bci);
                sb.append("\n");
                sb.append("  Pointers: [");
                // We start at 1 because the first slot is always a primitive (the bytecode index).
                for (int i = 1; i < cursor.pointers.length; i++) {
                    Object pointer = cursor.pointers[i];
                    if (pointer == null) {
                        sb.append("null");
                    } else if (pointer == this) {
                        sb.append("this continuation");   // Don't stack overflow.
                    } else {
                        sb.append(pointer);
                    }
                    if (i < cursor.pointers.length - 1) {
                        sb.append(", ");
                    }
                }
                sb.append("]\n");
                sb.append("  Primitives: ");
                for (int i = 1; i < cursor.primitives.length; i++) {
                    sb.append(cursor.primitives[i]);
                    if (i < cursor.pointers.length - 1) {
                        sb.append(" ");
                    }
                }
                sb.append("\n");
                cursor = cursor.next;
            }
        }
        return sb.toString();
    }

    // VM knows about this method and will check it is the last on the frame record.
    private void suspend() {
        suspend0();
    }

    private native boolean start0();

    private native boolean resume0();

    private native void suspend0();

    private native void materialize0();

    private native void dematerialize0();
    // endregion

    @SuppressWarnings("all")
    private static boolean areAssertionsEnabled() {
        boolean enabled = false;
        assert enabled = true;
        return enabled;
    }
}
