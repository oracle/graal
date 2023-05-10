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

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.AbstractVirtualInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.ActualReturnTypeFlow;
import com.oracle.graal.pointsto.flow.CallSiteSensitiveMethodTypeFlow;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.MethodFlowsGraphClone;
import com.oracle.graal.pointsto.flow.MethodFlowsGraphInfo;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.flow.context.AnalysisContext;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.svm.common.meta.MultiMethod.MultiMethodKey;

import jdk.vm.ci.code.BytecodePosition;

/**
 * Bytecode context sensitive implementation of the invoke virtual type flow update.
 * <p>
 * TODO Can we merge the slow path (i.e., this class) and fast path (i.e., the default, context
 * insensitive virtual invoke implementation) to be able to fall back to fast path when context
 * sensitivity is disabled or reaches budget threshold?
 */
final class BytecodeSensitiveVirtualInvokeTypeFlow extends AbstractVirtualInvokeTypeFlow {

    /*
     * Remember all the callee clones that were already linked in each context at this invocation
     * site to avoid redundant relinking. MethodFlows is unique for each method type flow and
     * context combination.
     */
    private final Set<MethodFlowsGraph> calleesFlows = new ConcurrentHashMap<MethodFlowsGraph, Boolean>(4, 0.75f, 1).keySet(Boolean.TRUE);
    private final AnalysisContext callerContext;

    BytecodeSensitiveVirtualInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, PointsToAnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, MultiMethodKey callerMultiMethodKey) {
        super(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, callerMultiMethodKey);
        callerContext = null;
    }

    private BytecodeSensitiveVirtualInvokeTypeFlow(PointsToAnalysis bb, MethodFlowsGraph methodFlows, BytecodeSensitiveVirtualInvokeTypeFlow original) {
        super(bb, methodFlows, original);
        callerContext = ((MethodFlowsGraphClone) methodFlows).context();
    }

    @Override
    public TypeFlow<BytecodePosition> copy(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
        return new BytecodeSensitiveVirtualInvokeTypeFlow(bb, methodFlows, this);
    }

    @Override
    protected void onFlowEnabled(PointsToAnalysis bb) {
        if (isClone()) {
            bb.postTask(() -> onObservedUpdate(bb));
        }
    }

    @Override
    public void onObservedUpdate(PointsToAnalysis bb) {
        assert this.isClone() || this.isContextInsensitive() : this;

        /*
         * Capture the current receiver state before the update. The type state objects are
         * immutable and a later call to getState() can yield a different value.
         */
        TypeState receiverState = getReceiver().getState();
        receiverState = filterReceiverState(bb, receiverState);

        if (receiverState.isEmpty() || receiverState.isNull()) {
            return;
        }

        /* Use the tandem types - objects iterator. */
        TypesObjectsIterator toi = new TypesObjectsIterator(receiverState);
        while (toi.hasNextType()) {
            AnalysisType type = toi.nextType();

            AnalysisMethod method = type.resolveConcreteMethod(getTargetMethod());
            if (method == null || Modifier.isAbstract(method.getModifiers())) {
                /*
                 * Type states can be conservative, i.e., we can have receiver types that do not
                 * implement the method. Just ignore such types.
                 */
                toi.skipObjects(type);
                continue;
            }

            assert !Modifier.isAbstract(method.getModifiers()) : method;

            Collection<PointsToAnalysisMethod> calleeList = bb.getHostVM().getMultiMethodAnalysisPolicy().determineCallees(bb, PointsToAnalysis.assertPointsToAnalysisMethod(method),
                            targetMethod, callerMultiMethodKey, this);
            for (PointsToAnalysisMethod callee : calleeList) {
                if (!callee.isOriginalMethod() && allOriginalCallees) {
                    allOriginalCallees = false;
                }
                CallSiteSensitiveMethodTypeFlow calleeTypeFlow = (CallSiteSensitiveMethodTypeFlow) callee.getTypeFlow();

                while (toi.hasNextObject(type)) {
                    AnalysisObject actualReceiverObject = toi.nextObject(type);

                    // get the context based on the actualReceiverObject
                    AnalysisContext calleeContext = BytecodeSensitiveAnalysisPolicy.contextPolicy(bb).calleeContext(bb, actualReceiverObject, (BytecodeAnalysisContext) callerContext, calleeTypeFlow);

                    MethodFlowsGraphInfo calleeFlows = calleeTypeFlow.addContext(bb, calleeContext, this);

                    if (calleesFlows.add((MethodFlowsGraph) calleeFlows)) {
                        /* register the analysis method as a callee for this invoke */
                        addCallee(calleeFlows.getMethod());
                        /* linkCallee() does not link the receiver object. */
                        linkCallee(bb, false, calleeFlows);
                    }

                    updateReceiver(bb, calleeFlows, actualReceiverObject);
                }
            }

        }
    }

    @Override
    public void onObservedSaturated(PointsToAnalysis bb, TypeFlow<?> observed) {
        /* When the receiver flow saturates start observing the flow of the receiver type. */
        replaceObservedWith(bb, receiverType);
    }

    @Override
    public Collection<MethodFlowsGraph> getAllNonStubCalleesFlows(PointsToAnalysis bb) {
        return calleesFlows;
    }
}
