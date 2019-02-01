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

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

import java.lang.reflect.Array;

@EspressoSubstitutions
public final class Target_java_lang_reflect_Array {

    @Substitution
    public static Object newArray(@Host(Class.class) StaticObjectClass componentType, int length) {
        if (componentType.getMirror().isPrimitive()) {
            byte jvmPrimitiveType = (byte) componentType.getMirror().getJavaKind().getBasicType();
            return InterpreterToVM.allocatePrimitiveArray(jvmPrimitiveType, length);
        }
        InterpreterToVM vm = EspressoLanguage.getCurrentContext().getInterpreterToVM();
        return vm.newArray(componentType.getMirror(), length);
    }

    @Substitution
    public static Object multiNewArray(@Host(Class.class) StaticObject componentType,
                    @Host(int[].class) StaticObject guestDimensions) {
        int[] dimensions = ((StaticObjectArray) guestDimensions).unwrap();
        return EspressoLanguage.getCurrentContext().getInterpreterToVM().newMultiArray(((StaticObjectClass) componentType).getMirror(), dimensions);
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

    @Substitution
    public static void set(@Host(Object.class) StaticObject array, int index, @Host(Object.class) StaticObject value) {
        if (array instanceof StaticObjectArray) {
            EspressoLanguage.getCurrentContext().getInterpreterToVM().setArrayObject(value, index, (StaticObjectArray) array);
        } else {
            if (StaticObject.isNull(array)) {
                throw EspressoLanguage.getCurrentContext().getMeta().throwEx(NullPointerException.class);
            } else {
                throw EspressoLanguage.getCurrentContext().getMeta().throwEx(IllegalArgumentException.class);
            }
        }
    }
}
