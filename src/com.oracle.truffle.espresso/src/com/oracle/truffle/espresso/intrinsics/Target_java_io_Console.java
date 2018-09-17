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
package com.oracle.truffle.espresso.intrinsics;

import java.io.Console;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.runtime.StaticObject;

@EspressoIntrinsics
public class Target_java_io_Console {

    enum ConsoleFunctions {
        ISTTY("istty"),
        ENCODING("encoding"),
        ECHO("echo", boolean.class);

        ConsoleFunctions(String name, Class<?>... parameterTypes) {
            try {
                this.method = Console.class.getDeclaredMethod(name, parameterTypes);
                this.method.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        private final Method method;

        public Method getMethod() {
            return method;
        }

        public Object invokeStatic(Object... args) {
            try {
                return getMethod().invoke(null, args);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }

    }

    @Intrinsic
    public static boolean istty() {
        return (boolean) ConsoleFunctions.ISTTY.invokeStatic();
    }

    @Intrinsic
    public static @Type(String.class) StaticObject encoding() {
        return EspressoLanguage.getCurrentContext().getMeta().toGuest((String) ConsoleFunctions.ENCODING.invokeStatic());
    }

    @Intrinsic
    public static boolean echo(boolean on) {
        // throws IOException;
        return (boolean) ConsoleFunctions.ECHO.invokeStatic(on);
    }
}
