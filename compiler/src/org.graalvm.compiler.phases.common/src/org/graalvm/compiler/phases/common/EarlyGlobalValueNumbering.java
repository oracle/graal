/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.common;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.loop.LoopEx;
import org.graalvm.compiler.nodes.loop.LoopsData;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.MemoryKill;
import org.graalvm.compiler.nodes.memory.MultiMemoryKill;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.word.LocationIdentity;

public class EarlyGlobalValueNumbering extends BasePhase<HighTierContext> {

    @Override
    protected void run(StructuredGraph graph, HighTierContext context) {

        /*
         *
         * process the dominator tree with deferring loop exits
         *
         * if a loop is visited, use the memory state visitor to kill
         *
         */
        ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, true, true, true);
        LoopsData ld = context.getLoopsDataProvider().getLoopsData(graph);
        cfg.visitDominatorTreeDeferLoopExits(new GVNVisitor(cfg, ld));
    }

    /*
     * Either we process a loop twice until a fix point or we compute the killed locations inside a
     * loop and process them further, we are mostly interested in memory reads here
     */
    static class GVNVisitor implements ControlFlowGraph.RecursiveVisitor<ValueMap> {
        final StructuredGraph graph;
        final ControlFlowGraph cfg;
        final LoopsData ld;
        ValueMap valueMap;
        final EconomicMap<LoopEx, EconomicSet<LocationIdentity>> killedLoopLocations;

        GVNVisitor(ControlFlowGraph cfg, LoopsData ld) {
            this.cfg = cfg;
            this.ld = ld;
            this.graph = cfg.graph;
            this.killedLoopLocations = EconomicMap.create();
            for (LoopEx loop : ld.loops()) {
                killedLoopLocations.put(loop, killedLoopLocations(loop));
            }
        }

        @Override
        public ValueMap enter(Block b) {
            ValueMap oldMap = null;
            if (valueMap == null) {
                valueMap = new ValueMap();
            } else {
                oldMap = valueMap;
                valueMap = oldMap.copy();
            }
            FixedWithNextNode cur = b.getBeginNode();
            while (cur != b.getEndNode()) {
                procesNode(cur, b);
                cur = (FixedWithNextNode) cur.next();
            }
            return oldMap;
        }

        @SuppressWarnings("unused")
        private void procesNode(FixedWithNextNode cur, Block b) {
            Loop<Block> hirLoop = b.getLoop();
            EconomicSet<LocationIdentity> thisLoopKilledLocations = hirLoop == null ? null : killedLoopLocations.get(ld.loop(hirLoop));

            if (MemoryKill.isMemoryKill(cur)) {
                valueMap.killMemory(cur);
            }

            if (cur instanceof MemoryAccess) {
                MemoryAccess access = (MemoryAccess) cur;
            }

        }

        @Override
        public void exit(Block b, ValueMap oldMap) {
            valueMap = oldMap;
        }

        @SuppressWarnings("unused")
        public boolean nodeIsLoopInvariant(Block loopBegin, Block current) {
            return false;
        }

        public boolean globalValueNumber(Node n) {
            return false;
        }

        public boolean performLICM() {
            return false;
        }

    }

    private static EconomicSet<LocationIdentity> killedLoopLocations(LoopEx loop) {
        EconomicSet<LocationIdentity> killedLocations = EconomicSet.create();
        for (Node n : loop.inside().nodes()) {
            if (MemoryKill.isMemoryKill(n)) {
                if (MemoryKill.isSingleMemoryKill(n)) {
                    SingleMemoryKill singleKill = MemoryKill.asSingleMemoryKill(n);
                    if (killedLocation(singleKill.getKilledLocationIdentity(), killedLocations)) {
                        return killedLocations;
                    }
                } else if (MemoryKill.isMultiMemoryKill(n)) {
                    MultiMemoryKill multiKill = MemoryKill.asMultiMemoryKill(n);
                    for (LocationIdentity ld : multiKill.getKilledLocationIdentities()) {
                        if (killedLocation(ld, killedLocations)) {
                            return killedLocations;
                        }
                    }
                }
            }
        }
        return killedLocations;
    }

    private static boolean killedLocation(LocationIdentity ld, EconomicSet<LocationIdentity> killedLocations) {
        if (ld.equals(LocationIdentity.ANY_LOCATION)) {
            killedLocations.clear();
            killedLocations.add(LocationIdentity.ANY_LOCATION);
            return true;
        }
        killedLocations.add(ld);
        return false;
    }

    /**
     * A datastructure used to implement global value numbering on fixed nodes in the Graal IR. It
     * storse all the values seen so far and exposes utility functionality to deal with memory.
     *
     * Loop handling: We can only perform global value numbering on the fixed node graph if we know
     * the memory effect of a loop. For a loop we can statically not tell if a kill inside may be
     * performed or not, thus, we can only perform GVN if we know that a loop does not kill a
     * certain memory location.
     *
     * Loop invariant code motion: This phase will not perform speculative loop invariant code
     * motion (this is done in mid tier on the real memory graph. Thus, we can only perform GVN
     */
    static final class ValueMap {

        ValueMap copy() {
            ValueMap other = new ValueMap();
            other.entries.putAll(entries);
            return other;
        }

        static class ValueMapEquivalence extends Equivalence {

            @Override
            public boolean equals(Object a, Object b) {
                if (a instanceof Node && b instanceof Node) {
                    Node n1 = (Node) a;
                    Node n2 = (Node) b;
                    if (n1.getNodeClass().equals(n2.getNodeClass())) {
                        if (n1.getNodeClass().dataEquals(n1, n2)) {
                            if (n1.getNodeClass().equalInputs(n1, n2)) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }

            @Override
            public int hashCode(Object o) {
                if (o instanceof Node) {
                    Node n1 = (Node) o;
                    return n1.getNodeClass().getNameTemplate().hashCode();
                }
                throw GraalError.shouldNotReachHere();
            }

        }

        private final EconomicMap<Node, Node> entries;

        private ValueMap() {
            this.entries = EconomicMap.create(new ValueMapEquivalence());
        }

        public void substitute(Node n) {
            Node edgeDataEqual = entries.get(n);
            if (edgeDataEqual != null) {
                assert edgeDataEqual.graph() != null;
                assert edgeDataEqual instanceof FixedNode : "Only process fixed nodes";
                StructuredGraph graph = (StructuredGraph) edgeDataEqual.graph();
                graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before replacing %s with %s", n, edgeDataEqual);
                graph.replaceFixedWithFixed((FixedWithNextNode) n, (FixedWithNextNode) edgeDataEqual);
                graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After replacing %s with %s", n, edgeDataEqual);
            }
        }

        public void addNode(Node n) {
            GraalError.guarantee(!entries.containsKey(n), "Must GVN before adding a new node");
            entries.put(n, n);
        }

        public void killMemory(Node n) {
            if (MemoryKill.isMemoryKill(n)) {
                if (MemoryKill.isSingleMemoryKill(n)) {
                    SingleMemoryKill singleMemoryKill = MemoryKill.asSingleMemoryKill(n);
                    killByIdentity(singleMemoryKill.getKilledLocationIdentity());
                } else if (MemoryKill.isMultiMemoryKill(n)) {
                    MultiMemoryKill multiMemoryKill = MemoryKill.asMultiMemoryKill(n);
                    for (LocationIdentity loc : multiMemoryKill.getKilledLocationIdentities()) {
                        killByIdentity(loc);
                    }
                }
            }
        }

        private void killByIdentity(LocationIdentity loc) {
            MapCursor<Node, Node> cursor = entries.getEntries();
            while (cursor.advance()) {
                Node next = cursor.getKey();
                if (next instanceof MemoryAccess) {
                    MemoryAccess mem = (MemoryAccess) next;
                    if (mem.getLocationIdentity().overlaps(loc)) {
                        cursor.remove();
                    }
                }
            }
        }

    }

}
