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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.graalvm.collections.EconomicSet;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.flow.PrimitiveFilterTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.PointsToAnalysisField;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.typestate.PrimitiveConstantTypeState;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.util.AnnotationUtil;
import com.oracle.svm.util.ImageBuildStatistics;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
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
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.nodes.extended.ValueAnchorNode;
import jdk.graal.compiler.nodes.java.ClassIsAssignableFromNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
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
import jdk.vm.ci.meta.PrimitiveConstant;

/**
 * Simplify graphs based on reachability information tracked by the static analysis. Additionally,
 * simplify graphs based on more concrete type information proven by the points-to analysis. The
 * optimizations enabled by the points-to analysis are a superset of the optimizations enabled by
 * the reachability analysis.
 */
class TypeFlowSimplifier extends ReachabilitySimplifier {

    private final PointsToAnalysis analysis;

    private final MethodTypeFlow methodFlow;
    private final TypeFlow<?>[] parameterFlows;
    private final NodeMap<TypeFlow<?>> nodeFlows;

    private final boolean allowConstantFolding;
    private final boolean allowOptimizeReturnParameter;

    private final EconomicSet<ValueNode> unreachableValues = EconomicSet.create();
    private final NodeBitMap createdPiNodes;

    TypeFlowSimplifier(StrengthenGraphs strengthenGraphs, AnalysisMethod method, StructuredGraph graph) {
        super(strengthenGraphs, method, graph);
        analysis = (PointsToAnalysis) strengthenGraphs.bb;
        methodFlow = ((PointsToAnalysisMethod) method).getTypeFlow();
        AnalysisError.guarantee(methodFlow.flowsGraphCreated(), "Trying to strengthen a method without a type flows graph: %s.", method);
        MethodFlowsGraph originalFlows = methodFlow.getMethodFlowsGraph();
        parameterFlows = originalFlows.getParameters();
        nodeFlows = new NodeMap<>(graph);
        var cursor = originalFlows.getNodeFlows().getEntries();
        while (cursor.advance()) {
            Node node = cursor.getKey().getNode();
            assert nodeFlows.get(node) == null : "overwriting existing entry for " + node;
            nodeFlows.put(node, cursor.getValue());
        }
        createdPiNodes = new NodeBitMap(graph);

        allowConstantFolding = strengthenGraphs.strengthenGraphWithConstants && analysis.getHostVM().allowConstantFolding(method);

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
        allowOptimizeReturnParameter = method.isOriginalMethod() && analysis.optimizeReturnedParameter();
    }

    private TypeFlow<?> getNodeFlow(Node node) {
        return nodeFlows == null || nodeFlows.isNew(node) ? null : nodeFlows.get(node);
    }

    @Override
    public void simplify(Node n, SimplifierTool tool) {
        if (n instanceof ValueNode node) {
            super.tryImproveStamp(node, tool);
        }

        if (strengthenGraphs.simplifyDelegate(n, tool)) {
            // Handled in the delegate simplification.
            return;
        }

        switch (n) {
            case ParameterNode node -> handleParameter(node, tool);
            case LoadFieldNode node -> handleLoadField(node, tool);
            case LoadIndexedNode node -> handleLoadIndexed(node, tool);
            case IfNode node -> handleIf(node, tool);
            case FixedGuardNode node -> handleFixedGuard(node, tool);
            case Invoke invoke -> handleInvoke(invoke, tool);
            // Next simplifications don't use type states and are shared with reachability analysis.
            case InstanceOfNode node -> super.handleInstanceOf(node, tool);
            case ClassIsAssignableFromNode node -> super.handleClassIsAssignableFrom(node, tool);
            case BytecodeExceptionNode node -> super.handleBytecodeException(node, tool);
            case FrameState node -> super.handleFrameState(node);
            case PiNode node -> super.handlePi(node, tool);
            case null, default -> {
            }
        }
    }

    private void handleParameter(ParameterNode node, SimplifierTool tool) {
        StartNode anchorPoint = graph.start();
        Object newStampOrConstant = strengthenStampFromTypeFlow(node, parameterFlows[node.index()], anchorPoint, tool);
        updateStampUsingPiNode(node, newStampOrConstant, anchorPoint, tool);
    }

    private void handleLoadField(LoadFieldNode node, SimplifierTool tool) {
        /*
         * First step: it is beneficial to strengthen the stamp of the LoadFieldNode because then
         * there is no artificial anchor after which the more precise type is available. However,
         * the memory load will be a floating node later, so we can only update the stamp directly
         * to the stamp that is correct for the whole method and all inlined methods.
         */
        PointsToAnalysisField field = (PointsToAnalysisField) node.field();
        Object fieldNewStampOrConstant = strengthenStampFromTypeFlow(node, field.getSinkFlow(), node, tool);
        if (fieldNewStampOrConstant instanceof JavaConstant) {
            ConstantNode replacement = ConstantNode.forConstant((JavaConstant) fieldNewStampOrConstant, analysis.getMetaAccess(), graph);
            graph.replaceFixedWithFloating(node, replacement);
            tool.addToWorkList(replacement);
        } else {
            super.updateStampInPlace(node, (Stamp) fieldNewStampOrConstant, tool);

            /*
             * Second step: strengthen using context-sensitive analysis results, which requires an
             * anchored PiNode.
             */
            Object nodeNewStampOrConstant = strengthenStampFromTypeFlow(node, getNodeFlow(node), node, tool);
            updateStampUsingPiNode(node, nodeNewStampOrConstant, node, tool);
        }
    }

    private void handleLoadIndexed(LoadIndexedNode node, SimplifierTool tool) {
        Object newStampOrConstant = strengthenStampFromTypeFlow(node, getNodeFlow(node), node, tool);
        updateStampUsingPiNode(node, newStampOrConstant, node, tool);
    }

    private void handleIf(IfNode node, SimplifierTool tool) {
        boolean trueUnreachable = isUnreachable(node.trueSuccessor());
        boolean falseUnreachable = isUnreachable(node.falseSuccessor());

        if (trueUnreachable && falseUnreachable) {
            super.makeUnreachable(node, tool, () -> super.location(node) + ": both successors of IfNode are unreachable");

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

    private boolean isUnreachable(Node branch) {
        TypeFlow<?> branchFlow = getNodeFlow(branch);
        if (branchFlow != null && !methodFlow.isSaturated(analysis, branchFlow)) {
            if (!branchFlow.isFlowEnabled()) {
                return true;
            }
            TypeState typeState = methodFlow.foldTypeFlow(analysis, branchFlow);
            if (branchFlow.isPrimitiveFlow()) {
                /*
                 * This assert is a safeguard to verify the assumption that only one type of flow
                 * has to be considered as a branch predicate at the moment.
                 */
                assert branchFlow instanceof PrimitiveFilterTypeFlow : "Unexpected type of primitive flow encountered as branch predicate: " + branchFlow;
            }
            return typeState.isEmpty();
        }
        return false;
    }

    private void handleInvoke(Invoke invoke, SimplifierTool tool) {
        if (!(invoke.callTarget() instanceof MethodCallTargetNode callTarget)) {
            return;
        }
        if (super.maybeMarkUnreachable(invoke, tool)) {
            /* Invoke is unreachable, there is no point in improving any types further. */
            return;
        }

        FixedNode node = invoke.asFixedNode();
        InvokeTypeFlow invokeFlow = (InvokeTypeFlow) getNodeFlow(node);
        if (invokeFlow == null) {
            /* No points-to analysis results. */
            return;
        }
        if (!invokeFlow.isFlowEnabled()) {
            super.unreachableInvoke(invoke, tool, () -> super.location(invoke) + ": flow is not enabled by its predicate " + invokeFlow.getPredicate());
            /* Invoke is unreachable, there is no point in improving any types further. */
            return;
        }

        AnalysisMethod targetMethod = (AnalysisMethod) callTarget.targetMethod();

        Collection<AnalysisMethod> callees = invokeFlow.getOriginalCallees();
        if (callees.isEmpty()) {
            if (strengthenGraphs.isClosedTypeWorld) {
                /* Invoke is unreachable, there is no point in improving any types further. */
                super.unreachableInvoke(invoke, tool, () -> super.location(invoke) + ": empty list of callees for call to " + targetMethod.getQualifiedName());
            }
            /* In open world we cannot make any assumptions about an invoke with 0 callees. */
            return;
        }
        assert invokeFlow.isFlowEnabled() : "Disabled invoke should have no callees: " + invokeFlow + ", in method " + StrengthenGraphs.getQualifiedName(graph);

        FixedWithNextNode beforeInvoke = (FixedWithNextNode) invoke.predecessor();
        NodeInputList<ValueNode> arguments = callTarget.arguments();
        for (int i = 0; i < arguments.size(); i++) {
            ValueNode argument = arguments.get(i);
            Object newStampOrConstant = strengthenStampFromTypeFlow(argument, invokeFlow.getActualParameters()[i], beforeInvoke, tool);
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
                    invokeWithNullReceiver(invoke);
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
         * 3. The receiver TypeFlow's type is a core type, so it may be not extended in a later
         * layer. (GR-70846: This check condition will be merged with the previous one once core
         * types are fully considered as closed.)
         *
         * 4. The receiver TypeFlow is not saturated.
         *
         * Otherwise, when the receiver's analysis results are incomplete, then it is possible for
         * more types to be observed in subsequent layers.
         */
        boolean receiverAnalysisResultsComplete = strengthenGraphs.isClosedTypeWorld ||
                        (hasReceiver && (analysis.isClosed(invokeFlow.getReceiverType()) || analysis.getHostVM().isCoreType(invokeFlow.getReceiverType()) ||
                                        !methodFlow.isSaturated(analysis, invokeFlow.getReceiver())));

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
        } else if (AnnotationUtil.isAnnotationPresent(targetMethod, Delete.class)) {
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
                if (methodFlow.isSaturated(analysis, invokeFlow.getReceiver())) {
                    /*
                     * Saturated receivers can be all instantiated subtypes of the target method's
                     * declaring class. Note if receiverAnalysisResultsComplete is false then new
                     * types may be seen later; however, this still serves as an optimistic
                     * approximation.
                     */
                    receiverTypeState = targetMethod.getDeclaringClass().getTypeFlow(analysis, true).getState();
                } else {
                    assert receiverAnalysisResultsComplete;
                    receiverTypeState = methodFlow.foldTypeFlow(analysis, invokeFlow.getReceiver());
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
        if (nodeFlow != null && node.getStackKind() == JavaKind.Void && !methodFlow.isSaturated(analysis, nodeFlow)) {
            /*
             * We track the reachability of return statements in void methods via returning either
             * Empty or AnyPrimitive TypeState, therefore we perform an emptiness check.
             */
            var typeState = methodFlow.foldTypeFlow(analysis, nodeFlow);
            if (typeState.isEmpty() && unreachableValues.add(node)) {
                super.makeUnreachable(anchorPointAfterInvoke.next(), tool, () -> super.location(node) + ": return from void method was proven unreachable");
            }
        }
        Object newStampOrConstant = strengthenStampFromTypeFlow(node, nodeFlow, anchorPointAfterInvoke, tool);
        updateStampUsingPiNode(node, newStampOrConstant, anchorPointAfterInvoke, tool);
    }

    /**
     * The invoke always has a null receiver, so it can be removed.
     */
    private void invokeWithNullReceiver(Invoke invoke) {
        FixedNode replacement = strengthenGraphs.createInvokeWithNullReceiverReplacement(graph);
        ((FixedWithNextNode) invoke.predecessor()).setNext(replacement);
        GraphUtil.killCFG(invoke.asFixedNode());
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
        if (newStampOrConstant != null && node.hasUsages() && !createdPiNodes.isMarked(node)) {
            ValueNode pi = insertPi(node, newStampOrConstant, anchorPoint);
            if (pi != null) {
                /*
                 * The Canonicalizer that drives all of our node processing is iterative. We only
                 * want to insert the PiNode the first time we handle a node.
                 */
                createdPiNodes.mark(node);

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
                assert analysis.getConstantReflectionProvider().constantEquals(input.asConstant(), constant) : input.asConstant() + ", " + constant;
                return null;
            }
            return ConstantNode.forConstant(constant, analysis.getMetaAccess(), graph);
        }

        Stamp piStamp = (Stamp) newStampOrConstant;
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

    private Object strengthenStampFromTypeFlow(ValueNode node, TypeFlow<?> nodeFlow, FixedWithNextNode anchorPoint, SimplifierTool tool) {
        if (nodeFlow == null || !analysis.isSupportedJavaKind(node.getStackKind())) {
            return null;
        }
        if (methodFlow.isSaturated(analysis, nodeFlow)) {
            /* The type flow is saturated, its type state does not matter. */
            return null;
        }
        if (unreachableValues.contains(node)) {
            /* This node has already been made unreachable - no further action is needed. */
            return null;
        }
        /*
         * If there are no usages of the node, then adding a PiNode would only bloat the graph.
         * However, we don't immediately return null since the stamp can still indicate this node is
         * unreachable.
         */
        boolean hasUsages = node.usages().filter(n -> !(n instanceof FrameState)).isNotEmpty();

        if (!nodeFlow.isFlowEnabled()) {
            super.makeUnreachable(anchorPoint.next(), tool, () -> super.location(node) + ": flow is not enabled by its predicate " + nodeFlow.getPredicate());
            unreachableValues.add(node);
            return null;
        }
        TypeState nodeTypeState = methodFlow.foldTypeFlow(analysis, nodeFlow);

        if (hasUsages && allowConstantFolding && !nodeTypeState.canBeNull()) {
            JavaConstant constantValue = nodeTypeState.asConstant();
            if (constantValue != null) {
                return constantValue;
            }
        }

        node.inferStamp();
        Stamp s = node.stamp(NodeView.DEFAULT);
        if (s.isIntegerStamp() || nodeTypeState.isPrimitive()) {
            return getIntegerStamp(node, ((IntegerStamp) s), anchorPoint, nodeTypeState, tool);
        }

        ObjectStamp oldStamp = (ObjectStamp) s;
        AnalysisType oldType = (AnalysisType) oldStamp.type();
        boolean nonNull = oldStamp.nonNull() || !nodeTypeState.canBeNull();

        /*
         * Find all types of the TypeState that are compatible with the current stamp. Since stamps
         * are propagated around immediately by the Canonicalizer it is possible and allowed that
         * the stamp is already more precise than the static analysis results.
         */
        List<AnalysisType> typeStateTypes = new ArrayList<>(nodeTypeState.typesCount());
        for (AnalysisType typeStateType : nodeTypeState.types(analysis)) {
            if (oldType == null || (oldStamp.isExactType() ? oldType.equals(typeStateType) : oldType.isJavaLangObject() || oldType.isAssignableFrom(typeStateType))) {
                typeStateTypes.add(typeStateType);
            }
        }

        if (typeStateTypes.isEmpty()) {
            if (nonNull) {
                super.makeUnreachable(anchorPoint.next(), tool, () -> super.location(node) + ": empty object type state when strengthening oldStamp " + oldStamp);
                unreachableValues.add(node);
                return null;
            } else {
                return hasUsages ? StampFactory.alwaysNull() : null;
            }

        } else if (!hasUsages) {
            // no need to return strengthened stamp if it is unused
            return null;
        } else if (typeStateTypes.size() == 1) {
            AnalysisType exactType = typeStateTypes.get(0);
            assert strengthenGraphs.getSingleImplementorType(exactType) == null || exactType.equals(strengthenGraphs.getSingleImplementorType(exactType)) : "exactType=" + exactType +
                            ", singleImplementor=" + strengthenGraphs.getSingleImplementorType(exactType);
            assert exactType.equals(strengthenGraphs.getStrengthenStampType(exactType)) : exactType;

            if (!oldStamp.isExactType() || !exactType.equals(oldType)) {
                if (typePredicate.test(exactType)) {
                    TypeReference typeRef = TypeReference.createExactTrusted(exactType);
                    return StampFactory.object(typeRef, nonNull);
                }
            }

        } else if (!oldStamp.isExactType()) {
            assert typeStateTypes.size() > 1 : typeStateTypes;
            AnalysisType baseType = typeStateTypes.get(0);
            for (int i = 1; i < typeStateTypes.size(); i++) {
                if (baseType.isJavaLangObject()) {
                    break;
                }
                baseType = baseType.findLeastCommonAncestor(typeStateTypes.get(i));
            }

            if (oldType != null && !oldType.isAssignableFrom(baseType)) {
                /*
                 * When the original stamp is an interface type, we do not want to weaken that type
                 * with the common base class of all implementation types (which could even be
                 * java.lang.Object).
                 */
                baseType = oldType;
            }

            /*
             * With more than one type in the type state, there cannot be a single implementor.
             * Because that single implementor would need to be the only type in the type state.
             */
            assert strengthenGraphs.getSingleImplementorType(baseType) == null || baseType.equals(strengthenGraphs.getSingleImplementorType(baseType)) : "baseType=" + baseType +
                            ", singleImplementor=" + strengthenGraphs.getSingleImplementorType(baseType);

            AnalysisType newType = strengthenGraphs.getStrengthenStampType(baseType);

            assert typeStateTypes.stream().map(newType::isAssignableFrom).reduce(Boolean::logicalAnd).get() : typeStateTypes;

            if (!newType.equals(oldType) && (oldType != null || !newType.isJavaLangObject())) {
                if (typePredicate.test(newType)) {
                    TypeReference typeRef = TypeReference.createTrustedWithoutAssumptions(newType);
                    return StampFactory.object(typeRef, nonNull);
                }
            }
        }

        if (nonNull != oldStamp.nonNull()) {
            assert nonNull : oldStamp;
            return oldStamp.asNonNull();
        }
        /* Nothing to strengthen. */
        return null;
    }

    private IntegerStamp getIntegerStamp(ValueNode node, IntegerStamp originalStamp, FixedWithNextNode anchorPoint, TypeState nodeTypeState, SimplifierTool tool) {
        assert analysis.trackPrimitiveValues() : nodeTypeState + "," + node + " in " + node.graph();
        assert nodeTypeState != null && (nodeTypeState.isEmpty() || nodeTypeState.isPrimitive()) : nodeTypeState + "," + node + " in " + node.graph();
        if (nodeTypeState.isEmpty()) {
            super.makeUnreachable(anchorPoint.next(), tool, () -> super.location(node) + ": empty primitive type state when strengthening oldStamp " + originalStamp);
            unreachableValues.add(node);
            return null;
        }
        if (nodeTypeState instanceof PrimitiveConstantTypeState constantTypeState) {
            long constantValue = constantTypeState.getValue();
            if (node instanceof ConstantNode constant) {
                /*
                 * Sanity check, verify that what was proven by the analysis is consistent with the
                 * constant node in the graph.
                 */
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
