/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.core.phases.MidTier;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.duplication.phases.DeDuplicationPhase;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.FrameStateAssignmentPhase;
import jdk.graal.compiler.phases.common.GuardLoweringPhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase.SchedulingStrategy;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.util.GraphOrder;
import org.junit.Assert;
import org.junit.Test;

public class DeDuplicationTest extends GraalCompilerTest {

    public static class TestObject {
        public int x;
    }

    public static int testFieldCoalescingSnippet(TestObject a, TestObject b, TestObject c, int x, int y) {
        int v;
        if (x < 0) {
            c.x = 1001;
            b.x = 1001;
            v = x + 1;
        } else {
            c.x = 1002;
            b.x = 1002;
            v = y + 1;
        }
        GraalDirectives.controlFlowAnchor();
        a.x = 1;
        return c.x + v;
    }

    @Test
    public void testFieldCoalescing() {
        TestObject testObject1 = new TestObject();
        TestObject testObject2 = new TestObject();
        test("testFieldCoalescingSnippet", testObject1, testObject2, testObject2, -10, 10);
        test("testFieldCoalescingSnippet", testObject1, testObject2, testObject1, 10, -5);
    }

    @Test
    public void testConfiguration() {
        OptionValues enabled = getInitialOptions();
        Assert.assertNotNull(new MidTier(enabled).findPhase(DeDuplicationPhase.class));

        OptionValues disabled = new OptionValues(enabled, DeDuplicationPhase.Options.OptDeDuplication, false);
        Assert.assertNull(new MidTier(disabled).findPhase(DeDuplicationPhase.class));
    }

    @Override
    protected void checkMidTierGraph(StructuredGraph graph) {
        Assert.assertEquals(3, graph.getNodes().filter(WriteNode.class).count());
        Assert.assertEquals(2, graph.getNodes().filter(AddNode.class).count());
        super.checkMidTierGraph(graph);
    }

    private static final class TestClass {
    }

    static Object object1 = new TestClass();
    static Object object2 = new Object();
    static Object object3 = "";
    static Object object4 = new TestClass();

    public static int checkCastSnippet(int arg) {
        Object obj;
        if (arg == 2) {
            obj = object2;
        } else if (arg == 3) {
            obj = object3;
        } else if (arg == 4) {
            obj = object4;
        } else {
            obj = object1;
        }
        if (obj == null) {
            return arg;
        }
        return 1;
    }

    @Test
    public void testBCCheckCast() throws Throwable {
        OptionValues opt = new OptionValues(getInitialOptions(), Assertions.Options.DetailedAsserts, true);
        StructuredGraph g = parseEager("checkCastSnippet", AllowAssumptions.YES, opt);
        Suites s = createSuites(opt);
        s.getHighTier().apply(g, getDefaultHighTierContext());
        new GuardLoweringPhase().apply(g, getDefaultMidTierContext());
        new FrameStateAssignmentPhase().apply(g);
        new DeDuplicationPhase(CanonicalizerPhase.create()).apply(g, getDefaultMidTierContext());
        GraphOrder.assertSchedulableGraph(g);
        SchedulePhase.runWithoutContextOptimizations(g, SchedulingStrategy.LATEST);
        CanonicalizerPhase.create().apply(g, getDefaultMidTierContext());
    }
}
