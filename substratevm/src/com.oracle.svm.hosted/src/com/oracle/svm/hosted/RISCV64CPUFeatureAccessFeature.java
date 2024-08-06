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

import java.util.EnumSet;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.riscv64.RISCV64CPUFeatureAccess;
import com.oracle.svm.core.riscv64.RISCV64LibCHelper;

import jdk.vm.ci.riscv64.RISCV64;

@AutomaticallyRegisteredFeature
@Platforms(Platform.RISCV64.class)
class RISCV64CPUFeatureAccessFeature extends CPUFeatureAccessFeatureBase implements InternalFeature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess arg) {
        var targetDescription = ImageSingletons.lookup(SubstrateTargetDescription.class);
        var arch = (RISCV64) targetDescription.arch;
        var buildtimeCPUFeatures = arch.getFeatures();
        initializeCPUFeatureAccessData(RISCV64.CPUFeature.values(), buildtimeCPUFeatures, RISCV64LibCHelper.CPUFeatures.class, (FeatureImpl.BeforeAnalysisAccessImpl) arg);
    }

    @Override
    protected RISCV64CPUFeatureAccess createCPUFeatureAccessSingleton(EnumSet<?> buildtimeCPUFeatures, int[] offsets, byte[] errorMessageBytes, byte[] buildtimeFeatureMaskBytes) {
        return new RISCV64CPUFeatureAccess(buildtimeCPUFeatures, offsets, errorMessageBytes, buildtimeFeatureMaskBytes);
    }
}
