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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.vm.VM;

public class InteropUtils {

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
        if (object.isForeignObject()) {
            return object.rawForeignObject(language);
        }
        // We need to unwrap foreign exceptions which are stored in guest throwable backtrace.
        // They only exist if polyglot is in use though.
        if (meta.polyglot == null || StaticObject.isNull(object)) {
            return object;
        }
        if (meta.java_lang_Throwable.isAssignableFrom(object.getKlass())) {
            return unwrapForeignException(object, meta);
        }
        return object;
    }

    private static Object unwrapForeignException(StaticObject object, Meta meta) {
        assert meta.java_lang_Throwable.isAssignableFrom(object.getKlass());
        if (meta.HIDDEN_FRAMES.getHiddenObject(object) == VM.StackTrace.FOREIGN_MARKER_STACK_TRACE) {
            return meta.java_lang_Throwable_backtrace.getObject(object).rawForeignObject(meta.getLanguage());
        }
        return object;
    }

    public static Object unwrap(EspressoLanguage language, Object object, Meta meta) {
        if (object instanceof StaticObject) {
            return unwrap(language, (StaticObject) object, meta);
        }
        return object;
    }

    public static boolean isForeignException(EspressoException e) {
        assert e != null;
        StaticObject guestException = e.getGuestException();
        Meta meta = guestException.getKlass().getMeta();
        if (meta.polyglot == null) {
            return false;
        }
        if (guestException.getKlass() == meta.polyglot.ForeignException) {
            return true;
        }
        Object stack = meta.HIDDEN_FRAMES.getHiddenObject(guestException);
        return stack == VM.StackTrace.FOREIGN_MARKER_STACK_TRACE;
    }

    @TruffleBoundary
    public static RuntimeException unwrapExceptionBoundary(EspressoLanguage language, EspressoException exception, Meta meta) {
        if (isForeignException(exception)) {
            StaticObject guestException = exception.getGuestException();
            // return the original foreign exception when leaving espresso interop
            return (AbstractTruffleException) meta.java_lang_Throwable_backtrace.getObject(guestException).rawForeignObject(language);
        } else {
            return exception;
        }
    }
}
