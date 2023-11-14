/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.core.test.EarlyGVNTest.NodeCount.count;
import static jdk.graal.compiler.core.test.EarlyGVNTest.NodeCount.invariantCount;
import static org.junit.Assume.assumeTrue;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.ListIterator;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import jdk.graal.compiler.loop.phases.LoopFullUnrollPhase;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.loop.LoopEx;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.ConditionalEliminationPhase;
import jdk.graal.compiler.phases.common.DominatorBasedGlobalValueNumberingPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.Suites;

public class EarlyGVNTest extends GraalCompilerTest {

    @Before
    public void checkOptions() {
        assumeTrue(GraalOptions.EarlyGVN.getValue(getInitialOptions()));
        assumeTrue(GraalOptions.EarlyLICM.getValue(getInitialOptions()));
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD})
    @interface MustFold {
        boolean noLoopLeft() default true;
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        MustFold fold = graph.method().getAnnotation(MustFold.class);
        if (fold != null) {
            if (fold.noLoopLeft()) {
                assertFalse(graph.hasLoops());
            } else {
                assertTrue(graph.hasLoops());
            }
        }
        super.checkHighTierGraph(graph);
    }

    /**
     * Helper class for assertion checking.
     */
    static class NodeCount {
        /**
         * Expected node class to be checked.
         */
        final NodeClass<?> nodeClass;

        /**
         * Expected count of nodes to be found in the graph.
         */
        final int count;

        /**
         * Expected count of nodes which are outside all loops.
         */
        final int invariantCount;

        NodeCount(NodeClass<?> nodeClass, int count, int invariantCount) {
            this.nodeClass = nodeClass;
            this.count = count;
            this.invariantCount = invariantCount;
        }

        static NodeCount invariantCount(NodeClass<?> nodeClass, int count) {
            return new NodeCount(nodeClass, count, count);
        }

        static NodeCount count(NodeClass<?> nodeClass, int count) {
            return new NodeCount(nodeClass, count, 0);
        }
    }

    private void checkHighTierGraph(String snippet, NodeCount... counts) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        Suites suites = super.createSuites(new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false));
        PhaseSuite<HighTierContext> ht = suites.getHighTier().copy();
        ListIterator<BasePhase<? super HighTierContext>> position = ht.findPhase(LoopFullUnrollPhase.class);
        position.add(new TestBasePhase<>() {

            @Override
            protected void run(@SuppressWarnings("hiding") StructuredGraph graph, HighTierContext context) {
                checkHighTierGraph(graph, counts);
            }
        });
        ht.apply(graph, getDefaultHighTierContext());
    }

    private void checkHighTierGraph(StructuredGraph graph, NodeCount... counts) {
        LoopsData loops = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph);

        for (NodeCount count : counts) {
            List<Node> nodes = graph.getNodes().filter(x -> x.getNodeClass().equals(count.nodeClass)).snapshot();
            int realCount = nodes.size();
            Assert.assertEquals("Wrong node count for node class " + count.nodeClass, count.count, realCount);
            if (count.invariantCount != 0) {
                int invariantCount = count.count;
                for (Node node : nodes) {
                    for (LoopEx loop : loops.loops()) {
                        if (loop.whole().contains(node)) {
                            invariantCount--;
                            break;
                        }
                    }
                }
                Assert.assertEquals("Wrong number of invariant nodes for node class " + count.nodeClass.getClazz(), count.invariantCount, invariantCount);
            }
        }
    }

    @MustFold()
    public static int snippet00(int[] arr) {
        int i = arr.length;
        int i2 = arr.length;
        return i + i2;
    }

    @Test
    public void test00() {
        String s = "snippet00";
        test(s, new int[]{1, 2, 3});
        checkHighTierGraph(s, invariantCount(ArrayLengthNode.TYPE, 1));
    }

    @MustFold
    public static void snippet01(int[] arr) {
        int i = 0;
        while (true) {
            @SuppressWarnings("unused")
            int len = arr.length;
            if (i < 10) {
                i++;
                continue;
            }
            break;
        }
    }

    @Test
    public void test01() {
        String s = "snippet01";
        test(s, new int[0]);
        checkHighTierGraph(s, invariantCount(ArrayLengthNode.TYPE, 1),
                        count(LoopBeginNode.TYPE, 0));
    }

    public static int field = 0;

    @MustFold(noLoopLeft = false)
    public static void snippet02(@SuppressWarnings("unused") int[] arr) {
        int i = 0;
        while (true) {
            @SuppressWarnings("unused")
            int len = field;
            if (i < 10) {
                GraalDirectives.sideEffect(len);
                i++;
                continue;
            }
            break;
        }
    }

    @Test
    public void test02() {
        String s = "snippet02";
        test(s, new int[0]);
        checkHighTierGraph(s, count(LoopBeginNode.TYPE, 1));
    }

    @MustFold
    public static void snippet03(int[] arr) {
        int i = 0;
        while (true) {
            @SuppressWarnings("unused")
            int len = arr.length;
            @SuppressWarnings("unused")
            int len2 = arr.length;
            if (i < 10) {
                i++;
                continue;
            }
            break;
        }
    }

    @Test
    public void test03() {
        String s = "snippet03";
        test(s, new int[0]);
        checkHighTierGraph(s, count(LoopBeginNode.TYPE, 0),
                        invariantCount(ArrayLengthNode.TYPE, 1));
    }

    static class X {
        int[] arr;

        X(int[] arr) {
            this.arr = arr;
        }
    }

    @MustFold
    public static void snippet04(X x) {
        int i = 0;
        while (true) {
            int[] arr = x.arr;
            @SuppressWarnings("unused")
            int len = arr.length;
            if (i < 10) {
                i++;
                continue;
            }
            break;
        }
    }

    @Test
    public void test04() {
        String s = "snippet04";
        test(s, new X(new int[0]));
        checkHighTierGraph(s, invariantCount(LoadFieldNode.TYPE, 1),
                        invariantCount(ArrayLengthNode.TYPE, 1),
                        count(LoopBeginNode.TYPE, 0));
    }

    static class Y {
        int y;
    }

    @MustFold(noLoopLeft = false)
    public static int snippet05(Y y) {
        int res = 0;
        int i = 0;
        while (true) {
            res = y.y;
            if (i < 10) {
                y.y = i;
                i++;
                continue;
            }
            break;
        }
        return res;
    }

    @Test
    public void test05() {
        String s = "snippet05";
        test(s, new ArgSupplier() {

            @Override
            public Object get() {
                return new Y();
            }
        });
        checkHighTierGraph(s, count(LoopBeginNode.TYPE, 1),
                        count(LoadFieldNode.TYPE, 1),
                        count(StoreFieldNode.TYPE, 1));
    }

    @MustFold
    public static void snippet06(int[] arr) {
        int i = 0;
        while (true) {
            if (arr == null) {
                GraalDirectives.deoptimizeAndInvalidate();
            }
            @SuppressWarnings("unused")
            int len = arr.length;
            if (i < 10) {
                i++;
                continue;
            }
            break;
        }
    }

    @Test
    public void test06() {
        String s = "snippet06";
        test(s, new int[0]);
        checkHighTierGraph(s, count(LoopBeginNode.TYPE, 0),
                        invariantCount(FixedGuardNode.TYPE, 1),
                        invariantCount(ArrayLengthNode.TYPE, 1));
    }

    @MustFold
    public static void snippet07(int[] arr) {
        int i = 0;
        if (arr.length == 123) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        while (true) {
            if (arr.length == 123) {
                GraalDirectives.deoptimizeAndInvalidate();
            }
            if (i < 10) {
                i++;
                continue;
            }
            break;
        }
    }

    @Test
    public void test07() {
        String s = "snippet07";
        test(s, new int[0]);
        // will only be one guard later but we would need a canon and another run of gvn to capture
        // that since we need a canonicalizer in between
        checkHighTierGraph(s, count(LoopBeginNode.TYPE, 0),
                        invariantCount(FixedGuardNode.TYPE, 2));
    }

    public static int field2;

    @MustFold
    public static int snippet08(int[] arr) {
        int f = field;
        field2 = f;
        if (arr.length == 123) {
            field = 123;
        }
        return field;
    }

    @Test
    public void test08() {
        String s = "snippet08";
        test(s, new int[0]);
        checkHighTierGraph(s, invariantCount(LoadFieldNode.TYPE, 2),
                        invariantCount(StoreFieldNode.TYPE, 2),
                        invariantCount(ArrayLengthNode.TYPE, 1));
    }

    @MustFold
    public static int snippet09(int[] arr) {
        int i = 0;
        while (true) {
            @SuppressWarnings("unused")
            int len = arr.length;
            if (i < 10) {
                i++;
                continue;
            }
            break;
        }
        return arr.length;
    }

    @Test
    public void test09() {
        String s = "snippet09";
        test(s, new int[0]);
        checkHighTierGraph(s, invariantCount(ArrayLengthNode.TYPE, 1),
                        count(LoopBeginNode.TYPE, 0));
    }

    @MustFold(noLoopLeft = false)
    public static int snippet10(Y y) {
        int res = 0;
        int i = 0;
        while (true) {
            if (i < 1000) {
                res = y.y;
                if (field == 123) {
                    res += 123;
                    field2 = res;
                } else {
                    if (field == 223) {
                        res += 33;
                    } else {
                        res += 55;
                    }
                    field2 = res;
                }
                i++;
                continue;
            }
            break;
        }
        return res;
    }

    @Test
    public void test10() {
        String s = "snippet10";
        test(s, new ArgSupplier() {

            @Override
            public Object get() {
                return new Y();
            }
        });
        checkHighTierGraph(s, invariantCount(FixedGuardNode.TYPE, 0),
                        count(LoopBeginNode.TYPE, 1));
    }

    @MustFold
    public static void snippet11(int[] arr) {
        int i = 0;
        while (true) {
            @SuppressWarnings("unused")
            int len = arr.length;
            if (i < 10) {
                i++;
                continue;
            }
            break;
        }
        while (true) {
            @SuppressWarnings("unused")
            int len = arr.length;
            if (i < 10) {
                i++;
                continue;
            }
            break;
        }
    }

    @Test
    public void test11() {
        String s = "snippet11";
        test(s, new int[0]);
        checkHighTierGraph(s, invariantCount(ArrayLengthNode.TYPE, 1));
    }

    @MustFold
    public static void snippet12(@SuppressWarnings("unused") int[] arr) {
        int i = 0;
        while (true) {
            @SuppressWarnings("unused")
            int len = field;
            if (i < 10) {
                len += field;
                len += field;
                len += field;
                field2 = len;
                i++;
                continue;
            }
            break;
        }
        GraalDirectives.sideEffect();
        while (true) {
            @SuppressWarnings("unused")
            int len = field;
            if (i < 10) {
                len += field;
                len += field;
                len += field;
                field2 = len;
                i++;
                continue;
            }
            break;
        }
    }

    @Test
    public void test12() {
        String s = "snippet12";
        test(s, new int[0]);
        checkHighTierGraph(s, invariantCount(LoadFieldNode.TYPE, 1));
    }

    public static int f3;

    static class F {
        int fi;
    }

    public static int snippet13(@SuppressWarnings("unused") int[] arr, F f) {
        field = f.fi;
        b: if (arr.length > 0) {
            if (arr.length == 123) {
                field = 123;
                GraalDirectives.controlFlowAnchor();
                f.fi = arr.length;
                if (arr.length == 1222) {
                    break b;
                }
            } else {
                field = 124;
                GraalDirectives.controlFlowAnchor();
            }
            int i = 0;
            while (true) {
                if (i >= arr.length) {
                    break b;
                }
                GraalDirectives.blackhole(i);
                i++;
            }
        }
        return f.fi;
    }

    @Test
    public void test13() {
        for (int i = 0; i < 100; i++) {
            snippet13(new int[]{1}, new F());
            snippet13(new int[0], new F());
        }
        String s = "snippet13";
        test(s, new int[]{123}, new F());
        checkHighTierGraph(s, invariantCount(FixedGuardNode.TYPE, 0),
                        count(LoopBeginNode.TYPE, 1),
                        invariantCount(ArrayLengthNode.TYPE, 1),
                        invariantCount(LoadFieldNode.TYPE, 2));
    }

    public static int snippetNestLoops(int[] arr) {
        int limit = 10;
        int len1 = 0;
        int len2 = 0;
        int i = 0;
        do {
            int j = 0;
            do {
                len2 += arr.length;
            } while (j++ <= limit);
            len1 += arr.length;
        } while (i++ <= limit);
        return len1 + len2;
    }

    @Test
    public void testNested() {
        String s = "snippetNestLoops";
        test(s, new int[0]);
        checkHighTierGraph(s, invariantCount(ArrayLengthNode.TYPE, 1));
    }

    public static int doubleNest(int l0, int l1, int l2, Object[] arr, Object[] arr2) {
        int i0 = 0;
        int i1 = 0;
        int i2 = 0;
        int result = 0;
        Object[] nonNullArray = GraalDirectives.guardingNonNull(arr);
        if (nonNullArray.length < 10) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        do {
            Object[] proxy = null;
            do {
                if (i1 > i0) {
                    proxy = nonNullArray;
                } else {
                    proxy = arr2;
                }
            } while (i1++ < l1);
            do {
                Object o1 = proxy[0];
                if (o1 == null) {
                    result *= 2;
                }
            } while (i2++ < l2);
            Object o2 = proxy[0];
            result += (Integer) o2;
        } while (i0++ < l0);
        return result;
    }

    @Test
    public void testNested2() {
        String s = "doubleNest";
        StructuredGraph g = parseEager(getResolvedJavaMethod(s), AllowAssumptions.NO);
        HighTierContext highTierContext = getDefaultHighTierContext();
        CanonicalizerPhase c = CanonicalizerPhase.create();

        c.apply(g, getDefaultHighTierContext());
        new ConvertDeoptimizeToGuardPhase(c).apply(g, highTierContext);
        new ConditionalEliminationPhase(c, false).apply(g, highTierContext);
        new DominatorBasedGlobalValueNumberingPhase(c).apply(g, highTierContext);
        new ConditionalEliminationPhase(c, false).apply(g, highTierContext);
        new DominatorBasedGlobalValueNumberingPhase(c).apply(g, highTierContext);

        checkHighTierGraph(g, count(FixedGuardNode.TYPE, 4),
                        count(LoopBeginNode.TYPE, 3),
                        invariantCount(ArrayLengthNode.TYPE, 1),
                        count(LoadIndexedNode.TYPE, 1));

    }
}
