/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.truffle.espresso.vm;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.blocking.EspressoLock;
import com.oracle.truffle.espresso.blocking.GuestInterruptedException;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.ContextAccessImpl;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_Thread;
import com.oracle.truffle.espresso.substitutions.Throws;
import com.oracle.truffle.espresso.threads.State;
import com.oracle.truffle.espresso.threads.Transition;

public final class InterpreterToVM extends ContextAccessImpl {

    public InterpreterToVM(EspressoContext context) {
        super(context);
    }

    @TruffleBoundary(allowInlining = true)
    public static void monitorUnsafeEnter(EspressoLock self) {
        self.lock();
    }

    @TruffleBoundary(allowInlining = true)
    public static void monitorUnsafeExit(EspressoLock self) {
        self.unlock();
    }

    @TruffleBoundary(allowInlining = true)
    public static void monitorNotifyAll(EspressoLock self) {
        self.signalAll();
    }

    @TruffleBoundary(allowInlining = true)
    public static void monitorNotify(EspressoLock self) {
        self.signal();
    }

    public static boolean monitorWait(EspressoLock self, long timeout) throws GuestInterruptedException {
        return self.await(timeout, TimeUnit.MILLISECONDS, null, null);
    }

    @TruffleBoundary(allowInlining = true)
    public static boolean monitorWait(EspressoLock self, long timeout, StaticObject thread, StaticObject obj) throws GuestInterruptedException {
        return self.await(timeout, TimeUnit.MILLISECONDS, thread, obj);
    }

    @TruffleBoundary(allowInlining = true)
    public static boolean monitorTryLock(EspressoLock lock) {
        return lock.tryLock();
    }

    @TruffleBoundary(allowInlining = true)
    public static boolean holdsLock(EspressoLock lock) {
        return lock.isHeldByCurrentThread();
    }

    // region Get (array) operations

    public int getArrayInt(EspressoLanguage language, int index, @JavaType(int[].class) StaticObject array) {
        return getArrayInt(language, index, array, null);
    }

    @TruffleBoundary
    public static String outOfBoundsMessage(int index, int length) {
        return "Index " + index + " out of bounds for length " + length;
    }

    public int getArrayInt(EspressoLanguage language, int index, @JavaType(int[].class) StaticObject array, BytecodeNode bytecodeNode) {
        int[] underlying = array.<int[]> unwrap(language);
        if (Integer.compareUnsigned(index, underlying.length) < 0) {
            return underlying[index];
        }
        if (bytecodeNode != null) {
            bytecodeNode.enterImplicitExceptionProfile();
        }
        Meta meta = getMeta();
        throw meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, outOfBoundsMessage(index, underlying.length));
    }

    public StaticObject getArrayObject(EspressoLanguage language, int index, @JavaType(Object[].class) StaticObject array) {
        return getArrayObject(language, index, array, null);
    }

    public StaticObject getArrayObject(EspressoLanguage language, int index, @JavaType(Object[].class) StaticObject array, BytecodeNode bytecodeNode) {
        StaticObject[] underlying = array.<StaticObject[]> unwrap(language);
        if (Integer.compareUnsigned(index, underlying.length) < 0) {
            return underlying[index];
        }
        if (bytecodeNode != null) {
            bytecodeNode.enterImplicitExceptionProfile();
        }
        Meta meta = getMeta();
        throw meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, outOfBoundsMessage(index, underlying.length));
    }

    public long getArrayLong(EspressoLanguage language, int index, @JavaType(long[].class) StaticObject array) {
        return getArrayLong(language, index, array, null);
    }

    public long getArrayLong(EspressoLanguage language, int index, @JavaType(long[].class) StaticObject array, BytecodeNode bytecodeNode) {
        long[] underlying = array.<long[]> unwrap(language);
        if (Integer.compareUnsigned(index, underlying.length) < 0) {
            return underlying[index];
        }
        if (bytecodeNode != null) {
            bytecodeNode.enterImplicitExceptionProfile();
        }
        Meta meta = getMeta();
        throw meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, outOfBoundsMessage(index, underlying.length));
    }

    public float getArrayFloat(EspressoLanguage language, int index, @JavaType(float[].class) StaticObject array) {
        return getArrayFloat(language, index, array, null);
    }

    public float getArrayFloat(EspressoLanguage language, int index, @JavaType(float[].class) StaticObject array, BytecodeNode bytecodeNode) {
        float[] underlying = array.<float[]> unwrap(language);
        if (Integer.compareUnsigned(index, underlying.length) < 0) {
            return underlying[index];
        }
        if (bytecodeNode != null) {
            bytecodeNode.enterImplicitExceptionProfile();
        }
        Meta meta = getMeta();
        throw meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, outOfBoundsMessage(index, underlying.length));
    }

    public double getArrayDouble(EspressoLanguage language, int index, @JavaType(double[].class) StaticObject array) {
        return getArrayDouble(language, index, array, null);
    }

    public double getArrayDouble(EspressoLanguage language, int index, @JavaType(double[].class) StaticObject array, BytecodeNode bytecodeNode) {
        double[] underlying = array.<double[]> unwrap(language);
        if (Integer.compareUnsigned(index, underlying.length) < 0) {
            return underlying[index];
        }
        if (bytecodeNode != null) {
            bytecodeNode.enterImplicitExceptionProfile();
        }
        Meta meta = getMeta();
        throw meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, outOfBoundsMessage(index, underlying.length));
    }

    public byte getArrayByte(EspressoLanguage language, int index,
                    @JavaType(byte[].class /* or boolean[].class */) StaticObject array) {
        return getArrayByte(language, index, array, null);
    }

    public byte getArrayByte(EspressoLanguage language, int index,
                    @JavaType(byte[].class /* or boolean[].class */) StaticObject array, BytecodeNode bytecodeNode) {
        byte[] underlying = array.<byte[]> unwrap(language);
        if (Integer.compareUnsigned(index, underlying.length) < 0) {
            return underlying[index];
        }
        if (bytecodeNode != null) {
            bytecodeNode.enterImplicitExceptionProfile();
        }
        Meta meta = getMeta();
        throw meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, outOfBoundsMessage(index, underlying.length));
    }

    public char getArrayChar(EspressoLanguage language, int index, @JavaType(char[].class) StaticObject array) {
        return getArrayChar(language, index, array, null);
    }

    public char getArrayChar(EspressoLanguage language, int index, @JavaType(char[].class) StaticObject array, BytecodeNode bytecodeNode) {
        char[] underlying = array.<char[]> unwrap(language);
        if (Integer.compareUnsigned(index, underlying.length) < 0) {
            return underlying[index];
        }
        if (bytecodeNode != null) {
            bytecodeNode.enterImplicitExceptionProfile();
        }
        Meta meta = getMeta();
        throw meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, outOfBoundsMessage(index, underlying.length));
    }

    public short getArrayShort(EspressoLanguage language, int index, @JavaType(short[].class) StaticObject array) {
        return getArrayShort(language, index, array, null);
    }

    public short getArrayShort(EspressoLanguage language, int index, @JavaType(short[].class) StaticObject array, BytecodeNode bytecodeNode) {
        short[] underlying = array.<short[]> unwrap(language);
        if (Integer.compareUnsigned(index, underlying.length) < 0) {
            return underlying[index];
        }
        if (bytecodeNode != null) {
            bytecodeNode.enterImplicitExceptionProfile();
        }
        Meta meta = getMeta();
        throw meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, outOfBoundsMessage(index, underlying.length));
    }

    // endregion

    // region Set (array) operations

    public void setArrayInt(EspressoLanguage language, int value, int index, @JavaType(int[].class) StaticObject array) {
        setArrayInt(language, value, index, array, null);
    }

    public void setArrayInt(EspressoLanguage language, int value, int index, @JavaType(int[].class) StaticObject array, BytecodeNode bytecodeNode) {
        int[] underlying = array.<int[]> unwrap(language);
        if (Integer.compareUnsigned(index, underlying.length) < 0) {
            underlying[index] = value;
            return;
        }
        if (bytecodeNode != null) {
            bytecodeNode.enterImplicitExceptionProfile();
        }
        Meta meta = getMeta();
        throw meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, outOfBoundsMessage(index, underlying.length));
    }

    public void setArrayLong(EspressoLanguage language, long value, int index, @JavaType(long[].class) StaticObject array) {
        setArrayLong(language, value, index, array, null);
    }

    public void setArrayLong(EspressoLanguage language, long value, int index, @JavaType(long[].class) StaticObject array, BytecodeNode bytecodeNode) {
        long[] underlying = array.<long[]> unwrap(language);
        if (Integer.compareUnsigned(index, underlying.length) < 0) {
            underlying[index] = value;
            return;
        }
        if (bytecodeNode != null) {
            bytecodeNode.enterImplicitExceptionProfile();
        }
        Meta meta = getMeta();
        throw meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, outOfBoundsMessage(index, underlying.length));
    }

    public void setArrayFloat(EspressoLanguage language, float value, int index, @JavaType(float[].class) StaticObject array) {
        setArrayFloat(language, value, index, array, null);
    }

    public void setArrayFloat(EspressoLanguage language, float value, int index, @JavaType(float[].class) StaticObject array, BytecodeNode bytecodeNode) {
        float[] underlying = array.<float[]> unwrap(language);
        if (Integer.compareUnsigned(index, underlying.length) < 0) {
            underlying[index] = value;
            return;
        }
        if (bytecodeNode != null) {
            bytecodeNode.enterImplicitExceptionProfile();
        }
        Meta meta = getMeta();
        throw meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, outOfBoundsMessage(index, underlying.length));
    }

    public void setArrayDouble(EspressoLanguage language, double value, int index, @JavaType(double[].class) StaticObject array) {
        setArrayDouble(language, value, index, array, null);
    }

    public void setArrayDouble(EspressoLanguage language, double value, int index, @JavaType(double[].class) StaticObject array, BytecodeNode bytecodeNode) {
        double[] underlying = array.<double[]> unwrap(language);
        if (Integer.compareUnsigned(index, underlying.length) < 0) {
            underlying[index] = value;
            return;
        }
        if (bytecodeNode != null) {
            bytecodeNode.enterImplicitExceptionProfile();
        }
        Meta meta = getMeta();
        throw meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, outOfBoundsMessage(index, underlying.length));
    }

    public void setArrayByte(EspressoLanguage language, byte value, int index,
                    @JavaType(byte[].class /* or boolean[].class */) StaticObject array) {
        setArrayByte(language, value, index, array, null);
    }

    public void setArrayByte(EspressoLanguage language, byte value, int index,
                    @JavaType(byte[].class /* or boolean[].class */) StaticObject array, BytecodeNode bytecodeNode) {
        byte maybeMaskedValue = getJavaVersion().java9OrLater() && array.getKlass() == getMeta()._boolean_array ? (byte) (value & 1) : value;
        byte[] underlying = array.<byte[]> unwrap(language);
        if (Integer.compareUnsigned(index, underlying.length) < 0) {
            underlying[index] = maybeMaskedValue;
            return;
        }
        if (bytecodeNode != null) {
            bytecodeNode.enterImplicitExceptionProfile();
        }
        Meta meta = getMeta();
        throw meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, outOfBoundsMessage(index, underlying.length));
    }

    public void setArrayChar(EspressoLanguage language, char value, int index, @JavaType(char[].class) StaticObject array) {
        setArrayChar(language, value, index, array, null);
    }

    public void setArrayChar(EspressoLanguage language, char value, int index, @JavaType(char[].class) StaticObject array, BytecodeNode bytecodeNode) {
        char[] underlying = array.<char[]> unwrap(language);
        if (Integer.compareUnsigned(index, underlying.length) < 0) {
            underlying[index] = value;
            return;
        }
        if (bytecodeNode != null) {
            bytecodeNode.enterImplicitExceptionProfile();
        }
        Meta meta = getMeta();
        throw meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, outOfBoundsMessage(index, underlying.length));
    }

    public void setArrayShort(EspressoLanguage language, short value, int index, @JavaType(short[].class) StaticObject array) {
        setArrayShort(language, value, index, array, null);
    }

    public void setArrayShort(EspressoLanguage language, short value, int index, @JavaType(short[].class) StaticObject array, BytecodeNode bytecodeNode) {
        short[] underlying = array.<short[]> unwrap(language);
        if (Integer.compareUnsigned(index, underlying.length) < 0) {
            underlying[index] = value;
            return;
        }
        if (bytecodeNode != null) {
            bytecodeNode.enterImplicitExceptionProfile();
        }
        Meta meta = getMeta();
        throw meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, outOfBoundsMessage(index, underlying.length));
    }

    public void setArrayObject(EspressoLanguage language, StaticObject value, int index, StaticObject wrapper) {
        setArrayObject(language, value, index, wrapper, null);
    }

    public void setArrayObject(EspressoLanguage language, StaticObject value, int index, StaticObject wrapper, BytecodeNode bytecodeNode) {
        StaticObject[] underlying = wrapper.<StaticObject[]> unwrap(language);
        if (Integer.compareUnsigned(index, underlying.length) < 0) {
            if (StaticObject.isNull(value) || instanceOf(value, ((ArrayKlass) wrapper.getKlass()).getComponentType())) {
                underlying[index] = value;
                return;
            } // else throw ArrayStoreException
            if (bytecodeNode != null) {
                bytecodeNode.enterImplicitExceptionProfile();
            }
            Meta meta = getMeta();
            throw meta.throwExceptionWithMessage(meta.java_lang_ArrayStoreException, value.getKlass().getTypeAsString());
        } // else throw ArrayIndexOutOfBoundsException
        if (bytecodeNode != null) {
            bytecodeNode.enterImplicitExceptionProfile();
        }
        Meta meta = getMeta();
        throw meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, outOfBoundsMessage(index, underlying.length));
    }

    // endregion

    // region Monitor enter/exit

    public static void monitorEnter(@JavaType(Object.class) StaticObject obj, Meta meta) {
        meta.getContext().getLanguage().getThreadLocalState().blockContinuationSuspension();
        final EspressoLock lock = obj.getLock(meta.getContext());
        EspressoContext context = meta.getContext();
        if (!monitorTryLock(lock)) {
            contendedMonitorEnter(obj, meta, lock, context);
        }
    }

    @TruffleBoundary /*- Throwable.addSuppressed blocklisted by SVM (from try-with-resources) */
    @SuppressWarnings("try")
    private static void contendedMonitorEnter(StaticObject obj, Meta meta, EspressoLock lock, EspressoContext context) {
        StaticObject thread = context.getCurrentPlatformThread();
        try (Transition transition = Transition.transition(context, State.BLOCKED)) {
            if (context.getEspressoEnv().EnableManagement) {
                // Locks bookkeeping.
                meta.HIDDEN_THREAD_PENDING_MONITOR.setHiddenObject(thread, obj);
                Field blockedCount = meta.HIDDEN_THREAD_BLOCKED_COUNT;
                Target_java_lang_Thread.incrementThreadCounter(thread, blockedCount);
            }
            final boolean report = context.shouldReportVMEvents();
            if (report) {
                context.reportOnContendedMonitorEnter(obj);
            }
            monitorUnsafeEnter(lock);
            if (report) {
                context.reportOnContendedMonitorEntered(obj);
            }
            if (context.getEspressoEnv().EnableManagement) {
                meta.HIDDEN_THREAD_PENDING_MONITOR.setHiddenObject(thread, null);
            }
        }
    }

    public static void monitorExit(@JavaType(Object.class) StaticObject obj, Meta meta) {
        final EspressoLock lock = obj.getLock(meta.getContext());
        if (!holdsLock(lock)) {
            // No owner checks in SVM. This is a safeguard against unbalanced monitor accesses until
            // Espresso has its own monitor handling.
            throw meta.throwException(meta.java_lang_IllegalMonitorStateException);
        }
        meta.getContext().getLanguage().getThreadLocalState().unblockContinuationSuspension();
        monitorUnsafeExit(lock);
    }

    // endregion

    public static boolean getFieldBoolean(StaticObject obj, Field field) {
        return field.getBoolean(obj);
    }

    public static int getFieldInt(StaticObject obj, Field field) {
        return field.getInt(obj);
    }

    public static long getFieldLong(StaticObject obj, Field field) {
        return field.getLong(obj);
    }

    public static byte getFieldByte(StaticObject obj, Field field) {
        return field.getByte(obj);
    }

    public static short getFieldShort(StaticObject obj, Field field) {
        return field.getShort(obj);
    }

    public static float getFieldFloat(StaticObject obj, Field field) {
        return field.getFloat(obj);
    }

    public static double getFieldDouble(StaticObject obj, Field field) {
        return field.getDouble(obj);
    }

    public static StaticObject getFieldObject(StaticObject obj, Field field) {
        return field.getObject(obj);
    }

    public static char getFieldChar(StaticObject obj, Field field) {
        return field.getChar(obj);
    }

    public static void setFieldBoolean(boolean value, StaticObject obj, Field field) {
        field.setBoolean(obj, value);
    }

    public static void setFieldByte(byte value, StaticObject obj, Field field) {
        field.setByte(obj, value);
    }

    public static void setFieldChar(char value, StaticObject obj, Field field) {
        field.setChar(obj, value);
    }

    public static void setFieldShort(short value, StaticObject obj, Field field) {
        field.setShort(obj, value);
    }

    public static void setFieldInt(int value, StaticObject obj, Field field) {
        field.setInt(obj, value);
    }

    public static void setFieldLong(long value, StaticObject obj, Field field) {
        field.setLong(obj, value);
    }

    public static void setFieldFloat(float value, StaticObject obj, Field field) {
        field.setFloat(obj, value);
    }

    public static void setFieldDouble(double value, StaticObject obj, Field field) {
        field.setDouble(obj, value);
    }

    public static void setFieldObject(StaticObject value, StaticObject obj, Field field) {
        field.setObject(obj, value);
    }

    /**
     * Subtyping among Array Types
     *
     * The following rules define the direct supertype relation among array types:
     *
     * <ul>
     * <li>If S and T are both reference types, then S[] >1 T[] iff S >1 T.
     * <li>Object >1 Object[]
     * <li>Cloneable >1 Object[]
     * <li>java.io.Serializable >1 Object[]
     * <li>If P is a primitive type, then: Object >1 P[] Cloneable >1 P[] java.io.Serializable >1
     * P[]
     * </ul>
     */
    public static boolean instanceOf(StaticObject instance, Klass typeToCheck) {
        if (StaticObject.isNull(instance)) {
            return false;
        }
        return typeToCheck.isAssignableFrom(instance.getKlass());
    }

    @Throws(ClassCastException.class)
    public static StaticObject checkCast(StaticObject instance, Klass klass) {
        if (StaticObject.isNull(instance) || instanceOf(instance, klass)) {
            return instance;
        }
        Meta meta = klass.getMeta();
        throw meta.throwException(meta.java_lang_ClassCastException);
    }

    public static int arrayLength(StaticObject arr, EspressoLanguage language) {
        assert arr.isArray();
        return arr.length(language);
    }

    public static boolean referenceIdentityEqual(StaticObject o1, StaticObject o2, EspressoLanguage language) {
        if (o1 == o2) {
            return true;
        }
        // Espresso null == foreign null
        if (StaticObject.isNull(o1) && StaticObject.isNull(o2)) {
            return true;
        }
        // an Espresso object can never be identical to a foreign object
        if (o1.isForeignObject() && o2.isForeignObject()) {
            Object foreignOp1 = o1.rawForeignObject(language);
            Object foreignOp2 = o2.rawForeignObject(language);
            InteropLibrary operand1Lib = InteropLibrary.getUncached(foreignOp1);
            InteropLibrary operand2Lib = InteropLibrary.getUncached(foreignOp2);
            return operand1Lib.isIdentical(foreignOp1, foreignOp2, operand2Lib);
        }
        return false;
    }

    public @JavaType(String.class) StaticObject intern(@JavaType(String.class) StaticObject guestString) {
        assert getMeta().java_lang_String == guestString.getKlass();
        return getStrings().intern(guestString);
    }

    /**
     * Preemptively added method to benefit from truffle lazy stack traces when they will be
     * reworked.
     */
    public static StaticObject fillInStackTraceLazy(@JavaType(Throwable.class) StaticObject throwable, Meta meta) {
        // Inlined calls to help StackOverflows.
        VM.StackTrace frames = (VM.StackTrace) meta.HIDDEN_FRAMES.getHiddenObject(throwable);
        if (frames != null) {
            return throwable;
        }
        EspressoException e = EspressoException.wrap(throwable, meta);
        List<TruffleStackTraceElement> trace = TruffleStackTrace.getStackTrace(e);
        if (trace == null) {
            meta.HIDDEN_FRAMES.setHiddenObject(throwable, VM.StackTrace.EMPTY_STACK_TRACE);
            meta.java_lang_Throwable_backtrace.setObject(throwable, throwable);
            return throwable;
        }
        frames = new VM.StackTrace();
        FrameFilter filter = new FillInStackTraceFramesFilter();
        for (TruffleStackTraceElement element : trace) {
            RootCallTarget target = element.getTarget();
            if (target != null) {
                RootNode rootNode = target.getRootNode();
                if (rootNode instanceof EspressoRootNode) {
                    Method m = ((EspressoRootNode) rootNode).getMethod();
                    if (!filter.include(m)) {
                        continue;
                    }
                    frames.add(new VM.EspressoStackElement(m, element.getBytecodeIndex()));
                }
            }
        }
        meta.HIDDEN_FRAMES.setHiddenObject(throwable, frames);
        meta.java_lang_Throwable_backtrace.setObject(throwable, throwable);
        return throwable;
    }

    // Recursion depth = 4
    public static StaticObject fillInStackTrace(@JavaType(Throwable.class) StaticObject throwable, Meta meta) {
        VM.StackTrace frames = getStackTrace(new FillInStackTraceFramesFilter(), EspressoContext.DEFAULT_STACK_SIZE);
        meta.HIDDEN_FRAMES.setHiddenObject(throwable, frames);
        meta.java_lang_Throwable_backtrace.setObject(throwable, throwable);
        if (meta.getJavaVersion().java9OrLater()) {
            meta.java_lang_Throwable_depth.setInt(throwable, frames.size);
        }
        return throwable;
    }

    public static final int MAX_STACK_DEPTH = Integer.MAX_VALUE;

    public static VM.StackTrace getStackTrace(FrameFilter filter, int maxDepth) {
        assert maxDepth >= 0;
        VM.StackTrace frames = new VM.StackTrace();
        if (maxDepth == 0) {
            return frames;
        }
        Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<>() {
            int count;

            @Override
            public Object visitFrame(FrameInstance frameInstance) {
                if (count >= maxDepth) {
                    return this; // stop iteration
                }
                CallTarget callTarget = frameInstance.getCallTarget();
                if (callTarget instanceof RootCallTarget) {
                    RootNode rootNode = ((RootCallTarget) callTarget).getRootNode();
                    if (rootNode instanceof EspressoRootNode) {
                        EspressoRootNode espressoNode = (EspressoRootNode) rootNode;
                        Method method = espressoNode.getMethod();

                        if (filter.include(method)) {
                            int bci = espressoNode.readBCI(frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY));
                            frames.add(new VM.EspressoStackElement(method, bci));
                            count++;
                        } else {
                            if (count == 0 && !DefaultHiddenFramesFilter.INSTANCE.include(method)) {
                                frames.markTopFrameHidden();
                            }
                        }
                    }
                }
                return null;
            }
        });
        return frames;
    }

    public static class DefaultHiddenFramesFilter implements FrameFilter {
        public static final DefaultHiddenFramesFilter INSTANCE = new DefaultHiddenFramesFilter();

        @Override
        public boolean include(Method m) {
            // Methods annotated with java.lang.invoke.LambdaForm.Hidden are ignored.
            return !m.isHidden();
        }
    }

    private static final class FillInStackTraceFramesFilter extends DefaultHiddenFramesFilter {
        private boolean afterFillInStackTrace;
        private boolean afterThrowableInit;

        @Override
        public boolean include(Method m) {
            if (!super.include(m)) {
                return false;
            }
            if (!afterFillInStackTrace) {
                if (Name.fillInStackTrace.equals(m.getName()) || Name.fillInStackTrace0.equals(m.getName())) {
                    return false;
                } else {
                    afterFillInStackTrace = true;
                }
            }
            if (!afterThrowableInit) {
                assert afterFillInStackTrace;
                if (Name._init_.equals(m.getName()) && m.getMeta().java_lang_Throwable.isAssignableFrom(m.getDeclaringKlass())) {
                    return false;
                } else {
                    afterThrowableInit = true;
                }
            }
            return true;
        }
    }

    public interface FrameFilter {
        boolean include(Method m);
    }
}
