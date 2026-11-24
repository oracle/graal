/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.guest.staging.core.heap.GCCause;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.singletons.ImageSingletonLoader;
import com.oracle.svm.shared.singletons.ImageSingletonWriter;
import com.oracle.svm.shared.singletons.LayeredPersistFlags;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.LayeredCallbacksSingletonTrait;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.debug.Assertions;

@AutomaticallyRegisteredFeature
@SingletonTraits(access = AllAccess.class, layeredCallbacks = GCCauseFeature.LayeredCallbacks.class)
class GCCauseFeature implements InternalFeature {
    /**
     * In layered builds all {@link GCCause}s are registered and installed in the initial layer.
     * Here we track which {@link GCCause}s were installed in the initial layer to detect issues.
     */
    List<String> registeredGCCauses;

    @Override
    public void onRegistration(OnRegistrationAccess access) {
        ImageSingletons.add(GCCauseFeature.class, this);
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        if (!ImageLayerBuildingSupport.buildingImageLayer()) {
            /*
             * In traditional builds we lazily register GCCauses as they become visible to the
             * native-image generator.
             */
            access.registerObjectReplacer(obj -> {
                if (obj instanceof GCCause gcCause) {
                    GCCause.registerGCCause(gcCause);
                }
                return obj;
            });
        } else {
            /*
             * For layered builds we eagerly register all GCCauses in the initial layer. In all
             * layers, via an object replacer, we then validate all referenced GCCauses have been
             * registered.
             */
            Function<Integer, String> idToGCCauseName;
            if (ImageLayerBuildingSupport.buildingInitialLayer()) {
                GCCause.registerGCCause(GCCause.JavaLangSystemGC);
                GCCause.registerGCCause(GCCause.TestGCInDeoptimizer);
                GCCause.registerGCCause(GCCause.HintedGC);
                GCCause.registerGCCause(GCCause.JvmtiForceGC);
                GCCause.registerGCCause(GCCause.HeapDump);
                GCCause.registerGCCause(GCCause.DiagnosticCommand);
                GCCause.registerGCCause(GCCause.WhiteBoxTestYoungGC);
                GCCause.registerGCCause(GCCause.WhiteBoxTestFullGC);
                GCCause.registerGCCause(GCCause.RuntimeStateTrimYoungGC);
                GCCause.registerGCCause(GCCause.RuntimeStateTrimFullGC);

                var gcCauseList = GCCause.getGCCauses();
                idToGCCauseName = (idx) -> gcCauseList.get(idx).getName();
            } else {
                idToGCCauseName = registeredGCCauses::get;
            }
            access.registerObjectReplacer(obj -> {
                if (obj instanceof GCCause gcCause) {
                    if (!idToGCCauseName.apply(gcCause.getId()).equals(gcCause.getName())) {
                        var id = gcCause.getId();
                        VMError.shouldNotReachHere("Mismatch in GCCause name for id %s: %s %s", id, idToGCCauseName.apply(id), gcCause.getName());
                    }
                }
                return obj;
            });
        }
    }

    static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {

        @Override
        public LayeredCallbacksSingletonTrait getLayeredCallbacksTrait() {
            return new LayeredCallbacksSingletonTrait(new SingletonLayeredCallbacks<GCCauseFeature>() {
                @Override
                public LayeredPersistFlags doPersist(ImageSingletonWriter writer, GCCauseFeature singleton) {
                    List<String> gcCauses;
                    if (ImageLayerBuildingSupport.buildingInitialLayer()) {
                        gcCauses = GCCause.getGCCauses().stream().map(gcCause -> {
                            if (gcCause == null) {
                                return "";
                            } else {
                                assert !gcCause.getName().isEmpty() : Assertions.errorMessage("Empty string is reserved for non-existent GCCauses", gcCause);
                                return gcCause.getName();
                            }
                        }).toList();
                    } else {
                        gcCauses = singleton.registeredGCCauses;
                    }
                    writer.writeStringList("registeredGCCauses", gcCauses);

                    return LayeredPersistFlags.CALLBACK_ON_REGISTRATION;
                }

                @Override
                public void onSingletonRegistration(ImageSingletonLoader loader, GCCauseFeature singleton) {
                    singleton.registeredGCCauses = Collections.unmodifiableList(loader.readStringList("registeredGCCauses"));
                }
            });
        }
    }
}
