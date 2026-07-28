/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.JavaMainWrapper;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.guest.staging.JavaMainSupport;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.GuestAccess;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * This feature contains some assertions to ensure the application layer is valid.
 */
@AutomaticallyRegisteredFeature
final class ApplicationLayerImageFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.buildingApplicationLayer();
    }

    @Override
    public void afterCompilation(AfterCompilationAccess a) {
        FeatureImpl.AfterCompilationAccessImpl access = (FeatureImpl.AfterCompilationAccessImpl) a;
        if (ImageSingletons.contains(JavaMainSupport.class)) {
            /*
             * The invokeMain method uses a MethodHandle to invoke the image main entry point, which
             * can be really costly if the method is not compiled in the application layer. In the
             * base layer, the MethodHandle cannot be intrinsified yet.
             *
             * It is not possible to delay the method to the application layer because some image
             * don't need this entry point and use a custom one.
             */
            GuestAccess guestAccess = GuestAccess.get();
            ResolvedJavaMethod invokeMain = guestAccess.lookupMethod(guestAccess.lookupType(JavaMainWrapper.class), "invokeMain", String[].class);
            AnalysisMethod analysisInvokeMain = access.getUniverse().getBigBang().getUniverse().lookup(invokeMain);
            VMError.guarantee(!analysisInvokeMain.analyzedInPriorLayer(), "Method %s should not be compiled in a prior layer", analysisInvokeMain);
            boolean inCurrentLayer = analysisInvokeMain.isInlined() || analysisInvokeMain.isImplementationInvoked();
            VMError.guarantee(inCurrentLayer, "Method %s should be compiled in the application layer if there is a main entry point", analysisInvokeMain);
        }
    }
}
