/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package org.graalvm.compiler.core.test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.GuardNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.IntegerBelowNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.extended.MultiGuardNode;
import org.graalvm.compiler.nodes.memory.AddressableMemoryAccess;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.nodes.memory.FloatingAccessNode;
import org.graalvm.compiler.nodes.memory.GuardedMemoryAccess;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.phases.common.IterativeConditionalEliminationPhase;
import org.graalvm.compiler.phases.tiers.MidTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.function.Consumer;

import static jdk.vm.ci.meta.DeoptimizationReason.BoundsCheckException;

public class RangeCheckEliminationTest extends GraalCompilerTest {
    private Consumer<StructuredGraph> afterRangeCheckElimination;

    private static int getBoundCheckCount(StructuredGraph graph) {
        return graph.getNodes().filter(n -> n instanceof IntegerBelowNode).count();
    }

    private static Set<Integer> getBoundCheckBounds(StructuredGraph graph) {
        final HashSet<Integer> bounds = new HashSet<>();
        for (Node node : graph.getNodes()) {
            if (node instanceof IntegerBelowNode) {
                final ValueNode x = ((IntegerBelowNode) node).getX();
                if (x instanceof AddNode) {
                    bounds.add(((IntegerStamp) ((AddNode) x).getY().stamp(NodeView.DEFAULT)).asConstant().asInt());
                } else {
                    bounds.add(0);
                }
            }
        }
        return bounds;
    }

    private static Set<ValueNode> getBoundCheckIndexes(StructuredGraph graph) {
        final HashSet<ValueNode> indexes = new HashSet<>();
        for (Node node : graph.getNodes()) {
            if (node instanceof IntegerBelowNode) {
                final ValueNode x = ((IntegerBelowNode) node).getX();
                if (x instanceof AddNode) {
                    indexes.add(((AddNode) x).getX());
                } else {
                    indexes.add(x);
                }
            }
        }
        return indexes;
    }

    @Override
    protected Suites createSuites(OptionValues opts) {
        final Suites suites = super.createSuites(opts);
        Suites ret = suites.copy();
        ListIterator<BasePhase<? super MidTierContext>> iter = ret.getMidTier().findPhase(IterativeConditionalEliminationPhase.class, true);
        iter.add(new Phase() {

            @Override
            protected void run(StructuredGraph graph) {
                afterRangeCheckElimination.accept(graph);
            }

            @Override
            public float codeSizeIncrease() {
                return NodeSize.IGNORE_SIZE_CONTRACT_FACTOR;
            }

            @Override
            protected CharSequence getName() {
                return "Verify guards";
            }
        });

        return ret;
    }

    private final SpeculationLog speculationLog;

    @Override
    protected SpeculationLog getSpeculationLog() {
        speculationLog.collectFailedSpeculations();
        return speculationLog;
    }

    public RangeCheckEliminationTest() {
        speculationLog = getCodeCache().createSpeculationLog();
    }

    private static class CheckOrAccess {
        private final Node n;
        private final Node index;
        private final int offset;

        CheckOrAccess(Node n, Node index, int offset) {
            this.n = n;
            this.index = index;
            this.offset = offset;
        }

        @Override
        public String toString() {
            return "CheckOrAccess{" +
                            "n=" + n +
                            ", index=" + index +
                            ", offset=" + offset +
                            '}';
        }

        public Node getNode() {
            return n;
        }

        public Node getIndex() {
            return index;
        }

        public int getOffset() {
            return offset;
        }
    }

    private static List<CheckOrAccess> getChecksAndAccesses(StructuredGraph graph) {
        final ArrayList<CheckOrAccess> checksAndAccesses = new ArrayList<>();
        StructuredGraph.ScheduleResult schedule = graph.getLastSchedule();
        ControlFlowGraph cfg = schedule.getCFG();
        Block[] blocks = cfg.getBlocks();
        for (Block b : blocks) {
            final List<Node> nodes = schedule.nodesFor(b);
            for (Node n : nodes) {
                if (isCheckOrAccess(n)) {
                    final CheckOrAccess checkOrAccess = checkOrAccess(n);
                    if (checkOrAccess.getIndex() != null) {
                        checksAndAccesses.add(checkOrAccess);
                    }
                }
            }
        }
        return checksAndAccesses;
    }

    private static boolean isCheckOrAccess(Node n) {
        return (n instanceof IfNode && ((IfNode) n).condition() instanceof IntegerBelowNode) ||
                        (n instanceof GuardNode && ((GuardNode) n).getCondition() instanceof IntegerBelowNode) ||
                        n instanceof FixedAccessNode ||
                        n instanceof FloatingAccessNode;
    }

    private static CheckOrAccess checkOrAccess(Node n) {
        Node index = null;
        if (n instanceof IfNode) {
            final IntegerBelowNode condition = (IntegerBelowNode) ((IfNode) n).condition();
            index = condition.getX();
        } else if (n instanceof GuardNode) {
            final IntegerBelowNode condition = (IntegerBelowNode) ((GuardNode) n).getCondition();
            index = condition.getX();
        } else if (n instanceof FixedAccessNode || n instanceof FloatingAccessNode) {
            AddressableMemoryAccess accessNode = (AddressableMemoryAccess) n;
            final AddressNode address = accessNode.getAddress();
            if (address instanceof OffsetAddressNode) {
                final ValueNode offset = ((OffsetAddressNode) address).getOffset();
                if (offset instanceof AddNode) {
                    final ValueNode shift = ((AddNode) offset).getX();
                    final ValueNode extend = ((LeftShiftNode) shift).getX();
                    final ValueNode pi = ((ZeroExtendNode) extend).getValue();
                    index = ((PiNode) pi).getOriginalNode();
                }
            } else {
                index = address.getIndex();
            }
        } else {
            throw new RuntimeException("Unexpected node type");
        }
        int offset = 0;
        if (index instanceof AddNode) {
            AddNode addNode = (AddNode) index;
            index = addNode.getX();
            offset = ((IntegerStamp) addNode.getY().stamp(NodeView.DEFAULT)).asConstant().asInt();
        }
        return new CheckOrAccess(n, index, offset);
    }

    private static void checkBounds(StructuredGraph graph, Integer... bounds) {
        HashSet<Integer> expectedBounds = new HashSet<>(Arrays.asList(bounds));
        Assert.assertEquals(getBoundCheckBounds(graph), expectedBounds);
    }

    class ChecksAndAccesses {
        final List<CheckOrAccess> checksAndAccesses;

        ChecksAndAccesses(StructuredGraph graph) {
            checksAndAccesses = getChecksAndAccesses(graph);
        }

        boolean contains(int guard, int... guards) {
            for (int g : guards) {
                if (g == guard) {
                    return true;
                }
            }
            return false;
        }

        void verifyAccessDependsOnGuards(int access, int... guards) {
            for (int i = 0; i < checksAndAccesses.size(); i++) {
                final CheckOrAccess checkOrAccess0 = checksAndAccesses.get(i);
                final Node accessNode = checkOrAccess0.getNode();
                if (accessNode instanceof GuardedMemoryAccess && checkOrAccess0.getOffset() == access) {
                    GuardingNode guard0 = ((GuardedMemoryAccess) accessNode).getGuard();
                    GuardingNode guard1 = null;
                    if (guard0 instanceof MultiGuardNode) {
                        final List<Node> snapshot = ((MultiGuardNode) guard0).inputs().snapshot();
                        assert snapshot.size() == 2;
                        guard0 = (GuardingNode) snapshot.get(0);
                        guard1 = (GuardingNode) snapshot.get(1);
                    }
                    int found = 0;
                    for (int j = 0; j < i; j++) {
                        final CheckOrAccess checkOrAccess1 = checksAndAccesses.get(j);
                        final Node guardNode = checkOrAccess1.getNode();
                        if (guardNode instanceof GuardNode && contains(checkOrAccess1.getOffset(), guards) &&
                                        (guardNode == guard0 || guardNode == guard1)) {
                            found++;
                        }
                    }
                    Assert.assertEquals(guards.length, found);
                    checksAndAccesses.remove(i);
                    return;
                }
            }
            Assert.fail();
        }
    }

    public static void rangeCheckElimination1(int[] array, int i) {
        array[i] = 0;
        array[i + 2] = 2;
        array[i + 1] = 1; // range check redundant with 2 previous ones
    }

    @Test
    public void test1() {
        afterRangeCheckElimination = graph -> {
            final ChecksAndAccesses checksAndAccesses = new ChecksAndAccesses(graph);
            checksAndAccesses.verifyAccessDependsOnGuards(0, 0);
            checksAndAccesses.verifyAccessDependsOnGuards(2, 2);
            checksAndAccesses.verifyAccessDependsOnGuards(1, 0, 2);
        };

        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("rangeCheckElimination1"));
        Assert.assertEquals(getBoundCheckCount(graph), 2);
        checkBounds(graph, 0, 2);
        Assert.assertEquals(getBoundCheckIndexes(graph).size(), 1);
    }

    public static void rangeCheckElimination2(int[] array, int i) {
        array[i + 2] = 2;
        array[i] = 0;
        array[i + 1] = 1; // range check redundant with 2 previous ones
    }

    @Test
    public void test2() {
        afterRangeCheckElimination = graph -> {
            final ChecksAndAccesses checksAndAccesses = new ChecksAndAccesses(graph);
            checksAndAccesses.verifyAccessDependsOnGuards(0, 0);
            checksAndAccesses.verifyAccessDependsOnGuards(2, 2);
            checksAndAccesses.verifyAccessDependsOnGuards(1, 0, 2);
        };
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("rangeCheckElimination2"));
        Assert.assertEquals(getBoundCheckCount(graph), 2);
        checkBounds(graph, 0, 2);
        Assert.assertEquals(getBoundCheckIndexes(graph).size(), 1);
    }

    public static void rangeCheckElimination3(int[] array, int i) {
        array[i] = 0;
        array[i + 1] = 1; // range check redundant with 2 other ones if floated below
        array[i + 2] = 2;
    }

    @Test
    public void test3() {
        afterRangeCheckElimination = graph -> {
            final ChecksAndAccesses checksAndAccesses = new ChecksAndAccesses(graph);
            checksAndAccesses.verifyAccessDependsOnGuards(0, 0);
            checksAndAccesses.verifyAccessDependsOnGuards(2, 2);
            checksAndAccesses.verifyAccessDependsOnGuards(1, 0, 2);
        };
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("rangeCheckElimination3"));
        Assert.assertEquals(getBoundCheckCount(graph), 2);
        checkBounds(graph, 0, 2);
        Assert.assertEquals(getBoundCheckIndexes(graph).size(), 1);
    }

    public static void rangeCheckElimination4(int[] array, int i) {
        array[i + 2] = 2;
        array[i + 1] = 1; // range check redundant with 2 other ones if floated below
        array[i] = 0;
    }

    @Test
    public void test4() {
        afterRangeCheckElimination = graph -> {
            final ChecksAndAccesses checksAndAccesses = new ChecksAndAccesses(graph);
            checksAndAccesses.verifyAccessDependsOnGuards(0, 0);
            checksAndAccesses.verifyAccessDependsOnGuards(2, 2);
            checksAndAccesses.verifyAccessDependsOnGuards(1, 0, 2);
        };
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("rangeCheckElimination4"));
        Assert.assertEquals(getBoundCheckCount(graph), 2);
        checkBounds(graph, 0, 2);
        Assert.assertEquals(getBoundCheckIndexes(graph).size(), 1);
    }

    public static void rangeCheckElimination5(int[] array, int i) {
        array[i + 1] = 1; // range check redundant with 2 other ones if floated below
        array[i] = 0;
        array[i + 2] = 2;
    }

    @Test
    public void test5() {
        afterRangeCheckElimination = graph -> {
            final ChecksAndAccesses checksAndAccesses = new ChecksAndAccesses(graph);
            checksAndAccesses.verifyAccessDependsOnGuards(0, 0);
            checksAndAccesses.verifyAccessDependsOnGuards(2, 2);
            checksAndAccesses.verifyAccessDependsOnGuards(1, 0, 2);
        };
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("rangeCheckElimination5"));
        Assert.assertEquals(getBoundCheckCount(graph), 2);
        checkBounds(graph, 0, 2);
        Assert.assertEquals(getBoundCheckIndexes(graph).size(), 1);
    }

    // Same tests as above but with loads instead of stores
    public static int rangeCheckElimination6(int[] array, int i) {
        final int i1 = array[i];
        final int i2 = array[i + 2];
        final int i3 = array[i + 1];
        return i1 + i2 + i3;
    }

    @Test
    public void test6() {
        afterRangeCheckElimination = graph -> {
            final ChecksAndAccesses checksAndAccesses = new ChecksAndAccesses(graph);
            checksAndAccesses.verifyAccessDependsOnGuards(0, 0);
            checksAndAccesses.verifyAccessDependsOnGuards(2, 2);
            checksAndAccesses.verifyAccessDependsOnGuards(1, 0, 2);
        };
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("rangeCheckElimination6"));
        Assert.assertEquals(getBoundCheckCount(graph), 2);
        checkBounds(graph, 0, 2);
        Assert.assertEquals(getBoundCheckIndexes(graph).size(), 1);
    }

    public static int rangeCheckElimination7(int[] array, int i) {
        final int i1 = array[i + 2];
        final int i2 = array[i];
        final int i3 = array[i + 1];
        return i1 + i2 + i3;
    }

    @Test
    public void test7() {
        afterRangeCheckElimination = graph -> {
            final ChecksAndAccesses checksAndAccesses = new ChecksAndAccesses(graph);
            checksAndAccesses.verifyAccessDependsOnGuards(0, 0);
            checksAndAccesses.verifyAccessDependsOnGuards(2, 2);
            checksAndAccesses.verifyAccessDependsOnGuards(1, 0, 2);
        };
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("rangeCheckElimination7"));
        Assert.assertEquals(getBoundCheckCount(graph), 2);
        checkBounds(graph, 0, 2);
        Assert.assertEquals(getBoundCheckIndexes(graph).size(), 1);
    }

    public static int rangeCheckElimination8(int[] array, int i) {
        final int i1 = array[i];
        final int i2 = array[i + 1];
        final int i3 = array[i + 2];
        return i1 + i2 + i3;
    }

    @Test
    public void test8() {
        afterRangeCheckElimination = graph -> {
            final ChecksAndAccesses checksAndAccesses = new ChecksAndAccesses(graph);
            checksAndAccesses.verifyAccessDependsOnGuards(0, 0);
            checksAndAccesses.verifyAccessDependsOnGuards(2, 2);
            checksAndAccesses.verifyAccessDependsOnGuards(1, 0, 2);
        };
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("rangeCheckElimination8"));
        Assert.assertEquals(getBoundCheckCount(graph), 2);
        checkBounds(graph, 0, 2);
        Assert.assertEquals(getBoundCheckIndexes(graph).size(), 1);
    }

    public static int rangeCheckElimination9(int[] array, int i) {
        final int i1 = array[i + 2];
        final int i2 = array[i + 1];
        final int i3 = array[i];
        return i1 + i2 + i3;
    }

    @Test
    public void test9() {
        afterRangeCheckElimination = graph -> {
            final ChecksAndAccesses checksAndAccesses = new ChecksAndAccesses(graph);
            checksAndAccesses.verifyAccessDependsOnGuards(0, 0);
            checksAndAccesses.verifyAccessDependsOnGuards(2, 2);
            checksAndAccesses.verifyAccessDependsOnGuards(1, 0, 2);
        };
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("rangeCheckElimination9"));
        Assert.assertEquals(getBoundCheckCount(graph), 2);
        checkBounds(graph, 0, 2);
        Assert.assertEquals(getBoundCheckIndexes(graph).size(), 1);
    }

    public static int rangeCheckElimination10(int[] array, int i) {
        final int i1 = array[i + 1];
        final int i2 = array[i];
        final int i3 = array[i + 2];
        return i1 + i2 + i3;
    }

    @Test
    public void test10() {
        afterRangeCheckElimination = graph -> {
            final ChecksAndAccesses checksAndAccesses = new ChecksAndAccesses(graph);
            checksAndAccesses.verifyAccessDependsOnGuards(0, 0);
            checksAndAccesses.verifyAccessDependsOnGuards(2, 2);
            checksAndAccesses.verifyAccessDependsOnGuards(1, 0, 2);
        };
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("rangeCheckElimination10"));
        Assert.assertEquals(getBoundCheckCount(graph), 2);
        checkBounds(graph, 0, 2);
        Assert.assertEquals(getBoundCheckIndexes(graph).size(), 1);
    }

    public static void rangeCheckElimination11(int[] array, int i, boolean flag, boolean flag2) {
        array[i] = 0;
        if (flag) {
            array[i + 2] = 2;
            if (flag2) {
                array[i + 1] = 1; // range check redundant with 2 previous ones
            }
        }
    }

    @Test
    public void test11() {
        afterRangeCheckElimination = graph -> {
            final ChecksAndAccesses checksAndAccesses = new ChecksAndAccesses(graph);
            checksAndAccesses.verifyAccessDependsOnGuards(0, 0);
            checksAndAccesses.verifyAccessDependsOnGuards(2, 2);
            checksAndAccesses.verifyAccessDependsOnGuards(1, 0, 2);
        };
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("rangeCheckElimination11"));
        Assert.assertEquals(getBoundCheckCount(graph), 2);
        checkBounds(graph, 0, 2);
        Assert.assertEquals(getBoundCheckIndexes(graph).size(), 1);
    }

    public static void rangeCheckElimination12(int[] array, int i, boolean flag, boolean flag2) {
        array[i + 2] = 2;
        if (flag) {
            array[i] = 0;
            if (flag2) {
                array[i + 1] = 1; // range check redundant with 2 previous ones
            }
        }
    }

    @Test
    public void test12() {
        afterRangeCheckElimination = graph -> {
            final ChecksAndAccesses checksAndAccesses = new ChecksAndAccesses(graph);
            checksAndAccesses.verifyAccessDependsOnGuards(0, 0);
            checksAndAccesses.verifyAccessDependsOnGuards(2, 2);
            checksAndAccesses.verifyAccessDependsOnGuards(1, 0, 2);
        };
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("rangeCheckElimination12"));
        Assert.assertEquals(getBoundCheckCount(graph), 2);
        checkBounds(graph, 0, 2);
        Assert.assertEquals(getBoundCheckIndexes(graph).size(), 1);
    }

    public static void rangeCheckElimination13(int[] array, int i, boolean flag, boolean flag2) {
        array[i] = 0;
        if (flag) {
            array[i + 1] = 1;
            if (flag2) {
                array[i + 2] = 2; // range check redundant once i+1 check widden to i+2
            }
        }
    }

    @Test
    public void test13() {
        afterRangeCheckElimination = graph -> {
            final ChecksAndAccesses checksAndAccesses = new ChecksAndAccesses(graph);
            checksAndAccesses.verifyAccessDependsOnGuards(0, 0);
            checksAndAccesses.verifyAccessDependsOnGuards(2, 2);
            checksAndAccesses.verifyAccessDependsOnGuards(1, 0, 2);
        };
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("rangeCheckElimination13"));
        Assert.assertEquals(getBoundCheckCount(graph), 2);
        checkBounds(graph, 0, 2);
        Assert.assertEquals(getBoundCheckIndexes(graph).size(), 1);
    }

    public static void rangeCheckElimination14(int[] array, int i, boolean flag, boolean flag2) {
        array[i + 2] = 2;
        if (flag) {
            array[i + 1] = 1;
            if (flag2) {
                array[i] = 0; // range check redundant once i+1 check widden to i
            }
        }
    }

    @Test
    public void test14() {
        afterRangeCheckElimination = graph -> {
            final ChecksAndAccesses checksAndAccesses = new ChecksAndAccesses(graph);
            checksAndAccesses.verifyAccessDependsOnGuards(0, 0);
            checksAndAccesses.verifyAccessDependsOnGuards(2, 2);
            checksAndAccesses.verifyAccessDependsOnGuards(1, 0, 2);
        };
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("rangeCheckElimination14"));
        Assert.assertEquals(getBoundCheckCount(graph), 2);
        checkBounds(graph, 0, 2);
        Assert.assertEquals(getBoundCheckIndexes(graph).size(), 1);
    }

    public static void rangeCheckElimination15(int[] array, int i, boolean flag, boolean flag2) {
        // no redundant range checks
        array[i + 1] = 1;
        if (flag) {
            array[i] = 0;
            if (flag2) {
                array[i + 2] = 2;
            }
        }
    }

    @Test
    public void test15() {
        afterRangeCheckElimination = graph -> {
            final ChecksAndAccesses checksAndAccesses = new ChecksAndAccesses(graph);
            checksAndAccesses.verifyAccessDependsOnGuards(1, 1);
            checksAndAccesses.verifyAccessDependsOnGuards(0, 0);
            checksAndAccesses.verifyAccessDependsOnGuards(2, 2);
        };
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("rangeCheckElimination15"));
        Assert.assertEquals(getBoundCheckCount(graph), 3);
        checkBounds(graph, 0, 1, 2);
        Assert.assertEquals(getBoundCheckIndexes(graph).size(), 1);
    }

    public static void rangeCheckElimination16(int[] array, int i, boolean flag, boolean flag2, boolean flag3) {
        array[i + 1] = 0;
        if (flag) {
            array[i] = 2;
            if (flag2) {
                array[i + 2] = 1;
                if (flag3) {
                    array[i + 3] = 1; // range check redundant once i+2 check widden to i+3
                }
            }
        }
    }

    @Test
    public void test16() {
        afterRangeCheckElimination = graph -> {
            final ChecksAndAccesses checksAndAccesses = new ChecksAndAccesses(graph);
            checksAndAccesses.verifyAccessDependsOnGuards(1, 1);
            checksAndAccesses.verifyAccessDependsOnGuards(0, 0);
            checksAndAccesses.verifyAccessDependsOnGuards(2, 0, 3);
            checksAndAccesses.verifyAccessDependsOnGuards(3, 3);
        };
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("rangeCheckElimination16"));
        Assert.assertEquals(getBoundCheckCount(graph), 3);
        checkBounds(graph, 1, 0, 3);
        Assert.assertEquals(getBoundCheckIndexes(graph).size(), 1);
    }

    public static void rangeCheckElimination17(int[] array, int i, boolean flag, boolean flag2, boolean flag3) {
        array[i + 2] = 0;
        if (flag) {
            array[i + 1] = 2;
            if (flag2) {
                array[i + 3] = 1;
                if (flag3) {
                    array[i] = 1; // range check redundant once i+1 check widden to i
                }
            }
        }
    }

    @Test
    public void test17() {
        afterRangeCheckElimination = graph -> {
            final ChecksAndAccesses checksAndAccesses = new ChecksAndAccesses(graph);
            checksAndAccesses.verifyAccessDependsOnGuards(2, 2);
            checksAndAccesses.verifyAccessDependsOnGuards(1, 0, 2);
            checksAndAccesses.verifyAccessDependsOnGuards(3, 3);
            checksAndAccesses.verifyAccessDependsOnGuards(0, 0);
        };
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("rangeCheckElimination17"));
        Assert.assertEquals(getBoundCheckCount(graph), 3);
        checkBounds(graph, 2, 0, 3);
        Assert.assertEquals(getBoundCheckIndexes(graph).size(), 1);
    }

    public static void rangeCheckElimination18(int[] array, int i, boolean flag, boolean flag2, boolean flag3) {
        array[i + 1] = 0;
        if (flag) {
            array[i + 2] = 2;
            if (flag2) {
                array[i] = 1;
                if (flag3) {
                    array[i + 3] = 1; // range check redundant once i+2 check widden to i+3
                }
            }
        }
    }

    @Test
    public void test18() {
        afterRangeCheckElimination = graph -> {
            final ChecksAndAccesses checksAndAccesses = new ChecksAndAccesses(graph);
            checksAndAccesses.verifyAccessDependsOnGuards(1, 1);
            checksAndAccesses.verifyAccessDependsOnGuards(2, 1, 3);
            checksAndAccesses.verifyAccessDependsOnGuards(0, 0);
            checksAndAccesses.verifyAccessDependsOnGuards(3, 3);
        };
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("rangeCheckElimination18"));
        Assert.assertEquals(getBoundCheckCount(graph), 3);
        checkBounds(graph, 1, 3, 0);
        Assert.assertEquals(getBoundCheckIndexes(graph).size(), 1);
    }

    public static void rangeCheckElimination19(int[] array, int i, boolean flag, boolean flag2, boolean flag3) {
        array[i + 1] = 0;
        if (flag) {
            array[i + 3] = 2;
            if (flag2) {
                array[i + 2] = 1; // eliminated because redundant
                if (flag3) {
                    array[i + 4] = 1; // range check redundant once i+3 check widden to i+4
                }
            }
        }
    }

    @Test
    public void test19() {
        afterRangeCheckElimination = graph -> {
            final ChecksAndAccesses checksAndAccesses = new ChecksAndAccesses(graph);
            checksAndAccesses.verifyAccessDependsOnGuards(1, 1);
            checksAndAccesses.verifyAccessDependsOnGuards(3, 1, 4);
            checksAndAccesses.verifyAccessDependsOnGuards(2, 1, 4);
            checksAndAccesses.verifyAccessDependsOnGuards(4, 1, 4);
        };
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("rangeCheckElimination19"));
        Assert.assertEquals(getBoundCheckCount(graph), 2);
        checkBounds(graph, 1, 4);
        Assert.assertEquals(getBoundCheckIndexes(graph).size(), 1);
    }

    public static void rangeCheckElimination20(int[] array, int i, int l, boolean flag) {
        int j = 1;
        for (int k = 0; k < 2; k++) {
            j *= 2;
        }
        if (l > j) {
            if (l > 0) { // this is proven true after 1st iteration of conditional elimination
                array[i + 3] = 3;
            }
            if (flag) {
                array[i] = 0;
                array[i + 2] = 2;
                array[i + 1] = 1;
            }
        }
    }

    @Test
    public void test20() {
        afterRangeCheckElimination = graph -> {
            final ChecksAndAccesses checksAndAccesses = new ChecksAndAccesses(graph);
            checksAndAccesses.verifyAccessDependsOnGuards(3, 3);
            checksAndAccesses.verifyAccessDependsOnGuards(0, 0);
            checksAndAccesses.verifyAccessDependsOnGuards(2, 0, 3);
            checksAndAccesses.verifyAccessDependsOnGuards(1, 0, 3);
        };
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("rangeCheckElimination20"));
        Assert.assertEquals(getBoundCheckCount(graph), 2);
        checkBounds(graph, 0, 3);
        Assert.assertEquals(getBoundCheckIndexes(graph).size(), 1);
    }

    // check that speculative widening is not performed once it caused a deoptimization
    public static void rangeCheckElimination21(int[] array, int i, boolean flag, boolean flag2) {
        array[i] = 0;
        if (flag) {
            array[i + 1] = 1;
            if (flag2) {
                array[i + 2] = 2;
            }
        }
    }

    @Test
    public void test21() throws InvalidInstalledCodeException {
        afterRangeCheckElimination = graph -> {
        };
        OptionValues options = getInitialOptions();
        ResolvedJavaMethod method = getResolvedJavaMethod("rangeCheckElimination21");
        InstalledCode compiledMethod = getCode(method, options);
        int[] array = new int[10];
        compiledMethod.executeVarargs(new Object[]{array, 8, true, false});
        getCode(method, options);
        final HashSet<DeoptimizationReason> deoptimizationReasons = new HashSet<>();
        deoptimizationReasons.add(BoundsCheckException);
        executeActualCheckDeopt(options, method, deoptimizationReasons, array, 8, true, false);
    }

    public static void rangeCheckElimination22(int[] array, int i, int l, boolean flag) {
        int j = 1;
        for (int k = 0; k < 2; k++) {
            j *= 2;
        }
        if (l > j) {
            if (l > 0) { // this is proven true after 1st iteration of conditional elimination
                array[i] = 3;
                array[i + 2] = 3;
            }
            if (flag) {
                array[i + 1] = 0;
                array[i + 3] = 2;
                array[i + 2] = 1;
            }
        }
    }

    @Test
    public void test22() {
        afterRangeCheckElimination = graph -> {
            final ChecksAndAccesses checksAndAccesses = new ChecksAndAccesses(graph);
            checksAndAccesses.verifyAccessDependsOnGuards(0, 0);
            checksAndAccesses.verifyAccessDependsOnGuards(2, 0, 3);
            checksAndAccesses.verifyAccessDependsOnGuards(1, 0, 3);
            checksAndAccesses.verifyAccessDependsOnGuards(3, 3);
            checksAndAccesses.verifyAccessDependsOnGuards(2, 0, 3);
        };
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("rangeCheckElimination22"));
        Assert.assertEquals(getBoundCheckCount(graph), 2);
        checkBounds(graph, 0, 3);
        Assert.assertEquals(getBoundCheckIndexes(graph).size(), 1);
    }

    public static void rangeCheckElimination23(int[] array, int i, int l, boolean flag) {
        int j = 1;
        for (int k = 0; k < 2; k++) {
            j *= 2;
        }
        if (l > j) {
            if (l > 0) { // this is proven true after 1st iteration of conditional elimination
                array[i + 1] = 3;
                array[i + 3] = 3;
            }
            array[i + 2] = 0;
            if (flag) {
                array[i + 4] = 2;
                array[i + 3] = 1;
            } else {
                array[i] = 2;
                array[i + 1] = 1;
            }
        }
    }

    @Test
    public void test23() {
        afterRangeCheckElimination = graph -> {
            final ChecksAndAccesses checksAndAccesses = new ChecksAndAccesses(graph);
            checksAndAccesses.verifyAccessDependsOnGuards(1, 1);
            checksAndAccesses.verifyAccessDependsOnGuards(3, 1, 4);
            checksAndAccesses.verifyAccessDependsOnGuards(2, 1, 4);
            checksAndAccesses.verifyAccessDependsOnGuards(4, 4);
            checksAndAccesses.verifyAccessDependsOnGuards(3, 1, 4);
            checksAndAccesses.verifyAccessDependsOnGuards(0, 0);
            checksAndAccesses.verifyAccessDependsOnGuards(1, 0, 4);
        };
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("rangeCheckElimination23"));
        Assert.assertEquals(getBoundCheckCount(graph), 3);
        checkBounds(graph, 0, 1, 4);
        Assert.assertEquals(getBoundCheckIndexes(graph).size(), 1);
    }
}
