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
package com.oracle.svm.core;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredPersistFlags;
import com.oracle.svm.core.traits.SingletonLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.core.traits.SingletonTrait;
import com.oracle.svm.core.traits.SingletonTraitKind;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.debug.GraalError;

public abstract class CPUFeatureAccessImpl implements CPUFeatureAccess {

    private final EnumSet<?> buildtimeCPUFeatures;

    /**
     * Stores the CPU features used for building the image as a {@code CPUFeatures} struct (see
     * {@code libchelper} project). The size of this global must be at least the size of the
     * {@code CPUFeatures} struct <em>and</em> a multiple of 64 bits. The 64 bits alignment allows
     * us to perform 64 bit comparisons without caring about remainders. The information is stored
     * in <em>bitwise negated</em> form to allow fast comparison at runtime. Potential padding
     * between struct member and after the struct to align to 64 bits must be set to all 1s.
     *
     * @see CPUFeatureAccess#verifyHostSupportsArchitectureEarly()
     * @see com.oracle.svm.core.amd64.AMD64LibCHelper.CPUFeatures
     * @see com.oracle.svm.core.aarch64.AArch64LibCHelper.CPUFeatures
     */
    protected static final CGlobalData<CCharPointer> BUILDTIME_CPU_FEATURE_MASK = CGlobalDataFactory
                    .createBytes(() -> ((CPUFeatureAccessImpl) ImageSingletons.lookup(CPUFeatureAccess.class)).buildtimeFeatureMask);

    /**
     * Error message printed when CPU features are missing. The string must be zero terminated.
     * 
     * @see CPUFeatureAccess#verifyHostSupportsArchitectureEarlyOrExit()
     */
    protected static final CGlobalData<CCharPointer> IMAGE_CPU_FEATURE_ERROR_MSG = CGlobalDataFactory
                    .createBytes(() -> ((CPUFeatureAccessImpl) ImageSingletons.lookup(CPUFeatureAccess.class)).cpuFeatureErrorMessage);
    /**
     * @see #IMAGE_CPU_FEATURE_ERROR_MSG
     */
    @Platforms(Platform.HOSTED_ONLY.class) private final byte[] cpuFeatureErrorMessage;

    /**
     * @see #BUILDTIME_CPU_FEATURE_MASK
     */
    @Platforms(Platform.HOSTED_ONLY.class) private final byte[] buildtimeFeatureMask;

    /**
     * Mapping from {@code <Architecture>.CPUFeature#ordinal()} to offsets of the corresponding
     * fields in {@code <Architecture>LibCHelper.CPUFeatures}.
     */
    private final int[] cpuFeatureEnumToStructOffsets;

    /**
     * Initializes the data structures to interface with the C part of {@link CPUFeatureAccess}
     * ({@code cpuid.c}).
     *
     * @param buildtimeCPUFeatures the CPU features enabled at build time
     * @param offsets see {@link #cpuFeatureEnumToStructOffsets}
     * @param errorMessageBytes see {@link #IMAGE_CPU_FEATURE_ERROR_MSG}
     * @param buildtimeFeatureMaskBytes see {@link #BUILDTIME_CPU_FEATURE_MASK}
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    protected CPUFeatureAccessImpl(EnumSet<?> buildtimeCPUFeatures, int[] offsets, byte[] errorMessageBytes, byte[] buildtimeFeatureMaskBytes) {
        GraalError.guarantee(errorMessageBytes[errorMessageBytes.length - 1] == 0, "error message not zero-terminated");
        GraalError.guarantee(buildtimeFeatureMaskBytes.length % Long.BYTES == 0, "build-time feature mask byte array not a multiple of 64 bits");
        this.cpuFeatureEnumToStructOffsets = offsets;
        this.cpuFeatureErrorMessage = errorMessageBytes;
        this.buildtimeFeatureMask = buildtimeFeatureMaskBytes;
        this.buildtimeCPUFeatures = EnumSet.copyOf(buildtimeCPUFeatures);
    }

    @Override
    public EnumSet<?> buildtimeCPUFeatures() {
        return buildtimeCPUFeatures;
    }

    /**
     * Determines whether a given JVMCI {@code <Architecture>.CPUFeature} is present on the current
     * hardware. Because the CPUFeatures available vary across different JDK versions, the features
     * are queried via their name, as opposed to the actual enum.
     */
    protected boolean isFeaturePresent(Enum<?> feature, Pointer cpuFeatures, List<String> unknownFeatures) {
        VMError.guarantee(cpuFeatureEnumToStructOffsets != null);
        int offset = cpuFeatureEnumToStructOffsets[feature.ordinal()];
        if (offset < 0) {
            unknownFeatures.add(feature.name());
            return false;
        }
        return cpuFeatures.readByte(offset) != 0;
    }

    public static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {
        private static final String BUILDTIME_CPU_FEATURES = "buildtimeCPUFeatures";
        private static final String CPU_FEATURE_ERROR_MESSAGE = "cpuFeatureErrorMessage";
        private static final String BUILDTIME_FEATURE_MASK = "buildtimeFeatureMask";
        private static final String CPU_FEATURE_ENUM_TO_STRUCT_OFFSETS = "cpuFeatureEnumToStructOffsets";

        @Override
        public SingletonTrait getLayeredCallbacksTrait() {
            var action = new SingletonLayeredCallbacks<CPUFeatureAccessImpl>() {
                @Override
                public LayeredPersistFlags doPersist(ImageSingletonWriter writer, CPUFeatureAccessImpl singleton) {
                    writer.writeStringList(BUILDTIME_CPU_FEATURES, getCPUFeaturesList(singleton));
                    writer.writeString(CPU_FEATURE_ERROR_MESSAGE, new String(singleton.cpuFeatureErrorMessage, StandardCharsets.ISO_8859_1));
                    writer.writeString(BUILDTIME_FEATURE_MASK, new String(singleton.buildtimeFeatureMask, StandardCharsets.ISO_8859_1));
                    writer.writeIntList(CPU_FEATURE_ENUM_TO_STRUCT_OFFSETS, Arrays.stream(singleton.cpuFeatureEnumToStructOffsets).boxed().toList());
                    return LayeredPersistFlags.CALLBACK_ON_REGISTRATION;
                }

                @Override
                public void onSingletonRegistration(ImageSingletonLoader loader, CPUFeatureAccessImpl singleton) {
                    List<String> previousLayerBuildtimeCPUFeatures = loader.readStringList(BUILDTIME_CPU_FEATURES);
                    List<String> currentLayerBuildtimeCPUFeatures = getCPUFeaturesList(singleton);
                    VMError.guarantee(previousLayerBuildtimeCPUFeatures.equals(currentLayerBuildtimeCPUFeatures),
                                    "The buildtime CPU Features should be consistent across layers. The previous layer CPU Features were %s, but the current layer are %s",
                                    previousLayerBuildtimeCPUFeatures, currentLayerBuildtimeCPUFeatures);

                    byte[] previousLayerCpuFeatureErrorMessage = loader.readString(CPU_FEATURE_ERROR_MESSAGE).getBytes(StandardCharsets.ISO_8859_1);
                    VMError.guarantee(Arrays.equals(singleton.cpuFeatureErrorMessage, previousLayerCpuFeatureErrorMessage), "Previous layer CPU Feature error message was %s, but current layer is %s",
                                    Arrays.toString(previousLayerCpuFeatureErrorMessage), Arrays.toString(singleton.cpuFeatureErrorMessage));

                    byte[] previousLayerBuildtimeFeatureMask = loader.readString(BUILDTIME_FEATURE_MASK).getBytes(StandardCharsets.ISO_8859_1);
                    VMError.guarantee(Arrays.equals(singleton.buildtimeFeatureMask, previousLayerBuildtimeFeatureMask), "Previous layer buildtime Feature mask was %s, but current layer is %s",
                                    Arrays.toString(previousLayerBuildtimeFeatureMask), Arrays.toString(singleton.buildtimeFeatureMask));

                    int[] previousLayerCpuFeatureEnumToStructOffsets = loader.readIntList(CPU_FEATURE_ENUM_TO_STRUCT_OFFSETS).stream().mapToInt(Integer::intValue).toArray();
                    VMError.guarantee(Arrays.equals(singleton.cpuFeatureEnumToStructOffsets, previousLayerCpuFeatureEnumToStructOffsets),
                                    "Previous CPU Feature enum to struct offsets was %s, but current layer is %s",
                                    Arrays.toString(previousLayerCpuFeatureEnumToStructOffsets), Arrays.toString(singleton.cpuFeatureEnumToStructOffsets));
                }
            };
            return new SingletonTrait(SingletonTraitKind.LAYERED_CALLBACKS, action);
        }

        private static List<String> getCPUFeaturesList(CPUFeatureAccessImpl cpuFeatureAccess) {
            return cpuFeatureAccess.buildtimeCPUFeatures.stream().map(Enum::toString).toList();
        }
    }
}
