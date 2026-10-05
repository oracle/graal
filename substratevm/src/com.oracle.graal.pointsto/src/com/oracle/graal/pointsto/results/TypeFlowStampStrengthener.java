/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.EconomicSet;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.typestate.PrimitiveConstantTypeState;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;

/**
 * Converts an explicitly supplied points-to {@link TypeFlow} into a stamp or constant and applies
 * unreachable-flow effects. It owns no node map and cannot discover a flow from a graph node.
 */
final class TypeFlowStampStrengthener {
    private final StrengthenGraphs strengthenGraphs;
    private final PointsToAnalysis bb;
    private final MethodTypeFlow methodFlow;
    private final GraphStrengtheningSupport strengtheningSupport;
    private final boolean allowConstantFolding;
    private final EconomicSet<ValueNode> unreachableValues = EconomicSet.create();

    /** Creates an explicit-flow converter for one analyzed method. */
    TypeFlowStampStrengthener(StrengthenGraphs strengthenGraphs, PointsToAnalysis bb, AnalysisMethod method, GraphStrengtheningSupport strengtheningSupport) {
        this.strengthenGraphs = strengthenGraphs;
        this.bb = bb;
        this.methodFlow = ((PointsToAnalysisMethod) method).getTypeFlow();
        AnalysisError.guarantee(methodFlow.flowsGraphCreated(), "Trying to strengthen a method without a type flows graph: %s.", method);
        this.strengtheningSupport = strengtheningSupport;
        this.allowConstantFolding = strengthenGraphs.strengthenGraphWithConstants && bb.getHostVM().allowConstantFolding(method) && method.allowStrengthenGraphWithConstants();
    }

    /**
     * Converts {@code flow} into a stronger stamp or constant for {@code node}. The caller must
     * supply the flow explicitly; {@code null}, saturated, or unsupported flows produce no result.
     * <p>
     * This helper does not distinguish flow-sensitive from flow-insensitive facts. The caller must
     * select a flow whose facts are valid for the intended transformation and preserve any required
     * control-position anchoring. Iterative callers must supply only globally valid flows, never
     * flows obtained from the original node-to-flow mapping.
     */
    Object strengthenStampFromTypeFlow(ValueNode node, TypeFlow<?> flow, FixedWithNextNode anchorPoint, SimplifierTool tool) {
        if (flow == null || node.getStackKind() == JavaKind.Void || !bb.isSupportedJavaKind(node.getStackKind())) {
            return null;
        }
        if (methodFlow.isSaturated(bb, flow) || unreachableValues.contains(node)) {
            /*
             * A saturated flow's state cannot strengthen the graph, and a node already made
             * unreachable needs no further processing.
             */
            return null;
        }
        /*
         * Avoid adding a PiNode for a value without non-state usages. Do not return yet because an
         * empty type state can still prove the surrounding control flow unreachable.
         */
        boolean hasUsages = node.usages().filter(usage -> !(usage instanceof FrameState)).isNotEmpty();

        if (!flow.isFlowEnabled()) {
            strengtheningSupport.makeUnreachable(anchorPoint.next(), tool, () -> strengtheningSupport.location(node) + ": flow is not enabled by its predicate " + flow.getPredicate().format(true,
                            true));
            unreachableValues.add(node);
            return null;
        }
        TypeState nodeTypeState = methodFlow.foldTypeFlow(bb, flow);

        if (openWorldOpenAllInstantiated(flow)) {
            if (!nodeTypeState.canBeNull() && hasUsages) {
                return StampFactory.objectNonNull();
            }
            return null;
        }

        if (hasUsages && allowConstantFolding && !nodeTypeState.canBeNull()) {
            JavaConstant constantValue = nodeTypeState.asConstant();
            if (constantValue != null) {
                return constantValue;
            }
        }

        /*
         * Refresh the graph-derived stamp from current inputs before intersecting it with
         * potentially less precise analysis state. The flow-sensitive snapshot does not otherwise
         * propagate input stamps iteratively.
         */
        node.inferStamp();
        Stamp stamp = node.stamp(NodeView.DEFAULT);
        if (stamp.isIntegerStamp() || nodeTypeState.isPrimitive()) {
            return getIntegerStamp(node, (IntegerStamp) stamp, anchorPoint, nodeTypeState, tool);
        }

        ObjectStamp oldStamp = (ObjectStamp) stamp;
        AnalysisType oldType = (AnalysisType) oldStamp.type();
        boolean nonNull = oldStamp.nonNull() || !nodeTypeState.canBeNull();

        /*
         * Keep only analysis types compatible with the current graph stamp. Input-stamp propagation
         * can make that stamp more precise than the static analysis result.
         */
        List<AnalysisType> typeStateTypes = new ArrayList<>(nodeTypeState.typesCount());
        for (AnalysisType typeStateType : nodeTypeState.types(bb)) {
            if (oldType == null || (oldStamp.isExactType() ? oldType.equals(typeStateType) : oldType.isJavaLangObject() || oldType.isAssignableFrom(typeStateType))) {
                typeStateTypes.add(typeStateType);
            }
        }

        if (typeStateTypes.isEmpty()) {
            if (nonNull) {
                strengtheningSupport.makeUnreachable(anchorPoint.next(), tool, () -> strengtheningSupport.location(node) + ": empty object type state when strengthening oldStamp " + oldStamp);
                unreachableValues.add(node);
                return null;
            }
            return hasUsages ? StampFactory.alwaysNull() : null;
        }
        if (!hasUsages) {
            /* A strengthened stamp would be unused. */
            return null;
        }
        if (typeStateTypes.size() == 1) {
            AnalysisType exactType = typeStateTypes.get(0);
            assert strengthenGraphs.getSingleImplementorType(exactType) == null || exactType.equals(strengthenGraphs.getSingleImplementorType(exactType)) : "exactType=" + exactType +
                            ", singleImplementor=" + strengthenGraphs.getSingleImplementorType(exactType);
            assert exactType.equals(strengthenGraphs.getStrengthenStampType(exactType)) : exactType;

            if ((!oldStamp.isExactType() || !exactType.equals(oldType)) && strengtheningSupport.canStrengthenStampTo(exactType)) {
                TypeReference typeRef = TypeReference.createExactTrusted(exactType);
                return StampFactory.object(typeRef, nonNull);
            }
        } else if (!oldStamp.isExactType()) {
            AnalysisType baseType = typeStateTypes.get(0);
            for (int i = 1; i < typeStateTypes.size(); i++) {
                if (baseType.isJavaLangObject()) {
                    break;
                }
                baseType = baseType.findLeastCommonAncestor(typeStateTypes.get(i));
            }

            if (oldType != null && !oldType.isAssignableFrom(baseType)) {
                /*
                 * Do not weaken an interface stamp to the common base class of its implementations,
                 * which can be java.lang.Object.
                 */
                baseType = oldType;
            }

            /*
             * Multiple analysis types rule out a distinct single implementor: that implementor
             * would itself have to be the only type in the state.
             */
            assert strengthenGraphs.getSingleImplementorType(baseType) == null || baseType.equals(strengthenGraphs.getSingleImplementorType(baseType)) : "baseType=" + baseType +
                            ", singleImplementor=" + strengthenGraphs.getSingleImplementorType(baseType);

            AnalysisType newType = strengthenGraphs.getStrengthenStampType(baseType);
            assert typeStateTypes.stream().map(newType::isAssignableFrom).reduce(Boolean::logicalAnd).get() : typeStateTypes;
            if (!newType.equals(oldType) && (oldType != null || !newType.isJavaLangObject()) && strengtheningSupport.canStrengthenStampTo(newType)) {
                TypeReference typeRef = TypeReference.createTrustedWithoutAssumptions(newType);
                return StampFactory.object(typeRef, nonNull);
            }
        }

        if (nonNull != oldStamp.nonNull()) {
            assert nonNull : oldStamp;
            return oldStamp.asNonNull();
        }
        /* Nothing to strengthen. */
        return null;
    }

    /** Returns whether an open-world all-instantiated flow carries incomplete type information. */
    private boolean openWorldOpenAllInstantiated(TypeFlow<?> flow) {
        AnalysisType declaredType = flow.getDeclaredType();
        return !strengthenGraphs.isClosedTypeWorld && flow.isAllInstantiated() && declaredType != null && !bb.isClosed(declaredType);
    }

    /** Converts a primitive flow state into an integer stamp or unreachable control. */
    private IntegerStamp getIntegerStamp(ValueNode node, IntegerStamp originalStamp, FixedWithNextNode anchorPoint, TypeState nodeTypeState, SimplifierTool tool) {
        assert bb.trackPrimitiveValues() : nodeTypeState + "," + node + " in " + node.graph();
        assert nodeTypeState != null && (nodeTypeState.isEmpty() || nodeTypeState.isPrimitive()) : nodeTypeState + "," + node + " in " + node.graph();
        if (nodeTypeState.isEmpty()) {
            strengtheningSupport.makeUnreachable(anchorPoint.next(), tool, () -> strengtheningSupport.location(node) + ": empty primitive type state when strengthening oldStamp " + originalStamp);
            unreachableValues.add(node);
            return null;
        }
        if (nodeTypeState instanceof PrimitiveConstantTypeState constantTypeState) {
            long constantValue = constantTypeState.getValue();
            if (node instanceof ConstantNode constant) {
                /* Verify that the analysis result agrees with the graph constant. */
                Constant value = constant.getValue();
                assert value instanceof PrimitiveConstant : "Node " + value + " should be a primitive constant when extracting an integer stamp, method " + node.graph().method();
                assert ((PrimitiveConstant) value).asLong() == constantValue : "The actual value of node: " + value + " is different than the value " + constantValue +
                                " computed by points-to analysis, method in " + node.graph().method();
            } else {
                return IntegerStamp.createConstant(originalStamp.getBits(), constantValue);
            }
        }
        return null;
    }
}
