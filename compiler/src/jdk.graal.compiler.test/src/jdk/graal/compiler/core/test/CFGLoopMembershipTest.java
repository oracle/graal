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
package jdk.graal.compiler.core.test;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.nodes.GraphState.GuardsStage;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

@RunWith(Parameterized.class)
public class CFGLoopMembershipTest extends GraalCompilerTest {

    @Parameters(name = "afterFSA={0}")
    public static Object[][] data() {
        return new Object[][]{{false}, {true}};
    }

    @Parameter public boolean afterFSA;

    public static int nestedAndSiblingLoops(int n) {
        int result = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                result += GraalDirectives.opaque(j);
            }
            for (int j = i; j < n; j++) {
                result += GraalDirectives.opaque(j);
            }
        }
        for (int i = 0; i < n; i++) {
            result += GraalDirectives.opaque(i);
        }
        return result;
    }

    public static int multipleBackedges(int n) {
        int result = 0;
        int i = 0;
        while (i < n) {
            if ((i & 1) == 0) {
                result += GraalDirectives.opaque(i++);
                continue;
            }
            result -= GraalDirectives.opaque(i++);
        }
        return result;
    }

    public static int earlyExits(int n, int stop) {
        int result = 0;
        outer: for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (j == stop) {
                    break;
                }
                if (i + j == stop) {
                    break outer;
                }
                if (i - j == stop) {
                    return result;
                }
                result += GraalDirectives.opaque(j);
            }
        }
        return result;
    }

    public static int deoptExit(int n, int stop) {
        int result = 0;
        for (int i = 0; i < n; i++) {
            if (i == stop) {
                GraalDirectives.deoptimize();
            }
            result += GraalDirectives.opaque(i);
        }
        return result;
    }

    private ControlFlowGraph checkMembership(String snippet, int expectedLoops) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.NO);
        // Exercise both CFG loop-membership policies without requiring the lowering pipeline.
        if (afterFSA) {
            graph.getGraphState().setGuardsStage(GuardsStage.AFTER_FSA);
            // Late proxy insertion can cache a pre-FSA CFG during parsing.
            graph.clearLastCFG();
        }
        ControlFlowGraph cfg = ControlFlowGraph.newBuilder(graph).connectBlocks(true).computeLoops(true).computeDominators(true).build();
        Assert.assertEquals(expectedLoops, cfg.getLoops().size());
        for (CFGLoop<HIRBlock> loop : cfg.getLoops()) {
            Assert.assertTrue(loop.containsBlock(loop.getHeader()));
            Assert.assertFalse(loop.containsBlock(cfg.getStartBlock()));
            for (HIRBlock block : cfg.getBlocks()) {
                Assert.assertEquals(loop + ": " + block, loop.getBlocks().contains(block), loop.containsBlock(block));
            }
        }
        return cfg;
    }

    @Test
    public void testNestedAndSiblingLoops() {
        ControlFlowGraph cfg = checkMembership("nestedAndSiblingLoops", 4);
        int nestedLoops = 0;
        for (CFGLoop<HIRBlock> loop : cfg.getLoops()) {
            if (loop.getParent() != null) {
                nestedLoops++;
                Assert.assertTrue(loop.getParent().containsBlock(loop.getHeader()));
                Assert.assertFalse(loop.containsBlock(loop.getParent().getHeader()));
            }
        }
        Assert.assertEquals(2, nestedLoops);
    }

    @Test
    public void testMultipleBackedges() {
        ControlFlowGraph cfg = checkMembership("multipleBackedges", 1);
        Assert.assertTrue(cfg.getLoops().getFirst().numBackedges() > 1);
    }

    @Test
    public void testEarlyExits() {
        checkMembership("earlyExits", 2);
    }

    @Test
    public void testConservativeDeoptExit() {
        ControlFlowGraph cfg = checkMembership("deoptExit", 1);
        CFGLoop<HIRBlock> loop = cfg.getLoops().getFirst();
        int includedNaturalExits = 0;
        for (HIRBlock exit : loop.getNaturalExits()) {
            if (loop.containsBlock(exit)) {
                includedNaturalExits++;
            }
        }
        Assert.assertEquals(afterFSA ? 0 : 1, includedNaturalExits);
    }
}
