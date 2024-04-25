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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serial;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * <p>
 * Exceptions thrown from the entry point propagate out of {@link #resume()} and then mark the
 * continuation as failed. Resuming the exception after that point will fail with
 * {@link IllegalStateException}. If you want to retry a failed continuation you must have a clone
 * from before the failure (see below).
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
public final class Continuation implements Externalizable {
    // Next steps:
    // - Refactor to pull frame serialization into Truffle itself, stop exposing frame guts to
    // language impls.
    // - Feature: Add more data to FrameRecord so we can do consistency checks in case the code has
    // changed.

    // We want a compact serialized representation, so use fields judiciously here.

    private static final VarHandle STATE_HANDLE;

    static {
        try {
            STATE_HANDLE = MethodHandles.lookup().findVarHandle(Continuation.class, "state", State.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    // region Suspended state

    // This field is set by the VM after a suspend.
    private volatile FrameRecord stackFrameHead;

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
    private static final class FrameRecord {
        /**
         * The next frame in the stack.
         */
        private FrameRecord next;   // Set by the VM

        /**
         * Pointer stack slots. Note that not every slot is used.
         */
        private final Object[] pointers;

        /**
         * Primitive stack slots. Note that not every slot is used.
         */
        private final long[] primitives;

        /**
         * The method of this stack frame.
         */
        private final Method method;

        /**
         * The bci at which to resume the frame.
         */
        private final int bci;

        /**
         * The top of the stack.
         */
        private final int top;

        private FrameRecord(Object[] pointers, long[] primitives, Method method, int bci, int top) {
            this.pointers = pointers;
            this.primitives = primitives;
            this.method = method;
            this.bci = bci;
            this.top = top;
        }
    }

    /**
     * A point in the lifecycle of a continuation.
     *
     * @see Continuation#getState
     */
    public enum State {
        // Note: If you change this enum, bump the format version and ensure correct deserialization
        // of old continuations.

        /** Constructed via the no-arg constructor and pending deserialization. */
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

    private volatile State state;

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

    /**
     * The entry point as provided to the constructor.
     */
    private EntryPoint entryPoint;
    private transient Thread exclusiveOwner;

    private void setExclusiveOwner() {
        assert exclusiveOwner == null;
        exclusiveOwner = Thread.currentThread();
    }

    private void clearExclusiveOwner() {
        assert exclusiveOwner == Thread.currentThread();
        exclusiveOwner = null;
    }

    // endregion State

    // region Public API
    /**
     * Returns true if this VM supports the continuations feature, false otherwise.
     */
    public static boolean isSupported() {
        return false;
    }

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
        this.state = State.NEW;
        this.entryPoint = entryPoint;
    }

    /**
     * This constructor is intended only to allow deserialization. You shouldn't use it directly.
     */
    public Continuation() {
        // Note: can't mark this as @hidden in javadoc because the doclet fork used by GraalVM
        // is too old to understand it.
        this.state = State.INCOMPLETE;
    }

    /**
     * A newly constructed continuation starts in {@link State#NEW}. After the first call to
     * {@link #resume()} the state will become {@link State#RUNNING} until either the entry point
     * returns, at which point the state becomes {@link State#COMPLETED}, or until the continuation
     * suspends, at which point it will be {@link State#SUSPENDED}.
     */
    public State getState() {
        return state;
    }

    /**
     * Returns the entrypoint used to construct this continuation.
     */
    public EntryPoint getEntryPoint() {
        return entryPoint;
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
    public final class SuspendCapability implements Serializable {
        @Serial private static final long serialVersionUID = 4790341975992263909L;

        /**
         * Suspends the continuation, unwinding the stack to the point at which it was previously
         * resumed.
         *
         * @throws IllegalStateException if you try to call this outside a continuation, or if there
         *             are native frames on the stack, or if the thread is inside a synchronized
         *             block.
         */
        public void suspend() {
            if (exclusiveOwner != Thread.currentThread()) {
                throw new IllegalStateException("Suspend capabilities can only be used inside a continuation.");
            }
            if (!updateState(State.RUNNING, State.SUSPENDED)) {
                throw new IllegalStateException("Suspend capabilities can only be used inside a running continuation.");
            }
            try {
                Continuation.this.suspend();
            } catch (IllegalStateException e) {
                if (!updateState(State.SUSPENDED, State.RUNNING)) {
                    // force failed state and maybe assert
                    State badState = forceState(State.RUNNING);
                    if (ASSERTIONS_ENABLED) {
                        AssertionError assertionError = new AssertionError(badState.toString());
                        assertionError.addSuppressed(e);
                        throw assertionError;
                    }
                }
                throw e;
            }
            assert state == State.RUNNING : state; // set in #resume()
        }
    }

    /**
     * <p>
     * Runs the continuation until it either completes or calls {@link SuspendCapability#suspend()}.
     * The difference between the two reasons for returning is visible in {@link #getState()}. A
     * continuation may not be resumed if it's already {@link State#COMPLETED} or
     * {@link State#FAILED}, nor if it is already {@link State#RUNNING}.
     * </p>
     *
     * <p>
     * If an exception is thrown by the continuation and escapes the entry point, it will be
     * rethrown here. The continuation is then no longer usable and must be discarded.
     * </p>
     *
     * @throws IllegalStateException if the {@link #getState()} is not {@link State#SUSPENDED}.
     */
    public void resume() {
        if (!isSupported()) {
            throw new UnsupportedOperationException("Continuations must be run on the Java on Truffle JVM with the java.Continuum option set to true.");
        }
        // Are we in the special waiting-to-start state?
        if (updateState(State.NEW, State.RUNNING)) {
            // Enable the use of suspend capabilities.
            setExclusiveOwner();
            try {
                start0();

            } finally {
                clearExclusiveOwner();
            }
        } else if (updateState(State.SUSPENDED, State.RUNNING)) {
            assert stackFrameHead != null;
            setExclusiveOwner();
            try {
                resume0();
            } finally {
                clearExclusiveOwner();
            }
        } else {
            // illegal state for resume: neither suspended nor new
            switch (state) {
                case RUNNING -> throw new IllegalStateException("You can't resume an already executing continuation.");
                case COMPLETED ->
                    throw new IllegalStateException("This continuation has already completed successfully.");
                case FAILED -> throw new IllegalStateException("This continuation has failed and must be discarded.");
                case INCOMPLETE ->
                    throw new IllegalStateException("Do not construct this class using the no-arg constructor, which is there only for deserialization purposes.");
                case LOCKED -> throw new IllegalStateException("You can't resume a continuation while it is being serialized or deserialized.");
                // this is racy so ensure we have a general error message in those case
                default -> throw new IllegalStateException("Only new or suspended continuation can be resumed");
            }
        }
    }

    // endregion

    // region Serialization
    @Serial private static final long serialVersionUID = -5833405097154096157L;

    private static final int FORMAT_VERSION = 2;

    private static final int FORMAT_SHIFT = 4;
    private static final int FORMAT_MASK = 0xFF;

    /**
     * Serializes the continuation using an internal format. The {@link ObjectOutput} will receive
     * some opaque bytes followed by writes of the objects pointed to by the stack. It's up to the
     * serialization engine to recursively serialize everything that's reachable.
     */
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        var currentState = lock();
        if (currentState == State.RUNNING) {
            throw new IllegalStateException("You cannot serialize a continuation whilst it's running, as this would have unclear semantics. Please suspend first.");
        }
        try {
            // We start by writing out a header byte. The high nibble contains a major version. Old
            // libraries will refuse to deserialize continuations with a higher version than what
            // they recognize. New libraries may choose to continue supporting the old formats. The
            // low nibble contains flags.
            int header = FORMAT_VERSION << FORMAT_SHIFT;

            out.writeByte(header);

            out.writeObject(currentState);
            out.writeObject(entryPoint);

            if (currentState == State.SUSPENDED) {
                Map<String, Integer> stringPool = new HashMap<>();
                // We serialize frame-at-a-time. Prims go first, then object pointers. Conceptually
                // there aren't two arrays, just one array of untyped slots but we don't currently
                // know the real types of the slots, so have to serialize both arrays even though
                // they'll contain quite a few nulls. There are more efficient encodings available.
                FrameRecord cursor = stackFrameHead;
                assert cursor != null;
                while (cursor != null) {
                    writeFrame(out, cursor, stringPool);
                    out.writeBoolean(cursor.next != null);
                    cursor = cursor.next;
                }
            }
        } finally {
            unlock(currentState);
        }
    }

    private static void writeFrame(ObjectOutput out, FrameRecord cursor, Map<String, Integer> stringPool) throws IOException {
        Method method = cursor.method;
        out.writeObject(cursor.pointers);
        out.writeObject(cursor.primitives);
        writeMethodNameAndTypes(out, method, cursor.pointers.length > 1 ? cursor.pointers[1] : null, stringPool);
        out.writeInt(cursor.bci);
        out.writeInt(cursor.top);
    }

    private static void writeMethodNameAndTypes(ObjectOutput out, Method method, Object receiver, Map<String, Integer> stringPool) throws IOException {
        if (receiver != null && method.getDeclaringClass() == receiver.getClass()) {
            // Some classes such as Lambda classes can't be looked up by name. This is a JVM
            // optimization designed to avoid
            // contention on the global dictionary lock, but it means we need another way to get the
            // class for the method. Fortunately, lambdas always have an instance, so we can read it
            // out of the first pointer slot.
            out.writeBoolean(true);
        } else {
            out.writeBoolean(false);
            if (method.getDeclaringClass().isHidden()) {
                throw new IOException("Can't serialize continuation with static frames from methods of hidden classes: %s.%s".formatted(method.getDeclaringClass().getName(), method.getName()));
            }
            writeString(out, method.getDeclaringClass().getName(), stringPool);
        }
        writeString(out, method.getName(), stringPool);
        writeClass(out, method.getReturnType(), stringPool);
        Class<?>[] paramTypes = method.getParameterTypes();
        out.writeByte(paramTypes.length);
        for (var p : paramTypes) {
            writeClass(out, p, stringPool);
        }
    }

    private static void writeClass(ObjectOutput out, Class<?> clazz, Map<String, Integer> stringPool) throws IOException {
        if (clazz.isPrimitive()) {
            if (clazz == int.class) {
                out.writeByte('I');
            } else if (clazz == boolean.class) {
                out.writeByte('Z');
            } else if (clazz == double.class) {
                out.writeByte('D');
            } else if (clazz == float.class) {
                out.writeByte('F');
            } else if (clazz == long.class) {
                out.writeByte('J');
            } else if (clazz == byte.class) {
                out.writeByte('B');
            } else if (clazz == char.class) {
                out.writeByte('C');
            } else if (clazz == short.class) {
                out.writeByte('S');
            } else if (clazz == void.class) {
                out.writeByte('V');
            } else {
                throw new RuntimeException("Should not reach here: " + clazz);
            }
        } else {
            out.writeByte('L');
            writeString(out, clazz.getName(), stringPool);
        }
    }

    private static final int POOL_IDX_BITS = 16;
    private static final int NEW_POOL_MASK = 1 << (POOL_IDX_BITS - 1);
    private static final int POOL_IDX_MASK = NEW_POOL_MASK - 1;
    private static final int MAX_POOL_IDX = NEW_POOL_MASK - 1;

    private static void writeString(ObjectOutput out, String str, Map<String, Integer> stringPool) throws IOException {
        Integer idx = stringPool.get(str);
        if (idx == null) {
            idx = stringPool.size();
            if (idx == NEW_POOL_MASK) {
                // pick an existing entry and replace it
                Map.Entry<String, Integer> toReplace = stringPool.entrySet().iterator().next();
                idx = toReplace.getValue();
                stringPool.remove(toReplace.getKey());
            }
            stringPool.put(str, idx);
            assert idx <= MAX_POOL_IDX;
            out.writeChar(idx | NEW_POOL_MASK);
            out.writeUTF(str);
        } else {
            assert idx <= MAX_POOL_IDX;
            out.writeChar(idx);
        }
    }

    /**
     * Initializes the continuation from the given {@link ObjectInput}.
     *
     * @throws FormatVersionException if the header read from the stream doesn't match the expected
     *             version number.
     * @throws IOException if there is a problem reading the stream, or if the stream appears to be
     *             corrupted.
     */
    @Override
    public synchronized void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        State previousState = lock();
        if (previousState != State.INCOMPLETE) {
            unlock(previousState);
            throw new IllegalStateException("Do not use readExternal on a Continuation object that was not just freshly created through the no-arg constructor");
        }
        int header = in.readByte();
        int version = (header >> FORMAT_SHIFT) & FORMAT_MASK;
        if (version != FORMAT_VERSION) {
            throw new FormatVersionException(version);
        }

        State serializedState = (State) in.readObject();
        entryPoint = (EntryPoint) in.readObject();

        if (serializedState == State.SUSPENDED) {
            try {
                FrameRecord last = null;
                List<String> stringPool = new ArrayList<>();
                do {
                    // We read the context classloader here because we need the classloader that
                    // holds the user's app. If we use Class.forName() in this code we get the
                    // platform classloader because this class is provided by the VM, and thus can't
                    // look up methods of user classes. If we use the classloader of the entrypoint
                    // it breaks for ContinuationEnumeration and any other classes we might want to
                    // ship with the VM that use this API. So we need the user's app class loader.
                    // We could walk the stack to find it just like ObjectInputStream does, but we
                    // go with the context classloader here to make it easier for the user to
                    // control.
                    var frame = readFrame(in, Thread.currentThread().getContextClassLoader(), stringPool);
                    if (last == null) {
                        stackFrameHead = frame;
                    } else {
                        last.next = frame;
                    }
                    last = frame;
                } while (in.readBoolean());
            } catch (NoSuchMethodException e) {
                throw new IOException(e);
            }
        }
        unlock(serializedState);
    }

    private static FrameRecord readFrame(ObjectInput in, ClassLoader classLoader, List<String> stringPool)
                    throws IOException, ClassNotFoundException, NoSuchMethodException {
        Object[] pointers = (Object[]) in.readObject();
        long[] primitives = (long[]) in.readObject();
        // Slot zero is always primitive (bci), so this is in slot 1.
        Method method = readMethodNameAndTypes(in, classLoader, pointers.length > 1 ? pointers[1] : null, stringPool);
        int bci = in.readInt();
        int top = in.readInt();
        return new FrameRecord(pointers, primitives, method, bci, top);
    }

    private static Method readMethodNameAndTypes(ObjectInput in, ClassLoader classLoader, Object possibleThis, List<String> stringPool)
                    throws IOException, ClassNotFoundException, NoSuchMethodException {
        Class<?> declaringClass;
        if (in.readBoolean()) {
            declaringClass = possibleThis.getClass();
        } else {
            declaringClass = Class.forName(readString(in, stringPool), false, classLoader);
        }
        String name = readString(in, stringPool);
        Class<?> returnType = readClass(in, classLoader, stringPool);

        int numArgs = in.readUnsignedByte();
        var argTypes = new Class<?>[numArgs];
        for (int i = 0; i < numArgs; i++) {
            argTypes[i] = readClass(in, classLoader, stringPool);
        }

        for (Method method : declaringClass.getDeclaredMethods()) {
            if (!method.getName().equals(name)) {
                continue;
            }
            if (!Arrays.equals(method.getParameterTypes(), argTypes)) {
                continue;
            }
            if (!method.getReturnType().equals(returnType)) {
                continue;
            }
            return method;
        }

        throw new NoSuchMethodException("%s %s.%s(%s)".formatted(
                        returnType.getName(), declaringClass.getName(), name, String.join(", ", Arrays.stream(argTypes).map(Class::getName).toList())));
    }

    private static Class<?> readClass(ObjectInput in, ClassLoader classLoader, List<String> stringPool) throws IOException, ClassNotFoundException {
        int kind = in.readUnsignedByte();
        return switch (kind) {
            case 'I' -> int.class;
            case 'Z' -> boolean.class;
            case 'D' -> double.class;
            case 'F' -> float.class;
            case 'J' -> long.class;
            case 'B' -> byte.class;
            case 'C' -> char.class;
            case 'S' -> short.class;
            case 'V' -> void.class;
            case 'L' -> Class.forName(readString(in, stringPool), false, classLoader);
            default -> throw new IOException("Unexpected kind: " + kind);
        };
    }

    private static String readString(ObjectInput in, List<String> stringPool) throws IOException {
        int idx = in.readChar();
        if ((idx & NEW_POOL_MASK) != 0) {
            String value = in.readUTF();
            idx = idx & POOL_IDX_MASK;
            if (idx == stringPool.size()) {
                stringPool.add(value);
            } else {
                stringPool.set(idx, value);
            }
            return value;
        } else {
            assert idx <= MAX_POOL_IDX;
            return stringPool.get(idx);
        }
    }

    /**
     * Thrown if the format of the serialized continuation is unrecognized i.e. from a newer version
     * of the runtime, or from a version too old to still be supported.
     */
    public static final class FormatVersionException extends IOException {
        @Serial private static final long serialVersionUID = 6913545866116536598L;

        public FormatVersionException(int version) {
            super("Unsupported serialized continuation version: " + version);
        }
    }

    // endregion

    // region Implementation

    private static final boolean ASSERTIONS_ENABLED = Continuation.class.desiredAssertionStatus();

    /**
     * Invoked by the VM. This is the first frame in the continuation. We get here from inside the
     * substituted start0 method.
     */
    @SuppressWarnings("unused")
    private void run() {
        assert state == State.RUNNING : state; // set in #resume()
        var cap = new SuspendCapability();
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
                throw e;
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
                sb.append(cursor.bci);
                sb.append("\n");
                sb.append("  Stack pointer: ");
                sb.append(cursor.top);
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
                cursor = cursor.next;
            }
        }
        return sb.toString();
    }

    // VM knows about this method and will check it is the last on the frame record.
    private void suspend() {
        suspend0();
    }

    private native void start0();

    private native void resume0();

    private native void suspend0();
    // endregion
}
