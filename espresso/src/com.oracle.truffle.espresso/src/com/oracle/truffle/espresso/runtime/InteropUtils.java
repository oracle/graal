/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.Meta;

public class InteropUtils {
    private static final int MANTISSA_PRECISION_FLOAT = 24;
    private static final float FLOAT_MAX_SAFE_INTEGER = (1 << MANTISSA_PRECISION_FLOAT) - 1;
    private static final int MANTISSA_PRECISION_DOUBLE = 53;
    private static final double DOUBLE_MAX_SAFE_INTEGER = (1L << MANTISSA_PRECISION_DOUBLE) - 1;

    public static boolean inSafeIntegerRange(float f) {
        return f >= -FLOAT_MAX_SAFE_INTEGER && f <= FLOAT_MAX_SAFE_INTEGER;
    }

    public static boolean inSafeIntegerRange(double d) {
        return d >= -DOUBLE_MAX_SAFE_INTEGER && d <= DOUBLE_MAX_SAFE_INTEGER;
    }

    public static boolean isNegativeZero(float f) {
        return Float.floatToRawIntBits(f) == Float.floatToRawIntBits(-0f);
    }

    public static boolean isNegativeZero(double d) {
        return Double.doubleToRawLongBits(d) == Double.doubleToRawLongBits(-0d);
    }

    public static boolean isAtMostByte(Klass klass) {
        return klass == klass.getMeta().java_lang_Byte;
    }

    public static boolean isAtMostShort(Klass klass) {
        Meta meta = klass.getMeta();
        return klass == meta.java_lang_Byte || klass == meta.java_lang_Short;
    }

    public static boolean isAtMostInt(Klass klass) {
        Meta meta = klass.getMeta();
        return klass == meta.java_lang_Byte || klass == meta.java_lang_Short || klass == meta.java_lang_Integer;
    }

    public static boolean isAtMostLong(Klass klass) {
        Meta meta = klass.getMeta();
        return klass == meta.java_lang_Byte || klass == meta.java_lang_Short || klass == meta.java_lang_Integer || klass == meta.java_lang_Long;
    }

    public static boolean isAtMostFloat(Klass klass) {
        Meta meta = klass.getMeta();
        return klass == meta.java_lang_Byte || klass == meta.java_lang_Short || klass == meta.java_lang_Float;
    }

    public static Object unwrap(EspressoLanguage language, StaticObject object, Meta meta) {
        if (meta.isBoxed(object.getKlass())) {
            return meta.unboxGuest(object);
        }
        return object.isForeignObject() ? object.rawForeignObject(language) : object;
    }

    public static Object unwrap(StaticObject object, Meta meta, Node languageLookupNode) {
        return unwrap(EspressoLanguage.get(languageLookupNode), object, meta);
    }

    public static Object unwrap(EspressoLanguage language, Object object, Meta meta) {
        if (object instanceof StaticObject) {
            return unwrap(language, (StaticObject) object, meta);
        }
        return object;
    }
}
