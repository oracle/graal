/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.jvmci.debug.*;
import jdk.internal.jvmci.debug.Debug.*;

import org.junit.*;

import com.oracle.graal.api.directives.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.memory.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;

/**
 * Tests that the hub access and the null check are folded.
 */
public class ImplicitNullCheckTest extends GraphScheduleTest {

    public static final class Receiver {

        public int a;
    }

    public static int test1Snippet(Object o) {
        if (GraalDirectives.guardingNonNull(o) instanceof Receiver) {
            return 42;
        }
        return 0;
    }

    @Ignore("temporarily disable until LoadHub lowering is clarified")
    @Test
    public void test1() {
        test("test1Snippet");
    }

    private void test(final String snippet) {
        try (Scope s = Debug.scope("FloatingReadTest", new DebugDumpScope(snippet))) {
            StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
            PhaseContext context = new PhaseContext(getProviders());
            new LoweringPhase(new CanonicalizerPhase(), LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
            new FloatingReadPhase().apply(graph);
            MidTierContext midTierContext = new MidTierContext(getProviders(), getCodeCache().getTarget(), OptimisticOptimizations.ALL, graph.method().getProfilingInfo());
            new GuardLoweringPhase().apply(graph, midTierContext);

            Assert.assertEquals(0, graph.getNodes(DeoptimizeNode.TYPE).count());
            Assert.assertTrue(graph.getNodes().filter(ReadNode.class).first().canNullCheck());

        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }
}
