/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.loop.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.loop.phases.NonCountedStripMiningPhase;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.SafepointNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.loop.BasicInductionVariable;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.options.OptionValues;

public class NonCountedStripMiningTest extends GraalCompilerTest {

    static class Node {
        int val;
        Node next;
    }

    private static Node createList(int length) {
        Node head = new Node();
        for (int i = 1; i < length; i++) {
            Node newHead = new Node();
            newHead.val = i;
            newHead.next = head;
            head = newHead;
        }
        return head;
    }

    public static int snippet01(Node head) {
        Node cur = head;
        int res = 0;
        while (cur != null) {
            res += cur.val;
            cur = cur.next;
        }
        return res;
    }

    public static int snippet02(Node head, int start) {
        Node cur = head;
        int res = 0;
        int candidateCounterIV = start;
        while (cur != null) {
            res += cur.val;
            cur = cur.next;
            // reuse the IV in a fixed node that causes fixed control flow inside the loop
            // after floating reads
            GraalDirectives.sideEffect(candidateCounterIV++);
        }
        return res;
    }

    public static int snippet03(Node head, int start) {
        Node cur = head;
        int res = 0;
        int candidateCounterIV = start;
        while (cur != null) {
            res += cur.val;
            cur = cur.next;
            GraalDirectives.sideEffect(candidateCounterIV--);
        }
        return res;
    }

    public static int snippet04(Node head, int start) {
        Node cur = head;
        int res = 0;
        int candidateCounterIV = start;
        while (cur != null) {
            res += cur.val;
            cur = cur.next;
            GraalDirectives.sideEffect(candidateCounterIV += 4);
        }
        return res;
    }

    public static int snippet05(Node head, int start) {
        Node cur = head;
        int res = 0;
        int candidateCounterIV = start;
        while (cur != null) {
            res += cur.val;
            cur = cur.next;
            GraalDirectives.sideEffect(candidateCounterIV -= 32);
        }
        return res;
    }

    public static long failedIVReuseStripLimitSnippet(Node head) {
        long sum = 0;
        long wide = 0;
        Node current = head;
        while (current != null) {
            sum += wide + current.val;
            wide += 1_000_000L;
            current = current.next;
        }
        return sum;
    }

    private static OptionValues stripMiningOptions(OptionValues initialOptions) {
        return new OptionValues(initialOptions,
                        NonCountedStripMiningPhase.Options.NonCountedStripMiningMinFrequency, 0D,
                        NonCountedStripMiningPhase.Options.NonCountedStripMiningInnerLoopTrips, 4,
                        NonCountedStripMiningPhase.Options.NonCountedStripMiningIgnoreSmallLoops, false,
                        GraalOptions.PartialUnroll, false,
                        GraalOptions.LoopPeeling, false);
    }

    private static void assertSafepointPlacement(StructuredGraph graph, LoopsData loopsData) {
        Assert.assertEquals(2, graph.getNodes(LoopBeginNode.TYPE).count());
        int safepoints = 0;
        for (SafepointNode safepoint : graph.getNodes().filter(SafepointNode.class)) {
            if (safepoint.predecessor() instanceof LoopExitNode) {
                // skip loop exit safepoints
                continue;
            }
            safepoints++;
            FixedNode next = safepoint.next();
            Assert.assertTrue("Must be followed by loop end", next instanceof LoopEndNode);
            Loop loop = getLoop(loopsData, ((LoopEndNode) next).loopBegin());
            Assert.assertEquals(1, loop.getCFGLoop().getDepth());
            Assert.assertEquals("Only one safepoint should remain after strip mining", 1, safepoints);
        }
    }

    private static Loop getLoop(LoopsData loopsData, LoopBeginNode loopBegin) {
        Loop loop = loopsData.loop(loopBegin);
        if (loop != null) {
            return loop;
        }
        throw GraalError.shouldNotReachHere("Must find loop"); // ExcludeFromJacocoGeneratedReport
    }

    @Test
    public void test01() {
        test(stripMiningOptions(getInitialOptions()), "snippet01", createList(10));
    }

    @Test
    public void test02() {
        OptionValues options = stripMiningOptions(getInitialOptions());
        for (int start : new int[]{0, 1, -1, 42, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            test(options, "snippet02", createList(10), start);
        }
        test(options, "snippet02", createList(1), 0);
        test(options, "snippet02", createList(0), 0);
        StructuredGraph graph = parseEager("snippet02", AllowAssumptions.NO, options);
        createSuites(options).getHighTier().apply(graph, getDefaultHighTierContext());
        createSuites(options).getMidTier().apply(graph, getDefaultMidTierContext());
        LoopsData loopsData = getDefaultMidTierContext().getLoopsDataProvider().getLoopsData(graph);
        loopsData.detectCountedLoops();
        Loop loop = loopsData.countedLoops().get(0);
        Assert.assertEquals(1, basicInductionVariableCount(loop));
        assertSafepointPlacement(graph, loopsData);
    }

    @Test
    public void test03() {
        testCandidateCounter("snippet03");
    }

    @Test
    public void test04() {
        testCandidateCounter("snippet04");
    }

    @Test
    public void test05() {
        testCandidateCounter("snippet05");
    }

    @Test
    public void testFailedIVReuseDoesNotChangeStripLimit() {
        OptionValues options = new OptionValues(stripMiningOptions(getInitialOptions()),
                        NonCountedStripMiningPhase.Options.NonCountedStripMiningInnerLoopTrips, 1024,
                        NonCountedStripMiningPhase.Options.NonCountedStripMiningReuseIVs, true);

        StructuredGraph graph = parseEager("failedIVReuseStripLimitSnippet", AllowAssumptions.NO, options);
        createSuites(options).getHighTier().apply(graph, getDefaultHighTierContext());
        createSuites(options).getMidTier().apply(graph, getDefaultMidTierContext());
        LoopBeginNode inner = graph.getNodes(LoopBeginNode.TYPE).filter(node -> ((LoopBeginNode) node).isNonCountedStripMinedInner()).first();
        Assert.assertNotNull(inner);
        Assert.assertEquals("rejected long IV must not change the inner strip limit", 1024, inner.getStripMinedLimit());
    }

    private void testCandidateCounter(String snippet) {
        OptionValues options = stripMiningOptions(getInitialOptions());
        for (int start : new int[]{0, 1, -1, 42, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            test(options, snippet, createList(10), start);
        }
        test(options, snippet, createList(1), 0);
        test(options, snippet, createList(0), 0);
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.NO, options);
        createSuites(options).getHighTier().apply(graph, getDefaultHighTierContext());
        createSuites(options).getMidTier().apply(graph, getDefaultMidTierContext());
        LoopsData loopsData = getDefaultMidTierContext().getLoopsDataProvider().getLoopsData(graph);
        loopsData.detectCountedLoops();
        Loop loop = loopsData.countedLoops().get(0);
        Assert.assertEquals(1, basicInductionVariableCount(loop));
        assertSafepointPlacement(graph, loopsData);
    }

    private static int basicInductionVariableCount(Loop loop) {
        int count = 0;
        for (InductionVariable inductionVariable : loop.getInductionVariables().getValues()) {
            if (inductionVariable instanceof BasicInductionVariable) {
                count++;
            }
        }
        return count;
    }
}
