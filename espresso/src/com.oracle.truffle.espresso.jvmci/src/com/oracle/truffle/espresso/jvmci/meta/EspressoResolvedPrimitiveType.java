/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.truffle.espresso.jvmci.meta.EspressoResolvedArrayType.findArrayClass;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;

public final class EspressoResolvedPrimitiveType extends AbstractEspressoResolvedPrimitiveType {
    private static final EspressoResolvedPrimitiveType[] primitives;
    static {
        EspressoResolvedPrimitiveType[] prims = new EspressoResolvedPrimitiveType[JavaKind.Void.getBasicType() + 1];
        prims[JavaKind.Boolean.getBasicType()] = new EspressoResolvedPrimitiveType(JavaKind.Boolean);
        prims[JavaKind.Byte.getBasicType()] = new EspressoResolvedPrimitiveType(JavaKind.Byte);
        prims[JavaKind.Short.getBasicType()] = new EspressoResolvedPrimitiveType(JavaKind.Short);
        prims[JavaKind.Char.getBasicType()] = new EspressoResolvedPrimitiveType(JavaKind.Char);
        prims[JavaKind.Int.getBasicType()] = new EspressoResolvedPrimitiveType(JavaKind.Int);
        prims[JavaKind.Float.getBasicType()] = new EspressoResolvedPrimitiveType(JavaKind.Float);
        prims[JavaKind.Long.getBasicType()] = new EspressoResolvedPrimitiveType(JavaKind.Long);
        prims[JavaKind.Double.getBasicType()] = new EspressoResolvedPrimitiveType(JavaKind.Double);
        prims[JavaKind.Void.getBasicType()] = new EspressoResolvedPrimitiveType(JavaKind.Void);
        primitives = prims;
    }

    private EspressoResolvedPrimitiveType(JavaKind kind) {
        super(kind);
    }

    static EspressoResolvedPrimitiveType forKind(JavaKind kind) {
        if (!kind.isPrimitive()) {
            throw new IllegalArgumentException("Not a primitive kind: " + kind);
        }
        return forBasicType(kind.getBasicType());
    }

    private static EspressoResolvedPrimitiveType forBasicType(int basicType) {
        if (primitives[basicType] == null) {
            throw new IllegalArgumentException("No primitive type for basic type " + basicType);
        }
        return primitives[basicType];
    }

    @Override
    public ResolvedJavaType lookupType(UnresolvedJavaType unresolvedJavaType, boolean resolve) {
        JavaType javaType = runtime().lookupType(unresolvedJavaType.getName(), runtime().getJavaLangObject(), resolve);
        if (javaType instanceof ResolvedJavaType resolved) {
            return resolved;
        }
        return null;
    }

    @Override
    protected AbstractEspressoResolvedArrayType getArrayClass0() {
        return new EspressoResolvedArrayType(this, 1, this, findArrayClass(getMirror(), 1));
    }
}
