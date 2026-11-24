/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.meta;

import java.util.Arrays;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubCompanion;
import com.oracle.svm.guest.staging.core.graal.meta.DynamicHubOffsets;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.GuestAccess;
import com.oracle.svm.util.GuestAnnotationAccess;
import com.oracle.svm.util.JVMCIReflectionUtil;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

@AutomaticallyRegisteredFeature
public final class DynamicHubOffsetsFeature implements InternalFeature {

    private static final String[] SKIPPED_FIELDS = new String[]{
                    /* closed world only */
                    "typeCheckStart", "typeCheckRange", "typeCheckSlot", "closedTypeWorldTypeCheckSlots",

                    /* handled by KnownOffsets */
                    "vtable"
    };

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(DynamicHubOffsets.class, new DynamicHubOffsets());
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess a) {
        initializeOffsets(DynamicHubOffsets.singleton(), ((FeatureImpl.BeforeCompilationAccessImpl) a).getMetaAccess());
    }

    private static void initializeOffsets(DynamicHubOffsets dynamicHubOffsets, MetaAccessProvider metaAccess) {
        GuestAccess guest = GuestAccess.get();
        JavaConstant offsets = guest.getSnippetReflection().forObject(dynamicHubOffsets);
        ResolvedJavaType offsetsType = guest.lookupType(DynamicHubOffsets.class);

        for (ResolvedJavaField field : metaAccess.lookupJavaType(DynamicHub.class).getInstanceFields(true)) {
            if (Arrays.stream(SKIPPED_FIELDS).anyMatch(field.getName()::equals)) {
                continue;
            }
            if (GuestAnnotationAccess.isAnnotationPresent(field, InjectAccessors.class)) {
                continue;
            }

            JVMCIReflectionUtil.writeField(offsetsType, field.getName() + "Offset", offsets, JavaConstant.forInt(field.getOffset()));
        }

        // Ensure the expected fields exist
        ResolvedJavaType hubType = guest.lookupType(DynamicHub.class);
        for (ResolvedJavaField field : JVMCIReflectionUtil.getAllFields(offsetsType)) {
            String name = field.getName();
            if (field.isInternal() || !name.endsWith("Offset")) {
                continue;
            }
            ResolvedJavaField hubField = JVMCIReflectionUtil.getUniqueDeclaredField(hubType, name.substring(0, name.length() - "Offset".length()));
            VMError.guarantee(!hubField.isInternal(), "Expected a declared DynamicHub field for offset field %s", name);
        }

        initializeCompanionOffsets(offsets, offsetsType, metaAccess);
    }

    private static void initializeCompanionOffsets(JavaConstant offsets, ResolvedJavaType offsetsType, MetaAccessProvider metaAccess) {
        ResolvedJavaField additionalFlagsField = JVMCIReflectionUtil.getUniqueDeclaredField(metaAccess.lookupJavaType(DynamicHubCompanion.class), "additionalFlags");
        JavaConstant companionOffsets = JVMCIReflectionUtil.readInstanceField(offsets, offsetsType, "companionOffsets");
        ResolvedJavaType companionOffsetsType = GuestAccess.get().getProviders().getMetaAccess().lookupJavaType(companionOffsets);
        JVMCIReflectionUtil.writeField(companionOffsetsType, "additionalFlagsOffset", companionOffsets, JavaConstant.forInt(additionalFlagsField.getOffset()));
    }
}
