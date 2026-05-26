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
package jdk.graal.compiler.phases.common;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.NodeStack;
import jdk.graal.compiler.nodes.ValueNode;

/**
 * Reusable iterator state for the bounded safe-stamp input search.
 *
 * Each query starts with {@link #start(ValueNode)}, which clears the seen bitmap, result stack, and
 * level queues. Iteration then exposes one value producer at a time while the implementation owns the
 * current-level and next-level queue swap.
 */
public final class SafeStampInputSearch {

    /**
     * Maximum value-input depth inspected while proving that a stamp producer is independent of
     * hidden control flow. Nodes at this depth must already be terminal or locally stamp-derived.
     */
    private static final int SAFE_STAMP_INPUT_SEARCH_DEPTH = 3;

    /**
     * Maximum number of distinct value nodes queued by one search before the stamp is considered too
     * expensive to prove safe.
     */
    private static final int SAFE_STAMP_INPUT_SEARCH_MAX_NODES = 32;

    /**
     * Nodes already queued by the current search, used to keep producer traversal finite and
     * duplicate-free.
     */
    private final NodeBitMap seen;

    /**
     * Producers returned by {@link #next()}, retained for the second validation pass over explicit
     * guard and anchor inputs.
     */
    private final NodeStack stampProducers;

    /**
     * Producers still to inspect at the current value-input depth.
     */
    private NodeStack currentLevel;

    /**
     * Producers queued for the next value-input depth.
     */
    private NodeStack nextLevel;

    /**
     * Number of distinct producers queued by this search.
     */
    private int queuedNodes;

    /**
     * Current value-input depth being inspected.
     */
    private int depth;

    public SafeStampInputSearch(Graph graph) {
        this.seen = graph.createNodeBitMap();
        this.stampProducers = new NodeStack();
        this.currentLevel = new NodeStack();
        this.nextLevel = new NodeStack();
    }

    /**
     * Starts a new search from {@code root}.
     */
    public void start(ValueNode root) {
        clear();
        GraalError.guarantee(push(currentLevel, root), "root node must fit into an empty safe-stamp input search");
    }

    /**
     * Returns true when another value producer is available at the current or next queued depth.
     */
    public boolean hasNext() {
        if (currentLevel.isEmpty() && !nextLevel.isEmpty()) {
            NodeStack finishedLevel = currentLevel;
            currentLevel = nextLevel;
            nextLevel = finishedLevel;
            nextLevel.clear();
            depth++;
        }
        return !currentLevel.isEmpty();
    }

    /**
     * Returns the next value producer and records it for the later control-dependency validation pass.
     */
    public ValueNode next() {
        GraalError.guarantee(hasNext(), "safe-stamp input search has no next value producer");
        ValueNode stampProducer = (ValueNode) currentLevel.pop();
        stampProducers.push(stampProducer);
        return stampProducer;
    }

    /**
     * Adds an input of the current producer to the next depth level.
     *
     * @return false if adding the node would exceed the bounded search budget
     */
    public boolean addInput(ValueNode node) {
        return push(nextLevel, node);
    }

    /**
     * Returns true when the search may inspect the current producer but must not enqueue deeper
     * inputs.
     */
    public boolean atMaxDepth() {
        return depth >= SAFE_STAMP_INPUT_SEARCH_DEPTH;
    }

    NodeStack stampProducers() {
        return stampProducers;
    }

    private void clear() {
        seen.grow();
        seen.clearAll();
        stampProducers.clear();
        currentLevel.clear();
        nextLevel.clear();
        queuedNodes = 0;
        depth = 0;
    }

    private boolean push(NodeStack stack, ValueNode node) {
        if (seen.isMarked(node)) {
            return true;
        } else if (queuedNodes >= SAFE_STAMP_INPUT_SEARCH_MAX_NODES) {
            return false;
        }
        seen.mark(node);
        queuedNodes++;
        stack.push(node);
        return true;
    }
}
