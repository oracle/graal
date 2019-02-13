/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge.graal;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.AbstractCompareAndSwapNode;
import org.graalvm.compiler.nodes.java.LoweredAtomicReadAndWriteNode;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.nodes.memory.HeapAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.tiers.MidTierContext;

/**
 * Walk the graph and find WriteNodes with barriers and turn them into WriteNodes without barriers
 * and separate barrier nodes.
 */
public class InsertWriteBarrierPhase extends BasePhase<MidTierContext> {

    public static InsertWriteBarrierPhase factory() {
        return new InsertWriteBarrierPhase();
    }

    @Override
    public boolean checkContract() {
        // the size / cost after is highly dynamic and dependent on the graph, thus we do not verify
        // costs for this phase
        return false;
    }

    @Override
    protected void run(StructuredGraph graph, MidTierContext context) {
        for (Node n : graph.getNodes()) {
            if (n instanceof WriteNode) {
                rewriteWithBarriers(graph, (WriteNode) n);
            }
            if (n instanceof AbstractCompareAndSwapNode) {
                rewriteWithBarriers(graph, (AbstractCompareAndSwapNode) n);
            }
            if (n instanceof LoweredAtomicReadAndWriteNode) {
                rewriteWithBarriers(graph, (LoweredAtomicReadAndWriteNode) n);
            }
        }
    }

    /**
     * WriteNode has
     * <ul>
     * <li>The object to which the write is done, in node.object(),</li>
     * <li>the value to be written, in node.value(),</li>
     * <li>the location within the object for the write, in node.location(),</li>
     * <li>whether a precise or imprecise barrier is needed, in node.getBarrierType(),</li> and
     * <li>whether the write is an initialization, in node.isInitialization()</li>.
     * </ul>
     */
    protected void rewriteWithBarriers(StructuredGraph graph, WriteNode node) {
        if (node.getBarrierType() == BarrierType.NONE) {
            // No barrier requested.
            return;
        }
        final ValueNode value = node.value();
        if (!value.getStackKind().isObject()) {
            // Storing something other than an Object does not require a barrier.
            return;
        }
        if (StampTool.isPointerAlwaysNull(value)) {
            // Storing a null does not require a barrier.
            return;
        }
        // TODO: What about initializing writes, which I can check in node.isInitialization()?
        addPostWriteBarrier(graph, node, node.getAddress());
    }

    /**
     * AbstractCompareAndSwapNode has
     * <ul>
     * <li>The object to which the write is done, in node.object(),</li>
     * <li>the value to be written, in node.getNewValue(),</li>
     * <li>the location within the object for the write, in node.location(),</li>
     * <li>whether a precise or imprecise barrier is needed, in node.getBarrierType(),</li>.
     * </ul>
     */
    protected void rewriteWithBarriers(StructuredGraph graph, AbstractCompareAndSwapNode node) {
        if (node.getBarrierType() == BarrierType.NONE) {
            // No barrier requested.
            return;
        }
        final ValueNode value = node.getNewValue();
        if (!value.getStackKind().isObject()) {
            // Storing something other than an Object does not require a barrier.
            return;
        }
        if (StampTool.isPointerAlwaysNull(value)) {
            // Storing a null does not require a barrier.
            return;
        }
        // TODO: What about initializing writes, which I can check in node.isInitialization()?
        addPostWriteBarrier(graph, node, node.getAddress());
    }

    /**
     * LoweredAtomicReadAndWriteNode has
     * <ul>
     * <li>The object to which the write is done, in node.object(),</li>
     * <li>the value to be written, in node.getNewValue(),</li>
     * <li>the location within the object for the write, in node.location(),</li>
     * <li>whether a precise or imprecise barrier is needed, in node.getBarrierType(),</li>.
     * </ul>
     */
    protected void rewriteWithBarriers(StructuredGraph graph, LoweredAtomicReadAndWriteNode node) {
        if (node.getBarrierType() == BarrierType.NONE) {
            // No barrier requested.
            return;
        }
        final ValueNode value = node.getNewValue();
        if (!value.getStackKind().isObject()) {
            // Storing something other than an Object does not require a barrier.
            return;
        }
        if (StampTool.isPointerAlwaysNull(value)) {
            // Storing a null does not require a barrier.
            return;
        }
        // TODO: What about initializing writes, which I can check in node.isInitialization()?
        addPostWriteBarrier(graph, node, node.getAddress());
    }

    protected void addPostWriteBarrier(StructuredGraph graph, FixedAccessNode node, AddressNode address) {
        // TODO: Decide if I want a precise or imprecise barrier.
        final PostWriteBarrierNode barrierNode = new PostWriteBarrierNode(address);
        graph.addAfterFixed(node, graph.add(barrierNode));
    }

    /** Constructor for sub-classes. */
    protected InsertWriteBarrierPhase() {
        super();
    }
}
