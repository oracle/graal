/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.fieldfolding;

import java.util.Arrays;

import com.oracle.graal.pointsto.flow.AnalysisParsedGraph.Stage;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ameta.FieldValueInterceptionSupport;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.extended.StateSplitProxyNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.phases.BasePhase;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Performs the constant folding of fields that are optimizable.
 *
 * This optimization phase will request the parsed graph for stage {@link Stage#BYTECODE_PARSED}
 * which means that it MUST BE applied after this stage. The earliest point in time this may be
 * applied is in parsing stage {@link Stage#AFTER_PARSING_HOOKS_DONE}. Any later application is also
 * valid.
 */
public final class StaticFinalFieldFoldingPhase extends BasePhase<CoreProviders> {

    private final FieldValueInterceptionSupport fieldValueInterceptionSupport;
    private final StaticFinalFieldFoldingFeature feature;

    public StaticFinalFieldFoldingPhase() {
        assert StaticFinalFieldFoldingFeature.isAvailable();
        this.feature = StaticFinalFieldFoldingFeature.singleton();
        this.fieldValueInterceptionSupport = FieldValueInterceptionSupport.singleton();
    }

    public static boolean isEnabled() {
        return StaticFinalFieldFoldingFeature.isAvailable();
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        if (feature == null) {
            return;
        }

        for (Node node : graph.getNodes()) {
            if (node instanceof LoadFieldNode loadFieldNode) {
                handleLoadFieldNode(graph, context.getMetaAccess(), loadFieldNode);
            }
        }
    }

    private void handleLoadFieldNode(StructuredGraph graph, MetaAccessProvider metaAccess, LoadFieldNode loadFieldNode) {
        ResolvedJavaField field = loadFieldNode.field();
        if (!field.isStatic() || !field.isFinal()) {
            return;
        }

        AnalysisField aField = StaticFinalFieldFoldingFeature.toAnalysisField(field);
        AnalysisMethod definingClassInitializer = aField.getDeclaringClass().getClassInitializer();
        if (!StaticFinalFieldFoldingFeature.isOptimizationCandidate(aField, definingClassInitializer, fieldValueInterceptionSupport)) {
            return;
        }

        assert StaticFinalFieldFoldingFeature.isAllowedTargetMethod(graph.method());

        boolean inClassInitializer = graph.method().isClassInitializer();

        /*
         * IMPORTANT: In order to avoid deadlocks when classes have cyclic dependencies, we may only
         * request the first stage graph if we are optimizing field loads in a class initializer.
         * Otherwise, we prefer the later stage because this may have more fields for constant
         * folding.
         */
        Stage stage = inClassInitializer ? Stage.BYTECODE_PARSED : Stage.AFTER_PARSING_HOOKS_DONE;

        /*
         * The foldable field values are collected after the first parsing stage of the class
         * initializer. If the class initializer is not parsed yet, parsing needs to be forced so
         * that {@link StaticFinalFieldFoldingFeature#onAnalysisMethodParsed} determines which
         * fields can be optimized.
         *
         * IMPORTANT: Never request parsing if the defining and using method is the same class
         * initializer. This would end up in a recursive parsing request.
         */
        if (!inClassInitializer || !graph.method().equals(definingClassInitializer)) {
            definingClassInitializer.ensureGraphParsed(feature.bb, stage);
        }

        /*
         * If this node is a candidate for static final field folding, the
         * StaticFinalFieldFoldingNodePlugin already inserted a StateSplitProxyNode that we can use.
         */
        VMError.guarantee(loadFieldNode.next() instanceof StateSplitProxyNode, "missing StateSplitProxy");
        StateSplitProxyNode stateSplitProxyNode = (StateSplitProxyNode) loadFieldNode.next();

        /*
         * Query the folded field value for the AnalysisField. To ensure deterministic image builds,
         * the value is queried for the stage this phase is currently executed in . Otherwise, the
         * results could vary depending on whether the declaring class initializer was already
         * parsed before or not.
         */
        JavaConstant initializedValue = feature.getFoldedFieldValue(stage, aField);
        if (initializedValue == null) {
            /* Field cannot be optimized. Remove the StateSplitProxyNode. */
            FrameState frameState = stateSplitProxyNode.stateAfter();
            stateSplitProxyNode.setStateAfter(null);
            frameState.safeDelete();
            stateSplitProxyNode.replaceAtUsages(loadFieldNode);
            graph.removeFixed(stateSplitProxyNode);
            return;
        }

        /*
         * Create an if-else structure with a PhiNode that either has the optimized value of the
         * field, or the uninitialized value. The initialization status array and the index into
         * that array are not known yet during bytecode parsing, so the array access will be created
         * lazily.
         */
        var fieldCheckStatusNode = graph.add(new IsStaticFinalFieldInitializedNode(field));
        graph.addBeforeFixed(loadFieldNode, fieldCheckStatusNode);
        ConstantNode falseConst = graph.addOrUnique(ConstantNode.forBoolean(false));
        LogicNode isUninitializedNode = graph.addOrUnique(IntegerEqualsNode.create(fieldCheckStatusNode, falseConst, NodeView.DEFAULT));

        JavaConstant uninitializedValue = aField.getConstantValue();
        if (uninitializedValue == null) {
            uninitializedValue = JavaConstant.defaultForKind(aField.getStorageKind());
        }
        ConstantNode uninitializedValueNode = graph.addOrUnique(ConstantNode.forConstant(uninitializedValue, metaAccess));
        ConstantNode initializedValueNode = graph.addOrUnique(ConstantNode.forConstant(initializedValue, metaAccess));

        EndNode uninitializedEndNode = graph.add(new EndNode());
        EndNode initializedEndNode = graph.add(new EndNode());
        IfNode add = graph.add(new IfNode(isUninitializedNode, uninitializedEndNode, initializedEndNode, BranchProbabilityNode.EXTREMELY_SLOW_PATH_PROFILE));
        loadFieldNode.replaceAtPredecessor(add);

        MergeNode merge = graph.add(new MergeNode());
        merge.addForwardEnd(uninitializedEndNode);
        merge.addForwardEnd(initializedEndNode);

        ConstantNode[] phiValueNodes = {uninitializedValueNode, initializedValueNode};
        ValuePhiNode phi = graph.addOrUnique(new ValuePhiNode(StampTool.meet(Arrays.asList(phiValueNodes)), merge, phiValueNodes));

        stateSplitProxyNode.replaceAtUsages(phi);

        merge.setStateAfter(stateSplitProxyNode.stateAfter());
        stateSplitProxyNode.setStateAfter(null);

        loadFieldNode.replaceAtUsages(phi);
        /*
         * Connect the MergeNode to LoadField's successor. The predecessor and the usages were
         * already handled. This also deletes the LoadField node.
         */
        graph.replaceFixedWithFixed(loadFieldNode, merge);
        assert !loadFieldNode.isAlive();

        graph.removeFixed(stateSplitProxyNode);
        assert !stateSplitProxyNode.isAlive();
    }
}
