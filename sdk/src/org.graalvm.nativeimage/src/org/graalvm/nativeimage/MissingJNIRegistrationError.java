/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * This exception is thrown when a JNI query tries to access an element that was not
 * <a href= "https://www.graalvm.org/latest/reference-manual/native-image/metadata/#jni">registered
 * for JNI access</a> in the program. When an element is not registered, the exception will be
 * thrown both for elements that exist and elements that do not exist on the given classpath.
 * <p/>
 * The purpose of this exception is to easily discover unregistered elements and to assure that all
 * JNI operations for registered elements have the expected behaviour.
 * <p/>
 * Queries will succeed (or throw the expected error) if the element was registered for JNI access.
 * If that is not the case, a {@link MissingJNIRegistrationError} will be thrown.
 * <p/>
 * The exception thrown by the JNI query is a <em>pending</em> exception that needs to be explicitly
 * checked by the calling native code.
 * </ol>
 * Examples:
 * <p/>
 * Registration: {@code "fields": [{"name": "registeredField"}, {"name":
 * "registeredNonexistentField"}]}<br>
 * {@code GetFieldID(declaringClass, "registeredField")} will return the expected field.<br>
 * {@code GetFieldID(declaringClass, "registeredNonexistentField")} will throw a
 * {@link NoSuchFieldError}.<br>
 * {@code GetFieldID(declaringClass, "unregisteredField")} will throw a
 * {@link MissingJNIRegistrationError}.<br>
 * {@code GetFieldID(declaringClass, "unregisteredNonexistentField")} will throw a
 * {@link MissingJNIRegistrationError}.<br>
 *
 * @since 24.1
 */
public final class MissingJNIRegistrationError extends Error {
    @Serial private static final long serialVersionUID = -8940056537864516986L;

    private final Class<?> elementType;

    private final Class<?> declaringClass;

    private final String elementName;

    private final String signature;

    /**
     * @since 24.1
     */
    public MissingJNIRegistrationError(String message, Class<?> elementType, Class<?> declaringClass, String elementName, String signature) {
        super(message);
        this.elementType = elementType;
        this.declaringClass = declaringClass;
        this.elementName = elementName;
        this.signature = signature;
    }

    /**
     * @return The type of the element trying to be queried ({@link Class}, {@link Method},
     *         {@link Field} or {@link Constructor}).
     * @since 23.0
     */
    public Class<?> getElementType() {
        return elementType;
    }

    /**
     * @return The class on which the missing query was tried, or null on static queries.
     * @since 23.0
     */
    public Class<?> getDeclaringClass() {
        return declaringClass;
    }

    /**
     * @return The name of the queried element.
     * @since 23.0
     */
    public String getElementName() {
        return elementName;
    }

    /**
     * @return The signature passed to the query, or null if the query doesn't take a signature as
     *         argument.
     * @since 23.0
     */
    public String getSignature() {
        return signature;
    }
}
