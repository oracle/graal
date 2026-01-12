/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.vmaccess;

import com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoResolvedArrayType;
import com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoResolvedInstanceType;
import com.oracle.truffle.espresso.jvmci.meta.EspressoResolvedJavaType;
import com.oracle.truffle.espresso.jvmci.meta.EspressoResolvedObjectType;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaConstant;

final class EspressoExternalResolvedArrayType extends AbstractEspressoResolvedArrayType {
    private final EspressoExternalVMAccess access;

    EspressoExternalResolvedArrayType(EspressoResolvedJavaType elementalType, int dimensions, EspressoExternalVMAccess access) {
        super(elementalType, dimensions);
        this.access = access;
    }

    EspressoExternalResolvedArrayType(EspressoResolvedJavaType elementalType, int dimensions, EspressoResolvedJavaType componentType, EspressoExternalVMAccess access) {
        super(elementalType, dimensions, componentType);
        this.access = access;
    }

    @Override
    protected AbstractEspressoResolvedArrayType withNewElementalType(EspressoResolvedJavaType resolvedElementalType) {
        return new EspressoExternalResolvedArrayType(resolvedElementalType, dimensions, access);
    }

    @Override
    protected AbstractEspressoResolvedArrayType getArrayComponentType0() {
        return new EspressoExternalResolvedArrayType(elementalType, dimensions - 1, access);
    }

    @Override
    protected AbstractEspressoResolvedArrayType getArrayClass0() {
        return new EspressoExternalResolvedArrayType(elementalType, dimensions + 1, this, access);
    }

    @Override
    protected Class<?> getMirror0() {
        throw JVMCIError.shouldNotReachHere("Mirrors cannot be accessed for external JVMCI");
    }

    @Override
    protected AbstractEspressoResolvedInstanceType getJavaLangObject() {
        return access.getJavaLangObject();
    }

    @Override
    protected AbstractEspressoResolvedInstanceType[] getArrayInterfaces() {
        return access.getArrayInterfaces();
    }

    @Override
    protected EspressoResolvedObjectType getObjectType(JavaConstant obj) {
        return ((EspressoExternalObjectConstant) obj).getType();
    }
}
