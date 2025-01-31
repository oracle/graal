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
package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.espresso.vm.VM;

/**
 * A wrapped guest exception. Espresso uses host exceptions to unwind the stack when the guest
 * throws, so we have to wrap guest with host here.
 */
@ExportLibrary(value = InteropLibrary.class, delegateTo = "exception")
@SuppressWarnings("serial")
public final class EspressoException extends AbstractTruffleException {
    protected final StaticObject exception;

    private EspressoException(@JavaType(Throwable.class) StaticObject throwable) {
        assert StaticObject.notNull(throwable);
        assert InterpreterToVM.instanceOf(throwable, throwable.getKlass().getMeta().java_lang_Throwable);
        this.exception = throwable;
    }

    public static EspressoException wrap(@JavaType(Throwable.class) StaticObject throwable, Meta meta) {
        if (throwable.isForeignObject()) {
            // We can't have hidden fields in foreign objects yet unfortunately.
            return new EspressoException(throwable);
        }
        // we must only have one wrapper per thrown exception for truffle's stack trace
        // mechanisms to work.
        EspressoException wrapper = (EspressoException) meta.HIDDEN_EXCEPTION_WRAPPER.getHiddenObject(throwable);
        if (wrapper != null) {
            return wrapper;
        }
        wrapper = new EspressoException(throwable);
        meta.HIDDEN_EXCEPTION_WRAPPER.setHiddenObject(throwable, wrapper);
        return wrapper;
    }

    public static VM.StackTrace getFrames(StaticObject exception, Meta meta) {
        return (VM.StackTrace) meta.HIDDEN_FRAMES.getHiddenObject(exception);
    }

    @Override
    public String getMessage() {
        return getMessage(exception);
    }

    public static StaticObject getGuestMessage(StaticObject e) {
        // this is used in toString, too dangerous to call a method
        return (StaticObject) e.getKlass().getMeta().java_lang_Throwable_detailMessage.get(e);
    }

    public static StaticObject getGuestCause(StaticObject e) {
        StaticObject cause = (StaticObject) e.getKlass().getMeta().java_lang_Throwable_cause.get(e);
        if (cause == e) {
            return StaticObject.NULL;
        }
        return cause;
    }

    public static String getMessage(StaticObject e) {
        return Meta.toHostStringStatic(getGuestMessage(e));
    }

    public StaticObject getGuestMessage() {
        return getGuestMessage(getGuestException());
    }

    public StaticObject getGuestException() {
        return exception;
    }

    @Override
    public String toString() {
        return "EspressoException<" + getGuestException() + ": " + getMessage() + ">";
    }

    // Debug methods

    @SuppressWarnings("unused")
    private boolean match(String exceptionClass, String message) {
        if (exceptionClass == null) {
            return getMessage() != null && getMessage().contains(message);
        }
        if (getGuestException().getKlass().getType().toString().contains(exceptionClass)) {
            if (message == null) {
                return true;
            }
            if (getMessage() == null) {
                return false;
            }
            return getMessage().contains(message);
        }
        return false;
    }

    @SuppressWarnings("unused")
    private boolean match(String exceptionClass) {
        return match(exceptionClass, null);
    }
}
