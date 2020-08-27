/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.CPUFeatureAccess;
import com.oracle.svm.core.CalleeSavedRegisters;
import com.oracle.svm.core.MemoryUtil;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;

@AutomaticFeature
@Platforms(Platform.AMD64.class)
class AMD64CPUFeatureAccessFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(CPUFeatureAccess.class, new AMD64CPUFeatureAccess());
    }
}

public class AMD64CPUFeatureAccess implements CPUFeatureAccess {
    @Platforms(Platform.AMD64.class)
    public static EnumSet<AMD64.CPUFeature> determineHostCPUFeatures() {
        EnumSet<AMD64.CPUFeature> features = EnumSet.noneOf(AMD64.CPUFeature.class);

        AMD64LibCHelper.CPUFeatures cpuFeatures = StackValue.get(AMD64LibCHelper.CPUFeatures.class);

        MemoryUtil.fillToMemoryAtomic((Pointer) cpuFeatures, SizeOf.unsigned(AMD64LibCHelper.CPUFeatures.class), (byte) 0);

        AMD64LibCHelper.determineCPUFeatures(cpuFeatures);
        if (cpuFeatures.fCX8()) {
            features.add(AMD64.CPUFeature.CX8);
        }
        if (cpuFeatures.fCMOV()) {
            features.add(AMD64.CPUFeature.CMOV);
        }
        if (cpuFeatures.fFXSR()) {
            features.add(AMD64.CPUFeature.FXSR);
        }
        if (cpuFeatures.fHT()) {
            features.add(AMD64.CPUFeature.HT);
        }
        if (cpuFeatures.fMMX()) {
            features.add(AMD64.CPUFeature.MMX);
        }
        if (cpuFeatures.fAMD3DNOWPREFETCH()) {
            features.add(AMD64.CPUFeature.AMD_3DNOW_PREFETCH);
        }
        if (cpuFeatures.fSSE()) {
            features.add(AMD64.CPUFeature.SSE);
        }
        if (cpuFeatures.fSSE2()) {
            features.add(AMD64.CPUFeature.SSE2);
        }
        if (cpuFeatures.fSSE3()) {
            features.add(AMD64.CPUFeature.SSE3);
        }
        if (cpuFeatures.fSSSE3()) {
            features.add(AMD64.CPUFeature.SSSE3);
        }
        if (cpuFeatures.fSSE4A()) {
            features.add(AMD64.CPUFeature.SSE4A);
        }
        if (cpuFeatures.fSSE41()) {
            features.add(AMD64.CPUFeature.SSE4_1);
        }
        if (cpuFeatures.fSSE42()) {
            features.add(AMD64.CPUFeature.SSE4_2);
        }
        if (cpuFeatures.fPOPCNT()) {
            features.add(AMD64.CPUFeature.POPCNT);
        }
        if (cpuFeatures.fLZCNT()) {
            features.add(AMD64.CPUFeature.LZCNT);
        }
        if (cpuFeatures.fTSC()) {
            features.add(AMD64.CPUFeature.TSC);
        }
        if (cpuFeatures.fTSCINV()) {
            features.add(AMD64.CPUFeature.TSCINV);
        }
        if (cpuFeatures.fAVX()) {
            features.add(AMD64.CPUFeature.AVX);
        }
        if (cpuFeatures.fAVX2()) {
            features.add(AMD64.CPUFeature.AVX2);
        }
        if (cpuFeatures.fAES()) {
            features.add(AMD64.CPUFeature.AES);
        }
        if (cpuFeatures.fERMS()) {
            features.add(AMD64.CPUFeature.ERMS);
        }
        if (cpuFeatures.fCLMUL()) {
            features.add(AMD64.CPUFeature.CLMUL);
        }
        if (cpuFeatures.fBMI1()) {
            features.add(AMD64.CPUFeature.BMI1);
        }
        if (cpuFeatures.fBMI2()) {
            features.add(AMD64.CPUFeature.BMI2);
        }
        if (cpuFeatures.fRTM()) {
            features.add(AMD64.CPUFeature.RTM);
        }
        if (cpuFeatures.fADX()) {
            features.add(AMD64.CPUFeature.ADX);
        }
        if (cpuFeatures.fAVX512F()) {
            features.add(AMD64.CPUFeature.AVX512F);
        }
        if (cpuFeatures.fAVX512DQ()) {
            features.add(AMD64.CPUFeature.AVX512DQ);
        }
        if (cpuFeatures.fAVX512PF()) {
            features.add(AMD64.CPUFeature.AVX512PF);
        }
        if (cpuFeatures.fAVX512ER()) {
            features.add(AMD64.CPUFeature.AVX512ER);
        }
        if (cpuFeatures.fAVX512CD()) {
            features.add(AMD64.CPUFeature.AVX512CD);
        }
        if (cpuFeatures.fAVX512BW()) {
            features.add(AMD64.CPUFeature.AVX512BW);
        }
        if (cpuFeatures.fSHA()) {
            features.add(AMD64.CPUFeature.SHA);
        }
        if (cpuFeatures.fFMA()) {
            features.add(AMD64.CPUFeature.FMA);
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

    @Override
    public void enableFeatures(Architecture runtimeArchitecture) {
        if (CalleeSavedRegisters.supportedByPlatform()) {
            /*
             * The code for saving and restoring callee-saved registers currently only covers the
             * registers and register bit width for the CPU features used at image build time. To
             * enable more CPU features for JIT compilation at run time, the new CPU features
             * computed by this method would need to be taken into account. Until this is
             * implemented as part of GR-20653, JIT compilation uses the same CPU features as AOT
             * compilation.
             */
            return;
        }
        AMD64 architecture = (AMD64) runtimeArchitecture;
        EnumSet<AMD64.CPUFeature> features = determineHostCPUFeatures();
        architecture.getFeatures().addAll(features);
    }
}
