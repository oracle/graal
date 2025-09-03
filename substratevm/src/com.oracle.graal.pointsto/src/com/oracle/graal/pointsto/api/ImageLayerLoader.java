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
package com.oracle.graal.pointsto.api;

import java.util.Set;

import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.vm.ci.meta.JavaConstant;

public class ImageLayerLoader {
    /**
     * Returns the type id of the given type in the base layer if it exists. This makes the link
     * between the base layer and the extension layer as the id is used to determine which constant
     * should be linked to this type.
     */
    @SuppressWarnings("unused")
    public int lookupHostedTypeInBaseLayer(AnalysisType type) {
        throw AnalysisError.shouldNotReachHere("This method should not be called");
    }

    @SuppressWarnings("unused")
    public void initializeBaseLayerType(AnalysisType type) {
        throw AnalysisError.shouldNotReachHere("This method should not be called");
    }

    /**
     * Returns the method id of the given method in the base layer if it exists. This makes the link
     * between the base layer and the extension layer as the id is used to determine the method used
     * in RelocatableConstants.
     */
    @SuppressWarnings("unused")
    public int lookupHostedMethodInBaseLayer(AnalysisMethod analysisMethod) {
        throw AnalysisError.shouldNotReachHere("This method should not be called");
    }

    @SuppressWarnings("unused")
    public void addBaseLayerMethod(AnalysisMethod analysisMethod) {
        throw AnalysisError.shouldNotReachHere("This method should not be called");
    }

    /**
     * We save analysis parsed graphs for methods considered
     * {@link AnalysisMethod#isTrackedAcrossLayers()}.
     */
    @SuppressWarnings("unused")
    public boolean hasAnalysisParsedGraph(AnalysisMethod analysisMethod) {
        throw AnalysisError.shouldNotReachHere("This method should not be called");
    }

    @SuppressWarnings("unused")
    public AnalysisParsedGraph getAnalysisParsedGraph(AnalysisMethod analysisMethod) {
        throw AnalysisError.shouldNotReachHere("This method should not be called");
    }

    @SuppressWarnings("unused")
    public void loadPriorStrengthenedGraphAnalysisElements(AnalysisMethod analysisMethod) {
        throw AnalysisError.shouldNotReachHere("This method should not be called");
    }

    /**
     * Returns the field id of the given field in the base layer if it exists. This makes the link
     * between the base layer and the extension image as the id allows to set the flags of the
     * fields in the extension image.
     */
    @SuppressWarnings("unused")
    public int lookupHostedFieldInBaseLayer(AnalysisField analysisField) {
        throw AnalysisError.shouldNotReachHere("This method should not be called");
    }

    @SuppressWarnings("unused")
    public void addBaseLayerField(AnalysisField analysisField) {
        throw AnalysisError.shouldNotReachHere("This method should not be called");
    }

    @SuppressWarnings("unused")
    public void initializeBaseLayerField(AnalysisField analysisField) {
        throw AnalysisError.shouldNotReachHere("This method should not be called");
    }

    @SuppressWarnings("unused")
    public boolean hasValueForConstant(JavaConstant javaConstant) {
        throw AnalysisError.shouldNotReachHere("This method should not be called");
    }

    @SuppressWarnings("unused")
    public ImageHeapConstant getValueForConstant(JavaConstant javaConstant) {
        throw AnalysisError.shouldNotReachHere("This method should not be called");
    }

    @SuppressWarnings("unused")
    public Set<Integer> getRelinkedFields(AnalysisType type) {
        throw AnalysisError.shouldNotReachHere("This method should not be called");
    }

    @SuppressWarnings("unused")
    public boolean hasDynamicHubIdentityHashCode(int tid) {
        throw AnalysisError.shouldNotReachHere("This method should not be called");
    }

    @SuppressWarnings("unused")
    public int getDynamicHubIdentityHashCode(int tid) {
        throw AnalysisError.shouldNotReachHere("This method should not be called");
    }
}
