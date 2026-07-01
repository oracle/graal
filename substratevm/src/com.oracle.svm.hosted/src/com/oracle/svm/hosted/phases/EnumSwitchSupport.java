/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;
import com.oracle.svm.shared.singletons.ImageSingletonLoader;
import com.oracle.svm.shared.singletons.ImageSingletonWriter;
import com.oracle.svm.shared.singletons.LayeredPersistFlags;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.LayeredCallbacksSingletonTrait;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

import jdk.graal.compiler.nodes.StructuredGraph;

@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = EnumSwitchSupport.LayeredCallbacks.class)
final class EnumSwitchSupport {
    private static final String METHODS_ID = "methodsId";
    private static final String METHODS_METHOD_VARIANT_KEY_NAME = "methodsMethodVariantKeyName";
    private static final String METHODS_SAFE = "methodsSafe";

    private ConcurrentMap<AnalysisMethodKey, Boolean> methodsSafeForExecution = new ConcurrentHashMap<>();

    private record AnalysisMethodKey(int id, String methodVariantKeyName) {
    }

    static EnumSwitchSupport singleton() {
        return ImageSingletons.lookup(EnumSwitchSupport.class);
    }

    void onMethodParsed(AnalysisMethod method, StructuredGraph graph) {
        boolean methodSafeForExecution = graph.getNodes().filter(node -> node instanceof EnsureClassInitializedNode).isEmpty();

        Boolean existingValue = methodsSafeForExecution.put(new AnalysisMethodKey(method.getId(), method.getMethodVariantKey().toString()), methodSafeForExecution);
        assert existingValue == null || SubstrateCompilationDirectives.isDeoptTarget(method) ||
                        (method.isInSharedLayer() && existingValue == methodSafeForExecution) : "Method parsed twice: " + method.format("%H.%n(%p)");
    }

    Boolean isMethodsSafeForExecution(AnalysisMethod method) {
        return methodsSafeForExecution.get(new AnalysisMethodKey(method.getId(), method.getMethodVariantKey().toString()));
    }

    void afterAnalysis() {
        /*
         * When building a Layered Image, the methods that are safe for execution need to be
         * persisted across layers.
         */
        if (!ImageLayerBuildingSupport.buildingImageLayer()) {
            methodsSafeForExecution = null;
        }
    }

    static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {
        @Override
        public LayeredCallbacksSingletonTrait getLayeredCallbacksTrait() {
            return new LayeredCallbacksSingletonTrait(new SingletonLayeredCallbacks<EnumSwitchSupport>() {
                @Override
                public LayeredPersistFlags doPersist(ImageSingletonWriter writer, EnumSwitchSupport singleton) {
                    List<Integer> methodsId = new ArrayList<>();
                    List<String> methodsMethodVariantKey = new ArrayList<>();
                    List<Boolean> methodsSafe = new ArrayList<>();
                    for (var entry : singleton.methodsSafeForExecution.entrySet()) {
                        AnalysisMethodKey key = entry.getKey();
                        methodsId.add(key.id());
                        methodsMethodVariantKey.add(key.methodVariantKeyName());
                        methodsSafe.add(entry.getValue());
                    }
                    writer.writeIntList(METHODS_ID, methodsId);
                    writer.writeStringList(METHODS_METHOD_VARIANT_KEY_NAME, methodsMethodVariantKey);
                    writer.writeBoolList(METHODS_SAFE, methodsSafe);
                    return LayeredPersistFlags.CALLBACK_ON_REGISTRATION;
                }

                @Override
                public void onSingletonRegistration(ImageSingletonLoader loader, EnumSwitchSupport singleton) {
                    List<Integer> methodsId = loader.readIntList(METHODS_ID);
                    List<String> methodsMethodVariantKeyName = loader.readStringList(METHODS_METHOD_VARIANT_KEY_NAME);
                    List<Boolean> methodsSafe = loader.readBoolList(METHODS_SAFE);
                    ConcurrentMap<AnalysisMethodKey, Boolean> methodsSafeForExecution = singleton.methodsSafeForExecution;
                    for (int i = 0; i < methodsId.size(); ++i) {
                        methodsSafeForExecution.put(new AnalysisMethodKey(methodsId.get(i), methodsMethodVariantKeyName.get(i)), methodsSafe.get(i));
                    }
                }
            });
        }
    }
}
