/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.aarch64;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.CPUFeatureAccess;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Architecture;

@AutomaticFeature
@Platforms(Platform.AARCH64.class)
class AArch64CPUFeatureAccessFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(CPUFeatureAccess.class, new AArch64CPUFeatureAccess());
    }
}

public class AArch64CPUFeatureAccess implements CPUFeatureAccess {

    /**
     * We include all flags which currently impact AArch64 performance.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static EnumSet<AArch64.Flag> enabledAArch64Flags() {
        return EnumSet.of(AArch64.Flag.UseLSE);
    }

    /**
     * Determines whether a given JVMCI AArch64.CPUFeature is present on the current hardware.
     * Because the CPUFeatures available vary across different JDK versions, the features are
     * queried via their name, as opposed to the actual enum.
     */
    private static boolean isFeaturePresent(String featureName, AArch64LibCHelper.CPUFeatures cpuFeatures, List<String> unknownFeatures) {
        switch (featureName) {
            case "FP":
                return cpuFeatures.fFP();
            case "ASIMD":
                return cpuFeatures.fASIMD();
            case "EVTSTRM":
                return cpuFeatures.fEVTSTRM();
            case "AES":
                return cpuFeatures.fAES();
            case "PMULL":
                return cpuFeatures.fPMULL();
            case "SHA1":
                return cpuFeatures.fSHA1();
            case "SHA2":
                return cpuFeatures.fSHA2();
            case "CRC32":
                return cpuFeatures.fCRC32();
            case "LSE":
                return cpuFeatures.fLSE();
            case "DCPOP":
                return cpuFeatures.fDCPOP();
            case "SHA3":
                return cpuFeatures.fSHA3();
            case "SHA512":
                return cpuFeatures.fSHA512();
            case "SVE":
                return cpuFeatures.fSVE();
            case "SVE2":
                return cpuFeatures.fSVE2();
            case "STXR_PREFETCH":
                return cpuFeatures.fSTXRPREFETCH();
            case "A53MAC":
                return cpuFeatures.fA53MAC();
            case "DMB_ATOMICS":
                return cpuFeatures.fDMBATOMICS();
            default:
                unknownFeatures.add(featureName);
                return false;
        }
    }

    @Platforms(Platform.AARCH64.class)
    public static EnumSet<AArch64.CPUFeature> determineHostCPUFeatures() {
        EnumSet<AArch64.CPUFeature> features = EnumSet.noneOf(AArch64.CPUFeature.class);

        AArch64LibCHelper.CPUFeatures cpuFeatures = StackValue.get(AArch64LibCHelper.CPUFeatures.class);

        UnmanagedMemoryUtil.fill((Pointer) cpuFeatures, SizeOf.unsigned(AArch64LibCHelper.CPUFeatures.class), (byte) 0);

        AArch64LibCHelper.determineCPUFeatures(cpuFeatures);

        ArrayList<String> unknownFeatures = new ArrayList<>();
        for (AArch64.CPUFeature feature : AArch64.CPUFeature.values()) {
            if (isFeaturePresent(feature.name(), cpuFeatures, unknownFeatures)) {
                features.add(feature);
            }
        }
        if (!unknownFeatures.isEmpty()) {
            throw VMError.shouldNotReachHere("Native image does not support the following JVMCI CPU features: " + unknownFeatures);
        }

        return features;
    }

    @Override
    public void verifyHostSupportsArchitecture(Architecture imageArchitecture) {
        AArch64 architecture = (AArch64) imageArchitecture;
        EnumSet<AArch64.CPUFeature> features = determineHostCPUFeatures();

        if (!features.containsAll(architecture.getFeatures())) {
            List<AArch64.CPUFeature> missingFeatures = new ArrayList<>();
            for (AArch64.CPUFeature feature : architecture.getFeatures()) {
                if (!features.contains(feature)) {
                    missingFeatures.add(feature);
                }
            }
            throw VMError.shouldNotReachHere("Current target does not support the following CPU features that are required by the image: " + missingFeatures);
        }

    }

    @Override
    public void enableFeatures(Architecture runtimeArchitecture) {
        AArch64 architecture = (AArch64) runtimeArchitecture;
        EnumSet<AArch64.CPUFeature> features = determineHostCPUFeatures();
        architecture.getFeatures().addAll(features);
    }
}
