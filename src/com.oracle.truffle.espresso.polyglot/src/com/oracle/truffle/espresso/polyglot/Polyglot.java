/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.polyglot;

/**
* Represents the Polyglot API, available from Espresso.
* The method implementations are substituted, the code in this class is not used.
*/
public final class Polyglot {
    private Polyglot() {
    }

    /**
     * @param object the object to test
     * @return true if the object is an interop object, i.e. comes from a different Truffle language.
     */
    @SuppressWarnings("unused")
    public static boolean isInteropObject(Object object) {
        return false;
    }

    /**
     * If <code>value</code> is an interop object, from now on treat it as an object of <code>targetClass</code> type. Otherwise the case is a
     * no-op, e.g. the existence of methods is not verified and if a method does not exist, an exception will be thrown
     * <i> on method invocation </i>.
     * <p>
     * If <code>value</code> is a regular Espresso object, perform checkcast.
     * @throws ClassCastException if <code>value</code> is a regular Espresso object and cannot be cast to <code>targetClass</code>.
     */
    public static <T> T cast(Class<? extends T> targetClass, Object value) throws ClassCastException {
        return targetClass.cast(value);
    }

    @SuppressWarnings("unused")
    public static Object eval(String language, String code) {
        return null;
    }

    @SuppressWarnings("unused")
    public static Object getBinding(String name) {
        return null;
    }

    @SuppressWarnings("unused")
    public static void setBinding(String name, Object value) {
    }
}
