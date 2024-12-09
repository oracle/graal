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

import java.lang.reflect.Method;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.code.ImageCodeInfo;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.meta.KnownOffsets;
import com.oracle.svm.core.layeredimagesingleton.FeatureSingleton;
import com.oracle.svm.core.layeredimagesingleton.UnsavedSingleton;
import com.oracle.svm.core.stack.JavaFrameAnchor;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.hosted.FeatureImpl.BeforeCompilationAccessImpl;
import com.oracle.svm.hosted.c.info.AccessorInfo;
import com.oracle.svm.hosted.c.info.StructFieldInfo;
import com.oracle.svm.hosted.config.DynamicHubLayout;
import com.oracle.svm.hosted.thread.VMThreadFeature;
import com.oracle.svm.util.ReflectionUtil;

@AutomaticallyRegisteredFeature
@Platforms(InternalPlatform.NATIVE_ONLY.class)
public final class KnownOffsetsFeature implements InternalFeature, FeatureSingleton, UnsavedSingleton {

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(VMThreadFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(KnownOffsets.class, new KnownOffsets());
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess a) {
        BeforeCompilationAccessImpl access = (BeforeCompilationAccessImpl) a;

        DynamicHubLayout dynamicHubLayout = DynamicHubLayout.singleton();
        int vtableBaseOffset = dynamicHubLayout.vTableOffset();
        int vtableEntrySize = dynamicHubLayout.vTableSlotSize;
        int typeIDSlotsOffset = SubstrateOptions.useClosedTypeWorldHubLayout() ? dynamicHubLayout.getClosedTypeWorldTypeCheckSlotsOffset() : -1;

        int javaFrameAnchorLastSPOffset = findStructOffset(access, JavaFrameAnchor.class, "getLastJavaSP");
        int javaFrameAnchorLastIPOffset = findStructOffset(access, JavaFrameAnchor.class, "getLastJavaIP");

        int vmThreadStatusOffset = ImageSingletons.lookup(VMThreadFeature.class).offsetOf(VMThreads.StatusSupport.statusTL);

        int imageCodeInfoCodeStartOffset = findFieldOffset(access, ImageCodeInfo.class, "codeStart");

        KnownOffsets.singleton().setLazyState(vtableBaseOffset, vtableEntrySize, typeIDSlotsOffset,
                        javaFrameAnchorLastSPOffset, javaFrameAnchorLastIPOffset, vmThreadStatusOffset, imageCodeInfoCodeStartOffset);
    }

    private static int findFieldOffset(BeforeCompilationAccessImpl access, Class<?> clazz, String fieldName) {
        return access.getMetaAccess().lookupJavaField(ReflectionUtil.lookupField(clazz, fieldName)).getLocation();
    }

    private static int findStructOffset(BeforeCompilationAccessImpl access, Class<?> clazz, String accessorName) {
        Method method = ReflectionUtil.lookupPublicMethodInClassHierarchy(clazz, accessorName);
        AccessorInfo accessorInfo = (AccessorInfo) access.getNativeLibraries().findElementInfo(access.getMetaAccess().lookupJavaMethod(method));
        StructFieldInfo structFieldInfo = (StructFieldInfo) accessorInfo.getParent();
        return structFieldInfo.getOffsetInfo().getProperty();
    }
}
