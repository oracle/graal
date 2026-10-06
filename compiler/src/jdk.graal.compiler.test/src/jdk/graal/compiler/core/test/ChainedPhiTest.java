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

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.phases.MidTier;
import jdk.graal.compiler.loop.phases.LoopInversionPhase;
import jdk.graal.compiler.nodes.ChainedPhiValueSplitNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import org.junit.Test;

public class ChainedPhiTest extends GraalCompilerTest {

    public static int snippet0(int count) {
        int last = 0;
        for (int i = 0; i < count; i++) {
            GraalDirectives.sideEffect();  // prevent removal of empty loop
            last = i;
        }
        return last;
    }

    @Test
    public void test0() {
        // Preserve the loop shape so the test exercises the chained-phi transformation directly.
        OptionValues loopOptions = new OptionValues(getInitialOptions(),
                        GraalOptions.PartialUnroll, false,
                        LoopInversionPhase.Options.LoopInversion, false,
                        MidTier.Options.StripMineCountedLoops, false);
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod("snippet0"), loopOptions);
        assertTrue(graph.getNodes().filter(ChainedPhiValueSplitNode.class).isNotEmpty());
    }

    public static int snippet1(int count) {
        int last = 0;
        for (int i = 1; i < count; i = (int) Math.pow(2, i)) {
            last = i;
        }
        return last;
    }

    @Test
    public void test1() {
        StructuredGraph graph = getFinalGraph("snippet1");
        assertTrue(graph.getNodes().filter(ChainedPhiValueSplitNode.class).isEmpty());
    }

    static volatile int a = 1;

    public static int snippet2(int count, int stride) {
        int last = 0;
        for (int i = 1; i < count; i += stride * a) {
            last = i;
        }
        return last;
    }

    @Test
    public void test2() {
        StructuredGraph graph = getFinalGraph("snippet2");
        assertTrue(graph.getNodes().filter(ChainedPhiValueSplitNode.class).isNotEmpty());
    }

}
