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
package com.oracle.svm.hosted;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.CPUFeatureAccess;
import com.oracle.svm.core.CPUFeatureAccessImpl;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.info.ElementInfo;
import com.oracle.svm.hosted.c.info.StructFieldInfo;
import com.oracle.svm.hosted.c.info.StructInfo;

public abstract class CPUFeatureAccessFeatureBase {
    /**
     * Initializes global data required at run time by {@link CPUFeatureAccess}. This method will
     * compute a translation table from the {@code CPUFeature} enum entries to the
     * {@code CPUFeatures} C struct, a (bitwise negated) {@code CPUFeatures} struct of the
     * {@linkplain CPUFeatureAccess#buildtimeCPUFeatures() required CPU features} that need to be
     * checked at runtime, and a predefined error message in case the features are not available.
     *
     * @param allCPUFeatures Array of all known enum entries representing the available CPU
     *            features.
     * @param cpuFeatureStructClass A {@link org.graalvm.nativeimage.c.struct.CStruct} annotated
     *            {@link Class} representing the CPU Features on the C level. For every entry in
     *            {@code allCPUFeatures}, there must be a {@code byte} field in this struct with
     *            {@link Enum#name()}, prefixed with {@code "f"}. Example: if there enum entry with
     *            name "AVX", this struct must contain a field of name "fAVX". Note that the struct
     *            must support all fields of all supported JVMCI versions. On the other hand, the
     *            enum might be missing some struct fields if built with older JVMCI versions.
     * @param access A {@link FeatureImpl.BeforeAnalysisAccessImpl} instance.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    protected <T extends Enum<T>> void initializeCPUFeatureAccessData(Enum<T>[] allCPUFeatures, EnumSet<?> buildtimeCPUFeatures, Class<?> cpuFeatureStructClass,
                    FeatureImpl.BeforeAnalysisAccessImpl access) {
        AnalysisMetaAccess metaAccess = access.getMetaAccess();
        NativeLibraries nativeLibraries = access.getNativeLibraries();
        AnalysisType invokeInterface = metaAccess.lookupJavaType(cpuFeatureStructClass);
        StructInfo cpuFeatureStructInfo = (StructInfo) nativeLibraries.findElementInfo(invokeInterface);

        // collect struct member offsets
        EconomicMap<String, Integer> fieldToOffset = EconomicMap.create();
        for (ElementInfo entry : cpuFeatureStructInfo.getChildren()) {
            if (entry instanceof StructFieldInfo) {
                var field = ((StructFieldInfo) entry);
                var fieldName = field.getName();
                int offset = field.getOffsetInfo().getProperty();
                int size = field.getSizeInfo().getProperty();
                GraalError.guarantee(size == Byte.BYTES, "Expected field %s to be byte sized, but was %s", field.getName(), size);
                GraalError.guarantee(fieldName.startsWith("f"), "Unexpected field name in %s: %s", cpuFeatureStructClass.getName(), fieldName);
                fieldToOffset.put(fieldName.substring(1), offset);
            }
        }

        // Initialize CPUFeatures struct.
        // Over-allocate to a multiple of 64 bit.
        int structSize = ((cpuFeatureStructInfo.getSizeInfo().getProperty() + Long.BYTES - 1) / Long.BYTES) * Long.BYTES;
        byte[] requiredFeaturesStruct = new byte[structSize];
        // Data is stored in bitwise negated form, thus initialize to all 1s.
        Arrays.fill(requiredFeaturesStruct, (byte) 0xff);
        ByteBuffer requiredFeaturesStructData = ByteBuffer.wrap(requiredFeaturesStruct);

        // Mapping from CPUFeature enum to CPUFeatures struct offset.
        int[] cpuFeatureEnumToStructOffset = new int[allCPUFeatures.length];
        // initialize to -1
        Arrays.fill(cpuFeatureEnumToStructOffset, -1);

        ArrayList<String> unknownFeatures = new ArrayList<>();
        for (var feature : allCPUFeatures) {
            GraalError.guarantee(fieldToOffset.containsKey(feature.name()), "No field f%s in %s", feature.name(), cpuFeatureStructClass.getName());
            int fieldOffset = fieldToOffset.get(feature.name());
            if (fieldOffset < 0) {
                unknownFeatures.add(feature.name());
            } else {
                // store struct offsets
                cpuFeatureEnumToStructOffset[feature.ordinal()] = fieldOffset;
                if (buildtimeCPUFeatures.contains(feature)) {
                    // set struct field to ~1 if feature is required
                    requiredFeaturesStructData.put(fieldOffset, (byte) ~1);
                }
            }
        }
        if (!unknownFeatures.isEmpty()) {
            throw VMError.shouldNotReachHere("Native image does not support the following JVMCI CPU features: " + unknownFeatures);
        }
        String errorMessage = "Current target does not support the following CPU features that are required by the image: " + buildtimeCPUFeatures.toString() + "\n\0";
        var cpuFeatureAccess = createCPUFeatureAccessSingleton(buildtimeCPUFeatures, cpuFeatureEnumToStructOffset, errorMessage.getBytes(StandardCharsets.UTF_8), requiredFeaturesStruct);
        ImageSingletons.add(CPUFeatureAccess.class, cpuFeatureAccess);
    }

    protected abstract CPUFeatureAccessImpl createCPUFeatureAccessSingleton(EnumSet<?> buildtimeCPUFeatures, int[] offsets, byte[] errorMessageBytes, byte[] buildtimeFeatureMaskBytes);
}
