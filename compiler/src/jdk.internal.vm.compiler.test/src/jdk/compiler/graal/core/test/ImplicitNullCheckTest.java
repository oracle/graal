/*
 * Copyright (c) 2011, 2019, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.core.test;

import jdk.compiler.graal.api.directives.GraalDirectives;
import jdk.compiler.graal.debug.DebugContext;
import jdk.compiler.graal.debug.DebugDumpScope;
import jdk.compiler.graal.nodes.DeoptimizeNode;
import jdk.compiler.graal.nodes.StructuredGraph;
import jdk.compiler.graal.nodes.StructuredGraph.AllowAssumptions;
import jdk.compiler.graal.nodes.memory.ReadNode;
import jdk.compiler.graal.nodes.spi.CoreProviders;
import jdk.compiler.graal.phases.OptimisticOptimizations;
import jdk.compiler.graal.phases.common.CanonicalizerPhase;
import jdk.compiler.graal.phases.common.FloatingReadPhase;
import jdk.compiler.graal.phases.common.GuardLoweringPhase;
import jdk.compiler.graal.phases.common.HighTierLoweringPhase;
import jdk.compiler.graal.phases.tiers.MidTierContext;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

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

    @SuppressWarnings("try")
    private void test(final String snippet) {
        DebugContext debug = getDebugContext();
        try (DebugContext.Scope s = debug.scope("FloatingReadTest", new DebugDumpScope(snippet))) {
            StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES, debug);
            CoreProviders context = getProviders();
            CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
            new HighTierLoweringPhase(canonicalizer).apply(graph, context);
            new FloatingReadPhase(canonicalizer).apply(graph, context);
            MidTierContext midTierContext = new MidTierContext(getProviders(), getTargetProvider(), OptimisticOptimizations.ALL, graph.getProfilingInfo());
            new GuardLoweringPhase().apply(graph, midTierContext);

            Assert.assertEquals(0, graph.getNodes(DeoptimizeNode.TYPE).count());
            Assert.assertTrue(graph.getNodes().filter(ReadNode.class).first().canNullCheck());

        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }
}
