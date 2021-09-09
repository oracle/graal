/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.extended;

import static org.graalvm.compiler.nodeinfo.InputType.Guard;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import org.graalvm.collections.EconomicSet;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.Simplifiable;
import org.graalvm.compiler.nodes.spi.SimplifierTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.util.GraphUtil;

import java.util.ArrayList;

@NodeInfo(allowedUsageTypes = Guard, cycles = CYCLES_0, size = SIZE_0)
public final class MultiGuardNode extends FloatingNode implements GuardingNode, LIRLowerable, Simplifiable, Canonicalizable, Node.ValueNumberable {
    public static final NodeClass<MultiGuardNode> TYPE = NodeClass.create(MultiGuardNode.class);

    @OptionalInput(Guard) NodeInputList<ValueNode> guards;

    public MultiGuardNode(ValueNode... guards) {
        super(TYPE, StampFactory.forVoid());
        this.guards = new NodeInputList<>(this, guards);
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        // Make sure there are no nulls remaining in the set of guards references.
        guards.trim();
        if (guards.size() == 0) {
            // No guards left => can delete the multi-guard.
            return null;
        } else if (guards.size() == 1) {
            // Only a single guard left => replace multi-guard with that single guard.
            return guards.get(0);
        } else {
            // Merge chain of MultiGuardNodes.
            if (guards.filter(MultiGuardNode.class).isNotEmpty()) {
                final ArrayList<ValueNode> list = new ArrayList<>();
                for (ValueNode guard : guards) {
                    if (guard instanceof MultiGuardNode) {
                        list.addAll(((MultiGuardNode) guard).guards);
                    } else {
                        list.add(guard);
                    }
                }
                return new MultiGuardNode(list.toArray(new ValueNode[0]));
            }
        }

        // if the guard contains duplicates we want to create a de-duplicated guard
        NodeBitMap guardsSeen = new NodeBitMap(graph());
        boolean duplicatesFound = false;
        for (ValueNode guard : guards) {
            if (guardsSeen.isMarked(guard)) {
                duplicatesFound = true;
                break;
            }
            guardsSeen.mark(guard);
        }
        if (duplicatesFound) {
            EconomicSet<ValueNode> uniqueGuards = EconomicSet.create();
            for (ValueNode guard : guards) {
                uniqueGuards.add(guard);
            }

            if (uniqueGuards.size() == 1) {
                return uniqueGuards.iterator().next();
            }

            return new MultiGuardNode(uniqueGuards.toArray(new ValueNode[uniqueGuards.size()]));
        }

        return this;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (usages().filter(node -> node instanceof ValueAnchorNode).isNotEmpty()) {
            /*
             * For ValueAnchorNode usages, we can optimize MultiGuardNodes away if they depend on
             * zero or one floating nodes (as opposed to fixed nodes).
             */
            Node singleFloatingGuard = null;
            for (ValueNode guard : guards) {
                if (GraphUtil.isFloatingNode(guard)) {
                    if (singleFloatingGuard == null) {
                        singleFloatingGuard = guard;
                    } else if (singleFloatingGuard != guard) {
                        return;
                    }
                }
            }
            for (Node usage : usages().snapshot()) {
                if (usage instanceof ValueAnchorNode) {
                    usage.replaceFirstInput(this, singleFloatingGuard);
                    tool.addToWorkList(usage);
                }
            }
            if (usages().isEmpty()) {
                GraphUtil.killWithUnusedFloatingInputs(this);
            }
        }
    }

    public void addGuard(GuardingNode g) {
        this.guards.add(g.asNode());
    }

    public static GuardingNode combine(GuardingNode first, GuardingNode second) {
        if (first == null) {
            return second;
        } else if (second == null) {
            return first;
        } else {
            StructuredGraph graph = first.asNode().graph();
            return graph.unique(new MultiGuardNode(first.asNode(), second.asNode()));
        }
    }

    public static GuardingNode addGuard(GuardingNode first, GuardingNode second) {
        if (first instanceof MultiGuardNode && second != null) {
            MultiGuardNode multi = (MultiGuardNode) first;
            multi.addGuard(second);
            return multi;
        } else {
            return combine(first, second);
        }
    }
}
