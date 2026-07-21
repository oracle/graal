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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.phases.common.inlining.DirectedInliningRules;
import jdk.graal.compiler.phases.common.priorityinline.CallTree;
import jdk.graal.compiler.phases.common.priorityinline.Expander;
import jdk.graal.compiler.phases.common.priorityinline.data.BenefitCostTuple;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@NodeInfo(cycles = NodeCycles.CYCLES_IGNORED, size = NodeSize.SIZE_IGNORED)
public abstract class CallTreeNode extends Node implements Comparable<CallTreeNode> {
    public static final NodeClass<CallTreeNode> TYPE = NodeClass.create(CallTreeNode.class);

    private static final int UNINITIALIZED_RECURSION_DEPTH = -1;

    private final NodeSourcePosition compilationRootPosition;
    private Invoke invoke;
    private double frequency;
    private int cutoffCount;
    private int activeCutoffCount;
    private int deadendCount;
    private int recursionDepth;
    private double localBenefit;
    private double priority;
    private double maxLeafPriority;
    private int subtreeTotalCompilerNodeCount;
    private int subtreeTotalCutoffCodeSize;
    private BenefitCostTuple costBenefitTuple;
    private BenefitCostTuple inlineAllCostBenefitTuple;
    private Collection<CallTreeNode> nonInlinedDescendants;
    private int costBenefitLastAchievedUpdateEpoch;
    private int costBenefitLastMissedUpdateEpoch;
    private boolean mustInline;
    private boolean hasChildForParameterEnhancement;
    private boolean needsParameterEnhancement;
    private InlineCause inlineCause;
    private DontInlineCause dontInlineCause;
    private DirectedInliningRules.Callsite[] directedInliningCallsites;
    // TODO AP GR-42092 Remove this in favour of compilationRootPosition;
    private NodeSourcePosition callerPosition;

    public CallTreeNode(NodeClass<? extends Node> c, NodeSourcePosition compilationRootPosition, Invoke invoke, double frequency) {
        super(c);
        this.compilationRootPosition = compilationRootPosition;
        this.invoke = invoke;
        this.frequency = frequency;
        this.recursionDepth = UNINITIALIZED_RECURSION_DEPTH;
        this.nonInlinedDescendants = Collections.emptyList();
        this.costBenefitLastMissedUpdateEpoch = 1;
        this.needsParameterEnhancement = true;
        this.inlineCause = InlineCause.Unspecified;
        this.dontInlineCause = DontInlineCause.Unspecified;
    }

    @Override
    public Map<Object, Object> getDebugProperties(Map<Object, Object> map) {
        Map<Object, Object> debugProperties = super.getDebugProperties(map);
        if (invoke() != null && invoke().callTarget() != null) {
            debugProperties.put("targetMethod", invoke().callTarget().targetName());
        } else {
            debugProperties.put("targetMethod", "<invoke or call target null>");
        }
        if (invoke() != null) {
            NodeSourcePosition nsp = invoke().asNode().getNodeSourcePosition();
            debugProperties.put("invokeNodeSourcePosition", nsp != null ? nsp.toString() : "null");
        }
        return debugProperties;
    }

    public final CallTreeNode parent() {
        assert this.graph() != null;
        Node pred = this.predecessor();
        return (CallTreeNode) pred;
    }

    public List<CallTreeNode> children() {
        return Collections.emptyList();
    }

    public CallTreeNode childFor(ResolvedJavaMethod method) {
        for (CallTreeNode child : children()) {
            if (child.targetMethod().equals(method)) {
                return child;
            }
        }
        return null;
    }

    public CallTreeNode childForInvoke(Invoke childInvoke) {
        for (CallTreeNode child : children()) {
            if (child.invoke().equals(childInvoke)) {
                return child;
            }
        }
        return null;
    }

    public CallTree callTree() {
        return (CallTree) graph();
    }

    public Invoke invoke() {
        return invoke;
    }

    public DirectedInliningRules.Callsite[] directedInliningCallsites() {
        return directedInliningCallsites;
    }

    public void setDirectedInliningCallsites(DirectedInliningRules.Callsite[] directedInliningCallsites) {
        this.directedInliningCallsites = directedInliningCallsites;
    }

    /**
     * Returns whether this node's graph must preserve explicit allocation OOME edges for its inline
     * context.
     */
    public boolean isInOOMEProtectedInlineContext() {
        return callTree().isInOOMEProtectedInlineContext(this);
    }

    public double getFrequency() {
        return frequency;
    }

    public void setFrequency(double frequency) {
        this.frequency = frequency;
    }

    public int cutoffCount() {
        return cutoffCount;
    }

    protected void setCutoffCount(int cutoffCount) {
        assert NumUtil.assertNonNegativeInt(cutoffCount);
        this.cutoffCount = cutoffCount;
    }

    public int activeCutoffCount() {
        return activeCutoffCount;
    }

    public void setActiveCutoffCount(int activeCutoffCount) {
        assert NumUtil.assertNonNegativeInt(activeCutoffCount);
        this.activeCutoffCount = activeCutoffCount;
    }

    public void increaseActiveCutoffCount(int n) {
        this.activeCutoffCount += n;
    }

    public int deadendCount() {
        return deadendCount;
    }

    protected void setDeadendCount(int deadendCount) {
        assert NumUtil.assertNonNegativeInt(deadendCount);
        this.deadendCount = deadendCount;
    }

    public abstract void initializeCounts();

    public double getPriority() {
        return priority;
    }

    public void setPriority(double priority) {
        this.priority = ((int) (priority * 100000)) / 100000.0;
    }

    public double getMaxLeafPriority() {
        return maxLeafPriority;
    }

    public void setMaxLeafPriority(double maxLeafPriority) {
        this.maxLeafPriority = maxLeafPriority;
    }

    public void setPriorityAndMaxLeafPriority(double priority) {
        setPriority(priority);
        setMaxLeafPriority(getPriority());
    }

    public boolean hasActiveCutoffs() {
        return activeCutoffCount > 0;
    }

    public void setLowestPriority() {
        setPriorityAndMaxLeafPriority(Expander.NODE_LOWEST_PRIORITY);
    }

    public int getSubtreeTotalCompilerNodeCount() {
        return subtreeTotalCompilerNodeCount;
    }

    public double getLocalBenefit() {
        return localBenefit;
    }

    public void setLocalBenefit(double localBenefit) {
        this.localBenefit = localBenefit;
    }

    @Override
    public int compareTo(CallTreeNode that) {
        if (this.priority > that.priority) {
            return -1;
        } else if (that.priority > this.priority) {
            return 1;
        } else {
            return 0;
        }
    }

    public BenefitCostTuple getCostBenefit() {
        return costBenefitTuple;
    }

    public void setCostBenefitTuple(BenefitCostTuple costBenefitTuple) {
        this.costBenefitTuple = costBenefitTuple;
    }

    public Collection<CallTreeNode> getNonInlinedDescendants() {
        return nonInlinedDescendants;
    }

    public void setNonInlinedDescendants(Collection<CallTreeNode> nonInlinedDescendants) {
        this.nonInlinedDescendants = nonInlinedDescendants;
    }

    public void markNeedsCostBenefitUpdate() {
        costBenefitLastMissedUpdateEpoch++;
    }

    public boolean needsCostBenefitUpdate() {
        return costBenefitLastAchievedUpdateEpoch < costBenefitLastMissedUpdateEpoch;
    }

    public void markCostBenefitUpdated() {
        costBenefitLastAchievedUpdateEpoch = costBenefitLastMissedUpdateEpoch;
    }

    public void markInlined() {
        mustInline = true;
    }

    public void markNotInlined() {
        mustInline = false;
    }

    public boolean isMarkedInlined() {
        return mustInline;
    }

    public boolean isForceInlined() {
        return false;
    }

    public void setInvoke(Invoke invoke) {
        assert this.invoke != null;
        assert invoke != null;
        this.invoke = invoke;
    }

    public void updateSubtreeStatistics() {
        int totalCompilerNodeCount = 0;
        if (this instanceof SubgraphNode) {
            totalCompilerNodeCount += ((SubgraphNode) this).getReadonlySubgraph().getNodeCount();
        }
        int totalCutoffCodeSize = 0;
        if (this instanceof CutoffNode) {
            totalCutoffCodeSize += 1 + this.invoke().callTarget().targetMethod().getCodeSize();
        }
        for (CallTreeNode child : children()) {
            totalCompilerNodeCount += child.getSubtreeTotalCompilerNodeCount();
            totalCutoffCodeSize += child.getSubtreeTotalCutoffCodeSize();
        }
        this.subtreeTotalCompilerNodeCount = totalCompilerNodeCount;
        this.subtreeTotalCutoffCodeSize = totalCutoffCodeSize;
    }

    public int getDepth() {
        int depth = 0;
        Invoke currentInvoke = this.invoke();
        CallTreeNode current = this;
        while (current != null) {
            if (current instanceof SubgraphNode) {
                depth++;
            }
            if (current.parent() != null) {
                currentInvoke = current.invoke();
            }
            current = current.parent();
        }
        if (currentInvoke != null && currentInvoke.stateAfter() != null) {
            FrameState frameState = currentInvoke.stateAfter().outerFrameState();
            while (frameState != null) {
                depth += 1;
                frameState = frameState.outerFrameState();
            }
        }
        return depth;
    }

    public int getRecursionDepth() {
        if (recursionDepth == UNINITIALIZED_RECURSION_DEPTH) {
            int depth = 0;
            ResolvedJavaMethod targetMethod = this.targetMethod();

            // First, traverse the call graph until reaching the root method.
            Invoke currInvoke = this.invoke();
            CallTreeNode curr = this.parent();
            while (curr != null) {
                assert curr.invoke() != null || curr.parent() == null : "Node: " + this;
                if (curr instanceof SubgraphNode && curr.invoke() != null && curr.targetMethod().equals(targetMethod)) {
                    depth++;
                }
                // We only update the current invoke if we are not in the root method.
                if (curr.parent() != null) {
                    currInvoke = curr.invoke();
                }
                curr = curr.parent();
            }

            // Second, traverse the frame state chain.
            FrameState frameState = currInvoke.stateAfter().outerFrameState();
            while (frameState != null) {
                if (targetMethod.equals(frameState.getMethod())) {
                    depth++;
                }
                frameState = frameState.outerFrameState();
            }
            recursionDepth = depth;
        }
        return recursionDepth;
    }

    public NodeSourcePosition getCallerPosition() {
        if (callerPosition == null) {
            CallTreeNode currentNode = this;
            NodeSourcePosition position = null;

            // Traverse the call graph until reaching the root method.
            while (currentNode != null) {
                assert currentNode.invoke() != null || currentNode.parent() == null : "Node: " + this;
                // We only update the current invoke if we are not in the root method.
                // Skip the InlineCacheNode as it refers to the same invoke as its child(ren)
                if (currentNode.parent() != null && !(currentNode instanceof InlineCacheNode)) {
                    NodeSourcePosition currentPosition = currentNode.invoke().asNode().getNodeSourcePosition();

                    if (currentPosition != null) {
                        NodeSourcePosition currentPositionClone = new NodeSourcePosition(currentPosition.getCaller(), currentPosition.getMethod(), currentPosition.getBCI());

                        if (position == null) {
                            position = currentPositionClone;
                        } else {
                            position = position.addCaller(currentPositionClone);
                        }
                    }
                }
                currentNode = currentNode.parent();
            }
            callerPosition = position;
        }

        return callerPosition;
    }

    public ResolvedJavaMethod targetMethod() {
        return invoke().getTargetMethod();
    }

    public void safeRecursiveDelete() {
        for (CallTreeNode child : this.children()) {
            child.replaceAtPredecessor(null);
            child.safeRecursiveDelete();
        }
        safeDelete();
    }

    public void setInlineAllCostBenefitTuple(BenefitCostTuple inlineAllCostBenefitTuple) {
        this.inlineAllCostBenefitTuple = inlineAllCostBenefitTuple;
    }

    public BenefitCostTuple getInlineAllCostBenefitTuple() {
        return inlineAllCostBenefitTuple;
    }

    public boolean hasChildForParameterEnhancement() {
        return hasChildForParameterEnhancement;
    }

    public void setHasChildForParameterEnhancement(boolean hasChildForParameterEnhancement) {
        this.hasChildForParameterEnhancement = hasChildForParameterEnhancement;
    }

    public boolean needsParameterEnhancement() {
        return needsParameterEnhancement;
    }

    public void setNeedsParameterEnhancement(boolean needsParameterEnhancement) {
        this.needsParameterEnhancement = needsParameterEnhancement;
    }

    public boolean enhanceParameters() {
        return false;
    }

    public int getSubtreeTotalCutoffCodeSize() {
        return subtreeTotalCutoffCodeSize;
    }

    public InlineCause getInlineCause() {
        return inlineCause;
    }

    public void setInlineCause(InlineCause inlineCause) {
        this.inlineCause = inlineCause;
    }

    public DontInlineCause getDontInlineCause() {
        return dontInlineCause;
    }

    public void setDontInlineCause(DontInlineCause dontInlineCause) {
        this.dontInlineCause = dontInlineCause;
    }

    public DeletedNode replaceWithDeleted() {
        assert !isDeleted() : "Must not be deleted " + this;
        DeletedNode deletedNode = callTree().add(new DeletedNode(compilationRootPosition, invoke(), getFrequency(), getLocalBenefit()));
        replaceAtPredecessor(deletedNode);
        safeRecursiveDelete();
        return deletedNode;
    }

    public void replaceWithAndDelete(CallTreeNode replacementNode) {
        replaceAtPredecessor(replacementNode);
        safeDelete();
    }

    public void adjustSubtreeFrequency(double factor) {
        if (this instanceof ParentNode) {
            for (CallTreeNode child : children()) {
                child.adjustSubtreeFrequency(factor);
            }
        }
        setFrequency(getFrequency() * factor);
    }

    public void preOrderTraverse(Consumer<CallTreeNode> f) {
        f.accept(this);
        for (CallTreeNode child : children()) {
            child.preOrderTraverse(f);
        }
    }

    public boolean isRoot() {
        return callTree().root() == this;
    }

    public NodeSourcePosition compilationRootPosition() {
        return compilationRootPosition;
    }
}
