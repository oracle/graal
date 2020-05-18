/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.Constants;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.nodes.quick.QuickNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.EspressoLock;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Host;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_Thread;
import com.oracle.truffle.espresso.substitutions.Throws;

public final class InterpreterToVM implements ContextAccess {

    private final EspressoContext context;

    public InterpreterToVM(EspressoContext context) {
        this.context = context;
    }

    @Override
    public EspressoContext getContext() {
        return context;
    }

    // region Get (array) operations

    public int getArrayInt(int index, @Host(int[].class) StaticObject array) {
        try {
            return (array.<int[]> unwrap())[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            Meta meta = getMeta();
            throw Meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, e.getMessage());
        }
    }

    public StaticObject getArrayObject(int index, @Host(Object[].class) StaticObject array) {
        try {
            return (array.<StaticObject[]> unwrap())[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            Meta meta = getMeta();
            throw Meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, e.getMessage());
        }
    }

    public long getArrayLong(int index, @Host(long[].class) StaticObject array) {
        try {
            return (array.<long[]> unwrap())[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            Meta meta = getMeta();
            throw Meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, e.getMessage());
        }
    }

    public float getArrayFloat(int index, @Host(float[].class) StaticObject array) {
        try {
            return (array.<float[]> unwrap())[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            Meta meta = getMeta();
            throw Meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, e.getMessage());
        }
    }

    public double getArrayDouble(int index, @Host(double[].class) StaticObject array) {
        try {
            return (array.<double[]> unwrap())[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            Meta meta = getMeta();
            throw Meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, e.getMessage());
        }
    }

    public byte getArrayByte(int index, @Host(byte[].class /* or boolean[] */) StaticObject array) {
        return array.getArrayByte(index, getMeta());
    }

    public char getArrayChar(int index, @Host(char[].class) StaticObject array) {
        try {
            return (array.<char[]> unwrap())[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            Meta meta = getMeta();
            throw Meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, e.getMessage());
        }
    }

    public short getArrayShort(int index, @Host(short[].class) StaticObject array) {
        try {
            return (array.<short[]> unwrap())[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            Meta meta = getMeta();
            throw Meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, e.getMessage());
        }
    }

    // endregion

    // region Set (array) operations

    public void setArrayInt(int value, int index, @Host(int[].class) StaticObject array) {
        try {
            (array.<int[]> unwrap())[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            Meta meta = getMeta();
            throw Meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, e.getMessage());
        }
    }

    public void setArrayLong(long value, int index, @Host(long[].class) StaticObject array) {
        try {
            (array.<long[]> unwrap())[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            Meta meta = getMeta();
            throw Meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, e.getMessage());
        }
    }

    public void setArrayFloat(float value, int index, @Host(float[].class) StaticObject array) {
        try {
            (array.<float[]> unwrap())[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            Meta meta = getMeta();
            throw Meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, e.getMessage());
        }
    }

    public void setArrayDouble(double value, int index, @Host(double[].class) StaticObject array) {
        try {
            (array.<double[]> unwrap())[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            Meta meta = getMeta();
            throw Meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, e.getMessage());
        }
    }

    public void setArrayByte(byte value, int index, @Host(byte[].class /* or boolean[] */) StaticObject arr) {
        arr.setArrayByte(value, index, getMeta());
    }

    public void setArrayChar(char value, int index, @Host(char[].class) StaticObject array) {
        try {
            (array.<char[]> unwrap())[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            Meta meta = getMeta();
            throw Meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, e.getMessage());
        }
    }

    public void setArrayShort(short value, int index, @Host(short[].class) StaticObject array) {
        try {
            (array.<short[]> unwrap())[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            Meta meta = getMeta();
            throw Meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, e.getMessage());
        }
    }

    public void setArrayObject(StaticObject value, int index, StaticObject wrapper) {
        wrapper.putObject(value, index, getMeta());
    }

    // endregion

    // region Monitor enter/exit

    @TruffleBoundary
    public static void monitorEnter(@Host(Object.class) StaticObject obj) {
        final EspressoLock lock = obj.getLock();
        EspressoContext context = obj.getKlass().getContext();
        if (!lock.tryLock()) {
            Meta meta = context.getMeta();
            StaticObject thread = context.getCurrentThread();
            Target_java_lang_Thread.fromRunnable(thread, meta, Target_java_lang_Thread.State.BLOCKED);
            if (context.EnableManagement) {
                // Locks bookkeeping.
                thread.setHiddenField(meta.HIDDEN_THREAD_BLOCKED_OBJECT, obj);
                Field blockedCount = meta.HIDDEN_THREAD_BLOCKED_COUNT;
                Target_java_lang_Thread.incrementThreadCounter(thread, blockedCount);
            }
            context.getJDWPListener().onContendedMonitorEnter(obj);
            lock.lock();
            context.getJDWPListener().onContendedMonitorEntered(obj);
            if (context.EnableManagement) {
                thread.setHiddenField(meta.HIDDEN_THREAD_BLOCKED_OBJECT, null);
            }
            Target_java_lang_Thread.toRunnable(thread, meta, Target_java_lang_Thread.State.RUNNABLE);
        }
        context.getJDWPListener().onMonitorEnter(obj);
    }

    @TruffleBoundary
    public static void monitorExit(@Host(Object.class) StaticObject obj) {
        final EspressoLock lock = obj.getLock();
        if (!lock.isHeldByCurrentThread()) {
            // No owner checks in SVM. This is a safeguard against unbalanced monitor accesses until
            // Espresso has its own monitor handling.
            Meta meta = EspressoLanguage.getCurrentContext().getMeta();
            throw Meta.throwException(meta.java_lang_IllegalMonitorStateException);
        }
        lock.unlock();
        obj.getKlass().getContext().getJDWPListener().onMonitorExit(obj);
    }

    // endregion

    public static boolean getFieldBoolean(StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Boolean && field.getDeclaringKlass().isAssignableFrom(obj.getKlass());
        return obj.getBooleanField(field);
    }

    public static int getFieldInt(StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Int && field.getDeclaringKlass().isAssignableFrom(obj.getKlass());
        return obj.getIntField(field);
    }

    public static long getFieldLong(StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Long && field.getDeclaringKlass().isAssignableFrom(obj.getKlass());
        return obj.getLongField(field);
    }

    public static byte getFieldByte(StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Byte && field.getDeclaringKlass().isAssignableFrom(obj.getKlass());
        return obj.getByteField(field);
    }

    public static short getFieldShort(StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Short && field.getDeclaringKlass().isAssignableFrom(obj.getKlass());
        return obj.getShortField(field);
    }

    public static float getFieldFloat(StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Float && field.getDeclaringKlass().isAssignableFrom(obj.getKlass());
        return obj.getFloatField(field);
    }

    public static double getFieldDouble(StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Double && field.getDeclaringKlass().isAssignableFrom(obj.getKlass());
        return obj.getDoubleField(field);
    }

    public static StaticObject getFieldObject(StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Object && field.getDeclaringKlass().isAssignableFrom(obj.getKlass());
        return obj.getField(field);
    }

    public static char getFieldChar(StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Char && field.getDeclaringKlass().isAssignableFrom(obj.getKlass());
        return obj.getCharField(field);
    }

    public static void setFieldBoolean(boolean value, StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Boolean && field.getDeclaringKlass().isAssignableFrom(obj.getKlass());
        obj.setBooleanField(field, value);
    }

    public static void setFieldByte(byte value, StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Byte && field.getDeclaringKlass().isAssignableFrom(obj.getKlass());
        obj.setByteField(field, value);
    }

    public static void setFieldChar(char value, StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Char && field.getDeclaringKlass().isAssignableFrom(obj.getKlass());
        obj.setCharField(field, value);
    }

    public static void setFieldShort(short value, StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Short && field.getDeclaringKlass().isAssignableFrom(obj.getKlass());
        obj.setShortField(field, value);
    }

    public static void setFieldInt(int value, StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Int && field.getDeclaringKlass().isAssignableFrom(obj.getKlass());
        obj.setIntField(field, value);
    }

    public static void setFieldLong(long value, StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Long && field.getDeclaringKlass().isAssignableFrom(obj.getKlass());
        obj.setLongField(field, value);
    }

    public static void setFieldFloat(float value, StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Float && field.getDeclaringKlass().isAssignableFrom(obj.getKlass());
        obj.setFloatField(field, value);
    }

    public static void setFieldDouble(double value, StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Double && field.getDeclaringKlass().isAssignableFrom(obj.getKlass());
        obj.setDoubleField(field, value);
    }

    public static void setFieldObject(StaticObject value, StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Object && field.getDeclaringKlass().isAssignableFrom(obj.getKlass());
        obj.setField(field, value);
    }

    public static StaticObject newReferenceArray(Klass componentType, int length) {
        if (length < 0) {
            // componentType is not always PE constant e.g. when called from the Array#newInstance
            // substitution. The derived context and meta accessor are not PE constant
            // either, so neither is componentType.getMeta().java_lang_NegativeArraySizeException.
            // The exception mechanism requires exception classes to be PE constant in order to
            // PE through exception allocation and initialization.
            // The definitive solution would be to distinguish the cases where the exception klass
            // is PE constant from the cases where it's dynamic. We can further reduce the dynamic
            // cases with an inline cache in the above substitution.
            throw throwNegativeArraySizeException(componentType.getMeta());
        }
        assert length >= 0;
        StaticObject[] arr = new StaticObject[length];
        Arrays.fill(arr, StaticObject.NULL);
        return StaticObject.createArray(componentType.getArrayClass(), arr);
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    private static EspressoException throwNegativeArraySizeException(Meta meta) {
        throw Meta.throwException(meta.java_lang_NegativeArraySizeException);
    }

    @TruffleBoundary
    public StaticObject newMultiArray(Klass component, int... dimensions) {
        Meta meta = getMeta();
        if (component == meta._void) {
            throw Meta.throwException(meta.java_lang_IllegalArgumentException);
        }
        for (int d : dimensions) {
            if (d < 0) {
                throw Meta.throwException(meta.java_lang_NegativeArraySizeException);
            }
        }
        return newMultiArrayWithoutChecks(component, dimensions);
    }

    private static StaticObject newMultiArrayWithoutChecks(Klass component, int... dimensions) {
        assert dimensions != null && dimensions.length > 0;
        if (dimensions.length == 1) {
            if (component.isPrimitive()) {
                return allocatePrimitiveArray((byte) component.getJavaKind().getBasicType(), dimensions[0]);
            } else {
                return component.allocateReferenceArray(dimensions[0], new IntFunction<StaticObject>() {
                    @Override
                    public StaticObject apply(int value) {
                        return StaticObject.NULL;
                    }
                });
            }
        }
        int[] newDimensions = Arrays.copyOfRange(dimensions, 1, dimensions.length);
        return component.allocateReferenceArray(dimensions[0], new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int i) {
                return newMultiArrayWithoutChecks(((ArrayKlass) component).getComponentType(), newDimensions);
            }

        });
    }

    public static StaticObject allocatePrimitiveArray(byte jvmPrimitiveType, int length) {
        // the constants for the cpi are loosely defined and no real cpi indices.
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        if (length < 0) {
            throw Meta.throwException(meta.java_lang_NegativeArraySizeException);
        }
        // @formatter:off
        switch (jvmPrimitiveType) {
            case 4  : return StaticObject.wrap(new boolean[length], meta);
            case 5  : return StaticObject.wrap(new char[length], meta);
            case 6  : return StaticObject.wrap(new float[length], meta);
            case 7  : return StaticObject.wrap(new double[length], meta);
            case 8  : return StaticObject.wrap(new byte[length], meta);
            case 9  : return StaticObject.wrap(new short[length], meta);
            case 10 : return StaticObject.wrap(new int[length], meta);
            case 11 : return StaticObject.wrap(new long[length], meta);
            default :
                CompilerDirectives.transferToInterpreter();
                throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
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
        throw Meta.throwException(meta.java_lang_ClassCastException);
    }

    /**
     * Allocates a new instance of the given class; does not call any constructor. If the class is
     * instantiable, it is initialized.
     * 
     * @param throwsError if the given class is not instantiable (abstract or interface); if true
     *            throws guest {@link InstantiationError} otherwise throws guest
     *            {@link InstantiationException}.
     */
    @Throws({InstantiationError.class, InstantiationException.class})
    public static StaticObject newObject(Klass klass, boolean throwsError) {
        // TODO(peterssen): Accept only ObjectKlass.
        assert klass != null && !klass.isArray() && !klass.isPrimitive() : klass;
        if (klass.isAbstract() || klass.isInterface()) {
            Meta meta = klass.getMeta();
            throw Meta.throwException(
                            throwsError
                                            ? meta.java_lang_InstantiationError
                                            : meta.java_lang_InstantiationException);
        }
        klass.safeInitialize();
        return StaticObject.createNew((ObjectKlass) klass);
    }

    public static int arrayLength(StaticObject arr) {
        assert arr.isArray();
        return arr.length();
    }

    public @Host(String.class) StaticObject intern(@Host(String.class) StaticObject guestString) {
        assert getMeta().java_lang_String == guestString.getKlass();
        return getStrings().intern(guestString);
    }

    /**
     * Preemptively added method to benefit from truffle lazy stack traces when they will be
     * reworked.
     */
    @SuppressWarnings("unused")
    public static StaticObject fillInStackTrace(@Host(Throwable.class) StaticObject throwable, Meta meta) {
        // Inlined calls to help StackOverflows.
        VM.StackTrace frames = (VM.StackTrace) throwable.getUnsafeField(meta.HIDDEN_FRAMES.getFieldIndex());
        if (frames != null) {
            return throwable;
        }
        EspressoException e = EspressoException.wrap(throwable);
        List<TruffleStackTraceElement> trace = TruffleStackTrace.getStackTrace(e);
        if (trace == null) {
            throwable.setHiddenField(meta.HIDDEN_FRAMES, VM.StackTrace.EMPTY_STACK_TRACE);
            throwable.setField(meta.java_lang_Throwable_backtrace, throwable);
            return throwable;
        }
        int bci = -1;
        Method m = null;
        frames = new VM.StackTrace();
        FrameCounter c = new FrameCounter();
        for (TruffleStackTraceElement element : trace) {
            Node location = element.getLocation();
            while (location != null) {
                if (location instanceof QuickNode) {
                    bci = ((QuickNode) location).getBCI();
                    break;
                }
                location = location.getParent();
            }
            RootCallTarget target = element.getTarget();
            if (target != null) {
                RootNode rootNode = target.getRootNode();
                if (rootNode instanceof EspressoRootNode) {
                    m = ((EspressoRootNode) rootNode).getMethod();
                    if (c.checkFillIn(m) || c.checkThrowableInit(m)) {
                        bci = -1;
                        continue;
                    }
                    if (m.isNative()) {
                        bci = -2;
                    }
                    frames.add(new VM.StackElement(m, bci));
                    bci = -1;
                }
            }
        }
        throwable.setHiddenField(meta.HIDDEN_FRAMES, frames);
        throwable.setField(meta.java_lang_Throwable_backtrace, throwable);
        return throwable;
    }

    // Recursion depth = 4
    public static StaticObject fillInStackTrace(@Host(Throwable.class) StaticObject throwable, boolean skipFirst, Meta meta) {
        FrameCounter c = new FrameCounter();
        int size = EspressoContext.DEFAULT_STACK_SIZE;
        VM.StackTrace frames = new VM.StackTrace();
        Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Object>() {
            boolean first = skipFirst;

            @Override
            public Object visitFrame(FrameInstance frameInstance) {
                if (first) {
                    first = false;
                    return null;
                }
                if (c.value < size) {
                    CallTarget callTarget = frameInstance.getCallTarget();
                    if (callTarget instanceof RootCallTarget) {
                        RootNode rootNode = ((RootCallTarget) callTarget).getRootNode();
                        if (rootNode instanceof EspressoRootNode) {
                            EspressoRootNode espressoNode = (EspressoRootNode) rootNode;
                            Method method = espressoNode.getMethod();

                            // Methods annotated with java.lang.invoke.LambdaForm.Hidden are
                            // ignored.
                            if ((method.getModifiers() & Constants.ACC_LAMBDA_FORM_HIDDEN) == 0) {
                                if (!c.checkFillIn(method)) {
                                    if (!c.checkThrowableInit(method)) {
                                        int bci = -1; // unknown
                                        if (espressoNode.isBytecodeNode()) {
                                            bci = espressoNode.readBCI(frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY));
                                        } else if (method.isNative()) {
                                            bci = -2; // native
                                        }
                                        frames.add(new VM.StackElement(method, bci));
                                        c.inc();
                                    }
                                }
                            }
                        }
                    }
                }
                return null;
            }
        });
        throwable.setHiddenField(meta.HIDDEN_FRAMES, frames);
        throwable.setField(meta.java_lang_Throwable_backtrace, throwable);
        return throwable;
    }

    private static class FrameCounter {
        public int value = 0;
        private boolean skipFillInStackTrace = true;
        private boolean skipThrowableInit = true;

        public int inc() {
            return value++;
        }

        boolean checkFillIn(Method m) {
            if (!skipFillInStackTrace) {
                return false;
            }
            if (!((Name.fillInStackTrace.equals(m.getName())) || (Name.fillInStackTrace0.equals(m.getName())))) {
                skipFillInStackTrace = false;
            }
            return skipFillInStackTrace;
        }

        boolean checkThrowableInit(Method m) {
            if (!skipThrowableInit) {
                return false;
            }
            if (!(Name._init_.equals(m.getName())) || !m.getMeta().java_lang_Throwable.isAssignableFrom(m.getDeclaringKlass())) {
                skipThrowableInit = false;
            }
            return skipThrowableInit;
        }
    }
}
