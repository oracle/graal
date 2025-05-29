/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.espresso.polyglot;

/**
 * Polyglot API for Espresso.
 * <p>
 * Allows Espresso to interact with other Truffle languages by {@link #eval evaluating} code in a
 * different language, {@link #importObject importing} symbols, exported by other languages and
 * making Espresso objects available to other languages by {@link #exportObject exporting} them.
 * <p>
 * Foreign values, obtained from {@link Polyglot#eval} or {@link Polyglot#importObject}, are
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
 * NB: for Java code running in Espresso, these methods will be intrinsified. Otherwise
 * multi-language environment is not available.
 *
 * @since 21.0
 */
public final class Polyglot {
    private Polyglot() {
        throw new RuntimeException("No instance of Polyglot can be created");
    }

    /**
     * Tests if an object is a foreign value, i.e. originates from a different Truffle language.
     *
     * @since 21.0
     */
    @SuppressWarnings("unused")
    public static boolean isForeignObject(Object object) {
        return false;
    }

    /**
     * If a regular {@link Class#cast} of {@code value} to {@code targetClass} succeeds,
     * {@code Polyglot.cast} succeeds too.
     *
     * In addition, if {@code value} is a {@link Polyglot#isForeignObject foreign} object:
     * <ul>
     * <li>if {@code targetClass} is a primitive class, converts the foreign value to this type and
     * returns the result as a boxed type. To avoid eager conversion, cast to the corresponding
     * wrapper class.
     * <li>if {@code targetClass} is a wrapper class, checks that the foreign value is a number, a
     * boolean or a character respectively, and returns a wrapper of the foreign value. When the
     * {@code value} field of the result is accessed, the current value of the foreign primitive is
     * fetched.
     * <li>if {@code targetClass} is an array class and the foreign value has array elements,
     * returns the foreign value as {@code targetClass}.
     * <li>if {@code targetClass} is a (non-abstract, non-wrapper) class, checks that all the
     * instance fields defined in the class or its ancestors exist in the foreign object. Returns
     * the foreign object as {@code targetClass}.
     * <li>if {@code targetClass} is an interface, returns the foreign object as
     * {@code targetClass}. The existence of methods, defined in {@code targetClass}, is not
     * verified and if a method does not exist, an exception will be thrown only when the method is
     * invoked.
     * </ul>
     * <p>
     *
     * @throws NullPointerException is targetClass is null
     * @throws ClassCastException
     *             <ul>
     *             <li>if {@code value} is a foreign object, {@code targetClass} is a primitive type
     *             but {@code value} does not represent an object of this primitive type
     *             <li>if {@code value} is a foreign object, {@code targetClass} is an array type
     *             but {@code value} does not have array elements
     *             <li>if {@code value} is a foreign object, {@code targetClass} is an abstract
     *             class and {@code value} was not previously cast to a concrete descendant of
     *             {@code targetClass}
     *             <li>if {@code value} is a foreign object, {@code targetClass} is a class and a
     *             field of {@code targetClass} does not exist in the object
     *             <li>if {@code value} is a regular Espresso object and cannot be cast to
     *             {@code targetClass}
     *             </ul>
     * @since 21.0
     */
    public static <T> T cast(Class<? extends T> targetClass, Object value) throws ClassCastException {
        return targetClass.cast(value);
    }

    /**
     * If a regular {@link Class#cast} of {@code value} to {@code target#getRawType} succeeds,
     * {@code Polyglot.castWithGenerics} succeeds too.
     *
     * In addition, if {@code value} is a {@link Polyglot#isForeignObject foreign} object:
     * <ul>
     * <li>if {@code target} represents a primitive class, converts the foreign value to this type
     * and returns the result as a boxed type. To avoid eager conversion, cast to the corresponding
     * wrapper class.
     * <li>if {@code target} is a wrapper class, checks that the foreign value is a number, a
     * boolean or a character respectively, and returns a wrapper of the foreign value. When the
     * {@code value} field of the result is accessed, the current value of the foreign primitive is
     * fetched.
     * <li>if {@code target} represents an array class and the foreign value has array elements,
     * returns the foreign value as {@code target}.
     * <li>if {@code target} represents a (non-abstract, non-wrapper) class, checks that all the
     * instance fields defined in the class or its ancestors exist in the foreign object. Returns
     * the foreign object as {@code target}.
     * <li>if {@code target} represents an interface, returns the foreign object as {@code target}.
     * The existence of methods, defined in {@code target#getRawType}, is not verified and if a
     * method does not exist, an exception will be thrown only when the method is invoked.
     * </ul>
     * <p>
     *
     * @throws NullPointerException is target is null
     * @throws ClassCastException
     *             <ul>
     *             <li>if {@code value} is a foreign object, {@code target} represents a primitive
     *             type but {@code value} does not represent an object of this primitive type
     *             <li>if {@code value} is a foreign object, {@code target} represents an array type
     *             but {@code value} does not have array elements
     *             <li>if {@code value} is a foreign object, {@code target} represents an abstract
     *             class and {@code value} was not previously cast to a concrete descendant of
     *             {@code targetClass}
     *             <li>if {@code value} is a foreign object, {@code target} represents a class and a
     *             field of {@code targetClass} does not exist in the object
     *             <li>if {@code value} is a regular Espresso object and cannot be cast to
     *             {@code targetClass}
     *             </ul>
     * @since 24.2
     */
    public static <T> T castWithGenerics(Object value, TypeLiteral<T> target) throws ClassCastException {
        return target.getRawType().cast(value);
    }

    /**
     * Evaluates the given code in the given language.
     *
     * <p>
     * To access members of the foreign object, write a corresponding class or interface stub in
     * Java and cast the eval result to it using {@link #cast Polyglot.cast}.
     *
     * @param languageId the id of one of the Truffle languages
     * @param sourceCode the source code in the language, identified by {@code languageId}
     *
     * @return the result of the evaluation as {@link Object}.
     *
     * @throws IllegalArgumentException
     *             <ul>
     *             <li>if the language is not available
     *             <li>if parsing of the code fails
     *             </ul>
     * @since 21.0
     */
    @SuppressWarnings("unused")
    public static Object eval(String languageId, String sourceCode) {
        throw new UnsupportedOperationException("Polyglot is not available. Use Espresso to interact with other Truffle languages.");
    }

    /**
     * {@link Polyglot#eval Evaluates} the given code in the given language, and
     * {@link Polyglot#cast casts} the result to the given class.
     * <p>
     * See {@link Polyglot#cast} for the details of casting.
     *
     * @param languageId id of one of the Truffle languages
     * @since 21.0
     */
    public static <T> T eval(String languageId, String sourceCode, Class<? extends T> targetClass) throws ClassCastException {
        return cast(targetClass, eval(languageId, sourceCode));
    }

    /**
     * Imports {@code name} from global Polyglot scope. If {@code name} does not exist in the scope,
     * returns {@code null}.
     *
     * The foreign value is returned as {@link Object}.
     *
     * <p>
     * To access the foreign object's members, write a corresponding class or interface stub in Java
     * and cast the eval result to it using {@link #cast Polyglot.cast}.
     *
     * @since 21.0
     */
    @SuppressWarnings("unused")
    public static Object importObject(String name) {
        throw new UnsupportedOperationException("Polyglot is not available. Use Espresso to interact with other Truffle languages.");
    }

    /**
     * {@link Polyglot#importObject Imports} {@code name} from global Polyglot scope and
     * {@link Polyglot#cast casts} the result to the given class.
     * <p>
     * See {@link Polyglot#cast} for the details of casting.
     *
     * @since 21.0
     */
    public static <T> T importObject(String name, Class<? extends T> targetClass) throws ClassCastException {
        return cast(targetClass, importObject(name));
    }

    /**
     * Exports {@code value} under {@code name} to the Polyglot scope.
     *
     * @since 21.0
     */
    @SuppressWarnings("unused")
    public static void exportObject(String name, Object value) {
        throw new UnsupportedOperationException("Polyglot is not available. Use Espresso to interact with other Truffle languages.");
    }
}
