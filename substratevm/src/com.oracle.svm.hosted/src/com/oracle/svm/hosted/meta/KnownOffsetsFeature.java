/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.code.ImageCodeInfo;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.meta.KnownOffsets;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.stack.JavaFrameAnchor;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.hosted.FeatureImpl.BeforeCompilationAccessImpl;
import com.oracle.svm.hosted.c.info.AccessorInfo;
import com.oracle.svm.hosted.c.info.StructFieldInfo;
import com.oracle.svm.hosted.config.HybridLayout;
import com.oracle.svm.hosted.thread.VMThreadMTFeature;
import com.oracle.svm.util.ReflectionUtil;

@AutomaticallyRegisteredFeature
public final class KnownOffsetsFeature implements InternalFeature {

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        if (SubstrateOptions.MultiThreaded.getValue()) {
            return Arrays.asList(VMThreadMTFeature.class);
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(KnownOffsets.class, new KnownOffsets());
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess a) {
        BeforeCompilationAccessImpl access = (BeforeCompilationAccessImpl) a;

        HybridLayout<DynamicHub> hubLayout = new HybridLayout<>(DynamicHub.class, ConfigurationValues.getObjectLayout(), access.getMetaAccess());
        int vtableBaseOffset = hubLayout.getArrayBaseOffset();
        int vtableEntrySize = ConfigurationValues.getObjectLayout().sizeInBytes(hubLayout.getArrayElementStorageKind());
        int typeIDSlotsOffset = HybridLayout.getTypeIDSlotsFieldOffset(ConfigurationValues.getObjectLayout());

        int componentHubOffset = findFieldOffset(access, DynamicHub.class, "componentType");

        int javaFrameAnchorLastSPOffset = findStructOffset(access, JavaFrameAnchor.class, "getLastJavaSP");
        int javaFrameAnchorLastIPOffset = findStructOffset(access, JavaFrameAnchor.class, "getLastJavaIP");

        int vmThreadStatusOffset = -1;
        if (SubstrateOptions.MultiThreaded.getValue()) {
            vmThreadStatusOffset = ImageSingletons.lookup(VMThreadMTFeature.class).offsetOf(VMThreads.StatusSupport.statusTL);
        }

        int imageCodeInfoCodeStartOffset = findFieldOffset(access, ImageCodeInfo.class, "codeStart");

        KnownOffsets.singleton().setLazyState(vtableBaseOffset, vtableEntrySize, typeIDSlotsOffset, componentHubOffset,
                        javaFrameAnchorLastSPOffset, javaFrameAnchorLastIPOffset, vmThreadStatusOffset, imageCodeInfoCodeStartOffset);
    }

    private static int findFieldOffset(BeforeCompilationAccessImpl access, Class<?> clazz, String fieldName) {
        return access.getMetaAccess().lookupJavaField(ReflectionUtil.lookupField(clazz, fieldName)).getLocation();
    }

    private static int findStructOffset(BeforeCompilationAccessImpl access, Class<?> clazz, String accessorName) {
        AccessorInfo accessorInfo = (AccessorInfo) access.getNativeLibraries().findElementInfo(access.getMetaAccess().lookupJavaMethod(ReflectionUtil.lookupMethod(clazz, accessorName)));
        StructFieldInfo structFieldInfo = (StructFieldInfo) accessorInfo.getParent();
        return structFieldInfo.getOffsetInfo().getProperty();
    }
}
