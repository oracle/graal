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
package com.oracle.graal.pointsto.flow.context.bytecode;

import static jdk.vm.ci.common.JVMCIError.guarantee;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.stream.Collectors;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.AbstractStaticInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.ActualReturnTypeFlow;
import com.oracle.graal.pointsto.flow.CallSiteSensitiveMethodTypeFlow;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.MethodFlowsGraphClone;
import com.oracle.graal.pointsto.flow.MethodFlowsGraphInfo;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.flow.context.AnalysisContext;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.util.LightImmutableCollection;
import com.oracle.svm.common.meta.MultiMethod.MultiMethodKey;

import jdk.vm.ci.code.BytecodePosition;

final class BytecodeSensitiveStaticInvokeTypeFlow extends AbstractStaticInvokeTypeFlow {

    /**
     * Contexts of the resolved method.
     */
    @SuppressWarnings("unused") private volatile Object calleesFlows;

    private static final AtomicReferenceFieldUpdater<BytecodeSensitiveStaticInvokeTypeFlow, Object> CALLEES_FLOWS_ACCESSOR = AtomicReferenceFieldUpdater.newUpdater(
                    BytecodeSensitiveStaticInvokeTypeFlow.class, Object.class,
                    "calleesFlows");

    /**
     * Context of the caller.
     */
    private AnalysisContext callerContext;

    BytecodeSensitiveStaticInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, PointsToAnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, MultiMethodKey callerMultiMethodKey) {
        super(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, callerMultiMethodKey);
    }

    private BytecodeSensitiveStaticInvokeTypeFlow(PointsToAnalysis bb, MethodFlowsGraph methodFlows, BytecodeSensitiveStaticInvokeTypeFlow original) {
        super(bb, methodFlows, original);
        this.callerContext = ((MethodFlowsGraphClone) methodFlows).context();
    }

    @Override
    public TypeFlow<BytecodePosition> copy(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
        return new BytecodeSensitiveStaticInvokeTypeFlow(bb, methodFlows, this);
    }

    @Override
    public boolean needsInitialization() {
        return true;
    }

    @Override
    public void initFlow(PointsToAnalysis bb) {
        /* Trigger the update for static invokes, there is no receiver to trigger it. */
        if (isClone() && isFlowEnabled()) {
            bb.postFlow(this);
        }
    }

    @Override
    public void update(PointsToAnalysis bb) {
        assert isFlowEnabled() : "The linking should only be triggered for enabled flows: " + this;
        assert isClone() : "Only clones should be updated: " + this;
        /* The static invokes should be updated only once and the callee should be null. */
        guarantee(LightImmutableCollection.isEmpty(this, CALLEES_ACCESSOR), "static invoke updated multiple times!");

        // Unlinked methods can not be parsed
        if (!targetMethod.getWrapped().getDeclaringClass().isLinked()) {
            return;
        }

        /*
         * Initialize the callee lazily so that if the invoke flow is not reached in this context,
         * i.e. for this clone, there is no callee linked/
         */
        initializeCallees(bb);
        PointsToAnalysisMethod singleMethod = LightImmutableCollection.toSingleElement(this, CALLEES_ACCESSOR);
        if (singleMethod != null) {
            LightImmutableCollection.initializeNonEmpty(this, CALLEES_FLOWS_ACCESSOR, getCalleeFlow(bb, singleMethod));
        } else {
            Collection<PointsToAnalysisMethod> collection = LightImmutableCollection.toCollection(this, CALLEES_ACCESSOR);
            var flows = collection.stream().map(callee -> getCalleeFlow(bb, callee)).collect(Collectors.toUnmodifiableSet());
            LightImmutableCollection.initializeNonEmpty(this, CALLEES_FLOWS_ACCESSOR, flows);
        }
    }

    private MethodFlowsGraph getCalleeFlow(PointsToAnalysis bb, PointsToAnalysisMethod callee) {
        MethodTypeFlow calleeTypeFlow = callee.getTypeFlow();

        AnalysisContext calleeContext = BytecodeSensitiveAnalysisPolicy.contextPolicy(bb).staticCalleeContext(bb, source, (BytecodeAnalysisContext) callerContext, calleeTypeFlow);
        MethodFlowsGraphInfo calleeFlows = ((CallSiteSensitiveMethodTypeFlow) calleeTypeFlow).addContext(bb, calleeContext, this);

        linkCallee(bb, true, calleeFlows);

        return (MethodFlowsGraph) calleeFlows;
    }

    @Override
    public Collection<MethodFlowsGraph> getAllNonStubCalleesFlows(PointsToAnalysis bb) {
        return LightImmutableCollection.toCollection(this, CALLEES_FLOWS_ACCESSOR);
    }
}
