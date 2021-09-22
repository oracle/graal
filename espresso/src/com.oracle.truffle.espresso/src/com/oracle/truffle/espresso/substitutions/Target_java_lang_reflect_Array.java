/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.substitutions;

import java.lang.reflect.Array;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

@EspressoSubstitutions
public final class Target_java_lang_reflect_Array {

    /**
     * Creates a new array with the specified component type and length. Invoking this method is
     * equivalent to creating an array as follows: <blockquote>
     * 
     * <pre>
     * int[] x = {length};
     * Array.newInstance(componentType, x);
     * </pre>
     * 
     * </blockquote>
     *
     * <p>
     * The number of dimensions of the new array must not exceed 255.
     *
     * @param componentType the {@code Class} object representing the component type of the new
     *            array
     * @param length the length of the new array
     * @return the new array
     * @exception NullPointerException if the specified {@code componentType} parameter is null
     * @exception IllegalArgumentException if componentType is {@link Void#TYPE} or if the number of
     *                dimensions of the requested array instance exceed 255.
     * @exception NegativeArraySizeException if the specified {@code length} is negative
     */
    @Substitution
    public static @JavaType(Object.class) StaticObject newArray(@JavaType(Class.class) StaticObject componentType, int length, @Inject Meta meta) {
        if (CompilerDirectives.isPartialEvaluationConstant(componentType)) {
            // PE-through.
            return newArrayImpl(componentType, length, meta);
        }
        return newArrayBoundary(componentType, length, meta);
    }

    @TruffleBoundary(allowInlining = true)
    static StaticObject newArrayBoundary(@JavaType(Class.class) StaticObject componentType, int length, @Inject Meta meta) {
        return newArrayImpl(componentType, length, meta);
    }

    static StaticObject newArrayImpl(@JavaType(Class.class) StaticObject componentType, int length, @Inject Meta meta) {
        if (StaticObject.isNull(componentType)) {
            throw meta.throwNullPointerException();
        }
        Klass component = componentType.getMirrorKlass();
        if (component == meta._void || Types.getArrayDimensions(component.getType()) >= 255) {
            throw meta.throwException(meta.java_lang_IllegalArgumentException);
        }
        if (component.isPrimitive()) {
            byte jvmPrimitiveType = (byte) component.getJavaKind().getBasicType();
            return InterpreterToVM.allocatePrimitiveArray(jvmPrimitiveType, length, meta);
        }
        return InterpreterToVM.newReferenceArray(component, length);
    }

    /**
     * Creates a new array with the specified component type and dimensions. If
     * {@code componentType} represents a non-array class or interface, the new array has
     * {@code dimensions.length} dimensions and {@code componentType} as its component type. If
     * {@code componentType} represents an array class, the number of dimensions of the new array is
     * equal to the sum of {@code dimensions.length} and the number of dimensions of
     * {@code componentType}. In this case, the component type of the new array is the component
     * type of {@code componentType}.
     *
     * <p>
     * The number of dimensions of the new array must not exceed 255.
     *
     * @param componentType the {@code Class} object representing the component type of the new
     *            array
     * @param dimensionsArray an array of {@code int} representing the dimensions of the new array
     * @return the new array
     * @exception NullPointerException if the specified {@code componentType} argument is null
     * @exception IllegalArgumentException if the specified {@code dimensions} argument is a
     *                zero-dimensional array, if componentType is {@link Void#TYPE}, or if the
     *                number of dimensions of the requested array instance exceed 255.
     * @exception NegativeArraySizeException if any of the components in the specified
     *                {@code dimensions} argument is negative.
     */
    @TruffleBoundary
    @Substitution
    public static @JavaType(Object.class) StaticObject multiNewArray(@JavaType(Class.class) StaticObject componentType, @JavaType(int[].class) StaticObject dimensionsArray, @Inject Meta meta) {
        if (StaticObject.isNull(componentType) || StaticObject.isNull(dimensionsArray)) {
            throw meta.throwNullPointerException();
        }
        Klass component = componentType.getMirrorKlass();
        if (component == meta._void || StaticObject.isNull(dimensionsArray)) {
            throw meta.throwException(meta.java_lang_IllegalArgumentException);
        }
        final int[] dimensions = dimensionsArray.unwrap();
        int finalDimensions = dimensions.length;
        if (component.isArray()) {
            finalDimensions += Types.getArrayDimensions(component.getType());
        }
        if (dimensions.length == 0 || finalDimensions > 255) {
            throw meta.throwException(meta.java_lang_IllegalArgumentException);
        }
        for (int d : dimensions) {
            if (d < 0) {
                throw meta.throwException(meta.java_lang_NegativeArraySizeException);
            }
        }
        if (dimensions.length == 1) {
            // getArrayClass(0) is undefined.
            return meta.getInterpreterToVM().newMultiArray(component, dimensions);
        }
        return meta.getInterpreterToVM().newMultiArray(component.getArrayClass(dimensions.length - 1), dimensions);
    }

    @Substitution
    public static boolean getBoolean(@JavaType(Object.class) StaticObject array, int index,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        if (StaticObject.isNull(array)) {
            profiler.profile(0);
            throw meta.throwNullPointerException();
        }
        // `getBoolean` should only access boolean arrays
        Klass arrayKlass = array.getKlass();
        if (arrayKlass != meta._boolean_array) {
            profiler.profile(1);
            throw meta.throwException(meta.java_lang_IllegalArgumentException);
        }
        try {
            return Array.getByte(array.unwrap(), index) != 0;
        } catch (ArrayIndexOutOfBoundsException e) {
            profiler.profile(5);
            throw rethrowAsGuestException(e, meta, profiler);
        }
    }

    @Substitution
    public static byte getByte(@JavaType(Object.class) StaticObject array, int index, @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        checkNonNullArray(array, meta, profiler);
        try {
            return Array.getByte(array.unwrap(), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            profiler.profile(5);
            throw rethrowAsGuestException(e, meta, profiler);
        }
    }

    @Substitution
    public static char getChar(@JavaType(Object.class) StaticObject array, int index, @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        checkNonNullArray(array, meta, profiler);
        try {
            return Array.getChar(array.unwrap(), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            profiler.profile(5);
            throw rethrowAsGuestException(e, meta, profiler);
        }
    }

    @Substitution
    public static short getShort(@JavaType(Object.class) StaticObject array, int index, @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        checkNonNullArray(array, meta, profiler);
        try {
            return Array.getShort(array.unwrap(), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            profiler.profile(5);
            throw rethrowAsGuestException(e, meta, profiler);
        }
    }

    @Substitution
    public static int getInt(@JavaType(Object.class) StaticObject array, int index, @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        checkNonNullArray(array, meta, profiler);
        try {
            return Array.getInt(array.unwrap(), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            profiler.profile(5);
            throw rethrowAsGuestException(e, meta, profiler);
        }
    }

    @Substitution
    public static float getFloat(@JavaType(Object.class) StaticObject array, int index, @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        checkNonNullArray(array, meta, profiler);
        try {
            return Array.getFloat(array.unwrap(), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            profiler.profile(5);
            throw rethrowAsGuestException(e, meta, profiler);
        }
    }

    @Substitution
    public static double getDouble(@JavaType(Object.class) StaticObject array, int index, @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        checkNonNullArray(array, meta, profiler);
        try {
            return Array.getDouble(array.unwrap(), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            profiler.profile(5);
            throw rethrowAsGuestException(e, meta, profiler);
        }
    }

    @Substitution
    public static long getLong(@JavaType(Object.class) StaticObject array, int index, @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        checkNonNullArray(array, meta, profiler);
        try {
            return Array.getLong(array.unwrap(), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            profiler.profile(5);
            throw rethrowAsGuestException(e, meta, profiler);
        }
    }

    private static EspressoException rethrowAsGuestException(RuntimeException e, Meta meta, SubstitutionProfiler profiler) {
        assert e instanceof NullPointerException || e instanceof ArrayIndexOutOfBoundsException || e instanceof IllegalArgumentException;
        if (e instanceof NullPointerException) {
            profiler.profile(2);
            throw meta.throwNullPointerException();
        }
        if (e instanceof ArrayIndexOutOfBoundsException) {
            profiler.profile(3);
            throw meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, e.getMessage());
        }
        if (e instanceof IllegalArgumentException) {
            profiler.profile(4);
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, getMessageBoundary(e));
        }
        throw EspressoError.shouldNotReachHere(e);
    }

    @TruffleBoundary
    // Some Subclasses of IllegalArgumentException use String concatenation.
    private static String getMessageBoundary(RuntimeException e) {
        return e.getMessage();
    }

    private static void checkNonNullArray(StaticObject array, Meta meta, SubstitutionProfiler profiler) {
        if (StaticObject.isNull(array)) {
            profiler.profile(0);
            throw meta.throwNullPointerException();
        }
        if (!(array.isArray())) {
            profiler.profile(1);
            throw meta.throwException(meta.java_lang_IllegalArgumentException);
        }
    }

    @Substitution
    public static void setBoolean(@JavaType(Object.class) StaticObject array, int index, boolean value, @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        if (StaticObject.isNull(array)) {
            profiler.profile(0);
            throw meta.throwNullPointerException();
        }
        // host `setByte` can write in all primitive arrays beside boolean array
        // `setBoolean` should only access boolean arrays
        Klass arrayKlass = array.getKlass();
        if (arrayKlass != meta._boolean_array) {
            profiler.profile(1);
            throw meta.throwException(meta.java_lang_IllegalArgumentException);
        }
        try {
            Array.setByte(array.unwrap(), index, value ? (byte) 1 : (byte) 0);
        } catch (ArrayIndexOutOfBoundsException e) {
            profiler.profile(5);
            throw rethrowAsGuestException(e, meta, profiler);
        }
    }

    @Substitution
    public static void setByte(@JavaType(Object.class) StaticObject array, int index, byte value, @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        checkNonNullArray(array, meta, profiler);
        try {
            Array.setByte(array.unwrap(), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            profiler.profile(5);
            throw rethrowAsGuestException(e, meta, profiler);
        }
    }

    @Substitution
    public static void setChar(@JavaType(Object.class) StaticObject array, int index, char value, @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        checkNonNullArray(array, meta, profiler);
        try {
            Array.setChar(array.unwrap(), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            profiler.profile(5);
            throw rethrowAsGuestException(e, meta, profiler);
        }
    }

    @Substitution
    public static void setShort(@JavaType(Object.class) StaticObject array, int index, short value, @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        checkNonNullArray(array, meta, profiler);
        try {
            Array.setShort(array.unwrap(), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            profiler.profile(5);
            throw rethrowAsGuestException(e, meta, profiler);
        }
    }

    @Substitution
    public static void setInt(@JavaType(Object.class) StaticObject array, int index, int value, @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        checkNonNullArray(array, meta, profiler);
        try {
            Array.setInt(array.unwrap(), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            profiler.profile(5);
            throw rethrowAsGuestException(e, meta, profiler);
        }
    }

    @Substitution
    public static void setFloat(@JavaType(Object.class) StaticObject array, int index, float value, @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        checkNonNullArray(array, meta, profiler);
        try {
            Array.setFloat(array.unwrap(), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            profiler.profile(5);
            throw rethrowAsGuestException(e, meta, profiler);
        }
    }

    @Substitution
    public static void setDouble(@JavaType(Object.class) StaticObject array, int index, double value, @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        checkNonNullArray(array, meta, profiler);
        try {
            Array.setDouble(array.unwrap(), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            profiler.profile(5);
            throw rethrowAsGuestException(e, meta, profiler);
        }
    }

    @Substitution
    public static void setLong(@JavaType(Object.class) StaticObject array, int index, long value, @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        checkNonNullArray(array, meta, profiler);
        try {
            Array.setLong(array.unwrap(), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            profiler.profile(5);
            throw rethrowAsGuestException(e, meta, profiler);
        }
    }

    /**
     * Sets the value of the indexed component of the specified array object to the specified new
     * value. The new value is first automatically unwrapped if the array has a primitive component
     * type.
     * 
     * @param array the array
     * @param index the index into the array
     * @param value the new value of the indexed component
     * @exception NullPointerException If the specified object argument is null
     * @exception IllegalArgumentException If the specified object argument is not an array, or if
     *                the array component type is primitive and an unwrapping conversion fails
     * @exception ArrayIndexOutOfBoundsException If the specified {@code index} argument is
     *                negative, or if it is greater than or equal to the length of the specified
     *                array
     */
    @Substitution
    public static void set(@JavaType(Object.class) StaticObject array, int index, @JavaType(Object.class) StaticObject value, @Inject Meta meta) {
        InterpreterToVM vm = meta.getInterpreterToVM();
        if (StaticObject.isNull(array)) {
            throw meta.throwNullPointerException();
        }
        if (array.isArray()) {
            // @formatter:off
            Object widenValue = Target_sun_reflect_NativeMethodAccessorImpl.checkAndWiden(meta, value, ((ArrayKlass) array.getKlass()).getComponentType());
            switch (((ArrayKlass) array.getKlass()).getComponentType().getJavaKind()) {
                case Boolean : vm.setArrayByte(((boolean) widenValue) ? (byte) 1 : (byte) 0, index, array); break;
                case Byte    : vm.setArrayByte(((byte) widenValue), index, array);       break;
                case Short   : vm.setArrayShort(((short) widenValue), index, array);     break;
                case Char    : vm.setArrayChar(((char) widenValue), index, array);  break;
                case Int     : vm.setArrayInt(((int) widenValue), index, array);     break;
                case Float   : vm.setArrayFloat(((float) widenValue), index, array);     break;
                case Long    : vm.setArrayLong(((long) widenValue), index, array);       break;
                case Double  : vm.setArrayDouble(((double) widenValue), index, array);   break;
                case Object  : vm.setArrayObject(value, index, array); break;
                default      :
                    CompilerDirectives.transferToInterpreter();
                    throw EspressoError.shouldNotReachHere("invalid array type: " + array);
            }
            // @formatter:on
        } else {
            throw meta.throwException(meta.java_lang_IllegalArgumentException);
        }
    }

    /**
     * Returns the value of the indexed component in the specified array object. The value is
     * automatically wrapped in an object if it has a primitive type.
     *
     * @param array the array
     * @param index the index
     * @return the (possibly wrapped) value of the indexed component in the specified array
     * @exception NullPointerException If the specified object is null
     * @exception IllegalArgumentException If the specified object is not an array
     * @exception ArrayIndexOutOfBoundsException If the specified {@code index} argument is
     *                negative, or if it is greater than or equal to the length of the specified
     *                array
     */
    @Substitution
    public static @JavaType(Object.class) StaticObject get(@JavaType(Object.class) StaticObject array, int index, @Inject Meta meta) {
        InterpreterToVM vm = meta.getInterpreterToVM();
        if (StaticObject.isNull(array)) {
            throw meta.throwNullPointerException();
        }
        if (array.isArray()) {
            // @formatter:off
            switch (((ArrayKlass) array.getKlass()).getComponentType().getJavaKind()) {
                case Boolean : return meta.boxBoolean(vm.getArrayByte(index, array) != 0);
                case Byte    : return meta.boxByte(vm.getArrayByte(index, array));
                case Short   : return meta.boxShort(vm.getArrayShort(index, array));
                case Char    : return meta.boxCharacter(vm.getArrayChar(index, array));
                case Int     : return meta.boxInteger(vm.getArrayInt(index, array));
                case Float   : return meta.boxFloat(vm.getArrayFloat(index, array));
                case Long    : return meta.boxLong(vm.getArrayLong(index, array));
                case Double  : return meta.boxDouble(vm.getArrayDouble(index, array));
                case Object  : return vm.getArrayObject(index, array);
                default      :
                    CompilerDirectives.transferToInterpreter();
                    throw EspressoError.shouldNotReachHere("invalid array type: " + array);
            }
            // @formatter:on
        } else {
            throw meta.throwException(meta.java_lang_IllegalArgumentException);
        }
    }

}
