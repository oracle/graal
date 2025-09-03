/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.meta;

import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class HotSpotMetaAccessExtensionProvider implements MetaAccessExtensionProvider {
    private final ConstantReflectionProvider constantReflection;

    public HotSpotMetaAccessExtensionProvider(ConstantReflectionProvider constantReflection) {
        this.constantReflection = constantReflection;
    }

    @Override
    public JavaKind getStorageKind(JavaType type) {
        return type.getJavaKind();
    }

    @Override
    public boolean canConstantFoldDynamicAllocation(ResolvedJavaType type) {
        /*
         * The HotSpot lowering of DynamicNewInstanceNode includes an explicit is-initialized check
         * and deoptimizes, but the lowering of NewInstanceNode does not. So we must not constant
         * fold a non-initialized instance allocation.
         */
        return type.isArray() || type.isInitialized();
    }

    @Override
    public boolean isGuaranteedSafepoint(ResolvedJavaMethod method, boolean isDirect) {
        return true;
    }

    @Override
    public boolean canVirtualize(ResolvedJavaType instanceType) {
        return true;
    }

    @Override
    public ResolvedJavaField getStaticFieldForAccess(JavaConstant base, long offset, JavaKind accessKind) {
        if (accessKind.getSlotCount() <= 0) {
            throw new IllegalArgumentException("Unexpected access kind: " + accessKind);
        }
        if (!(base instanceof HotSpotObjectConstant objectConstant)) {
            return null;
        }
        ResolvedJavaType type = constantReflection.asJavaType(base);
        // check that it's indeed a j.l.Class when we get a result since constant reflection will
        // also return a type if the constant wraps a ResolvedJavaType
        if (type == null || !objectConstant.getType().getName().equals("Ljava/lang/Class;")) {
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
