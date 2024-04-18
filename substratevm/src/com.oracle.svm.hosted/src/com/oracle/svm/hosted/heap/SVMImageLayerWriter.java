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
package com.oracle.svm.hosted.heap;

import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.CLASS_ID_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.METHOD_POINTER_TAG;

import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.heap.ImageHeap;
import com.oracle.graal.pointsto.heap.ImageLayerWriter;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.lambda.LambdaSubstitutionType;
import com.oracle.svm.hosted.lambda.StableLambdaProxyNameFeature;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.RelocatableConstant;
import com.oracle.svm.hosted.methodhandles.MethodHandleFeature;
import com.oracle.svm.hosted.methodhandles.MethodHandleInvokerSubstitutionType;
import com.oracle.svm.hosted.reflect.proxy.ProxyRenamingSubstitutionProcessor;
import com.oracle.svm.hosted.reflect.proxy.ProxySubstitutionType;
import com.oracle.svm.util.LogUtils;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class SVMImageLayerWriter extends ImageLayerWriter {
    public SVMImageLayerWriter(ImageHeap imageHeap) {
        super(imageHeap, new SVMImageLayerSnapshotUtil());
    }

    @Override
    public void checkTypeStability(AnalysisType type) {
        /*
         * Lambda functions containing the same method invocations will return the same hash. They
         * will still have a different name, but in a multi threading context, the names can be
         * switched.
         */
        if (type.getWrapped() instanceof LambdaSubstitutionType lambdaSubstitutionType) {
            StableLambdaProxyNameFeature stableLambdaProxyNameFeature = ImageSingletons.lookup(StableLambdaProxyNameFeature.class);
            if (!stableLambdaProxyNameFeature.getLambdaSubstitutionProcessor().isNameAlwaysStable(lambdaSubstitutionType.getName())) {
                String message = "The lambda method " + lambdaSubstitutionType.getName() + " might not have a stable name in the extension image.";
                handleNameConflict(message);
            }
        }
        /*
         * Method handle with the same inner method handles will return the same hash. They will
         * still have a different name, but in a multi threading context, the names can be switched.
         */
        if (type.getWrapped() instanceof MethodHandleInvokerSubstitutionType methodHandleSubstitutionType) {
            MethodHandleFeature methodHandleFeature = ImageSingletons.lookup(MethodHandleFeature.class);
            if (!methodHandleFeature.getMethodHandleSubstitutionProcessor().isNameAlwaysStable(methodHandleSubstitutionType.getName())) {
                String message = "The method handle " + methodHandleSubstitutionType.getName() + " might not have a stable name in the extension image.";
                handleNameConflict(message);
            }
        }

        if (type.getWrapped() instanceof ProxySubstitutionType proxySubstitutionType) {
            if (!ProxyRenamingSubstitutionProcessor.isNameAlwaysStable(proxySubstitutionType.getName())) {
                String message = "The Proxy type " + proxySubstitutionType.getName() + " might not have a stable name in the extension image.";
                handleNameConflict(message);
            }
        }
    }

    private static void handleNameConflict(String message) {
        if (SubstrateOptions.AbortOnNameConflict.getValue()) {
            throw VMError.shouldNotReachHere(message);
        } else {
            LogUtils.warning(message);
        }
    }

    @Override
    public void persistConstantRelinkingInfo(EconomicMap<String, Object> constantMap, BigBang bb, Class<?> clazz, JavaConstant hostedObject) {
        ResolvedJavaType type = bb.getConstantReflectionProvider().asJavaType(hostedObject);
        if (type instanceof AnalysisType analysisType) {
            /*
             * Until another solution for implementing a stable name for $$TypeSwitch classes is
             * found, the constant containing a DynamicHub corresponding to a $$TypeSwitch class is
             * not persisted as it would not be possible to relink it. Considering that those
             * classes are only used as a container for a static method, recreating the constant in
             * the extension image alongside the class should not cause too much issues.
             */
            if (!isTypeSwitch(analysisType)) {
                constantMap.put(CLASS_ID_TAG, analysisType.getId());
            }
        } else {
            super.persistConstantRelinkingInfo(constantMap, bb, clazz, hostedObject);
        }
    }

    @Override
    protected boolean delegateProcessing(List<List<Object>> data, Object constant) {
        if (constant instanceof RelocatableConstant relocatableConstant) {
            data.add(List.of(METHOD_POINTER_TAG, getRelocatableConstantMethodId(relocatableConstant)));
            return true;
        }
        return super.delegateProcessing(data, constant);
    }

    private static int getRelocatableConstantMethodId(RelocatableConstant relocatableConstant) {
        ResolvedJavaMethod method = ((MethodPointer) relocatableConstant.getPointer()).getMethod();
        if (method instanceof HostedMethod hostedMethod) {
            return getMethodId(hostedMethod.wrapped);
        } else {
            return getMethodId((AnalysisMethod) method);
        }
    }

    private static int getMethodId(AnalysisMethod analysisMethod) {
        if (!analysisMethod.isReachable()) {
            /*
             * At the moment, only reachable methods are persisted, so the method will not be loaded
             * in the extension image.
             */
            return -1;
        } else {
            return analysisMethod.getId();
        }
    }
}
