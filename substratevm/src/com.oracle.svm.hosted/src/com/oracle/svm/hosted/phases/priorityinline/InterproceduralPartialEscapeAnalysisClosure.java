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
package com.oracle.svm.hosted.phases.priorityinline;

import static com.oracle.svm.hosted.phases.priorityinline.InterproceduralPartialEscapeAnalysisPhase.lookupVirtual;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.oracle.svm.core.nodes.SubstrateMethodCallTargetNode;
import com.oracle.svm.hosted.phases.priorityinline.InterproceduralPartialEscapeAnalysisPhase.AnalysisResult;
import com.oracle.svm.hosted.phases.priorityinline.InterproceduralPartialEscapeAnalysisPhase.AnalysisResult.Materialization;
import com.oracle.svm.hosted.phases.priorityinline.InterproceduralPartialEscapeAnalysisPhase.AnalysisResult.VirtualCutoffEscapee;
import com.oracle.svm.hosted.phases.priorityinline.InterproceduralPartialEscapeAnalysisPhase.CallerContext;
import com.oracle.svm.hosted.phases.priorityinline.InterproceduralPartialEscapeAnalysisPhase.VirtualInfo;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.graph.spi.NodeWithIdentity;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FullInfopointNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.ScheduleResult;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.VirtualState;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.VirtualizableAllocation;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CallTreeNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CutoffNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.SubgraphNode;
import jdk.graal.compiler.virtual.phases.ea.GraphEffectList;
import jdk.graal.compiler.virtual.phases.ea.ObjectState;
import jdk.graal.compiler.virtual.phases.ea.PartialEscapeBlockState;
import jdk.graal.compiler.virtual.phases.ea.PartialEscapeClosure;

/**
 * This class implements the custom speculative partial escape analysis used within the
 * InterproceduralPartialEscapeAnalysisPhase (see {@link InterproceduralPartialEscapeAnalysisPhase})
 * for detailed about the phase's overall structure.
 */
public class InterproceduralPartialEscapeAnalysisClosure extends PartialEscapeClosure<PartialEscapeBlockState.Final> {

    /**
     * This node is used to replace values in a virtual object that are parameter and return values
     * which come from a different method (either param from caller or returned value from callee).
     */
    @NodeInfo(size = SIZE_IGNORED, cycles = CYCLES_IGNORED)
    public static class AnalysisBoundaryNode extends FloatingNode implements NodeWithIdentity {
        public static final NodeClass<AnalysisBoundaryNode> TYPE = NodeClass.create(AnalysisBoundaryNode.class);

        /* Tracking original node for debugging purposes. */
        protected final AnalysisBoundaryWrappedOriginalNode originalNode;

        protected AnalysisBoundaryNode(Stamp stamp, ValueNode originalNode) {
            super(TYPE, stamp);
            this.originalNode = new AnalysisBoundaryWrappedOriginalNode(originalNode);
        }

        static class AnalysisBoundaryWrappedOriginalNode {
            ValueNode node;

            AnalysisBoundaryWrappedOriginalNode(ValueNode node) {
                this.node = node;
            }
        }
    }

    private final CoreProviders providers;
    private final CallerContext callerContext;

    InterproceduralPartialEscapeAnalysisClosure(ScheduleResult schedule, CoreProviders providers, CallerContext callerContext) {
        super(schedule, providers);
        this.providers = providers;
        this.callerContext = callerContext;
    }

    private AnalysisResult analysisResult() {
        return callerContext.analysisResult();
    }

    /**
     * All nodes which either need to inject virtual state or initiate nested analysis must be
     * processed when we perform analysis with Virtual Injection.
     */
    @Override
    protected boolean requiresProcessing(Node node) {
        // We have to treat ParameterNodes like VirtualizableRoots
        return node instanceof SubstrateMethodCallTargetNode ||
                        (node instanceof FullInfopointNode) ||
                        (node instanceof Invoke) ||
                        (node instanceof ParameterNode) ||
                        (node instanceof ReturnNode) ||
                        super.requiresProcessing(node);
    }

    /**
     * This method is used to inject virtual state into the analysis pass and initiate nested
     * analysis in subsequent CallTargets. The registering of materializations is done by overriding
     * ensureMaterialized function.
     * <p>
     * This method is called before PartialEscapeClosure.processNodeInputs within
     * PartialEscapeClosure.processNodeInternal, so by returning false from the method or by
     * altering the virtual object state it is possible to prevent materializations from happening
     * which would normally occur during partial escape analysis.
     *
     * @return true when more processing of this node is needed, or false if processing on this node
     *         should stop.
     */
    @Override
    protected boolean virtualize(ValueNode node, VirtualizerTool vt) {
        if (node instanceof ParameterNode) {
            return virtualize((ParameterNode) node, vt);
        } else if (node instanceof FullInfopointNode) {
            return virtualize((FullInfopointNode) node, vt);
        } else if (node instanceof SubstrateMethodCallTargetNode) {
            return virtualize((SubstrateMethodCallTargetNode) node, vt);
        } else if (node instanceof Invoke) {
            return virtualize((Invoke) node, vt);
        } else if (node instanceof ReturnNode) {
            return virtualize((ReturnNode) node, vt);
        } else if (node instanceof VirtualizableAllocation) {
            return virtualize((VirtualizableAllocation) node, vt);
        } else {
            return super.virtualize(node, vt);
        }
    }

    private boolean virtualize(ReturnNode node, VirtualizerTool vt) {
        if (!callerContext.isRootContext()) {
            ValueNode returnResult = node.result();
            VirtualInfo returnResultState = VirtualInfo.capture(returnResult, vt);
            if (returnResultState != null) {
                callerContext.putVirtualReturnObject(returnResultState);
            }
        }
        return true; // request further node processing
    }

    /*
     * TODO(GR-39611): This virtualize here does not work exactly as intended, but it works well as
     * an approximation.
     */
    private boolean virtualize(VirtualizableAllocation node, VirtualizerTool vt) {
        ValueNode valueNode = (ValueNode) node;
        super.virtualize(valueNode, vt);
        ValueNode potentialVirtualObject = vt.getAlias(valueNode);
        if (potentialVirtualObject instanceof CommitAllocationNode) {
            CommitAllocationNode commitAllocationNode = (CommitAllocationNode) potentialVirtualObject;
            callerContext.putVirtualAllocation(callerContext.getCallTreeNode(), commitAllocationNode);
            for (VirtualObjectNode virtualObjectNode : commitAllocationNode.getVirtualObjects()) {
                callerContext.putVirtualObjectAllocationNode(virtualObjectNode, commitAllocationNode);
            }
            return true; // request further node processing
        } else if (!(potentialVirtualObject instanceof VirtualObjectNode)) {
            /*
             * This means the VirtualizerTool decided not to virtualize this
             * VirtualizableAllocation.
             */
            return true; // request further node processing
        } else {
            if (node instanceof AllocatedObjectNode) {
                AllocatedObjectNode allocatedObjectNode = (AllocatedObjectNode) node;
                callerContext.putVirtualObjectAllocationNode(allocatedObjectNode.getVirtualObject(), allocatedObjectNode.getCommit());
                callerContext.putVirtualAllocation(callerContext.getCallTreeNode(), allocatedObjectNode.getCommit());
            } else {
                VirtualObjectNode virtualObjectNode = (VirtualObjectNode) potentialVirtualObject;
                callerContext.putVirtualObjectAllocationNode(virtualObjectNode, node);
                callerContext.putVirtualAllocation(callerContext.getCallTreeNode(), node);
            }
            return true; // request further node processing
        }
    }

    private boolean virtualize(ParameterNode param, VirtualizerTool vt) {
        debug.log(3, ">> %s", param);

        if (callerContext.isRootContext()) {
            return false; // The root analysis needs no ParameterNode handling
        }

        int paramIndex = param.index();
        VirtualInfo argumentState = callerContext.getVirtualParam(paramIndex);
        if (argumentState == null) {
            return false; // No virtualized argument -> No virtualized parameter
        }

        ValueNode[] parameterStateEntries = Arrays.stream(argumentState.entries)
                        .map(argumentFieldNode -> {
                            ValueNode parameterFieldNode;
                            if (argumentFieldNode instanceof ConstantNode) {
                                parameterFieldNode = (ValueNode) argumentFieldNode.copyWithInputs(false);
                            } else {
                                parameterFieldNode = new AnalysisBoundaryNode(argumentFieldNode.stamp(NodeView.DEFAULT), argumentFieldNode);
                            }
                            return param.graph().addOrUniqueWithInputs(parameterFieldNode);
                        }).toArray(ValueNode[]::new);

        VirtualObjectNode parameterVirtualObject = argumentState.virtual.duplicate();
        vt.createVirtualObject(parameterVirtualObject, parameterStateEntries, Collections.emptyList(), null, false);
        param.graph().add(parameterVirtualObject);

        // Replace ParameterNode with new VirtualObjectNode and let PEA take care of the rest.
        vt.replaceWithVirtual(parameterVirtualObject);
        callerContext.putVirtualObjectAllocationNode(parameterVirtualObject, callerContext.getAllocationNode(argumentState.virtual));
        if (debug.isLogEnabled(3)) {
            debug.log(3, ">> %s: Parameter %d: Replaced with %s, %s", param, param.index(), parameterVirtualObject, Arrays.toString(parameterStateEntries));
        }
        return true; // request further node processing
    }

    private static boolean virtualize(FullInfopointNode node, VirtualizerTool vt) {
        node.getDebug().log(5, ">> %s: Remove ", node);
        vt.delete();
        return true; // request further node processing
    }

    private boolean virtualize(SubstrateMethodCallTargetNode callTarget, VirtualizerTool vt) {

        if (!callTarget.invoke().useForInlining()) {
            return true; // request further node processing
        }

        CallTreeNode callTargetCallTreeNode = analysisResult().callTreeNodeForInvokeInCopiedGraph(callTarget.invoke(), callerContext.getCallTreeNode());

        if (callTargetCallTreeNode instanceof CutoffNode) {
            List<VirtualCutoffEscapee> cutoffEscapees = new ArrayList<>();
            for (ValueNode arg : callTarget.arguments()) {
                if (lookupVirtual(arg, vt) != null) {
                    cutoffEscapees.add(new VirtualCutoffEscapee(VirtualCutoffEscapee.EscapeType.ARGUMENT));
                }
            }
            if (hasVirtualReturnValueCandidate(callTarget) && hasNonStateUsage(callTarget.invoke().asNode())) {
                cutoffEscapees.add(new VirtualCutoffEscapee(VirtualCutoffEscapee.EscapeType.RETURN));
            }

            if (cutoffEscapees.size() == 0) {
                return true; // request further node processing
            }

            analysisResult().cutoffEscapees().put(callTargetCallTreeNode, cutoffEscapees);
            return true; // request further node processing
        }

        if (!(callTargetCallTreeNode instanceof SubgraphNode)) {
            return true; // request further node processing
        }

        SubgraphNode callTargetSubgraphNode = (SubgraphNode) callTargetCallTreeNode;
        StructuredGraph targetMethodGraph = callTargetSubgraphNode.getReadonlySubgraph();

        /* Store virtual state of argument at call-site in callerContext */
        NodeInputList<ValueNode> arguments = callTarget.arguments();
        ArrayList<VirtualInfo> virtualInfos = new ArrayList<>(arguments.size());
        for (int i = 0; i < arguments.size(); ++i) {
            VirtualInfo vi = VirtualInfo.capture(arguments.get(i), vt);
            virtualInfos.add(i, vi);
        }

        CallerContext nestedCallerContext = new CallerContext(callerContext, vt, callTarget, callTargetCallTreeNode, virtualInfos);

        // Perform recursive application of analysis
        if (debug.isLogEnabled(3)) {
            debug.log(3, ">>> %s: Recursive analysis (%d, %s): Begin {{{", callTarget, nestedCallerContext.depth, callTargetCallTreeNode);
        }

        if (callTargetSubgraphNode.getRecursionDepth() > 1) {
            return true;
        }

        InterproceduralPartialEscapeAnalysisPhase calleePEA = new InterproceduralPartialEscapeAnalysisPhase(nestedCallerContext.canonicalizer, targetMethodGraph.getOptions());
        calleePEA.runNested(targetMethodGraph, providers, nestedCallerContext);
        if (debug.isLogEnabled(3)) {
            debug.log(3, ">>> %s: Recursive analysis (%d, %s): }}} End", callTarget, nestedCallerContext.depth, callTargetCallTreeNode);
        }
        return true; // request further node processing (cause materialization)
    }

    private boolean virtualize(Invoke invoke, VirtualizerTool vt) {
        VirtualInfo virtualReturnResultState = callerContext.getVirtualReturnObject(invoke);
        /* If we have virtualReturnResultState we need to create a caller side representation. */
        if (virtualReturnResultState != null) {
            ValueNode[] returnResultStateEntries = Arrays.stream(virtualReturnResultState.entries)
                            .map(fieldNode -> {
                                ValueNode returnResultFieldNode;
                                if (fieldNode instanceof ConstantNode) {
                                    returnResultFieldNode = (ValueNode) fieldNode.copyWithInputs(false);
                                } else {
                                    returnResultFieldNode = new AnalysisBoundaryNode(fieldNode.stamp(NodeView.DEFAULT).unrestricted(), fieldNode);
                                }
                                return invoke.asNode().graph().addOrUniqueWithInputs(returnResultFieldNode);
                            }).toArray(ValueNode[]::new);

            VirtualObjectNode returnResultVirtualObject = virtualReturnResultState.virtual.duplicate();
            vt.createVirtualObject(returnResultVirtualObject, returnResultStateEntries, Collections.emptyList(), null, false);
            ((InvokeWithExceptionNode) invoke).graph().add(returnResultVirtualObject);
            callerContext.putVirtualObjectAllocationNode(returnResultVirtualObject, callerContext.getAllocationNode(virtualReturnResultState.virtual));
            /* Replace invoke with caller side representation of the virtual return result */
            vt.replaceWithVirtual(returnResultVirtualObject);
        }
        return true;
    }

    /**
     * This method is called in
     * {@link PartialEscapeClosure#processNodeInputs(ValueNode, FixedNode, PartialEscapeBlockState, GraphEffectList)}.
     * We specialize the method, so that we can keep objects virtual that escape into methods that
     * correspond to a SubgraphNode in the callTree. Furthermore, we keep objects virtual that
     * materialize due to a ReturnNode, so we can inject the virtual state in the parent. We still
     * store a materialization in the AnalysisResult, to compute effects of inlining on only a
     * subset of all invokes for which the objects escape into.
     *
     * @return true if this should trigger a materialization.
     */
    @Override
    protected boolean shouldMaterializeNonVirtualizable(PartialEscapeBlockState.Final state, int objectId, FixedNode materializeBefore) {
        VirtualObjectNode virtualObjectNode = virtualObjects.get(objectId);
        ObjectState objectState = state.getObjectState(objectId);
        if (materializeBefore instanceof Invoke) {
            /* Only Trigger materializations for Invoke which are not subgraphNodes. */
            Invoke invoke = (Invoke) materializeBefore;
            CallTreeNode callTreeNode = analysisResult().callTreeNodeForInvokeInCopiedGraph(invoke, callerContext.getCallTreeNode());
            if (callTreeNode instanceof SubgraphNode && objectState.isVirtual()) {
                VirtualizableAllocation allocation = analysisResult().virtualObjectAllocationMap().get(virtualObjectNode);
                callerContext.addMaterialization(materializeBefore, allocation);
                return false;
            }
            return true;
        } else if (materializeBefore instanceof ReturnNode) {
            /* Do not trigger materialization for ReturnNode */
            return false;
        } else {
            return true;
        }
    }

    /**
     * This method is the entry point into materializeBefore, which would insert a CommitAllocation
     * into the graph in PartialEscapeClosure. The frequency of the FixedNode materializeBefore
     * exactly matches the frequency of an allocation. We use this to create a
     * {@link Materialization} object and store it into the AnalysisResults.
     *
     * @return true if and only materializeBefore is responsible for the object with objectId to
     *         materialize. This does not return true if the object is no longer virtual at
     *         materializeBefore.
     */
    @Override
    protected boolean ensureMaterialized(PartialEscapeBlockState<?> state, int objectId, FixedNode materializeBefore, GraphEffectList effects, CounterKey counter) {
        if (materializeBefore == null) {
            return super.ensureMaterialized(state, objectId, null, effects, counter);
        }
        VirtualObjectNode virtualObjectNode = virtualObjects.get(objectId);
        if (!super.ensureMaterialized(state, objectId, materializeBefore, effects, counter)) {
            return false;
        }
        VirtualizableAllocation allocation = analysisResult().virtualObjectAllocationMap().get(virtualObjectNode);
        callerContext.addMaterialization(materializeBefore, allocation);
        return true;
    }

    private static boolean hasNonStateUsage(Node node) {
        return node.usages().filter(n -> !(n instanceof VirtualState)).isNotEmpty();
    }

    private static boolean hasVirtualReturnValueCandidate(SubstrateMethodCallTargetNode callTarget) {
        return callTarget.returnKind().isObject();
    }

    @Override
    protected PartialEscapeBlockState.Final getInitialState() {
        StructuredGraph graph = cfg.graph;
        return new PartialEscapeBlockState.Final(graph.getOptions(), graph.getDebug());
    }

    @Override
    protected PartialEscapeBlockState.Final cloneState(PartialEscapeBlockState.Final oldState) {
        return new PartialEscapeBlockState.Final(oldState);
    }

    /*
     * Stop after 1st iteration and do not apply any side effects (we are just interested in the
     * partial escape analysis)
     */
    @Override
    public boolean hasChanged() {
        return false;
    }

    /*
     * Never apply any effects. We are just interested in the analysis results.
     */
    @Override
    public void applyEffects() {
    }
}
