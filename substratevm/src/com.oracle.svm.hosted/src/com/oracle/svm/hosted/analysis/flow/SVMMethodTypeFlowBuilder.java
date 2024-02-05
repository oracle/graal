/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.analysis.flow;

import com.oracle.graal.pointsto.AbstractAnalysisEngine;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.MethodTypeFlowBuilder;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.flow.builder.TypeFlowBuilder;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.svm.core.graal.nodes.InlinedInvokeArgumentsNode;
import com.oracle.svm.core.graal.thread.CompareAndSetVMThreadLocalNode;
import com.oracle.svm.core.graal.thread.StoreVMThreadLocalNode;
import com.oracle.svm.core.util.UserError.UserException;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.ameta.FieldValueInterceptionSupport;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.vm.ci.code.BytecodePosition;

public class SVMMethodTypeFlowBuilder extends MethodTypeFlowBuilder {

    private final boolean addImplicitNullCheckFilters;

    public SVMMethodTypeFlowBuilder(PointsToAnalysis bb, PointsToAnalysisMethod method, MethodFlowsGraph flowsGraph, MethodFlowsGraph.GraphKind graphKind) {
        super(bb, method, flowsGraph, graphKind);
        /*
         * We only add these filters for runtime compiled methods, as other multi-method variants
         * require explicit null checks.
         */
        addImplicitNullCheckFilters = SubstrateCompilationDirectives.isRuntimeCompiledMethod(method);
    }

    protected SVMHost getHostVM() {
        return (SVMHost) bb.getHostVM();
    }

    @SuppressWarnings("serial")
    public static class UnsafeOffsetError extends UserException {

        UnsafeOffsetError(String message) {
            super(message);
        }

        static void report(String message) {
            throw new UnsafeOffsetError(message);
        }
    }

    @Override
    protected void checkUnsafeOffset(ValueNode base, ValueNode offsetNode) {
        if (!NativeImageOptions.ThrowUnsafeOffsetErrors.getValue()) {
            /* Skip the checks below. */
            return;
        }

        /*
         * Offset static fields that are initialized at build time and used in unsafe operations
         * need value recomputation. Check that they are properly intercepted. Detection of offset
         * fields that need value re-computation is best effort. For some node types (e.g.,
         * SignExtendNode, LoadFieldNode, InvokeNode, AddNode, ParameterNode, AndNode,
         * ValueProxyNode, LoadIndexedNode) that can be used as an offset in an unsafe operation we
         * cannot determine if the value was properly intercepted or not by simply looking at the
         * node itself.
         *
         * Determining if an offset that comes from a ConstantNode was properly intercepted is not
         * reliable. First the canonicalization in UnsafeAccessNode tries to replace the unsafe
         * access with a field access when the offset is a constant. Thus, we don't actually see the
         * unsafe access node. Second if the field is intercepted but RecomputeFieldValue.isFinal is
         * set to true then the recomputed value is constant folded, i.e., there is no load of the
         * offset field. Then we cannot determine if the offset was recomputed or not and attempting
         * this check can lead to false positive errors.
         *
         * The only offset node type that we are left with and that we can unequivocally determine
         * if it was properly intercepted or not is LoadFieldNode.
         */

        BytecodePosition pos = AbstractAnalysisEngine.sourcePosition(offsetNode);
        if (offsetNode instanceof LoadFieldNode) {
            LoadFieldNode offsetLoadNode = (LoadFieldNode) offsetNode;
            AnalysisField field = (AnalysisField) offsetLoadNode.field();
            if (field.isStatic() &&
                            getHostVM().getClassInitializationSupport().maybeInitializeAtBuildTime(field.getDeclaringClass()) &&
                            !field.getDeclaringClass().unsafeFieldsRecomputed() &&
                            !FieldValueInterceptionSupport.singleton().hasFieldValueTransformer(field) &&
                            !(base.isConstant() && base.asConstant().isDefaultForKind())) {
                String message = String.format("Field %s is used as an offset in an unsafe operation, but no value recomputation found.%n Wrapped field: %s", field, field.wrapped);
                message += String.format("%n Location: %s", pos);
                UnsafeOffsetError.report(message);
            }
        } else if (NativeImageOptions.ReportUnsafeOffsetWarnings.getValue()) {
            String message = "Offset used in an unsafe operation. Cannot determine if the offset value is recomputed.";
            message += String.format("%nNode class: %s", offsetNode.getClass().getName());
            message += String.format("%n Location: %s", pos);
            if (NativeImageOptions.UnsafeOffsetWarningsAreFatal.getValue()) {
                UnsafeOffsetError.report(message);
            } else {
                System.out.println(message);
            }
        }

    }

    @Override
    protected boolean delegateNodeProcessing(FixedNode n, TypeFlowsOfNodes state) {
        /*
         * LoadVMThreadLocalNode is handled by the default node processing in
         * MethodTypeFlowBuilder.TypeFlowsOfNodes.lookup(), i.e., it creates a source type flow when
         * the node has an exact type. This works with allocation site sensitivity because the
         * StoreVMThreadLocal is modeled by writing the objects to the all-instantiated.
         */
        if (n instanceof StoreVMThreadLocalNode node) {
            storeVMThreadLocal(state, node, node.getValue());
            return true;
        } else if (n instanceof CompareAndSetVMThreadLocalNode node) {
            storeVMThreadLocal(state, node, node.getUpdate());
            return true;
        } else if (n instanceof InlinedInvokeArgumentsNode node) {
            processInlinedInvokeArgumentsNode(state, node);
            return true;
        }
        return super.delegateNodeProcessing(n, state);
    }

    private void storeVMThreadLocal(TypeFlowsOfNodes state, ValueNode storeNode, ValueNode value) {
        Stamp stamp = value.stamp(NodeView.DEFAULT);
        if (stamp instanceof ObjectStamp) {
            /* Add the value object to the state of its declared type. */
            TypeFlowBuilder<?> valueBuilder = state.lookup(value);
            ObjectStamp valueStamp = (ObjectStamp) stamp;
            AnalysisType valueType = (AnalysisType) (valueStamp.type() == null ? bb.getObjectType() : valueStamp.type());

            TypeFlowBuilder<?> storeBuilder = TypeFlowBuilder.create(bb, storeNode, TypeFlow.class, () -> {
                TypeFlow<?> proxy = bb.analysisPolicy().proxy(AbstractAnalysisEngine.sourcePosition(storeNode), valueType.getTypeFlow(bb, false));
                flowsGraph.addMiscEntryFlow(proxy);
                return proxy;
            });
            storeBuilder.addUseDependency(valueBuilder);
            typeFlowGraphBuilder.registerSinkBuilder(storeBuilder);
        }
    }

    private void processInlinedInvokeArgumentsNode(TypeFlowsOfNodes state, InlinedInvokeArgumentsNode node) {
        /*
         * Create a proxy invoke type flow for the inlined method.
         */
        PointsToAnalysisMethod targetMethod = (PointsToAnalysisMethod) node.getInvokeTarget();
        InvokeKind invokeKind = targetMethod.isStatic() ? InvokeKind.Static : InvokeKind.Special;
        processMethodInvocation(state, node, invokeKind, targetMethod, node.getArguments(), true, getInvokePosition(node), true);
    }

    @Override
    protected void processImplicitNonNull(ValueNode node, ValueNode source, TypeFlowsOfNodes state) {
        // GR-49362 - remove after improving non-runtime graphs
        if (addImplicitNullCheckFilters) {
            super.processImplicitNonNull(node, source, state);
        }
    }
}
