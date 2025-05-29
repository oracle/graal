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

import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.debug.GraalError;
import com.oracle.truffle.espresso.jvmci.meta.EspressoConstantReflectionProvider;
import com.oracle.truffle.espresso.jvmci.meta.EspressoObjectConstant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class EspressoMetaAccessExtensionProvider implements MetaAccessExtensionProvider {
    private final EspressoConstantReflectionProvider constantReflection;

    public EspressoMetaAccessExtensionProvider(EspressoConstantReflectionProvider constantReflection) {
        this.constantReflection = constantReflection;
    }

    @Override
    public JavaKind getStorageKind(JavaType type) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public boolean canConstantFoldDynamicAllocation(ResolvedJavaType type) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public boolean isGuaranteedSafepoint(ResolvedJavaMethod method, boolean isDirect) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public boolean canVirtualize(ResolvedJavaType instanceType) {
        throw GraalError.unimplementedOverride();
    }

    @Override
    public ResolvedJavaField getStaticFieldForAccess(JavaConstant base, long offset, JavaKind accessKind) {
        if (accessKind.getSlotCount() <= 0) {
            throw new IllegalArgumentException("Unexpected access kind: " + accessKind);
        }
        if (!(base instanceof EspressoObjectConstant)) {
            return null;
        }
        ResolvedJavaType type = constantReflection.getTypeForStaticBase((EspressoObjectConstant) base);
        if (type == null) {
            return null;
        }
        for (ResolvedJavaField field : type.getStaticFields()) {
            if (field.getOffset() == offset && accessKind == field.getJavaKind()) {
                return field;
            }
        }
        return null;
    }
}
