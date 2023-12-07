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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.graalvm.collections.EconomicSet;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.infrastructure.Universe;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.typestate.PrimitiveConstantTypeState;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.svm.util.ImageBuildStatistics;

import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
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
import jdk.graal.compiler.nodes.GraphEncoder;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.nodes.extended.ValueAnchorNode;
import jdk.graal.compiler.nodes.java.ClassIsAssignableFromNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.LimitedValueProxy;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase.CustomSimplification;
import jdk.graal.compiler.phases.common.inlining.InliningUtil;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.TriState;

/**
 * This class applies static analysis results directly to the {@link StructuredGraph Graal IR} used
 * to build the type flow graph.
 * 
 * It uses a {@link CustomSimplification} for the {@link CanonicalizerPhase}, because that provides
 * all the framework for iterative stamp propagation and adding/removing control flow nodes while
 * processing the graph.
 * 
 * From the single-method view that the compiler has when later compiling the graph, static analysis
 * results appear "out of thin air": At some random point in the graph, we suddenly have a more
 * precise type (= stamp) for a value. Since many nodes are floating, and even currently fixed nodes
 * might float later, we need to be careful that all information coming from the type flow graph
 * remains properly anchored to the point where the static analysis actually proved the information.
 * So we cannot just change the stamp of, e.g., the parameter of a method invocation, to a more
 * precise stamp. We need to do that indirectly by adding a {@link PiNode} that is anchored using a
 * {@link ValueAnchorNode}.
 */
public abstract class StrengthenGraphs {

    public static class Options {
        @Option(help = "Perform constant folding in StrengthenGraphs")//
        public static final OptionKey<Boolean> StrengthenGraphWithConstants = new OptionKey<>(true);
    }

    protected final BigBang bb;
    /**
     * The universe used to convert analysis metadata to hosted metadata, or {@code null} if no
     * conversion should be performed.
     */
    protected final Universe converter;

    private final Map<JavaTypeProfile, JavaTypeProfile> cachedTypeProfiles = new ConcurrentHashMap<>();
    private final Map<JavaMethodProfile, JavaMethodProfile> cachedMethodProfiles = new ConcurrentHashMap<>();

    /* Cached option values to avoid repeated option lookup. */
    private final int analysisSizeCutoff;
    private final boolean strengthenGraphWithConstants;

    private final StrengthenGraphsCounters beforeCounters;
    private final StrengthenGraphsCounters afterCounters;

    public StrengthenGraphs(BigBang bb, Universe converter) {
        this.bb = bb;
        this.converter = converter;

        analysisSizeCutoff = PointstoOptions.AnalysisSizeCutoff.getValue(bb.getOptions());
        strengthenGraphWithConstants = Options.StrengthenGraphWithConstants.getValue(bb.getOptions());

        if (ImageBuildStatistics.Options.CollectImageBuildStatistics.getValue(bb.getOptions())) {
            beforeCounters = new StrengthenGraphsCounters(ImageBuildStatistics.CheckCountLocation.BEFORE_STRENGTHEN_GRAPHS);
            afterCounters = new StrengthenGraphsCounters(ImageBuildStatistics.CheckCountLocation.AFTER_STRENGTHEN_GRAPHS);
        } else {
            beforeCounters = null;
            afterCounters = null;
        }
    }

    @SuppressWarnings("try")
    public final void applyResults(AnalysisMethod method) {
        var nodeReferences = method instanceof PointsToAnalysisMethod ptaMethod && ptaMethod.getTypeFlow().flowsGraphCreated()
                        ? ptaMethod.getTypeFlow().getMethodFlowsGraph().getNodeFlows().getKeys()
                        : null;
        var debug = new DebugContext.Builder(bb.getOptions(), new GraalDebugHandlersFactory(bb.getSnippetReflectionProvider())).build();
        var graph = method.decodeAnalyzedGraph(debug, nodeReferences);
        if (graph == null) {
            return;
        }

        graph.resetDebug(debug);
        if (beforeCounters != null) {
            beforeCounters.collect(graph);
        }
        try (var s = debug.scope("StrengthenGraphs", graph); var a = debug.activate()) {
            new AnalysisStrengthenGraphsPhase(method, graph).apply(graph, bb.getProviders(method));
        } catch (Throwable ex) {
            debug.handle(ex);
        }
        if (afterCounters != null) {
            afterCounters.collect(graph);
        }
        method.setAnalyzedGraph(GraphEncoder.encodeSingleGraph(graph, AnalysisParsedGraph.HOST_ARCHITECTURE));

        if (nodeReferences != null) {
            /* Ensure the temporarily decoded graph is not kept alive via the node references. */
            for (var nodeReference : nodeReferences) {
                nodeReference.clear();
            }
        }
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

    protected abstract FixedNode createInvokeWithNullReceiverReplacement(StructuredGraph graph);

    protected abstract void setInvokeProfiles(Invoke invoke, JavaTypeProfile typeProfile, JavaMethodProfile methodProfile);

    protected abstract String getTypeName(AnalysisType type);

    protected abstract boolean simplifyDelegate(Node n, SimplifierTool tool);

    /* Wrapper to clearly identify phase in IGV graph dumps. */
    class AnalysisStrengthenGraphsPhase extends BasePhase<CoreProviders> {
        final CanonicalizerPhase phase;

        AnalysisStrengthenGraphsPhase(AnalysisMethod method, StructuredGraph graph) {
            phase = CanonicalizerPhase.create().copyWithCustomSimplification(new StrengthenSimplifier(method, graph));
        }

        @Override
        public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
            return phase.notApplicableTo(graphState);
        }

        @Override
        protected void run(StructuredGraph graph, CoreProviders context) {
            phase.apply(graph, context);
        }

        @Override
        public CharSequence getName() {
            return "AnalysisStrengthenGraphs";
        }
    }

    class StrengthenSimplifier implements CustomSimplification {

        private final StructuredGraph graph;
        private final MethodTypeFlow methodFlow;

        private final NodeBitMap createdPiNodes;

        private final TypeFlow<?>[] parameterFlows;
        private final NodeMap<TypeFlow<?>> nodeFlows;

        private final boolean allowConstantFolding;
        private final boolean allowOptimizeReturnParameter;
        private final EconomicSet<ValueNode> unreachableValues = EconomicSet.create();

        /**
         * For runtime compiled methods, we must be careful to ensure new SubstrateTypes are not
         * created due to the optimizations performed during the
         * {@link AnalysisStrengthenGraphsPhase}.
         */
        private final Function<AnalysisType, ResolvedJavaType> toTargetFunction;

        StrengthenSimplifier(AnalysisMethod method, StructuredGraph graph) {
            this.graph = graph;
            this.createdPiNodes = new NodeBitMap(graph);

            if (method instanceof PointsToAnalysisMethod ptaMethod && ptaMethod.getTypeFlow().flowsGraphCreated()) {
                methodFlow = ptaMethod.getTypeFlow();
                MethodFlowsGraph originalFlows = methodFlow.getMethodFlowsGraph();
                parameterFlows = originalFlows.getParameters();
                nodeFlows = new NodeMap<>(graph);
                var cursor = originalFlows.getNodeFlows().getEntries();
                while (cursor.advance()) {
                    Node node = cursor.getKey().getNode();
                    assert nodeFlows.get(node) == null : "overwriting existing entry for " + node;
                    nodeFlows.put(node, cursor.getValue());
                }

                allowConstantFolding = strengthenGraphWithConstants && bb.getHostVM().allowConstantFolding(method);

                /*
                 * In deoptimization target methods optimizing the return parameter can make new
                 * values live across deoptimization entrypoints.
                 *
                 * In runtime-compiled methods invokes may be intrinsified during runtime partial
                 * evaluation and change the behavior of the invoke. This would be a problem if the
                 * behavior of the method completely changed; however, currently this
                 * intrinsification is used to improve the stamp of the returned value, but not to
                 * alter the semantics. Hence, it is preferred to continue to use the return value
                 * of the invoke (as opposed to the parameter value).
                 */
                allowOptimizeReturnParameter = method.isOriginalMethod() && ((PointsToAnalysis) bb).optimizeReturnedParameter();

            } else {
                methodFlow = null;
                parameterFlows = null;
                nodeFlows = null;
                allowConstantFolding = false;
                allowOptimizeReturnParameter = false;
            }

            this.toTargetFunction = bb.getHostVM().getStrengthenGraphsToTargetFunction(method.getMultiMethodKey());
        }

        private TypeFlow<?> getNodeFlow(Node node) {
            return nodeFlows == null || nodeFlows.isNew(node) ? null : nodeFlows.get(node);
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

            if (simplifyDelegate(n, tool)) {
                // handled elsewhere
            } else if (n instanceof ParameterNode && parameterFlows != null) {
                ParameterNode node = (ParameterNode) n;
                StartNode anchorPoint = graph.start();
                Object newStampOrConstant = strengthenStampFromTypeFlow(node, parameterFlows[node.index()], anchorPoint, tool);
                updateStampUsingPiNode(node, newStampOrConstant, anchorPoint, tool);

            } else if (n instanceof LoadFieldNode || n instanceof LoadIndexedNode) {
                FixedWithNextNode node = (FixedWithNextNode) n;
                Object newStampOrConstant = strengthenStampFromTypeFlow(node, getNodeFlow(node), node, tool);
                /*
                 * Even though the memory load will be a floating node later, we can update the
                 * stamp directly because the type information maintained by the static analysis
                 * about memory is not flow-sensitive and not context-sensitive. If we ever revive a
                 * context-sensitive analysis, we will need to change this. But for now, we are
                 * fine.
                 */
                if (newStampOrConstant instanceof JavaConstant) {
                    ConstantNode replacement = ConstantNode.forConstant((JavaConstant) newStampOrConstant, bb.getMetaAccess(), graph);
                    graph.replaceFixedWithFloating(node, replacement);
                    tool.addToWorkList(replacement);
                } else {
                    updateStampInPlace(node, (Stamp) newStampOrConstant, tool);
                }

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
                        ValueAnchorNode anchor = graph.add(new ValueAnchorNode());
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
            FixedNode node = invoke.asFixedNode();
            MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();

            if (callTarget.invokeKind().isDirect() && !((AnalysisMethod) callTarget.targetMethod()).isSimplyImplementationInvoked()) {
                /*
                 * This is a direct call to a method that the static analysis did not see as
                 * invoked. This can happen when the receiver is always null. In most cases, the
                 * method profile also has a length of 0 and the below code to kill the invoke would
                 * trigger. But when only running the reachability analysis, there is no detailed
                 * list of callees.
                 */
                unreachableInvoke(invoke, tool);
                /* Invoke is unreachable, there is no point in improving any types further. */
                return;
            }

            InvokeTypeFlow invokeFlow = (InvokeTypeFlow) getNodeFlow(node);
            if (invokeFlow == null) {
                /* No points-to analysis results. */
                return;
            }

            Collection<AnalysisMethod> callees = invokeFlow.getOriginalCallees();
            if (callees.isEmpty()) {
                unreachableInvoke(invoke, tool);
                /* Invoke is unreachable, there is no point in improving any types further. */
                return;
            }

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
                if (invokeFlow.getTargetMethod().hasReceiver() && !methodFlow.isSaturated((PointsToAnalysis) bb, invokeFlow.getReceiver())) {
                    receiverTypeState = methodFlow.foldTypeFlow((PointsToAnalysis) bb, invokeFlow.getReceiver());
                }

                JavaTypeProfile typeProfile = makeTypeProfile(receiverTypeState);
                JavaMethodProfile methodProfile = makeMethodProfile(callees);
                assert typeProfile == null || typeProfile.getTypes().length > 1 : "Should devirtualize with typeProfile=" + typeProfile + " and methodProfile=" + methodProfile;
                assert methodProfile == null || methodProfile.getMethods().length > 1 : "Should devirtualize with typeProfile=" + typeProfile + " and methodProfile=" + methodProfile;

                setInvokeProfiles(invoke, typeProfile, methodProfile);
            }

            if (allowOptimizeReturnParameter) {
                optimizeReturnedParameter(callees, arguments, node, tool);
            }

            FixedWithNextNode anchorPointAfterInvoke = (FixedWithNextNode) (invoke instanceof InvokeWithExceptionNode ? invoke.next() : invoke);
            Object newStampOrConstant = strengthenStampFromTypeFlow(node, invokeFlow.getResult(), anchorPointAfterInvoke, tool);
            updateStampUsingPiNode(node, newStampOrConstant, anchorPointAfterInvoke, tool);
        }

        /**
         * If all possible callees return the same parameter, then we can replace the invoke with
         * that parameter at all usages. This is the same that would happen when the callees are
         * inlined. So we get a bit of the benefits of method inlining without actually performing
         * the inlining.
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
            assert returnedParameterIndex != -1 : callees;

            ValueNode returnedActualParameter = arguments.get(returnedParameterIndex);
            tool.addToWorkList(invoke.usages());
            invoke.replaceAtUsages(returnedActualParameter);
        }

        /**
         * The invoke always has a null receiver, so it can be removed.
         */
        protected void invokeWithNullReceiver(Invoke invoke) {
            FixedNode replacement = createInvokeWithNullReceiverReplacement(graph);
            ((FixedWithNextNode) invoke.predecessor()).setNext(replacement);
            GraphUtil.killCFG(invoke.asFixedNode());
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

            makeUnreachable(invoke.asFixedNode(), tool, () -> "method " + ((AnalysisMethod) graph.method()).getQualifiedName() + ", node " + invoke +
                            ": empty list of callees for call to " + ((AnalysisMethod) invoke.callTarget().targetMethod()).getQualifiedName());
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

        private boolean isUnreachable(Node branch) {
            TypeFlow<?> branchFlow = getNodeFlow(branch);
            return branchFlow != null &&
                            !methodFlow.isSaturated((PointsToAnalysis) bb, branchFlow) &&
                            methodFlow.foldTypeFlow((PointsToAnalysis) bb, branchFlow).isEmpty();
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

        private void updateStampUsingPiNode(ValueNode node, Object newStampOrConstant, FixedWithNextNode anchorPoint, SimplifierTool tool) {
            if (newStampOrConstant != null && node.hasUsages() && !createdPiNodes.isMarked(node)) {
                ValueNode pi = insertPi(node, newStampOrConstant, anchorPoint);
                if (pi != null) {
                    /*
                     * The Canonicalizer that drives all of our node processing is iterative. We
                     * only want to insert the PiNode the first time we handle a node.
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

        /*
         * See comment on {@link StrengthenGraphs} on why anchoring is necessary.
         */
        private ValueNode insertPi(ValueNode input, Object newStampOrConstant, FixedWithNextNode anchorPoint) {
            if (newStampOrConstant instanceof JavaConstant) {
                JavaConstant constant = (JavaConstant) newStampOrConstant;
                if (input.isConstant()) {
                    assert bb.getConstantReflectionProvider().constantEquals(input.asConstant(), constant) : input.asConstant() + ", " + constant;
                    return null;
                }
                return ConstantNode.forConstant(constant, bb.getMetaAccess(), graph);
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
            if (nodeFlow == null || !((PointsToAnalysis) bb).isSupportedJavaKind(node.getStackKind())) {
                return null;
            }
            if (methodFlow.isSaturated((PointsToAnalysis) bb, nodeFlow)) {
                /* The type flow is saturated, its type state does not matter. */
                return null;
            }
            if (unreachableValues.contains(node)) {
                // This node has already been made unreachable - no further action is needed
                return null;
            }
            /*
             * If there are no usages of the node, then adding a PiNode would only bloat the graph.
             * However, we don't immediately return null since the stamp can still indicate this
             * node is unreachable.
             */
            boolean hasUsages = node.usages().filter(n -> !(n instanceof FrameState)).isNotEmpty();

            TypeState nodeTypeState = methodFlow.foldTypeFlow((PointsToAnalysis) bb, nodeFlow);

            if (hasUsages && allowConstantFolding && !nodeTypeState.canBeNull()) {
                JavaConstant constantValue = nodeTypeState.asConstant();
                if (constantValue != null) {
                    return constantValue;
                }
            }

            node.inferStamp();
            Stamp s = node.stamp(NodeView.DEFAULT);
            if (s.isIntegerStamp() || nodeTypeState.isPrimitive()) {
                return getIntegerStamp(node, s, nodeTypeState);
            }

            ObjectStamp oldStamp = (ObjectStamp) s;
            AnalysisType oldType = (AnalysisType) oldStamp.type();
            boolean nonNull = oldStamp.nonNull() || !nodeTypeState.canBeNull();

            /*
             * Find all types of the TypeState that are compatible with the current stamp. Since
             * stamps are propagated around immediately by the Canonicalizer, and the static
             * analysis does not track primitive types at all, it is possible and allowed that the
             * stamp is already more precise than the static analysis results.
             */
            List<AnalysisType> typeStateTypes = new ArrayList<>(nodeTypeState.typesCount());
            for (AnalysisType typeStateType : nodeTypeState.types(bb)) {
                if (oldType == null || (oldStamp.isExactType() ? oldType.equals(typeStateType) : oldType.isJavaLangObject() || oldType.isAssignableFrom(typeStateType))) {
                    typeStateTypes.add(typeStateType);
                }
            }

            if (typeStateTypes.size() == 0) {
                if (nonNull) {
                    makeUnreachable(anchorPoint.next(), tool,
                                    () -> "method " + ((AnalysisMethod) graph.method()).getQualifiedName() + ", node " + node + ": empty stamp when strengthening oldStamp " + oldStamp);
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
                assert getSingleImplementorType(exactType) == null || exactType.equals(getSingleImplementorType(exactType)) : "exactType=" + exactType + ", singleImplementor=" +
                                getSingleImplementorType(exactType);
                assert exactType.equals(getStrengthenStampType(exactType)) : exactType;

                if (!oldStamp.isExactType() || !exactType.equals(oldType)) {
                    ResolvedJavaType targetType = toTargetFunction.apply(exactType);
                    if (targetType != null) {
                        TypeReference typeRef = TypeReference.createExactTrusted(targetType);
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

                assert typeStateTypes.stream().map(typeStateType -> newType.isAssignableFrom(typeStateType)).reduce(Boolean::logicalAnd).get() : typeStateTypes;

                if (!newType.equals(oldType) && (oldType != null || !newType.isJavaLangObject())) {
                    ResolvedJavaType targetType = toTargetFunction.apply(newType);
                    if (targetType != null) {
                        TypeReference typeRef = TypeReference.createTrustedWithoutAssumptions(targetType);
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

        private IntegerStamp getIntegerStamp(ValueNode node, Stamp stamp, TypeState nodeTypeState) {
            assert bb.trackPrimitiveValues() : nodeTypeState + "," + node + " in " + node.graph();
            assert nodeTypeState != null && (nodeTypeState.isEmpty() || nodeTypeState.isPrimitive()) : nodeTypeState + "," + node + " in " + node.graph();
            if (nodeTypeState instanceof PrimitiveConstantTypeState constantTypeState) {
                long constantValue = constantTypeState.getValue();
                if (node instanceof ConstantNode constant) {
                    /*
                     * Sanity check, verify that what was proven by the analysis is consistent with
                     * the constant node in the graph.
                     */
                    Constant value = constant.getValue();
                    assert value instanceof PrimitiveConstant : "Node " + value + " should be a primitive constant when extracting an integer stamp, method " + node.graph().method();
                    assert ((PrimitiveConstant) value).asLong() == constantValue : "The actual value of node: " + value + " is different than the value " + constantValue +
                                    " computed by points-to analysis, method in " + node.graph().method();
                } else {
                    return IntegerStamp.createConstant(((IntegerStamp) stamp).getBits(), constantValue);
                }
            }
            return null;
        }

        private void makeUnreachable(FixedNode node, CoreProviders providers, Supplier<String> message) {
            FixedNode unreachableNode = createUnreachable(graph, providers, message);
            ((FixedWithNextNode) node.predecessor()).setNext(unreachableNode);
            GraphUtil.killCFG(node);
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
                ResolvedJavaType targetType = toTargetFunction.apply(singleImplementorType);
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

    protected JavaTypeProfile makeTypeProfile(TypeState typeState) {
        if (typeState == null || analysisSizeCutoff != -1 && typeState.typesCount() > analysisSizeCutoff) {
            return null;
        }
        var created = createTypeProfile(typeState);
        var existing = cachedTypeProfiles.putIfAbsent(created, created);
        return existing != null ? existing : created;
    }

    private JavaTypeProfile createTypeProfile(TypeState typeState) {
        double probability = 1d / typeState.typesCount();

        Stream<? extends ResolvedJavaType> stream = typeState.typesStream(bb);
        if (converter != null) {
            stream = stream.map(converter::lookup).sorted(converter.hostVM().getTypeComparator());
        }
        JavaTypeProfile.ProfiledType[] pitems = stream
                        .map(type -> new JavaTypeProfile.ProfiledType(type, probability))
                        .toArray(JavaTypeProfile.ProfiledType[]::new);

        return new JavaTypeProfile(TriState.get(typeState.canBeNull()), 0, pitems);
    }

    protected JavaMethodProfile makeMethodProfile(Collection<AnalysisMethod> callees) {
        if (analysisSizeCutoff != -1 && callees.size() > analysisSizeCutoff) {
            return null;
        }
        var created = createMethodProfile(callees);
        var existing = cachedMethodProfiles.putIfAbsent(created, created);
        return existing != null ? existing : created;
    }

    private JavaMethodProfile createMethodProfile(Collection<AnalysisMethod> callees) {
        JavaMethodProfile.ProfiledMethod[] pitems = new JavaMethodProfile.ProfiledMethod[callees.size()];
        double probability = 1d / pitems.length;

        int idx = 0;
        for (AnalysisMethod aMethod : callees) {
            ResolvedJavaMethod convertedMethod = converter == null ? aMethod : converter.lookup(aMethod);
            pitems[idx++] = new JavaMethodProfile.ProfiledMethod(convertedMethod, probability);
        }
        return new JavaMethodProfile(0, pitems);
    }
}

/**
 * Infrastructure for collecting detailed counters that capture the benefits of strengthening
 * graphs. The counter dumping is handled by {@link ImageBuildStatistics}.
 */
final class StrengthenGraphsCounters {

    enum Counter {
        METHOD,
        BLOCK,
        IS_NULL,
        INSTANCE_OF,
        INVOKE_STATIC,
        INVOKE_DIRECT,
        INVOKE_INDIRECT,
        LOAD_FIELD,
        CONSTANT,
    }

    private final AtomicLong[] values;

    StrengthenGraphsCounters(ImageBuildStatistics.CheckCountLocation location) {
        values = new AtomicLong[Counter.values().length];

        ImageBuildStatistics imageBuildStats = ImageBuildStatistics.counters();
        for (Counter counter : Counter.values()) {
            values[counter.ordinal()] = imageBuildStats.insert(location + "_" + counter.name());
        }
    }

    void collect(StructuredGraph graph) {
        int[] localValues = new int[Counter.values().length];

        inc(localValues, Counter.METHOD);
        for (Node n : graph.getNodes()) {
            if (n instanceof AbstractBeginNode) {
                inc(localValues, Counter.BLOCK);
            } else if (n instanceof ConstantNode) {
                inc(localValues, Counter.CONSTANT);
            } else if (n instanceof LoadFieldNode) {
                inc(localValues, Counter.LOAD_FIELD);
            } else if (n instanceof IfNode node) {
                collect(localValues, node.condition());
            } else if (n instanceof ConditionalNode node) {
                collect(localValues, node.condition());
            } else if (n instanceof MethodCallTargetNode node) {
                collect(localValues, node.invokeKind());
            }
        }

        for (int i = 0; i < localValues.length; i++) {
            values[i].addAndGet(localValues[i]);
        }
    }

    private static void collect(int[] localValues, LogicNode condition) {
        if (condition instanceof IsNullNode) {
            inc(localValues, Counter.IS_NULL);
        } else if (condition instanceof InstanceOfNode) {
            inc(localValues, Counter.INSTANCE_OF);
        }
    }

    private static void collect(int[] localValues, CallTargetNode.InvokeKind invokeKind) {
        switch (invokeKind) {
            case Static:
                inc(localValues, Counter.INVOKE_STATIC);
                break;
            case Virtual:
            case Interface:
                inc(localValues, Counter.INVOKE_INDIRECT);
                break;
            case Special:
                inc(localValues, Counter.INVOKE_DIRECT);
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(invokeKind);
        }
    }

    private static void inc(int[] localValues, Counter counter) {
        localValues[counter.ordinal()]++;
    }
}
