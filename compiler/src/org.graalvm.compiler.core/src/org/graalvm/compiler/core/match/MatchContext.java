/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.match;

import static org.graalvm.compiler.debug.DebugOptions.LogVerbose;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import jdk.vm.ci.meta.Value;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.core.gen.NodeLIRBuilder;
import org.graalvm.compiler.core.match.MatchPattern.Result;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

/**
 * Container for state captured during a match.
 */
public class MatchContext {

    private final Node root;

    private final List<Node> nodes;

    private final MatchStatement rule;

    private EconomicMap<String, NamedNode> namedNodes;

    /**
     * A node consumed by a match. Keeps track of whether side effects can be ignored.
     */
    static final class ConsumedNode {
        final Node node;
        final boolean ignoresSideEffects;

        ConsumedNode(Node node, boolean ignoresSideEffects) {
            this.node = node;
            this.ignoresSideEffects = ignoresSideEffects;
        }
    }

    /**
     * The collection of nodes consumed by this match.
     */
    static final class ConsumedNodes implements Iterable<Node> {
        private ArrayList<ConsumedNode> nodes;


        private final class ConsumedNodesIterator implements Iterator<Node> {
            final Iterator<ConsumedNode> iterator;

            ConsumedNodesIterator() {
                iterator = nodes.iterator();
            }

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Node next() {
                return iterator.next().node;
            }
        }

        ConsumedNodes() {
            this.nodes = null;
        }

        public void add(Node node, boolean ignoresSideEffects) {
            if (nodes == null) {
                nodes = new ArrayList<>(2);
            }
            nodes.add(new ConsumedNode(node, ignoresSideEffects));
        }

        public boolean contains(Node node) {
            for (ConsumedNode c : nodes) {
                if (c.node == node) {
                    return true;
                }
            }
            return false;
        }

        public ConsumedNode find(Node node) {
            for (ConsumedNode c : nodes) {
                if (c.node == node) {
                    return c;
                }
            }
            return null;
        }

        @Override
        public String toString() {
            Node[] arr = new Node[nodes.size()];
            int i = 0;
            for (ConsumedNode c : nodes) {
                arr[i++] = c.node;
            }
            return Arrays.toString(arr);
        }

        @Override
        public Iterator<Node> iterator() {
            return new ConsumedNodesIterator();
        }
    }

    ConsumedNodes consumed = new ConsumedNodes();

    private int startIndex;

    private int endIndex;

    /**
     * Index in the block at which the match should be emitted.
     * Differs from endIndex for (ZeroExtend Read=access) for instance: match should be emitted where the Read is.
     */
    private int emitIndex;

    private final NodeLIRBuilder builder;

    private static class NamedNode {
        final Class<? extends Node> type;
        final Node value;

        NamedNode(Class<? extends Node> type, Node value) {
            this.type = type;
            this.value = value;
        }
    }

    public MatchContext(NodeLIRBuilder builder, MatchStatement rule, int index, Node node, List<Node> nodes) {
        this.builder = builder;
        this.rule = rule;
        this.root = node;
        this.nodes = nodes;
        assert index == nodes.indexOf(node);
        // The root should be the last index since all the inputs must be scheduled before it.
        emitIndex = startIndex = endIndex = index;
    }

    public Node getRoot() {
        return root;
    }

    public Result captureNamedValue(String name, Class<? extends Node> type, Node value) {
        if (namedNodes == null) {
            namedNodes = EconomicMap.create(Equivalence.DEFAULT);
        }
        NamedNode current = namedNodes.get(name);
        if (current == null) {
            current = new NamedNode(type, value);
            namedNodes.put(name, current);
            return Result.OK;
        } else {
            if (current.value != value || current.type != type) {
                return Result.namedValueMismatch(value, rule.getPattern());
            }
            return Result.OK;
        }
    }

    public Result validate() {
        // Ensure that there's no unsafe work in between these operations.
        Node sideEffect = null;
        boolean consumedSideEffect = false;
        for (int i = startIndex; i <= endIndex; i++) {
            Node node = nodes.get(i);
            if (node instanceof VirtualObjectNode || node instanceof FloatingNode) {
                // The order of evaluation of these nodes controlled by data dependence so they
                // don't interfere with this match.
                continue;
            } else {
                ConsumedNode cn = consumed.find(node);
                if (cn == null) {
                    sideEffect = node;
                } else if (!cn.ignoresSideEffects) {
                    emitIndex = i;
                    // There should be no side effects between 2 nodes that are affected by side effects.
                    if (consumedSideEffect && sideEffect != null) {
                        logFailedMatch("unexpected node %s", sideEffect);
                        return Result.notSafe(node, rule.getPattern());
                    }
                    consumedSideEffect = true;
                }
            }
        }
        assert emitIndex == endIndex || consumed.find(root).ignoresSideEffects;
        // We are going to emit the match at emitIndex. We need to make sure nodes of the match between emitIndex and
        // endIndex don't have inputs after position emitIndex that would make emitIndex an illegal position.
        for (int i = emitIndex + 1; i <= endIndex; i++) {
            Node node = nodes.get(i);
            ConsumedNode cn = consumed.find(node);
            if (cn != null) {
                assert cn.ignoresSideEffects;
                for (Node in : node.inputs()) {
                    if (!consumed.contains(in)) {
                        for (int j = emitIndex + 1; j < i; j++) {
                            if (nodes.get(j) == in) {
                                logFailedMatch("Earliest position in block is too late %s", in);
                                return Result.notSafe(node, rule.getPattern());
                            }
                        }
                    }
                }
            }
        }
        return Result.OK;
    }

    private void logFailedMatch(String s, Node node) {
        if (LogVerbose.getValue(root.getOptions())) {
            DebugContext debug = root.getDebug();
            debug.log(s, node);
            for (int j = startIndex; j <= endIndex; j++) {
                Node theNode = nodes.get(j);
                debug.log("%s(%s) %1s", consumed.contains(theNode) ? "*" : " ", theNode.getUsageCount(), theNode);
            }
        }
    }

    /**
     * Mark the interior nodes with INTERIOR_MATCH and set the Value of the root to be the result.
     * During final LIR generation it will be evaluated to produce the actual LIR value.
     *
     * @param result
     */
    public void setResult(ComplexMatchResult result) {
        ComplexMatchValue value = new ComplexMatchValue(result);
        Node emitNode = nodes.get(emitIndex);
        DebugContext debug = root.getDebug();
        if (debug.isLogEnabled()) {
            debug.log("matched %s %s", rule.getName(), rule.getPattern());
            debug.log("with nodes %s", rule.formatMatch(root));
        }
        for (Node node : consumed) {
            if (node == root || node == emitNode) {
                continue;
            }
            // All the interior nodes should be skipped during the normal doRoot calls in
            // NodeLIRBuilder so mark them as interior matches. The root of the match will get a
            // closure which will be evaluated to produce the final LIR.
            builder.setMatchResult(node, ComplexMatchValue.INTERIOR_MATCH);
        }
        builder.setMatchResult(emitNode, value);
        if (root != emitNode) {
            // Match is not emitted at the position of root in the block but the uses of root needs the result of the
            // match so add a ComplexMatchValue that will simply return the result of the actual match above.
            builder.setMatchResult(root, new ComplexMatchValue(gen -> gen.operand(emitNode)));
        }
    }

    /**
     * Mark a node as consumed by the match. Consumed nodes will never be evaluated.
     *
     * @return Result.OK if the node can be safely consumed.
     */
    public Result consume(Node node, boolean ignoresSideEffects, boolean atRoot) {
        if (atRoot) {
            consumed.add(node, ignoresSideEffects);
            return Result.OK;
        }
        assert MatchPattern.isSingleValueUser(node) : "should have already been checked";

        // Check NOT_IN_BLOCK first since that usually implies ALREADY_USED
        int index = nodes.indexOf(node);
        if (index == -1) {
            return Result.notInBlock(node, rule.getPattern());
        }

        if (builder.hasOperand(node)) {
            return Result.alreadyUsed(node, rule.getPattern());
        }

        startIndex = Math.min(startIndex, index);
        consumed.add(node, ignoresSideEffects);
        return Result.OK;
    }

    /**
     * Return the named node. It's an error if the
     *
     * @param name the name of a node in the match rule
     * @return the matched node
     * @throws GraalError is the named node doesn't exist.
     */
    public Node namedNode(String name) {
        if (namedNodes != null) {
            NamedNode value = namedNodes.get(name);
            if (value != null) {
                return value.value;
            }
        }
        throw new GraalError("missing node %s", name);
    }

    @Override
    public String toString() {
        return String.format("%s %s (%d, %d) consumed %s", rule, root, startIndex, endIndex, consumed);
    }
}
