/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph.RecursiveVisitor;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.nodes.memory.FloatingAccessNode;
import org.graalvm.compiler.nodes.memory.FloatingReadNode;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.graph.ScheduledNodeIterator;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase.SchedulingStrategy;
import org.graalvm.compiler.phases.tiers.LowTierContext;

/**
 * This phase lowers {@link FloatingReadNode FloatingReadNodes} into corresponding fixed reads.
 */
public class FixReadsPhase extends BasePhase<LowTierContext> {

    private static class FixReadsIterator extends ScheduledNodeIterator implements RecursiveVisitor<Integer> {

        private final ScheduleResult schedule;

        public FixReadsIterator(ScheduleResult schedule) {
            this.schedule = schedule;
        }

        @Override
        protected void processNode(Node node) {
            if (node instanceof FloatingAccessNode) {
                FloatingAccessNode floatingAccessNode = (FloatingAccessNode) node;
                FixedAccessNode fixedAccess = floatingAccessNode.asFixedNode();
                replaceCurrent(fixedAccess);
            } else if (node instanceof PiNode) {
                PiNode piNode = (PiNode) node;
                piNode.replaceAndDelete(piNode.getOriginalNode());
            }
        }

        @Override
        public Integer enter(Block b) {
            this.processNodes(b, schedule);
            return null;
        }

        @Override
        public void exit(Block b, Integer value) {
        }

    }

    @Override
    protected void run(StructuredGraph graph, LowTierContext context) {
        SchedulePhase schedulePhase = new SchedulePhase(SchedulingStrategy.LATEST_OUT_OF_LOOPS);
        schedulePhase.apply(graph);
        ScheduleResult schedule = graph.getLastSchedule();
        schedule.getCFG().visitDominatorTree(new FixReadsIterator(schedule), false);
    }
}
