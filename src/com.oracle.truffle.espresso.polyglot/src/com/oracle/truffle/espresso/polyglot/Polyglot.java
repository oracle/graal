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
 * <p>
 * Foreign objects, obtained from {@link Polyglot#eval} or {@link Polyglot#importObject}, are
 * returned as {@link Object}. There are two options to make their members accessible from Espresso:
 * <ul>
 * <li>{@link Polyglot#cast Polyglot.cast} the object to a Java interface. In this case, method
 * calls to the interface methods will be forwarded to the foreign object.
 * <li>{@link Polyglot#cast Polyglot.cast} the object to a Java class. In this case, accesses to the
 * class fields will be forwarded to the foreign object, but the Java implementations of the methods
 * will be used.
 * </ul>
 * Casting to abstract classes is not supported.
 * <p>
 * NB: if Espresso is running on GraalVM, the method implementations of this class are substituted.
 * Otherwise multi-language environment is not available.
 */
public final class Polyglot {
    private Polyglot() {
    }

    /**
     * Tests if an object is a foreign object, i.e. originates from a different Truffle language.
     */
    @SuppressWarnings("unused")
    public static boolean isForeignObject(Object object) {
        return false;
    }

    /**
     * If {@code value} is a {@link Polyglot#isForeignObject foreign} object:
     * <ul>
     * <li>if {@code targetClass} is a primitive class, converts the foreign value to this type and
     * returns the result as a boxed type.
     * <li>if {@code targetClass} is a (non-abstract) class, checks that all the instance fields
     * defined in the class or its ancestors exist in the foreign object. Returns the foreign object
     * as a {@code targetClass}.
     * <li>if {@code targetClass} is an interface, returns the foreign object as a
     * {@code targetClass}. The existence of methods, defined in {@code targetClass}, is not
     * verified and if a method does not exist, an exception will be thrown only when this method is
     * invoked.
     * </ul>
     * <p>
     * If {@code value} is a regular Espresso object, performs {@link Class#cast checkcast}.
     *
     * @throws ClassCastException
     *             <ul>
     *             <li>if {@code value} is a foreign object, {@code targetClass} is a primitive type
     *             but {@code value} does not represent an object of this primitive type
     *             <li>if {@code value} is a foreign object, {@code targetClass} is a class and a
     *             field of {@code targetClass} does not exist in the object
     *             <li>if {@code value} is a regular Espresso object and cannot be cast to
     *             {@code targetClass}
     *             </ul>
     */
    public static <T> T cast(Class<? extends T> targetClass, Object value) throws ClassCastException {
        return targetClass.cast(value);
    }

    /**
     * Evaluates the given code in the given language.
     *
     * @param languageId the id of one of the Truffle languages
     * @param sourceCode the source code in the {@code language}
     *
     * @return the result of the evaluation as {@link Object}.
     *
     * @throws IllegalArgumentException
     *             <ul>
     *             <li>if the language is not available
     *             <li>if parsing of the code fails
     *             </ul>
     *
     * @apiNote To access members of the foreign object, write a corresponding class or interface
     *          stub in Java and cast the eval result to it using {@link #cast Polyglot.cast}.
     */
    @SuppressWarnings("unused")
    public static Object eval(String languageId, String sourceCode) {
        throw new UnsupportedOperationException("Polyglot is not available. Run Espresso on GraalVM to interact with other Truffle languages.");
    }

    /**
     * Imports {@code name} from global Polyglot scope. If {@code name} does not exist in the scope,
     * returns {@code null}.
     *
     * The foreign value is returned as {@link Object}.
     *
     * @apiNote To access the foreign object's members, write a corresponding class or interface
     *          stub in Java and cast the eval result to it using {@link #cast Polyglot.cast}.
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
