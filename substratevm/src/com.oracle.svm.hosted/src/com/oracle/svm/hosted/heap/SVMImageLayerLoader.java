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

import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.METHOD_POINTER_TAG;

import java.util.concurrent.ConcurrentHashMap;

import com.oracle.graal.pointsto.heap.ImageLayerLoader;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.BaseLayerMethod;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.meta.RelocatableConstant;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class SVMImageLayerLoader extends ImageLayerLoader {

    public SVMImageLayerLoader(AnalysisUniverse universe) {
        super(universe, new SVMImageLayerSnapshotUtil());
    }

    @Override
    protected boolean delegateProcessing(String constantType, Object constantValue, Object[] values, int i) {
        if (constantType.equals(METHOD_POINTER_TAG)) {
            AnalysisType methodPointerType = metaAccess.lookupJavaType(MethodPointer.class);
            int mid = (int) constantValue;
            AnalysisMethod method = methods.get(mid);
            if (method != null) {
                values[i] = new RelocatableConstant(new MethodPointer(method), methodPointerType);
            } else {
                AnalysisFuture<JavaConstant> task = new AnalysisFuture<>(() -> {
                    ResolvedJavaMethod resolvedMethod = methods.get(mid);
                    if (resolvedMethod == null) {
                        /*
                         * The method is not loaded yet, so we use a placeholder until it is
                         * available.
                         */
                        resolvedMethod = new BaseLayerMethod();
                        missingMethodTasks.computeIfAbsent(mid, unused -> ConcurrentHashMap.newKeySet()).add(new AnalysisFuture<>(() -> {
                            AnalysisMethod analysisMethod = methods.get(mid);
                            VMError.guarantee(analysisMethod != null, "Method with method id %d should be loaded.", mid);
                            RelocatableConstant constant = new RelocatableConstant(new MethodPointer(analysisMethod), methodPointerType);
                            values[i] = constant;
                            return constant;
                        }));
                    }
                    JavaConstant methodPointer = new RelocatableConstant(new MethodPointer(resolvedMethod), methodPointerType);
                    values[i] = methodPointer;
                    return methodPointer;
                });
                values[i] = task;
                missingMethodTasks.computeIfAbsent(mid, unused -> ConcurrentHashMap.newKeySet()).add(task);
            }
            return true;
        }
        return super.delegateProcessing(constantType, constantValue, values, i);
    }
}
