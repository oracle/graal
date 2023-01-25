/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.runtime.dispatch;

import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.espresso.runtime.StaticObject;

@ExportLibrary(value = InteropLibrary.class, receiverType = StaticObject.class)
public class ThrowableInterop extends EspressoInterop {

    @ExportMessage
    public static ExceptionType getExceptionType(@SuppressWarnings("unused") StaticObject receiver) {
        return ExceptionType.RUNTIME_ERROR;
    }

    @ExportMessage
    public static boolean isException(StaticObject object) {
        object.checkNotForeign();
        return true;
    }

    @ExportMessage
    public static RuntimeException throwException(StaticObject object) {
        object.checkNotForeign();
        throw object.getKlass().getMeta().throwException(object);
    }

    @ExportMessage
    public static boolean hasExceptionCause(StaticObject object) {
        object.checkNotForeign();
        return getMeta().java_lang_Throwable_cause.getObject(object) != StaticObject.NULL;
    }

    @ExportMessage
    public static Object getExceptionCause(StaticObject object) throws UnsupportedMessageException {
        object.checkNotForeign();
        if (!hasExceptionCause(object)) {
            throw UnsupportedMessageException.create();
        }
        return getMeta().java_lang_Throwable_cause.getObject(object);
    }

    @ExportMessage
    public static boolean hasExceptionMessage(StaticObject object) {
        object.checkNotForeign();
        return getMeta().java_lang_Throwable_detailMessage.getObject(object) != StaticObject.NULL;
    }

    @ExportMessage
    public static Object getExceptionMessage(StaticObject object) throws UnsupportedMessageException {
        object.checkNotForeign();
        if (!hasExceptionMessage(object)) {
            throw UnsupportedMessageException.create();
        }
        return getMeta().java_lang_Throwable_detailMessage.getObject(object);
    }

}
