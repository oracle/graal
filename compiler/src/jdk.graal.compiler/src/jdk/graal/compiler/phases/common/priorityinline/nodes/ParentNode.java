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
import java.util.PriorityQueue;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicSet;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.graph.NodeSuccessorList;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.common.priorityinline.Expander;
import jdk.graal.compiler.phases.common.priorityinline.data.BenefitKind;

@NodeInfo
public abstract class ParentNode extends CallTreeNode {
    public static final NodeClass<ParentNode> TYPE = NodeClass.create(ParentNode.class);

    @SuppressWarnings("this-escape")//
    @Successor private NodeSuccessorList<CallTreeNode> children = new NodeSuccessorList<>(this, 0);
    private PriorityQueue<CallTreeNode> expansionQueue = new PriorityQueue<>();

    public ParentNode(NodeSourcePosition compilationRootPosition, NodeClass<? extends ParentNode> c, Invoke invoke, double frequency) {
        super(c, compilationRootPosition, invoke, frequency);
    }

    public void addChild(CallTreeNode child) {
        assert child != null;
        assert !(this instanceof InlineCacheNode && child instanceof IndirectNode) : "Cannot add " + child + " to " + this + ".";
        children.add(child);
    }

    @Override
    public NodeSuccessorList<CallTreeNode> children() {
        return children;
    }

    public void addToExpansionQueue(CallTreeNode node) {
        assert node instanceof CutoffNode || node.hasActiveCutoffs() : "Must be cutoff or have cutoffs " + node;
        expansionQueue.add(node);
    }

    public void clearExpansionQueue() {
        expansionQueue.clear();
    }

    public CallTreeNode pollExpansionQueue() {
        return expansionQueue.poll();
    }

    public boolean isExpansionQueueEmpty() {
        return expansionQueue.isEmpty();
    }

    public double peekExpansionQueueHighestPriority() {
        if (expansionQueue.isEmpty()) {
            return Expander.NODE_LOWEST_PRIORITY;
        } else {
            return expansionQueue.peek().getPriority();
        }
    }

    public abstract void createImmediateChildren(CallTreeNode caller);

    public abstract void inline(CoreProviders providers, Consumer<CallTreeNode> considerChild, Consumer<CallTreeNode> removeChild,
                    Consumer<EconomicSet<Node>> newCanonicalizableNodes);

    public EnumSet<BenefitKind> getBenefits() {
        return EnumSet.noneOf(BenefitKind.class);
    }

    @Override
    public final void initializeCounts() {
        // These counters are temporary and will be reset when traversing the children.
        // After this method is called, the child counts must be included into the current node.
        setCutoffCount(0);
        setActiveCutoffCount(0);
        setDeadendCount(0);
    }

    public final void excludeChildInCounts(CallTreeNode child) {
        assert children.contains(child) : child + " must be in " + children;
        setCutoffCount(cutoffCount() - child.cutoffCount());
        setActiveCutoffCount(activeCutoffCount() - child.activeCutoffCount());
        setDeadendCount(deadendCount() - child.deadendCount());
    }

    public final void includeChildInCounts(CallTreeNode child) {
        assert children.contains(child) : child + " must be in " + children;
        setCutoffCount(cutoffCount() + child.cutoffCount());
        setActiveCutoffCount(activeCutoffCount() + child.activeCutoffCount());
        setDeadendCount(deadendCount() + child.deadendCount());
    }
}
