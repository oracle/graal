/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.imagelayer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.heap.ImageHeapObjectArray;
import com.oracle.graal.pointsto.heap.ImageHeapRelocatableConstant;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.nodes.SubstrateCompressionNode;
import com.oracle.svm.core.graal.nodes.SubstrateNarrowOopStamp;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.hosted.FeatureImpl;

import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.memory.FloatingReadNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * We implement support for loading {@link ImageHeapRelocatableConstant}s directly within graphs via
 * storing an array in the image heap whose elements consist of the
 * {@link ImageHeapRelocatableConstant}s referenced from within graphs. Within the graphs themselves
 * the accesses are converted to loads from {@link #finalizedImageHeapRelocatableConstantsArray}.
 */
@AutomaticallyRegisteredFeature
public class ImageHeapRelocatableConstantFeature extends ImageHeapRelocatableConstantSupport implements InternalFeature {

    final Map<ImageHeapRelocatableConstant, Integer> constantToInfoMap = new ConcurrentHashMap<>();
    final AtomicInteger nextIndex = new AtomicInteger();
    JavaConstant finalizedImageHeapRelocatableConstantsArray;
    boolean sealed = false;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.buildingImageLayer();
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        ImageSingletons.add(ImageHeapRelocatableConstantSupport.class, this);
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        sealed = true;
        var config = (FeatureImpl.AfterAnalysisAccessImpl) access;

        final JavaConstant[] elements = new JavaConstant[constantToInfoMap.size()];
        constantToInfoMap.forEach((constantValue, index) -> {
            assert elements[index] == null : elements[index];
            elements[index] = constantValue;
        });

        finalizedImageHeapRelocatableConstantsArray = ImageHeapObjectArray.createUnbackedImageHeapArray(config.getMetaAccess().lookupJavaType(Object[].class), elements);
    }

    private JavaConstant getImageHeapRelocatableConstantsArray() {
        assert finalizedImageHeapRelocatableConstantsArray != null;
        return finalizedImageHeapRelocatableConstantsArray;
    }

    private int getAssignedIndex(ImageHeapRelocatableConstant constant) {
        assert constantToInfoMap.containsKey(constant);
        return constantToInfoMap.get(constant);
    }

    @Override
    void registerLoadableConstant(ImageHeapRelocatableConstant constant) {
        constantToInfoMap.computeIfAbsent(constant, (key) -> {
            assert !sealed;
            return nextIndex.getAndIncrement();
        });
    }

    @Override
    FloatingNode emitLoadConstant(StructuredGraph graph, MetaAccessProvider metaAccess, ImageHeapRelocatableConstant constant) {
        /*
         * We need to load the appropriate spot from the array storing all image heap relocatable
         * constants referenced from the text section.
         */
        long arrayOffset = ConfigurationValues.getObjectLayout().getArrayElementOffset(JavaKind.Object, getAssignedIndex(constant));
        var array = getImageHeapRelocatableConstantsArray();
        var address = new OffsetAddressNode(ConstantNode.forConstant(array, metaAccess, graph), ConstantNode.forLong(arrayOffset));

        var compressEncoding = ReferenceAccess.singleton().getCompressEncoding();
        var compressedStamp = SubstrateNarrowOopStamp.compressed((AbstractObjectStamp) StampFactory.forConstant(constant, metaAccess), compressEncoding);
        var read = new FloatingReadNode(address, NamedLocationIdentity.FINAL_LOCATION, graph.start(), compressedStamp);
        return SubstrateCompressionNode.uncompress(graph, graph.addOrUniqueWithInputs(read), compressEncoding);
    }
}
