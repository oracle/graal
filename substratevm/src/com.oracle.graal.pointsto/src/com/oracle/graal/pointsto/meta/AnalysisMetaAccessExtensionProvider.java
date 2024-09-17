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

package com.oracle.graal.pointsto.meta;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.TypedConstant;
import com.oracle.graal.pointsto.util.GraalAccess;

import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class AnalysisMetaAccessExtensionProvider implements MetaAccessExtensionProvider {
    private final AnalysisUniverse aUniverse;

    public AnalysisMetaAccessExtensionProvider(AnalysisUniverse aUniverse) {
        this.aUniverse = aUniverse;
    }

    @Override
    public JavaKind getStorageKind(JavaType type) {
        return ((AnalysisType) type).getStorageKind();
    }

    @Override
    public boolean canConstantFoldDynamicAllocation(ResolvedJavaType t) {
        AnalysisType type = (AnalysisType) t;
        if (aUniverse.sealed()) {
            /* Static analysis has finished, e.g., we are applying static analysis results. */
            return type.isInstantiated();
        } else {
            /* Static analysis is still running, so it will mark the type as instantiated. */
            return true;
        }
    }

    @Override
    public boolean isGuaranteedSafepoint(ResolvedJavaMethod method, boolean isDirect) {
        throw GraalError.unimplementedOverride(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public boolean canVirtualize(ResolvedJavaType instanceType) {
        return true;
    }

    @Override
    public ResolvedJavaField getStaticFieldForAccess(JavaConstant base, long offset, JavaKind accessKind) {
        JavaConstant hostedObject;
        if (base instanceof ImageHeapConstant imageHeapConstant) {
            hostedObject = imageHeapConstant.getHostedObject();
            if (hostedObject == null) {
                return null;
            }
            assert !(hostedObject instanceof ImageHeapConstant);
        } else if (!(base instanceof TypedConstant)) {
            /*
             * Ideally, this path should be unreachable, i.e., we should only see TypedConstant. But
             * the image heap scanning during static analysis is not implemented cleanly enough and
             * invokes this method both with image heap constants and original HotSpot object
             * constants. See AnalysisMetaAccess.lookupJavaType.
             */
            hostedObject = base;
        } else {
            return null;
        }
        MetaAccessExtensionProvider original = GraalAccess.getOriginalProviders().getMetaAccessExtensionProvider();
        return aUniverse.lookup(original.getStaticFieldForAccess(hostedObject, offset, accessKind));
    }
}
