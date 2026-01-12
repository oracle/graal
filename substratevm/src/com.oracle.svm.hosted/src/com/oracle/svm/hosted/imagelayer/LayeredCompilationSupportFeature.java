/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.imagelayer;

import java.lang.reflect.Executable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.sdk.staging.hosted.layeredimage.LayeredCompilationSupport;
import com.oracle.svm.sdk.staging.layeredimage.LayeredCompilationBehavior;

/**
 * Internal logic for registering methods' layered compilation behavior during the
 * {@link Feature#beforeAnalysis} phase. It is expected that the user has invoked all
 * {@link LayeredCompilationSupport#registerCompilationBehavior}s in {@link Feature#duringSetup}.
 */
@AutomaticallyRegisteredFeature
public class LayeredCompilationSupportFeature extends LayeredCompilationSupport implements InternalFeature {
    record BehaviorRequest(Executable method, LayeredCompilationBehavior.Behavior behavior) {
    }

    /**
     * Saves the {@link #registerCompilationBehavior} registrations so that they can be registered
     * by this feature in the {@link Feature#beforeAnalysis} phase.
     */
    Set<BehaviorRequest> queueRegistrations = ConcurrentHashMap.newKeySet();

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.buildingImageLayer();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(LayeredCompilationSupport.class, this);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        AnalysisMetaAccess metaAccess = ((FeatureImpl.BeforeAnalysisAccessImpl) access).getMetaAccess();
        for (var registration : queueRegistrations) {
            AnalysisMethod aMethod = metaAccess.lookupJavaMethod(registration.method);
            registerCompilationBehavior(aMethod, registration.behavior);
        }
        queueRegistrations = null;
    }

    @Override
    public void registerCompilationBehavior(Executable method, LayeredCompilationBehavior.Behavior behavior) {
        if (queueRegistrations == null) {
            throw UserError.abort("Trying to register compilation behavior too late");
        }
        if (behavior == LayeredCompilationBehavior.Behavior.DEFAULT) {
            throw UserError.abort("Invalid behavior specified %s: %s", behavior, method);
        }
        queueRegistrations.add(new BehaviorRequest(method, behavior));
    }

    private static void registerCompilationBehavior(AnalysisMethod aMethod, LayeredCompilationBehavior.Behavior behavior) {
        switch (behavior) {
            case FULLY_DELAYED_TO_APPLICATION_LAYER -> aMethod.setFullyDelayedToApplicationLayer();
            case PINNED_TO_INITIAL_LAYER -> aMethod.setPinnedToInitialLayer();
            default -> throw VMError.shouldNotReachHere("Invalid behavior specified %s: %s", behavior, aMethod);
        }
    }
}
