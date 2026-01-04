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

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredPersistFlags;
import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.core.traits.SingletonLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.core.traits.SingletonTrait;
import com.oracle.svm.core.traits.SingletonTraitKind;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.util.LogUtils;

/**
 * This feature collects <code>System.getProperty("java.home")</code> usage information from the
 * {@link com.oracle.svm.hosted.phases.AnalyzeJavaHomeAccessPhase} and logs it in the image-build
 * output.
 */
@AutomaticallyRegisteredFeature
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = AnalyzeJavaHomeAccessFeature.LayeredCallbacks.class, layeredInstallationKind = Independent.class)
public class AnalyzeJavaHomeAccessFeature implements InternalFeature {
    private boolean javaHomeUsed = false;
    private Set<String> javaHomeUsageLocations = Collections.newSetFromMap(new ConcurrentSkipListMap<>());

    public static AnalyzeJavaHomeAccessFeature instance() {
        return ImageSingletons.lookup(AnalyzeJavaHomeAccessFeature.class);
    }

    public void setJavaHomeUsed() {
        javaHomeUsed = true;
    }

    public boolean getJavaHomeUsed() {
        return javaHomeUsed;
    }

    public void addJavaHomeUsageLocation(String location) {
        javaHomeUsageLocations.add(location);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        for (String location : javaHomeUsageLocations) {
            LogUtils.warning("System.getProperty(\"java.home\") detected at " + location);
        }
        if (!ImageLayerBuildingSupport.buildingSharedLayer()) {
            javaHomeUsageLocations = null;
        }
    }

    static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {
        private static final String JAVA_HOME_USED = "javaHomeUsed";
        private static final String JAVA_HOME_USAGE_LOCATIONS = "javaHomeUsageLocations";

        @Override
        public SingletonTrait getLayeredCallbacksTrait() {
            return new SingletonTrait(SingletonTraitKind.LAYERED_CALLBACKS, new SingletonLayeredCallbacks<AnalyzeJavaHomeAccessFeature>() {
                @Override
                public LayeredPersistFlags doPersist(ImageSingletonWriter writer, AnalyzeJavaHomeAccessFeature singleton) {
                    writer.writeInt(JAVA_HOME_USED, singleton.javaHomeUsed ? 1 : 0);
                    writer.writeStringList(JAVA_HOME_USAGE_LOCATIONS, singleton.javaHomeUsageLocations.stream().toList());
                    return LayeredPersistFlags.CALLBACK_ON_REGISTRATION;
                }

                @Override
                public void onSingletonRegistration(ImageSingletonLoader loader, AnalyzeJavaHomeAccessFeature singleton) {
                    singleton.javaHomeUsed = loader.readInt(JAVA_HOME_USED) == 1;
                    singleton.javaHomeUsageLocations.addAll(loader.readStringList(JAVA_HOME_USAGE_LOCATIONS));
                }
            });
        }
    }
}
