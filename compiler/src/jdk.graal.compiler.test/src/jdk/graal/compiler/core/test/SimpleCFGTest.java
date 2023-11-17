/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.PermanentBailoutException;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Builder;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.ProfileData.BranchProbabilityData;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.debug.ControlFlowAnchored;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;
import org.junit.Assert;
import org.junit.Test;

public class SimpleCFGTest extends GraalCompilerTest {

    private static void dumpGraph(final StructuredGraph graph) {
        DebugContext debug = graph.getDebug();
        debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph");
    }

    @NodeInfo(allowedUsageTypes = InputType.Anchor, cycles = NodeCycles.CYCLES_0, size = NodeSize.SIZE_0)
    static class SingleSplit extends ControlSplitNode implements ControlFlowAnchored {
        public static final NodeClass<SingleSplit> TYPE = NodeClass.create(SingleSplit.class);

        @Successor AbstractBeginNode singleSuccessor;

        protected SingleSplit() {
            super(TYPE, StampFactory.forVoid());
        }

        void setSingleSuccessor(AbstractBeginNode node) {
            updatePredecessor(singleSuccessor, node);
            singleSuccessor = node;
        }

        @Override
        public double probability(AbstractBeginNode successor) {
            return getProfileData().getDesignatedSuccessorProbability();
        }

        @Override
        public int getSuccessorCount() {
            return 1;
        }

        @Override
        public boolean setProbability(AbstractBeginNode successor, BranchProbabilityData profileData) {
            throw new PermanentBailoutException("There is only one successor, so probability cannot change");
        }

        @Override
        public BranchProbabilityData getProfileData() {
            return BranchProbabilityNode.ALWAYS_TAKEN_PROFILE;
        }

        @Override
        public AbstractBeginNode getPrimarySuccessor() {
            return singleSuccessor;
        }

    }

    static int single() {
        GraalDirectives.deoptimizeAndInvalidate();
        return -1;
    }

    @Test
    public void testSingleSplit() {
        StructuredGraph g = parseEager(getResolvedJavaMethod("single"), AllowAssumptions.NO);
        FixedNode next = g.start().next();
        g.start().setNext(null);
        SingleSplit s = g.add(new SingleSplit());
        AbstractBeginNode b = g.add(new BeginNode());
        b.setNext(next);
        s.setSingleSuccessor(b);
        g.start().setNext(s);

        g.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, g, "after build");
        ControlFlowGraph.newBuilder(g).connectBlocks(true).computeLoops(true).computeDominators(true).computePostdominators(true).computeFrequency(true).build();
    }

    static int singleLoop(int end) {
        int i = 0;
        while (true) {
            switch (i) {
                default:
            }
            if (i == end) {
                break;
            }
            i++;
            continue;
        }
        return i;
    }

    @Test
    public void testSingleSplitLoop() {
        StructuredGraph g = parseEager(getResolvedJavaMethod("singleLoop"), AllowAssumptions.NO);
        g.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, g, "after build");
        ControlFlowGraph.newBuilder(g).connectBlocks(true).computeLoops(true).computeDominators(true).computePostdominators(true).computeFrequency(true).build();
    }

    static int foo(int a, int b) {
        int res = 0;
        int i = 0;
        while (true) {
            if (i >= a) {
                break;
            }
            if (i == 123) {
                GraalDirectives.deoptimizeAndInvalidate();
            }
            if (i == 126) {
                GraalDirectives.deoptimizeAndInvalidate();
            }
            res += i;
            i++;
        }
        if (b == 42) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        return res;
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        super.checkHighTierGraph(graph);
        if (!modify) {
            return;
        }
        for (LoopExitNode lex : graph.getNodes().filter(LoopExitNode.class)) {
            lex.setStateAfter(lex.loopBegin().stateAfter());
        }
    }

    private boolean modify = false;

    @Test
    public void testFoo() {
        modify = true;
        test("foo", 12, 12);
        modify = false;
    }

    @Test
    public void testImplies() {
        OptionValues options = getInitialOptions();
        DebugContext debug = new Builder(options, new GraalDebugHandlersFactory(getSnippetReflection())).build();
        StructuredGraph graph = new StructuredGraph.Builder(options, debug, AllowAssumptions.YES).build();

        EndNode trueEnd = graph.add(new EndNode());
        EndNode falseEnd = graph.add(new EndNode());

        AbstractBeginNode trueBegin = graph.add(new BeginNode());
        trueBegin.setNext(trueEnd);
        AbstractBeginNode falseBegin = graph.add(new BeginNode());
        falseBegin.setNext(falseEnd);

        IfNode ifNode = graph.add(new IfNode(null, trueBegin, falseBegin, BranchProbabilityData.unknown()));
        graph.start().setNext(ifNode);

        AbstractMergeNode merge = graph.add(new MergeNode());
        merge.addForwardEnd(trueEnd);
        merge.addForwardEnd(falseEnd);
        ReturnNode returnNode = graph.add(new ReturnNode(null));
        merge.setNext(returnNode);

        dumpGraph(graph);

        ControlFlowGraph cfg = ControlFlowGraph.newBuilder(graph).connectBlocks(true).computeLoops(true).computeDominators(true).computePostdominators(true).computeFrequency(true).build();

        HIRBlock[] blocks = cfg.getBlocks();
        // check number of blocks
        assertDeepEquals(4, blocks.length);

        // check block - node assignment
        assertDeepEquals(blocks[0], cfg.blockFor(graph.start()));
        assertDeepEquals(blocks[0], cfg.blockFor(ifNode));
        assertDeepEquals(blocks[1], cfg.blockFor(trueBegin));
        assertDeepEquals(blocks[1], cfg.blockFor(trueEnd));
        assertDeepEquals(blocks[2], cfg.blockFor(falseBegin));
        assertDeepEquals(blocks[2], cfg.blockFor(falseEnd));
        assertDeepEquals(blocks[3], cfg.blockFor(merge));
        assertDeepEquals(blocks[3], cfg.blockFor(returnNode));

        // check dominators
        assertDominator(blocks[0], null);
        assertDominator(blocks[1], blocks[0]);
        assertDominator(blocks[2], blocks[0]);
        assertDominator(blocks[3], blocks[0]);

        // check dominated
        assertDominatedSize(blocks[0], 3);
        assertDominatedSize(blocks[1], 0);
        assertDominatedSize(blocks[2], 0);
        assertDominatedSize(blocks[3], 0);

        // check postdominators
        assertPostdominator(blocks[0], blocks[3]);
        assertPostdominator(blocks[1], blocks[3]);
        assertPostdominator(blocks[2], blocks[3]);
        assertPostdominator(blocks[3], null);
    }

    public static void assertDominator(HIRBlock block, HIRBlock expectedDominator) {
        Assert.assertEquals("dominator of " + block, expectedDominator, block.getDominator());
    }

    public static void assertDominatedSize(HIRBlock block, int size) {
        int count = 0;
        HIRBlock domChild = block.getFirstDominated();
        while (domChild != null) {
            count++;
            domChild = domChild.getDominatedSibling();
        }
        Assert.assertEquals("number of dominated blocks of " + block, size, count);
    }

    public static void assertPostdominator(HIRBlock block, HIRBlock expectedPostdominator) {
        Assert.assertEquals("postdominator of " + block, expectedPostdominator, block.getPostdominator());
    }

}
