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
package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.Meta;

public class InteropUtils {
    public static boolean inIntRange(float f) {
        return f >= Integer.MIN_VALUE && f <= Integer.MAX_VALUE;
    }

    public static boolean inIntRange(double d) {
        return d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE;
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

    public static boolean isAtMostDouble(Klass klass) {
        Meta meta = klass.getMeta();
        return klass == meta.java_lang_Byte || klass == meta.java_lang_Short || klass == meta.java_lang_Integer || klass == meta.java_lang_Float || klass == meta.java_lang_Double;
    }
}
