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
package com.oracle.svm.hosted;

import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.hub.DynamicHubSupport;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.hub.registry.ClassRegistries;

@AutomaticallyRegisteredFeature
public class ClassRegistryFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ClassForNameSupport.respectClassLoader();
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(SymbolsFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.lookup(RuntimeClassInitializationSupport.class).initializeAtBuildTime("com.oracle.svm.espresso.classfile",
                        "Native Image classes needed for runtime class loading initialized at build time");
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        FeatureImpl.BeforeAnalysisAccessImpl access = (FeatureImpl.BeforeAnalysisAccessImpl) a;
        access.registerSubtypeReachabilityHandler((unused, cls) -> onTypeReachable(cls), Object.class);
    }

    private static void onTypeReachable(Class<?> cls) {
        if (cls.isArray() || cls.isHidden()) {
            return;
        }
        if (RuntimeClassLoading.isSupported() || ClassForNameSupport.isCurrentLayerRegisteredClass(cls.getName())) {
            ClassRegistries.addAOTClass(ClassLoaderFeature.getRuntimeClassLoader(cls.getClassLoader()), cls);
        }
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        ClassRegistries.setStartingTypeId(DynamicHubSupport.currentLayer().getMaxTypeId() + 1);
    }
}
