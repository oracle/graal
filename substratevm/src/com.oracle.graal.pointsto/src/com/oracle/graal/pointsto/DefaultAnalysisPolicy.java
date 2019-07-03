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
import java.util.List;

import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.options.OptionValues;

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
            return new UnifiedArrayElementsTypeStore(object);
        } else {
            return null;
        }
    }

    @Override
    public AbstractVirtualInvokeTypeFlow createVirtualInvokeTypeFlow(Invoke invoke, MethodCallTargetNode target,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, BytecodeLocation location) {
        return new DefaultVirtualInvokeTypeFlow(invoke, target, actualParameters, actualReturn, location);
    }

    /** Explicitly context insensitive implementation of the invoke virtual type flow update. */
    private static class DefaultVirtualInvokeTypeFlow extends AbstractVirtualInvokeTypeFlow {

        private TypeState seenReceiverTypes = TypeState.forEmpty();

        protected DefaultVirtualInvokeTypeFlow(Invoke invoke, MethodCallTargetNode target,
                        TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, BytecodeLocation location) {
            super(invoke, target, actualParameters, actualReturn, location);
        }

        protected DefaultVirtualInvokeTypeFlow(BigBang bb, MethodFlowsGraph methodFlows, DefaultVirtualInvokeTypeFlow original) {
            super(bb, methodFlows, original);
        }

        @Override
        public TypeFlow<MethodCallTargetNode> copy(BigBang bb, MethodFlowsGraph methodFlows) {
            return new DefaultVirtualInvokeTypeFlow(bb, methodFlows, this);
        }

        @Override
        public void onObservedUpdate(BigBang bb) {
            assert this.isClone();

            TypeState receiverState = getReceiver().getState();

            if (receiverState.isUnknown()) {
                bb.reportIllegalUnknownUse(graphRef.getMethod(), source, "Illegal: Invoke on UnknownTypeState objects. Invoke: " + this);
                return;
            }

            for (AnalysisType type : receiverState.types()) {
                if (seenReceiverTypes.containsType(type)) {
                    /* Already resolved this type and linked the callee in a previous update. */
                    continue;
                }

                AnalysisMethod method = type.resolveConcreteMethod(getTargetMethod(), getSource().invoke().getContextType());
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
        public Collection<MethodFlowsGraph> getCalleesFlows(BigBang bb) {
            // collect the flow graphs, one for each analysis method, since it is context
            // insensitive
            Collection<AnalysisMethod> callees = getCallees();
            List<MethodFlowsGraph> methodFlowsGraphs = new ArrayList<>(callees.size());
            for (AnalysisMethod method : callees) {
                methodFlowsGraphs.add(method.getTypeFlow().getFlows(bb.contextPolicy().emptyContext()));
            }
            return methodFlowsGraphs;
        }

    }

}
