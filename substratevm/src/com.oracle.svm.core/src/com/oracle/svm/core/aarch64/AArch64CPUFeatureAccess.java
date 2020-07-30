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
import com.oracle.svm.core.MemoryUtil;
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
    @Platforms(Platform.AARCH64.class)
    public static EnumSet<AArch64.CPUFeature> determineHostCPUFeatures() {
        EnumSet<AArch64.CPUFeature> features = EnumSet.noneOf(AArch64.CPUFeature.class);

        AArch64LibCHelper.CPUFeatures cpuFeatures = StackValue.get(AArch64LibCHelper.CPUFeatures.class);

        MemoryUtil.fillToMemoryAtomic((Pointer) cpuFeatures, SizeOf.unsigned(AArch64LibCHelper.CPUFeatures.class), (byte) 0);

        AArch64LibCHelper.determineCPUFeatures(cpuFeatures);

        if (cpuFeatures.fFP()) {
            features.add(AArch64.CPUFeature.FP);
        }
        if (cpuFeatures.fASIMD()) {
            features.add(AArch64.CPUFeature.ASIMD);
        }
        if (cpuFeatures.fEVTSTRM()) {
            features.add(AArch64.CPUFeature.EVTSTRM);
        }
        if (cpuFeatures.fAES()) {
            features.add(AArch64.CPUFeature.AES);
        }
        if (cpuFeatures.fPMULL()) {
            features.add(AArch64.CPUFeature.PMULL);
        }
        if (cpuFeatures.fSHA1()) {
            features.add(AArch64.CPUFeature.SHA1);
        }
        if (cpuFeatures.fSHA2()) {
            features.add(AArch64.CPUFeature.SHA2);
        }
        if (cpuFeatures.fCRC32()) {
            features.add(AArch64.CPUFeature.CRC32);
        }
        if (cpuFeatures.fLSE()) {
            features.add(AArch64.CPUFeature.LSE);
        }
        if (cpuFeatures.fSTXRPREFETCH()) {
            features.add(AArch64.CPUFeature.STXR_PREFETCH);
        }
        if (cpuFeatures.fA53MAC()) {
            features.add(AArch64.CPUFeature.A53MAC);
        }
        if (cpuFeatures.fDMBATOMICS()) {
            try {
                features.add(AArch64.CPUFeature.valueOf("DMB_ATOMICS"));
            } catch (IllegalArgumentException e) {
                // This JVMCI CPU feature is not available in all JDKs (JDK-8243339)
            }
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
