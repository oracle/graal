/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.flow;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.graal.compiler.replacements.nodes.BasicArrayCopyNode;
import jdk.vm.ci.code.BytecodePosition;

/**
 * Models the flow transfer of an {@link BasicArrayCopyNode} node which intrinsifies calls to
 * System.arraycopy(). This flow registers itself as an observer for both the source and the
 * destination. When either the source or the destination elements change the element flows from
 * source are passed to destination.
 */
public class ArrayCopyTypeFlow extends TypeFlow<BytecodePosition> {

    TypeFlow<?> srcArrayFlow;
    TypeFlow<?> dstArrayFlow;

    public ArrayCopyTypeFlow(BytecodePosition location, AnalysisType declaredType, TypeFlow<?> srcArrayFlow, TypeFlow<?> dstArrayFlow) {
        super(location, declaredType);
        this.srcArrayFlow = srcArrayFlow;
        this.dstArrayFlow = dstArrayFlow;
    }

    public ArrayCopyTypeFlow(PointsToAnalysis bb, ArrayCopyTypeFlow original, MethodFlowsGraph methodFlows) {
        super(original, methodFlows);
        this.srcArrayFlow = methodFlows.lookupCloneOf(bb, original.srcArrayFlow);
        this.dstArrayFlow = methodFlows.lookupCloneOf(bb, original.dstArrayFlow);
    }

    @Override
    public TypeFlow<BytecodePosition> copy(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
        return new ArrayCopyTypeFlow(bb, this, methodFlows);
    }

    private TypeState lastSrc;
    private TypeState lastDst;

    @Override
    public void onObservedUpdate(PointsToAnalysis bb) {
        if (bb.analysisPolicy().aliasArrayTypeFlows()) {
            /* All arrays are aliased, no need to model the array copy operation. */
            return;
        }
        /*
         * Both the source and the destination register this flow as an observer and notify it when
         * either of them is updated. When either the source or the destination elements change the
         * element flows from source are passed to destination.
         */
        TypeState srcArrayState = srcArrayFlow.getState();
        TypeState dstArrayState = dstArrayFlow.getState();

        /*
         * The handling in processStates() has quadratic complexity because source and destination
         * types states are iterated in nested loops. That was visible in profiling runs of large
         * applications. So we optimize it as much as possible: We compute the delta of added source
         * and destination types, to avoid re-processing the same elements over and over.
         */
        if (lastSrc == null || lastDst == null || bb.analysisPolicy().allocationSiteSensitiveHeap()) {
            /*
             * No previous state available, process the full type states. We also need to do that
             * when using the allocation site context, because TypeState.forSubtraction does not
             * work for that yet.
             */
            processStates(bb, srcArrayState, dstArrayState);

        } else {
            TypeState addedSrcState = TypeState.forSubtraction(bb, srcArrayState, lastSrc);
            TypeState addedDstState = TypeState.forSubtraction(bb, dstArrayState, lastDst);
            int addedSrcSize = addedSrcState.objectsCount();
            int addedDstSize = addedDstState.objectsCount();

            if (addedSrcSize == 0 && addedDstSize == 0) {
                /* Source and destination did not change. There is nothing to do. */
            } else if (addedSrcSize == 0) {
                processStates(bb, srcArrayState, addedDstState);
            } else if (addedDstSize == 0) {
                processStates(bb, addedSrcState, dstArrayState);

            } else if ((long) srcArrayState.objectsCount() * addedDstSize + (long) addedSrcSize * dstArrayState.objectsCount() < (long) srcArrayState.objectsCount() * dstArrayState.objectsCount()) {
                /*
                 * We process some elements twice. But overall, we estimate to process fewer total
                 * elements. It is only an estimate because we look at the size of the type state,
                 * which can contain AnalysisObjects that are immediately filtered out in the nested
                 * loops of processStates().
                 */
                processStates(bb, srcArrayState, addedDstState);
                processStates(bb, addedSrcState, dstArrayState);
            } else {
                processStates(bb, srcArrayState, dstArrayState);
            }
        }

        lastSrc = srcArrayState;
        lastDst = dstArrayState;
    }

    private static void processStates(PointsToAnalysis bb, TypeState srcArrayState, TypeState dstArrayState) {
        bb.analysisPolicy().processArrayCopyStates(bb, srcArrayState, dstArrayState);
    }

    @Override
    public boolean addState(PointsToAnalysis bb, TypeState add) {
        throw AnalysisError.shouldNotReachHere("Adding state to an ArrayCopyTypeFlow.");
    }

    public TypeFlow<?> source() {
        return srcArrayFlow;
    }

    public TypeFlow<?> destination() {
        return dstArrayFlow;
    }

    @Override
    public String toString() {
        return "ArrayCopyTypeFlow<" + getStateDescription() + ">";
    }
}
