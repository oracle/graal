/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.Supplier;

import org.graalvm.compiler.core.common.type.AbstractObjectStamp;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.GraphEncoder;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.LogicConstantNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode;
import org.graalvm.compiler.nodes.extended.ValueAnchorNode;
import org.graalvm.compiler.nodes.java.ClassIsAssignableFromNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.spi.LimitedValueProxy;
import org.graalvm.compiler.nodes.spi.SimplifierTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase.CustomSimplification;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.infrastructure.Universe;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.svm.util.ImageBuildStatistics;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This class applies static analysis results directly to the {@link StructuredGraph Graal IR} used
 * to build the type flow graph.
 * 
 * It uses a {@link CustomSimplification} for the {@link CanonicalizerPhase}, because that provides
 * all the framework for iterative stamp propagation and adding/removing control flow nodes while
 * processing the graph.
 * 
 * From the single-method view that the compiler has when later compiling the graph, static analysis
 * results appear "out of thin air": At some some random point in the graph, we suddenly have a more
 * precise type (= stamp) for a value. Since many nodes are floating, and even currently fixed nodes
 * might float later, we need to be careful that all information coming from the type flow graph
 * remains properly anchored to the point where the static analysis actually proved the information.
 * So we cannot just change the stamp of, e.g., the parameter of a method invocation, to a more
 * precise stamp. We need to do that indirectly by adding a {@link PiNode} that is anchored using a
 * {@link ValueAnchorNode}.
 */
public abstract class StrengthenGraphs extends AbstractAnalysisResultsBuilder {

    public StrengthenGraphs(PointsToAnalysis bb, Universe converter) {
        super(bb, converter);
    }

    private PointsToAnalysis getAnalysis() {
        return ((PointsToAnalysis) bb);
    }

    @Override
    @SuppressWarnings("try")
    public StaticAnalysisResults makeOrApplyResults(AnalysisMethod m) {
        PointsToAnalysisMethod method = PointsToAnalysis.assertPointsToAnalysisMethod(m);
        MethodTypeFlow methodTypeFlow = method.getTypeFlow();
        if (!methodTypeFlow.flowsGraphCreated()) {
            return StaticAnalysisResults.NO_RESULTS;
        }
        DebugContext debug = new DebugContext.Builder(bb.getOptions(), new GraalDebugHandlersFactory(bb.getProviders().getSnippetReflection())).build();
        StructuredGraph graph = method.decodeAnalyzedGraph(debug, methodTypeFlow.getMethodFlowsGraph().getNodeFlows().getKeys());
        if (graph != null) {
            graph.resetDebug(debug);
            try (DebugContext.Scope s = debug.scope("StrengthenGraphs", graph);
                            DebugContext.Activation a = debug.activate()) {
                CanonicalizerPhase.create().copyWithCustomSimplification(new StrengthenSimplifier(method, graph)).apply(graph, bb.getProviders());
            } catch (Throwable ex) {
                debug.handle(ex);
            }

            method.setAnalyzedGraph(GraphEncoder.encodeSingleGraph(graph, AnalysisParsedGraph.HOST_ARCHITECTURE));
        }

        /* Ensure that the temporarily decoded graph is not kept alive via the node references. */
        var cursor = methodTypeFlow.getMethodFlowsGraph().getNodeFlows().getEntries();
        while (cursor.advance()) {
            cursor.getKey().clear();
        }
        /*
         * All information from the static analysis has been incorporated into the graph. There is
         * no benefit in keeping results around anymore.
         */
        return StaticAnalysisResults.NO_RESULTS;
    }

    @Override
    public JavaTypeProfile makeTypeProfile(AnalysisField field) {
        /*
         * Since LoadFieldNode are improved directly in this class, there is no need to provide a
         * type profile for fields.
         */
        return null;
    }

    /*
     * Returns a type that can replace the original type in stamps as an exact type. When the
     * returned type is the original type itself, the original type has no subtype and can be used
     * as an exact type.
     * 
     * Returns null if there is no single implementor type.
     */
    protected abstract AnalysisType getSingleImplementorType(AnalysisType originalType);

    /*
     * Returns a type that can replace the original type in stamps.
     * 
     * Returns null if the original type has no assignable type that is instantiated, i.e., the code
     * using the type is unreachable.
     * 
     * Returns the original type itself if there is no optimization potential, i.e., if the original
     * type itself is instantiated or has more than one instantiated direct subtype.
     */
    protected abstract AnalysisType getStrengthenStampType(AnalysisType originalType);

    protected abstract FixedNode createUnreachable(StructuredGraph graph, CoreProviders providers, Supplier<String> message);

    protected abstract void setInvokeProfiles(Invoke invoke, JavaTypeProfile typeProfile, JavaMethodProfile methodProfile);

    protected abstract String getTypeName(AnalysisType type);

    class StrengthenSimplifier implements CustomSimplification {

        private final StructuredGraph graph;
        private final MethodTypeFlow methodFlow;

        private final NodeBitMap createdPiNodes;

        private final TypeFlow<?>[] parameterFlows;
        private final NodeMap<TypeFlow<?>> nodeFlows;

        StrengthenSimplifier(PointsToAnalysisMethod method, StructuredGraph graph) {
            this.graph = graph;
            this.methodFlow = method.getTypeFlow();
            this.createdPiNodes = new NodeBitMap(graph);

            MethodFlowsGraph originalFlows = methodFlow.getMethodFlowsGraph();
            parameterFlows = originalFlows.getParameters();
            nodeFlows = new NodeMap<>(graph);
            var cursor = originalFlows.getNodeFlows().getEntries();
            while (cursor.advance()) {
                Node node = cursor.getKey().getNode();
                assert nodeFlows.get(node) == null : "overwriting existing entry for " + node;
                nodeFlows.put(node, cursor.getValue());
            }
        }

        private TypeFlow<?> getNodeFlow(Node node) {
            return nodeFlows.isNew(node) ? null : nodeFlows.get(node);
        }

        @Override
        public void simplify(Node n, SimplifierTool tool) {
            if (n instanceof ValueNode && !(n instanceof LimitedValueProxy) && !(n instanceof PhiNode)) {
                /*
                 * The stamp of proxy nodes and phi nodes is inferred automatically, so we do not
                 * need to improve them.
                 */
                ValueNode node = (ValueNode) n;
                /*
                 * First ask the node to improve the stamp itself, to incorporate already improved
                 * input stamps.
                 */
                node.inferStamp();
                /*
                 * Since this new stamp is not based on a type flow, it is valid for the entire
                 * method and we can update the stamp of the node directly. We do not need an
                 * anchored PiNode.
                 */
                updateStampInPlace(node, strengthenStamp(node.stamp(NodeView.DEFAULT)), tool);
            }

            if (n instanceof ParameterNode) {
                ParameterNode node = (ParameterNode) n;
                StartNode anchorPoint = graph.start();
                Stamp newStamp = strengthenStampFromTypeFlow(node, parameterFlows[node.index()], anchorPoint, tool);
                updateStampUsingPiNode(node, newStamp, anchorPoint, tool);

            } else if (n instanceof LoadFieldNode || n instanceof LoadIndexedNode) {
                FixedWithNextNode node = (FixedWithNextNode) n;
                Stamp newStamp = strengthenStampFromTypeFlow(node, getNodeFlow(node), node, tool);
                /*
                 * Even though the memory load will be a floating node later, we can update the
                 * stamp directly because the type information maintained by the static analysis
                 * about memory is not flow-sensitive and not context-sensitive. If we ever revive a
                 * context-sensitive analysis, we will need to change this. But for now, we are
                 * fine.
                 */
                updateStampInPlace(node, newStamp, tool);

            } else if (n instanceof Invoke) {
                Invoke invoke = (Invoke) n;
                if (invoke.callTarget() instanceof MethodCallTargetNode) {
                    handleInvoke(invoke, tool);
                }

            } else if (n instanceof IfNode) {
                IfNode node = (IfNode) n;
                boolean trueUnreachable = isUnreachable(node.trueSuccessor());
                boolean falseUnreachable = isUnreachable(node.falseSuccessor());

                if (trueUnreachable && falseUnreachable) {
                    makeUnreachable(node, tool, () -> "method " + graph.method().format("%H.%n(%p)") + ", node " + node + ": both successors of IfNode are unreachable");

                } else if (trueUnreachable || falseUnreachable) {
                    AbstractBeginNode killedBegin = node.successor(trueUnreachable);
                    AbstractBeginNode survivingBegin = node.successor(!trueUnreachable);

                    if (survivingBegin.hasUsages()) {
                        /*
                         * Even when we know that the IfNode is not necessary because the condition
                         * is statically proven, all PiNode that are anchored at the surviving
                         * branch must remain anchored at exactly this point. It would be wrong to
                         * anchor the PiNode at the BeginNode of the preceding block, because at
                         * that point the condition is not proven yet.
                         */
                        ValueAnchorNode anchor = graph.add(new ValueAnchorNode(null));
                        graph.addAfterFixed(survivingBegin, anchor);
                        survivingBegin.replaceAtUsages(anchor, InputType.Guard, InputType.Anchor);
                    }
                    graph.removeSplit(node, survivingBegin);
                    GraphUtil.killCFG(killedBegin);
                }

            } else if (n instanceof FixedGuardNode) {
                FixedGuardNode node = (FixedGuardNode) n;
                if (isUnreachable(node)) {
                    node.setCondition(LogicConstantNode.tautology(graph), true);
                    tool.addToWorkList(node);
                }

            } else if (n instanceof InstanceOfNode) {
                InstanceOfNode node = (InstanceOfNode) n;
                ObjectStamp oldStamp = node.getCheckedStamp();
                Stamp newStamp = strengthenStamp(oldStamp);
                if (newStamp != null) {
                    LogicNode replacement = graph.addOrUniqueWithInputs(InstanceOfNode.createHelper((ObjectStamp) oldStamp.improveWith(newStamp), node.getValue(), node.profile(), node.getAnchor()));
                    node.replaceAndDelete(replacement);
                    tool.addToWorkList(replacement);
                }

            } else if (n instanceof ClassIsAssignableFromNode) {
                ClassIsAssignableFromNode node = (ClassIsAssignableFromNode) n;
                AnalysisType nonReachableType = asConstantNonReachableType(node.getThisClass(), tool);
                if (nonReachableType != null) {
                    node.replaceAndDelete(LogicConstantNode.contradiction(graph));
                }

            } else if (n instanceof BytecodeExceptionNode) {
                /*
                 * We do not want a type to be reachable only to be used for the error message of a
                 * ClassCastException. Therefore, in that case we replace the java.lang.Class with a
                 * java.lang.String that is then used directly in the error message.
                 */
                BytecodeExceptionNode node = (BytecodeExceptionNode) n;
                if (node.getExceptionKind() == BytecodeExceptionNode.BytecodeExceptionKind.CLASS_CAST) {
                    AnalysisType nonReachableType = asConstantNonReachableType(node.getArguments().get(1), tool);
                    if (nonReachableType != null) {
                        node.getArguments().set(1, ConstantNode.forConstant(tool.getConstantReflection().forString(getTypeName(nonReachableType)), tool.getMetaAccess(), graph));
                    }
                }

            } else if (n instanceof FrameState) {
                /*
                 * We do not want a type to be reachable only to be used for debugging purposes in a
                 * FrameState. We could just null out the frame slot, but to leave as much
                 * information as possible we replace the java.lang.Class with the type name.
                 */
                FrameState node = (FrameState) n;
                for (int i = 0; i < node.values().size(); i++) {
                    AnalysisType nonReachableType = asConstantNonReachableType(node.values().get(i), tool);
                    if (nonReachableType != null) {
                        node.values().set(i, ConstantNode.forConstant(tool.getConstantReflection().forString(getTypeName(nonReachableType)), tool.getMetaAccess(), graph));
                    }
                }

            } else if (n instanceof PiNode) {
                PiNode node = (PiNode) n;
                Stamp oldStamp = node.piStamp();
                Stamp newStamp = strengthenStamp(oldStamp);
                if (newStamp != null) {
                    node.strengthenPiStamp(oldStamp.improveWith(newStamp));
                    tool.addToWorkList(node);
                }
            }
        }

        private AnalysisType asConstantNonReachableType(ValueNode value, CoreProviders providers) {
            if (value != null && value.isConstant()) {
                AnalysisType expectedType = (AnalysisType) providers.getConstantReflection().asJavaType(value.asConstant());
                if (expectedType != null && !expectedType.isReachable()) {
                    return expectedType;
                }
            }
            return null;
        }

        private void handleInvoke(Invoke invoke, SimplifierTool tool) {
            PointsToAnalysis pta = getAnalysis();
            FixedNode node = invoke.asFixedNode();
            MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();

            InvokeTypeFlow invokeFlow = (InvokeTypeFlow) getNodeFlow(node);
            Collection<AnalysisMethod> callees = invokeFlow.getCallees();
            if (callees.isEmpty()) {
                unreachableInvoke(invoke, tool);
                /* Invoke is unreachable, there is no point in improving any types further. */
                return;
            }

            FixedWithNextNode beforeInvoke = (FixedWithNextNode) invoke.predecessor();
            NodeInputList<ValueNode> arguments = callTarget.arguments();
            for (int i = 0; i < arguments.size(); i++) {
                ValueNode argument = arguments.get(i);
                Stamp newStamp = strengthenStampFromTypeFlow(argument, invokeFlow.getActualParameters()[i], beforeInvoke, tool);
                if (node.isDeleted()) {
                    /* Parameter stamp was empty, so invoke is unreachable. */
                    return;
                } else if (newStamp != null) {
                    PiNode pi = insertPi(argument, newStamp, beforeInvoke);
                    if (pi != null) {
                        callTarget.replaceAllInputs(argument, pi);
                    }
                }
            }

            if (callees.size() == 1) {
                AnalysisMethod singleCallee = callees.iterator().next();
                if (callTarget.invokeKind().isDirect()) {
                    assert callTarget.targetMethod().equals(singleCallee) : "Direct invoke target mismatch: " + callTarget.targetMethod() + " != " + singleCallee;
                } else {
                    devirtualizeInvoke(singleCallee, invoke);
                }

            } else {
                TypeState receiverTypeState = null;
                /* If the receiver flow is saturated, its exact type state does not matter. */
                if (invokeFlow.getTargetMethod().hasReceiver() && !methodFlow.isSaturated(pta, invokeFlow.getReceiver())) {
                    receiverTypeState = methodFlow.foldTypeFlow(pta, invokeFlow.getReceiver());
                }

                JavaTypeProfile typeProfile = makeTypeProfile(receiverTypeState);
                JavaMethodProfile methodProfile = makeMethodProfile(callees);
                assert typeProfile == null || typeProfile.getTypes().length > 1 : "Should devirtualize with typeProfile=" + typeProfile + " and methodProfile=" + methodProfile;
                assert methodProfile == null || methodProfile.getMethods().length > 1 : "Should devirtualize with typeProfile=" + typeProfile + " and methodProfile=" + methodProfile;

                setInvokeProfiles(invoke, typeProfile, methodProfile);
            }

            optimizeReturnedParameter(callees, arguments, node, tool);

            FixedWithNextNode anchorPointAfterInvoke = (FixedWithNextNode) (invoke instanceof InvokeWithExceptionNode ? invoke.next() : invoke);
            Stamp newStamp = strengthenStampFromTypeFlow(node, invokeFlow.getResult(), anchorPointAfterInvoke, tool);
            updateStampUsingPiNode(node, newStamp, anchorPointAfterInvoke, tool);
        }

        /**
         * If all possible callees return the same parameter, then we can replace the invoke with
         * that parameter at all usages. This is the same that would happen when the callees are
         * inlined. So we get a bit of the befits of method inlining without actually performing the
         * inlining.
         */
        private void optimizeReturnedParameter(Collection<AnalysisMethod> callees, NodeInputList<ValueNode> arguments, FixedNode invoke, SimplifierTool tool) {
            int returnedParameterIndex = -1;
            for (AnalysisMethod callee : callees) {
                if (callee.hasNeverInlineDirective()) {
                    /*
                     * If the method is explicitly marked as "never inline", it might be an
                     * intentional sink to prevent an optimization. Mostly, this is a pattern we use
                     * in unit tests. So this reduces the surprise that tests are
                     * "too well optimized" without doing any harm for real-world methods.
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
            assert returnedParameterIndex != -1;

            ValueNode returnedActualParameter = arguments.get(returnedParameterIndex);
            tool.addToWorkList(invoke.usages());
            invoke.replaceAtUsages(returnedActualParameter);
        }

        /**
         * The invoke has no callee, i.e., it is unreachable.
         */
        private void unreachableInvoke(Invoke invoke, SimplifierTool tool) {
            if (invoke.getInvokeKind() != CallTargetNode.InvokeKind.Static) {
                /*
                 * Ensure that a null check for the receiver remains in the graph. There should be
                 * already an explicit null check in the graph, but we are paranoid and check again.
                 */
                InliningUtil.nonNullReceiver(invoke);
            }

            makeUnreachable(invoke.asFixedNode(), tool, () -> "method " + graph.method().format("%H.%n(%p)") + ", node " + invoke +
                            ": empty list of callees for call to " + invoke.callTarget().targetMethod().format("%H.%n(%P)"));
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

            assert invoke.getInvokeKind().isIndirect();
            invoke.callTarget().setInvokeKind(CallTargetNode.InvokeKind.Special);
            invoke.callTarget().setTargetMethod(singleCallee);
        }

        private boolean isUnreachable(Node branch) {
            PointsToAnalysis pta = getAnalysis();
            TypeFlow<?> branchFlow = getNodeFlow(branch);
            return branchFlow != null &&
                            !methodFlow.isSaturated(pta, branchFlow) &&
                            methodFlow.foldTypeFlow(pta, branchFlow).isEmpty();
        }

        private void updateStampInPlace(ValueNode node, Stamp newStamp, SimplifierTool tool) {
            if (newStamp != null) {
                Stamp oldStamp = node.stamp(NodeView.DEFAULT);
                Stamp computedStamp = oldStamp.improveWith(newStamp);
                if (!oldStamp.equals(computedStamp)) {
                    node.setStamp(newStamp);
                    tool.addToWorkList(node.usages());
                }
            }
        }

        private void updateStampUsingPiNode(ValueNode node, Stamp newStamp, FixedWithNextNode anchorPoint, SimplifierTool tool) {
            if (newStamp != null && node.hasUsages() && !createdPiNodes.isMarked(node)) {
                PiNode pi = insertPi(node, newStamp, anchorPoint);
                if (pi != null) {
                    /*
                     * The Canonicalizer that drives all of our node processing is iterative. We
                     * only want to insert the PiNode the first time we handle a node.
                     */
                    createdPiNodes.mark(node);

                    FrameState anchorState = node instanceof StateSplit ? ((StateSplit) node).stateAfter() : graph.start().stateAfter();
                    node.replaceAtUsages(pi, usage -> usage != pi && usage != anchorState);

                    tool.addToWorkList(pi.usages());
                }
            }
        }

        /*
         * See comment on {@link StrengthenGraphs} on why anchoring is necessary.
         */
        private PiNode insertPi(ValueNode input, Stamp piStamp, FixedWithNextNode anchorPoint) {
            Stamp oldStamp = input.stamp(NodeView.DEFAULT);
            Stamp computedStamp = oldStamp.improveWith(piStamp);
            if (oldStamp.equals(computedStamp)) {
                /* The PiNode does not give any additional information. */
                return null;
            }

            ValueAnchorNode anchor = graph.add(new ValueAnchorNode(null));
            graph.addAfterFixed(anchorPoint, anchor);
            return graph.unique(new PiNode(input, piStamp, anchor));
        }

        private Stamp strengthenStampFromTypeFlow(ValueNode node, TypeFlow<?> nodeFlow, FixedWithNextNode anchorPoint, SimplifierTool tool) {
            PointsToAnalysis pta = getAnalysis();
            if (node.getStackKind() != JavaKind.Object) {
                return null;
            }
            if (node.usages().filter(n -> !(n instanceof FrameState)).isEmpty()) {
                /*
                 * No usages that can benefit from a stronger stamp, so no need to bloat the graph
                 * with a PiNode.
                 */
                return null;
            }
            if (methodFlow.isSaturated(pta, nodeFlow)) {
                /* The type flow is saturated, its type state does not matter. */
                return null;
            }

            TypeState nodeTypeState = methodFlow.foldTypeFlow(pta, nodeFlow);
            node.inferStamp();
            ObjectStamp oldStamp = (ObjectStamp) node.stamp(NodeView.DEFAULT);
            AnalysisType oldType = (AnalysisType) oldStamp.type();
            boolean nonNull = oldStamp.nonNull() || !nodeTypeState.canBeNull();

            /*
             * Find all types of the TypeState that are compatible with the current stamp. Since
             * stamps are propagated around immediately by the Canonicalizer, and the static
             * analysis does not track primitive types at all, it is possible and allowed that the
             * stamp is already more precise than the static analysis results.
             */
            List<AnalysisType> typeStateTypes = new ArrayList<>(nodeTypeState.typesCount());
            for (AnalysisType typeStateType : nodeTypeState.types(pta)) {
                if (oldType == null || (oldStamp.isExactType() ? oldType.equals(typeStateType) : oldType.isAssignableFrom(typeStateType))) {
                    typeStateTypes.add(typeStateType);
                }
            }

            if (typeStateTypes.size() == 0) {
                if (nonNull) {
                    makeUnreachable(anchorPoint.next(), tool, () -> "method " + graph.method().format("%H.%n(%p)") + ", node " + node + ": empty stamp when strengthening oldStamp " + oldStamp);
                    return null;
                } else {
                    return StampFactory.alwaysNull();
                }

            } else if (typeStateTypes.size() == 1) {
                AnalysisType exactType = typeStateTypes.get(0);
                assert getSingleImplementorType(exactType) == null || exactType.equals(getSingleImplementorType(exactType)) : "exactType=" + exactType + ", singleImplementor=" +
                                getSingleImplementorType(exactType);
                assert exactType.equals(getStrengthenStampType(exactType));

                if (!oldStamp.isExactType() || !exactType.equals(oldType)) {
                    ResolvedJavaType targetType = toTarget(exactType);
                    if (targetType != null) {
                        TypeReference typeRef = TypeReference.createExactTrusted(targetType);
                        return StampFactory.object(typeRef, nonNull);
                    }
                }

            } else if (!oldStamp.isExactType()) {
                assert typeStateTypes.size() > 1;
                AnalysisType baseType = typeStateTypes.get(0);
                for (int i = 1; i < typeStateTypes.size(); i++) {
                    baseType = baseType.findLeastCommonAncestor(typeStateTypes.get(i));
                }

                if (oldType != null && !oldType.isAssignableFrom(baseType)) {
                    /*
                     * When the original stamp is an interface type, we do not want to weaken that
                     * type with the common base class of all implementation types (which could even
                     * be java.lang.Object).
                     */
                    baseType = oldType;
                }

                /*
                 * With more than one type in the type state, there cannot be a single implementor.
                 * Because that single implementor would need to be the only type in the type state.
                 */
                assert getSingleImplementorType(baseType) == null || baseType.equals(getSingleImplementorType(baseType)) : "baseType=" + baseType + ", singleImplementor=" +
                                getSingleImplementorType(baseType);

                AnalysisType newType = getStrengthenStampType(baseType);

                assert typeStateTypes.stream().map(typeStateType -> newType.isAssignableFrom(typeStateType)).reduce(Boolean::logicalAnd).get();

                if (!newType.equals(oldType) && (oldType != null || !newType.isJavaLangObject())) {
                    ResolvedJavaType targetType = toTarget(newType);
                    if (targetType != null) {
                        TypeReference typeRef = TypeReference.createTrustedWithoutAssumptions(targetType);
                        return StampFactory.object(typeRef, nonNull);
                    }
                }
            }

            if (nonNull != oldStamp.nonNull()) {
                assert nonNull;
                return oldStamp.asNonNull();
            }
            /* Nothing to strengthen. */
            return null;
        }

        private void makeUnreachable(FixedNode node, CoreProviders providers, Supplier<String> message) {
            FixedNode unreachableNode = createUnreachable(graph, providers, message);
            ((FixedWithNextNode) node.predecessor()).setNext(unreachableNode);
            GraphUtil.killCFG(node);
        }

        protected ResolvedJavaType toTarget(AnalysisType type) {
            /*
             * This method will require more checks once we also parse graphs for JIT compilation:
             * When the SubstrateType was not created during static analysis for the provided
             * AnalysisType, then we must return null.
             */
            return type;
        }

        private Stamp strengthenStamp(Stamp s) {
            if (!(s instanceof AbstractObjectStamp)) {
                return null;
            }
            AbstractObjectStamp stamp = (AbstractObjectStamp) s;
            AnalysisType originalType = (AnalysisType) stamp.type();
            if (originalType == null) {
                return null;
            }

            if (!originalType.isReachable()) {
                /* We must be in dead code. */
                if (stamp.nonNull()) {
                    /* We must be in dead code. */
                    return StampFactory.empty(JavaKind.Object);
                } else {
                    return StampFactory.alwaysNull();
                }
            }

            AnalysisType singleImplementorType = getSingleImplementorType(originalType);
            if (singleImplementorType != null && (!stamp.isExactType() || !singleImplementorType.equals(originalType))) {
                ResolvedJavaType targetType = toTarget(singleImplementorType);
                if (targetType != null) {
                    TypeReference typeRef = TypeReference.createExactTrusted(targetType);
                    return StampFactory.object(typeRef, stamp.nonNull());
                }
            }

            AnalysisType strengthenType = getStrengthenStampType(originalType);
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
                    ResolvedJavaType targetType = toTarget(strengthenType);
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
}
