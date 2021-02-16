/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.graalvm.compiler.options.OptionValues;

import com.oracle.graal.pointsto.flow.AbstractSpecialInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.AbstractVirtualInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.ActualReturnTypeFlow;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.flow.context.AnalysisContext;
import com.oracle.graal.pointsto.flow.context.BytecodeLocation;
import com.oracle.graal.pointsto.flow.context.free.DefaultAnalysisContextPolicy;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.typestore.ArrayElementsTypeStore;
import com.oracle.graal.pointsto.typestore.FieldTypeStore;
import com.oracle.graal.pointsto.typestore.UnifiedArrayElementsTypeStore;
import com.oracle.graal.pointsto.typestore.UnifiedFieldTypeStore;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaConstant;

public class DefaultAnalysisPolicy extends AnalysisPolicy {

    private DefaultAnalysisContextPolicy contextPolicy;

    public DefaultAnalysisPolicy(OptionValues options) {
        super(options);
        this.contextPolicy = new DefaultAnalysisContextPolicy();
    }

    @Override
    public DefaultAnalysisContextPolicy contextPolicy() {
        return contextPolicy;
    }

    @Override
    public boolean needsConstantCache() {
        return false;
    }

    @Override
    public boolean isSummaryObject(AnalysisObject object) {
        /* Context insensitive objects summarize context sensitive objects of the same type. */
        return object.isContextInsensitiveObject();
    }

    @Override
    public boolean isMergingEnabled() {
        // by default no merging is necessary
        return false;
    }

    @Override
    public void noteMerge(BigBang bb, TypeState t) {
        // nothing to do
    }

    @Override
    public void noteMerge(BigBang bb, AnalysisObject... a) {
        // nothing to do
    }

    @Override
    public void noteMerge(BigBang bb, AnalysisObject a) {
        // nothing to do
    }

    @Override
    public boolean isContextSensitiveAllocation(BigBang bb, AnalysisType type, AnalysisContext allocationContext) {
        return false;
    }

    @Override
    public AnalysisObject createHeapObject(BigBang bb, AnalysisType type, BytecodeLocation allocationSite, AnalysisContext allocationContext) {
        return type.getContextInsensitiveAnalysisObject();
    }

    @Override
    public AnalysisObject createConstantObject(BigBang bb, JavaConstant constant, AnalysisType exactType) {
        return exactType.getContextInsensitiveAnalysisObject();
    }

    @Override
    public BytecodeLocation createAllocationSite(BigBang bb, int bci, AnalysisMethod method) {
        return BytecodeLocation.create(bci, method);
    }

    @Override
    public FieldTypeStore createFieldTypeStore(AnalysisObject object, AnalysisField field, AnalysisUniverse universe) {
        return new UnifiedFieldTypeStore(field, object);
    }

    @Override
    public ArrayElementsTypeStore createArrayElementsTypeStore(AnalysisObject object, AnalysisUniverse universe) {
        if (object.type().isArray()) {
            if (aliasArrayTypeFlows) {
                /* Alias all array type flows using the elements type flow model of Object type. */
                if (object.type().getComponentType().isJavaLangObject()) {
                    return new UnifiedArrayElementsTypeStore(object);
                }
                return universe.objectType().getArrayClass().getContextInsensitiveAnalysisObject().getArrayElementsTypeStore();
            }
            return new UnifiedArrayElementsTypeStore(object);
        } else {
            return null;
        }
    }

    @Override
    public AbstractVirtualInvokeTypeFlow createVirtualInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, AnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, BytecodeLocation location) {
        return new DefaultVirtualInvokeTypeFlow(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, location);
    }

    @Override
    public AbstractSpecialInvokeTypeFlow createSpecialInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, AnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, BytecodeLocation location) {
        return new DefaultSpecialInvokeTypeFlow(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, location);
    }

    /** Explicitly context insensitive implementation of the invoke virtual type flow update. */
    private static class DefaultVirtualInvokeTypeFlow extends AbstractVirtualInvokeTypeFlow {

        private TypeState seenReceiverTypes = TypeState.forEmpty();

        protected DefaultVirtualInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, AnalysisMethod targetMethod,
                        TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, BytecodeLocation location) {
            super(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, location);
        }

        protected DefaultVirtualInvokeTypeFlow(BigBang bb, MethodFlowsGraph methodFlows, DefaultVirtualInvokeTypeFlow original) {
            super(bb, methodFlows, original);
        }

        @Override
        public TypeFlow<BytecodePosition> copy(BigBang bb, MethodFlowsGraph methodFlows) {
            return new DefaultVirtualInvokeTypeFlow(bb, methodFlows, this);
        }

        @Override
        public void onObservedUpdate(BigBang bb) {
            assert this.isClone() || this.isContextInsensitive();
            if (isSaturated()) {
                /* The receiver can saturate while the invoke update was waiting to be scheduled. */
                return;
            }
            TypeState receiverState = getReceiver().getState();

            if (receiverState.isUnknown()) {
                bb.reportIllegalUnknownUse(graphRef.getMethod(), source, "Illegal: Invoke on UnknownTypeState objects. Invoke: " + this);
                return;
            }
            if (!isContextInsensitive()) {
                /*
                 * The context insensitive invoke receiver doesn't need any filtering, the invoke is
                 * directly linked to its receiver type.
                 */
                receiverState = filterReceiverState(bb, receiverState);
            }

            for (AnalysisType type : receiverState.types()) {
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

                AnalysisMethod method = type.resolveConcreteMethod(getTargetMethod());
                if (method == null || Modifier.isAbstract(method.getModifiers())) {
                    /*
                     * Type states can be conservative, i.e., we can have receiver types that do not
                     * implement the method. Just ignore such types.
                     */
                    continue;
                }

                assert !Modifier.isAbstract(method.getModifiers());

                MethodTypeFlow callee = method.getTypeFlow();
                MethodFlowsGraph calleeFlows = callee.addContext(bb, bb.contextPolicy().emptyContext(), this);

                assert callee.getContexts()[0] == bb.contextPolicy().emptyContext();

                /*
                 * Different receiver type can yield the same target method; although it is correct
                 * in a context insensitive analysis to link the callee only if it was not linked
                 * before, in a context sensitive analysis the callee should be linked for each
                 * different context.
                 */
                if (addCallee(callee.getMethod())) {
                    linkCallee(bb, false, calleeFlows);
                }

                updateReceiver(bb, calleeFlows, TypeState.forExactType(bb, type, false));
            }

            /* Remember the types we have already linked. */
            seenReceiverTypes = receiverState;
        }

        @Override
        public void onObservedSaturated(BigBang bb, TypeFlow<?> observed) {
            assert this.isClone() && !this.isContextInsensitive();

            setSaturated();

            /*
             * The receiver object flow of the invoke operation is saturated; it will stop sending
             * notificatons. Swap the invoke flow with the unique, context-insensitive invoke flow
             * corresponding to the target method, which is already registered as an observer for
             * the type flow of the receiver type and therefore saturated. This is a conservative
             * approximation and this invoke will reach all possible callees.
             */

            /* Deregister the invoke as an observer of the receiver. */
            getReceiver().removeObserver(this);

            /* Unlink all callees. */
            for (AnalysisMethod callee : super.getCallees()) {
                MethodFlowsGraph calleeFlows = callee.getTypeFlow().getFlows(bb.contextPolicy().emptyContext());
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
                if (actualReturn != null && calleeFlows.getResult() != null) {
                    calleeFlows.getResult().removeUse(actualReturn);
                }
            }

            /* Link the saturated invoke. */
            AbstractVirtualInvokeTypeFlow contextInsensitiveInvoke = (AbstractVirtualInvokeTypeFlow) targetMethod.initAndGetContextInsensitiveInvoke(bb, source);
            contextInsensitiveInvoke.addInvokeLocation(getSource());

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
        public final Collection<AnalysisMethod> getCallees() {
            if (isSaturated()) {
                return targetMethod.getContextInsensitiveInvoke().getCallees();
            } else {
                return super.getCallees();
            }
        }

        @Override
        public Collection<MethodFlowsGraph> getCalleesFlows(BigBang bb) {
            // collect the flow graphs, one for each analysis method, since it is context
            // insensitive
            Collection<AnalysisMethod> calleesList = getCallees();
            List<MethodFlowsGraph> methodFlowsGraphs = new ArrayList<>(calleesList.size());
            for (AnalysisMethod method : calleesList) {
                methodFlowsGraphs.add(method.getTypeFlow().getFlows(bb.contextPolicy().emptyContext()));
            }
            return methodFlowsGraphs;
        }

    }

    private static final class DefaultSpecialInvokeTypeFlow extends AbstractSpecialInvokeTypeFlow {

        MethodFlowsGraph calleeFlows = null;

        DefaultSpecialInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, AnalysisMethod targetMethod,
                        TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, BytecodeLocation location) {
            super(invokeLocation, receiverType, targetMethod, actualParameters, actualReturn, location);
        }

        private DefaultSpecialInvokeTypeFlow(BigBang bb, MethodFlowsGraph methodFlows, DefaultSpecialInvokeTypeFlow original) {
            super(bb, methodFlows, original);
        }

        @Override
        public TypeFlow<BytecodePosition> copy(BigBang bb, MethodFlowsGraph methodFlows) {
            return new DefaultSpecialInvokeTypeFlow(bb, methodFlows, this);
        }

        @Override
        public void onObservedUpdate(BigBang bb) {
            assert this.isClone();
            /* The receiver state has changed. Process the invoke. */

            /*
             * If this is the first time the invoke is updated then set the callee and link the
             * calee's type flows. If this invoke is never updated then the callee will never be
             * set, therefore the callee will be unreachable from this call site.
             */
            initCallee();
            if (calleeFlows == null) {
                calleeFlows = callee.addContext(bb, bb.contextPolicy().emptyContext(), this);
                linkCallee(bb, false, calleeFlows);
            }

            /*
             * Every time the actual receiver state changes in the caller the formal receiver state
             * needs to be updated as there is no direct update link between actual and formal
             * receivers.
             */
            TypeState invokeState = filterReceiverState(bb, getReceiver().getState());
            updateReceiver(bb, calleeFlows, invokeState);
        }

        @Override
        public Collection<MethodFlowsGraph> getCalleesFlows(BigBang bb) {
            if (callee == null) {
                /* This static invoke was not updated. */
                return Collections.emptyList();
            } else {
                MethodFlowsGraph methodFlows = callee.getFlows(bb.contextPolicy().emptyContext());
                return Collections.singletonList(methodFlows);
            }
        }
    }

}
