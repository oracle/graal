/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.phases.inlining;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.NodeSuccessorList;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCallNode;
import org.graalvm.compiler.truffle.compiler.PartialEvaluator;
import org.graalvm.compiler.truffle.compiler.nodes.InlineDecisionInjectNode;
import org.graalvm.compiler.truffle.compiler.nodes.InlineDecisionNode;

@NodeInfo(nameTemplate = "{p#truffleAST}", cycles = NodeCycles.CYCLES_IGNORED, size = NodeSize.SIZE_IGNORED)
public final class CallNode extends Node {

    private static final NodeClass<CallNode> TYPE = NodeClass.create(CallNode.class);
    private final TruffleCallNode truffleCaller;
    private final CompilableTruffleAST truffleAST;
    private final TruffleCallNode[] truffleCallees;
    private final double rootRelativeFrequency;
    private final int depth;
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
    // Effectively final, populated only as part of expanded
    private StructuredGraph ir;
    // Effectively final, populated only as part of expanded (unless root, root does not have
    // invoke)
    private Invoke invoke;

    // Needs to be protected because of the @NodeInfo annotation
    protected CallNode(TruffleCallNode truffleCaller, CompilableTruffleAST truffleAST, double rootRelativeFrequency, int depth) {
        super(TYPE);
        this.state = State.Cutoff;
        this.recursionDepth = -1;
        this.rootRelativeFrequency = rootRelativeFrequency;
        this.truffleCaller = truffleCaller;
        this.truffleAST = truffleAST;
        this.truffleCallees = truffleAST == null ? new TruffleCallNode[0] : truffleAST.getCallNodes();
        this.children = new NodeSuccessorList<>(this, 0);
        this.depth = depth;
    }

    /**
     * Returns a fully expanded and partially evaluated CallNode to be used as a root of a callTree.
     */
    static CallNode makeRoot(CallTree callTree, PartialEvaluator.Request request) {
        Objects.requireNonNull(callTree);
        Objects.requireNonNull(request);
        CallNode root = new CallNode(null, request.compilable, 1, 0);
        callTree.add(root);
        root.ir = request.graph;
        root.policyData = callTree.getPolicy().newCallNodeData(root);
        root.addChildren();
        EconomicMap<TruffleCallNode, Invoke> truffleCallNodeToInvoke = callTree.getGraphManager().peRoot();
        for (CallNode child : root.children) {
            Invoke invoke = truffleCallNodeToInvoke.get(child.getTruffleCaller());
            child.setInvokeOrRemove(invoke);
        }
        root.state = State.Inlined;
        callTree.getPolicy().afterExpand(root);
        return root;
    }

    private static double calculateFrequency(CompilableTruffleAST target, TruffleCallNode callNode) {
        return (double) Math.max(1, callNode.getCallCount()) / (double) Math.max(1, target.getCallCount());
    }

    private static void handleInlineDecisionNode(Invoke invoke) {
        NodeInputList<ValueNode> arguments = invoke.callTarget().arguments();
        ValueNode argument = arguments.get(1);
        if (!(argument instanceof InlineDecisionInjectNode)) {
            GraalError.shouldNotReachHere("Agnostic inlining expectations not met by graph");
        }
        InlineDecisionInjectNode injectNode = (InlineDecisionInjectNode) argument;
        ValueNode maybeDecision = injectNode.getDecision();
        if (!(maybeDecision instanceof InlineDecisionNode)) {
            GraalError.shouldNotReachHere("Agnostic inlining expectations not met by graph");
        }
        InlineDecisionNode inlineDecisionNode = (InlineDecisionNode) maybeDecision;
        inlineDecisionNode.inlined();
        injectNode.resolve();
    }

    public CompilableTruffleAST getTruffleAST() {
        return truffleAST;
    }

    private void putProperties(Map<Object, Object> properties) {
        if (state == State.Indirect) {
            return;
        }
        properties.put("Frequency", rootRelativeFrequency);
        properties.put("Recursion Depth", getRecursionDepth());
        properties.put("IR Nodes", ir == null ? 0 : ir.getNodeCount());
        properties.put("Truffle Callees", truffleCallees.length);
        properties.put("Explore/inline ratio", exploreInlineRatio());
        properties.put("Depth", depth);
        properties.put("Forced", isRoot() ? false : isForced());
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
        return computeRecursionDepth(getParent(), truffleAST);
    }

    private int computeRecursionDepth(CallNode node, CompilableTruffleAST target) {
        if (node == null) {
            return 0;
        }
        int parentDepth = computeRecursionDepth(node.getParent(), target);
        if (node.truffleAST.isSameOrSplit(target)) {
            return parentDepth + 1;
        } else {
            return parentDepth;
        }
    }

    private void addChildren() {
        // In the current implementation, this may be called only once.
        for (TruffleCallNode childCallNode : truffleCallees) {
            double relativeFrequency = calculateFrequency(truffleAST, childCallNode);
            double childFrequency = relativeFrequency * rootRelativeFrequency;
            CallNode callNode = new CallNode(childCallNode, childCallNode.getCurrentCallTarget(), childFrequency, depth + 1);
            getCallTree().add(callNode);
            children.add(callNode);
            callNode.policyData = getPolicy().newCallNodeData(callNode);
        }
        getPolicy().afterAddChildren(this);
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
                CallNode child = new CallNode(null, null, 0, depth + 1);
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
        addChildren();
        assert state == State.Expanded;
        assert ir == null;
        GraphManager.Entry entry = getCallTree().getGraphManager().pe(truffleAST);
        ir = copyGraphAndUpdateInvokes(entry);
        addIndirectChildren(entry);
        getPolicy().afterExpand(this);
    }

    private StructuredGraph copyGraphAndUpdateInvokes(GraphManager.Entry entry) {
        StructuredGraph graph = entry.graph;
        return (StructuredGraph) graph.copy(new Consumer<UnmodifiableEconomicMap<Node, Node>>() {
            @Override
            public void accept(UnmodifiableEconomicMap<Node, Node> duplicates) {
                for (CallNode child : children) {
                    TruffleCallNode childTruffleCallNode = child.getTruffleCaller();
                    Invoke original = entry.truffleCallNodeToInvoke.get(childTruffleCallNode);
                    if (original == null || !original.isAlive()) {
                        child.remove();
                    } else {
                        Invoke replacement = (Invoke) duplicates.get((Node) original);
                        child.setInvokeOrRemove(replacement);
                    }
                }
            }
        }, graph.getDebug());
    }

    public void inline() {
        assert state == State.Expanded : "Cannot inline node that is not expanded: " + state;
        assert ir != null && getParent() != null;
        if (!invoke.isAlive()) {
            remove();
            return;
        }
        handleInlineDecisionNode(invoke);
        UnmodifiableEconomicMap<Node, Node> replacements = getCallTree().getGraphManager().doInline(invoke, ir, truffleAST);
        updateChildInvokes(replacements);
        state = State.Inlined;
        getCallTree().inlined++;
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

    /**
     * A large number of call targets seem to have a single known callsite in most code. However,
     * some targets have many callsites, and it is usually important to compile them separately even
     * though they are inlined into one of their callsites.
     */
    void cancelCompilationIfSingleCallsite() {
        if (truffleAST != getCallTree().getRoot().truffleAST && truffleAST.getKnownCallSiteCount() == 1) {
            truffleAST.cancelInstalledTask();
        }
    }

    public boolean isForced() {
        return truffleCaller.isInliningForced();
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
        return truffleCaller == null && state == State.Inlined;
    }

    public String getName() {
        if (state == State.Indirect) {
            return "<indirect>";
        }
        return truffleAST.toString();
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

    private TruffleCallNode getTruffleCaller() {
        return truffleCaller;
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

    public Object getPolicyData() {
        return policyData;
    }

    public TruffleCallNode[] getTruffleCallees() {
        return truffleCallees;
    }

    @Override
    public String toString(Verbosity v) {
        return "CallNode{" +
                        "state=" + state +
                        ", children=" + children +
                        ", truffleCallNode=" + truffleCaller +
                        ", truffleAST=" + truffleAST +
                        '}';
    }

    public enum State {
        Cutoff,
        Expanded,
        Inlined,
        Removed,
        Indirect
    }
}
