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
import org.graalvm.compiler.truffle.compiler.nodes.InlineDecisionInjectNode;
import org.graalvm.compiler.truffle.compiler.nodes.InlineDecisionNode;
import org.graalvm.options.OptionValues;

@NodeInfo(nameTemplate = "{p#truffleAST}", cycles = NodeCycles.CYCLES_IGNORED, size = NodeSize.SIZE_IGNORED)
public final class CallNode extends Node {

    private static final NodeClass<CallNode> TYPE = NodeClass.create(CallNode.class);
    private final TruffleCallNode truffleCaller;
    private final CompilableTruffleAST truffleAST;
    private final TruffleCallNode[] truffleCallees;
    private final double rootRelativeFrequency;
    private final OptionValues options;
    private Object data;
    private State state;
    @Successor private NodeSuccessorList<CallNode> children;
    private int recursionDepth;
    private int depth;
    /*
     * The ir field and the childInvokes fields are initially null and empty collection, and
     * populated once the node is partially evaluated;
     */
    private StructuredGraph ir;
    private EconomicMap<CallNode, Invoke> childInvokes;

    // Needs to be protected because of the @NodeInfo annotation
    protected CallNode(OptionValues options, TruffleCallNode truffleCallNode, CompilableTruffleAST truffleAST, StructuredGraph ir, double rootRelativeFrequency, int depth) {
        super(TYPE);
        this.options = options;
        this.state = State.Cutoff;
        this.recursionDepth = -1;
        this.rootRelativeFrequency = rootRelativeFrequency;
        this.truffleCaller = truffleCallNode;
        this.truffleAST = truffleAST;
        this.truffleCallees = truffleAST.getCallNodes();
        this.ir = ir;
        this.childInvokes = EconomicMap.create();
        this.children = new NodeSuccessorList<>(this, 0);
        this.depth = depth;
    }

    /**
     * Returns a fully expanded and partially evaluated CallNode to be used as a root of a callTree.
     */
    static CallNode makeRoot(OptionValues options, CallTree callTree, CompilableTruffleAST truffleAST, StructuredGraph ir) {
        Objects.requireNonNull(callTree);
        Objects.requireNonNull(truffleAST);
        Objects.requireNonNull(ir);
        final CallNode root = new CallNode(options, null, truffleAST, ir, 1, 0);
        callTree.add(root);
        root.data = callTree.getPolicy().newCallNodeData(root);
        assert root.state == State.Cutoff : "Cannot expand a non-cutoff node. State is " + root.state;
        root.addChildren();
        root.partiallyEvaluateRoot();
        callTree.getPolicy().afterExpand(root);
        return root;
    }

    private static double calculateFrequency(CompilableTruffleAST target, TruffleCallNode callNode) {
        return (double) Math.max(1, callNode.getCallCount()) / (double) Math.max(1, target.getCallCount());
    }

    public CompilableTruffleAST getTruffleAST() {
        return truffleAST;
    }

    void putProperties(Map<Object, Object> properties) {
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
        final CallTree callTree = getCallTree();
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
            final double relativeFrequency = calculateFrequency(truffleAST, childCallNode);
            final double childFrequency = relativeFrequency * this.rootRelativeFrequency;
            CallNode callNode = new CallNode(options, childCallNode, childCallNode.getCurrentCallTarget(), null, childFrequency, this.depth + 1);
            getCallTree().add(callNode);
            this.children.add(callNode);
            callNode.data = getPolicy().newCallNodeData(callNode);
        }
        getPolicy().afterAddChildren(this);
    }

    public int getDepth() {
        return depth;
    }

    public InliningPolicy getPolicy() {
        return getCallTree().getPolicy();
    }

    private void partiallyEvaluateRoot() {
        assert getParent() == null;
        final EconomicMap<TruffleCallNode, Invoke> truffleCallNodeToInvoke = getCallTree().getGraphManager().peRoot(options, truffleAST);
        state = State.Inlined;
        for (CallNode child : children) {
            final Invoke invoke = truffleCallNodeToInvoke.get(child.getTruffleCaller());
            putChildInvokeOrRemoveChild(child, invoke);
        }
    }

    private void putChildInvokeOrRemoveChild(CallNode child, Invoke invoke) {
        if (invoke == null || !invoke.isAlive()) {
            child.state = State.Removed;
            getPolicy().removedNode(this, child);
        } else {
            childInvokes.put(child, invoke);
        }
    }

    private void updateChildrenList(EconomicMap<TruffleCallNode, Invoke> truffleCallNodeToInvoke) {
        for (CallNode child : children) {
            final Invoke childInvoke = truffleCallNodeToInvoke.get(child.getTruffleCaller());
            if (childInvoke == null || !childInvoke.isAlive()) {
                child.state = State.Removed;
                getPolicy().removedNode(this, child);
            }
        }
    }

    public void expand() {
        assert state == State.Cutoff : "Cannot expand a non-cutoff node. Not is " + state;
        assert getParent() != null;
        this.state = State.Expanded;
        getCallTree().expanded++;
        this.addChildren();
        final EconomicMap<TruffleCallNode, Invoke> truffleCallNodeInvoke = partiallyEvaluate();
        getPolicy().afterPartialEvaluation(this);
        updateChildrenList(truffleCallNodeInvoke);
        getPolicy().afterExpand(this);
    }

    private EconomicMap<TruffleCallNode, Invoke> partiallyEvaluate() {
        assert state == State.Expanded;
        assert ir == null;
        GraphManager.Entry entry = getCallTree().getGraphManager().get(options, truffleAST);
        ir = copyGraphAndUpdateInvokes(entry);
        return entry.truffleCallNodeToInvoke;
    }

    private StructuredGraph copyGraphAndUpdateInvokes(GraphManager.Entry entry) {
        final StructuredGraph graph = entry.graph;
        return (StructuredGraph) graph.copy(new Consumer<UnmodifiableEconomicMap<Node, Node>>() {
            @Override
            public void accept(UnmodifiableEconomicMap<Node, Node> duplicates) {
                for (CallNode child : children) {
                    final TruffleCallNode childTruffleCallNode = child.getTruffleCaller();
                    final Invoke original = entry.truffleCallNodeToInvoke.get(childTruffleCallNode);
                    if (original == null || !original.isAlive()) {
                        child.state = State.Removed;
                        getPolicy().removedNode(CallNode.this, child);
                    } else {
                        final Invoke replacement = (Invoke) duplicates.get((Node) original);
                        putChildInvokeOrRemoveChild(child, replacement);
                    }
                }
            }
        }, graph.getDebug());
    }

    public void inline() {
        assert state == State.Expanded : "Cannot inline node that is not expanded: " + state;
        assert ir != null && getParent() != null;
        final Invoke invoke = getInvoke();
        if (!invoke.isAlive()) {
            state = State.Removed;
            return;
        }
        handleIsAttachedInlinedNode(invoke);
        final UnmodifiableEconomicMap<Node, Node> replacements = getCallTree().getGraphManager().doInline(invoke, ir, truffleAST);
        for (CallNode child : childInvokes.getKeys()) {
            if (child.state != State.Removed) {
                final Node childInvoke = (Node) childInvokes.get(child);
                if (!childInvoke.isAlive()) {
                    child.state = State.Removed;
                    getPolicy().removedNode(this, child);
                    continue;
                }
                final Invoke value = (Invoke) replacements.get(childInvoke);
                putChildInvokeOrRemoveChild(child, value);
            }
        }
        state = State.Inlined;
        getCallTree().inlined++;
    }

    private static void handleIsAttachedInlinedNode(Invoke invoke) {
        final NodeInputList<ValueNode> arguments = invoke.callTarget().arguments();
        final ValueNode argument = arguments.get(1);
        if (!(argument instanceof InlineDecisionInjectNode)) {
            GraalError.shouldNotReachHere("Agnostic inlining expectations not met by graph");
        }
        final InlineDecisionInjectNode attachNode = (InlineDecisionInjectNode) argument;
        final ValueNode maybeDecision = attachNode.getDecision();
        if (!(maybeDecision instanceof InlineDecisionNode)) {
            GraalError.shouldNotReachHere("Agnostic inlining expectations not met by graph");
        }
        final InlineDecisionNode inlineDecisionNode = (InlineDecisionNode) maybeDecision;
        inlineDecisionNode.inlined();
        attachNode.resolve();
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

    private Invoke getChildInvoke(CallNode child) {
        return this.childInvokes.get(child);
    }

    public CallNode getParent() {
        return (CallNode) predecessor();
    }

    public Invoke getInvoke() {
        CallNode parent = getParent();
        return parent != null ? parent.getChildInvoke(this) : null;
    }

    public State getState() {
        return state;
    }

    public boolean isRoot() {
        return truffleCaller == null;
    }

    public String getName() {
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

    TruffleCallNode getTruffleCaller() {
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
        final HashMap<Object, Object> properties = new HashMap<>();
        putProperties(properties);
        final HashMap<String, Object> stringProperties = new HashMap<>();
        for (Object key : properties.keySet()) {
            stringProperties.put(key.toString(), properties.get(key));
        }
        return stringProperties;
    }

    public double getRootRelativeFrequency() {
        return rootRelativeFrequency;
    }

    public Object getData() {
        return data;
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
