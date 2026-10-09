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

import java.util.Collection;
import java.util.function.Predicate;

import org.graalvm.collections.EconomicSet;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.flow.PrimitiveFilterTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.PointsToAnalysisField;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.util.GuestAnnotationAccess;
import com.oracle.svm.util.ImageBuildStatistics;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.ValueAnchorNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.JavaTypeProfile;

/**
 * Materializes node- and control-position-specific points-to facts during a one-shot snapshot
 * traversal. This class alone owns parameter flows and the node-to-{@link TypeFlow} mapping and is
 * intentionally not a canonicalizer custom simplification.
 */
final class FlowSensitiveSimplifier {

    private final StrengthenGraphs strengthenGraphs;
    private final StructuredGraph graph;
    private final GraphStrengtheningSupport strengtheningSupport;
    private final TypeFlowStampStrengthener typeFlowStrengthener;
    private final PointsToAnalysis bb;

    private final MethodTypeFlow methodFlow;
    private final TypeFlow<?>[] parameterFlows;
    private final NodeMap<TypeFlow<?>> nodeFlows;

    private final boolean allowOptimizeReturnParameter;

    private final EconomicSet<ValueNode> unreachableValues = EconomicSet.create();

    /** Creates the one-shot flow-sensitive simplifier for {@code method}. */
    FlowSensitiveSimplifier(StrengthenGraphs strengthenGraphs, AnalysisMethod method, StructuredGraph graph, GraphStrengtheningSupport strengtheningSupport) {
        this.strengthenGraphs = strengthenGraphs;
        this.graph = graph;
        this.strengtheningSupport = strengtheningSupport;
        this.bb = (PointsToAnalysis) strengthenGraphs.bb;
        this.typeFlowStrengthener = new TypeFlowStampStrengthener(strengthenGraphs, bb, method, strengtheningSupport);
        this.methodFlow = ((PointsToAnalysisMethod) method).getTypeFlow();
        AnalysisError.guarantee(methodFlow.flowsGraphCreated(), "Trying to strengthen a method without a type flows graph: %s.", method);
        MethodFlowsGraph originalFlows = methodFlow.getMethodFlowsGraph();
        this.parameterFlows = originalFlows.getParameters();
        this.nodeFlows = new NodeMap<>(graph);
        var cursor = originalFlows.getNodeFlows().getEntries();
        while (cursor.advance()) {
            Node node = cursor.getKey().getNode();
            assert nodeFlows.get(node) == null : "overwriting existing entry for " + node;
            nodeFlows.put(node, cursor.getValue());
        }
        /*
         * In deoptimization target methods optimizing the return parameter can make new values live
         * across deoptimization entrypoints.
         *
         * In runtime-compiled methods invokes may be intrinsified during runtime partial evaluation
         * and change the behavior of the invoke. This would be a problem if the behavior of the
         * method completely changed; however, currently this intrinsification is used to improve
         * the stamp of the returned value, but not to alter the semantics. Hence, it is preferred
         * to continue to use the return value of the invoke (as opposed to the parameter value).
         */
        this.allowOptimizeReturnParameter = method.isOriginalMethod() && bb.optimizeReturnedParameter();
    }

    private TypeFlow<?> getNodeFlow(Node node) {
        return nodeFlows == null || nodeFlows.isNew(node) ? null : nodeFlows.get(node);
    }

    /** Materializes flow-sensitive facts for one original {@code node}. */
    public void simplify(Node node, SimplifierTool tool) {
        Predicate<Node> isUnreachable = this::isUnreachable;
        if (strengthenGraphs.simplifyFlowSensitiveDelegate(node, tool, isUnreachable)) {
            return;
        }

        switch (node) {
            case ParameterNode parameter -> handleParameter(parameter, tool);
            case LoadFieldNode loadField -> handleLoadField(loadField, tool);
            case LoadIndexedNode loadIndexed -> handleLoadIndexed(loadIndexed, tool);
            case IfNode ifNode -> handleIf(ifNode, tool);
            case FixedGuardNode guard -> handleFixedGuard(guard, tool);
            case Invoke invoke -> handleInvoke(invoke, tool);
            default -> {
            }
        }
    }

    private void handleParameter(ParameterNode node, SimplifierTool tool) {
        StartNode anchorPoint = graph.start();
        Object newStampOrConstant = typeFlowStrengthener.strengthenStampFromTypeFlow(node, parameterFlows[node.index()], anchorPoint, tool);
        updateStampUsingPiNode(node, newStampOrConstant, anchorPoint, tool);
    }

    /** Applies the load's control-position-specific flow through an anchored PiNode. */
    private void handleLoadField(LoadFieldNode node, SimplifierTool tool) {
        PointsToAnalysisField field = (PointsToAnalysisField) node.field();
        /* An open field may be written from the open world, so its state cannot be trusted. */
        if (!bb.isClosed(field)) {
            return;
        }
        /* Unlike the globally valid sink flow, this mapped flow requires an anchored PiNode. */
        Object newStampOrConstant = typeFlowStrengthener.strengthenStampFromTypeFlow(node, getNodeFlow(node), node, tool);
        updateStampUsingPiNode(node, newStampOrConstant, node, tool);
    }

    private void handleLoadIndexed(LoadIndexedNode node, SimplifierTool tool) {
        Object newStampOrConstant = typeFlowStrengthener.strengthenStampFromTypeFlow(node, getNodeFlow(node), node, tool);
        updateStampUsingPiNode(node, newStampOrConstant, node, tool);
    }

    private void handleIf(IfNode node, SimplifierTool tool) {
        boolean trueUnreachable = isUnreachable(node.trueSuccessor());
        boolean falseUnreachable = isUnreachable(node.falseSuccessor());

        if (trueUnreachable && falseUnreachable) {
            strengtheningSupport.makeUnreachable(node, tool, () -> strengtheningSupport.location(node) + ": both successors of IfNode are unreachable");

        } else if (trueUnreachable || falseUnreachable) {
            AbstractBeginNode killedBegin = node.successor(trueUnreachable);
            AbstractBeginNode survivingBegin = node.successor(!trueUnreachable);

            if (survivingBegin.hasUsages()) {
                /*
                 * Even when we know that the IfNode is not necessary because the condition is
                 * statically proven, all PiNode that are anchored at the surviving branch must
                 * remain anchored at exactly this point. It would be wrong to anchor the PiNode at
                 * the BeginNode of the preceding block, because at that point the condition is not
                 * proven yet.
                 */
                ValueAnchorNode anchor = graph.add(new ValueAnchorNode());
                graph.addAfterFixed(survivingBegin, anchor);
                survivingBegin.replaceAtUsages(anchor, InputType.Guard, InputType.Anchor);
            }
            graph.removeSplit(node, survivingBegin);
            GraphUtil.killCFG(killedBegin);
        }
    }

    private void handleFixedGuard(FixedGuardNode node, SimplifierTool tool) {
        if (isUnreachable(node)) {
            node.setCondition(LogicConstantNode.tautology(graph), true);
            tool.addToWorkList(node);
        }
    }

    private boolean isUnreachable(Node node) {
        TypeFlow<?> flow = getNodeFlow(node);
        if (flow instanceof InvokeTypeFlow invokeFlow) {
            return !invokeFlow.isFlowEnabled() || invokeFlow.getAllCallees().isEmpty();
        }
        if (flow != null && !methodFlow.isSaturated(bb, flow)) {
            if (!flow.isFlowEnabled()) {
                return true;
            }
            TypeState typeState = methodFlow.foldTypeFlow(bb, flow);
            if (flow.isPrimitiveFlow()) {
                /*
                 * This assert is a safeguard to verify the assumption that only one type of flow
                 * has to be considered as a branch predicate at the moment.
                 */
                assert flow instanceof PrimitiveFilterTypeFlow : "Unexpected type of primitive flow encountered as branch predicate: " + flow;
            }
            return typeState.isEmpty();
        }
        return false;
    }

    private void handleInvoke(Invoke invoke, SimplifierTool tool) {
        if (!(invoke.callTarget() instanceof MethodCallTargetNode callTarget)) {
            return;
        }
        FixedNode node = invoke.asFixedNode();
        InvokeTypeFlow invokeFlow = (InvokeTypeFlow) getNodeFlow(node);
        if (invokeFlow == null) {
            /* No points-to analysis results. */
            return;
        }
        if (!invokeFlow.isFlowEnabled()) {
            strengtheningSupport.unreachableInvoke(invoke, tool, () -> strengtheningSupport.location(invoke) + ": Invoke flow with target method " + invokeFlow.getTargetMethod().format("%H.%n") +
                            " is not enabled by its predicate: " + invokeFlow.getPredicate().format(true, true));
            /* Invoke is unreachable, there is no point in improving any types further. */
            return;
        }

        AnalysisMethod targetMethod = (AnalysisMethod) callTarget.targetMethod();

        Collection<AnalysisMethod> callees = invokeFlow.getOriginalCallees();
        if (callees.isEmpty()) {
            if (strengthenGraphs.isClosedTypeWorld) {
                /* Invoke is unreachable, there is no point in improving any types further. */
                strengtheningSupport.unreachableInvoke(invoke, tool, () -> strengtheningSupport.location(invoke) + ": empty list of callees for call to " + targetMethod.getQualifiedName());
            }
            /* In open world we cannot make any assumptions about an invoke with 0 callees. */
            return;
        }
        assert invokeFlow.isFlowEnabled() : "Disabled invoke should have no callees: " + invokeFlow + ", in method " + StrengthenGraphs.getQualifiedName(graph);

        FixedWithNextNode beforeInvoke = (FixedWithNextNode) invoke.predecessor();
        NodeInputList<ValueNode> arguments = callTarget.arguments();
        for (int i = 0; i < arguments.size(); i++) {
            ValueNode argument = arguments.get(i);
            Object newStampOrConstant = typeFlowStrengthener.strengthenStampFromTypeFlow(argument, invokeFlow.getActualParameters()[i], beforeInvoke, tool);
            if (node.isDeleted()) {
                /* Parameter stamp was empty, so invoke is unreachable. */
                return;
            }
            if (i == 0 && invoke.getInvokeKind() != CallTargetNode.InvokeKind.Static) {
                /*
                 * Check for null receiver. If so, the invoke is unreachable.
                 *
                 * Note it is not necessary to check for an empty stamp, as in that case
                 * strengthenStampFromTypeFlow will make the invoke unreachable.
                 */
                boolean nullReceiver = false;
                if (argument instanceof ConstantNode constantNode) {
                    nullReceiver = constantNode.getValue().isDefaultForKind();
                }
                if (!nullReceiver && newStampOrConstant instanceof ObjectStamp stamp) {
                    nullReceiver = stamp.alwaysNull();
                }
                if (!nullReceiver && newStampOrConstant instanceof Constant constantValue) {
                    nullReceiver = constantValue.isDefaultForKind();
                }
                if (nullReceiver) {
                    strengtheningSupport.replaceInvokeWithNullReceiver(invoke);
                    return;
                }
            }
            if (newStampOrConstant != null) {
                ValueNode pi = insertPi(argument, newStampOrConstant, beforeInvoke);
                if (pi != null && pi != argument) {
                    callTarget.replaceAllInputs(argument, pi);
                }
            }
        }

        boolean hasReceiver = invokeFlow.getTargetMethod().hasReceiver();
        /*
         * The receiver's analysis results are complete when either:
         *
         * 1. We are in the closed world.
         *
         * 2. The receiver TypeFlow's type is a closed type, so it may be not extended in a later
         * layer.
         *
         * 3. The receiver TypeFlow is not saturated.
         *
         * Otherwise, when the receiver's analysis results are incomplete, then it is possible for
         * more types to be observed in subsequent layers.
         */
        boolean receiverAnalysisResultsComplete = strengthenGraphs.isClosedTypeWorld ||
                        (hasReceiver && (bb.isClosed(invokeFlow.getReceiverType()) || !methodFlow.isSaturated(bb, invokeFlow.getReceiver())));

        if (callTarget.invokeKind().isDirect()) {
            /*
             * Note: A direct invoke doesn't necessarily imply that the analysis should have
             * discovered a single callee. When dealing with interfaces it is in fact possible that
             * the Graal stamps are more accurate than the analysis results. So an interface call
             * may have already been optimized to a special call by stamp strengthening of the
             * receiver object, hence the invoke kind is direct, whereas the points-to analysis
             * inaccurately concluded there can be more than one callee.
             *
             * Below we just check that if there is a direct invoke *and* the analysis discovered a
             * single callee, then the callee should match the target method.
             */
            if (callees.size() == 1) {
                AnalysisMethod singleCallee = callees.iterator().next();
                assert targetMethod.equals(singleCallee) : "Direct invoke target mismatch: " + targetMethod + " != " + singleCallee + ". Called from " + graph.method().format("%H.%n");
            }
        } else if (GuestAnnotationAccess.isAnnotationPresent(targetMethod, Delete.class)) {
            /* We de-virtualize invokes to deleted methods since the callee must be unique. */
            AnalysisError.guarantee(callees.size() == 1, "@Delete methods should have a single callee.");
            AnalysisMethod singleCallee = callees.iterator().next();
            devirtualizeInvoke(singleCallee, invoke);
        } else if (targetMethod.canBeStaticallyBound() || (receiverAnalysisResultsComplete && callees.size() == 1)) {
            /*
             * A method can be devirtualized if there is only one possible callee. This can be
             * determined by the following ways:
             *
             * 1. The method can be trivially statically bound, as determined independently of
             * analysis results.
             *
             * 2. Analysis results indicate there is only one callee. The analysis results are
             * required to be complete, as there could be more than only one callee in subsequent
             * layers.
             */
            assert callees.size() == 1;
            AnalysisMethod singleCallee = callees.iterator().next();
            devirtualizeInvoke(singleCallee, invoke);
        } else {
            TypeState receiverTypeState = null;
            if (hasReceiver) {
                if (methodFlow.isSaturated(bb, invokeFlow.getReceiver())) {
                    /*
                     * Saturated receivers can be all instantiated subtypes of the target method's
                     * declaring class. Note if receiverAnalysisResultsComplete is false then new
                     * types may be seen later; however, this still serves as an optimistic
                     * approximation.
                     */
                    receiverTypeState = targetMethod.getDeclaringClass().getTypeFlow(bb, true).getState();
                } else {
                    assert receiverAnalysisResultsComplete;
                    receiverTypeState = methodFlow.foldTypeFlow(bb, invokeFlow.getReceiver());
                }
            }
            assignInvokeProfiles(invoke, invokeFlow, callees, receiverTypeState, !receiverAnalysisResultsComplete);
        }

        if (allowOptimizeReturnParameter && (strengthenGraphs.isClosedTypeWorld || callTarget.invokeKind().isDirect() || targetMethod.canBeStaticallyBound())) {
            /* Can only optimize returned parameter when all possible callees are visible. */
            optimizeReturnedParameter(callees, arguments, node, tool);
        }

        FixedWithNextNode anchorPointAfterInvoke = (FixedWithNextNode) (invoke instanceof InvokeWithExceptionNode ? invoke.next() : invoke);
        TypeFlow<?> nodeFlow = invokeFlow.getResult();
        if (nodeFlow != null && node.getStackKind() == JavaKind.Void && !methodFlow.isSaturated(bb, nodeFlow)) {
            /*
             * We track the reachability of return statements in void methods via returning either
             * Empty or AnyPrimitive TypeState, therefore we perform an emptiness check.
             */
            var typeState = methodFlow.foldTypeFlow(bb, nodeFlow);
            if (typeState.isEmpty() && unreachableValues.add(node)) {
                strengtheningSupport.makeUnreachable(anchorPointAfterInvoke.next(), tool, () -> strengtheningSupport.location(node) + ": return from void method was proven unreachable");
            }
        }
        Object newStampOrConstant = typeFlowStrengthener.strengthenStampFromTypeFlow(node, nodeFlow, anchorPointAfterInvoke, tool);
        updateStampUsingPiNode(node, newStampOrConstant, anchorPointAfterInvoke, tool);
    }

    /**
     * The invoke has only one callee, i.e., the call can be devirtualized to this callee. This
     * allows later inlining of the callee.
     */
    private void devirtualizeInvoke(AnalysisMethod singleCallee, Invoke invoke) {
        if (ImageBuildStatistics.Options.CollectImageBuildStatistics.getValue(graph.getOptions())) {
            ImageBuildStatistics.counters().incDevirtualizedInvokeCounter();
        }

        Stamp anchoredReceiverStamp = StampFactory.object(TypeReference.createWithoutAssumptions(singleCallee.getDeclaringClass()));
        ValueNode piReceiver = insertPi(invoke.getReceiver(), anchoredReceiverStamp, (FixedWithNextNode) invoke.asNode().predecessor());
        if (piReceiver != null) {
            invoke.callTarget().replaceFirstInput(invoke.getReceiver(), piReceiver);
        }

        assert invoke.getInvokeKind().isIndirect() : invoke;
        invoke.callTarget().setInvokeKind(CallTargetNode.InvokeKind.Special);
        invoke.callTarget().setTargetMethod(singleCallee);
    }

    private void assignInvokeProfiles(Invoke invoke, InvokeTypeFlow invokeFlow, Collection<AnalysisMethod> callees, TypeState receiverTypeState, boolean assumeNotRecorded) {
        /*
         * In an open type world we cannot trust the type state of the receiver for virtual calls as
         * new subtypes could be added later.
         *
         * Note: assumeNotRecorded specifies if profiles are injected for a closed or open world.
         * For a closed world with precise analysis results we never have a notRecordedProbabiltiy
         * in any profile. For the open world we always assume that there is a not recorded
         * probability in the profile. Such a not recorded probability will be injected if
         * assumeNotRecorded==true.
         */
        JavaTypeProfile typeProfile = strengthenGraphs.makeTypeProfile(receiverTypeState, assumeNotRecorded);
        /*
         * In a closed type world analysis the method profile of an invoke is complete and contains
         * all the callees reachable at that invocation location. Even if that invoke is saturated
         * it is still correct as it contains all the reachable implementations of the target
         * method. However, in an open type world the method profile of an invoke, saturated or not,
         * is incomplete, as there can be implementations that we haven't yet seen.
         */
        JavaMethodProfile methodProfile = strengthenGraphs.makeMethodProfile(callees, assumeNotRecorded);

        assert typeProfile == null || typeProfile.getTypes().length > 1 || assumeNotRecorded : "Should devirtualize with typeProfile=" + typeProfile + " and methodProfile=" + methodProfile +
                        " and callees" + callees + " invoke " + invokeFlow + " " + invokeFlow.getReceiver() + " in method " + StrengthenGraphs.getQualifiedName(graph);
        assert methodProfile == null || methodProfile.getMethods().length > 1 || assumeNotRecorded : "Should devirtualize with typeProfile=" + typeProfile + " and methodProfile=" + methodProfile +
                        " and callees" + callees + " invoke " + invokeFlow + " " + invokeFlow.getReceiver() + " in method " + StrengthenGraphs.getQualifiedName(graph);

        strengthenGraphs.setInvokeProfiles(invoke, typeProfile, methodProfile);
    }

    /**
     * If all possible callees return the same parameter, then we can replace the invoke with that
     * parameter at all usages. This is the same that would happen when the callees are inlined. So
     * we get a bit of the benefits of method inlining without actually performing the inlining.
     */
    private static void optimizeReturnedParameter(Collection<AnalysisMethod> callees, NodeInputList<ValueNode> arguments, FixedNode invoke, SimplifierTool tool) {
        int returnedParameterIndex = -1;
        for (AnalysisMethod callee : callees) {
            if (callee.hasNeverInlineDirective()) {
                /*
                 * If the method is explicitly marked as "never inline", it might be an intentional
                 * sink to prevent an optimization. Mostly, this is a pattern we use in unit tests.
                 * So this reduces the surprise that tests are "too well optimized" without doing
                 * any harm for real-world methods.
                 */
                return;
            }
            int returnedCalleeParameterIndex = PointsToAnalysis.assertPointsToAnalysisMethod(callee).getTypeFlow().getReturnedParameterIndex();
            if (returnedCalleeParameterIndex == -1) {
                /* This callee does not return a parameter. */
                return;
            }
            if (returnedParameterIndex == -1) {
                returnedParameterIndex = returnedCalleeParameterIndex;
            } else if (returnedParameterIndex != returnedCalleeParameterIndex) {
                /* This callee returns a different parameter than a previous callee. */
                return;
            }
        }
        assert returnedParameterIndex != -1 : callees;

        ValueNode returnedActualParameter = arguments.get(returnedParameterIndex);
        tool.addToWorkList(invoke.usages());
        invoke.replaceAtUsages(returnedActualParameter);
    }

    private void updateStampUsingPiNode(ValueNode node, Object newStampOrConstant, FixedWithNextNode anchorPoint, SimplifierTool tool) {
        if (newStampOrConstant != null && node.hasUsages()) {
            ValueNode pi = insertPi(node, newStampOrConstant, anchorPoint);
            if (pi != null) {
                if (pi.isConstant()) {
                    node.replaceAtUsages(pi);
                } else {
                    FrameState anchorState = node instanceof StateSplit ? ((StateSplit) node).stateAfter() : graph.start().stateAfter();
                    node.replaceAtUsages(pi, usage -> usage != pi && usage != anchorState);
                }
                tool.addToWorkList(pi.usages());
            }
        }
    }

    /**
     * See comment on {@link StrengthenGraphs} on why anchoring is necessary.
     */
    private ValueNode insertPi(ValueNode input, Object newStampOrConstant, FixedWithNextNode anchorPoint) {
        if (newStampOrConstant instanceof JavaConstant constant) {
            if (input.isConstant()) {
                assert bb.getConstantReflectionProvider().constantEquals(input.asConstant(), constant) : input.asConstant() + ", " + constant;
                return null;
            }
            return ConstantNode.forConstant(constant, bb.getMetaAccess(), graph);
        }

        Stamp piStamp = (Stamp) newStampOrConstant;
        Stamp reachabilityStamp = strengtheningSupport.strengthenStamp(piStamp);
        if (reachabilityStamp != null) {
            /* Analysis-created PiNodes are not revisited, so initialize their complete stamp now. */
            piStamp = piStamp.improveWith(reachabilityStamp);
        }
        Stamp oldStamp = input.stamp(NodeView.DEFAULT);
        Stamp computedStamp = oldStamp.improveWith(piStamp);
        if (oldStamp.equals(computedStamp)) {
            /* The PiNode does not give any additional information. */
            return null;
        }

        ValueAnchorNode anchor = graph.add(new ValueAnchorNode());
        graph.addAfterFixed(anchorPoint, anchor);
        return graph.unique(new PiNode(input, piStamp, anchor));
    }

}
