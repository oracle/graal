/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.thread;

import java.lang.reflect.Field;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import com.oracle.svm.core.SubstrateControlFlowIntegrity;
import com.oracle.svm.core.SubstrateControlFlowIntegrityFeature;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.heap.StoredContinuationAccess;
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
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = ContinuationsFeature.LayeredCallbacks.class, layeredInstallationKind = Independent.class)
@AutomaticallyRegisteredFeature
public class ContinuationsFeature implements InternalFeature {
    private boolean supported;

    public static boolean isSupported() {
        return ImageSingletons.lookup(ContinuationsFeature.class).supported;
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(SubstrateControlFlowIntegrityFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        boolean previousLayerSupported = supported;

        /* If continuations are not supported, "virtual" threads are bound to platform threads. */
        if (ContinuationSupport.Options.VMContinuations.getValue()) {
            boolean hostSupport = jdk.internal.vm.ContinuationSupport.isSupported();
            if (!hostSupport) {
                if (ContinuationSupport.Options.VMContinuations.hasBeenSet()) {
                    throw UserError.abort("Continuation support has been explicitly enabled with option %s but is not available in the host VM", ContinuationSupport.Options.VMContinuations.getName());
                }
                RuntimeClassInitializationSupport rci = ImageSingletons.lookup(RuntimeClassInitializationSupport.class);
                rci.initializeAtRunTime("jdk.internal.vm.Continuation", "Host continuations are not supported");
            }
            supported = hostSupport && !DeoptimizationSupport.enabled() && !SubstrateOptions.useLLVMBackend() && SubstrateControlFlowIntegrity.singleton().continuationsSupported();
            UserError.guarantee(supported || !ContinuationSupport.Options.VMContinuations.hasBeenSet(),
                            "Continuation support has been explicitly enabled with option %s but is not available " +
                                            "because of the runtime compilation, LLVM backend, or control flow integrity features.",
                            ContinuationSupport.Options.VMContinuations.getName());
        } else {
            supported = false;
        }

        if (ImageLayerBuildingSupport.buildingExtensionLayer()) {
            VMError.guarantee(supported == previousLayerSupported, "The previous layer supported value was %b, but the one from the current layer is %b", previousLayerSupported, supported);
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (isSupported()) {
            if (!ImageSingletons.contains(ContinuationSupport.class) && ImageLayerBuildingSupport.firstImageBuild()) {
                ImageSingletons.add(ContinuationSupport.class, new ContinuationSupport());
            }

            Field ipField = ReflectionUtil.lookupField(StoredContinuation.class, "ip");
            access.registerAsAccessed(ipField);

            access.registerReachabilityHandler(_ -> access.registerAsInHeap(StoredContinuation.class),
                            ReflectionUtil.lookupMethod(StoredContinuationAccess.class, "allocate", int.class));
        } else {
            access.registerReachabilityHandler(_ -> VMError.shouldNotReachHere("Virtual threads internals are reachable but support is not available or active."), StoredContinuationAccess.class);
        }
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        if (isSupported() && ImageLayerBuildingSupport.firstImageBuild()) {
            Field ipField = ReflectionUtil.lookupField(StoredContinuation.class, "ip");
            long offset = access.objectFieldOffset(ipField);
            ContinuationSupport.singleton().setIPOffset(offset);
        }
    }

    static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {
        @Override
        public SingletonTrait getLayeredCallbacksTrait() {
            var action = new SingletonLayeredCallbacks<ContinuationsFeature>() {
                @Override
                public LayeredPersistFlags doPersist(ImageSingletonWriter writer, ContinuationsFeature singleton) {
                    writer.writeInt("supported", ContinuationsFeature.isSupported() ? 1 : 0);
                    return LayeredPersistFlags.CALLBACK_ON_REGISTRATION;
                }

                @Override
                public void onSingletonRegistration(ImageSingletonLoader loader, ContinuationsFeature singleton) {
                    singleton.supported = loader.readInt("supported") == 1;
                }
            };
            return new SingletonTrait(SingletonTraitKind.LAYERED_CALLBACKS, action);
        }
    }
}
