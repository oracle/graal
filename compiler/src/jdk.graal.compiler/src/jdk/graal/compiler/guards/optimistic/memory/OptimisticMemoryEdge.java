/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.guards.optimistic.memory;

import static jdk.graal.compiler.nodeinfo.InputType.Guard;
import static jdk.graal.compiler.nodeinfo.InputType.Memory;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.guards.optimistic.OptimisticGuardNode;
import jdk.graal.compiler.guards.optimistic.OptimisticGuardedNode;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValueNodeInterface;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.memory.FloatingReadNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.MemoryPhiNode;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.MemoryEdgeProxy;
import jdk.graal.compiler.nodes.util.GraphUtil;

/**
 * This class represents a choice in the memory graph: This node can always safely be replaced by
 * the {@link #conservativeEdge}. It can also be replaced by the {@link #optimisticEdge}, but only
 * if the {@link #guard} holds.
 * <p>
 * Since we don't have write->read edges, a {@link FloatingReadNode} can never float below writes to
 * the same {@link LocationIdentity}. Because of that, unconditionally rewiring memory edges further
 * up can be counterproductive, since that forces the read to a different place in the graph,
 * instead of just extending the range where it can be scheduled.
 * <p>
 * The {@link OptimisticMemoryEdge} can be used to accurately record this information in the memory
 * graph. Consider the following memory graph (assuming all writes and reads are to the same
 * {@link LocationIdentity}:
 *
 * <pre>
 *  WriteNode
 *    |
 *  WriteNode
 *    |
 *  WriteNode
 *    |    \
 *    |   FloatingReadNode
 *    |
 *  WriteNode
 * </pre>
 *
 * The {@link FloatingReadNode} can only be scheduled between the third and the fourth
 * {@link WriteNode}, everything else would potentially be wrong because all accesses could alias.
 * <p>
 * If we could somehow show that the {@link FloatingReadNode} doesn't actually alias with the third
 * {@link WriteNode} (e.g. because we can statically show the index is different), we could rewire
 * this graph:
 *
 * <pre>
 *  WriteNode
 *    |
 *  WriteNode
 *    |     \
 *    |    FloatingReadNode
 *    |
 *  WriteNode
 *    |
 *  WriteNode
 * </pre>
 *
 * But this means the {@link FloatingReadNode} can now be only scheduled between the second and the
 * third {@link WriteNode}, but not between the third and fourth {@link WriteNode}. We lost the
 * information that it definitely doesn't alias with the third {@link WriteNode}.
 * <p>
 * With {@link OptimisticMemoryEdge}, this information can be encoded like this:
 *
 * <pre>
 *  WriteNode
 *    |     \
 *    |      \
 *    |       \      OptimisticGuard
 *  WriteNode  |         |
 *    |    |   |         |
 *    |   OptimisticMemoryEdge
 *    |        |
 *    |        |
 *  WriteNode  |
 *    |    |   |
 *    |   OptimisticMemoryEdge
 *    |        |
 *    |     ReadNode
 *    |
 *  WriteNode
 * </pre>
 *
 * The {@link OptimisticMemoryEdge} nodes have three inputs (left to right in the above graph):
 * <ul>
 * <li>{@link #conservativeEdge}
 * <li>{@link #optimisticEdge}
 * <li>(optional) {@link #guard}
 * </ul>
 *
 *
 * This graph now explicitly contains the information that the {@link FloatingReadNode} and the
 * third {@link WriteNode} definitely don't alias, and that the {@link FloatingReadNode} and the
 * second {@link WriteNode} don't alias if a {@link #guard} holds.
 * <p>
 * This means:
 * <ul>
 * <li>The {@link FloatingReadNode} can be scheduled between the third and fourth {@link WriteNode}
 * ({@link #conservativeEdge} of the second {@link OptimisticMemoryEdge}). That's what's going to
 * happen if the graph stays in that shape until the {@link OptimisticGuardsPhase}, which will
 * remove all {@link OptimisticMemoryEdge} from the graph.
 * <li>The {@link FloatingReadNode} can also be scheduled between the second and third
 * {@link WriteNode} by following the {@link #optimisticEdge} of the second
 * {@link OptimisticMemoryEdge}. Since it has no {@link #guard}, this is always valid. We still
 * don't do it automatically so we don't lose the possibility to schedule the
 * {@link FloatingReadNode} below the third {@link WriteNode}.
 * <li>The {@link FloatingReadNode} can be scheduled between the first and second {@link WriteNode},
 * but only if you attach the {@link OptimisticGuardNode} to it.
 * </ul>
 *
 * Compiler optimizations that rely on a {@link FloatingReadNode} to have some particular
 * lastLocationAccess can choose to replace this node with the {@link #conservativeEdge}, or they
 * can replace this node with the {@link #optimisticEdge} and attach the {@link #guard} to all
 * usages.
 */
@NodeInfo(allowedUsageTypes = Memory, cycles = CYCLES_0, size = SIZE_0)
public class OptimisticMemoryEdge extends FloatingNode implements OptimisticGuardedNode, MemoryEdgeProxy, SingleMemoryKill, Canonicalizable {

    public static final NodeClass<OptimisticMemoryEdge> TYPE = NodeClass.create(OptimisticMemoryEdge.class);
    @OptionalInput(Guard) GuardingNode guard;

    @Input(Memory) MemoryKill conservativeEdge;
    @Input(Memory) MemoryKill optimisticEdge;

    protected final LocationIdentity identity;

    public OptimisticMemoryEdge(GuardingNode guard, MemoryKill conservativeEdge, MemoryKill optimisticEdge, LocationIdentity identity) {
        super(TYPE, StampFactory.forVoid());
        this.guard = guard;
        this.conservativeEdge = conservativeEdge;
        this.optimisticEdge = optimisticEdge;
        this.identity = identity;
    }

    @Override
    public GuardingNode getGuard() {
        return guard;
    }

    @Override
    public void setGuard(GuardingNode guard) {
        updateUsagesInterface(this.guard, guard);
        this.guard = guard;
    }

    public MemoryKill getConservativeEdge() {
        return conservativeEdge;
    }

    public MemoryKill getOptimisticEdge() {
        return optimisticEdge;
    }

    @Override
    public void revert() {
        replaceAtUsages(ValueNodeInterface.asNode(conservativeEdge));
        GraphUtil.killWithUnusedFloatingInputs(this);
    }

    @Override
    public ValueNode getOriginalNode() {
        return ValueNodeInterface.asNode(conservativeEdge);
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return identity;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return getLocationIdentity();
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable()) {
            /**
             * The conservative edge usually points to a memory access which can itself have an
             * optimistic memory edge as its lastLocationAccess:
             *
             * <pre>
             *    | conservative A    / optimistic A
             *    |                  /
             *   OptimisticMemoryEdge A
             *    |
             *   Write A
             *    |
             *    | conservative B    / optimistic B
             *    |                  /
             *   OptimisticMemoryEdge B
             *    |
             *   Write B
             *    |
             *    | conservative C    / optimistic C
             *    |                  /
             *   OptimisticMemoryEdge C
             *    |
             *   Access C
             * </pre>
             *
             * (Guard conditions not shown.) The semantics of these edges is: {@code Access C} must
             * be scheduled after {@code Write B} or the node on the {@code optimistic C} edge;
             * {@code Write B} itself must be scheduled after {@code Write A} or
             * {@code optimistic B}; and {@code Write A} must be scheduled after
             * {@code conservative A} or {@code optimistic A}.
             * </p>
             *
             * Assume {@code Write A} and {@code Write B} are optimized out. Their
             * {@code OptimisticMemoryEdge}s remain:
             *
             * <pre>
             *    | conservative A    / optimistic A
             *    |                  /
             *   OptimisticMemoryEdge A
             *    |
             *    | conservative B    / optimistic B
             *    |                  /
             *   OptimisticMemoryEdge B
             *    |
             *    | conservative C    / optimistic C
             *    |                  /
             *   OptimisticMemoryEdge C
             *    |
             *   Access C
             * </pre>
             *
             * Assume further that all the optimistic edges are the same, i.e., the edge
             * {@code optimistic C} points to the same node as the edges {@code optimistic B} and
             * {@code optimistic A}. In this case {@code OptimisticMemoryEdge A} and
             * {@code OptimisticMemoryEdge B} add no information: {@code Access C} must now be
             * scheduled after {@code conservative A} or {@code optimistic C}. We can drop the
             * unnecessary edges and rewrite only {@code C}'s conservative edge:
             *
             * <pre>
             *    | conservative A    / optimistic C
             *    |                  /
             *   OptimisticMemoryEdge C
             *    |
             *   Access C
             * </pre>
             */
            MemoryKill improvedConservativeEdge = conservativeEdge;
            while (improvedConservativeEdge.asNode().hasExactlyOneUsage() && improvedConservativeEdge instanceof OptimisticMemoryEdge) {
                OptimisticMemoryEdge other = (OptimisticMemoryEdge) improvedConservativeEdge;
                if (other.optimisticEdge == optimisticEdge) {
                    improvedConservativeEdge = other.conservativeEdge;
                } else {
                    break;
                }
            }

            if (hasExactlyOneUsage()) {
                Node singleUsage = singleUsage();
                if (singleUsage == optimisticEdge) {
                    assert singleUsage instanceof MemoryPhiNode && ((MemoryPhiNode) singleUsage).isLoopPhi() : "only phis can be cyclic";
                    /*
                     * This was an optimistic memory edge for a memory access in a loop. That access
                     * has been eliminated, so we don't need its memory edge anymore.
                     */
                    return improvedConservativeEdge.asNode();
                }
            }

            if (improvedConservativeEdge != conservativeEdge) {
                return new OptimisticMemoryEdge(guard, improvedConservativeEdge, optimisticEdge, identity);
            }
        }

        return this;
    }
}
