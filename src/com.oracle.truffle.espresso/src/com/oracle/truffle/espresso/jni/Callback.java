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
package com.oracle.truffle.espresso.jni;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.espresso.meta.EspressoError;

public class Callback implements TruffleObject {

    private final int arity;
    private final Function function;

    public Callback(int arity, Function function) {
        this.arity = arity;
        this.function = function;
    }

    @CompilerDirectives.TruffleBoundary
    Object call(Object... args) {
        if (args.length == arity) {
            Object ret = function.call(args);
            return ret;
        } else {
            throw ArityException.raise(arity, args.length);
        }
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return CallbackMessageResolutionForeign.ACCESS;
    }

    public interface Function {
        Object call(Object... args);
    }

    private static Callback wrap(Object receiver, Method m) {
        assert m != null;
        return new Callback(m.getParameterCount(), args -> {
            try {
                return m.invoke(receiver, args);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        });
    }

    static Callback wrapStaticMethod(Class<?> clazz, String methodName, Class<?> parameterTypes) {
        Method m;
        try {
            m = clazz.getDeclaredMethod(methodName, parameterTypes);
            assert Modifier.isStatic(m.getModifiers());
        } catch (NoSuchMethodException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
        return wrap(clazz, m);
    }

    public static Callback wrapInstanceMethod(Object receiver, String methodName, Class<?> parameterTypes) {
        Method m;
        try {
            m = receiver.getClass().getDeclaredMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
        return wrap(receiver, m);
    }
}
