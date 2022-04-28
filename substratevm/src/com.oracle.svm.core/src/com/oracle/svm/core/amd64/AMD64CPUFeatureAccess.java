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
import java.util.List;

import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.SubstrateOptions;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.CPUFeatureAccess;
import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.jdk.JVMCISubstitutions;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Architecture;

public class AMD64CPUFeatureAccess implements CPUFeatureAccess {

    private final EnumSet<?> buildtimeCPUFeatures;

    public AMD64CPUFeatureAccess() {
        var targetDescription = ImageSingletons.lookup(SubstrateTargetDescription.class);
        var arch = (AMD64) targetDescription.arch;
        buildtimeCPUFeatures = EnumSet.copyOf(arch.getFeatures());
    }

    @Override
    public EnumSet<?> buildtimeCPUFeatures() {
        return buildtimeCPUFeatures;
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

    /**
     * Determines whether a given JVMCI AMD64.CPUFeature is present on the current hardware. Because
     * the CPUFeatures available vary across different JDK versions, the features are queried via
     * their name, as opposed to the actual enum.
     */
    private static boolean isFeaturePresent(String featureName, AMD64LibCHelper.CPUFeatures cpuFeatures, List<String> unknownFeatures) {
        switch (featureName) {
            case "CX8":
                return cpuFeatures.fCX8();
            case "CMOV":
                return cpuFeatures.fCMOV();
            case "FXSR":
                return cpuFeatures.fFXSR();
            case "HT":
                return cpuFeatures.fHT();
            case "MMX":
                return cpuFeatures.fMMX();
            case "AMD_3DNOW_PREFETCH":
                return cpuFeatures.fAMD_3DNOW_PREFETCH();
            case "SSE":
                return cpuFeatures.fSSE();
            case "SSE2":
                return cpuFeatures.fSSE2();
            case "SSE3":
                return cpuFeatures.fSSE3();
            case "SSSE3":
                return cpuFeatures.fSSSE3();
            case "SSE4A":
                return cpuFeatures.fSSE4A();
            case "SSE4_1":
                return cpuFeatures.fSSE4_1();
            case "SSE4_2":
                return cpuFeatures.fSSE4_2();
            case "POPCNT":
                return cpuFeatures.fPOPCNT();
            case "LZCNT":
                return cpuFeatures.fLZCNT();
            case "TSC":
                return cpuFeatures.fTSC();
            case "TSCINV":
                return cpuFeatures.fTSCINV();
            case "TSCINV_BIT":
                return cpuFeatures.fTSCINV_BIT();
            case "AVX":
                return cpuFeatures.fAVX();
            case "AVX2":
                return cpuFeatures.fAVX2();
            case "AES":
                return cpuFeatures.fAES();
            case "ERMS":
                return cpuFeatures.fERMS();
            case "CLMUL":
                return cpuFeatures.fCLMUL();
            case "BMI1":
                return cpuFeatures.fBMI1();
            case "BMI2":
                return cpuFeatures.fBMI2();
            case "RTM":
                return cpuFeatures.fRTM();
            case "ADX":
                return cpuFeatures.fADX();
            case "AVX512F":
                return cpuFeatures.fAVX512F();
            case "AVX512DQ":
                return cpuFeatures.fAVX512DQ();
            case "AVX512PF":
                return cpuFeatures.fAVX512PF();
            case "AVX512ER":
                return cpuFeatures.fAVX512ER();
            case "AVX512CD":
                return cpuFeatures.fAVX512CD();
            case "AVX512BW":
                return cpuFeatures.fAVX512BW();
            case "AVX512VL":
                return cpuFeatures.fAVX512VL();
            case "SHA":
                return cpuFeatures.fSHA();
            case "FMA":
                return cpuFeatures.fFMA();
            case "VZEROUPPER":
                return cpuFeatures.fVZEROUPPER();
            case "AVX512_VPOPCNTDQ":
                return cpuFeatures.fAVX512_VPOPCNTDQ();
            case "AVX512_VPCLMULQDQ":
                return cpuFeatures.fAVX512_VPCLMULQDQ();
            case "AVX512_VAES":
                return cpuFeatures.fAVX512_VAES();
            case "AVX512_VNNI":
                return cpuFeatures.fAVX512_VNNI();
            case "FLUSH":
                return cpuFeatures.fFLUSH();
            case "FLUSHOPT":
                return cpuFeatures.fFLUSHOPT();
            case "CLWB":
                return cpuFeatures.fCLWB();
            case "AVX512_VBMI2":
                return cpuFeatures.fAVX512_VBMI2();
            case "AVX512_VBMI":
                return cpuFeatures.fAVX512_VBMI();
            case "HV":
                return cpuFeatures.fHV();
            default:
                unknownFeatures.add(featureName);
                return false;
        }
    }

    @Override
    @Platforms(Platform.AMD64.class)
    public EnumSet<AMD64.CPUFeature> determineHostCPUFeatures() {
        EnumSet<AMD64.CPUFeature> features = EnumSet.noneOf(AMD64.CPUFeature.class);

        AMD64LibCHelper.CPUFeatures cpuFeatures = StackValue.get(AMD64LibCHelper.CPUFeatures.class);

        UnmanagedMemoryUtil.fill((Pointer) cpuFeatures, SizeOf.unsigned(AMD64LibCHelper.CPUFeatures.class), (byte) 0);

        AMD64LibCHelper.determineCPUFeatures(cpuFeatures);

        ArrayList<String> unknownFeatures = new ArrayList<>();
        for (AMD64.CPUFeature feature : AMD64.CPUFeature.values()) {
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
        AMD64 architecture = (AMD64) imageArchitecture;
        EnumSet<AMD64.CPUFeature> features = determineHostCPUFeatures();

        if (!features.containsAll(architecture.getFeatures())) {
            List<AMD64.CPUFeature> missingFeatures = new ArrayList<>();
            for (AMD64.CPUFeature feature : architecture.getFeatures()) {
                if (!features.contains(feature)) {
                    missingFeatures.add(feature);
                }
            }
            throw VMError.shouldNotReachHere("Current target does not support the following CPU features that are required by the image: " + missingFeatures);
        }
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
