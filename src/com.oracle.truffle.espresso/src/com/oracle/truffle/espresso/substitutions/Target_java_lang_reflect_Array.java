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

package com.oracle.truffle.espresso.substitutions;

import java.lang.reflect.Array;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

@EspressoSubstitutions
public final class Target_java_lang_reflect_Array {

    @Substitution
    public static Object newArray(@Host(Class.class) StaticObjectClass componentType, int length) {
        if (componentType.getMirrorKlass().isPrimitive()) {
            byte jvmPrimitiveType = (byte) componentType.getMirrorKlass().getJavaKind().getBasicType();
            return InterpreterToVM.allocatePrimitiveArray(jvmPrimitiveType, length);
        }
        InterpreterToVM vm = EspressoLanguage.getCurrentContext().getInterpreterToVM();
        return vm.newArray(componentType.getMirrorKlass(), length);
    }

    @Substitution
    public static Object multiNewArray(@Host(Class.class) StaticObject componentType,
                    @Host(int[].class) StaticObject guestDimensions) {
        int[] dimensions = ((StaticObjectArray) guestDimensions).unwrap();
        return EspressoLanguage.getCurrentContext().getInterpreterToVM().newMultiArray(((StaticObjectClass) componentType).getMirrorKlass(), dimensions);
    }

    @Substitution
    public static boolean getBoolean(@Host(Object.class) StaticObject array, int index) {
        if (!(array instanceof StaticObjectArray)) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(IllegalArgumentException.class);
        }
        try {
            return Array.getBoolean(((StaticObjectArray) array).unwrap(), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    public static byte getByte(@Host(Object.class) StaticObject array, int index) {
        if (!(array instanceof StaticObjectArray)) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(IllegalArgumentException.class);
        }
        try {
            return Array.getByte(((StaticObjectArray) array).unwrap(), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    public static char getChar(@Host(Object.class) StaticObject array, int index) {
        if (!(array instanceof StaticObjectArray)) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(IllegalArgumentException.class);
        }
        try {
            return Array.getChar(((StaticObjectArray) array).unwrap(), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    public static short getShort(@Host(Object.class) StaticObject array, int index) {
        if (!(array instanceof StaticObjectArray)) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(IllegalArgumentException.class);
        }
        try {
            return Array.getShort(((StaticObjectArray) array).unwrap(), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    public static int getInt(@Host(Object.class) StaticObject array, int index) {
        if (!(array instanceof StaticObjectArray)) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(IllegalArgumentException.class);
        }
        try {
            return Array.getInt(((StaticObjectArray) array).unwrap(), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    public static float getFloat(@Host(Object.class) StaticObject array, int index) {
        if (!(array instanceof StaticObjectArray)) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(IllegalArgumentException.class);
        }
        try {
            return Array.getFloat(((StaticObjectArray) array).unwrap(), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    public static double getDouble(@Host(Object.class) StaticObject array, int index) {
        if (!(array instanceof StaticObjectArray)) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(IllegalArgumentException.class);
        }
        try {
            return Array.getDouble(((StaticObjectArray) array).unwrap(), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    public static long getLong(@Host(Object.class) StaticObject array, int index) {
        if (!(array instanceof StaticObjectArray)) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(IllegalArgumentException.class);
        }
        try {
            return Array.getLong(((StaticObjectArray) array).unwrap(), index);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    public static void setBoolean(@Host(Object.class) StaticObject array, int index, boolean value) {
        try {
            Array.setBoolean(((StaticObjectArray) array).unwrap(), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    public static void setByte(@Host(Object.class) StaticObject array, int index, byte value) {
        try {
            Array.setByte(((StaticObjectArray) array).unwrap(), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    public static void setChar(@Host(Object.class) StaticObject array, int index, char value) {
        try {
            Array.setChar(((StaticObjectArray) array).unwrap(), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    public static void setShort(@Host(Object.class) StaticObject array, int index, short value) {
        try {
            Array.setShort(((StaticObjectArray) array).unwrap(), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    public static void setInt(@Host(Object.class) StaticObject array, int index, int value) {
        try {
            Array.setInt(((StaticObjectArray) array).unwrap(), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    public static void setFloat(@Host(Object.class) StaticObject array, int index, float value) {
        try {
            Array.setFloat(((StaticObjectArray) array).unwrap(), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    public static void setDouble(@Host(Object.class) StaticObject array, int index, double value) {
        try {
            Array.setDouble(((StaticObjectArray) array).unwrap(), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass(), e.getMessage());
        }
    }

    @Substitution
    public static void setLong(@Host(Object.class) StaticObject array, int index, long value) {
        try {
            Array.setLong(((StaticObjectArray) array).unwrap(), index, value);
        } catch (NullPointerException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(e.getClass(), e.getMessage());
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
    public static void set(@Host(Object.class) StaticObject array, int index, @Host(Object.class) StaticObject value) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        InterpreterToVM vm = meta.getInterpreterToVM();
        if (StaticObject.isNull(array)) {
            throw meta.throwEx(meta.NullPointerException);
        }
        if (array instanceof StaticObjectArray) {
            // @formatter:off
            // Checkstyle: stop
            switch (array.getKlass().getComponentType().getJavaKind()) {
                case Boolean : vm.setArrayByte(meta.unboxBoolean(value) ? (byte) 1 : (byte) 0, index, array); break;
                case Byte    : vm.setArrayByte(meta.unboxByte(value), index, array);       break;
                case Short   : vm.setArrayShort(meta.unboxShort(value), index, array);     break;
                case Char    : vm.setArrayChar(meta.unboxCharacter(value), index, array);  break;
                case Int     : vm.setArrayInt(meta.unboxInteger(value), index, array);     break;
                case Float   : vm.setArrayFloat(meta.unboxFloat(value), index, array);     break;
                case Long    : vm.setArrayLong(meta.unboxLong(value), index, array);       break;
                case Double  : vm.setArrayDouble(meta.unboxDouble(value), index, array);   break;
                case Object  : vm.setArrayObject(value, index, (StaticObjectArray) array); break ;
                default      : throw EspressoError.shouldNotReachHere("invalid array type: " + array);
            }
            // @formatter:on
            // Checkstyle: resume
        } else {
            throw meta.throwEx(meta.IllegalArgumentException);
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
    public static @Host(Object.class) StaticObject get(@Host(Object.class) StaticObject array, int index) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        InterpreterToVM vm = meta.getInterpreterToVM();
        if (StaticObject.isNull(array)) {
            throw meta.throwEx(meta.NullPointerException);
        }
        if (array instanceof StaticObjectArray) {
            // @formatter:off
            // Checkstyle: stop
            switch (array.getKlass().getComponentType().getJavaKind()) {
                case Boolean : return meta.boxBoolean(vm.getArrayByte(index, array) != 0);
                case Byte    : return meta.boxByte(vm.getArrayByte(index, array));
                case Short   : return meta.boxShort(vm.getArrayShort(index, array));
                case Char    : return meta.boxCharacter(vm.getArrayChar(index, array));
                case Int     : return meta.boxInteger(vm.getArrayInt(index, array));
                case Float   : return meta.boxFloat(vm.getArrayFloat(index, array));
                case Long    : return meta.boxLong(vm.getArrayLong(index, array));
                case Double  : return meta.boxDouble(vm.getArrayDouble(index, array));
                case Object  : return vm.getArrayObject(index, array);
                default      : throw EspressoError.shouldNotReachHere("invalid array type: " + array);
            }
            // @formatter:on
            // Checkstyle: resume
        } else {
            throw meta.throwEx(meta.IllegalArgumentException);
        }
    }

}
