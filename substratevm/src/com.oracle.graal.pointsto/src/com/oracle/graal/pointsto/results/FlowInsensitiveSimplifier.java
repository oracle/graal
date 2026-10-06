/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.results;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.PointsToAnalysisField;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.nodes.extended.FieldOffsetProvider;
import jdk.graal.compiler.nodes.java.ClassIsAssignableFromNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.phases.common.CanonicalizerPhase.CustomSimplification;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Iteratively simplifies a graph using globally valid reachability and type facts. It deliberately
 * owns no parameter flow or node-to-flow mapping, so canonicalization-created and replacement nodes
 * can be processed safely through the canonicalizer worklist.
 */
final class FlowInsensitiveSimplifier implements CustomSimplification {
    private final StrengthenGraphs strengthenGraphs;
    private final StructuredGraph graph;
    private final GraphStrengtheningSupport strengtheningSupport;
    private final BigBang bb;
    /** Available only for points-to analysis, which provides field sink flows. */
    private final TypeFlowStampStrengthener typeFlowStrengthener;

    /** Creates the repeatable simplifier for one analyzed method graph. */
    FlowInsensitiveSimplifier(StrengthenGraphs strengthenGraphs, AnalysisMethod method, StructuredGraph graph, GraphStrengtheningSupport strengtheningSupport) {
        this.strengthenGraphs = strengthenGraphs;
        this.graph = graph;
        this.strengtheningSupport = strengtheningSupport;
        this.bb = strengthenGraphs.bb;
        if (bb.isPointsToAnalysis()) {
            this.typeFlowStrengthener = new TypeFlowStampStrengthener(strengthenGraphs, (PointsToAnalysis) bb, method, strengtheningSupport);
        } else {
            this.typeFlowStrengthener = null;
        }
    }

    @Override
    public void simplify(Node node, SimplifierTool tool) {
        if (node instanceof ValueNode valueNode) {
            strengtheningSupport.inferAndStrengthenStamp(valueNode, tool);
        }

        if (strengthenGraphs.simplifyFlowInsensitiveDelegate(node, tool)) {
            return;
        }

        switch (node) {
            case LoadFieldNode load -> handleLoadField(load, tool);
            case InstanceOfNode instanceOf -> {
                handleInstanceOf(instanceOf, tool);
                /*
                 * The original can be replaced and deleted above. Its replacement is processed and
                 * assigned a profile through the worklist.
                 */
                if (instanceOf.isAlive()) {
                    strengthenGraphs.maybeAssignInstanceOfProfiles(instanceOf);
                }
            }
            case ClassIsAssignableFromNode assignable -> handleClassIsAssignableFrom(assignable, tool);
            case BytecodeExceptionNode exception -> handleBytecodeException(exception, tool);
            case FrameState frameState -> handleFrameState(frameState);
            case PiNode pi -> handlePi(pi, tool);
            case Invoke invoke -> handleInvoke(invoke, tool);
            default -> {
            }
        }
    }

    /**
     * Applies only the globally valid sink flow of a closed field. Updating the load directly avoids
     * an artificial anchor, but is safe only with a stamp valid for the whole method and all inlined
     * methods because the memory load can float later. An open field may be written from the open
     * world, so its analyzed state cannot be trusted.
     */
    private void handleLoadField(LoadFieldNode node, SimplifierTool tool) {
        if (!bb.isPointsToAnalysis()) {
            /* Reachability analysis does not provide points-to field sink flows. */
            return;
        }
        PointsToAnalysis pointsToAnalysis = (PointsToAnalysis) bb;
        PointsToAnalysisField field = (PointsToAnalysisField) node.field();
        if (!pointsToAnalysis.isClosed(field)) {
            return;
        }
        Object newStampOrConstant = typeFlowStrengthener.strengthenStampFromTypeFlow(node, field.getSinkFlow(), node, tool);
        if (newStampOrConstant instanceof JavaConstant constant) {
            ConstantNode replacement = ConstantNode.forConstant(constant, pointsToAnalysis.getMetaAccess(), graph);
            graph.replaceFixedWithFloating(node, replacement);
            tool.addToWorkList(replacement);
        } else {
            GraphStrengtheningSupport.updateStampInPlace(node, (Stamp) newStampOrConstant, tool);
        }
    }

    /** Refines an {@link InstanceOfNode}'s checked stamp and schedules any replacement. */
    private void handleInstanceOf(InstanceOfNode node, SimplifierTool tool) {
        ObjectStamp oldStamp = node.getCheckedStamp();
        Stamp newStamp = strengtheningSupport.strengthenStamp(oldStamp);
        if (newStamp != null) {
            LogicNode replacement = graph.addOrUniqueWithInputs(InstanceOfNode.createHelper((ObjectStamp) oldStamp.improveWith(newStamp), node.getValue(), node.profile(), node.getAnchor()));
            /* GR-59681: Remove this check once BaseLayerType implements isAssignable. */
            AnalysisError.guarantee(node != replacement, "The new stamp needs to be different from the old stamp");
            node.replaceAndDelete(replacement);
            tool.addToWorkList(replacement);
        }
    }

    /** Folds assignability checks whose constant target type is globally unreachable. */
    private void handleClassIsAssignableFrom(ClassIsAssignableFromNode node, SimplifierTool tool) {
        if (strengthenGraphs.isClosedTypeWorld) {
            /*
             * MethodTypeFlowBuilder.ignoreConstant avoids making this target type reachable merely
             * because Class.isAssignableFrom uses it. A globally unreachable target therefore makes
             * the check false. This is valid only in a closed type world; an open world may use the
             * type later.
             */
            AnalysisType nonReachableType = asConstantNonReachableType(node.getThisClass(), tool);
            if (nonReachableType != null) {
                node.replaceAndDelete(LogicConstantNode.contradiction(graph));
            }
        }
    }

    /** Replaces an unreachable class literal used only by a class-cast exception message. */
    private void handleBytecodeException(BytecodeExceptionNode node, SimplifierTool tool) {
        /*
         * Do not make a type reachable only for a ClassCastException message. Replace its Class
         * object with the type-name String consumed directly by the message. This is safe in both
         * closed and open type worlds.
         */
        if (node.getExceptionKind() == BytecodeExceptionNode.BytecodeExceptionKind.CLASS_CAST) {
            AnalysisType nonReachableType = asConstantNonReachableType(node.getArguments().get(1), tool);
            if (nonReachableType != null) {
                node.getArguments().set(1, ConstantNode.forConstant(tool.getConstantReflection().forString(strengthenGraphs.getTypeName(nonReachableType)), tool.getMetaAccess(), graph));
            }
        }
    }

    /** Returns the constant {@link AnalysisType} when it is globally unreachable. */
    private static AnalysisType asConstantNonReachableType(ValueNode value, CoreProviders providers) {
        if (value != null && value.isConstant()) {
            AnalysisType expectedType = (AnalysisType) providers.getConstantReflection().asJavaType(value.asConstant());
            if (expectedType != null && !expectedType.isReachable()) {
                return expectedType;
            }
        }
        return null;
    }

    /** Removes analysis-only values from a frame state. */
    private void handleFrameState(FrameState node) {
        /* Do not make a constant reachable only for debugging information in a FrameState. */
        for (int i = 0; i < node.values().size(); i++) {
            if (node.values().get(i) instanceof ConstantNode constantNode && constantNode.getValue() instanceof ImageHeapConstant imageHeapConstant && !imageHeapConstant.isReachable()) {
                node.values().set(i, ConstantNode.defaultForKind(JavaKind.Object, graph));
            }
            if (node.values().get(i) instanceof FieldOffsetProvider fieldOffsetProvider && !((AnalysisField) fieldOffsetProvider.getField()).isUnsafeAccessed()) {
                /*
                 * Use a unique marker replacement so searching the code base for the value leads
                 * back to this transformation.
                 */
                node.values().set(i, ConstantNode.forIntegerKind(fieldOffsetProvider.asNode().getStackKind(), 0xDEA51106, graph));
            }
        }
    }

    /** Applies globally valid reachability refinement to an existing Pi stamp. */
    private void handlePi(PiNode node, SimplifierTool tool) {
        Stamp oldStamp = node.piStamp();
        Stamp newStamp = strengtheningSupport.strengthenStamp(oldStamp);
        if (newStamp != null) {
            Stamp newPiStamp = oldStamp.improveWith(newStamp);
            /* GR-59681: Remove this check once BaseLayerType implements isAssignable. */
            AnalysisError.guarantee(!newPiStamp.equals(oldStamp), "The new stamp needs to be different from the old stamp");
            node.strengthenPiStamp(newPiStamp);
            tool.addToWorkList(node);
        }
    }

    /** Removes a globally unreachable direct invoke. */
    private void handleInvoke(Invoke invoke, SimplifierTool tool) {
        if (invoke.callTarget() instanceof MethodCallTargetNode callTarget && callTarget.invokeKind().isDirect()) {
            AnalysisMethod targetMethod = (AnalysisMethod) callTarget.targetMethod();
            if (!targetMethod.isSimplyImplementationInvoked()) {
                /*
                 * Analysis did not observe this direct target as invoked, typically because its
                 * receiver is always null. Points-to analysis usually also reports no callees, but
                 * reachability analysis has no detailed callee list, so retain this independent
                 * check.
                 */
                strengtheningSupport.unreachableInvoke(invoke, tool, () -> strengtheningSupport.location(invoke) + ": target method is not marked as simply implementation invoked");
            }
        }
    }
}
