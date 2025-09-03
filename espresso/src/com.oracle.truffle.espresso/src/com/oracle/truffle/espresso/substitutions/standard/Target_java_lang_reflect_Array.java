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
package com.oracle.truffle.espresso.substitutions.standard;

import java.lang.reflect.Array;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.GuestAllocator.AllocationChecks;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionProfiler;
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
        Klass component = componentType.getMirrorKlass(meta);
        if (component == meta._void || TypeSymbols.getArrayDimensions(component.getType()) >= 255) {
            throw meta.throwException(meta.java_lang_IllegalArgumentException);
        }
        AllocationChecks.checkCanAllocateArray(meta, length);
        if (component.isPrimitive()) {
            return meta.getAllocator().createNewPrimitiveArray(component, length);
        }
        return meta.getAllocator().createNewReferenceArray(component, length);
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
    public static @JavaType(Object.class) StaticObject multiNewArray(@JavaType(Class.class) StaticObject componentType, @JavaType(int[].class) StaticObject dimensionsArray,
                    @Inject EspressoLanguage language,
                    @Inject Meta meta) {
        if (StaticObject.isNull(componentType) || StaticObject.isNull(dimensionsArray)) {
            throw meta.throwNullPointerException();
        }
        Klass component = componentType.getMirrorKlass(meta);
        if (component == meta._void || StaticObject.isNull(dimensionsArray)) {
            throw meta.throwException(meta.java_lang_IllegalArgumentException);
        }
        final int[] dimensions = dimensionsArray.unwrap(language);
        int finalDimensions = dimensions.length;
        if (component.isArray()) {
            finalDimensions += TypeSymbols.getArrayDimensions(component.getType());
        }
        if (dimensions.length == 0 || finalDimensions > 255) {
            throw meta.throwException(meta.java_lang_IllegalArgumentException);
        }
        AllocationChecks.checkCanAllocateMultiArray(meta, component, dimensions);
        if (dimensions.length == 1) {
            return meta.getAllocator().createNewMultiArray(component, dimensions);
        }
        return meta.getAllocator().createNewMultiArray(component.getArrayKlass(dimensions.length - 1), dimensions);
    }

    @Substitution
    public static boolean getBoolean(@JavaType(Object.class) StaticObject array, int index,
                    @Inject EspressoLanguage language,
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
        if (array.isForeignObject()) {
            try {
                InteropLibrary library = InteropLibrary.getUncached();
                return library.asBoolean(library.readArrayElement(array.rawForeignObject(language), index));
            } catch (UnsupportedMessageException e) {
                throw meta.throwException(meta.java_lang_IllegalArgumentException);
            } catch (InvalidArrayIndexException e) {
                throw meta.throwException(meta.java_lang_ArrayIndexOutOfBoundsException);
            }
        } else {
            try {
                return Array.getByte(array.unwrap(language), index) != 0;
            } catch (ArrayIndexOutOfBoundsException e) {
                profiler.profile(5);
                throw rethrowAsGuestException(e, meta, profiler);
            }
        }
    }

    @Substitution
    public static byte getByte(@JavaType(Object.class) StaticObject array, int index,
                    @Inject EspressoLanguage language,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        checkNonNullArray(array, meta, profiler);
        if (array.isForeignObject()) {
            try {
                InteropLibrary library = InteropLibrary.getUncached();
                return library.asByte(library.readArrayElement(array.rawForeignObject(language), index));
            } catch (UnsupportedMessageException e) {
                throw meta.throwException(meta.java_lang_IllegalArgumentException);
            } catch (InvalidArrayIndexException e) {
                throw meta.throwException(meta.java_lang_ArrayIndexOutOfBoundsException);
            }
        } else {
            try {
                return Array.getByte(array.unwrap(language), index);
            } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
                profiler.profile(5);
                throw rethrowAsGuestException(e, meta, profiler);
            }
        }
    }

    @Substitution
    public static char getChar(@JavaType(Object.class) StaticObject array, int index,
                    @Inject EspressoLanguage language,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        checkNonNullArray(array, meta, profiler);
        if (array.isForeignObject()) {
            try {
                InteropLibrary library = InteropLibrary.getUncached();
                String str = library.asString(library.readArrayElement(array.rawForeignObject(language), index));
                if (str.isEmpty()) {
                    return '\u0000';
                } else if (str.length() > 1) {
                    throw meta.throwException(meta.java_lang_IllegalArgumentException);
                } else {
                    return str.charAt(0);
                }
            } catch (UnsupportedMessageException e) {
                throw meta.throwException(meta.java_lang_IllegalArgumentException);
            } catch (InvalidArrayIndexException e) {
                throw meta.throwException(meta.java_lang_ArrayIndexOutOfBoundsException);
            }
        } else {
            try {
                return Array.getChar(array.unwrap(language), index);
            } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
                profiler.profile(5);
                throw rethrowAsGuestException(e, meta, profiler);
            }
        }
    }

    @Substitution
    public static short getShort(@JavaType(Object.class) StaticObject array, int index,
                    @Inject EspressoLanguage language,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        checkNonNullArray(array, meta, profiler);
        if (array.isForeignObject()) {
            try {
                InteropLibrary library = InteropLibrary.getUncached();
                return library.asShort(library.readArrayElement(array.rawForeignObject(language), index));
            } catch (UnsupportedMessageException e) {
                throw meta.throwException(meta.java_lang_IllegalArgumentException);
            } catch (InvalidArrayIndexException e) {
                throw meta.throwException(meta.java_lang_ArrayIndexOutOfBoundsException);
            }
        } else {
            try {
                return Array.getShort(array.unwrap(language), index);
            } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
                profiler.profile(5);
                throw rethrowAsGuestException(e, meta, profiler);
            }
        }
    }

    @Substitution
    public static int getInt(@JavaType(Object.class) StaticObject array, int index,
                    @Inject EspressoLanguage language,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        checkNonNullArray(array, meta, profiler);
        if (array.isForeignObject()) {
            try {
                InteropLibrary library = InteropLibrary.getUncached();
                return library.asInt(library.readArrayElement(array.rawForeignObject(language), index));
            } catch (UnsupportedMessageException e) {
                throw meta.throwException(meta.java_lang_IllegalArgumentException);
            } catch (InvalidArrayIndexException e) {
                throw meta.throwException(meta.java_lang_ArrayIndexOutOfBoundsException);
            }
        } else {
            try {
                return Array.getInt(array.unwrap(language), index);
            } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
                profiler.profile(5);
                throw rethrowAsGuestException(e, meta, profiler);
            }
        }
    }

    @Substitution
    public static float getFloat(@JavaType(Object.class) StaticObject array, int index,
                    @Inject EspressoLanguage language,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        checkNonNullArray(array, meta, profiler);
        if (array.isForeignObject()) {
            try {
                InteropLibrary library = InteropLibrary.getUncached();
                return library.asFloat(library.readArrayElement(array.rawForeignObject(language), index));
            } catch (UnsupportedMessageException e) {
                throw meta.throwException(meta.java_lang_IllegalArgumentException);
            } catch (InvalidArrayIndexException e) {
                throw meta.throwException(meta.java_lang_ArrayIndexOutOfBoundsException);
            }
        } else {
            try {
                return Array.getFloat(array.unwrap(language), index);
            } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
                profiler.profile(5);
                throw rethrowAsGuestException(e, meta, profiler);
            }
        }
    }

    @Substitution
    public static double getDouble(@JavaType(Object.class) StaticObject array, int index,
                    @Inject EspressoLanguage language,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        checkNonNullArray(array, meta, profiler);
        if (array.isForeignObject()) {
            try {
                InteropLibrary library = InteropLibrary.getUncached();
                return library.asDouble(library.readArrayElement(array.rawForeignObject(language), index));
            } catch (UnsupportedMessageException e) {
                throw meta.throwException(meta.java_lang_IllegalArgumentException);
            } catch (InvalidArrayIndexException e) {
                throw meta.throwException(meta.java_lang_ArrayIndexOutOfBoundsException);
            }
        } else {
            try {
                return Array.getDouble(array.unwrap(language), index);
            } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
                profiler.profile(5);
                throw rethrowAsGuestException(e, meta, profiler);
            }
        }
    }

    @Substitution
    public static long getLong(@JavaType(Object.class) StaticObject array, int index,
                    @Inject EspressoLanguage language,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        checkNonNullArray(array, meta, profiler);
        if (array.isForeignObject()) {
            try {
                InteropLibrary library = InteropLibrary.getUncached();
                return library.asLong(library.readArrayElement(array.rawForeignObject(language), index));
            } catch (UnsupportedMessageException e) {
                throw meta.throwException(meta.java_lang_IllegalArgumentException);
            } catch (InvalidArrayIndexException e) {
                throw meta.throwException(meta.java_lang_ArrayIndexOutOfBoundsException);
            }
        } else {
            try {
                return Array.getLong(array.unwrap(language), index);
            } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
                profiler.profile(5);
                throw rethrowAsGuestException(e, meta, profiler);
            }
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
        CompilerDirectives.transferToInterpreterAndInvalidate();
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
    public static void setBoolean(@JavaType(Object.class) StaticObject array, int index, boolean value,
                    @Inject EspressoLanguage language,
                    @Inject Meta meta,
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
        if (array.isForeignObject()) {
            writeForeignArrayElement(array, language, index, value, meta);
        } else {
            try {
                Array.setByte(array.unwrap(language), index, value ? (byte) 1 : (byte) 0);
            } catch (ArrayIndexOutOfBoundsException e) {
                profiler.profile(5);
                throw rethrowAsGuestException(e, meta, profiler);
            }
        }
    }

    @Substitution
    public static void setByte(@JavaType(Object.class) StaticObject array, int index, byte value,
                    @Inject EspressoLanguage language,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        checkNonNullArray(array, meta, profiler);
        if (array.isForeignObject()) {
            writeForeignArrayElement(array, language, index, value, meta);
        } else {
            try {
                Array.setByte(array.unwrap(language), index, value);
            } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
                profiler.profile(5);
                throw rethrowAsGuestException(e, meta, profiler);
            }
        }
    }

    @Substitution
    public static void setChar(@JavaType(Object.class) StaticObject array, int index, char value,
                    @Inject EspressoLanguage language,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        checkNonNullArray(array, meta, profiler);
        if (array.isForeignObject()) {
            writeForeignArrayElement(array, language, index, value, meta);
        } else {
            try {
                Array.setChar(array.unwrap(language), index, value);
            } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
                profiler.profile(5);
                throw rethrowAsGuestException(e, meta, profiler);
            }
        }
    }

    @Substitution
    public static void setShort(@JavaType(Object.class) StaticObject array, int index, short value,
                    @Inject EspressoLanguage language,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        checkNonNullArray(array, meta, profiler);
        if (array.isForeignObject()) {
            writeForeignArrayElement(array, language, index, value, meta);
        } else {
            try {
                Array.setShort(array.unwrap(language), index, value);
            } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
                profiler.profile(5);
                throw rethrowAsGuestException(e, meta, profiler);
            }
        }
    }

    @Substitution
    public static void setInt(@JavaType(Object.class) StaticObject array, int index, int value,
                    @Inject EspressoLanguage language,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        checkNonNullArray(array, meta, profiler);
        if (array.isForeignObject()) {
            writeForeignArrayElement(array, language, index, value, meta);
        } else {
            try {
                Array.setInt(array.unwrap(language), index, value);
            } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
                profiler.profile(5);
                throw rethrowAsGuestException(e, meta, profiler);
            }
        }
    }

    @Substitution
    public static void setFloat(@JavaType(Object.class) StaticObject array, int index, float value,
                    @Inject EspressoLanguage language,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        checkNonNullArray(array, meta, profiler);
        if (array.isForeignObject()) {
            writeForeignArrayElement(array, language, index, value, meta);
        } else {
            try {
                Array.setFloat(array.unwrap(language), index, value);
            } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
                profiler.profile(5);
                throw rethrowAsGuestException(e, meta, profiler);
            }
        }
    }

    @Substitution
    public static void setDouble(@JavaType(Object.class) StaticObject array, int index, double value,
                    @Inject EspressoLanguage language,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        checkNonNullArray(array, meta, profiler);
        if (array.isForeignObject()) {
            writeForeignArrayElement(array, language, index, value, meta);
        } else {
            try {
                Array.setDouble(array.unwrap(language), index, value);
            } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
                profiler.profile(5);
                throw rethrowAsGuestException(e, meta, profiler);
            }
        }
    }

    @Substitution
    public static void setLong(@JavaType(Object.class) StaticObject array, int index, long value,
                    @Inject EspressoLanguage language,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        checkNonNullArray(array, meta, profiler);
        if (array.isForeignObject()) {
            writeForeignArrayElement(array, language, index, value, meta);
        } else {
            try {
                Array.setLong(array.unwrap(language), index, value);
            } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
                profiler.profile(5);
                throw rethrowAsGuestException(e, meta, profiler);
            }
        }
    }

    private static void writeForeignArrayElement(StaticObject array, EspressoLanguage language, int index, Object value, Meta meta) {
        try {
            InteropLibrary library = InteropLibrary.getUncached();
            library.writeArrayElement(array.rawForeignObject(language), index, value);
        } catch (UnsupportedMessageException | UnsupportedTypeException e) {
            throw meta.throwException(meta.java_lang_IllegalArgumentException);
        } catch (InvalidArrayIndexException e) {
            throw meta.throwException(meta.java_lang_ArrayIndexOutOfBoundsException);
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
    public static void set(@JavaType(Object.class) StaticObject array, int index, @JavaType(Object.class) StaticObject value,
                    @Inject EspressoLanguage language,
                    @Inject Meta meta) {
        InterpreterToVM vm = meta.getInterpreterToVM();
        if (StaticObject.isNull(array)) {
            throw meta.throwNullPointerException();
        }
        if (array.isArray()) {
            Object widenValue = Target_sun_reflect_NativeMethodAccessorImpl.checkAndWiden(meta, value, ((ArrayKlass) array.getKlass()).getComponentType());
            if (array.isForeignObject()) {
                try {
                    InteropLibrary library = InteropLibrary.getUncached();
                    library.writeArrayElement(array.rawForeignObject(language), index, widenValue);
                    return;
                } catch (UnsupportedMessageException | UnsupportedTypeException e) {
                    throw meta.throwException(meta.java_lang_IllegalArgumentException);
                } catch (InvalidArrayIndexException e) {
                    throw meta.throwException(meta.java_lang_ArrayIndexOutOfBoundsException);
                }
            }
            // @formatter:off
            switch (((ArrayKlass) array.getKlass()).getComponentType().getJavaKind()) {
                case Boolean : vm.setArrayByte(language, ((boolean) widenValue) ? (byte) 1 : (byte) 0, index, array); break;
                case Byte    : vm.setArrayByte(language, ((byte) widenValue), index, array); break;
                case Short   : vm.setArrayShort(language, ((short) widenValue), index, array); break;
                case Char    : vm.setArrayChar(language, ((char) widenValue), index, array); break;
                case Int     : vm.setArrayInt(language, ((int) widenValue), index, array); break;
                case Float   : vm.setArrayFloat(language, ((float) widenValue), index, array); break;
                case Long    : vm.setArrayLong(language, ((long) widenValue), index, array); break;
                case Double  : vm.setArrayDouble(language, ((double) widenValue), index, array); break;
                case Object  : vm.setArrayObject(language, value, index, array); break;
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
    public static @JavaType(Object.class) StaticObject get(@JavaType(Object.class) StaticObject array, int index,
                    @Inject EspressoLanguage language,
                    @Inject Meta meta) {
        InterpreterToVM vm = meta.getInterpreterToVM();
        if (StaticObject.isNull(array)) {
            throw meta.throwNullPointerException();
        }
        if (array.isArray()) {
            try {
                switch (((ArrayKlass) array.getKlass()).getComponentType().getJavaKind()) {
                    case Boolean: {
                        boolean result;
                        if (array.isForeignObject()) {
                            InteropLibrary library = InteropLibrary.getUncached();
                            result = library.asBoolean(library.readArrayElement(array.rawForeignObject(language), index));
                        } else {
                            result = vm.getArrayByte(language, index, array) != 0;
                        }
                        return meta.boxBoolean(result);
                    }
                    case Byte: {
                        byte result;
                        if (array.isForeignObject()) {
                            InteropLibrary library = InteropLibrary.getUncached();
                            result = library.asByte(library.readArrayElement(array.rawForeignObject(language), index));
                        } else {
                            result = vm.getArrayByte(language, index, array);
                        }
                        return meta.boxByte(result);
                    }
                    case Short: {
                        short result;
                        if (array.isForeignObject()) {
                            InteropLibrary library = InteropLibrary.getUncached();
                            result = library.asShort(library.readArrayElement(array.rawForeignObject(language), index));
                        } else {
                            result = vm.getArrayShort(language, index, array);
                        }
                        return meta.boxShort(result);
                    }
                    case Char: {
                        char result;
                        if (array.isForeignObject()) {
                            InteropLibrary library = InteropLibrary.getUncached();
                            String str = library.asString(library.readArrayElement(array.rawForeignObject(language), index));
                            if (str.isEmpty()) {
                                result = '\u0000';
                            } else if (str.length() > 1) {
                                throw meta.throwException(meta.java_lang_IllegalArgumentException);
                            } else {
                                result = str.charAt(0);
                            }
                        } else {
                            result = vm.getArrayChar(language, index, array);
                        }
                        return meta.boxCharacter(result);
                    }
                    case Int: {
                        int result;
                        if (array.isForeignObject()) {
                            InteropLibrary library = InteropLibrary.getUncached();
                            result = library.asInt(library.readArrayElement(array.rawForeignObject(language), index));
                        } else {
                            result = vm.getArrayInt(language, index, array);
                        }
                        return meta.boxInteger(result);
                    }
                    case Float: {
                        float result;
                        if (array.isForeignObject()) {
                            InteropLibrary library = InteropLibrary.getUncached();
                            result = library.asFloat(library.readArrayElement(array.rawForeignObject(language), index));
                        } else {
                            result = vm.getArrayFloat(language, index, array);
                        }
                        return meta.boxFloat(result);
                    }
                    case Long: {
                        long result;
                        if (array.isForeignObject()) {
                            InteropLibrary library = InteropLibrary.getUncached();
                            result = library.asLong(library.readArrayElement(array.rawForeignObject(language), index));
                        } else {
                            result = vm.getArrayLong(language, index, array);
                        }
                        return meta.boxLong(result);
                    }
                    case Double: {
                        double result;
                        if (array.isForeignObject()) {
                            InteropLibrary library = InteropLibrary.getUncached();
                            result = library.asDouble(library.readArrayElement(array.rawForeignObject(language), index));
                        } else {
                            result = vm.getArrayDouble(language, index, array);
                        }
                        return meta.boxDouble(result);
                    }
                    case Object: {
                        if (array.isForeignObject()) {
                            InteropLibrary library = InteropLibrary.getUncached();
                            Object result = library.readArrayElement(array.rawForeignObject(language), index);
                            return StaticObject.createForeign(language, meta.java_lang_Object, result, InteropLibrary.getUncached(result));
                        } else {
                            return vm.getArrayObject(language, index, array);
                        }
                    }
                    default:
                        CompilerDirectives.transferToInterpreter();
                        throw EspressoError.shouldNotReachHere("invalid array type: " + array);
                }
            } catch (UnsupportedMessageException e) {
                throw meta.throwException(meta.java_lang_IllegalArgumentException);
            } catch (InvalidArrayIndexException e) {
                int length = getForeignArrayLength(array, language, meta);
                throw meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, InterpreterToVM.outOfBoundsMessage(index, length));
            }
        } else {
            throw meta.throwException(meta.java_lang_IllegalArgumentException);
        }
    }

    private static int getForeignArrayLength(StaticObject array, EspressoLanguage language, Meta meta) {
        assert array.isForeignObject();
        try {
            Object foreignObject = array.rawForeignObject(language);
            InteropLibrary library = InteropLibrary.getUncached(foreignObject);
            long arrayLength = library.getArraySize(foreignObject);
            if (arrayLength > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            return (int) arrayLength;
        } catch (UnsupportedMessageException e) {
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "can't get array length because foreign object is not an array");
        }
    }
}
