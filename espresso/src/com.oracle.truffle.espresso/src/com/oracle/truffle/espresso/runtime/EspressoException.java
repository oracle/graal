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

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.espresso.vm.VM;

// TODO(peterssen): Fix deprecation, GR-26729
@SuppressWarnings("deprecation")
public final class EspressoException extends RuntimeException implements com.oracle.truffle.api.TruffleException {
    private static final long serialVersionUID = -7667957575377419520L;
    private final StaticObject exception;

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

    public StaticObject getGuestMessage() {
        return (StaticObject) exception.getKlass().lookupMethod(Name.getMessage, Signature.String).invokeDirect(exception);
    }

    public static String getMessage(StaticObject e) {
        return Meta.toHostStringStatic((StaticObject) e.getKlass().lookupMethod(Name.getMessage, Signature.String).invokeDirect(e));
    }

    @SuppressWarnings("sync-override")
    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    @Override
    public StaticObject getExceptionObject() {
        return exception;
    }

    @Override
    public Node getLocation() {
        return null;
    }

    @Override
    public SourceSection getSourceLocation() {
        return null;
    }

    @Override
    public String toString() {
        return "EspressoException<" + getExceptionObject() + ": " + getMessage() + ">";
    }

    // Debug methods

    @SuppressWarnings("unused")
    private boolean match(String exceptionClass, String message) {
        if (exceptionClass == null) {
            return getMessage() != null && getMessage().contains(message);
        }
        if (getExceptionObject().getKlass().getType().toString().contains(exceptionClass)) {
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
