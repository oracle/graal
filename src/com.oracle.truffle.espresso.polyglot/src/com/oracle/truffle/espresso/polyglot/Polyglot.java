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
 * Polyglot API for Espresso.
 * <p>
 * Allows Espresso to interact with other Truffle languages by {@link #eval evaluating} code in a
 * different language, {@link #importObject importing} symbols, exported by other languages and
 * making Espresso objects available to other languages by {@link #exportObject exporting} them.
 * </p>
 *
 * NB: At runtime, the method implementations are substituted, only the definitions of this class
 * are used.
 */
public final class Polyglot {
    private Polyglot() {
    }

    /**
     * Tests if an object is an interop object, i.e. originates from a different Truffle language.
     */
    @SuppressWarnings("unused")
    public static boolean isInteropObject(Object object) {
        return false;
    }

    /**
     * If {@code value} is an interop object, changes its type to {@code targetClass}. The existence
     * of methods, defined in {@code targetClass}, is not verified and if a method does not exist,
     * an exception will be thrown only when this method is invoked.
     * <p>
     * If {@code value} is a regular Espresso object, performs {@link Class#cast checkcast}.
     *
     * @throws ClassCastException if {@code value} is a regular Espresso object and cannot be cast
     *             to {@code targetClass}.
     */
    public static <T> T cast(Class<? extends T> targetClass, Object value) throws ClassCastException {
        return targetClass.cast(value);
    }

    // TODO: link something about language ids?
    /**
     * Evaluates the given code in the given language.
     *
     * @return the result of the evaluation wrapped as {@link Object}. To access members of the
     *         underlying interop object, write a corresponding class or interface stub in Java and
     *         cast the eval result to it using {@link #cast Polyglot.cast}.
     */
    @SuppressWarnings("unused")
    public static Object eval(String language, String code) {
        throw new UnsupportedOperationException("Polyglot is not available. Run Espresso on GraalVM to interact with other Truffle languages.");
    }

    /**
     * Imports {@code name} from global Polyglot scope. If {@code name} does not exist in the scope,
     * returns {@code null}.
     *
     * The returned interop value is wrapped as {@link Object}. To access members of the underlying
     * interop object, write a corresponding class or interface stub in Java and cast the eval
     * result to it using {@link #cast Polyglot.cast}.
     */
    @SuppressWarnings("unused")
    public static Object importObject(String name) {
        throw new UnsupportedOperationException("Polyglot is not available. Run Espresso on GraalVM to interact with other Truffle languages.");
    }

    /**
     * Exports {@code value} under {@code name} to the Polyglot scope.
     */
    @SuppressWarnings("unused")
    public static void exportObject(String name, Object value) {
        throw new UnsupportedOperationException("Polyglot is not available. Run Espresso on GraalVM to interact with other Truffle languages.");
    }
}
