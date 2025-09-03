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
package com.oracle.truffle.espresso.runtime.dispatch.staticobject;

import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Names;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Signatures;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.dispatch.messages.GenerateInteropNodes;
import com.oracle.truffle.espresso.runtime.dispatch.messages.Shareable;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

@GenerateInteropNodes
@Shareable
@ExportLibrary(value = InteropLibrary.class, receiverType = StaticObject.class)
public class ThrowableInterop extends EspressoInterop {

    @ExportMessage
    @SuppressWarnings("unused")
    public static ExceptionType getExceptionType(StaticObject receiver) throws UnsupportedMessageException {
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
        Meta meta = object.getKlass().getMeta();
        Method resolvedMessageMethod = object.getKlass().lookupMethod(Names.getCause, Signatures.Throwable);
        if (resolvedMessageMethod == meta.java_lang_Throwable_getCause) {
            // not overridden, then we can trust the field value
            StaticObject guestCause = meta.java_lang_Throwable_cause.getObject(object);
            return StaticObject.notNull(guestCause) && guestCause != object;
        } else if (resolvedMessageMethod.isInlinableGetter()) {
            // only call the method for a 'has' interop message if it's simple
            StaticObject guestCause = (StaticObject) resolvedMessageMethod.invokeDirect(object);
            return StaticObject.notNull(guestCause) && guestCause != object;
        } else {
            /*
             * not a simple method, so we might end up returning guest null for
             * 'getExceptionMessage' which is OK in this case
             */
            return true;
        }
    }

    @ExportMessage
    public static Object getExceptionCause(StaticObject object) throws UnsupportedMessageException {
        object.checkNotForeign();
        if (!hasExceptionCause(object)) {
            throw UnsupportedMessageException.create();
        }
        return object.getKlass().lookupMethod(Names.getCause, Signatures.Throwable).invokeDirect(object);
    }

    @ExportMessage
    public static boolean hasExceptionMessage(StaticObject object) {
        object.checkNotForeign();
        Meta meta = object.getKlass().getMeta();
        Method resolvedMessageMethod = object.getKlass().lookupMethod(Names.getMessage, Signatures.String);
        if (resolvedMessageMethod == meta.java_lang_Throwable_getMessage) {
            // not overridden, then we can trust the field value
            return StaticObject.notNull(meta.java_lang_Throwable_detailMessage.getObject(object));
        } else if (resolvedMessageMethod.isInlinableGetter()) {
            // only call the method for a 'has' interop message if it's simple
            return StaticObject.notNull((StaticObject) resolvedMessageMethod.invokeDirect(object));
        } else {
            /*
             * not a simple method, so we might end up returning guest null for
             * 'getExceptionMessage' which is OK in this case
             */
            return true;
        }
    }

    @ExportMessage
    public static Object getExceptionMessage(StaticObject object) throws UnsupportedMessageException {
        object.checkNotForeign();
        if (!hasExceptionMessage(object)) {
            throw UnsupportedMessageException.create();
        }
        return object.getKlass().lookupMethod(Names.getMessage, Signatures.String).invokeDirect(object);
    }
}
