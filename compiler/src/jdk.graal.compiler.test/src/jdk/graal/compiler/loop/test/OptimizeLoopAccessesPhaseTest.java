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

import java.util.ListIterator;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.phases.MidTier;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.core.test.TestPhase;
import jdk.graal.compiler.loop.phases.OptimizeLoopAccessesPhase;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.memory.FloatingReadNode;
import jdk.graal.compiler.nodes.memory.MemoryPhiNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.tiers.Suites;

/// Tests replacement of a loop-carried memory dependency with a value phi.
public class OptimizeLoopAccessesPhaseTest extends GraalCompilerTest {

    /// Stores the value carried between loop iterations.
    static int value;

    /// Reads and writes the same field on every iteration to create a loop-carried memory state.
    public static int loopCarriedRead(int count) {
        int result = 0;
        for (int i = 0; i < count; i++) {
            result += value;
            value = i;
        }
        return result;
    }

    /// Inserts a structural assertion immediately after the phase under test.
    @Override
    protected Suites createSuites(OptionValues options) {
        Suites suites = super.createSuites(options);
        ListIterator<BasePhase<? super MidTierContext>> position = suites.getMidTier().findPhase(OptimizeLoopAccessesPhase.class);
        Assert.assertNotNull("OptimizeLoopAccessesPhase must be part of the mid tier", position);
        // Retain the exact loop read and its memory dependency across the phase.
        FloatingReadNode[] candidate = new FloatingReadNode[1];
        MemoryPhiNode[] originalMemory = new MemoryPhiNode[1];
        position.previous();
        position.add(new TestPhase() {
            @Override
            protected void run(StructuredGraph graph) {
                recordCandidate(graph, candidate, originalMemory);
            }
        });
        position.next();
        position.add(new TestPhase() {
            @Override
            protected void run(StructuredGraph graph) {
                verifyOptimization(candidate[0], originalMemory[0]);
            }
        });
        return suites;
    }

    /// Records the only floating read whose memory state is carried by a loop phi.
    private static void recordCandidate(StructuredGraph graph, FloatingReadNode[] candidate, MemoryPhiNode[] originalMemory) {
        for (FloatingReadNode read : graph.getNodes().filter(FloatingReadNode.class)) {
            if (read.getLastLocationAccess() instanceof MemoryPhiNode memoryPhi) {
                Assert.assertNull("The test graph must contain only one candidate read", candidate[0]);
                candidate[0] = read;
                originalMemory[0] = memoryPhi;
            }
        }
        Assert.assertNotNull("The test graph must contain a loop-carried read", candidate[0]);
    }

    /// Verifies that the phase transformed the exact read recorded before it ran.
    private static void verifyOptimization(FloatingReadNode candidate, MemoryPhiNode originalMemory) {
        Assert.assertTrue("The candidate read must remain alive", candidate.isAlive());
        Assert.assertNotSame("The candidate read must no longer depend on its loop memory phi", originalMemory, candidate.getLastLocationAccess());
        Assert.assertTrue("The candidate read must seed a value phi", candidate.usages().filter(ValuePhiNode.class).isNotEmpty());
    }

    @Test
    public void testLoopCarriedRead() {
        // Keep the loop read available for the phase under test.
        OptionValues options = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.OptReadElimination, false,
                        MidTier.Options.OptimizeLoopAccesses, true);
        getCode(getResolvedJavaMethod("loopCarriedRead"), options);
    }
}
