/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.ValueAnchorNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.java.MonitorEnterNode;
import jdk.graal.compiler.nodes.java.MonitorExitNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.ConditionalEliminationPhase;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.common.FloatingReadPhase;
import jdk.graal.compiler.phases.common.FrameStateAssignmentPhase;
import jdk.graal.compiler.phases.common.GuardLoweringPhase;
import jdk.graal.compiler.phases.common.HighTierLoweringPhase;
import jdk.graal.compiler.phases.common.LateLockEliminationPhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class AdvancedLockEliminationTest extends GraalCompilerTest {
    public static Object field1;
    public static Object field2;

    public static void testConsecutiveSnippet(Object o) {
        synchronized (o) {
            field1 = null;
        }
        field1 = field2;
        synchronized (o) {
            field1 = null;
        }
    }

    public Object sink;
    private int expectedExitCount = -1;
    private int expectedEnterCount = -1;

    @Test
    public void testConsecutive() {
        testGraph("testConsecutiveSnippet", 1, 1, new Object());
    }

    public static void testConsecutiveNotSnippet(Object o1, Object o2) {
        synchronized (o1) {
            field1 = null;
        }
        field1 = field2;
        synchronized (o2) {
            field1 = null;
        }
    }

    public static void testNestedSnippet(Object o) {
        synchronized (o) {
            field1 = null;
            synchronized (o) {
                field1 = field2;
            }
        }
    }

    @Test
    public void testNested() {
        testGraph("testNestedSnippet", 1, 1, new Object());
    }

    public static void testSequentialNestedSnippet(Object o) {
        synchronized (o) {
            field1 = null;
            synchronized (o) {
                field1 = field2;
            }
            synchronized (o) {
                field1 = field2;
                GraalDirectives.deoptimize();
            }
        }
    }

    @Test
    public void testSequentialNested() {
        /*
         * Disable replacement of deopts with guards. Otherwise, the snippet code would fold to an
         * immediate deopt, because the deopt is in an always executed path.
         */
        OptionValues options = new OptionValues(getInitialOptions(), GraalOptions.OptConvertDeoptsToGuards, false);
        testGraph("testSequentialNestedSnippet", options, 1, 0, new Object());
    }

    @Test
    public void testSequentialNested2() {
        /*
         * Nested lock elimination can result in an exit followed by an enter at different depths.
         * This is fine since they are unrelated locks.
         */
        testGraph("testSequentialNestedSnippet2", 4, 4, new Object(), new Object(), new Object());
    }

    public static void testSequentialNestedSnippet2(Object o, Object o2, Object o3) {
        synchronized (o2) {
            field2 = o2;
        }
        synchronized (o) {
            field1 = null;
            synchronized (o) {
                synchronized (o3) {
                    synchronized (o) {
                        field1 = field2;
                    }
                }
                if (field2 != null) {
                    synchronized (o) {
                        synchronized (o2) {
                            field2 = null;
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testSequentialNestedMerge() {
        /*
         * Nested lock elimination can result in an exit followed by an enter at different depths.
         * This is fine since they are unrelated locks.
         */
        testGraph("testSequentialNestedMergeSnippet", 5, 5, new Object(), new Object(), new Object());
    }

    public static void testSequentialNestedMergeSnippet(Object o, Object o2, Object o3) {
        synchronized (o2) {
            field2 = o2;
        }
        synchronized (o) {
            field1 = null;
            synchronized (o) {
                if (field2 != null) {
                    synchronized (o) {
                        synchronized (o2) {
                            field2 = null;
                        }
                    }
                } else {
                    synchronized (o2) {
                        synchronized (o) {
                            field2 = null;
                        }
                    }
                }
                synchronized (o3) {
                    synchronized (o) {
                        field1 = field2;
                    }
                }
            }
        }
    }

    @Test
    public void testConsecutiveNot() {
        testGraph("testConsecutiveNotSnippet", 2, 2, new Object(), new Object());
    }

    public static void testIfMerge1Snippet(int n, Object o) {
        synchronized (o) {
            field1 = null;
        }
        if (n == 0) {
            synchronized (o) {
                field1 = null;
            }
        }
        synchronized (o) {
            // keep the anchor in the locked region to avoid non-reorderable nodes between the merge
            // and the enter but do not allow duplication to duplicate at this merge
            GraalDirectives.controlFlowAnchor();
            field1 = null;
        }
    }

    @Test
    public void testIfMerge1() {
        testGraph("testIfMerge1Snippet", 1, 1, 1, new Object());
    }

    public static void testIfMerge2Snippet(int n, Object o) {
        synchronized (o) {
            field1 = null;
        }
        if (n == 0) {
            synchronized (o) {
                field1 = null;
            }
        } else {
            synchronized (o) {
                field1 = null;
            }
        }
        synchronized (o) {
            // keep the anchor in the locked region to avoid non-reorderable nodes between the merge
            // and the enter but do not allow duplication to duplicate at this merge
            GraalDirectives.controlFlowAnchor();
            field1 = null;
        }
    }

    @Test
    public void testIfMerge2() {
        testGraph("testIfMerge2Snippet", 1, 1, 1, new Object());
    }

    public static void testIfMerge3Snippet(int n, Object o) {
        if (n == 0) {
            synchronized (o) {
                field1 = null;
            }
        }
        synchronized (o) {
            // keep the anchor in the locked region to avoid non-reorderable nodes between the merge
            // and the enter but do not allow duplication to duplicate at this merge
            GraalDirectives.controlFlowAnchor();
            field1 = null;
        }
    }

    @Test
    public void testIfMerge3() {
        testGraph("testIfMerge3Snippet", 2, 1, 1, new Object());
    }

    public static void testIfMerge4Snippet(int n, Object o) {
        synchronized (o) {
            field1 = null;
        }
        if (n == 0) {
            synchronized (o) {
                field1 = null;
            }
        }
    }

    @Test
    public void testIfMerge4() {
        testGraph("testIfMerge4Snippet", 1, 2, 1, new Object());
    }

    public static void testIfMergeNotSnippet(int n, Object o1, Object o2) {
        synchronized (o2) {
            field1 = null;
        }
        if (n == 0) {
            synchronized (o1) {
                field1 = null;
            }
        }
        synchronized (o2) {
            // keep the anchor in the locked region to avoid non-reorderable nodes between the merge
            // and the enter but do not allow duplication to duplicate at this merge
            GraalDirectives.controlFlowAnchor();
            field1 = null;
        }
    }

    @Test
    public void testIfMergeNot() {
        testGraph("testIfMergeNotSnippet", 3, 3, 1, new Object(), new Object());
    }

    public static void testIfMergePartialSnippet(int n, Object o1, Object o2) {
        if (n == 0) {
            synchronized (o1) {
                field1 = null;
            }
        } else {
            synchronized (o2) {
                field1 = null;
            }
        }
        synchronized (o2) {
            // keep the anchor in the locked region to avoid non-reorderable nodes between the merge
            // and the enter but do not allow duplication to duplicate at this merge
            GraalDirectives.controlFlowAnchor();
            field1 = null;
        }
    }

    @Test
    public void testIfMergePartial() {
        testGraph("testIfMergePartialSnippet", 3, 2, 1, new Object(), new Object());
    }

    static class SynchronizedTest {
        int i;
        volatile int d;

        @BytecodeParserForceInline
        public synchronized void method1() {
            method2();
        }

        @BytecodeParserForceInline
        public synchronized void method2() {
            // Some nonsense work to keep deopt grouping from backing up to an ealier state.
            d++;
            if (i == 0) {
                GraalDirectives.deoptimize();
            }
        }
    }

    public static void testDeoptRecursiveSnippet(SynchronizedTest object) {
        object.method1();
    }

    @Test
    public void testDeoptRecursive() {
        SynchronizedTest testObject = new SynchronizedTest();
        // This test will only fail if run in a fastdebug vm since hotspot seems to happily ignore
        // the unbalanced monitors in product.
        testGraph("testDeoptRecursiveSnippet", 1, 1, testObject);
    }

    static class Dummy {
        int f1;
        int f2;

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Dummy) {
                Dummy other = (Dummy) obj;
                return other.f1 == f1 && other.f2 == f2;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }
    }

    public Object recursiveEarlyEscapeSnippet() {
        Dummy v = new Dummy();
        v.f1 = 2;
        v.f2 = 4;
        sink = v;
        synchronized (v) {
            synchronized (v) {
                v.f2 = 0;
            }
        }
        return v;
    }

    @Test
    public void testRecursiveEarlyEscape() {
        testGraph("recursiveEarlyEscapeSnippet", 1, 1);
    }

    public Object recursivePartialEscapeSnippet() {
        Dummy v = new Dummy();
        v.f1 = 2;
        v.f2 = 4;
        synchronized (v) {
            synchronized (v) {
                sink = v;
                v.f2 = 0;
            }
        }
        return v;
    }

    @Test
    public void testRecursivePartialEscape() {
        testGraph("recursivePartialEscapeSnippet", 1, 1);
    }

    private static Object piCastNonNull(Object o) {
        return o;
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        conf.getPlugins().getInvocationPlugins().register(getClass(), new InvocationPlugin("piCastNonNull", Object.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode obj) {
                ValueAnchorNode anchor = b.add(new ValueAnchorNode());
                b.addPush(JavaKind.Object, new PiNode(obj, StampFactory.objectNonNull(), anchor));
                return true;
            }
        });
        return super.editGraphBuilderConfiguration(conf);
    }

    /**
     * A snippet demonstrating the GR-32668 bug where by a monitorenter is hoisted up a path above a
     * value anchor for the node whose monitor is being locked.
     */
    public static void testSnippetGR32668(Object o, int a) {
        if (a < 0) {
            synchronized (o) {
            }
        } else {
            GraalDirectives.blackhole(o);
        }

        // Create an anchored pi on `o` specifying that `o` is non-null.
        Object o2 = piCastNonNull(o);

        synchronized (o2) {
            field1 = field2;
        }
    }

    @Test
    public void testGR32668() {
        // not optimizable due to scheduling restrictions
        testGraph("testSnippetGR32668", 2, 2, new Object(), 123);
    }

    static class Empty {
    }

    public static void testGuardedMergeDoesNotCoarsenSnippet(Object o) {
        Object mergedLock = piCastNonNull(o);
        if (o instanceof Empty) {
            synchronized ((Empty) o) {
                field1 = null;
            }
        }
        synchronized (mergedLock) {
            GraalDirectives.controlFlowAnchor();
            field1 = null;
        }
    }

    @Test
    public void testGuardedMergeDoesNotCoarsen() {
        testGraph("testGuardedMergeDoesNotCoarsenSnippet", 2, 2, new Empty());
    }

    @Override
    protected void checkMidTierGraph(StructuredGraph graph) {
        if (expectedEnterCount != -1) {
            Assert.assertEquals(expectedEnterCount, graph.getNodes(MonitorEnterNode.TYPE).count());
        }
        if (expectedExitCount != -1) {
            Assert.assertEquals(expectedExitCount, graph.getNodes(MonitorExitNode.TYPE).count());
        }
        super.checkMidTierGraph(graph);
    }

    private void testGraph(String snippet, int enterCount, int exitCount, Object... args) {
        testGraph(snippet, getInitialOptions(), enterCount, exitCount, args);
    }

    private void testGraph(String snippet, OptionValues options, int enterCount, int exitCount, Object... args) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES, options);

        MidTierContext context = new MidTierContext(getProviders(), getTargetProvider(), OptimisticOptimizations.ALL, null);

        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        new HighTierLoweringPhase(canonicalizer).apply(graph, context);
        new FloatingReadPhase(canonicalizer).apply(graph, context);
        new DeadCodeEliminationPhase().apply(graph);
        new ConditionalEliminationPhase(canonicalizer, true).apply(graph, context);
        new GuardLoweringPhase().apply(graph, context);
        new FrameStateAssignmentPhase().apply(graph);

        new LateLockEliminationPhase().apply(graph);
        new SchedulePhase(graph.getOptions()).apply(graph, context);

        Assert.assertEquals(enterCount, graph.getNodes(MonitorEnterNode.TYPE).count());
        Assert.assertEquals(exitCount, graph.getNodes(MonitorExitNode.TYPE).count());

        /*
         * Test that it works in the context of a full compile which might produce slightly
         * different graph shapes.
         */
        try {
            this.expectedEnterCount = enterCount;
            this.expectedExitCount = exitCount;
            test(options, snippet, args);
        } finally {
            this.expectedEnterCount = -1;
            this.expectedExitCount = -1;
        }
    }

    private static int[] nullArray = null;
    public static int sum = 0;

    public void testGR59023Snippet0(boolean flag) {
        while (flag) {
            // The while condition test will trigger loop unswitching
            synchronized (this) {
                synchronized (this) {
                    // Craft an unwind path
                    sum += nullArray.length;
                }
            }
        }
    }

    @Test
    public void testGR59023Case0() {
        test("testGR59023Snippet0", true);
    }

    private static Object nullReceiver = null;

    public void testGR59023Snippet1(boolean flag) {
        while (flag) {
            // The while condition test will trigger loop unswitching
            synchronized (this) {
                synchronized (this) {
                    // Craft an unwind path
                    nullReceiver.toString();
                }
            }
        }
    }

    @Test
    public void testGR59023Case1() {
        test("testGR59023Snippet1", true);
    }
}
