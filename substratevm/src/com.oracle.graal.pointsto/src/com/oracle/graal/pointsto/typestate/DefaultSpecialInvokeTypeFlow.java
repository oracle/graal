/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.typestate;

import java.util.Collection;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.AbstractSpecialInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.ActualReturnTypeFlow;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.MethodFlowsGraphInfo;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.util.LightImmutableCollection;
import com.oracle.svm.common.meta.MultiMethod.MultiMethodKey;

import jdk.vm.ci.code.BytecodePosition;

final class DefaultSpecialInvokeTypeFlow extends AbstractSpecialInvokeTypeFlow {

    private boolean calleesLinked = false;
    private final boolean isDeoptInvokeTypeFlow;

    DefaultSpecialInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, PointsToAnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, MultiMethodKey callerMultiMethodKey) {
        this(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, callerMultiMethodKey, false);
    }

    DefaultSpecialInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, PointsToAnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, MultiMethodKey callerMultiMethodKey, boolean isDeoptInvokeTypeFlow) {
        super(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, callerMultiMethodKey);
        this.isDeoptInvokeTypeFlow = isDeoptInvokeTypeFlow;
    }

    @Override
    public void onObservedUpdate(PointsToAnalysis bb) {
        assert !isSaturated() : this;

        /*
         * Filter types not compatible with the receiver type and determine which types have been
         * added.
         */
        TypeState receiverState = filterReceiverState(bb, getReceiver().getState());
        if (seenReceiverTypes.equals(receiverState)) {
            // No new types have been added - nothing to do
            return;
        }

        /* The receiver state has changed. */
        seenReceiverTypes = receiverState;
        if (receiverState.isNull()) {
            // no types have been recorded
            return;
        }

        /* Process the invoke. */

        /*
         * If this is the first time the invoke is updated then set the callee and link the callee's
         * type flows. If this invoke is never updated then the callee will never be set, therefore
         * the callee will be unreachable from this call site.
         */
        initializeCallees(bb);
        LightImmutableCollection.forEach(this, CALLEES_ACCESSOR, (PointsToAnalysisMethod callee) -> {
            MethodFlowsGraphInfo calleeFlows = callee.getTypeFlow().getOrCreateMethodFlowsGraphInfo(bb, this);
            assert calleeFlows.getMethod().equals(callee) : callee;

            if (!calleesLinked) {
                linkCallee(bb, false, calleeFlows);
            }

            /*
             * Every time the actual receiver state changes in the caller the formal receiver state
             * needs to be updated as there is no direct update link between actual and formal
             * receivers.
             *
             * See InvokeTypeFlow#linkCallee for a more thorough explanation of the receiver
             * linking.
             */
            updateReceiver(bb, calleeFlows, receiverState);
        });
        calleesLinked = true;
    }

    @Override
    public Collection<MethodFlowsGraph> getAllNonStubCalleesFlows(PointsToAnalysis bb) {
        return DefaultInvokeTypeFlowUtil.getAllNonStubCalleesFlows(this);
    }

    @Override
    public boolean isDeoptInvokeTypeFlow() {
        return isDeoptInvokeTypeFlow;
    }
}
