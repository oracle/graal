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
import java.lang.reflect.Method;
import java.util.Arrays;

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
 * continuation as failed. Resuming the exception after that point will fail with {@link
 * IllegalStateException}. If you want to retry a failed continuation you must have a clone from
 * before the failure (see below).
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

    // region Suspended state

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
         * The stack pointer (how many slots are used at the current bytecode index).
         */
        public final int sp;

        /**
         * Location in the program source where the suspend happened (versus location in the
         * bytecode).
         */
        public final int statementIndex;

        /**
         * Reserved. Will always be null when using release builds of the JVM.
         */
        public final Object reserved1;

        // Invoked by the VM.
        FrameRecord(Object[] pointers, long[] primitives, Method method, int sp, int statementIndex, Object reserved1) {
            this.pointers = pointers;
            this.primitives = primitives;
            this.method = method;
            this.sp = sp;
            this.statementIndex = statementIndex;
            this.reserved1 = reserved1;
        }
    }

    /**
     * The entry point as provided to the constructor.
     */
    public EntryPoint entryPoint;

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
    private static final class StateHolder implements Serializable {
        @Serial
        private static final long serialVersionUID = -4139336648021552606L;

        State state = State.INCOMPLETE;
    }

    private StateHolder stateHolder = new StateHolder();

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
        this.stateHolder.state = State.NEW;
        this.entryPoint = entryPoint;
    }

    /**
     * This constructor is intended only to allow deserialization. You shouldn't use it directly.
     */
    public Continuation() {
        // Note: can't mark this as @hidden in javadoc because the doclet fork used by GraalVM
        // is too old to understand it.
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
    public static final class SuspendCapability implements Serializable {
        @Serial
        private static final long serialVersionUID = 4790341975992263909L;

        // Will be assigned separately to break the cycle that occurs because this object has to be
        // on the entry stack.
        StateHolder stateHolder;

        /**
         * Suspends the continuation, unwinding the stack to the point at which it was previously
         * resumed.
         *
         * @throws IllegalStateException if you try to call this outside a continuation, or if
         * there are native frames on the stack, or if the thread is inside a synchronized block.
         */
        public void suspend() {
            if (!insideContinuation.get())
                throw new IllegalStateException("Suspend capabilities can only be used inside a continuation.");
            stateHolder.state = State.SUSPENDED;
            Continuation.suspend0();
            stateHolder.state = State.RUNNING;
        }
    }

    /**
     * <p>
     * Runs the continuation until it either completes or calls {@link SuspendCapability#suspend()}.
     * The difference between the two reasons for returning is visible in {@link #getState()}. A
     * continuation may not be resumed if it's already {@link State#COMPLETED} or {@link
     * State#FAILED}, nor if it is already {@link State#RUNNING}.
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
        if (stateHolder.state == State.INCOMPLETE)
            throw new IllegalStateException("Do not construct this class using the no-arg constructor, which is there only for deserialization purposes.");

        // Are we in the special waiting-to-start state?
        if (stateHolder.state == State.NEW) {
            if (entryPoint == null)
                throw new IllegalStateException("The entry point is not set. Do not use the public no-args constructor to create this class, it's only for serialization.");
            // Enable the use of suspend capabilities.
            insideContinuation.set(true);
            try {
                start0();
            } finally {
                insideContinuation.set(false);
            }
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

        // Enable the use of suspend capabilities.
        insideContinuation.set(true);
        try {
            resume0();
        } finally {
            insideContinuation.set(false);
        }
    }

    // endregion

    // region Serialization
    @Serial
    private static final long serialVersionUID = -5833405097154096157L;

    private static final int FORMAT_VERSION = 1;

    /**
     * Serializes the continuation using an internal format. The {@link ObjectOutput} will receive
     * some opaque bytes followed by writes of the objects pointed to by the stack. It's up to
     * the serialization engine to recursively serialize everything that's reachable.
     */
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        var state = getState();
        if (state == State.RUNNING)
            throw new IllegalStateException("You cannot serialize a continuation whilst it's running, as this would have unclear semantics. Please suspend first.");

        // We start by writing out a header byte. The high nibble contains a major version. Old
        // libraries will refuse to deserialize continuations with a higher version than what they
        // recognize. New libraries may choose to continue supporting the old formats. The low
        // nibble contains flags.
        int header = FORMAT_VERSION << 4;
        // The first flag indicates if VM assertions are enabled, in which case we have to also
        // record the slot tag array.
        boolean writeSlotTags = hasSlotTagsInFrames();
        // We'll have statement indexes if the user is currently debugging.
        boolean writeStatementIndexes = hasStatementIndexesInFrames();
        if (writeSlotTags)
            header |= 1;
        if (writeStatementIndexes)
            header |= 2;
        out.writeByte(header);

        out.writeObject(stateHolder);
        out.writeObject(entryPoint);

        if (state == State.SUSPENDED) {
            // We serialize frame-at-a-time. Prims go first, then object pointers. Conceptually there
            // aren't two arrays, just one array of untyped slots but we don't currently know the
            // real types of the slots, so have to serialize both arrays even though they'll contain
            // quite a few nulls. There are more efficient encodings available.
            FrameRecord cursor = stackFrameHead;
            assert cursor != null;
            while (cursor != null) {
                writeFrame(out, cursor, writeSlotTags, writeStatementIndexes);
                out.writeBoolean(cursor.next != null);
                cursor = cursor.next;
            }
        }
    }

    private static void writeFrame(ObjectOutput out, FrameRecord cursor, boolean writeSlotTags, boolean writeStatementIndexes) throws IOException {
        Method method = cursor.method;
        out.writeObject(cursor.pointers);
        out.writeObject(cursor.primitives);
        writeMethodNameAndTypes(out, method);
        out.writeInt(cursor.sp);
        if (writeSlotTags)
            out.writeObject(cursor.reserved1);
        if (writeStatementIndexes)
            out.writeInt(cursor.statementIndex);
    }

    private boolean hasSlotTagsInFrames() {
        FrameRecord cursor = stackFrameHead;
        while (cursor != null) {
            if (cursor.reserved1 != null)
                return true;
            cursor = cursor.next;
        }
        return false;
    }

    private boolean hasStatementIndexesInFrames() {
        FrameRecord cursor = stackFrameHead;
        while (cursor != null) {
            if (cursor.statementIndex != -1)
                return true;
            cursor = cursor.next;
        }
        return false;
    }

    private static void writeMethodNameAndTypes(ObjectOutput out, Method method) throws IOException {
        out.writeUTF(method.getReturnType().getName());
        out.writeUTF(method.getDeclaringClass().getName());
        out.writeUTF(method.getName());
        Class<?>[] paramTypes = method.getParameterTypes();
        out.writeByte(paramTypes.length);
        for (var p : paramTypes) {
            out.writeUTF(p.getName());
        }
    }

    /**
     * Initializes the continuation from the given {@link ObjectInput}.
     *
     * @throws FormatVersionException if the header read from the stream doesn't match the expected
     * version number.
     * @throws IOException if there is a problem reading the stream, or if the stream appears to
     * be corrupted.
     */
    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        int header = in.readByte();
        boolean hasSlotTags = (header & 1) == 1;
        boolean hasStatementIndex = (header & 2) == 2;
        int version = (header >> 4) & 0xFF;
        if (version != FORMAT_VERSION)
            throw new FormatVersionException(version);

        stateHolder = (StateHolder) in.readObject();
        entryPoint = (EntryPoint) in.readObject();

        if (getState() == State.SUSPENDED) {
            try {
                FrameRecord last = null;
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
                    var frame = readFrame(in, Thread.currentThread().getContextClassLoader(), hasSlotTags, hasStatementIndex);
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
    }

    private FrameRecord readFrame(ObjectInput in, ClassLoader classLoader, boolean hasSlotTags, boolean hasStatementIndex) throws IOException, ClassNotFoundException, NoSuchMethodException {
        Object[] pointers = (Object[]) in.readObject();
        long[] primitives = (long[]) in.readObject();
        // Slot zero is always primitive (bci), so this is in slot 1.
        Method method = readMethodNameAndTypes(in, classLoader, pointers.length > 1 ? pointers[1] : null);
        int sp = in.readInt();

        Object reserved1 = null;
        if (hasSlotTags)
            reserved1 = in.readObject();

        int statementIndex = -1;
        if (hasStatementIndex)
            statementIndex = in.readInt();

        return new FrameRecord(pointers, primitives, method, sp, statementIndex, reserved1);
    }

    private Method readMethodNameAndTypes(ObjectInput in, ClassLoader classLoader, Object possibleThis) throws IOException, ClassNotFoundException, NoSuchMethodException {
        String returnTypeAsString = in.readUTF();
        String declaringClassAsString = in.readUTF();
        String name = in.readUTF();

        Class<?> returnType = classForTypeName(classLoader, returnTypeAsString);
        Class<?> declaringClass;
        // Lambda classes can't be looked up by name. This is a JVM optimization designed to avoid
        // contention on the global dictionary lock, but it means we need another way to get the
        // class for the method. Fortunately, lambdas always have an instance, so we can read it
        // out of the first pointer slot.
        if (declaringClassAsString.contains("$$Lambda/")) {
            if (possibleThis == null)
                throw new IllegalStateException("Lambda method with no this pointer in frame");
            declaringClass = possibleThis.getClass();
            if (!declaringClass.getName().contains("$$Lambda/"))
                throw new IllegalStateException("Lambda method on stack with incorrect 'this' pointer.");
        } else {
            declaringClass = Class.forName(declaringClassAsString, false, classLoader);
        }

        var numArgs = in.readUnsignedByte();
        var argTypes = new Class<?>[numArgs];
        for (int i = 0; i < numArgs; i++) {
            String typeName = in.readUTF();
            Class<?> type = classForTypeName(classLoader, typeName);
            argTypes[i] = type;
        }

        for (Method method : declaringClass.getDeclaredMethods()) {
            if (!method.getName().equals(name)) continue;
            if (!Arrays.equals(method.getParameterTypes(), argTypes)) continue;
            if (returnType != null && !method.getReturnType().equals(returnType)) continue;
            return method;
        }

        throw new NoSuchMethodException("%s %s(%s)".formatted(
                returnTypeAsString, name, String.join(", ", Arrays.stream(argTypes).map(Object::toString).toList()))
        );
    }

    private static Class<?> classForTypeName(ClassLoader classLoader, String typeName) throws ClassNotFoundException {
        return switch (typeName) {
            case "void" -> null;
            case "int" -> int.class;
            case "long" -> long.class;
            case "double" -> double.class;
            case "float" -> float.class;
            case "boolean" -> boolean.class;
            case "char" -> char.class;
            case "byte" -> byte.class;
            case "short" -> short.class;
            default -> Class.forName(typeName, false, classLoader);
        };
    }

    /**
     * Thrown if the format of the serialized continuation is unrecognized i.e. from a newer
     * version of the runtime, or from a version too old to still be supported.
     */
    public static final class FormatVersionException extends IOException {
        @Serial
        private static final long serialVersionUID = 6913545866116536598L;

        public FormatVersionException(int version) {
            super("Unsupported serialized continuation version: " + version);
        }
    }

    // endregion

    // region Implementation

    /**
     * Tracks whether the current thread has passed through {@link #resume()}. If it hasn't then
     * suspending is illegal.
     */
    private static final ThreadLocal<Boolean> insideContinuation = ThreadLocal.withInitial(() -> false);

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
                sb.append("  Statement index: ");
                sb.append(cursor.statementIndex);
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

    private static UnsupportedOperationException notOnEspresso() {
        // Caller should have been replaced by an intrinsic / substitution.
        return new UnsupportedOperationException("Continuations must be run on the Java on Truffle JVM.");
    }
    // endregion
}
