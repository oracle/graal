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

import static com.oracle.graal.pointsto.heap.ImageLayerLoader.get;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.FACTORY_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.METHOD_TYPE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.TARGET_CONSTRUCTOR_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.THROW_ALLOCATED_OBJECT_TAG;

import org.graalvm.collections.EconomicMap;

import com.oracle.graal.pointsto.heap.ImageLayerLoader;
import com.oracle.graal.pointsto.heap.ImageLayerLoaderHelper;
import com.oracle.svm.hosted.code.FactoryMethodSupport;

public class SVMImageLayerLoaderHelper extends ImageLayerLoaderHelper {
    public SVMImageLayerLoaderHelper(ImageLayerLoader imageLayerLoader) {
        super(imageLayerLoader);
    }

    @Override
    protected boolean loadMethod(EconomicMap<String, Object> methodData, int mid) {
        String methodType = get(methodData, METHOD_TYPE_TAG);
        if (methodType == null) {
            return false;
        }
        if (methodType.equals(FACTORY_TAG)) {
            int constructorId = get(methodData, TARGET_CONSTRUCTOR_TAG);
            boolean throwAllocatedObject = get(methodData, THROW_ALLOCATED_OBJECT_TAG);
            FactoryMethodSupport.singleton().lookup(imageLayerLoader.getMetaAccess(), imageLayerLoader.getAnalysisMethod(constructorId), throwAllocatedObject);
            return true;
        }
        return false;
    }
}
