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

import java.util.List;

import com.oracle.graal.pointsto.heap.ImageHeap;
import com.oracle.graal.pointsto.heap.ImageLayerWriter;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.RelocatableConstant;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class SVMImageLayerWriter extends ImageLayerWriter {
    public SVMImageLayerWriter(ImageHeap imageHeap) {
        super(imageHeap, new SVMImageLayerSnapshotUtil());
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
