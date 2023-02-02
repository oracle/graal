/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.core;

import java.util.function.Consumer;
import java.util.function.Function;

import com.oracle.svm.core.util.VMError;

/**
 * Encode the result of loading a class. It contains either a type object, if the loading is
 * succesful, or a Throwable object encoding the reason why the loading failed.
 */
public final class TypeResult<T> {

    public static <V> TypeResult<V> forType(String name, V type) {
        return new TypeResult<>(name, type);
    }

    public static TypeResult<Class<?>> forClass(Class<?> clazz) {
        return new TypeResult<>(clazz.getName(), clazz);
    }

    public static <T> TypeResult<T> forException(String name, Throwable exception) {
        return new TypeResult<>(name, null, exception);
    }

    private final String name;
    private final T type;
    private final Throwable exception;

    private TypeResult(String name, T type) {
        this(name, type, null);
    }

    private TypeResult(String name, T type, Throwable exception) {
        this.name = name;
        this.type = type;
        this.exception = exception;
    }

    public boolean isPresent() {
        return type != null;
    }

    public void ifPresent(Consumer<? super T> consumer) {
        if (type != null) {
            consumer.accept(type);
        }
    }

    public T get() {
        return type;
    }

    public <U> TypeResult<U> map(Function<T, U> f) {
        if (isPresent()) {
            return new TypeResult<>(name, f.apply(get()));
        } else {
            return TypeResult.forException(name, getException());
        }

    }

    public Throwable getException() {
        return exception;
    }

    public T getOrFail() {
        if (type != null) {
            return type;
        } else {
            throw VMError.shouldNotReachHere("Type " + name + " not found", exception);
        }
    }
}
