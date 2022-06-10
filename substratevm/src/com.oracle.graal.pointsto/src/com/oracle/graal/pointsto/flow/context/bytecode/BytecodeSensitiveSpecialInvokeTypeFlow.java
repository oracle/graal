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

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.AbstractSpecialInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.ActualReturnTypeFlow;
import com.oracle.graal.pointsto.flow.CallSiteSensitiveMethodTypeFlow;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.MethodFlowsGraphClone;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.flow.context.AnalysisContext;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.typestate.TypeState;

import jdk.vm.ci.code.BytecodePosition;

final class BytecodeSensitiveSpecialInvokeTypeFlow extends AbstractSpecialInvokeTypeFlow {

    /**
     * Contexts of the resolved method.
     */
    private final ConcurrentMap<MethodFlowsGraph, Object> calleesFlows = new ConcurrentHashMap<>(4, 0.75f, 1);

    /**
     * Context of the caller.
     */
    private AnalysisContext callerContext;

    BytecodeSensitiveSpecialInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, PointsToAnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn) {
        super(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn);
    }

    private BytecodeSensitiveSpecialInvokeTypeFlow(PointsToAnalysis bb, MethodFlowsGraph methodFlows, BytecodeSensitiveSpecialInvokeTypeFlow original) {
        super(bb, methodFlows, original);
        this.callerContext = ((MethodFlowsGraphClone) methodFlows).context();
    }

    @Override
    public TypeFlow<BytecodePosition> copy(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
        return new BytecodeSensitiveSpecialInvokeTypeFlow(bb, methodFlows, this);
    }

    @Override
    public void onObservedUpdate(PointsToAnalysis bb) {
        /* The receiver state has changed. Process the invoke. */

        initCallee();

        TypeState invokeState = filterReceiverState(bb, getReceiver().getState());
        for (AnalysisObject receiverObject : invokeState.objects(bb)) {
            AnalysisContext calleeContext = BytecodeSensitiveAnalysisPolicy.contextPolicy(bb).calleeContext(bb, receiverObject, (BytecodeAnalysisContext) callerContext, callee);
            MethodFlowsGraph calleeFlows = ((CallSiteSensitiveMethodTypeFlow) callee).addContext(bb, calleeContext, this);

            if (calleesFlows.putIfAbsent(calleeFlows, Boolean.TRUE) == null) {
                linkCallee(bb, false, calleeFlows);
            }

            updateReceiver(bb, calleeFlows, receiverObject);
        }
    }

    @Override
    public Collection<MethodFlowsGraph> getCalleesFlows(PointsToAnalysis bb) {
        return new ArrayList<>(calleesFlows.keySet());
    }
}
