/*
 * Copyright (c) 2013, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common;

import static com.oracle.graal.phases.GraalOptions.*;

import java.util.*;
import java.util.Map.Entry;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.phases.tiers.*;

public class GuardLoweringPhase extends BasePhase<MidTierContext> {

    private abstract static class ScheduledNodeIterator {

        private FixedWithNextNode lastFixed;
        private FixedWithNextNode reconnect;
        private ListIterator<ScheduledNode> iterator;

        public void processNodes(List<ScheduledNode> nodes, FixedWithNextNode begin) {
            assert begin != null;
            lastFixed = begin;
            reconnect = null;
            iterator = nodes.listIterator();
            while (iterator.hasNext()) {
                Node node = iterator.next();
                if (!node.isAlive()) {
                    continue;
                }
                if (reconnect != null && node instanceof FixedNode) {
                    reconnect.setNext((FixedNode) node);
                    reconnect = null;
                }
                if (node instanceof FixedWithNextNode) {
                    lastFixed = (FixedWithNextNode) node;
                }
                processNode(node);
            }
        }

        protected void insert(FixedNode start, FixedWithNextNode end) {
            this.lastFixed.setNext(start);
            this.lastFixed = end;
            this.reconnect = end;
        }

        protected void replaceCurrent(FixedWithNextNode newNode) {
            Node current = iterator.previous();
            iterator.next(); // needed because of the previous() call
            current.replaceAndDelete(newNode);
            insert(newNode, newNode);
            iterator.set(newNode);
        }

        protected abstract void processNode(Node node);
    }

    private static class UseImplicitNullChecks extends ScheduledNodeIterator {

        private final IdentityHashMap<ValueNode, GuardNode> nullGuarded = new IdentityHashMap<>();
        private final int implicitNullCheckLimit;

        UseImplicitNullChecks(int implicitNullCheckLimit) {
            this.implicitNullCheckLimit = implicitNullCheckLimit;
        }

        @Override
        protected void processNode(Node node) {
            if (node instanceof GuardNode) {
                processGuard(node);
            } else if (node instanceof Access) {
                processAccess((Access) node);
            }
            if (node instanceof StateSplit && ((StateSplit) node).stateAfter() != null) {
                nullGuarded.clear();
            } else {
                Iterator<Entry<ValueNode, GuardNode>> it = nullGuarded.entrySet().iterator();
                while (it.hasNext()) {
                    Entry<ValueNode, GuardNode> entry = it.next();
                    GuardNode guard = entry.getValue();
                    if (guard.usages().contains(node)) {
                        it.remove();
                    }
                }
            }
        }

        private void processAccess(Access access) {
            GuardNode guard = nullGuarded.get(access.object());
            if (guard != null && isImplicitNullCheck(access.nullCheckLocation())) {
                access.setGuard(guard.getGuard());
                Access fixedAccess = access;
                if (access instanceof FloatingAccessNode) {
                    fixedAccess = ((FloatingAccessNode) access).asFixedNode();
                    replaceCurrent((FixedWithNextNode) fixedAccess.asNode());
                }
                assert fixedAccess instanceof FixedNode;
                fixedAccess.setNullCheck(true);
                LogicNode condition = guard.condition();
                guard.replaceAndDelete(fixedAccess.asNode());
                if (condition.usages().isEmpty()) {
                    GraphUtil.killWithUnusedFloatingInputs(condition);
                }
                nullGuarded.remove(fixedAccess.object());
            }
        }

        private void processGuard(Node node) {
            GuardNode guard = (GuardNode) node;
            if (guard.negated() && guard.condition() instanceof IsNullNode) {
                ValueNode obj = ((IsNullNode) guard.condition()).object();
                nullGuarded.put(obj, guard);
            }
        }

        private boolean isImplicitNullCheck(LocationNode location) {
            if (location instanceof ConstantLocationNode) {
                return ((ConstantLocationNode) location).getDisplacement() < implicitNullCheckLimit;
            } else {
                return false;
            }
        }
    }

    private static class LowerGuards extends ScheduledNodeIterator {

        private final Block block;

        public LowerGuards(Block block) {
            this.block = block;
        }

        @Override
        protected void processNode(Node node) {
            if (node instanceof GuardNode) {
                GuardNode guard = (GuardNode) node;
                if (guard.negated() && guard.condition() instanceof IsNullNode) {
                    lowerToNullCheck(guard);
                } else {
                    lowerToIf(guard);
                }
            }
        }

        private void lowerToIf(GuardNode guard) {
            StructuredGraph graph = guard.graph();
            AbstractBeginNode fastPath = graph.add(new BeginNode());
            DeoptimizeNode deopt = graph.add(new DeoptimizeNode(guard.action(), guard.reason()));
            AbstractBeginNode deoptBranch = AbstractBeginNode.begin(deopt);
            AbstractBeginNode trueSuccessor;
            AbstractBeginNode falseSuccessor;
            insertLoopExits(deopt);
            if (guard.negated()) {
                trueSuccessor = deoptBranch;
                falseSuccessor = fastPath;
            } else {
                trueSuccessor = fastPath;
                falseSuccessor = deoptBranch;
            }
            IfNode ifNode = graph.add(new IfNode(guard.condition(), trueSuccessor, falseSuccessor, trueSuccessor == fastPath ? 1 : 0));
            guard.replaceAndDelete(fastPath);
            insert(ifNode, fastPath);
        }

        private void lowerToNullCheck(GuardNode guard) {
            IsNullNode isNull = (IsNullNode) guard.condition();
            NullCheckNode nullCheck = guard.graph().add(new NullCheckNode(isNull.object()));
            replaceCurrent(nullCheck);
            if (isNull.usages().isEmpty()) {
                isNull.safeDelete();
            }
        }

        private void insertLoopExits(DeoptimizeNode deopt) {
            Loop loop = block.getLoop();
            StructuredGraph graph = deopt.graph();
            while (loop != null) {
                LoopExitNode exit = graph.add(new LoopExitNode(loop.loopBegin()));
                graph.addBeforeFixed(deopt, exit);
                loop = loop.parent;
            }
        }
    }

    @Override
    protected void run(StructuredGraph graph, MidTierContext context) {
        SchedulePhase schedule = new SchedulePhase();
        schedule.apply(graph);

        for (Block block : schedule.getCFG().getBlocks()) {
            processBlock(block, schedule, context.getTarget().implicitNullCheckLimit);
        }
    }

    private static void processBlock(Block block, SchedulePhase schedule, int implicitNullCheckLimit) {
        List<ScheduledNode> nodes = schedule.nodesFor(block);
        if (OptImplicitNullChecks.getValue() && implicitNullCheckLimit > 0) {
            new UseImplicitNullChecks(implicitNullCheckLimit).processNodes(nodes, block.getBeginNode());
        }
        new LowerGuards(block).processNodes(nodes, block.getBeginNode());
    }
}
