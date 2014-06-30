/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.test;

import static com.oracle.graal.compiler.common.GraalOptions.*;
import static org.junit.Assert.*;
import java.util.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.options.*;
import com.oracle.graal.options.OptionValue.OverrideScope;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.inlining.*;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.phases.schedule.SchedulePhase.MemoryScheduling;
import com.oracle.graal.phases.schedule.SchedulePhase.SchedulingStrategy;
import com.oracle.graal.phases.tiers.*;

/**
 * In these test the FrameStates are explicitly cleared out, so that the scheduling of
 * FloatingReadNodes depends solely on the scheduling algorithm. The FrameStates normally keep the
 * FloatingReadNodes above a certain point, so that they (most of the time...) magically do the
 * right thing.
 *
 * The scheduling shouldn't depend on FrameStates, which is tested by this class.
 */
public class MemoryScheduleTest extends GraphScheduleTest {

    private static enum TestMode {
        WITH_FRAMESTATES,
        WITHOUT_FRAMESTATES,
        INLINED_WITHOUT_FRAMESTATES
    }

    public static class Container {

        public int a;
        public int b;
        public int c;

        public Object obj;
    }

    private static final Container container = new Container();
    private static final List<Container> containerList = new ArrayList<>();

    /**
     * In this test the read should be scheduled before the write.
     */
    public static int testSimpleSnippet() {
        try {
            return container.a;
        } finally {
            container.a = 15;
        }
    }

    @Test
    public void testSimple() {
        for (TestMode mode : TestMode.values()) {
            SchedulePhase schedule = getFinalSchedule("testSimpleSnippet", mode);
            StructuredGraph graph = schedule.getCFG().graph;
            assertReadAndWriteInSameBlock(schedule, true);
            assertOrderedAfterSchedule(schedule, graph.getNodes().filter(FloatingReadNode.class).first(), graph.getNodes().filter(WriteNode.class).first());
        }
    }

    /**
     * In this case the read should be scheduled in the first block.
     */
    public static int testSplit1Snippet(int a) {
        try {
            return container.a;
        } finally {
            if (a < 0) {
                container.a = 15;
            } else {
                container.b = 15;
            }
        }
    }

    @Test
    public void testSplit1() {
        for (TestMode mode : TestMode.values()) {
            SchedulePhase schedule = getFinalSchedule("testSplit1Snippet", mode);
            assertReadWithinStartBlock(schedule, true);
            assertReadWithinAllReturnBlocks(schedule, false);
        }
    }

    /**
     * Here the read should float to the end.
     */
    public static int testSplit2Snippet(int a) {
        try {
            return container.a;
        } finally {
            if (a < 0) {
                container.c = 15;
            } else {
                container.b = 15;
            }
            container.obj = null;
        }
    }

    @Test
    public void testSplit2() {
        SchedulePhase schedule = getFinalSchedule("testSplit2Snippet", TestMode.WITHOUT_FRAMESTATES);
        assertReadWithinStartBlock(schedule, false);
        assertReadWithinAllReturnBlocks(schedule, true);
    }

    /**
     * Here the read should not float to the end.
     */
    public static int testLoop1Snippet(int a, int b) {
        try {
            return container.a;
        } finally {
            for (int i = 0; i < a; i++) {
                if (b < 0) {
                    container.b = 10;
                } else {
                    container.a = 15;
                }
            }
        }
    }

    @Test
    public void testLoop1() {
        SchedulePhase schedule = getFinalSchedule("testLoop1Snippet", TestMode.WITHOUT_FRAMESTATES);
        assertDeepEquals(6, schedule.getCFG().getBlocks().size());
        assertReadWithinStartBlock(schedule, true);
        assertReadWithinAllReturnBlocks(schedule, false);
    }

    /**
     * Here the read should float to the end.
     */
    public static int testLoop2Snippet(int a, int b) {
        try {
            return container.a;
        } finally {
            for (int i = 0; i < a; i++) {
                if (b < 0) {
                    container.b = 10;
                } else {
                    container.c = 15;
                }
            }
        }
    }

    @Test
    public void testLoop2() {
        SchedulePhase schedule = getFinalSchedule("testLoop2Snippet", TestMode.WITHOUT_FRAMESTATES);
        assertDeepEquals(6, schedule.getCFG().getBlocks().size());
        assertReadWithinStartBlock(schedule, false);
        assertReadWithinAllReturnBlocks(schedule, true);
    }

    /**
     * Here the read should float out of the loop.
     */
    public static int testLoop3Snippet(int a) {
        int j = 0;
        for (int i = 0; i < a; i++) {
            if (i - container.a == 0) {
                break;
            }
            j++;
        }
        return j;
    }

    @Test
    public void testLoop3() {
        SchedulePhase schedule = getFinalSchedule("testLoop3Snippet", TestMode.WITHOUT_FRAMESTATES);
        assertDeepEquals(6, schedule.getCFG().getBlocks().size());
        assertReadWithinStartBlock(schedule, true);
        assertReadWithinAllReturnBlocks(schedule, false);
    }

    public String testStringReplaceSnippet(String input) {
        return input.replace('a', 'b');
    }

    @Test
    public void testStringReplace() {
        getFinalSchedule("testStringReplaceSnippet", TestMode.INLINED_WITHOUT_FRAMESTATES);
        test("testStringReplaceSnippet", "acbaaa");
    }

    /**
     * Here the read should float out of the loop.
     */
    public static int testLoop5Snippet(int a, int b, MemoryScheduleTest obj) {
        int ret = 0;
        int bb = b;
        for (int i = 0; i < a; i++) {
            ret = obj.hash;
            if (a > 10) {
                bb++;
            } else {
                bb--;
            }
            ret = ret / 10;
        }
        return ret + bb;
    }

    @Test
    public void testLoop5() {
        SchedulePhase schedule = getFinalSchedule("testLoop5Snippet", TestMode.WITHOUT_FRAMESTATES);
        assertDeepEquals(10, schedule.getCFG().getBlocks().size());
        assertReadWithinStartBlock(schedule, false);
        assertReadWithinAllReturnBlocks(schedule, false);
    }

    /**
     * Here the read should float to the end (into the same block as the return).
     */
    public static int testArrayCopySnippet(Integer intValue, char[] a, char[] b, int len) {
        System.arraycopy(a, 0, b, 0, len);
        return intValue.intValue();
    }

    @Test
    public void testArrayCopy() {
        SchedulePhase schedule = getFinalSchedule("testArrayCopySnippet", TestMode.INLINED_WITHOUT_FRAMESTATES);
        StructuredGraph graph = schedule.getCFG().getStartBlock().getBeginNode().graph();
        assertDeepEquals(1, graph.getNodes(ReturnNode.class).count());
        ReturnNode ret = graph.getNodes(ReturnNode.class).first();
        assertTrue(ret.result() + " should be a FloatingReadNode", ret.result() instanceof FloatingReadNode);
        assertDeepEquals(schedule.getCFG().blockFor(ret), schedule.getCFG().blockFor(ret.result()));
        assertReadWithinAllReturnBlocks(schedule, true);
    }

    /**
     * Here the read should not float to the end.
     */
    public static int testIfRead1Snippet(int a) {
        int res = container.a;
        if (a < 0) {
            container.a = 10;
        }
        return res;
    }

    @Test
    public void testIfRead1() {
        SchedulePhase schedule = getFinalSchedule("testIfRead1Snippet", TestMode.WITHOUT_FRAMESTATES);
        assertDeepEquals(3, schedule.getCFG().getBlocks().size());
        assertReadWithinStartBlock(schedule, true);
        assertReadAndWriteInSameBlock(schedule, false);
    }

    /**
     * Here the read should float in the else block.
     */
    public static int testIfRead2Snippet(int a) {
        int res = 0;
        if (a < 0) {
            container.a = 10;
        } else {
            res = container.a;
        }
        return res;
    }

    @Test
    public void testIfRead2() {
        SchedulePhase schedule = getFinalSchedule("testIfRead2Snippet", TestMode.WITHOUT_FRAMESTATES);
        assertDeepEquals(3, schedule.getCFG().getBlocks().size());
        assertDeepEquals(1, schedule.getCFG().graph.getNodes().filter(FloatingReadNode.class).count());
        assertReadWithinStartBlock(schedule, false);
        assertReadWithinAllReturnBlocks(schedule, false);
        assertReadAndWriteInSameBlock(schedule, false);
    }

    /**
     * Here the read should float to the end, right before the write.
     */
    public static int testIfRead3Snippet(int a) {
        if (a < 0) {
            container.a = 10;
        }
        int res = container.a;
        container.a = 20;
        return res;
    }

    @Test
    public void testIfRead3() {
        SchedulePhase schedule = getFinalSchedule("testIfRead3Snippet", TestMode.WITHOUT_FRAMESTATES);
        assertDeepEquals(4, schedule.getCFG().getBlocks().size());
        assertReadWithinStartBlock(schedule, false);
        assertReadWithinAllReturnBlocks(schedule, true);
    }

    /**
     * Here the read should be just in the if branch (with the write).
     */
    public static int testIfRead4Snippet(int a) {
        if (a > 0) {
            int res = container.a;
            container.a = 0x20;
            return res;
        } else {
            return 0x10;
        }
    }

    @Test
    public void testIfRead4() {
        SchedulePhase schedule = getFinalSchedule("testIfRead4Snippet", TestMode.WITHOUT_FRAMESTATES);
        assertDeepEquals(3, schedule.getCFG().getBlocks().size());
        assertReadWithinStartBlock(schedule, false);
        assertReadWithinAllReturnBlocks(schedule, false);
        assertReadAndWriteInSameBlock(schedule, true);
    }

    /**
     * Here the read should float to the end.
     */
    public static int testIfRead5Snippet(int a) {
        if (a < 0) {
            container.a = 10;
        }
        return container.a;
    }

    @Test
    public void testIfRead5() {
        SchedulePhase schedule = getFinalSchedule("testIfRead5Snippet", TestMode.WITHOUT_FRAMESTATES);
        assertDeepEquals(4, schedule.getCFG().getBlocks().size());
        assertReadWithinStartBlock(schedule, false);
        assertReadWithinAllReturnBlocks(schedule, true);
        assertReadAndWriteInSameBlock(schedule, false);
    }

    public static int testAntiDependencySnippet(int a) {
        /*
         * This read must not be scheduled after the following write.
         */
        int res = container.a;
        container.a = 10;

        /*
         * Add some more basic blocks.
         */
        if (a < 0) {
            container.b = 20;
        }
        container.c = 30;
        return res;
    }

    @Test
    public void testAntiDependency() {
        SchedulePhase schedule = getFinalSchedule("testAntiDependencySnippet", TestMode.WITHOUT_FRAMESTATES);
        assertDeepEquals(4, schedule.getCFG().getBlocks().size());
        assertReadBeforeAllWritesInStartBlock(schedule);
    }

    /**
     * testing scheduling within a block.
     */
    public static int testBlockScheduleSnippet() {
        int res = 0;
        container.a = 0x00;
        container.a = 0x10;
        container.a = 0x20;
        container.a = 0x30;
        container.a = 0x40;
        res = container.a;
        container.a = 0x50;
        container.a = 0x60;
        container.a = 0x70;
        return res;
    }

    @Test
    public void testBlockSchedule() {
        SchedulePhase schedule = getFinalSchedule("testBlockScheduleSnippet", TestMode.WITHOUT_FRAMESTATES);
        StructuredGraph graph = schedule.getCFG().graph;
        NodeIterable<WriteNode> writeNodes = graph.getNodes().filter(WriteNode.class);

        assertDeepEquals(1, schedule.getCFG().getBlocks().size());
        assertDeepEquals(8, writeNodes.count());
        assertDeepEquals(1, graph.getNodes().filter(FloatingReadNode.class).count());

        FloatingReadNode read = graph.getNodes().filter(FloatingReadNode.class).first();

        WriteNode[] writes = new WriteNode[8];
        int i = 0;
        for (WriteNode n : writeNodes) {
            writes[i] = n;
            i++;
        }
        assertOrderedAfterSchedule(schedule, writes[4], read);
        assertOrderedAfterSchedule(schedule, read, writes[5]);
        for (int j = 0; j < 7; j++) {
            assertOrderedAfterSchedule(schedule, writes[j], writes[j + 1]);
        }
    }

    /**
     * read should move inside the loop (out of loop is disabled).
     */
    public static int testBlockSchedule2Snippet(int value) {
        int res = 0;

        container.a = value;
        for (int i = 0; i < 100; i++) {
            if (i == 10) {
                return container.a;
            }
            res += i;
        }
        return res;
    }

    @Test
    public void testBlockSchedule2() {
        SchedulePhase schedule = getFinalSchedule("testBlockSchedule2Snippet", TestMode.WITHOUT_FRAMESTATES, MemoryScheduling.OPTIMAL, SchedulingStrategy.LATEST);
        assertReadWithinStartBlock(schedule, false);
        assertReadWithinAllReturnBlocks(schedule, false);
        assertReadAndWriteInSameBlock(schedule, false);
    }

    /*
     * read of field a should be in first block, read of field b in loop begin block
     */
    public static void testProxy1Snippet() {
        while (container.a < container.b) {
            container.b--;
        }
        container.b++;
    }

    @Test
    public void testProxy1() {
        SchedulePhase schedule = getFinalSchedule("testProxy1Snippet", TestMode.WITHOUT_FRAMESTATES);
        assertReadWithinStartBlock(schedule, true); // read of container.a should be in start block
        /*
         * read of container.b for increment operation should be in return block. TODO: not sure
         * though, could be replaced by read of container.b of the loop header...
         */
        assertReadWithinAllReturnBlocks(schedule, true);
    }

    public static void testProxy2Snippet() {
        while (container.a < container.b) {
            List<Container> list = new ArrayList<>(containerList);
            while (container.c < list.size()) {
                if (container.obj != null) {
                    return;
                }
                container.c++;
            }
            container.a = 0;
            container.b--;
        }
        container.b++;
    }

    @Test
    public void testProxy2() {
        SchedulePhase schedule = getFinalSchedule("testProxy2Snippet", TestMode.WITHOUT_FRAMESTATES);
        assertReadWithinStartBlock(schedule, false);
        assertReadWithinAllReturnBlocks(schedule, false);
    }

    private int hash = 0;
    private final char[] value = new char[3];

    public int testStringHashCodeSnippet() {
        int h = hash;
        if (h == 0 && value.length > 0) {
            char[] val = value;

            for (int i = 0; i < value.length; i++) {
                h = 31 * h + val[i];
            }
            hash = h;
        }
        return h;
    }

    @Test
    public void testStringHashCode() {
        SchedulePhase schedule = getFinalSchedule("testStringHashCodeSnippet", TestMode.WITHOUT_FRAMESTATES);
        assertReadWithinStartBlock(schedule, true);
        assertReadWithinAllReturnBlocks(schedule, false);

        hash = 0x1337;
        value[0] = 'a';
        value[1] = 'b';
        value[2] = 'c';
        test("testStringHashCodeSnippet");
    }

    public static int testLoop4Snippet(int count) {
        int[] a = new int[count];

        for (int i = 0; i < a.length; i++) {
            a[i] = i;
        }

        int i = 0;
        int iwrap = count - 1;
        int sum = 0;

        while (i < count) {
            sum += (a[i] + a[iwrap]) / 2;
            iwrap = i;
            i++;
        }
        return sum;
    }

    @Test
    public void testLoop4() {
        SchedulePhase schedule = getFinalSchedule("testLoop4Snippet", TestMode.WITHOUT_FRAMESTATES);
        assertReadWithinStartBlock(schedule, false);
        assertReadWithinAllReturnBlocks(schedule, false);
    }

    private void assertReadWithinAllReturnBlocks(SchedulePhase schedule, boolean withinReturnBlock) {
        StructuredGraph graph = schedule.getCFG().graph;
        assertTrue(graph.getNodes(ReturnNode.class).isNotEmpty());

        int withRead = 0;
        int returnBlocks = 0;
        for (ReturnNode returnNode : graph.getNodes(ReturnNode.class)) {
            Block block = schedule.getCFG().getNodeToBlock().get(returnNode);
            for (Node node : schedule.getBlockToNodesMap().get(block)) {
                if (node instanceof FloatingReadNode) {
                    withRead++;
                    break;
                }
            }
            returnBlocks++;
        }
        assertDeepEquals(withRead == returnBlocks, withinReturnBlock);
    }

    private void assertReadWithinStartBlock(SchedulePhase schedule, boolean withinStartBlock) {
        boolean readEncountered = false;
        for (Node node : schedule.getBlockToNodesMap().get(schedule.getCFG().getStartBlock())) {
            if (node instanceof FloatingReadNode) {
                readEncountered = true;
            }
        }
        assertDeepEquals(withinStartBlock, readEncountered);
    }

    private static void assertReadAndWriteInSameBlock(SchedulePhase schedule, boolean inSame) {
        StructuredGraph graph = schedule.getCFG().graph;
        FloatingReadNode read = graph.getNodes().filter(FloatingReadNode.class).first();
        WriteNode write = graph.getNodes().filter(WriteNode.class).first();
        assertTrue(!(inSame ^ schedule.getCFG().blockFor(read) == schedule.getCFG().blockFor(write)));
    }

    private static void assertReadBeforeAllWritesInStartBlock(SchedulePhase schedule) {
        boolean writeNodeFound = false;
        boolean readNodeFound = false;
        for (Node node : schedule.nodesFor(schedule.getCFG().getStartBlock())) {
            if (node instanceof FloatingReadNode) {
                assertTrue(!writeNodeFound);
                readNodeFound = true;
            } else if (node instanceof WriteNode) {
                writeNodeFound = true;
            }
        }
        assertTrue(readNodeFound);
    }

    private SchedulePhase getFinalSchedule(final String snippet, final TestMode mode) {
        return getFinalSchedule(snippet, mode, MemoryScheduling.OPTIMAL);
    }

    private SchedulePhase getFinalSchedule(final String snippet, final TestMode mode, final MemoryScheduling memsched) {
        return getFinalSchedule(snippet, mode, memsched, SchedulingStrategy.LATEST_OUT_OF_LOOPS);
    }

    private SchedulePhase getFinalSchedule(final String snippet, final TestMode mode, final MemoryScheduling memsched, final SchedulingStrategy schedulingStrategy) {
        final StructuredGraph graph = parse(snippet);
        try (Scope d = Debug.scope("FloatingReadTest", graph)) {
            try (OverrideScope s = OptionValue.override(OptScheduleOutOfLoops, schedulingStrategy == SchedulingStrategy.LATEST_OUT_OF_LOOPS, OptImplicitNullChecks, false)) {
                Assumptions assumptions = new Assumptions(false);
                HighTierContext context = new HighTierContext(getProviders(), assumptions, null, getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL);
                CanonicalizerPhase canonicalizer = new CanonicalizerPhase(true);
                canonicalizer.apply(graph, context);
                if (mode == TestMode.INLINED_WITHOUT_FRAMESTATES) {
                    new InliningPhase(canonicalizer).apply(graph, context);
                }
                new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
                if (mode == TestMode.WITHOUT_FRAMESTATES || mode == TestMode.INLINED_WITHOUT_FRAMESTATES) {
                    for (Node node : graph.getNodes()) {
                        if (node instanceof StateSplit) {
                            FrameState stateAfter = ((StateSplit) node).stateAfter();
                            if (stateAfter != null) {
                                ((StateSplit) node).setStateAfter(null);
                                GraphUtil.killWithUnusedFloatingInputs(stateAfter);
                            }
                        }
                    }
                }
                Debug.dump(graph, "after removal of framestates");

                new FloatingReadPhase().apply(graph);
                new RemoveValueProxyPhase().apply(graph);

                MidTierContext midContext = new MidTierContext(getProviders(), assumptions, getCodeCache().getTarget(), OptimisticOptimizations.ALL, graph.method().getProfilingInfo(), null);
                new GuardLoweringPhase().apply(graph, midContext);
                new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.MID_TIER).apply(graph, midContext);
                new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.LOW_TIER).apply(graph, midContext);

                SchedulePhase schedule = new SchedulePhase(schedulingStrategy, memsched);
                schedule.apply(graph);
                assertDeepEquals(1, graph.getNodes().filter(StartNode.class).count());
                return schedule;
            }
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }
}
