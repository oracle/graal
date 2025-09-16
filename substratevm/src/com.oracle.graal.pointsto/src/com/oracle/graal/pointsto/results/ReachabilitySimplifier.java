/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.Function;
import java.util.function.Supplier;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.nodes.extended.FieldOffsetProvider;
import jdk.graal.compiler.nodes.java.ClassIsAssignableFromNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.LimitedValueProxy;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.common.CanonicalizerPhase.CustomSimplification;
import jdk.graal.compiler.phases.common.inlining.InliningUtil;
import jdk.graal.compiler.replacements.nodes.MacroInvokable;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Simplify graphs based on reachability information tracked by the static analysis.
 */
class ReachabilitySimplifier implements CustomSimplification {

    protected final StrengthenGraphs strengthenGraphs;
    protected final StructuredGraph graph;

    /**
     * For runtime compiled methods, we must be careful to ensure new SubstrateTypes are not created
     * due to the optimizations performed during the
     * {@link StrengthenGraphs.AnalysisStrengthenGraphsPhase}.
     */
    protected final Function<AnalysisType, ResolvedJavaType> toTargetFunction;

    ReachabilitySimplifier(StrengthenGraphs strengthenGraphs, AnalysisMethod method, StructuredGraph graph) {
        this.strengthenGraphs = strengthenGraphs;
        this.graph = graph;
        this.toTargetFunction = strengthenGraphs.bb.getHostVM().getStrengthenGraphsToTargetFunction(method.getMultiMethodKey());
    }

    @Override
    public void simplify(Node n, SimplifierTool tool) {
        if (n instanceof ValueNode node) {
            tryImproveStamp(node, tool);
        }

        if (strengthenGraphs.simplifyDelegate(n, tool)) {
            // Handled in the delegate simplification.
            return;
        }

        switch (n) {
            case InstanceOfNode node -> handleInstanceOf(node, tool);
            case ClassIsAssignableFromNode node -> handleClassIsAssignableFrom(node, tool);
            case BytecodeExceptionNode node -> handleBytecodeException(node, tool);
            case FrameState node -> handleFrameState(node);
            case PiNode node -> handlePi(node, tool);
            case Invoke invoke -> handleInvoke(invoke, tool);
            case null, default -> {
            }
        }
    }

    protected void tryImproveStamp(ValueNode node, SimplifierTool tool) {
        if (!(node instanceof LimitedValueProxy) && !(node instanceof PhiNode) && !(node instanceof MacroInvokable)) {
            /*
             * The stamp of proxy nodes and phi nodes is inferred automatically, so we do not need
             * to improve them. Macro nodes prohibit changing their stamp because it is derived from
             * the macro's fallback invoke. First ask the node to improve the stamp itself, to
             * incorporate already improved input stamps.
             */
            node.inferStamp();
            /*
             * Since this new stamp is not based on a type flow, it is valid for the entire method
             * and we can update the stamp of the node directly. We do not need an anchored PiNode.
             */
            updateStampInPlace(node, strengthenStamp(node.stamp(NodeView.DEFAULT)), tool);
        }
    }

    protected void updateStampInPlace(ValueNode node, Stamp newStamp, SimplifierTool tool) {
        if (newStamp != null) {
            Stamp oldStamp = node.stamp(NodeView.DEFAULT);
            Stamp computedStamp = oldStamp.improveWith(newStamp);
            if (!oldStamp.equals(computedStamp)) {
                node.setStamp(newStamp);
                tool.addToWorkList(node.usages());
            }
        }
    }

    protected void handleInstanceOf(InstanceOfNode node, SimplifierTool tool) {
        ObjectStamp oldStamp = node.getCheckedStamp();
        Stamp newStamp = strengthenStamp(oldStamp);
        if (newStamp != null) {
            LogicNode replacement = graph.addOrUniqueWithInputs(InstanceOfNode.createHelper((ObjectStamp) oldStamp.improveWith(newStamp), node.getValue(), node.profile(), node.getAnchor()));
            /*
             * GR-59681: Once isAssignable is implemented for BaseLayerType, this check can be
             * removed
             */
            AnalysisError.guarantee(node != replacement, "The new stamp needs to be different from the old stamp");
            node.replaceAndDelete(replacement);
            tool.addToWorkList(replacement);
        } else {
            strengthenGraphs.maybeAssignInstanceOfProfiles(node);
        }
    }

    protected void handleClassIsAssignableFrom(ClassIsAssignableFromNode node, SimplifierTool tool) {
        if (strengthenGraphs.isClosedTypeWorld) {
            /*
             * If the constant receiver of a Class#isAssignableFrom is an unreachable type we can
             * constant-fold the ClassIsAssignableFromNode to false. See also
             * MethodTypeFlowBuilder#ignoreConstant where we avoid marking the corresponding type as
             * reachable just because it is used by the ClassIsAssignableFromNode. We only apply
             * this optimization if it's a closed type world, for open world we cannot fold the type
             * check since the type may be used later.
             */
            AnalysisType nonReachableType = asConstantNonReachableType(node.getThisClass(), tool);
            if (nonReachableType != null) {
                node.replaceAndDelete(LogicConstantNode.contradiction(graph));
            }
        }
    }

    protected void handleBytecodeException(BytecodeExceptionNode node, SimplifierTool tool) {
        /*
         * We do not want a type to be reachable only to be used for the error message of a
         * ClassCastException. Therefore, in that case we replace the java.lang.Class with a
         * java.lang.String that is then used directly in the error message. We can apply this
         * optimization optimistically for both closed and open type world.
         */
        if (node.getExceptionKind() == BytecodeExceptionNode.BytecodeExceptionKind.CLASS_CAST) {
            AnalysisType nonReachableType = asConstantNonReachableType(node.getArguments().get(1), tool);
            if (nonReachableType != null) {
                node.getArguments().set(1, ConstantNode.forConstant(tool.getConstantReflection().forString(strengthenGraphs.getTypeName(nonReachableType)), tool.getMetaAccess(), graph));
            }
        }
    }

    private static AnalysisType asConstantNonReachableType(ValueNode value, CoreProviders providers) {
        if (value != null && value.isConstant()) {
            AnalysisType expectedType = (AnalysisType) providers.getConstantReflection().asJavaType(value.asConstant());
            if (expectedType != null && !expectedType.isReachable()) {
                return expectedType;
            }
        }
        return null;
    }

    protected void handleFrameState(FrameState node) {
        /*
         * We do not want a constant to be reachable only to be used for debugging purposes in a
         * FrameState.
         */
        for (int i = 0; i < node.values().size(); i++) {
            if (node.values().get(i) instanceof ConstantNode constantNode && constantNode.getValue() instanceof ImageHeapConstant imageHeapConstant && !imageHeapConstant.isReachable()) {
                node.values().set(i, ConstantNode.defaultForKind(JavaKind.Object, graph));
            }
            if (node.values().get(i) instanceof FieldOffsetProvider fieldOffsetProvider && !((AnalysisField) fieldOffsetProvider.getField()).isUnsafeAccessed()) {
                /*
                 * We use a unique marker constant as the replacement value, so that a search in the
                 * code base for the value leads us to here.
                 */
                node.values().set(i, ConstantNode.forIntegerKind(fieldOffsetProvider.asNode().getStackKind(), 0xDEA51106, graph));
            }
        }
    }

    protected void handlePi(PiNode node, SimplifierTool tool) {
        Stamp oldStamp = node.piStamp();
        Stamp newStamp = strengthenStamp(oldStamp);
        if (newStamp != null) {
            Stamp newPiStamp = oldStamp.improveWith(newStamp);
            /*
             * GR-59681: Once isAssignable is implemented for BaseLayerType, this check can be
             * removed
             */
            AnalysisError.guarantee(!newPiStamp.equals(oldStamp), "The new stamp needs to be different from the old stamp");
            node.strengthenPiStamp(newPiStamp);
            tool.addToWorkList(node);
        }
    }

    private void handleInvoke(Invoke invoke, SimplifierTool tool) {
        if (invoke.callTarget() instanceof MethodCallTargetNode) {
            maybeMarkUnreachable(invoke, tool);
        }
    }

    protected boolean maybeMarkUnreachable(Invoke invoke, SimplifierTool tool) {
        MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();
        AnalysisMethod targetMethod = (AnalysisMethod) callTarget.targetMethod();
        if (callTarget.invokeKind().isDirect() && !targetMethod.isSimplyImplementationInvoked()) {
            /*
             * This is a direct call to a method that the static analysis did not see as invoked.
             * This can happen when the receiver is always null. In most cases, the method profile
             * also has a length of 0 and the below code to kill the invoke would trigger. But when
             * only running the reachability analysis, there is no detailed list of callees.
             */
            unreachableInvoke(invoke, tool, () -> location(invoke) + ": target method is not marked as simply implementation invoked");
            return true;
        }
        return false;
    }

    /**
     * The invoke has no callee, i.e., it is unreachable.
     */
    protected void unreachableInvoke(Invoke invoke, SimplifierTool tool, Supplier<String> messageSupplier) {
        if (invoke.getInvokeKind() != CallTargetNode.InvokeKind.Static) {
            /*
             * Ensure that a null check for the receiver remains in the graph. There should be
             * already an explicit null check in the graph, but we are paranoid and check again.
             */
            InliningUtil.nonNullReceiver(invoke);
        }

        makeUnreachable(invoke.asFixedNode(), tool, messageSupplier);
    }

    protected void makeUnreachable(FixedNode node, CoreProviders providers, Supplier<String> message) {
        FixedNode unreachableNode = strengthenGraphs.createUnreachable(graph, providers, message);
        ((FixedWithNextNode) node.predecessor()).setNext(unreachableNode);
        GraphUtil.killCFG(node);
    }

    protected String location(Invoke invoke) {
        return "method " + StrengthenGraphs.getQualifiedName(graph) + ", node " + invoke;
    }

    protected String location(Node node) {
        return "method " + StrengthenGraphs.getQualifiedName(graph) + ", node " + node;
    }

    private Stamp strengthenStamp(Stamp s) {
        if (!(s instanceof AbstractObjectStamp stamp)) {
            return null;
        }
        AnalysisType originalType = (AnalysisType) stamp.type();
        if (originalType == null) {
            return null;
        }

        /* In open world the type may become reachable later. */
        if (strengthenGraphs.isClosedTypeWorld && !originalType.isReachable()) {
            /* We must be in dead code. */
            if (stamp.nonNull()) {
                /* We must be in dead code. */
                return StampFactory.empty(JavaKind.Object);
            } else {
                return StampFactory.alwaysNull();
            }
        }

        AnalysisType singleImplementorType = strengthenGraphs.getSingleImplementorType(originalType);
        if (singleImplementorType != null && (!stamp.isExactType() || !singleImplementorType.equals(originalType))) {
            ResolvedJavaType targetType = toTargetFunction.apply(singleImplementorType);
            if (targetType != null) {
                TypeReference typeRef = TypeReference.createExactTrusted(targetType);
                return StampFactory.object(typeRef, stamp.nonNull());
            }
        }

        AnalysisType strengthenType = strengthenGraphs.getStrengthenStampType(originalType);
        if (originalType.equals(strengthenType)) {
            /* Nothing to strengthen. */
            return null;
        }

        Stamp newStamp;
        if (strengthenType == null) {
            /* The type and its subtypes are not instantiated. */
            if (stamp.nonNull()) {
                /* We must be in dead code. */
                newStamp = StampFactory.empty(JavaKind.Object);
            } else {
                newStamp = StampFactory.alwaysNull();
            }

        } else {
            if (stamp.isExactType()) {
                /* We must be in dead code. */
                newStamp = StampFactory.empty(JavaKind.Object);
            } else {
                ResolvedJavaType targetType = toTargetFunction.apply(strengthenType);
                if (targetType == null) {
                    return null;
                }
                TypeReference typeRef = TypeReference.createTrustedWithoutAssumptions(targetType);
                newStamp = StampFactory.object(typeRef, stamp.nonNull());
            }
        }
        return newStamp;
    }
}
