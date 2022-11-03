/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.amd64;

import java.util.ArrayList;
import java.util.EnumSet;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.CPUFeatureAccessImpl;
import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.jdk.JVMCISubstitutions;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Architecture;

public class AMD64CPUFeatureAccess extends CPUFeatureAccessImpl {

    @Platforms(Platform.HOSTED_ONLY.class)
    public AMD64CPUFeatureAccess(EnumSet<?> buildtimeCPUFeatures, int[] offsets, byte[] errorMessageBytes, byte[] buildtimeFeatureMaskBytes) {
        super(buildtimeCPUFeatures, offsets, errorMessageBytes, buildtimeFeatureMaskBytes);
    }

    /**
     * We include all flags that enable AMD64 CPU instructions as we want best possible performance
     * for the code.
     *
     * @return All the flags that enable AMD64 CPU instructions.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static EnumSet<AMD64.Flag> allAMD64Flags() {
        return EnumSet.of(AMD64.Flag.UseCountLeadingZerosInstruction, AMD64.Flag.UseCountTrailingZerosInstruction);
    }

    @Override
    @Platforms(Platform.AMD64.class)
    public EnumSet<AMD64.CPUFeature> determineHostCPUFeatures() {
        EnumSet<AMD64.CPUFeature> features = EnumSet.noneOf(AMD64.CPUFeature.class);

        AMD64LibCHelper.CPUFeatures cpuFeatures = UnsafeStackValue.get(AMD64LibCHelper.CPUFeatures.class);

        UnmanagedMemoryUtil.fill((Pointer) cpuFeatures, SizeOf.unsigned(AMD64LibCHelper.CPUFeatures.class), (byte) 0);

        AMD64LibCHelper.determineCPUFeatures(cpuFeatures);

        ArrayList<String> unknownFeatures = new ArrayList<>();
        for (AMD64.CPUFeature feature : AMD64.CPUFeature.values()) {
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
        return AMD64LibCHelper.checkCPUFeatures(BUILDTIME_CPU_FEATURE_MASK.get());
    }

    @Uninterruptible(reason = "Thread state not set up yet.")
    @Override
    public void verifyHostSupportsArchitectureEarlyOrExit() {
        AMD64LibCHelper.checkCPUFeaturesOrExit(BUILDTIME_CPU_FEATURE_MASK.get(), IMAGE_CPU_FEATURE_ERROR_MSG.get());
    }

    /**
     * Returns {@code true} if the CPU feature set will be updated for JIT compilations. As a
     * consequence, the size of {@link AMD64#XMM} registers is different AOT vs JIT.
     *
     * Updating CPU features in only enabled if {@linkplain SubstrateOptions#SpawnIsolates isolates
     * are enabled}. There is not a fundamental problem. The only reason for this restriction is
     * that with isolates we have a {@linkplain ReservedRegisters#getHeapBaseRegister() heap base
     * register} which makes dynamic CPU feature checks simple because they do not require an
     * intermediate register for testing the
     * {@linkplain com.oracle.svm.core.cpufeature.RuntimeCPUFeatureCheckImpl cpu feature mask}.
     */
    public static boolean canUpdateCPUFeatures() {
        return SubstrateOptions.SpawnIsolates.getValue();
    }

    @Override
    public void enableFeatures(Architecture runtimeArchitecture) {
        if (!canUpdateCPUFeatures()) {
            return;
        }
        // update cpu features
        AMD64 architecture = (AMD64) runtimeArchitecture;
        EnumSet<AMD64.CPUFeature> features = determineHostCPUFeatures();
        architecture.getFeatures().addAll(features);

        // update largest storable kind
        AMD64Kind largestStorableKind = (new AMD64(features, architecture.getFlags())).getLargestStorableKind(AMD64.XMM);
        JVMCISubstitutions.updateLargestStorableKind(architecture, largestStorableKind);
    }
}
