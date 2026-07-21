/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common.priorityinline.nodes;

import java.util.EnumSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.UnmodifiableEconomicMap;

import jdk.graal.compiler.duplication.util.DuplicationUtil;

import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import jdk.graal.compiler.phases.common.inlining.InliningUtil;
import jdk.graal.compiler.phases.common.inlining.walker.InliningIterator;
import jdk.graal.compiler.phases.common.priorityinline.GraphCache;
import jdk.graal.compiler.phases.common.priorityinline.InliningMath;
import jdk.graal.compiler.phases.common.priorityinline.data.BenefitCostTuple;
import jdk.graal.compiler.phases.common.priorityinline.data.BenefitKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo(nameTemplate = "Sg({p#nodeCount}) #{p#targetMethod/s} [P = {p#priority}]")
public class SubgraphNode extends ParentNode {
    public static final NodeClass<SubgraphNode> TYPE = NodeClass.create(SubgraphNode.class);

    private final GraphCache.Ref<ResolvedJavaMethod, StructuredGraph> subgraph;
    private final boolean intrinsic;
    private final boolean monomorphic;
    private final ResolvedJavaMethod dispatchedMethod;
    private final ResolvedJavaType dispatchedType;
    private final ResolvedJavaType originalDispatchedType;
    private final EnumSet<BenefitKind> benefits;

    public SubgraphNode(NodeSourcePosition compilationRootPosition, Invoke invoke, double frequency, GraphCache.Ref<ResolvedJavaMethod, StructuredGraph> subgraph,
                    boolean monomorphic, ResolvedJavaMethod dispatchedMethod, ResolvedJavaType dispatchedType,
                    ResolvedJavaType originalDispatchedType, EnumSet<BenefitKind> benefits, boolean intrinsic) {
        super(compilationRootPosition, TYPE, invoke, frequency);
        this.subgraph = subgraph;
        this.monomorphic = monomorphic;
        this.dispatchedMethod = dispatchedMethod;
        this.dispatchedType = dispatchedType;
        this.originalDispatchedType = originalDispatchedType;
        this.benefits = benefits;
        this.intrinsic = intrinsic;
    }

    @Override
    public Map<Object, Object> getDebugProperties(Map<Object, Object> map) {
        Map<Object, Object> debugProperties = super.getDebugProperties(map);
        debugProperties.put("targetMethod", getReadonlySubgraph().method());
        debugProperties.put("nodeCount", getReadonlySubgraph().getNodeCount());
        return debugProperties;
    }

    @Override
    public ResolvedJavaMethod targetMethod() {
        return dispatchedMethod;
    }

    @SuppressWarnings("unused")
    @Override
    public void createImmediateChildren(CallTreeNode caller) {
        refreshImmediateChildren();
    }

    private void refreshImmediateChildren() {
        // Track previously added child nodes and their invokes to prevent adding them twice.
        EconomicSet<Invoke> invokesWithExistingCallTreeNodes = EconomicSet.create();
        for (CallTreeNode child : children()) {
            invokesWithExistingCallTreeNodes.add(child.invoke());
        }

        // Traverse inlineable invokes and add child nodes to the call graph for each unseen invoke.
        StructuredGraph compilerGraph = subgraph.readonly();
        LinkedList<Invoke> invokes = new InliningIterator(compilerGraph).apply();

        if (invokes.size() > 0) {
            ControlFlowGraph cfg = ControlFlowGraph.newBuilder(compilerGraph).connectBlocks(true).computeFrequency(true).build();

            for (Invoke childInvoke : invokes) {
                if (invokesWithExistingCallTreeNodes.contains(childInvoke)) {
                    continue;
                }
                FixedNode childInvokeNode = childInvoke.asFixedNode();
                if (childInvokeNode.isAlive()) {
                    double childFrequency = getInitialFrequency(cfg.blockFor(childInvokeNode)) * getFrequency();
                    CallTreeNode child = callTree().createChild(this, childInvoke, childFrequency);
                    assert !child.invoke().callTarget().arguments().contains(null) : child.invoke().callTarget().arguments();
                    addChild(child);
                } else {
                    // Skip this invoke. It was canonicalized after finding a more precise return
                    // stamp for one of the preceding invokes.
                }
            }
        }
    }

    private static double getInitialFrequency(HIRBlock initialBlock) {
        HIRBlock currentBlock = initialBlock;
        double currentFrequency = currentBlock.getRelativeFrequency();

        // TODO (yz) compute cfg with loop information.
        // Bump the frequency of cold invocation in a hot loop
        for (CFGLoop<HIRBlock> loop = currentBlock.getLoop(); loop != null; loop = loop.getParent()) {
            double loopHeaderFrequency = loop.getHeader().getRelativeFrequency();
            currentFrequency = Math.max(loopHeaderFrequency / 10, currentFrequency);
        }

        return InliningMath.restrictFrequency(currentFrequency);
    }

    private Stamp calcArgumentStamp(int index, ValueNode argument) {
        Stamp argumentStamp = argument.stamp(NodeView.DEFAULT);
        if (!monomorphic && index == 0) {
            if (dispatchedType != null) {
                argumentStamp = argumentStamp.improveWith(StampFactory.objectNonNull(TypeReference.createExactTrusted(dispatchedType)));
            }
        }
        return argumentStamp;
    }

    private boolean hasPotentialForEnhancement() {
        for (ParameterNode parameterNode : getReadonlySubgraph().getNodes(ParameterNode.TYPE)) {
            int index = parameterNode.index();
            NodeInputList<ValueNode> arguments = invoke().callTarget().arguments();
            if (index >= arguments.size()) {
                // TODO BS GR-45988
                // This is a quickfix for a transient and should be addressed properly.
                return false;
            }
            ValueNode argument = arguments.get(index);
            if (argument.isConstant()) {
                return true;
            }
            Stamp argumentStamp = calcArgumentStamp(index, argument);
            Stamp newImprovedStamp = parameterNode.stamp(NodeView.DEFAULT).tryImproveWith(argumentStamp);
            if (newImprovedStamp != null) {
                return true;
            }
            if ((argument instanceof AllocatedObjectNode || argument instanceof NewInstanceNode)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("try")
    @Override
    public boolean enhanceParameters() {
        int canonicalizationCount = 0;
        Invoke invoke = invoke();
        if (invoke != null && hasPotentialForEnhancement()) {
            StructuredGraph mutableSubgraph = subgraph.uniqueRef(null, this::updateChildrenInvokePointersAfterCopy);

            for (ParameterNode formalParameter : mutableSubgraph.getNodes(ParameterNode.TYPE).snapshot()) {
                int index = formalParameter.index();
                ValueNode actualParameter = invoke.callTarget().arguments().get(index);
                assert actualParameter != null : "Found null argument: " + invoke + ", " + invoke.callTarget().arguments();

                if (actualParameter.isConstant()) {
                    ConstantNode constant = (ConstantNode) actualParameter.copyWithInputs(false);
                    ConstantNode uniqueConstant = mutableSubgraph.unique(constant);
                    // The source position comes from the containing graph so it's not valid in the
                    // context of this graph.
                    uniqueConstant.clearNodeSourcePosition();
                    formalParameter.replaceAndDelete(uniqueConstant);

                    if (callTree().canonicalizeUsages(uniqueConstant)) {
                        canonicalizationCount++;
                    }
                } else {
                    Stamp originalStamp = formalParameter.stamp(NodeView.DEFAULT);
                    Stamp improvedStamp = originalStamp.tryImproveWith(calcArgumentStamp(index, actualParameter));
                    if (improvedStamp != null) {
                        assert !originalStamp.equals(improvedStamp) : "originalStamp = " + originalStamp + ", improvedStamp = " + improvedStamp;
                        assert originalStamp.tryImproveWith(improvedStamp) != null;
                        formalParameter.setStamp(improvedStamp);

                        if (callTree().canonicalizeUsages(formalParameter)) {
                            canonicalizationCount++;
                        }
                    }
                }
            }
        }

        // Check if children were removed.
        for (CallTreeNode child : children()) {
            if (child.invoke().asNode().isDeleted()) {
                child.replaceWithDeleted();
            }
        }

        // Some canonicalizations (e.g. method handles) introduce new invoke nodes -- check if nodes
        // were added.
        refreshImmediateChildren();

        // Recursively enhance children parameters.
        boolean anyChildChanged = false;
        for (CallTreeNode child : children()) {
            boolean childChanged = child.enhanceParameters();
            if (childChanged) {
                callTree().restoreSubtreeInvariants(child, false);
            }
            anyChildChanged |= childChanged;
        }

        callTree().restoreSubtreeInvariants(this, false);
        return anyChildChanged || canonicalizationCount > 0;
    }

    private void updateChildrenInvokePointersAfterCopy(UnmodifiableEconomicMap<Node, Node> mapping) {
        for (CallTreeNode child : children()) {
            if (child instanceof DeletedNode) {
                continue;
            }
            assert child.invoke().asNode().isAlive() : child;
            Node newInvoke = mapping.get(child.invoke().asNode());
            assert newInvoke != null;
            child.setInvoke((Invoke) newInvoke);
        }
    }

    public StructuredGraph getReadonlySubgraph() {
        return subgraph.readonly();
    }

    public ResolvedJavaMethod getDispatchedMethod() {
        return dispatchedMethod;
    }

    public ResolvedJavaType getDispatchedType() {
        return dispatchedType;
    }

    @Override
    public boolean isForceInlined() {
        return targetMethod() != null && (targetMethod().shouldBeInlined() || callTree().matchesForceInlineFilter(targetMethod()) ||
                        callTree().matchDirectedInline(this) != null);
    }

    @Override
    public EnumSet<BenefitKind> getBenefits() {
        return benefits;
    }

    public int getLocalCost() {
        if (intrinsic) {
            return BenefitCostTuple.ZERO_COST;
        }

        return InliningMath.getLocalCost(getReadonlySubgraph(), !monomorphic);
    }

    public int getRawLocalCost() {
        return getReadonlySubgraph().getNodeCount();
    }

    @Override
    public void inline(CoreProviders context, Consumer<CallTreeNode> considerChild, Consumer<CallTreeNode> removeChild,
                    Consumer<EconomicSet<Node>> trackCanonicalizable) {
        callTree().state().incNumMethodsInlined();
        String reason = getInlineCause().longDescription();
        InliningUtil.traceInlinedMethod(invoke(), getDepth(), true, invoke().callTarget().targetMethod(), reason);
        EconomicSet<Node> canonicalizableNodes = InliningUtil.inlineForCanonicalization(invoke(), getReadonlySubgraph(), true, dispatchedMethod, m -> removeDeletedInvokes(considerChild, m), reason,
                        "PriorityInliningPhase", new DuplicationUtil.EEInliningReturnAction(context));
        trackCanonicalizable.accept(canonicalizableNodes);
    }

    private void removeDeletedInvokes(Consumer<CallTreeNode> considerChild, UnmodifiableEconomicMap<Node, Node> mapping) {
        for (CallTreeNode child : children()) {
            if (child instanceof DeletedNode) {
                child.replaceAtPredecessor(null);
                child.safeDelete();
                continue;
            }

            assert child.invoke() != null;
            Node newInvoke = mapping.get(child.invoke().asNode());
            assert newInvoke != null;
            if (newInvoke.isAlive()) {
                Invoke copiedInvoke = (Invoke) newInvoke;
                child.setInvoke(copiedInvoke);
                if (isInOOMEProtectedInlineContext()) {
                    copiedInvoke.setInOOMETry(true);
                }
                considerChild.accept(child);
            } else {
                // TODO (yz) remove the node without inserting a DeletedNode
                DeletedNode deletedNode = callTree().add(new DeletedNode(child.compilationRootPosition(), (Invoke) newInvoke, child.getFrequency(), child.getCostBenefit().getBenefit()));
                child.replaceAtPredecessor(deletedNode);
                considerChild.accept(deletedNode);
            }
        }
    }

    public ResolvedJavaType getOriginalDispatchedType() {
        return originalDispatchedType;
    }

    @Override
    public void safeRecursiveDelete() {
        super.safeRecursiveDelete();
        subgraph.release();
    }

}
