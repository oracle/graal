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
package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;

import java.lang.reflect.Array;

public final class StaticObjectArray extends StaticObject {

    private final Object array;

    public StaticObjectArray(Klass klass, Object array) {
        super(klass);
        assert klass.isArray();
        assert array != null;
        assert !(array instanceof StaticObject);
        assert array.getClass().isArray();
        this.array = array;
    }

    public static StaticObjectArray wrapPrimitiveArray(Object array) {
        assert array != null;
        assert array.getClass().isArray() && array.getClass().getComponentType().isPrimitive();
        if (array instanceof boolean[]) {
            return wrap((boolean[]) array);
        }
        if (array instanceof byte[]) {
            return wrap((byte[]) array);
        }
        if (array instanceof char[]) {
            return wrap((char[]) array);
        }
        if (array instanceof short[]) {
            return wrap((short[]) array);
        }
        if (array instanceof int[]) {
            return wrap((int[]) array);
        }
        if (array instanceof float[]) {
            return wrap((float[]) array);
        }
        if (array instanceof double[]) {
            return wrap((double[]) array);
        }
        if (array instanceof long[]) {
            return wrap((long[]) array);
        }
        throw EspressoError.shouldNotReachHere("Not a primitive array " + array);
    }

    @SuppressWarnings("unchecked")
    public <T> T unwrap() {
        return (T) array;
    }

    public <T> T get(int index) {
        return this.<T[]> unwrap()[index];
    }

    public int length() {
        return Array.getLength(array);
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return StaticObjectArrayMessageResolutionForeign.ACCESS;
    }

    // region wrappers

    public static StaticObjectArray wrap(StaticObject[] array) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        return new StaticObjectArray(meta.OBJECT.array().rawKlass(), array);
    }

    public static StaticObjectArray wrap(byte[] array) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        return new StaticObjectArray(meta.BYTE.array().rawKlass(), array);
    }

    public static StaticObjectArray wrap(boolean[] array) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        return new StaticObjectArray(meta.BOOLEAN.array().rawKlass(), array);
    }

    public static StaticObjectArray wrap(char[] array) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        return new StaticObjectArray(meta.CHAR.array().rawKlass(), array);
    }

    public static StaticObjectArray wrap(short[] array) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        return new StaticObjectArray(meta.SHORT.array().rawKlass(), array);
    }

    public static StaticObjectArray wrap(int[] array) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        return new StaticObjectArray(meta.INT.array().rawKlass(), array);
    }

    public static StaticObjectArray wrap(float[] array) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        return new StaticObjectArray(meta.FLOAT.array().rawKlass(), array);
    }

    public static StaticObjectArray wrap(double[] array) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        return new StaticObjectArray(meta.DOUBLE.array().rawKlass(), array);
    }

    public static StaticObjectArray wrap(long[] array) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        return new StaticObjectArray(meta.LONG.array().rawKlass(), array);
    }

    private Object cloneWrapped() {
        if (array instanceof boolean[]) {
            return this.<boolean[]> unwrap().clone();
        }
        if (array instanceof byte[]) {
            return this.<boolean[]> unwrap().clone();
        }
        if (array instanceof char[]) {
            return this.<char[]> unwrap().clone();
        }
        if (array instanceof short[]) {
            return this.<short[]> unwrap().clone();
        }
        if (array instanceof int[]) {
            return this.<int[]> unwrap().clone();
        }
        if (array instanceof float[]) {
            return this.<float[]> unwrap().clone();
        }
        if (array instanceof double[]) {
            return this.<double[]> unwrap().clone();
        }
        if (array instanceof long[]) {
            return this.<long[]> unwrap().clone();
        }
        return this.<StaticObject[]> unwrap().clone();
    }

    public StaticObjectArray copy() {
        return new StaticObjectArray(getKlass(), cloneWrapped());
    }

    // endregion
}
