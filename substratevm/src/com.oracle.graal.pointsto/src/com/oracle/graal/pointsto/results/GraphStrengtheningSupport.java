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

import java.util.function.Predicate;
import java.util.function.Supplier;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;

import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.LimitedValueProxy;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.common.inlining.InliningUtil;
import jdk.graal.compiler.replacements.nodes.MacroInvokable;
import jdk.vm.ci.meta.JavaKind;

/**
 * Per-method graph context shared by {@link FlowSensitiveSimplifier} and
 * {@link FlowInsensitiveSimplifier}. It owns generic graph mutation and globally valid
 * stamp-strengthening mechanics using state specific to one graph, while {@link StrengthenGraphs}
 * supplies host-VM-specific queries and node factories. It deliberately owns no node dispatch or
 * static-analysis flow mapping.
 */
final class GraphStrengtheningSupport {
    private final StrengthenGraphs strengthenGraphs;
    private final StructuredGraph graph;
    /**
     * Controls whether a strengthened stamp for this method variant may reference an analysis type.
     * This allows the host VM to prevent a graph from referring to a type whose runtime
     * representation is unavailable.
     * <p>
     * For runtime-compiled methods, SVM uses this policy to allow only types whose corresponding
     * {@code SubstrateType} was already created. Graph strengthening runs after analysis and must
     * not create additional substrate types at that point.
     */
    private final Predicate<AnalysisType> typePredicate;

    /** Creates support for strengthening one analyzed method graph. */
    GraphStrengtheningSupport(StrengthenGraphs strengthenGraphs, AnalysisMethod method, StructuredGraph graph) {
        this.strengthenGraphs = strengthenGraphs;
        this.graph = graph;
        this.typePredicate = strengthenGraphs.bb.getHostVM().getStrengthenGraphsTypePredicate(method.getMethodVariantKey());
    }

    /** Returns whether graph strengthening may refine a stamp to reference {@code type}. */
    boolean canStrengthenStampTo(AnalysisType type) {
        return typePredicate.test(type);
    }

    /** Applies graph-intrinsic inference followed by globally valid reachability strengthening. */
    void inferAndStrengthenStamp(ValueNode node, SimplifierTool tool) {
        if (!(node instanceof LimitedValueProxy) && !(node instanceof PhiNode) && !(node instanceof MacroInvokable)) {
            /*
             * Proxy and phi stamps are inferred automatically. Macro stamps come from their
             * fallback invokes and must not be changed independently. First ask the node to
             * incorporate already improved input stamps.
             */
            node.inferStamp();
            /*
             * This refinement is not based on a position-specific type flow, so it is valid for the
             * entire method and can update the node directly without an anchored PiNode.
             */
            updateStampInPlace(node, strengthenStamp(node.stamp(NodeView.DEFAULT)), tool);
        }
    }

    /** Improves {@code node}'s stamp and schedules its usages when the stamp changed. */
    static void updateStampInPlace(ValueNode node, Stamp newStamp, SimplifierTool tool) {
        if (newStamp != null) {
            Stamp oldStamp = node.stamp(NodeView.DEFAULT);
            Stamp computedStamp = oldStamp.improveWith(newStamp);
            if (!oldStamp.equals(computedStamp)) {
                node.setStamp(computedStamp);
                tool.addToWorkList(node.usages());
            }
        }
    }

    /**
     * Tries to improve {@code stamp} using globally valid reachability and type-hierarchy facts. It
     * only handles {@link AbstractObjectStamp object stamps}: primitive types are implicitly
     * reachable, and neither reachability nor points-to analysis tracks primitive ranges here.
     * Returns {@code null} when no improvement is available.
     */
    Stamp strengthenStamp(Stamp stamp) {
        if (!(stamp instanceof AbstractObjectStamp objectStamp)) {
            /* Only object types can be strengthened using global reachability. */
            return null;
        }
        AnalysisType originalType = (AnalysisType) objectStamp.type();
        if (originalType == null) {
            /* The stamp is either empty or a non-exact java.lang.Object. */
            return null;
        }

        /* In an open world the type may become reachable later. */
        if (strengthenGraphs.isClosedTypeWorld && !originalType.isReachable()) {
            return objectStamp.nonNull() ? StampFactory.empty(JavaKind.Object) : StampFactory.alwaysNull();
        }

        /* First try to make the stamp exact when analysis proves a single implementation. */
        AnalysisType singleImplementorType = strengthenGraphs.getSingleImplementorType(originalType);
        if (singleImplementorType != null && (!objectStamp.isExactType() || !singleImplementorType.equals(originalType)) && canStrengthenStampTo(singleImplementorType)) {
            TypeReference typeRef = TypeReference.createExactTrusted(singleImplementorType);
            return StampFactory.object(typeRef, objectStamp.nonNull());
        }

        /*
         * If exactification is not possible, narrow to a subtype that still covers every instantiated
         * type assignable to the original type. A null result proves that no such instantiated type
         * exists.
         */
        AnalysisType strengthenType = strengthenGraphs.getStrengthenStampType(originalType);
        if (originalType.equals(strengthenType)) {
            return null;
        }
        if (strengthenType == null) {
            /* Neither the type nor any subtype is instantiated. */
            return objectStamp.nonNull() ? StampFactory.empty(JavaKind.Object) : StampFactory.alwaysNull();
        }
        if (objectStamp.isExactType()) {
            /* The exact type is not instantiated, so this value is dead. */
            return StampFactory.empty(JavaKind.Object);
        }
        if (canStrengthenStampTo(strengthenType)) {
            TypeReference typeRef = TypeReference.createTrustedWithoutAssumptions(strengthenType);
            return StampFactory.object(typeRef, objectStamp.nonNull());
        }
        return null;
    }

    /** Replaces an invoke whose receiver is known to be {@code null} using a host-VM-specific node. */
    void replaceInvokeWithNullReceiver(Invoke invoke) {
        FixedNode replacement = strengthenGraphs.createInvokeWithNullReceiverReplacement(graph);
        ((FixedWithNextNode) invoke.predecessor()).setNext(replacement);
        GraphUtil.killCFG(invoke.asFixedNode());
    }

    /** Replaces an invoke proven unreachable while preserving receiver null-check semantics. */
    void unreachableInvoke(Invoke invoke, SimplifierTool tool, Supplier<String> messageSupplier) {
        if (invoke.getInvokeKind() != CallTargetNode.InvokeKind.Static) {
            /*
             * Preserve a receiver null check. The graph should already contain an explicit check,
             * but enforce it here before removing the invoke.
             */
            InliningUtil.nonNullReceiver(invoke);
        }
        makeUnreachable(invoke.asFixedNode(), tool, messageSupplier);
    }

    /** Replaces control flow at {@code node} using a host-VM-specific unreachable-control node. */
    void makeUnreachable(FixedNode node, CoreProviders providers, Supplier<String> message) {
        FixedNode unreachableNode = strengthenGraphs.createUnreachable(graph, providers, message);
        ((FixedWithNextNode) node.predecessor()).setNext(unreachableNode);
        GraphUtil.killCFG(node);
    }

    /** Returns a diagnostic location for {@code invoke}. */
    String location(Invoke invoke) {
        return "method " + StrengthenGraphs.getQualifiedName(graph) + ", node " + invoke;
    }

    /** Returns a diagnostic location for {@code node}. */
    String location(Node node) {
        return "method " + StrengthenGraphs.getQualifiedName(graph) + ", node " + node;
    }
}
