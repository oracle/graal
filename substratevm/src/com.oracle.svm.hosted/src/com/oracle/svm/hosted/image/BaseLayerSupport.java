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
package com.oracle.svm.hosted.image;

import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.PointerBase;

import com.oracle.objectfile.ObjectFile.ProgbitsSectionImpl;
import com.oracle.objectfile.ObjectFile.RelocationKind;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.code.BaseLayerMethodAccessor;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.hosted.c.CGlobalDataFeature;
import com.oracle.svm.hosted.image.BaseLayerSupport.BaseLayerMethodAccessorImpl;
import com.oracle.svm.hosted.meta.HostedMethod;

@AutomaticallyRegisteredFeature
class LoadBaseLayerFeature implements InternalFeature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.buildingExtensionLayer();
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        ImageSingletons.add(BaseLayerMethodAccessor.class, new BaseLayerMethodAccessorImpl());
    }
}

public class BaseLayerSupport {

    public static void markDynamicRelocationSites(ProgbitsSectionImpl rwDataSection) {
        if (ImageSingletons.contains(BaseLayerMethodAccessor.class)) {
            ((BaseLayerMethodAccessorImpl) ImageSingletons.lookup(BaseLayerMethodAccessor.class)).markDynamicRelocations(rwDataSection);
        }
    }

    public static class BaseLayerMethodAccessorImpl implements BaseLayerMethodAccessor {

        final ConcurrentHashMap<HostedMethod, CGlobalDataInfo> methodMap = new ConcurrentHashMap<>();

        @Override
        public CGlobalDataInfo getMethodData(SharedMethod method) {
            return methodMap.computeIfAbsent((HostedMethod) method, m -> {
                /*
                 * Create word to store the absolute method address at run time. We will register a
                 * relocation for the offset of this CGlobalData object which the dynamic linker
                 * will resolve at run time, when the base layer is loaded.
                 */
                String symbolName = "MethodEntry_" + NativeImage.localSymbolNameForMethod(method);
                CGlobalData<PointerBase> cGlobalData = CGlobalDataFactory.createWord(symbolName);
                return CGlobalDataFeature.singleton().registerAsAccessedOrGet(cGlobalData);
            });
        }

        void markDynamicRelocations(ProgbitsSectionImpl rwDataSection) {
            methodMap.forEach((method, info) -> {
                /* For each of the accessed base layer methods create a relocation record. */
                int pointerSize = ConfigurationValues.getTarget().wordSize;
                String symbolName = NativeImage.localSymbolNameForMethod(method);
                rwDataSection.markRelocationSite(info.getOffset(), RelocationKind.getDirect(pointerSize), symbolName, 0L);
            });
        }
    }
}
