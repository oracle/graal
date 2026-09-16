/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.auximage;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.genscavenge.AuxiliaryImageHeap;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.os.AuxiliaryImageProvider;
import com.oracle.svm.core.thread.ContinuationSupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.guest.staging.jdk.RuntimeSupport;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;

@AutomaticallyRegisteredFeature
class AuxiliaryImageFeature implements InternalFeature {
    private static boolean isSupported() {
        if (ImageLayerBuildingSupport.buildingImageLayer()) {
            return false;
        }
        if (!SubstrateOptions.useSerialGC() && !SubstrateOptions.useEpsilonGC()) {
            return false;
        }
        return Platform.includedIn(Platform.LINUX.class) || Platform.includedIn(Platform.DARWIN.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (!isSupported()) {
            if (ImageLayerBuildingSupport.firstImageBuild()) {
                ImageSingletons.add(AuxiliaryImageProvider.class, new UnsupportedAuxiliaryImageProvider());
            }
            return;
        }

        ImageSingletons.add(AuxiliaryImageHeap.class, new AuxiliaryImageHeapImpl());
        ImageSingletons.add(ContinuationSupport.class, new PersistedContinuationSupport());

        ImageSingletons.add(AuxiliaryImageProvider.class, new PosixLikeAuxiliaryImageProvider());
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        if (isSupported() && PersistedRuntimeCode.isSupportedInCurrentImage()) {
            RuntimeSupport.getRuntimeSupport().addInitializationHook(new AuxiliaryImageCodeInitializationHook());
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (isSupported()) {
            return;
        }

        String message = SubstrateOptions.useG1GC() ? "The G1 garbage collector ('--gc=G1') does not support auxiliary images."
                        : "Auxiliary images are not supported with the current configuration or platform.";
        long reservedBytes = SubstrateOptions.ReservedAuxiliaryImageBytes.getValue();
        if (reservedBytes != 0) {
            throw UserError.invalidOptionValue(SubstrateOptions.ReservedAuxiliaryImageBytes, reservedBytes, message);
        }

        access.registerReachabilityHandler(_ -> {
            throw UserError.abort(message);
        }, AuxiliaryImageBuilder.class);
    }
}

final class AuxiliaryImageCodeInitializationHook implements RuntimeSupport.Hook {
    @Override
    public void execute(boolean isFirstIsolate) {
        AuxiliaryImageLoader.installLoadedImageCode();
    }
}
