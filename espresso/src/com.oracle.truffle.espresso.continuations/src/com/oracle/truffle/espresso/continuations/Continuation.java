/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.continuations;

import java.lang.reflect.Method;

/**
 * <p>
 * A delimited one-shot continuation, which encapsulates a part of the program's execution such that
 * it can be resumed from the point at which it suspended.
 * </p>
 *
 * <p>
 * Continuations allow you to mark a region of code in which the program can <i>suspend</i>, which
 * passes control flow up the stack to the point at which the continuation was <i>resumed</i>. This
 * implementation is low level and doesn't address common needs, such as passing objects in and out
 * of the continuation as it suspends and resumes.
 * </p>
 *
 * <p>
 * Continuations are not threads. When accessing thread locals they see the values that their
 * hosting thread would see. Continuations are also not thread safe.
 * </p>
 *
 * <h1>Serialization</h1>
 *
 * <p>
 * Continuations can be serialized to disk and resumed later in a separate process. Alternatively
 * they can be discarded and left for the GC to clean up.
 * </p>
 *
 * <p>
 * Continuation deserialization is <b>not secure</b>. You should only deserialize continuations you
 * yourself suspended, as resuming a malicious continuation can cause arbitrary undefined behaviour,
 * i.e. is equivalent to handing control of the JVM to the attacker.
 * </p>
 *
 * <p>
 * This class implements <i>one shot</i> continuations, meaning you cannot restart a continuation
 * from the same suspend point more than once. For that reason this class is mutable, and the act of
 * calling {@code resume} changes its state. If you want to roll back and retry a resume operation
 * you should start by serializing the continuation, then to try deserialize it to obtain a new
 * {@code Continuation} that you can resume again. This is required because the continuation may
 * have mutated the heap in arbitrary ways, and so resuming a continuation more than once could
 * cause extremely confusing and apparently 'impossible' states to occur.
 * </p>
 */
public final class Continuation {
    // Next steps:
    // - Be able to abort the unwind if we hit a frame that can't be suspended e.g. that holds
    // monitors.
    // - Ensure unwinds fail if there are any non-bytecode methods on the stack.
    // - Refactor to pull frame serialization into Truffle itself, stop exposing frame guts to
    // language impls.
    // - Feature: Add more data to FrameRecord so we can do consistency checks in case the code has
    // changed.
    // - (lowpri) Refactor to use frame slots and avoid having a non-final field in BytecodeNode.

    // We want a compact serialized representation, so use fields judiciously here.

    // This field is set by the VM after a suspend.
    public volatile FrameRecord stackFrameHead;

    /**
     * <p>
     * A singly linked list of reified stack frames, from top to bottom. The arrays are of the same
     * length but each slot is either a pointer or a primitive, in which case the same indexed entry
     * in the other array has no meaningful content.
     * </p>
     *
     * <p>
     * The contents of the arrays should be treated as opaque.
     * </p>
     */
    public static final class FrameRecord {
        /**
         * The next frame in the stack.
         */
        public FrameRecord next;   // Set by the VM

        /**
         * Pointer stack slots. Note that not every slot is used.
         */
        public final Object[] pointers;

        /**
         * Primitive stack slots. Note that not every slot is used.
         */
        public final long[] primitives;

        /**
         * The method of this stack frame.
         */
        public final Method method;

        /**
         * The stack pointer (how many slots are used at the current bci).
         */
        public final int sp;

        /**
         * Reserved. Will always be null when using release builds of the JVM.
         */
        public final Object reserved1;

        // Invoked by the VM.
        FrameRecord(Object[] pointers, long[] primitives, Method method, int sp, Object reserved1) {
            this.pointers = pointers;
            this.primitives = primitives;
            this.method = method;
            this.sp = sp;
            this.reserved1 = reserved1;
        }
    }

    /**
     * The entry point as provided to the constructor.
     */
    public final EntryPoint entryPoint;

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
        /** Suspend has been called. */
        SUSPENDED,
        /** Completed successfully. */
        COMPLETED,
        /** An exception propagated out of the entry point. */
        FAILED
    }

    // Avoid the continuation stack having a reference to this controller class.
    private static final class StateHolder {
        State state = State.NEW;
    }

    private final StateHolder stateHolder = new StateHolder();

    /**
     * <p>
     * Creates a new suspended continuation.
     * </p>
     *
     * <p>
     * Pass an implementation of {@link EntryPoint}. The new continuation starts in the
     * {@link State#SUSPENDED} state. To begin execution call the {@link Continuation#resume()}
     * method. The entry point will be passed a capability object allowing it to suspend itself. If
     * you don't want to deal with passing capabilities around, just stick it in a global variable
     * that you clear when done, or use a thread local.
     * </p>
     *
     * <p>
     * Be careful with using lambdas as the entry point. It works but you should be careful not to
     * accidentally capture the calling object if you intend to serialize the results.
     * </p>
     */
    public Continuation(EntryPoint entryPoint) {
        this.entryPoint = entryPoint;
    }

    /**
     * A newly constructed continuation starts in {@link State#NEW}. After the first call to
     * {@link #resume()} the state will become {@link State#RUNNING} until either the entry point
     * returns, at which point the state becomes {@link State#COMPLETED}, or until the continuation
     * suspends, at which point it will be {@link State#SUSPENDED}.
     */
    public State getState() {
        return stateHolder.state;
    }

    /**
     * A functional interface you implement to delimit the starting point of the continuation. You
     * can only suspend the continuation when your implementation of {@code start} is on the stack.
     */
    @FunctionalInterface
    public interface EntryPoint {
        /**
         * The starting point of your continuation. The {@code suspendCapability} should only be
         * invoked on this thread.
         */
        void start(SuspendCapability suspendCapability);
    }

    /**
     * An object provided by the system that lets you yield control and return from
     * {@link Continuation#resume()}.
     */
    public static final class SuspendCapability {
        // Will be assigned separately to break the cycle that occurs because this object has to be
        // on the entry stack.
        StateHolder stateHolder;

        /**
         * Suspends the continuation, unwinding the stack to the point at which it was previously
         * resumed.
         */
        public void suspend() {
            stateHolder.state = State.SUSPENDED;
            Continuation.suspend0();
            stateHolder.state = State.RUNNING;
        }
    }

    /**
     * Runs the continuation until it either completes or calls {@link SuspendCapability#suspend()}.
     * The difference between the two reasons for returning is visible in {@link #getState()}. A
     * continuation may not be resumed if it's already {@link State#COMPLETED} or
     * {@link State#FAILED}, nor if it is already {@link State#RUNNING}.
     */
    public void resume() {
        // Are we in the special waiting-to-start state?
        if (stateHolder.state == State.NEW) {
            start0();
            return;
        }

        switch (stateHolder.state) {
            case RUNNING -> throw new IllegalStateException("You can't recursively resume an already executing continuation.");
            case SUSPENDED -> {
                // OK
            }
            case COMPLETED -> throw new IllegalStateException("This continuation has already completed successfully.");
            case FAILED -> throw new IllegalStateException("This continuation has failed and must be discarded.");
        }

        assert stackFrameHead != null;
        resume0();
    }

    /**
     * Invoked by the VM. This is the first frame in the continuation. We get here from inside the
     * substituted start0 method.
     */
    @SuppressWarnings("unused")
    private void run() {
        var cap = new SuspendCapability();
        cap.stateHolder = stateHolder;
        stateHolder.state = State.RUNNING;
        try {
            entryPoint.start(cap);
            stateHolder.state = State.COMPLETED;
            stackFrameHead = null;
        } catch (Throwable e) {
            stateHolder.state = State.FAILED;
            stackFrameHead = null;
            throw e;
        }
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append("Continuation in state ");
        sb.append(getState());
        sb.append("\n");
        if (stackFrameHead != null) {
            Continuation.FrameRecord cursor = stackFrameHead;
            while (cursor != null) {
                sb.append("Frame: ");
                sb.append(cursor.method);
                sb.append("\n");
                sb.append("  Current bytecode index: ");
                sb.append(cursor.primitives[0]);
                sb.append("\n");
                sb.append("  Stack pointer: ");
                sb.append(cursor.sp);
                sb.append("\n");
                sb.append("  Pointers: [");
                // We start at 1 because the first slot is always a primitive (the bytecode index).
                for (int i = 1; i < cursor.pointers.length; i++) {
                    var pointer = cursor.pointers[i];
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
                if (cursor.reserved1 instanceof byte[] slotTags) {
                    // We only take this path in debug builds of Espresso when assertions are
                    // enabled for the VM itself.
                    sb.append("  Slot tags: ");
                    for (int i = 0; i < slotTags.length; i++) {
                        sb.append(slotTags[i]);
                        if (i < slotTags.length - 1) {
                            sb.append(" ");
                        }
                    }
                    sb.append("\n");
                }
                cursor = cursor.next;
            }
        }
        return sb.toString();
    }

    // region Intrinsics
    private void start0() {
        // Control passes from here to run() via the VM.
        throw notOnEspresso();
    }

    private void resume0() {
        throw notOnEspresso();
    }

    // This is native rather than throwing, because if we throw then IntelliJ can look inside the
    // implementation to determine it always fails and then its static analysis starts flagging
    // non-existent errors in the user's source code.
    private static native void suspend0();

    /**
     * Returns true if this VM supports the continuations feature, false otherwise.
     */
    public static boolean isSupported() {
        return false;
    }

    private static UnsupportedOperationException notOnEspresso() {
        // Caller should have been replaced by an intrinsic / substitution.
        return new UnsupportedOperationException("Continuations must be run on the Java on Truffle JVM.");
    }
    // endregion
}
