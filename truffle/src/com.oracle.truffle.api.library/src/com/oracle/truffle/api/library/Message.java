/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.library;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * Represents a description of library message. A message description refers to one public method in
 * a {@link Library library} subclass. Messages may be resolved dynamically by calling
 * {@link #resolve(Class, String)} with a known library class and message name. Message instances
 * provide meta-data about the simple and qualified name of the message, return type, receiver type,
 * parameter types and library name. Message instances are used to invoke library messages or
 * implement library messages reflectively using the {@link ReflectionLibrary reflection library}.
 * <p>
 * Message instances are globally unique and can safely be compared by identity. In other words, if
 * the same message is {@link #resolve(Class, String) resolved} twice the same instance will be
 * returned. Since they are shared message instances must not be used as locks to avoid deadlocks.
 * <p>
 * Note: This class is intended to be sub-classed by generated code only and must *not* be
 * sub-classed by user-code.
 *
 * @see ReflectionLibrary
 * @see Library
 * @since 19.0
 */
public abstract class Message {

    private final String simpleName;
    private final String qualifiedName;
    private final int hash;
    private final Class<?> returnType;
    private final Class<? extends Library> libraryClass;
    private final List<Class<?>> parameterTypes;
    private final int parameterCount;
    @CompilationFinal LibraryFactory<Library> library;

    /**
     * @since 19.0
     */
    @SuppressWarnings("unchecked")
    protected Message(Class<? extends Library> libraryClass, String messageName, Class<?> returnType, Class<?>... parameterTypes) {
        Objects.requireNonNull(libraryClass);
        Objects.requireNonNull(messageName);
        Objects.requireNonNull(returnType);
        this.libraryClass = libraryClass;
        this.simpleName = messageName.intern();
        this.returnType = returnType;
        this.parameterTypes = Collections.unmodifiableList(Arrays.asList(parameterTypes));
        this.qualifiedName = (getLibraryName() + "." + simpleName).intern();
        this.parameterCount = parameterTypes.length;
        this.hash = qualifiedName.hashCode();
    }

    /**
     * Returns a qualified and unique name of this message. The qualified name is specified as
     * <code>getLibraryName() + "." + getSimpleName()</code>. The returned name is
     * {@link String#intern() interned} can can safely be compared by identity. The returned name is
     * never <code>null</code>.
     *
     * @see #getSimpleName()
     * @see #getLibraryName()
     * @since 19.0
     */
    public final String getQualifiedName() {
        return qualifiedName;
    }

    /**
     * Returns the simple name of this message. The simple name is unique per library and equals the
     * method name of the method in the library specification class. The returned name is
     * {@link String#intern() interned} can can safely be compared by identity. The returned name is
     * never <code>null</code>.
     *
     * @since 19.0
     */
    public final String getSimpleName() {
        return simpleName;
    }

    /**
     * Returns the name of the library of this message. The name of the library is specified as the
     * {@link Class#getName() name} of the {@link #getLibraryClass() library class}. The returned
     * name is never <code>null</code>.
     *
     * @since 19.0
     */
    public final String getLibraryName() {
        return getLibraryClass().getName();
    }

    /**
     * Returns the return type of the message. The return type can be useful for
     * {@link ReflectionLibrary reflective} invocations of the message.
     *
     * @since 19.0
     */
    public final Class<?> getReturnType() {
        return returnType;
    }

    /**
     * Returns the receiver type of the message. The receiver type is always the same as the first
     * {@link #getParameterTypes() parameter type}. In many cases the receiver type of a message is
     * {@link Object}. However, it possible for libraries to restrict the receiver type to
     * sub-types. The receiver type may be useful for {@link ReflectionLibrary reflective}
     * invocations of the message.
     *
     * @since 19.0
     */
    public final Class<?> getReceiverType() {
        return parameterTypes.get(0);
    }

    /**
     * Returns all parameter types including the receiver type of the message. The returned
     * immutable parameter types list corresponds to the {@link Method#getParameterTypes()}
     * parameter types of the declared library method. The parameter types may be useful for
     * {@link ReflectionLibrary reflective} invocations of the message.
     *
     * @since 19.0
     */
    public final List<Class<?>> getParameterTypes() {
        return parameterTypes;
    }

    /**
     * Returns the number of parameters including the receiver type.
     *
     * @since 19.0
     */
    public final int getParameterCount() {
        return parameterCount;
    }

    /**
     * Returns the library class of this message. The library class may be used to
     * {@link #resolve(Class, String) resolve} other messages of the same library.
     *
     * @since 19.0
     */
    public final Class<? extends Library> getLibraryClass() {
        return libraryClass;
    }

    /**
     * @since 19.0
     */
    public final LibraryFactory<?> getFactory() {
        return library;
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public final boolean equals(Object obj) {
        return this == obj;
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public final int hashCode() {
        return hash;
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    protected final Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public final String toString() {
        StringBuilder b = new StringBuilder();
        b.append("Message[");
        b.append(getReturnType().getSimpleName());
        b.append(" ").append(getQualifiedName());
        b.append("(");
        String sep = "";
        for (Class<?> param : getParameterTypes()) {
            b.append(sep);
            b.append(param.getSimpleName());
            sep = ", ";
        }
        b.append(")");
        return b.toString();
    }

    /**
     * Resolves a message globally for a given library class and message name. The message name
     * corresponds to the method name of the library message. The returned message always returns
     * the same instance for a combination of library class and message. The provided library class
     * and message name must not be <code>null</code>. If the library or message is invalid or not
     * found an {@link IllegalArgumentException} is thrown.
     *
     * @param libraryClass the class of the library this message is contained in.
     * @param messageName the simple name of this message.
     * @since 19.0
     */
    @TruffleBoundary
    public static Message resolve(Class<? extends Library> libraryClass, String messageName) {
        return LibraryFactory.resolveMessage(libraryClass, messageName, true);
    }

    /**
     * Resolves a message globally for a given library class and message name. The message name
     * corresponds to the method name of the library message. The returned message always returns
     * the same instance for a combination of library class and message. The provided library class
     * and message name must not be <code>null</code>.
     *
     * @param libraryClass the class of the library this message is contained in.
     * @param messageName the simple name of this message.
     * @param fail whether to fail with an {@link IllegalArgumentException} or return
     *            <code>null</code> if the message was not found.
     * @since 19.0
     */
    @TruffleBoundary
    public static Message resolve(Class<? extends Library> libraryClass, String messageName, boolean fail) {
        return LibraryFactory.resolveMessage(libraryClass, messageName, fail);
    }

}
