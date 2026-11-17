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
package jdk.graal.compiler.duplication.test;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.duplication.phases.simulation.SimulationCFGTraversal;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph.RecursiveVisitor;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

public class DuplicationSimulationTraversalTest extends GraalCompilerTest {
    public static int nestedSnippet(int x, int y) {
        while (x > 0) {
            if (y < 0) {
                return x;
            }
            if (x > y) {
                y += x;
            } else {
                y -= x;
            }
            x--;
        }
        return y;
    }

    public static int wideSnippet(int x, int y) {
        switch (x) {
            case 0:
                return y + 0;
            case 1:
                return y + 1;
            case 2:
                return y + 2;
            case 3:
                return y + 3;
            case 4:
                return y + 4;
            case 5:
                return y + 5;
            case 6:
                return y + 6;
            case 7:
                return y + 7;
            case 8:
                return y + 8;
            case 9:
                return y + 9;
            case 10:
                return y + 10;
            case 11:
                return y + 11;
            case 12:
                return y + 12;
            case 13:
                return y + 13;
            case 14:
                return y + 14;
            case 15:
                return y + 15;
            case 16:
                return y + 16;
            case 17:
                return y + 17;
            case 18:
                return y + 18;
            case 19:
                return y + 19;
            case 20:
                return y + 20;
            case 21:
                return y + 21;
            case 22:
                return y + 22;
            case 23:
                return y + 23;
            default:
                return y - 1;
        }
    }

    private ControlFlowGraph cfg(String snippet) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        return ControlFlowGraph.newBuilder(graph).connectBlocks(true).computeDominators(true).computePostdominators(true).build();
    }

    private static class RecordingVisitor implements RecursiveVisitor<HIRBlock> {
        final List<Integer> events = new ArrayList<>();

        @Override
        public HIRBlock enter(HIRBlock block) {
            events.add(block.getId() + 1);
            return block;
        }

        @Override
        public void exit(HIRBlock block, HIRBlock value) {
            Assert.assertSame(block, value);
            events.add(-(block.getId() + 1));
        }
    }

    /**
     * Recursive reference for the original postdominator-last, reverse-sibling traversal order.
     */
    private static void visitReference(RecordingVisitor visitor, HIRBlock block, int startDepth, int maxDepth) {
        HIRBlock value = visitor.enter(block);
        if (block.getDominatorDepth() - startDepth <= maxDepth) {
            HIRBlock postdominator = block.getPostdominator();
            List<HIRBlock> children = new ArrayList<>();
            for (HIRBlock child = block.getFirstDominated(); child != null; child = child.getDominatedSibling()) {
                if (child != postdominator) {
                    children.add(child);
                }
            }
            for (int i = children.size() - 1; i >= 0; i--) {
                visitReference(visitor, children.get(i), startDepth, maxDepth);
            }
            if (postdominator != null && postdominator.getDominator() == block) {
                visitReference(visitor, postdominator, startDepth, maxDepth);
            }
        }
        visitor.exit(block, value);
    }

    @Test
    public void testOrderBoundsAndReuse() {
        SimulationCFGTraversal<HIRBlock> traversal = new SimulationCFGTraversal<>();
        for (String snippet : new String[]{"wideSnippet", "nestedSnippet"}) {
            ControlFlowGraph cfg = cfg(snippet);
            for (HIRBlock start : cfg.getBlocks()) {
                for (int maxDepth : new int[]{-1, 0, 1, 2, Integer.MAX_VALUE}) {
                    RecordingVisitor expected = new RecordingVisitor();
                    visitReference(expected, start, start.getDominatorDepth(), maxDepth);
                    RecordingVisitor actual = new RecordingVisitor();
                    traversal.visitDominatorTreeBounded(actual, start, start.getDominatorDepth(), maxDepth);
                    Assert.assertEquals(expected.events, actual.events);
                }
            }
        }
    }

    @Test
    public void testReuseAfterException() {
        ControlFlowGraph cfg = cfg("wideSnippet");
        SimulationCFGTraversal<HIRBlock> traversal = new SimulationCFGTraversal<>();
        RuntimeException failure = new RuntimeException();
        try {
            traversal.visitDominatorTreeBounded(new RecordingVisitor() {
                @Override
                public HIRBlock enter(HIRBlock block) {
                    if (block != cfg.getStartBlock()) {
                        throw failure;
                    }
                    return super.enter(block);
                }
            }, cfg.getStartBlock(), 0, Integer.MAX_VALUE);
            Assert.fail("Expected the visitor to throw");
        } catch (RuntimeException e) {
            Assert.assertSame(failure, e);
        }
        RecordingVisitor expected = new RecordingVisitor();
        visitReference(expected, cfg.getStartBlock(), 0, Integer.MAX_VALUE);
        RecordingVisitor actual = new RecordingVisitor();
        traversal.visitDominatorTreeBounded(actual, cfg.getStartBlock(), 0, Integer.MAX_VALUE);
        Assert.assertEquals(expected.events, actual.events);
    }
}
