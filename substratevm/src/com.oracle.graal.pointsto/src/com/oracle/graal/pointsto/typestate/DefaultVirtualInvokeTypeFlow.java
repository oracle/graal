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

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.flow.AbstractVirtualInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.ActualReturnTypeFlow;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.MethodFlowsGraphInfo;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.svm.common.meta.MultiMethod.MultiMethodKey;

import jdk.vm.ci.code.BytecodePosition;

/**
 * Explicitly context insensitive implementation of the invoke virtual type flow update.
 */
final class DefaultVirtualInvokeTypeFlow extends AbstractVirtualInvokeTypeFlow {

    private TypeState seenReceiverTypes = TypeState.forEmpty();

    DefaultVirtualInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, PointsToAnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, MultiMethodKey callerMultiMethodKey) {
        super(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, callerMultiMethodKey);
    }

    @Override
    public void onObservedUpdate(PointsToAnalysis bb) {
        if (isSaturated()) {
            /* The receiver can saturate while the invoke update was waiting to be scheduled. */
            return;
        }
        TypeState receiverState = getReceiver().getState();
        if (!isContextInsensitive()) {
            /*
             * The context insensitive invoke receiver doesn't need any filtering, the invoke is
             * directly linked to its receiver type.
             */
            receiverState = filterReceiverState(bb, receiverState);
        }

        for (AnalysisType type : receiverState.types(bb)) {
            if (isSaturated()) {
                /*-
                 * The receiver can become saturated during the callees linking, which saturates
                 * the invoke, when linking the return flow of callees for code patterns like:
                 *
                 *  Object cur = ...
                 *  while {
                 *      cur = cur.next();
                 *  }
                 */
                return;
            }
            if (seenReceiverTypes.containsType(type)) {
                /* Already resolved this type and linked the callee in a previous update. */
                continue;
            }

            AnalysisMethod method = null;
            try {
                method = type.resolveConcreteMethod(targetMethod);
            } catch (UnsupportedFeatureException ex) {
                /* Register the ex with UnsupportedFeatures and allow analysis to continue. */
                bb.getUnsupportedFeatures().addMessage("resolve_" + targetMethod.format("%H.%n(%p)"), targetMethod, ex.getMessage(), null, ex);
            }

            if (method == null || Modifier.isAbstract(method.getModifiers())) {
                /*
                 * Type states can be conservative, i.e., we can have receiver types that do not
                 * implement the method. Just ignore such types.
                 */
                continue;
            }

            assert !Modifier.isAbstract(method.getModifiers()) : method;

            var calleeList = bb.getHostVM().getMultiMethodAnalysisPolicy().determineCallees(bb, PointsToAnalysis.assertPointsToAnalysisMethod(method), targetMethod, callerMultiMethodKey,
                            this);
            for (PointsToAnalysisMethod callee : calleeList) {
                if (!callee.isOriginalMethod() && allOriginalCallees) {
                    allOriginalCallees = false;
                }
                MethodFlowsGraphInfo calleeFlows = callee.getTypeFlow().getOrCreateMethodFlowsGraphInfo(bb, this);
                assert callee.getTypeFlow().getMethod().equals(callee) : callee;

                /*
                 * Different receiver type can yield the same target method; although it is correct
                 * in a context insensitive analysis to link the callee only if it was not linked
                 * before, in a context sensitive analysis the callee should be linked for each
                 * different context.
                 */
                if (addCallee(callee)) {
                    linkCallee(bb, false, calleeFlows);
                }

                updateReceiver(bb, calleeFlows, TypeState.forExactType(bb, type, false));
            }
        }

        /* Remember the types we have already seen. */
        seenReceiverTypes = receiverState;
    }

    @Override
    public void onObservedSaturated(PointsToAnalysis bb, TypeFlow<?> observed) {
        /* Eagerly ensure context insensitive invoke is created before the saturated flag is set. */
        AbstractVirtualInvokeTypeFlow contextInsensitiveInvoke = (AbstractVirtualInvokeTypeFlow) targetMethod.initAndGetContextInsensitiveInvoke(bb, source, false, callerMultiMethodKey);
        contextInsensitiveInvoke.addInvokeLocation(getSource());

        setSaturated();

        /*
         * The receiver object flow of the invoke operation is saturated; it will stop sending
         * notifications. Swap the invoke flow with the unique, context-insensitive invoke flow
         * corresponding to the target method, which is already registered as an observer for the
         * type flow of the receiver type and therefore saturated. This is a conservative
         * approximation and this invoke will reach all possible callees.
         */

        /* Deregister the invoke as an observer of the receiver. */
        getReceiver().removeObserver(this);

        /* Unlink all callees. */
        for (AnalysisMethod callee : super.getAllCallees()) {
            MethodFlowsGraphInfo calleeFlows = PointsToAnalysis.assertPointsToAnalysisMethod(callee).getTypeFlow().getMethodFlowsGraphInfo();
            /* Iterate over the actual parameters in caller context. */
            for (int i = 0; i < actualParameters.length; i++) {
                /* Get the formal parameter from the callee. */
                TypeFlow<?> formalParam = calleeFlows.getParameter(i);
                /* Remove the link between the actual and the formal parameters. */
                if (actualParameters[i] != null && formalParam != null) {
                    actualParameters[i].removeUse(formalParam);
                }
            }
            /* Remove the link between the formal and the actual return, if present. */
            if (actualReturn != null && calleeFlows.getReturnFlow() != null) {
                calleeFlows.getReturnFlow().removeUse(actualReturn);
            }
        }

        /*
         * Link the call site actual parameters to the saturated invoke actual parameters. The
         * receiver is already set in the saturated invoke.
         */
        for (int i = 1; i < actualParameters.length; i++) {
            /* Primitive type parameters are not modeled, hence null. */
            if (actualParameters[i] != null) {
                actualParameters[i].addUse(bb, contextInsensitiveInvoke.getActualParameter(i));
            }
        }
        if (actualReturn != null) {
            /* Link the actual return. */
            contextInsensitiveInvoke.getActualReturn().addUse(bb, actualReturn);
        }
    }

    @Override
    public void setSaturated() {
        super.setSaturated();
        if (this.isClone()) {
            /*
             * If this is a clone, mark the original as saturated too such that
             * originalInvoke.getCallees() is redirected to the context-insensitive invoke.
             */
            originalInvoke.setSaturated();
        }
    }

    @Override
    public Collection<AnalysisMethod> getOriginalCallees() {
        if (isSaturated()) {
            return targetMethod.getContextInsensitiveVirtualInvoke(callerMultiMethodKey).getOriginalCallees();
        } else {
            return super.getOriginalCallees();
        }
    }

    @Override
    public Collection<AnalysisMethod> getAllCallees() {
        if (isSaturated()) {
            return targetMethod.getContextInsensitiveVirtualInvoke(callerMultiMethodKey).getAllCallees();
        } else {
            return super.getAllCallees();
        }
    }

    @Override
    public Collection<AnalysisMethod> getCalleesForReturnLinking() {
        if (isSaturated()) {
            /* If the invoke has saturated, then it is not necessary to link the callees. */
            return Collections.emptyList();
        } else {
            return super.getAllCallees();
        }
    }

    @Override
    public Collection<MethodFlowsGraph> getAllNonStubCalleesFlows(PointsToAnalysis bb) {
        return DefaultInvokeTypeFlowUtil.getAllNonStubCalleesFlows(this);
    }
}
