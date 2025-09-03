/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.graal;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.Objects;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.debug.GraalError;
import com.oracle.truffle.espresso.jvmci.meta.EspressoConstantReflectionProvider;
import com.oracle.truffle.espresso.jvmci.meta.EspressoObjectConstant;
import com.oracle.truffle.espresso.jvmci.meta.EspressoResolvedJavaField;
import com.oracle.truffle.espresso.jvmci.meta.EspressoResolvedJavaMethod;
import com.oracle.truffle.espresso.jvmci.meta.EspressoResolvedJavaType;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class EspressoSnippetReflectionProvider implements SnippetReflectionProvider {
    private final EspressoConstantReflectionProvider constantReflectionProvider;

    public EspressoSnippetReflectionProvider(EspressoConstantReflectionProvider constantReflectionProvider) {
        this.constantReflectionProvider = constantReflectionProvider;
    }

    @Override
    public JavaConstant forObject(Object object) {
        return constantReflectionProvider.forObject(object);
    }

    @Override
    public <T> T asObject(Class<T> type, JavaConstant constant) {
        if (constant.isNull()) {
            return null;
        }
        if (constant instanceof EspressoObjectConstant) {
            return constantReflectionProvider.asObject(type, (EspressoObjectConstant) constant);
        }
        return null;
    }

    @Override
    public <T> T getInjectedNodeIntrinsicParameter(Class<T> type) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public Class<?> originalClass(ResolvedJavaType type) {
        Objects.requireNonNull(type);
        if (!(type instanceof EspressoResolvedJavaType)) {
            throw new IllegalArgumentException(type.getClass().getName());
        }
        return ((EspressoResolvedJavaType) type).getMirror();
    }

    @Override
    public Executable originalMethod(ResolvedJavaMethod method) {
        Objects.requireNonNull(method);
        if (!(method instanceof EspressoResolvedJavaMethod)) {
            throw new IllegalArgumentException(method.getClass().getName());
        }
        return ((EspressoResolvedJavaMethod) method).getMirror();
    }

    @Override
    public Field originalField(ResolvedJavaField field) {
        Objects.requireNonNull(field);
        if (!(field instanceof EspressoResolvedJavaField)) {
            throw new IllegalArgumentException(field.getClass().getName());
        }
        return ((EspressoResolvedJavaField) field).getMirror();
    }
}
