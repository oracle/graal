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

import static jdk.graal.compiler.core.common.NativeImageSupport.inRuntimeCode;
import static jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase.Options.MinPolymorphicDispatchProbability;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicSet;

import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.priorityinline.CallTree;
import jdk.graal.compiler.phases.common.priorityinline.InliningProvider;
import jdk.graal.compiler.phases.common.priorityinline.nodes.devirtualization.Devirtualization;
import jdk.graal.compiler.phases.common.priorityinline.nodes.devirtualization.DevirtualizationUtil;
import jdk.graal.compiler.phases.common.priorityinline.nodes.devirtualization.MethodBasedDevirtualization;
import jdk.graal.compiler.phases.common.priorityinline.nodes.devirtualization.ReceiverBasedDevirtualization;
import jdk.graal.compiler.phases.common.priorityinline.nodes.dispatch.Dispatch;
import jdk.graal.compiler.phases.common.priorityinline.nodes.dispatch.DispatchInfo;
import jdk.graal.compiler.phases.common.priorityinline.nodes.dispatch.MethodDispatch;
import jdk.graal.compiler.phases.common.priorityinline.nodes.dispatch.ReceiverTypeDispatch;
import jdk.vm.ci.meta.AbstractJavaProfile;
import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Call-tree node that is used for virtual or indirect call targets. This class conceptually
 * represents a (virtual stub) method that does the virtual dispatch.
 * <p>
 * Each instance of this node is typically based on some profile, and its inlining generates a
 * devirtualization cascade, see {@link DevirtualizationUtil}. Which profile the node is based on,
 * and which kind of runtime checks it does to prove that a direct call can be made instead, is
 * determined by the {@link Dispatch} object associated with the node, which is its policy object.
 * <p>
 * Here is an example of what happens in the IR when a virtual call that in the call tree
 * corresponds to the {@link InlineCacheNode} that uses the {@link ReceiverTypeDispatch} object.
 * Before inlining:
 *
 * <pre>
 *       .        [..receiver..]              [..param..]
 *       |              |                          |
 *    [invoke]-----[MethodCallTarget List.add, profile = [ArrayList:0.9, LinkedList:0.1, other: 0.0]
 *       |
 *       .
 * </pre>
 *
 * After inlining (below is the actual devirtualization cascade):
 *
 * <pre>
 *                .                    [..receiver..]
 *                |                      |       |
 *               [if]---[instanceof ArrayList]   |
 *               /  \                            |
 *              /    \                           |
 *             /      \                          |
 *            /        \                         |
 *           /          [if]---[instanceof LinkedList]
 *          /            | \
 *   ... code for ...    |  \
 * ... ArrayList.add ... |   \
 *          |            |    \
 *          |            |   [deopt] <- because the not-recorded-probability is 0, and we are in JIT
 *          |            |                  (otherwise, we'd have a virtual call again here)
 *          |      ... code for ...
 *          |   ... LinkedList.add ...
 *          |       /
 *          |      /
 *          |     /
 *          |    /
 *          |   /
 *         [merge]
 *           |
 *           .
 * </pre>
 *
 * Similar patterns emerge for method-based dispatch. The inliner is tuned to limit the number of
 * cases in the devirtualization cascade. These cases are initially represented as
 * {@link CutoffNode}s below the {@link InlineCacheNode}. The inliner may expand some cutoffs or
 * all, and inline some nodes or all below the {@link InlineCacheNode}. This can happen in multiple
 * rounds of the inliner.
 */
@NodeInfo(nameTemplate = "IC({p#targetMethod/s}) [P = {p#priority}]")
public class InlineCacheNode extends ParentNode {
    public static final NodeClass<InlineCacheNode> TYPE = NodeClass.create(InlineCacheNode.class);

    private final int maxDispatches;
    private final Dispatch dispatch;

    /**
     * The original method intended as the target for this call-tree node.
     * <p>
     * NOTE: The value of this field may differ from the method targeted by the associated
     * {@link Invoke}, due to IR simplifications that can alter the static method signature during
     * compilation.
     * </p>
     */
    private final ResolvedJavaMethod originalTargetMethod;

    /**
     * Flag indicating whether the fallback invoke should be marked as polymorphic.
     */
    private boolean isPolymorphic;

    public InlineCacheNode(NodeSourcePosition compilationRootPosition, Invoke invoke, ResolvedJavaMethod originalTargetMethod, double frequency, AbstractJavaProfile<?, ?> profile, int maxDispatches) {
        super(compilationRootPosition, TYPE, invoke, frequency);
        assert maxDispatches > 0 : "Max number of dispatches must be positive for " + invoke + ", but is : " + maxDispatches;
        this.maxDispatches = maxDispatches;
        this.isPolymorphic = false;
        this.originalTargetMethod = originalTargetMethod;
        if (profile instanceof JavaTypeProfile) {
            this.dispatch = new ReceiverTypeDispatch((JavaTypeProfile) profile);
        } else {
            this.dispatch = new MethodDispatch((JavaMethodProfile) profile);
        }
    }

    @Override
    public Map<Object, Object> getDebugProperties(Map<Object, Object> map) {
        Map<Object, Object> properties = super.getDebugProperties(map);
        dispatch.addDebugProperties(map);
        return properties;
    }

    @Override
    public void createImmediateChildren(CallTreeNode caller) {
        assert callTree() != null : this + " is not in the graph.";
        dispatch.createChildren(caller, this);
    }

    /**
     * Given a list of {@link DispatchInfo} objects, provided by the {@link Dispatch} policy, this
     * creates the child nodes of this {@link InlineCacheNode}.
     */
    public final void createChildrenFromDispatches(CallTreeNode caller, ArrayList<DispatchInfo> dispatches, double adjustedNotRecorded) {
        if (adjustedNotRecorded > 0) {
            this.isPolymorphic = true;
        }

        // Only take the largest dispatches, with positive probabilities.
        Invoke invoke = invoke();
        final OptionValues options = invoke.asNode().getOptions();
        double notRecorded = adjustedNotRecorded;
        ArrayList<DispatchInfo> dispatchInfos = dispatches;
        int maxPolymorphicDispatches = caller.callTree().inliningProvider().getMaxPolymorphicDispatches(options);
        if (notRecorded > 0 || dispatchInfos.size() > maxPolymorphicDispatches) {
            ArrayList<DispatchInfo> relevantDispatches = new ArrayList<>();

            int dispatchesLeft = maxDispatches;
            for (DispatchInfo info : dispatchInfos) {
                if (callTree().matchesForceInlineFilter(info.dispatchedMethod) ||
                                callTree().matchesDirectedInline(invoke, info.dispatchedMethod, info.dispatchedType, caller)) {
                    // Force-inlined and directed dispatches are not counted toward the maximum
                    // number of dispatches.
                    relevantDispatches.add(info);
                } else if (dispatchesLeft > 0 && info.probability >= MinPolymorphicDispatchProbability.getValue(options)) {
                    relevantDispatches.add(info);
                    dispatchesLeft--;
                } else {
                    notRecorded += info.probability;
                }
            }

            if (dispatchInfos.size() - relevantDispatches.size() > 1) {
                isPolymorphic = true;
            }

            dispatchInfos = relevantDispatches;
        }

        assert getFrequency() > 0.0 : "The frequency of the call tree node was not clamped to a non-zero range: " + this;

        ResolvedJavaMethod targetMethod = invoke.getTargetMethod();
        for (DispatchInfo info : dispatchInfos) {
            assert !info.needsMethodDispatch || callTree().inliningProvider().isMethodForDevirtualizationInTable(originalTargetMethod, targetMethod, info.dispatchedMethod,
                            invoke.getReceiverType()) : info.dispatchedMethod.format("%H.%n") + " at " + this +
                                            " is not in the virtual method table of " + invoke.getReceiverType();
            // TODO: Check if this can always be replaced with createDirectChild or
            // createCutoffNode (GR-24328).
            addChild(callTree().createChild(caller, invoke, info.dispatchedMethod, info.needsMethodDispatch ? null : info.dispatchedType, info.dispatchedType,
                            info.probability * getFrequency()));
        }

        if (notRecorded > 0) {
            addChild(callTree().createGenericChild(caller, invoke, notRecorded * getFrequency(), DontInlineCause.Indirect));
        }

        assert checkChildren();
    }

    private boolean checkChildren() {
        assert children().size() > 0 : "Must have non-empty list of " + children();
        assert containsNoIndirectNode(this) : "Must not contain indirect children " + children();
        return true;
    }

    /**
     * Inlines the {@link SubgraphNode}s below this node as described in the top-level comment.
     */
    @Override
    @SuppressWarnings("try")
    public void inline(CoreProviders context, Consumer<CallTreeNode> considerChild, Consumer<CallTreeNode> removeChild,
                    Consumer<EconomicSet<Node>> trackCanonicalizable) {
        callTree().state().incNumMethodsInlined();
        //
        // Step 1: Identify inlined and postponed cases.
        //
        StructuredGraph graph = invoke().asNode().graph();
        ArrayList<SubgraphNode> inlinedSubgraphs = new ArrayList<>();
        ArrayList<CallTreeNode> postponedChildren = new ArrayList<>();
        ArrayList<IndirectNode> indirectNodes = new ArrayList<>();
        GenericNode fallbackChild = null;
        boolean hasIndirectCalls = false;
        double indirectFrequencySum = 0.0;
        for (CallTreeNode child : children()) {
            if (child.isMarkedInlined()) {
                inlinedSubgraphs.add((SubgraphNode) child);
            } else if (child instanceof SubgraphNode || child instanceof CutoffNode) {
                postponedChildren.add(child);
            } else if (child instanceof IndirectNode) {
                indirectNodes.add((IndirectNode) child);
                // Record the fact that there should be a fallback.
                hasIndirectCalls = true;
                indirectFrequencySum += child.getFrequency();
            } else {
                assert child instanceof GenericNode : "Node: " + child;
                fallbackChild = (GenericNode) child;
            }
        }
        if (hasIndirectCalls) {
            if (fallbackChild == null) {
                fallbackChild = callTree().add(new GenericNode(compilationRootPosition(), this.invoke(), indirectFrequencySum, DontInlineCause.Indirect));
            } else {
                fallbackChild.setFrequency(fallbackChild.getFrequency() + indirectFrequencySum);
            }
        }

        for (IndirectNode node : indirectNodes) {
            node.replaceAtPredecessor(null);
            node.safeDelete();
        }

        //
        // Step 2: If there are any children that should be inlined, create an inline cache for
        // them.
        //
        if (!inlinedSubgraphs.isEmpty()) {
            try (DebugCloseable scope = graph.withNodeSourcePosition(invoke().asNode())) {
                SpeculationLog.Speculation speculation = SpeculationLog.NO_SPECULATION;
                if (fallbackChild == null && postponedChildren.isEmpty()) {
                    SpeculationLog speculationLog = graph.getSpeculationLog();
                    if (speculationLog != null) {
                        speculation = dispatch.tryCreateDeoptSpeculation(this, speculationLog);
                        if (speculation == SpeculationLog.NO_SPECULATION) {
                            // indirectFrequencySum must be 0 because we don't have a fallbackChild
                            // and thus no generic calls or indirect calls (if there were we would
                            // have already created a fallbackChild
                            assert indirectFrequencySum == 0 : "Frequency for indirect call must be 0";
                            fallbackChild = callTree().add(new GenericNode(compilationRootPosition(), this.invoke(), indirectFrequencySum, DontInlineCause.CantSpeculate));
                        }
                    }
                }
                createDevirtualizationCascade(context, inlinedSubgraphs, fallbackChild == null && postponedChildren.isEmpty(), speculation, trackCanonicalizable);
            }
        }

        //
        // Step 3: Update the inline-cache node in the call tree, and prune the inlined cases from
        // the profile.
        //
        double inlinedFrequencySum = 0.0;
        for (CallTreeNode child : children()) {
            if (child != null && child.isMarkedInlined()) {
                considerChild.accept(child);
                inlinedFrequencySum += child.getFrequency();
            }
        }

        // We distinguish the following cases:
        // 1. There are child nodes that have been postponed.
        // 2. There are no remaining subgraph child nodes.
        //
        // Orthogonally:
        // 1. There was a generic child fallback (i.e. deopt was not emitted for the generic case).
        // 2. There was no generic child fallback (i.e. a deopt was emitted for the generic case).
        if (!postponedChildren.isEmpty()) {
            // There are postponed child nodes.
            assert fallbackChild == null || this.invoke().asNode().isAlive() : "Must not have a fallback or the invoke is alive " + invoke() + " " + this;
            AbstractJavaProfile<?, ?> newProfile = dispatch.createProfileForPostponed(postponedChildren);
            InlineCacheNode inlineCacheNode = callTree().add(new InlineCacheNode(compilationRootPosition(), invoke(), originalTargetMethod(), getFrequency() - inlinedFrequencySum, newProfile,
                            CallTree.profileLength(newProfile)));
            considerChild.accept(inlineCacheNode);
            for (CallTreeNode child : postponedChildren) {
                if (child.parent() != null) {
                    child.replaceAtPredecessor(null);
                }
                inlineCacheNode.children().add(child);
            }
            if (fallbackChild != null) {
                if ((fallbackChild.isAlive() || fallbackChild.isDeleted()) && fallbackChild.parent() != null) {
                    fallbackChild.replaceAtPredecessor(null);
                }
                inlineCacheNode.children().add(fallbackChild);
            }
            assert inRuntimeCode() || containsNoIndirectNode(inlineCacheNode) : "Outside NI must not have indirect nodes " + inlineCacheNode;
        } else {
            // There are no postponed child nodes.
            assert fallbackChild != null || this.invoke().asNode().isDeleted() : "Node: " + this + ", invoke: " + invoke();
            if (fallbackChild != null) {
                // We replace the generic call node with an indirect call node.
                if (fallbackChild.isAlive() || fallbackChild.isDeleted()) {
                    removeChild.accept(fallbackChild);
                }
                AbstractJavaProfile<?, ?> newProfile = dispatch.createProfileForEmpty();
                // TODO: Replace with createIndirectChild, and check if this is always valid
                // (GR-24328).
                CallTreeNode replacement = callTree().createInlineCacheOrIndirectChild(this.parent(), invoke(), (MethodCallTargetNode) invoke().callTarget(), getFrequency() - inlinedFrequencySum,
                                newProfile);
                considerChild.accept(replacement);
            }
        }
    }

    @Override
    public boolean enhanceParameters() {
        CallTreeNode direct = callTree().replaceWithDirectIfApplicable(this);
        if (direct != this) {
            callTree().restoreSubtreeInvariants(direct, false);
            return true;
        }

        boolean changed = false;
        for (CallTreeNode child : children()) {
            if (child instanceof SubgraphNode) {
                changed |= child.enhanceParameters();
            }
        }

        callTree().restoreSubtreeInvariants(this, false);
        return changed;
    }

    /**
     * Creates the devirtualization cascade (as described in the top-level comment) for the list of
     * {@link SubgraphNode}s that are the children of this node. The {@link Devirtualization}
     * strategy for the cascade is {@link SubgraphNodeMethodBasedDevirtualization}, which is
     * produced from the respective {@link SubgraphNode}s.
     */
    @SuppressWarnings("try")
    public void createDevirtualizationCascade(CoreProviders coreProviders, List<SubgraphNode> subgraphs, boolean useDeoptAsFallback, SpeculationLog.Speculation speculation,
                    Consumer<EconomicSet<Node>> trackCanonicalizable) {
        InliningProvider inliningProvider = callTree().inliningProvider();
        Invoke invoke = invoke();
        double totalFrequency = this.getFrequency();
        List<Devirtualization> devirtualizations = new ArrayList<>();
        for (SubgraphNode subgraph : subgraphs) {
            if (subgraph.getDispatchedType() != null) {
                devirtualizations.add(new SubgraphNodeReceiverBasedDevirtualization(subgraph, totalFrequency));
            } else {
                devirtualizations.add(new SubgraphNodeMethodBasedDevirtualization(subgraph, originalTargetMethod, totalFrequency));
            }
        }
        DevirtualizationUtil.createDevirtualizationCascade(coreProviders, inliningProvider, invoke, devirtualizations, useDeoptAsFallback, isPolymorphic,
                        speculation, trackCanonicalizable);
    }

    @Override
    public void setInvoke(Invoke invoke) {
        super.setInvoke(invoke);
        for (CallTreeNode child : children()) {
            child.setInvoke(invoke);
        }
    }

    @Override
    public boolean isMarkedInlined() {
        return super.isMarkedInlined() && hasAnyChildMarkedInline();
    }

    private boolean hasAnyChildMarkedInline() {
        for (CallTreeNode child : children()) {
            if (child.isMarkedInlined()) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsNoIndirectNode(InlineCacheNode inlineCacheNode) {
        for (CallTreeNode child : inlineCacheNode.children()) {
            if (child instanceof IndirectNode) {
                return false;
            }
        }
        return true;
    }

    public ResolvedJavaMethod originalTargetMethod() {
        return originalTargetMethod;
    }

    static class SubgraphNodeReceiverBasedDevirtualization extends ReceiverBasedDevirtualization {
        private final SubgraphNode subgraphNode;
        private final double totalFrequency;

        SubgraphNodeReceiverBasedDevirtualization(SubgraphNode subgraphNode, double totalFrequency) {
            this.subgraphNode = subgraphNode;
            this.totalFrequency = totalFrequency;
        }

        @Override
        public NodeSourcePosition callerPosition() {
            return subgraphNode.getCallerPosition();
        }

        @Override
        public double probability() {
            return subgraphNode.getFrequency() / totalFrequency;
        }

        @Override
        protected ResolvedJavaType dispatchedType() {
            return subgraphNode.getDispatchedType();
        }

        @Override
        protected ResolvedJavaMethod dispatchedMethod() {
            return subgraphNode.getDispatchedMethod();
        }

        @Override
        protected void setDuplicatedInvoke(Invoke duplicatedInvoke) {
            subgraphNode.setInvoke(duplicatedInvoke);
        }

        @Override
        protected Invoke duplicatedInvoke() {
            return subgraphNode.invoke();
        }

    }

    static class SubgraphNodeMethodBasedDevirtualization extends MethodBasedDevirtualization {
        private final SubgraphNode subgraphNode;
        private final ResolvedJavaMethod originalTargetMethod;
        private final double totalFrequency;

        SubgraphNodeMethodBasedDevirtualization(SubgraphNode subgraphNode, ResolvedJavaMethod originalTargetMethod, double totalFrequency) {
            this.subgraphNode = subgraphNode;
            this.originalTargetMethod = originalTargetMethod;
            this.totalFrequency = totalFrequency;
        }

        @Override
        public NodeSourcePosition callerPosition() {
            return subgraphNode.getCallerPosition();
        }

        @Override
        public double probability() {
            return subgraphNode.getFrequency() / totalFrequency;
        }

        @Override
        protected ResolvedJavaMethod dispatchedMethod() {
            return subgraphNode.getDispatchedMethod();
        }

        @Override
        protected ResolvedJavaMethod originalTargetMethod() {
            return originalTargetMethod;
        }

        @Override
        protected void setDuplicatedInvoke(Invoke duplicatedInvoke) {
            subgraphNode.setInvoke(duplicatedInvoke);
        }

        @Override
        protected Invoke duplicatedInvoke() {
            return subgraphNode.invoke();
        }

    }
}
