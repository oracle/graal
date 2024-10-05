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

import static jdk.vm.ci.common.JVMCIError.guarantee;

import java.util.Collection;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.AbstractStaticInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.ActualReturnTypeFlow;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.MethodFlowsGraphInfo;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.util.LightImmutableCollection;
import com.oracle.svm.common.meta.MultiMethod.MultiMethodKey;

import jdk.vm.ci.code.BytecodePosition;

final class DefaultStaticInvokeTypeFlow extends AbstractStaticInvokeTypeFlow {
    private final boolean isDeoptInvokeTypeFlow;

    DefaultStaticInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, PointsToAnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, MultiMethodKey callerMultiMethodKey) {
        this(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, callerMultiMethodKey, false);
    }

    DefaultStaticInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, PointsToAnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, MultiMethodKey callerMultiMethodKey, boolean isDeoptInvokeTypeFlow) {
        super(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, callerMultiMethodKey);
        this.isDeoptInvokeTypeFlow = isDeoptInvokeTypeFlow;
    }

    @Override
    protected void onFlowEnabled(PointsToAnalysis bb) {
        bb.postTask(() -> update(bb));
    }

    @Override
    public void update(PointsToAnalysis bb) {
        assert isFlowEnabled() : "The linking should only be triggered for enabled flows: " + this;
        /* The static invokes should be updated only once and the callee should be null. */
        guarantee(LightImmutableCollection.isEmpty(this, CALLEES_ACCESSOR), "Static invoke updated multiple times, source %s, target method %s", getSource(), targetMethod);

        // Unlinked methods can not be parsed
        if (!targetMethod.getWrapped().getDeclaringClass().isLinked()) {
            return;
        }

        /*
         * Initialize the callee lazily so that if the invoke flow is not reached in this context,
         * i.e. for this clone, there is no callee linked/
         */
        initializeCallees(bb);

        LightImmutableCollection.forEach(this, CALLEES_ACCESSOR, (PointsToAnalysisMethod callee) -> {
            MethodFlowsGraphInfo calleeFlows = callee.getTypeFlow().getOrCreateMethodFlowsGraphInfo(bb, this);
            linkCallee(bb, true, calleeFlows);
        });
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
