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
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.SafepointNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;

public class LoopSafepointEliminationTest extends GraalCompilerTest {

    private int expectedLoopSafepoints = -1;

    private void reset() {
        expectedLoopSafepoints = -1;
    }

    public static long descendingLoopBeyondIntRangeSnippet(boolean condition) {
        long start = condition ? Long.MAX_VALUE : 1_000L;
        long limit = condition ? Long.MIN_VALUE : -1_000L;
        long result = 0;
        for (long i = start; i > limit; i--) {
            result += i;
            GraalDirectives.sideEffect();
        }
        return result;
    }

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        super.checkLowTierGraph(graph);
        assert expectedLoopSafepoints >= 0 : "Must be set";
        int loopSafepoints = 0;
        for (SafepointNode safepoint : graph.getNodes().filter(SafepointNode.class)) {
            if (safepoint.getLoopLink() instanceof LoopBeginNode) {
                loopSafepoints++;
            }
        }
        Assert.assertEquals("Loop safepoints", expectedLoopSafepoints, loopSafepoints);
    }

    @Test
    public void testDescendingLoopBeyondIntRange() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        GraalOptions.FullUnroll, false,
                        GraalOptions.PartialUnroll, false,
                        GraalOptions.LoopPeeling, false);
        expectedLoopSafepoints = 1;
        test(options, "descendingLoopBeyondIntRangeSnippet", false);
        reset();
    }
}
