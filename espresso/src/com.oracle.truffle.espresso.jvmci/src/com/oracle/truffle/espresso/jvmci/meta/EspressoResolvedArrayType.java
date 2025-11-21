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

import static com.oracle.truffle.espresso.jvmci.EspressoJVMCIRuntime.runtime;

import java.lang.reflect.Array;

import jdk.vm.ci.meta.JavaConstant;

final class EspressoResolvedArrayType extends AbstractEspressoResolvedArrayType {
    private final Class<?> mirror;

    private EspressoResolvedArrayType(EspressoResolvedJavaType elementalType, int dimensions, Class<?> mirror) {
        super(elementalType, dimensions);
        this.mirror = mirror;
    }

    EspressoResolvedArrayType(EspressoResolvedJavaType elementalType, int dimensions, EspressoResolvedJavaType componentType, Class<?> mirror) {
        super(elementalType, dimensions, componentType);
        this.mirror = mirror;
    }

    @Override
    protected Class<?> getMirror0() {
        return mirror;
    }

    @Override
    protected AbstractEspressoResolvedInstanceType getJavaLangObject() {
        return runtime().getJavaLangObject();
    }

    @Override
    protected AbstractEspressoResolvedInstanceType[] getArrayInterfaces() {
        return runtime().getArrayInterfaces();
    }

    @Override
    protected EspressoResolvedArrayType getArrayComponentType0() {
        return new EspressoResolvedArrayType(elementalType, dimensions - 1, mirror.getComponentType());
    }

    @Override
    protected EspressoResolvedArrayType getArrayClass0() {
        return new EspressoResolvedArrayType(elementalType, dimensions + 1, this, findArrayClass(mirror, 1));
    }

    @Override
    protected EspressoResolvedArrayType withNewElementalType(EspressoResolvedJavaType resolvedElementalType) {
        return new EspressoResolvedArrayType(resolvedElementalType, dimensions, findArrayClass(resolvedElementalType.getMirror(), dimensions));
    }

    static Class<?> findArrayClass(Class<?> base, int dimensionsDelta) {
        return Array.newInstance(base, new int[dimensionsDelta]).getClass();
    }

    @Override
    protected EspressoResolvedObjectType getObjectType(JavaConstant obj) {
        return ((EspressoObjectConstant) obj).getType();
    }
}
