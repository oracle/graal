/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A predicate that returns {@code true} iff a specified method exists.
 */
// Checkstyle: stop
public class MethodPredicate implements BooleanSupplier {

    private final Class<?> declaringClass;
    private final String innerClassName;
    private final String methodName;
    private final Class<?>[] parameterTypes;

    public MethodPredicate(Class<?> declaringClass, String innerClassName, String methodName, Class<?>... parameterTypes) {
        this.declaringClass = declaringClass;
        this.innerClassName = innerClassName;
        this.methodName = methodName;
        this.parameterTypes = parameterTypes;
    }

    @Override
    public boolean getAsBoolean() {
        Optional<Class<?>> cls = innerClassName != null ?
                        Stream.of(declaringClass.getDeclaredClasses()).filter(c -> c.getSimpleName().equals(innerClassName)).findFirst() :
                        Optional.of(declaringClass);
        if (cls.isPresent()) {
            return Stream.of(cls.get().getDeclaredMethods()).filter(m -> m.getName().equals(methodName) && Arrays.equals(m.getParameters(), parameterTypes)).findFirst().isPresent();
        }
        return false;
    }
}
