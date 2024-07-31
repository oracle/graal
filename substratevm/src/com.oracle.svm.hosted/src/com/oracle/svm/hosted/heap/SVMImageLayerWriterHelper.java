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

import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.FACTORY_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.METHOD_TYPE_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.TARGET_CONSTRUCTOR_TAG;
import static com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil.THROW_ALLOCATED_OBJECT_TAG;

import org.graalvm.collections.EconomicMap;

import com.oracle.graal.pointsto.heap.ImageLayerWriter;
import com.oracle.graal.pointsto.heap.ImageLayerWriterHelper;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.code.FactoryMethod;

public class SVMImageLayerWriterHelper extends ImageLayerWriterHelper {
    public SVMImageLayerWriterHelper(ImageLayerWriter imageLayerWriter) {
        super(imageLayerWriter);
    }

    @Override
    protected void persistMethod(AnalysisMethod method, EconomicMap<String, Object> methodMap) {
        if (method.wrapped instanceof FactoryMethod factoryMethod) {
            methodMap.put(METHOD_TYPE_TAG, FACTORY_TAG);
            AnalysisMethod targetConstructor = method.getUniverse().lookup(factoryMethod.getTargetConstructor());
            if (!method.isReachable() && !imageLayerWriter.isMethodPersisted(targetConstructor)) {
                imageLayerWriter.persistAnalysisParsedGraph(targetConstructor);
                imageLayerWriter.persistMethod(targetConstructor);
            }
            methodMap.put(TARGET_CONSTRUCTOR_TAG, targetConstructor.getId());
            methodMap.put(THROW_ALLOCATED_OBJECT_TAG, factoryMethod.throwAllocatedObject());
        }
        super.persistMethod(method, methodMap);
    }
}
