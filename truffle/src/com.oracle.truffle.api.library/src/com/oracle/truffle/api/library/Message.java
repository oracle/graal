/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

public abstract class Message {

    private final String simpleName;
    private final String qualifiedName;
    private final int hash;
    private final Class<?> returnType;
    private final Class<? extends Library> libraryClass;
    private final List<Class<?>> parameterTypes;
    @CompilationFinal ResolvedLibrary<Library> library;

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
        this.hash = qualifiedName.hashCode();
    }

    public final String getQualifiedName() {
        return qualifiedName;
    }

    public final String getSimpleName() {
        return simpleName;
    }

    public final Class<?> getReturnType() {
        return returnType;
    }

    public final Class<?> getReceiverType() {
        return parameterTypes.get(0);
    }

    public final List<Class<?>> getParameterTypes() {
        return parameterTypes;
    }

    public final String getLibraryName() {
        return getLibraryClass().getName();
    }

    public final Class<? extends Library> getLibraryClass() {
        return libraryClass;
    }

    final ResolvedLibrary<?> getResolvedLibrary() {
        return library;
    }

    @Override
    public final boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public final int hashCode() {
        return hash;
    }

    @Override
    protected final Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    @Override
    public final String toString() {
        return "Message[" + getLibraryClass().getSimpleName() + ":" + simpleName + "]";
    }

    public static Message resolve(String libraryName, String messageName) {
        return ResolvedLibrary.resolveMessage(libraryName, messageName);
    }

    public static Message lookup(Class<? extends Library> libraryClass, String messageName) {
        return ResolvedLibrary.resolveMessage(libraryClass, messageName);
    }

}
