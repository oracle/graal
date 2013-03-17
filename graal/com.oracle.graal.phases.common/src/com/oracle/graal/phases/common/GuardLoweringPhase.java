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

import java.util.*;
import java.util.Map.Entry;

import com.oracle.graal.api.code.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.schedule.*;

public class GuardLoweringPhase extends Phase {

    private TargetDescription target;

    public GuardLoweringPhase(TargetDescription target) {
        this.target = target;
    }

    @Override
    protected void run(StructuredGraph graph) {
        SchedulePhase schedule = new SchedulePhase(SchedulePhase.SchedulingStrategy.EARLIEST);
        schedule.apply(graph);

        for (Block block : schedule.getCFG().getBlocks()) {
            processBlock(block, schedule, graph, target);
        }
    }

    private static void processBlock(Block block, SchedulePhase schedule, StructuredGraph graph, TargetDescription target) {
        List<ScheduledNode> nodes = schedule.nodesFor(block);
        if (GraalOptions.OptImplicitNullChecks && target.implicitNullCheckLimit > 0) {
            useImplicitNullChecks(block.getBeginNode(), nodes, graph, target);
        }
        FixedWithNextNode lastFixed = block.getBeginNode();
        BeginNode lastFastPath = null;
        for (Node node : nodes) {
            if (!node.isAlive()) {
                continue;
            }
            if (lastFastPath != null && node instanceof FixedNode) {
                lastFastPath.setNext((FixedNode) node);
                lastFastPath = null;
            }
            if (node instanceof FixedWithNextNode) {
                lastFixed = (FixedWithNextNode) node;
            } else if (node instanceof GuardNode) {
                GuardNode guard = (GuardNode) node;
                BeginNode fastPath = graph.add(new BeginNode());
                BeginNode trueSuccessor;
                BeginNode falseSuccessor;
                DeoptimizeNode deopt = graph.add(new DeoptimizeNode(guard.action(), guard.reason()));
                BeginNode deoptBranch = BeginNode.begin(deopt);
                Loop loop = block.getLoop();
                while (loop != null) {
                    LoopExitNode exit = graph.add(new LoopExitNode(loop.loopBegin()));
                    graph.addBeforeFixed(deopt, exit);
                    loop = loop.parent;
                }
                if (guard.negated()) {
                    trueSuccessor = deoptBranch;
                    falseSuccessor = fastPath;
                } else {
                    trueSuccessor = fastPath;
                    falseSuccessor = deoptBranch;
                }
                IfNode ifNode = graph.add(new IfNode(guard.condition(), trueSuccessor, falseSuccessor, trueSuccessor == fastPath ? 1 : 0));
                guard.replaceAndDelete(fastPath);
                lastFixed.setNext(ifNode);
                lastFixed = fastPath;
                lastFastPath = fastPath;
            }
        }
    }

    private static void useImplicitNullChecks(BeginNode begin, List<ScheduledNode> nodes, StructuredGraph graph, TargetDescription target) {
        ListIterator<ScheduledNode> iterator = nodes.listIterator();
        IdentityHashMap<ValueNode, GuardNode> nullGuarded = new IdentityHashMap<>();
        FixedWithNextNode lastFixed = begin;
        FixedWithNextNode reconnect = null;
        while (iterator.hasNext()) {
            Node node = iterator.next();

            if (reconnect != null && node instanceof FixedNode) {
                reconnect.setNext((FixedNode) node);
                reconnect = null;
            }
            if (node instanceof FixedWithNextNode) {
                lastFixed = (FixedWithNextNode) node;
            }

            if (node instanceof GuardNode) {
                GuardNode guard = (GuardNode) node;
                if (guard.negated() && guard.condition() instanceof IsNullNode) {
                    ValueNode obj = ((IsNullNode) guard.condition()).object();
                    nullGuarded.put(obj, guard);
                }
            } else if (node instanceof Access) {
                Access access = (Access) node;
                GuardNode guard = nullGuarded.get(access.object());
                if (guard != null && isImplicitNullCheck(access.location(), target)) {
                    NodeInputList<ValueNode> dependencies = ((ValueNode) access).dependencies();
                    dependencies.remove(guard);
                    if (access instanceof FloatingReadNode) {
                        ReadNode read = graph.add(new ReadNode(access.object(), access.location(), ((FloatingReadNode) access).stamp(), dependencies));
                        node.replaceAndDelete(read);
                        access = read;
                        lastFixed.setNext(read);
                        lastFixed = read;
                        reconnect = read;
                        iterator.set(read);
                    }
                    assert access instanceof AccessNode;
                    access.setNullCheck(true);
                    LogicNode condition = guard.condition();
                    guard.replaceAndDelete(access.node());
                    if (condition.usages().isEmpty()) {
                        GraphUtil.killWithUnusedFloatingInputs(condition);
                    }
                    nullGuarded.remove(access.object());
                }
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
    }

    private static boolean isImplicitNullCheck(LocationNode location, TargetDescription target) {
        return !(location instanceof IndexedLocationNode) && location.displacement() < target.implicitNullCheckLimit;
    }
}
