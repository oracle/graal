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
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Names;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Signatures;
import com.oracle.truffle.espresso.impl.ContextAccessImpl;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

/**
 * Allows fast-path creation of well-known guest runtime exceptions .
 */
public final class ExceptionDispatch extends ContextAccessImpl {
    private final Meta meta;

    private final ObjectKlass runtimeException;

    public ExceptionDispatch(Meta meta) {
        super(meta.getContext());
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
        StaticObject ex = allocateException(klass);
        // TODO: Remove this when truffle exceptions are reworked.
        InterpreterToVM.fillInStackTrace(ex, meta);

        // Support extended NPE messages
        if (meta.java_lang_NullPointerException == klass && meta.java_lang_NullPointerException_extendedMessageState != null) {
            meta.java_lang_NullPointerException_extendedMessageState.setInt(ex, 1);
        }

        if (message != null) {
            meta.java_lang_Throwable_detailMessage.setObject(ex, message);
        }
        if (cause != null) {
            meta.java_lang_Throwable_cause.setObject(ex, cause);
        }
        return ex;
    }

    private StaticObject allocateException(ObjectKlass klass) {
        EspressoContext ctx = getContext();
        // avoid PE recursion in the checks in klass.allocateInstance
        // the exception types allocated here should be well-known and well-behaved
        assert !klass.isAbstract() && !klass.isInterface();
        return ctx.getAllocator().createNew(klass);
    }

    private StaticObject slowPath(ObjectKlass klass, StaticObject message, StaticObject cause) {
        assert meta.java_lang_Throwable.isAssignableFrom(klass);
        StaticObject ex;
        // if klass was a compilation constant, the constantness of the klass field in ex should
        // propagate even through the boundary.
        ex = allocateException(klass);
        slowInitEx(ex, klass, message, cause);
        return ex;
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
            if (!doFullInit(ex, klass, message, cause)) {
                doMessageInit(ex, klass, message);
                meta.java_lang_Throwable_initCause.invokeDirectVirtual(ex, cause);
            }
        }
    }

    @CompilerDirectives.TruffleBoundary
    private static boolean doFullInit(StaticObject ex, ObjectKlass klass, StaticObject message, StaticObject cause) {
        Method method = klass.lookupDeclaredMethod(Names._init_, Signatures._void_String_Throwable);
        if (method == null) {
            return false;
        }
        method.invokeDirectSpecial(ex, message, cause);
        return true;
    }

    @CompilerDirectives.TruffleBoundary
    private static void doCauseInit(StaticObject ex, ObjectKlass klass, StaticObject cause) {
        Method method = klass.lookupDeclaredMethod(Names._init_, Signatures._void_Throwable);
        assert method != null : "No (Throwable) constructor in " + klass;
        method.invokeDirectSpecial(ex, cause);
    }

    @CompilerDirectives.TruffleBoundary
    private static void doMessageInit(StaticObject ex, ObjectKlass klass, StaticObject message) {
        Method method = klass.lookupDeclaredMethod(Names._init_, Signatures._void_String);
        assert method != null : "No (String) constructor in " + klass;
        method.invokeDirectSpecial(ex, message);
    }

    @CompilerDirectives.TruffleBoundary
    private static void doInit(StaticObject ex, ObjectKlass klass) {
        Method method = klass.lookupDeclaredMethod(Names._init_, Signatures._void);
        assert method != null : "No () constructor in " + klass;
        method.invokeDirectSpecial(ex);
    }
}
