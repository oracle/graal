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
package com.oracle.truffle.espresso.jvmci.meta;

import static com.oracle.truffle.espresso.jvmci.meta.EspressoResolvedArrayType.findArrayClass;

import java.lang.annotation.Annotation;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public abstract class EspressoResolvedJavaType implements ResolvedJavaType {
    static final Annotation[] NO_ANNOTATIONS = {};
    protected static final EspressoResolvedJavaField[] NO_FIELDS = new EspressoResolvedJavaField[0];
    protected static final ResolvedJavaType[] NO_TYPES = new ResolvedJavaType[0];
    protected static final ResolvedJavaMethod[] NO_METHODS = new ResolvedJavaMethod[0];
    protected EspressoResolvedArrayType arrayType;

    @Override
    public EspressoResolvedArrayType getArrayClass() {
        if (arrayType == null) {
            arrayType = new EspressoResolvedArrayType(this, 1, this, findArrayClass(getMirror(), 1));
        }
        return arrayType;
    }

    @Override
    public abstract EspressoResolvedJavaType resolve(ResolvedJavaType accessingClass);

    public abstract Class<?> getMirror();

    public abstract boolean isDefinitelyResolvedWithRespectTo(ResolvedJavaType accessingClass);

    @Override
    public final ResolvedJavaMethod[] getDeclaredMethods() {
        return getDeclaredMethods(true);
    }

    @Override
    public abstract ResolvedJavaMethod[] getDeclaredMethods(boolean forceLink);

    @Override
    public final ResolvedJavaMethod[] getDeclaredConstructors() {
        return getDeclaredConstructors(true);
    }

    @Override
    public abstract ResolvedJavaMethod[] getDeclaredConstructors(boolean forceLink);

    @Override
    public String toString() {
        return getClass().getSimpleName() + "<" + getName() + ">";
    }
}
