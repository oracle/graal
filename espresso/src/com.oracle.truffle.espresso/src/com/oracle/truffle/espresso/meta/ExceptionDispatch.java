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

package com.oracle.truffle.espresso.meta;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

/**
 * Allows fast-path runtime guest exception creation.
 */
public final class ExceptionDispatch implements ContextAccess {
    private final Meta meta;

    private final ObjectKlass runtimeException;

    @Override
    public EspressoContext getContext() {
        return meta.getContext();
    }

    @Override
    public Meta getMeta() {
        return meta;
    }

    public ExceptionDispatch(Meta meta) {
        this.meta = meta;
        this.runtimeException = meta.java_lang_RuntimeException;
    }

    /**
     * Quickly initializes frequent runtime exceptions without needing a boundary.
     */
    StaticObject initEx(ObjectKlass klass, StaticObject message, StaticObject cause) {
        if (!CompilerDirectives.inInterpreter() && CompilerDirectives.isPartialEvaluationConstant(klass)) {
            if (StaticObject.isNull(klass.getDefiningClassLoader()) && runtimeException.isAssignableFrom(klass)) {
                return fastPath(klass, message, cause);
            }
        }
        return slowPath(klass, message, cause);
    }

    private StaticObject fastPath(ObjectKlass klass, StaticObject message, StaticObject cause) {
        StaticObject ex = klass.allocateInstance();

        // TODO: Remove this when truffle exceptions are reworked.
        InterpreterToVM.fillInStackTrace(ex, false, meta);

        if (message != null) {
            meta.java_lang_Throwable_detailMessage.setObject(ex, message);
        }
        if (cause != null) {
            meta.java_lang_Throwable_cause.setObject(ex, cause);
        }
        return ex;
    }

    private StaticObject slowPath(ObjectKlass klass, StaticObject message, StaticObject cause) {
        assert meta.java_lang_Throwable.isAssignableFrom(klass);
        StaticObject ex;
        if (CompilerDirectives.isPartialEvaluationConstant(klass)) {
            // if klass was a compilation constant, the constantness of the klass field in ex should
            // propagate even through the boundary.
            ex = klass.allocateInstance();
        } else {
            ex = allocate(klass);
        }
        slowInitEx(ex, klass, message, cause);
        return ex;
    }

    @CompilerDirectives.TruffleBoundary
    private static StaticObject allocate(ObjectKlass klass) {
        return klass.allocateInstance();
    }

    private void slowInitEx(StaticObject ex, ObjectKlass klass, StaticObject message, StaticObject cause) {
        if (message == null && cause == null) {
            // Call constructor.
            doInit(ex, klass);
        } else if (message != null && cause == null) {
            assert StaticObject.isNull(message) || meta.java_lang_String.isAssignableFrom(message.getKlass());
            doMessageInit(ex, klass, message);
        } else if (message == null) {
            assert StaticObject.isNull(cause) || meta.java_lang_Throwable.isAssignableFrom(cause.getKlass());
            doCauseInit(ex, klass, cause);
        } else {
            assert StaticObject.isNull(cause) || meta.java_lang_Throwable.isAssignableFrom(cause.getKlass());
            assert StaticObject.isNull(message) || meta.java_lang_String.isAssignableFrom(message.getKlass());
            doFullInit(ex, klass, message, cause);
        }
    }

    @CompilerDirectives.TruffleBoundary
    private static void doFullInit(StaticObject ex, ObjectKlass klass, StaticObject message, StaticObject cause) {
        klass.lookupDeclaredMethod(Symbol.Name._init_, Symbol.Signature._void_String_Throwable).invokeDirect(ex, message, cause);
    }

    @CompilerDirectives.TruffleBoundary
    private static void doCauseInit(StaticObject ex, ObjectKlass klass, StaticObject cause) {
        klass.lookupDeclaredMethod(Symbol.Name._init_, Symbol.Signature._void_Throwable).invokeDirect(ex, cause);
    }

    @CompilerDirectives.TruffleBoundary
    private static void doMessageInit(StaticObject ex, ObjectKlass klass, StaticObject message) {
        klass.lookupDeclaredMethod(Symbol.Name._init_, Symbol.Signature._void_String).invokeDirect(ex, message);
    }

    @CompilerDirectives.TruffleBoundary
    private static void doInit(StaticObject ex, ObjectKlass klass) {
        klass.lookupDeclaredMethod(Symbol.Name._init_, Symbol.Signature._void).invokeDirect(ex);
    }
}
