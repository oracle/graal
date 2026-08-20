/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.reflect.target;

import org.graalvm.nativeimage.hosted.FieldValueTransformerRequiresUniverse;

import com.oracle.svm.core.fieldvaluetransformer.JVMCIFieldValueTransformerWithAvailability;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.shared.BuildPhaseProvider;
import com.oracle.svm.shared.singletons.MultiLayeredImageSingleton;
import com.oracle.svm.util.GuestAccess;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Builder-side field transformers referenced by name from the guest reflection substitutions, so
 * their JVMCI dependencies remain outside guest staging.
 */
public final class ReflectionFieldValueComputers {
    private ReflectionFieldValueComputers() {
    }

    public static final class FieldDeletionReasonComputer implements JVMCIFieldValueTransformerWithAvailability, FieldValueTransformerRequiresUniverse {
        @Override
        public boolean isAvailable() {
            return BuildPhaseProvider.isHostedUniverseBuilt();
        }

        @Override
        public JavaConstant transform(JavaConstant receiver, JavaConstant originalValue) {
            ResolvedJavaField field = GuestAccess.get().asResolvedJavaField(receiver);
            String deletionReason = ReflectionSubstitutionSupport.singleton().getDeletionReason(field);
            return deletionReason == null ? JavaConstant.NULL_POINTER : GuestAccess.get().asGuestString(deletionReason);
        }
    }

    public static final class FieldOffsetComputer implements JVMCIFieldValueTransformerWithAvailability, FieldValueTransformerRequiresUniverse {
        @Override
        public boolean isAvailable() {
            return BuildPhaseProvider.isHostedUniverseBuilt();
        }

        @Override
        public JavaConstant transform(JavaConstant receiver, JavaConstant originalValue) {
            ResolvedJavaField field = GuestAccess.get().asResolvedJavaField(receiver);
            return JavaConstant.forInt(ReflectionSubstitutionSupport.singleton().getFieldOffset(field, true));
        }
    }

    public static final class LayerNumberComputer implements JVMCIFieldValueTransformerWithAvailability, FieldValueTransformerRequiresUniverse {
        @Override
        public boolean isAvailable() {
            return BuildPhaseProvider.isHostedUniverseBuilt();
        }

        @Override
        public JavaConstant transform(JavaConstant receiver, JavaConstant originalValue) {
            ResolvedJavaField field = GuestAccess.get().asResolvedJavaField(receiver);
            if (ImageLayerBuildingSupport.buildingImageLayer() && field.isStatic()) {
                return JavaConstant.forInt(ReflectionSubstitutionSupport.singleton().getInstalledLayerNumber(field));
            }
            return JavaConstant.forInt(MultiLayeredImageSingleton.UNUSED_LAYER_NUMBER);
        }
    }
}
