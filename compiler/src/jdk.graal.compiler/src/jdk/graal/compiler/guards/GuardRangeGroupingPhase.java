/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.guards;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.collections.Pair;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.IntegerBelowNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.extended.MultiGuardNode;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.FloatingGuardPhase;
import jdk.graal.compiler.phases.tiers.MidTierContext;

/// Replaces multiple integer based guards with a single [MultiGuardNode] covering the low and high
/// bounds implied by the guards' conjunction. For example:
///
/// ```
/// Guard(x >= 10)
/// Guard(x > 50)
/// Guard(x < 100)
/// Guard(x < 1000)
/// Use(x)
/// ```
///
/// becomes:
///
/// ```
/// MultiGuard(Guard(x > 50), Guard(x < 100))
/// Use(x)
/// ```
public class GuardRangeGroupingPhase extends BasePhase<MidTierContext> implements FloatingGuardPhase {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.unlessRunBefore(this, StageFlag.GUARD_LOWERING, graphState);
    }

    @Override
    protected void run(StructuredGraph graph, MidTierContext context) {
        for (AbstractBeginNode begin : graph.getNodes(AbstractBeginNode.TYPE)) {
            checkGuardAnchor(begin);
        }
    }

    private static class Info {
        private long lowest;
        private long highest;
        private GuardNode lowestGuard;
        private GuardNode highestGuard;
        private List<GuardNode> guards;

        Info(GuardNode firstGuard, long value) {
            this.guards = new ArrayList<>();
            guards.add(firstGuard);
            this.lowestGuard = firstGuard;
            this.highestGuard = firstGuard;
            this.lowest = value;
            this.highest = value;
        }

        public void addGuard(GuardNode guardNode, long value) {
            if (value < lowest) {
                lowest = value;
                lowestGuard = guardNode;
            }
            if (value > highest) {
                highest = value;
                highestGuard = guardNode;
            }
            this.guards.add(guardNode);
        }

        @Override
        public String toString() {
            return String.format("%d to %d / %s", lowest, highest, guards);
        }

        public GuardNode getHighestGuard() {
            return this.highestGuard;
        }

        public Collection<GuardNode> getGuards() {
            return this.guards;
        }

        public GuardNode getLowestGuard() {
            return this.lowestGuard;
        }

        public int getGuardCount() {
            return guards.size();
        }
    }

    private static void checkGuardAnchor(AbstractBeginNode begin) {
        if (begin.getUsageCount() > 1) {
            EconomicMap<Pair<ValueNode, ValueNode>, Info> map = EconomicMap.create();
            for (GuardNode guard : begin.usages().filter(GuardNode.class)) {
                LogicNode condition = guard.getCondition();
                if (condition instanceof IntegerBelowNode) {
                    IntegerBelowNode integerBelowNode = (IntegerBelowNode) condition;
                    ValueNode x = integerBelowNode.getX();
                    ValueNode y = integerBelowNode.getY();
                    if (!guard.isNegated()) {
                        register(map, guard, x, y);
                    }
                } else if (condition instanceof IntegerEqualsNode) {
                    IntegerEqualsNode integerEqualsNode = (IntegerEqualsNode) condition;
                    if (guard.isNegated()) {
                        ValueNode x = integerEqualsNode.getX();
                        ValueNode y = integerEqualsNode.getY();
                        // x != 0 is equivalent with 0 |<| x.
                        if (x instanceof ConstantNode) {
                            ConstantNode constantNode = (ConstantNode) x;
                            if (constantNode.asJavaConstant().asLong() == 0) {
                                registerSucceeding(map, guard, y, null, constantNode);
                            }
                        } else if (y instanceof ConstantNode) {
                            ConstantNode constantNode = (ConstantNode) y;
                            if (constantNode.asJavaConstant().asLong() == 0) {
                                registerSucceeding(map, guard, x, null, constantNode);
                            }
                        }
                    }

                }
            }

            if (map != null && map.size() > 0) {
                MapCursor<Pair<ValueNode, ValueNode>, Info> cursor = map.getEntries();
                while (cursor.advance()) {
                    check(cursor.getKey(), cursor.getValue(), begin.graph());
                }
            }
        }
    }

    private static void check(Pair<ValueNode, ValueNode> key, Info value, StructuredGraph graph) {
        if (key.getLeft() == null) {
            if (value.getGuardCount() > 1) {
                GuardNode highestGuard = value.getHighestGuard();
                if (highestGuard.isAlive()) {
                    for (GuardNode guard : value.getGuards()) {
                        if (guard != highestGuard) {
                            guard.replaceAndDelete(highestGuard);
                        }
                    }
                }
            }
        } else {
            GuardNode highestGuard = value.getHighestGuard();
            GuardNode lowestGuard = value.getLowestGuard();
            if (highestGuard.isAlive() && lowestGuard.isAlive() && value.getGuardCount() > 2) {
                MultiGuardNode multiGuard = graph.addWithoutUnique(new MultiGuardNode());
                multiGuard.setNodeSourcePosition(highestGuard.getNodeSourcePosition());
                highestGuard.replaceAtUsages(multiGuard);
                lowestGuard.replaceAtUsages(multiGuard);
                multiGuard.addGuard(highestGuard);
                multiGuard.addGuard(lowestGuard);
                for (GuardNode guard : value.getGuards()) {
                    if (guard != highestGuard && guard != lowestGuard && guard.isAlive()) {
                        guard.replaceAndDelete(multiGuard);
                    }
                }
                graph.getOptimizationLog().report(GuardRangeGroupingPhase.class, "RangeGuardCombination", lowestGuard);
            }
        }
    }

    private static void register(EconomicMap<Pair<ValueNode, ValueNode>, Info> map, GuardNode guardNode, ValueNode x, ValueNode y) {
        assert !guardNode.isNegated() : guardNode;
        if (x instanceof ConstantNode) {
            ConstantNode constantNode = (ConstantNode) x;
            registerSucceeding(map, guardNode, y, null, constantNode);
        } else {
            if (x instanceof AddNode) {
                AddNode addNode = (AddNode) x;
                ValueNode addX = addNode.getX();
                ValueNode addY = addNode.getY();
                /*
                 * For range check grouping its important that no single arithmetic operation used
                 * to prove something can result in an overflow. If the arithmetic of one of the
                 * guards used to prove a portion for another guard overflows we are effectively not
                 * proving what we think.
                 */
                if (!IntegerStamp.addCanOverflow((IntegerStamp) addX.stamp(NodeView.DEFAULT), (IntegerStamp) addY.stamp(NodeView.DEFAULT))) {
                    if (addX instanceof ConstantNode) {
                        ConstantNode constantNode = (ConstantNode) addX;
                        registerSucceeding(map, guardNode, y, addY, constantNode);
                    } else if (addY instanceof ConstantNode) {
                        ConstantNode constantNode = (ConstantNode) addY;
                        registerSucceeding(map, guardNode, y, addX, constantNode);
                    }
                }
            }
            registerSucceeding(map, guardNode, y, x, ConstantNode.forInt(0));
        }
    }

    private static void registerSucceeding(EconomicMap<Pair<ValueNode, ValueNode>, Info> map, GuardNode guardNode, ValueNode node, ValueNode base, ConstantNode constantNode) {
        IntegerStamp constantStamp = (IntegerStamp) constantNode.stamp(NodeView.DEFAULT);
        long value = constantStamp.upperBound();
        Pair<ValueNode, ValueNode> pair = Pair.create(node, base);
        Info info = map.get(pair);
        if (info == null) {
            map.put(pair, new Info(guardNode, value));
        } else {
            info.addGuard(guardNode, value);
        }
    }
}
