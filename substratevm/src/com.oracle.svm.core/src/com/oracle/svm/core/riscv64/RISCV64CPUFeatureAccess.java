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
package com.oracle.svm.core.riscv64;

import java.util.ArrayList;
import java.util.EnumSet;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.CPUFeatureAccessImpl;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.riscv64.RISCV64;

public class RISCV64CPUFeatureAccess extends CPUFeatureAccessImpl {

    @Platforms(Platform.HOSTED_ONLY.class)
    public RISCV64CPUFeatureAccess(EnumSet<?> buildtimeCPUFeatures, int[] offsets, byte[] errorMessageBytes, byte[] buildtimeFeatureMaskBytes) {
        super(buildtimeCPUFeatures, offsets, errorMessageBytes, buildtimeFeatureMaskBytes);
    }

    /**
     * We include all flags which currently impact RISCV64 performance.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static EnumSet<RISCV64.Flag> enabledRISCV64Flags() {
        return EnumSet.of(RISCV64.Flag.UseConservativeFence, RISCV64.Flag.AvoidUnalignedAccesses);
    }

    @Override
    @Platforms(Platform.RISCV64.class)
    public EnumSet<RISCV64.CPUFeature> determineHostCPUFeatures() {
        EnumSet<RISCV64.CPUFeature> features = EnumSet.noneOf(RISCV64.CPUFeature.class);

        RISCV64LibCHelper.CPUFeatures cpuFeatures = UnsafeStackValue.get(RISCV64LibCHelper.CPUFeatures.class);

        UnmanagedMemoryUtil.fill((Pointer) cpuFeatures, SizeOf.unsigned(RISCV64LibCHelper.CPUFeatures.class), (byte) 0);

        RISCV64LibCHelper.determineCPUFeatures(cpuFeatures);

        ArrayList<String> unknownFeatures = new ArrayList<>();
        for (RISCV64.CPUFeature feature : RISCV64.CPUFeature.values()) {
            if (isFeaturePresent(feature, (Pointer) cpuFeatures, unknownFeatures)) {
                features.add(feature);
            }
        }
        if (!unknownFeatures.isEmpty()) {
            throw VMError.shouldNotReachHere("Native image does not support the following JVMCI CPU features: " + unknownFeatures);
        }

        return features;
    }

    @Uninterruptible(reason = "Thread state not set up yet.")
    @Override
    public int verifyHostSupportsArchitectureEarly() {
        return RISCV64LibCHelper.checkCPUFeatures(BUILDTIME_CPU_FEATURE_MASK.get());
    }

    @Uninterruptible(reason = "Thread state not set up yet.")
    @Override
    public void verifyHostSupportsArchitectureEarlyOrExit() {
        RISCV64LibCHelper.checkCPUFeaturesOrExit(BUILDTIME_CPU_FEATURE_MASK.get(), IMAGE_CPU_FEATURE_ERROR_MSG.get());
    }

    @Override
    public void enableFeatures(Architecture runtimeArchitecture) {
        RISCV64 architecture = (RISCV64) runtimeArchitecture;
        EnumSet<RISCV64.CPUFeature> features = determineHostCPUFeatures();
        architecture.getFeatures().addAll(features);
    }
}
