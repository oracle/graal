/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.thread;

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.CalleeSavedRegisters;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.thread.SafepointSlowpath;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;

@AutomaticallyRegisteredFeature
@Platforms(InternalPlatform.NATIVE_ONLY.class)
final class SafepointTailCallFeature implements InternalFeature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        if (!SubstrateOptions.SafepointCheckInEpilogue.getValue() || !CalleeSavedRegisters.supportedByPlatform()) {
            return;
        }

        FeatureImpl.BeforeAnalysisAccessImpl access = (FeatureImpl.BeforeAnalysisAccessImpl) a;

        /*
         * The method used for tail calls is not invoked in a snippet, so static analysis and the
         * compile queue do not automatically see it as invoked. Register it manually as compiled.
         */
        access.registerAsRoot((AnalysisMethod) SafepointSlowpath.ENTER_SLOW_PATH_SAFEPOINT_CHECK_OBJECT.findMethod(access.getMetaAccess()), true,
                        "Safepoint tail-call target, registered in " + SafepointTailCallFeature.class);
    }
}
