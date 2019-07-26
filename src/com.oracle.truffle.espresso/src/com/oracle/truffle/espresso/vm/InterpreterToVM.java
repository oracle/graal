/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.IntFunction;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Host;

import sun.misc.Unsafe;

public final class InterpreterToVM implements ContextAccess {

    private final EspressoContext context;

    public InterpreterToVM(EspressoContext context) {
        this.context = context;
    }

    @Override
    public EspressoContext getContext() {
        return context;
    }

    private static final Unsafe hostUnsafe;

    static {
        try {
            java.lang.reflect.Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            hostUnsafe = (Unsafe) f.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    // region Get (array) operations

    public int getArrayInt(int index, StaticObject arr) {
        try {
            return (arr.<int[]> unwrap())[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw getMeta().throwExWithMessage(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public StaticObject getArrayObject(int index, StaticObject arr) {
        try {
            return (arr.<StaticObject[]> unwrap())[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw getMeta().throwExWithMessage(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public long getArrayLong(int index, StaticObject arr) {
        try {
            return (arr.<long[]> unwrap())[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw getMeta().throwExWithMessage(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public float getArrayFloat(int index, StaticObject arr) {
        try {
            return (arr.<float[]> unwrap())[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw getMeta().throwExWithMessage(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public double getArrayDouble(int index, StaticObject arr) {
        try {
            return (arr.<double[]> unwrap())[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw getMeta().throwExWithMessage(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public byte getArrayByte(int index, StaticObject arr) {
        return arr.getArrayByte(index, getMeta());
    }

    public char getArrayChar(int index, StaticObject arr) {
        try {
            return (arr.<char[]> unwrap())[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw getMeta().throwExWithMessage(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public short getArrayShort(int index, StaticObject arr) {
        try {
            return (arr.<short[]> unwrap())[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw getMeta().throwExWithMessage(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }
    // endregion

    // region Set (array) operations
    public void setArrayInt(int value, int index, StaticObject arr) {
        try {
            (arr.<int[]> unwrap())[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw getMeta().throwExWithMessage(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public void setArrayLong(long value, int index, StaticObject arr) {
        try {
            (arr.<long[]> unwrap())[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw getMeta().throwExWithMessage(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public void setArrayFloat(float value, int index, StaticObject arr) {
        try {
            (arr.<float[]> unwrap())[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw getMeta().throwExWithMessage(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public void setArrayDouble(double value, int index, StaticObject arr) {
        try {
            (arr.<double[]> unwrap())[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw getMeta().throwExWithMessage(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public void setArrayByte(byte value, int index, StaticObject arr) {
        arr.setArrayByte(value, index, getMeta());
    }

    public void setArrayChar(char value, int index, StaticObject arr) {
        try {
            (arr.<char[]> unwrap())[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw getMeta().throwExWithMessage(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public void setArrayShort(short value, int index, StaticObject arr) {
        try {
            (arr.<short[]> unwrap())[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw getMeta().throwExWithMessage(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public void setArrayObject(StaticObject value, int index, StaticObject wrapper) {
        wrapper.putObject(value, index, getMeta());
    }

    // endregion

    // region Monitor enter/exit

    @SuppressWarnings({"deprecation"})
    @TruffleBoundary
    public static void monitorEnter(@Host(Object.class) Object obj) {
        assert obj instanceof StaticObject;
        hostUnsafe.monitorEnter(obj);
    }

    @SuppressWarnings({"deprecation"})
    @TruffleBoundary
    public static void monitorExit(@Host(Object.class) Object obj) {
        assert obj instanceof StaticObject;
        if (!Thread.holdsLock(obj)) {
            // No owner checks in SVM. This is a safeguard against unbalanced monitor accesses until
            // Espresso has its own monitor handling.
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(IllegalMonitorStateException.class);
        }
        hostUnsafe.monitorExit(obj);
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

    public static StaticObject newArray(Klass componentType, int length) {
        if (length < 0) {
            throw componentType.getContext().getMeta().throwEx(NegativeArraySizeException.class);
        }
        assert !componentType.isPrimitive() : "use allocateNativeArray for primitives";
        assert length >= 0;
        StaticObject[] arr = new StaticObject[length];
        Arrays.fill(arr, StaticObject.NULL);
        return StaticObject.createArray(componentType.getArrayClass(), arr);
    }

    @TruffleBoundary
    public StaticObject newMultiArray(Klass component, int... dimensions) {
        Meta meta = getMeta();
        if (component == meta._void) {
            throw meta.throwEx(meta.IllegalArgumentException);
        }
        for (int d : dimensions) {
            if (d < 0) {
                throw meta.throwEx(meta.NegativeArraySizeException);
            }
        }
        return newMultiArrayWithoutChecks(component, dimensions);
    }

    @TruffleBoundary
    private StaticObject newMultiArrayWithoutChecks(Klass component, int... dimensions) {
        assert dimensions != null && dimensions.length > 0;
        if (dimensions.length == 1) {
            if (component.isPrimitive()) {
                return allocatePrimitiveArray((byte) component.getJavaKind().getBasicType(), dimensions[0]);
            } else {
                return component.allocateArray(dimensions[0], new IntFunction<StaticObject>() {
                    @Override
                    public StaticObject apply(int value) {
                        return StaticObject.NULL;
                    }
                });
            }
        }
        int[] newDimensions = Arrays.copyOfRange(dimensions, 1, dimensions.length);
        return component.allocateArray(dimensions[0], new IntFunction<StaticObject>() {
            @Override
            public StaticObject apply(int i) {
                return newMultiArrayWithoutChecks(component.getComponentType(), newDimensions);
            }
        });
    }

    public static StaticObject allocatePrimitiveArray(byte jvmPrimitiveType, int length) {
        // the constants for the cpi are loosely defined and no real cpi indices.
        if (length < 0) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(NegativeArraySizeException.class);
        }
        // @formatter:off
        // Checkstyle: stop
        switch (jvmPrimitiveType) {
            case 4  : return StaticObject.wrap(new boolean[length]);
            case 5  : return StaticObject.wrap(new char[length]);
            case 6  : return StaticObject.wrap(new float[length]);
            case 7  : return StaticObject.wrap(new double[length]);
            case 8  : return StaticObject.wrap(new byte[length]);
            case 9  : return StaticObject.wrap(new short[length]);
            case 10 : return StaticObject.wrap(new int[length]);
            case 11 : return StaticObject.wrap(new long[length]);
            default : throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
        // Checkstyle: resume
    }

    /**
     * Subtyping among Array Types
     *
     * The following rules define the direct supertype relation among array types:
     *
     * - If S and T are both reference types, then S[] >1 T[] iff S >1 T. - Object >1 Object[] -
     * Cloneable >1 Object[] - java.io.Serializable >1 Object[] - If P is a primitive type, then:
     * Object >1 P[] Cloneable >1 P[] java.io.Serializable >1 P[]
     */
    public static boolean instanceOf(StaticObject instance, Klass typeToCheck) {
        if (StaticObject.isNull(instance)) {
            return false;
        }
        return typeToCheck.isAssignableFrom(instance.getKlass());
    }

    public StaticObject checkCast(StaticObject instance, Klass klass) {
        if (StaticObject.isNull(instance) || instanceOf(instance, klass)) {
            return instance;
        }
        throw getMeta().throwEx(getMeta().ClassCastException);
    }

    public static StaticObject newObject(Klass klass) {
        // TODO(peterssen): Accept only ObjectKlass.
        assert klass != null && !klass.isArray() && !klass.isPrimitive() && !klass.isAbstract() : klass;
        if (klass.isAbstract() || klass.isInterface()) {
            throw klass.getMeta().throwEx(InstantiationError.class);
        }
        klass.safeInitialize();
        return new StaticObject((ObjectKlass) klass);
    }

    public static int arrayLength(StaticObject arr) {
        return arr.length();
    }

    public @Host(String.class) StaticObject intern(@Host(String.class) StaticObject guestString) {
        assert getMeta().String == guestString.getKlass();
        return getStrings().intern(guestString);
    }

    public static StaticObject fillInStackTrace(ArrayList<Method> frames, StaticObject throwable, Meta meta) {
        FrameCounter c = new FrameCounter();
        int size = EspressoContext.DEFAULT_STACK_SIZE;
        frames.clear();
        Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Object>() {
            @Override
            public Object visitFrame(FrameInstance frameInstance) {
                if (c.value < size) {
                    CallTarget callTarget = frameInstance.getCallTarget();
                    if (callTarget instanceof RootCallTarget) {
                        RootNode rootNode = ((RootCallTarget) callTarget).getRootNode();
                        if (rootNode instanceof EspressoRootNode) {
                            if (!c.checkFillIn(((EspressoRootNode) rootNode).getMethod())) {
                                if (!c.checkThrowableInit(((EspressoRootNode) rootNode).getMethod())) {
                                    frames.add(((EspressoRootNode) rootNode).getMethod());
                                    c.inc();
                                }
                            }
                        }
                    }
                }
                return null;
            }
        });
        throwable.setHiddenField(meta.HIDDEN_FRAMES, frames.toArray(Method.EMPTY_ARRAY));
        meta.Throwable_backtrace.set(throwable, throwable);
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
            if (!((m.getName() == Symbol.Name.fillInStackTrace) || (m.getName() == Symbol.Name.fillInStackTrace0))) {
                skipFillInStackTrace = false;
            }
            return skipFillInStackTrace;
        }

        boolean checkThrowableInit(Method m) {
            if (!skipThrowableInit) {
                return false;
            }
            if (!(m.getName() == Symbol.Name.INIT) || !m.getMeta().Throwable.isAssignableFrom(m.getDeclaringKlass())) {
                skipThrowableInit = false;
            }
            return skipThrowableInit;
        }
    }
}
