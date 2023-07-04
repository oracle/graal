/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage;

import java.io.Serial;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * This exception is thrown when a reflective query (such as
 * {@link Class#getMethod(String, Class[])}) tries to access an element that was not <a href=
 * "https://www.graalvm.org/latest/reference-manual/native-image/metadata/#reflection">registered
 * for reflection</a> in the program. When an element is not registered, the exception will be
 * thrown both for elements that exist and elements that do not exist on the given classpath.
 * <p/>
 * The purpose of this exception is to easily discover unregistered elements and to assure that all
 * reflective operations for registered elements have the expected behaviour.
 * <p/>
 * We distinguish between two types of reflective queries: bulk queries and individual queries.
 * <ol>
 * <li>Bulk queries are methods like {@link Class#getFields()} which return a complete list of
 * corresponding elements. Those queries need to be explicitly registered for reflection in order to
 * be called. If that is not the case, a {@link MissingReflectionRegistrationError} will be
 * thrown.</li>
 * <li>Individual queries are methods like {@link Class#getField(String)} which return a single
 * element. Those queries will succeed (or throw the expected {@link ReflectiveOperationException}
 * if either the element was individually registered for reflection, or the corresponding bulk query
 * was registered for reflection. If that is not the case, a
 * {@link MissingReflectionRegistrationError} will be thrown. Some individual queries, like
 * {@link Class#forName(String)}, do not have a corresponding bulk query and as such need their
 * arguments to be individually registered for reflection in order to behave correctly.</li>
 * </ol>
 * Examples:
 * <p/>
 * Registration: {@code "queryAllDeclaredMethods": true}<br>
 * {@code declaringClass.getDeclaredMethods()} will succeed.<br>
 * {@code declaringClass.getDeclaredMethod("existingMethod")} will return the expected method.<br>
 * {@code declaringClass.getDeclaredMethod("nonexistentMethod")} will throw a
 * {@link NoSuchMethodException}.
 * <p/>
 * Registration: {@code "fields": [{"name": "registeredField"}, {"name":
 * "registeredNonexistentField"}]}<br>
 * {@code declaringClass.getDeclaredFields()} will throw a
 * {@link MissingReflectionRegistrationError}.<br>
 * {@code declaringClass.getField("registeredField")} will return the expected field.<br>
 * {@code declaringClass.getField("registeredNonexistentField")} will throw a
 * {@link NoSuchFieldException}.<br>
 * {@code declaringClass.getField("unregisteredField")} will throw a
 * {@link MissingReflectionRegistrationError}.<br>
 * {@code declaringClass.getField("unregisteredNonexistentField")} will throw a
 * {@link MissingReflectionRegistrationError}.<br>
 *
 * @since 23.0
 */
public final class MissingReflectionRegistrationError extends Error {
    @Serial private static final long serialVersionUID = 2764341882856270640L;

    private final Class<?> elementType;

    private final Class<?> declaringClass;

    private final String elementName;

    private final Class<?>[] parameterTypes;

    /**
     * @since 23.0
     */
    public MissingReflectionRegistrationError(String message, Class<?> elementType, Class<?> declaringClass, String elementName, Class<?>[] parameterTypes) {
        super(message);
        this.elementType = elementType;
        this.declaringClass = declaringClass;
        this.elementName = elementName;
        this.parameterTypes = parameterTypes;
    }

    /**
     * @return The type of the element trying to be queried ({@link Class}, {@link Method},
     *         {@link Field} or {@link Constructor}), or null if the query is a bulk query (like
     *         {@link Class#getMethods()}).
     *
     * @since 23.0
     */
    public Class<?> getElementType() {
        return elementType;
    }

    /**
     * @return The class on which the missing query was tried, or null on static queries (e.g.
     *         {@link Class#forName(String)}).
     *
     * @since 23.0
     */
    public Class<?> getDeclaringClass() {
        return declaringClass;
    }

    /**
     * @return The name of the queried element, or bulk query method (e.g. {@code "getMethods"}).
     *
     * @since 23.0
     */
    public String getElementName() {
        return elementName;
    }

    /**
     * @return The parameter types passed to the query, or null if the query doesn't take parameter
     *         types as argument.
     *
     * @since 23.0
     */
    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }
}
