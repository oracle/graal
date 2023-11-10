/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.phases.inlining;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.UnmodifiableEconomicMap;

import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilationTask;

import jdk.graal.compiler.core.common.PermanentBailoutException;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeSuccessorList;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.phases.common.inlining.InliningUtil;
import jdk.graal.compiler.phases.common.inlining.InliningUtil.InlineeReturnAction;
import jdk.graal.compiler.phases.contract.NodeCostUtil;
import jdk.graal.compiler.truffle.PerformanceInformationHandler;
import jdk.graal.compiler.truffle.TruffleCompilerOptions.PerformanceWarningKind;
import jdk.graal.compiler.truffle.TruffleTierContext;
import jdk.vm.ci.meta.JavaConstant;

@NodeInfo(nameTemplate = "{p#directCallTarget}", cycles = NodeCycles.CYCLES_IGNORED, size = NodeSize.SIZE_IGNORED)
public final class CallNode extends Node implements Comparable<CallNode> {

    private static final NodeClass<CallNode> TYPE = NodeClass.create(CallNode.class);
    private final JavaConstant callNode;
    private final TruffleCompilable directCallTarget;
    private final int truffleCallees;
    private final double rootRelativeFrequency;
    private final int depth;
    private final int id;
    // Should be final, but needs to be mutable to be corrected if the language marks a non-trivial
    // root node as trivial
    private boolean trivial;
    // Effectively final, populated only as part of expansion. Cannot be final because of Successor
    // annotation
    @Successor private NodeSuccessorList<CallNode> children;
    private State state;
    // Effectively final, cannot be because policies need access to the CallNode to create
    // policyData.
    private Object policyData;
    // Effectively final, cannot be initialized in the constructor because needs getParent() to
    // calculate
    private int recursionDepth;
    // Effectively final, populated only as part of expanded if debug dump level >= info
    @SuppressWarnings("unused") private StructuredGraph irAfterPE;
    // Effectively final, populated only as part of expanded
    private StructuredGraph ir;
    // Effectively final, populated only as part of expanded (unless root, root does not have
    // invoke)
    private Invoke invoke;
    // Only used if TruffleCompilerOptions.InliningUseSize is true
    private int graphSize;

    private boolean forced;

    // Needs to be protected because of the @NodeInfo annotation
    protected CallNode(JavaConstant callNode, TruffleCompilable directCallTarget, double rootRelativeFrequency, int depth, int id, boolean forced) {
        super(TYPE);
        this.state = State.Cutoff;
        this.recursionDepth = -1;
        this.rootRelativeFrequency = rootRelativeFrequency;
        this.callNode = callNode;
        this.directCallTarget = directCallTarget;
        this.truffleCallees = directCallTarget != null ? directCallTarget.countDirectCallNodes() : 0;
        this.trivial = directCallTarget != null && directCallTarget.isTrivial();
        this.children = new NodeSuccessorList<>(this, 0);
        this.depth = depth;
        this.id = id;
        this.forced = forced;
    }

    public JavaConstant getCallNode() {
        return callNode;
    }

    /**
     * Returns a fully expanded and partially evaluated CallNode to be used as a root of a callTree.
     */
    static CallNode makeRoot(TruffleTierContext context, CallTree callTree) {
        Objects.requireNonNull(callTree);
        Objects.requireNonNull(context);
        CallNode root = new CallNode(null, context.compilable, 1, 0, callTree.nextId(), false);
        callTree.add(root);
        root.ir = context.graph;
        root.policyData = callTree.getPolicy().newCallNodeData(root);
        final GraphManager.Entry entry = callTree.getGraphManager().peRoot();
        root.irAfterPE = entry.graphAfterPEForDebugDump;
        root.graphSize = entry.graphSize;
        EconomicSet<Invoke> invokeToTruffleCallNode = entry.invokeToTruffleCallNode;
        root.verifyTrivial(entry);
        addChildren(context, root, invokeToTruffleCallNode);
        root.state = State.Inlined;
        callTree.getPolicy().afterExpand(root);
        callTree.frontierSize = root.children.size();
        return root;
    }

    private static void addChildren(TruffleTierContext context, CallNode node, EconomicSet<Invoke> invokeToTruffleCallNode) {
        for (Invoke invoke : invokeToTruffleCallNode) {
            if (!invoke.isAlive()) {
                continue;
            }
            ValueNode nodeArgument = invoke.callTarget().arguments().get(1);
            if (!nodeArgument.isJavaConstant()) {
                throw GraalError.shouldNotReachHere("CallNode must be a constant but was " + nodeArgument);
            }
            JavaConstant callNodeConstant = nodeArgument.asJavaConstant();
            int callNodeCount = getCallCount(context, callNodeConstant);
            TruffleCompilable currentTarget = getCurrentCallTarget(context, callNodeConstant);
            boolean forced = isInliningForced(context, callNodeConstant);
            double relativeFrequency = calculateFrequency(node.directCallTarget, callNodeCount);
            double childFrequency = relativeFrequency * node.rootRelativeFrequency;
            CallNode callNode = new CallNode(callNodeConstant, currentTarget, childFrequency, node.depth + 1, node.getCallTree().nextId(), forced);
            node.getCallTree().add(callNode);
            node.children.add(callNode);
            callNode.policyData = node.getPolicy().newCallNodeData(callNode);
            callNode.setInvokeOrRemove(invoke);
        }
        node.getPolicy().afterAddChildren(node);
    }

    static TruffleCompilable getCurrentCallTarget(TruffleTierContext context, JavaConstant directCallNode) {
        JavaConstant constant = context.getConstantReflection().readFieldValue(context.types().OptimizedDirectCallNode_currentCallTarget, directCallNode);
        return context.runtime().asCompilableTruffleAST(constant);
    }

    static int getCallCount(TruffleTierContext context, JavaConstant directCallNode) {
        return context.getConstantReflection().readFieldValue(context.types().OptimizedDirectCallNode_callCount, directCallNode).asInt();
    }

    static boolean isInliningForced(TruffleTierContext context, JavaConstant directCallNode) {
        return context.getConstantReflection().readFieldValue(context.types().OptimizedDirectCallNode_inliningForced, directCallNode).asBoolean();
    }

    private static double calculateFrequency(TruffleCompilable target, int callNodeCount) {
        return (double) Math.max(1, callNodeCount) / (double) Math.max(1, target.getCallCount());
    }

    public TruffleCompilable getDirectCallTarget() {
        return directCallTarget;
    }

    private void putProperties(Map<Object, Object> properties) {
        if (state == State.Indirect) {
            return;
        }
        properties.put("Frequency", rootRelativeFrequency);
        properties.put("Recursion Depth", getRecursionDepth());
        properties.put("IR Nodes", ir == null ? 0 : ir.getNodeCount());
        properties.put("Graph Size", graphSize);
        properties.put("Truffle Callees", truffleCallees);
        properties.put("Explore/inline ratio", exploreInlineRatio());
        properties.put("Depth", depth);
        properties.put("Forced", isForced());
        getPolicy().putProperties(this, properties);
    }

    private double exploreInlineRatio() {
        CallTree callTree = getCallTree();
        return isRoot() ? (double) callTree.expanded / callTree.inlined : Double.NaN;
    }

    public int getRecursionDepth() {
        if (recursionDepth == -1) {
            recursionDepth = computeRecursionDepth();
        }
        return recursionDepth;
    }

    private int computeRecursionDepth() {
        return computeRecursionDepth(getParent(), directCallTarget);
    }

    private int computeRecursionDepth(CallNode node, TruffleCompilable target) {
        if (node == null) {
            return 0;
        }
        int parentDepth = computeRecursionDepth(node.getParent(), target);
        if (node.directCallTarget.isSameOrSplit(target)) {
            return parentDepth + 1;
        } else {
            return parentDepth;
        }
    }

    public int getDepth() {
        return depth;
    }

    public InliningPolicy getPolicy() {
        return getCallTree().getPolicy();
    }

    private void setInvokeOrRemove(Invoke newInvoke) {
        if (newInvoke == null || !newInvoke.isAlive()) {
            remove();
        } else {
            invoke = newInvoke;
        }
    }

    public void remove() {
        state = State.Removed;
        getPolicy().removedNode(this);
    }

    private void addIndirectChildren(GraphManager.Entry entry) {
        for (Invoke indirectInvoke : entry.indirectInvokes) {
            if (indirectInvoke != null && indirectInvoke.isAlive()) {
                CallNode child = new CallNode(null, null, 0, depth + 1, getCallTree().nextId(), false);
                child.state = State.Indirect;
                child.invoke = indirectInvoke;
                getCallTree().add(child);
                children.add(child);
            }
        }
    }

    public void expand() {
        assert state == State.Cutoff : "Cannot expand a non-cutoff node. Node is " + state;
        assert getParent() != null;
        state = State.Expanded;
        getCallTree().expanded++;
        assert state == State.Expanded : Assertions.errorMessage(state);
        assert ir == null;
        GraphManager.Entry entry;
        GraphManager manager = getCallTree().getGraphManager();
        try {
            entry = manager.pe(directCallTarget);
        } catch (PermanentBailoutException e) {
            state = State.BailedOut;
            return;
        }
        verifyTrivial(entry);
        ir = copyGraphAndAddChildren(manager.rootContext(), entry);
        graphSize = entry.graphSize;
        irAfterPE = entry.graphAfterPEForDebugDump;
        addIndirectChildren(entry);
        getPolicy().afterExpand(this);
    }

    private void verifyTrivial(GraphManager.Entry entry) {
        if (trivial && !entry.trivial) {
            trivial = false;
            PerformanceInformationHandler.logPerformanceWarning(PerformanceWarningKind.TRIVIAL_FAIL, directCallTarget, Collections.emptyList(),
                            "Root node of target marked trivial but not trivial after PE", Collections.emptyMap());
        }
    }

    private StructuredGraph copyGraphAndAddChildren(TruffleTierContext context, GraphManager.Entry entry) {
        StructuredGraph graph = entry.graph;
        return (StructuredGraph) graph.copy(new Consumer<UnmodifiableEconomicMap<Node, Node>>() {
            @Override
            public void accept(UnmodifiableEconomicMap<Node, Node> duplicates) {
                final EconomicSet<Invoke> replacements = EconomicSet.create();
                for (Invoke original : entry.invokeToTruffleCallNode) {
                    if (!original.isAlive()) {
                        continue;
                    }
                    Invoke replacement = (Invoke) duplicates.get((Node) original);
                    if (replacement != null && replacement.isAlive()) {
                        replacements.add(replacement);
                    }
                }
                addChildren(context, CallNode.this, replacements);
            }
        }, graph.getDebug());
    }

    public void inline() {
        inline(InliningUtil.NoReturnAction);
    }

    public void inline(InlineeReturnAction returnAction) {
        assert state == State.Expanded : "Cannot inline node that is not expanded: " + state;
        assert ir != null : ir;
        assert getParent() != null;
        if (!invoke.isAlive()) {
            remove();
            return;
        }
        UnmodifiableEconomicMap<Node, Node> replacements = getCallTree().getGraphManager().doInline(invoke, ir, directCallTarget, returnAction);
        updateChildInvokes(replacements);
        state = State.Inlined;
        getCallTree().inlined++;
        getCallTree().frontierSize += children.size() - 1;
    }

    private void updateChildInvokes(UnmodifiableEconomicMap<Node, Node> replacements) {
        for (CallNode child : children) {
            if (child.state != State.Removed) {
                Node childInvoke = (Node) child.invoke;
                if (childInvoke == null || !childInvoke.isAlive() || !replacements.containsKey(childInvoke)) {
                    child.remove();
                    continue;
                }
                Invoke replacementInvoke = (Invoke) replacements.get(childInvoke);
                child.setInvokeOrRemove(replacementInvoke);
            }
        }
    }

    public boolean isForced() {
        return forced;
    }

    public CallNode getParent() {
        return (CallNode) predecessor();
    }

    public Invoke getInvoke() {
        return invoke;
    }

    public State getState() {
        return state;
    }

    public boolean isRoot() {
        return callNode == null && state == State.Inlined;
    }

    public String getName() {
        if (directCallTarget == null) {
            return "<indirect>";
        }
        return directCallTarget.toString();
    }

    public List<CallNode> getChildren() {
        return children;
    }

    public StructuredGraph getIR() {
        return ir;
    }

    public CallTree getCallTree() {
        return (CallTree) graph();
    }

    @Override
    public Map<Object, Object> getDebugProperties(Map<Object, Object> map) {
        Map<Object, Object> debugProperties = super.getDebugProperties(map);
        putProperties(debugProperties);
        if (ir != null) {
            debugProperties.put("ir node count", ir.getNodeCount());
        }
        return debugProperties;
    }

    HashMap<String, Object> getStringProperties() {
        HashMap<Object, Object> properties = new HashMap<>();
        putProperties(properties);
        HashMap<String, Object> stringProperties = new HashMap<>();
        for (Object key : properties.keySet()) {
            stringProperties.put(key.toString(), properties.get(key));
        }
        return stringProperties;
    }

    public double getRootRelativeFrequency() {
        return rootRelativeFrequency;
    }

    public boolean isTrivial() {
        return trivial;
    }

    public int getSize() {
        if (getCallTree().useSize) {
            return graphSize;
        }
        return ir.getNodeCount();
    }

    public int recalculateSize() {
        if (getCallTree().useSize) {
            graphSize = NodeCostUtil.computeGraphSize(ir);
            return graphSize;
        }
        return ir.getNodeCount();
    }

    public Object getPolicyData() {
        return policyData;
    }

    public int getTruffleCallees() {
        return truffleCallees;
    }

    @Override
    public String toString(Verbosity v) {
        return "CallNode{" +
                        "state=" + state +
                        ", children=" + children +
                        ", truffleCallNode=" + callNode +
                        ", truffleAST=" + directCallTarget +
                        '}';
    }

    @Override
    public int compareTo(CallNode o) {
        return Integer.compare(id, o.id);
    }

    public void finalizeGraph() {
        if (state == State.Inlined) {
            for (CallNode child : children) {
                child.finalizeGraph();
            }
        }
        if (state == State.Cutoff || state == State.Expanded || state == State.BailedOut) {
            if (invoke.isAlive()) {
                getCallTree().getGraphManager().finalizeGraph(invoke, directCallTarget);
            } else {
                state = State.Removed;
            }
        }
    }

    void collectTargetsToDequeue(TruffleCompilationTask provider) {
        if (state == State.Inlined) {
            if (directCallTarget != getCallTree().getRoot().directCallTarget && directCallTarget.getKnownCallSiteCount() == 1) {
                provider.addTargetToDequeue(directCallTarget);
            }
            for (CallNode child : children) {
                child.collectTargetsToDequeue(provider);
            }
        }
    }

    public void collectInlinedTargets(TruffleCompilationTask inliningPlan) {
        if (state == State.Inlined) {
            inliningPlan.addInlinedTarget(directCallTarget);
            for (CallNode child : children) {
                child.collectInlinedTargets(inliningPlan);
            }
        }
    }

    public enum State {
        Cutoff,
        Expanded,
        Inlined,
        Removed,
        BailedOut,
        Indirect
    }
}
