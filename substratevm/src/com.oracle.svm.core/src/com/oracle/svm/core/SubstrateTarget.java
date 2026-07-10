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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.code.RuntimeCodeCache;
import com.oracle.svm.shared.singletons.ImageSingletonLoader;
import com.oracle.svm.shared.singletons.ImageSingletonWriter;
import com.oracle.svm.shared.singletons.LayeredPersistFlags;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.LayeredCallbacksSingletonTrait;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.Duplicable;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;

@SingletonTraits(access = AllAccess.class, layeredCallbacks = SubstrateTarget.LayeredCallbacks.class, layeredInstallationKind = Duplicable.class)
public class SubstrateTarget extends TargetDescription {
    @Fold
    public static SubstrateTarget singleton() {
        return ImageSingletons.lookup(SubstrateTarget.class);
    }

    @Fold
    public static JavaKind getWordKind() {
        return singleton().wordJavaKind;
    }

    @Fold
    public static int getWordSize() {
        return singleton().wordSize;
    }

    @Fold
    public static Architecture getArchitecture() {
        return singleton().arch;
    }

    public static Stamp getWordStamp() {
        return StampFactory.forKind(getWordKind());
    }

    public static boolean shouldInlineObjectsInRuntimeCode() {
        return RuntimeCodeCache.Options.WriteableCodeCache.getValue();
    }

    private final EnumSet<?> runtimeCheckedCPUFeatures;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateTarget(Architecture arch, boolean isMP, int stackAlignment, int implicitNullCheckLimit, EnumSet<?> runtimeCheckedCPUFeatures) {
        super(arch, isMP, stackAlignment, implicitNullCheckLimit, true);
        this.runtimeCheckedCPUFeatures = runtimeCheckedCPUFeatures;
    }

    public EnumSet<?> getRuntimeCheckedCPUFeatures() {
        return runtimeCheckedCPUFeatures;
    }

    static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {
        private static final String RUNTIME_CHECKED_CPU_FEATURES = "runtimeCheckedCPUFeatures";

        @Override
        public LayeredCallbacksSingletonTrait getLayeredCallbacksTrait() {
            var action = new SingletonLayeredCallbacks<SubstrateTarget>() {
                @Override
                public LayeredPersistFlags doPersist(ImageSingletonWriter writer, SubstrateTarget singleton) {
                    writer.writeStringList(RUNTIME_CHECKED_CPU_FEATURES, getCPUFeaturesList(singleton));
                    return LayeredPersistFlags.CALLBACK_ON_REGISTRATION;
                }

                @Override
                public void onSingletonRegistration(ImageSingletonLoader loader, SubstrateTarget singleton) {
                    List<String> previousLayerRuntimeCheckedCPUFeatures = loader.readStringList(RUNTIME_CHECKED_CPU_FEATURES);
                    List<String> currentLayerRuntimeCheckedCPUFeatures = getCPUFeaturesList(singleton);
                    VMError.guarantee(previousLayerRuntimeCheckedCPUFeatures.equals(currentLayerRuntimeCheckedCPUFeatures),
                                    "The runtime checked CPU Features should be consistent across layers. The previous layer CPU Features were %s, but the current layer are %s",
                                    previousLayerRuntimeCheckedCPUFeatures, currentLayerRuntimeCheckedCPUFeatures);
                }
            };
            return new LayeredCallbacksSingletonTrait(action);
        }
    }

    private static List<String> getCPUFeaturesList(SubstrateTarget substrateTarget) {
        return substrateTarget.runtimeCheckedCPUFeatures.stream().map(Enum::toString).toList();
    }
}
