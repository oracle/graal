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
package com.oracle.svm.core;

import java.util.EnumSet;
import java.util.List;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.code.RuntimeCodeCache;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton.PersistFlags;
import com.oracle.svm.core.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.core.traits.SingletonLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.core.traits.SingletonTrait;
import com.oracle.svm.core.traits.SingletonTraitKind;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.TargetDescription;

@SingletonTraits(access = AllAccess.class, layeredCallbacks = SubstrateTargetDescription.LayeredCallbacks.class, layeredInstallationKind = Independent.class)
public class SubstrateTargetDescription extends TargetDescription {
    private static final String RUNTIME_CHECKED_CPU_FEATURES = "runtimeCheckedCPUFeatures";

    @Platforms(Platform.HOSTED_ONLY.class)
    public static boolean shouldInlineObjectsInImageCode() {
        return SubstrateOptions.SpawnIsolates.getValue();
    }

    public static boolean shouldInlineObjectsInRuntimeCode() {
        return SubstrateOptions.SpawnIsolates.getValue() && RuntimeCodeCache.Options.WriteableCodeCache.getValue();
    }

    private final EnumSet<?> runtimeCheckedCPUFeatures;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateTargetDescription(Architecture arch, boolean isMP, int stackAlignment, int implicitNullCheckLimit, EnumSet<?> runtimeCheckedCPUFeatures) {
        super(arch, isMP, stackAlignment, implicitNullCheckLimit, shouldInlineObjectsInImageCode());
        this.runtimeCheckedCPUFeatures = runtimeCheckedCPUFeatures;
    }

    public EnumSet<?> getRuntimeCheckedCPUFeatures() {
        return runtimeCheckedCPUFeatures;
    }

    static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {
        @Override
        public SingletonTrait getLayeredCallbacksTrait() {
            SingletonLayeredCallbacks action = new SingletonLayeredCallbacks() {
                @Override
                public LayeredImageSingleton.PersistFlags doPersist(ImageSingletonWriter writer, Object singleton) {
                    SubstrateTargetDescription substrateTargetDescription = (SubstrateTargetDescription) singleton;
                    writer.writeStringList(RUNTIME_CHECKED_CPU_FEATURES, getCPUFeaturesList(substrateTargetDescription));
                    return PersistFlags.CALLBACK_ON_REGISTRATION;
                }

                @Override
                public void onSingletonRegistration(ImageSingletonLoader loader, Object singleton) {
                    SubstrateTargetDescription substrateTargetDescription = (SubstrateTargetDescription) singleton;
                    List<String> previousLayerRuntimeCheckedCPUFeatures = loader.readStringList(RUNTIME_CHECKED_CPU_FEATURES);
                    List<String> currentLayerRuntimeCheckedCPUFeatures = getCPUFeaturesList(substrateTargetDescription);
                    VMError.guarantee(previousLayerRuntimeCheckedCPUFeatures.equals(currentLayerRuntimeCheckedCPUFeatures),
                                    "The runtime checked CPU Features should be consistent across layers. The previous layer CPU Features were %s, but the current layer are %s",
                                    previousLayerRuntimeCheckedCPUFeatures, currentLayerRuntimeCheckedCPUFeatures);
                }
            };
            return new SingletonTrait(SingletonTraitKind.LAYERED_CALLBACKS, action);
        }
    }

    private static List<String> getCPUFeaturesList(SubstrateTargetDescription substrateTargetDescription) {
        return substrateTargetDescription.runtimeCheckedCPUFeatures.stream().map(Enum::toString).toList();
    }
}
