/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumSet;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.CPUFeatureAccess;
import com.oracle.svm.core.annotate.AutomaticFeature;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Architecture;

@AutomaticFeature
@Platforms(Platform.AArch64.class)
class AArch64CPUFeatureAccessFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(CPUFeatureAccess.class, new AArch64CPUFeatureAccess());
    }
}

public class AArch64CPUFeatureAccess implements CPUFeatureAccess {
    @Platforms(Platform.AArch64.class)
    public static EnumSet<AArch64.CPUFeature> determineHostCPUFeatures() {
        EnumSet<AArch64.CPUFeature> features = EnumSet.noneOf(AArch64.CPUFeature.class);
        return features;
    }

    @Override
    public void verifyHostSupportsArchitecture(Architecture imageArchitecture) {

    }

    @Override
    public void enableFeatures(Architecture runtimeArchitecture) {
        AArch64 architecture = (AArch64) runtimeArchitecture;
        EnumSet<AArch64.CPUFeature> features = determineHostCPUFeatures();
        architecture.getFeatures().addAll(features);
    }
}
