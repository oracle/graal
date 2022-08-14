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
package org.graalvm.compiler.core.test;

import java.util.ListIterator;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.calc.FloatingIntegerDivRemNode;
import org.graalvm.compiler.nodes.calc.IntegerDivRemNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class FloatingDivTest extends GraalCompilerTest {

    private void checkHighTierGraph(String snippet, int fixedDivsBeforeLowering, int floatingDivsBeforeLowering, int fixedDivAfterLowering, int floatingDivAfterLowering) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        Suites suites = super.createSuites(new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.EarlyGVN, false));
        PhaseSuite<HighTierContext> ht = suites.getHighTier().copy();
        ListIterator<BasePhase<? super HighTierContext>> position = ht.findPhase(LoweringPhase.class);
        position.previous();
        position.add(new TestBasePhase<HighTierContext>() {

            @Override
            protected void run(@SuppressWarnings("hiding") StructuredGraph graph, HighTierContext context) {
                Assert.assertEquals(fixedDivsBeforeLowering, graph.getNodes().filter(IntegerDivRemNode.class).count());
                Assert.assertEquals(floatingDivsBeforeLowering, graph.getNodes().filter(FloatingIntegerDivRemNode.class).count());
            }
        });
        ht.apply(graph, getDefaultHighTierContext());

        Assert.assertEquals(fixedDivAfterLowering, graph.getNodes().filter(IntegerDivRemNode.class).count());
        Assert.assertEquals(floatingDivAfterLowering, graph.getNodes().filter(FloatingIntegerDivRemNode.class).count());
    }

    private void checkFinalGraph(String snippet, int fixedDivs, int floatingDivs, int zeroChecks) {
        if (!isArchitecture("AMD64")) {
            /*
             * We only try to fold divs and their guards back together if the architecture supports
             * it, i.e., amd64 at the moment.
             */
            return;
        }
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        Suites suites = super.createSuites(new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.EarlyGVN, false));

        suites.getHighTier().apply(graph, getDefaultHighTierContext());
        suites.getMidTier().apply(graph, getDefaultMidTierContext());
        suites.getLowTier().apply(graph, getDefaultLowTierContext());

        Assert.assertEquals(fixedDivs, graph.getNodes().filter(IntegerDivRemNode.class).count());
        Assert.assertEquals(floatingDivs, graph.getNodes().filter(FloatingIntegerDivRemNode.class).count());
        int ie = 0;
        for (IntegerEqualsNode ieq : graph.getNodes().filter(IntegerEqualsNode.class)) {
            if (ieq.getY().isConstant() && ieq.getY().asJavaConstant().asLong() == 0) {
                if (ieq.getY().usages().filter(FloatingIntegerDivRemNode.class).isNotEmpty()) {
                    ie++;
                }
            }
        }
        Assert.assertEquals(zeroChecks, ie);
    }

    public static int snippet(int x) {
        int result = 0;
        for (int n = 0; n < x; n++) {
            if (n % 5 == 0 && n % 3 == 0) {
                result += 1;
            } else if (n % 5 == 0) {
                result += 2;
            } else if (n % 3 == 0) {
                result += 3;
            } else {
                result += 4;
            }
        }
        return result;
    }

    @Test
    public void test01() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.EarlyGVN, false);
        String s = "snippet";
        test(opt, s, 100);
        checkHighTierGraph(s, 0, 2, 0, 2);
        checkFinalGraph(s, 0, 0, 0);
    }

    public static int snippet2(int x, int y, int z) {
        int result = 0;
        for (int n = 0; n < x; n += GraalDirectives.opaque(1)) {
            if (n % y == 0 && n % z == 0) {
                GraalDirectives.controlFlowAnchor();
                result += 1;
            }
            GraalDirectives.controlFlowAnchor();
            if (n % y == 0) {
                result += 2;
            }
            GraalDirectives.controlFlowAnchor();
            if (n % z == 0) {
                result += 3;
            } else {
                result += 4;
            }
            GraalDirectives.controlFlowAnchor();
        }
        return result;
    }

    @Test
    public void test02() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.EarlyGVN, false);
        String s = "snippet2";
        test(opt, s, 100, 5, 3);
        if (isArchitecture("aarch64")) {
            // overflow does not trap on aarch
            checkHighTierGraph(s, 4, 0, 0, 2);
        } else if (isArchitecture("AMD64")) {
            checkHighTierGraph(s, 4, 0, 4, 0);
        }
        checkFinalGraph(s, 4, 0, 0);
    }

    public static int snippet3(int a) {
        return a / -1;
    }

    @Test
    public void test03() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.EarlyGVN, false);
        String s = "snippet3";
        test(opt, s, 10);
        test(opt, s, Integer.MIN_VALUE);
        checkHighTierGraph(s, 0, 0, 0, 0);
        checkFinalGraph(s, 0, 0, 0);
    }

    public static int snippet4(int a, @SuppressWarnings("unused") int b) {
        int i = 0;
        for (; i < a; i += GraalDirectives.opaque(1)) {
            GraalDirectives.sideEffect();
        }
        int res1 = i / 100000;
        GraalDirectives.sideEffect();
        return a / (res1 + 1);
    }

    @Test
    public void test04() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.EarlyGVN, false);
        String s = "snippet4";
        test(opt, s, 10, 3);
        if (isArchitecture("aarch64")) {
            // overflow does not trap on aarch
            checkHighTierGraph(s, 1, 1, 0, 2);
        } else if (isArchitecture("AMD64")) {
            checkHighTierGraph(s, 1, 1, 1, 1);
        }
        checkFinalGraph(s, 1, 0, 0);
    }

    public static int snippet5(int a, @SuppressWarnings("unused") int b) {
        int i = a / b;
        GraalDirectives.sideEffect();
        i += a / b;
        return i;
    }

    @Test
    public void test05() {
        String s = "snippet5";
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.EarlyGVN, false);
        test(opt, s, 10, 3);
        if (isArchitecture("aarch64")) {
            // overflow does not trap on aarch
            checkHighTierGraph(s, 2, 0, 0, 1);
        } else if (isArchitecture("AMD64")) {
            checkHighTierGraph(s, 2, 0, 2, 0);
        }
        checkFinalGraph(s, 2, 0, 0);
    }

    public static int snippet6(@SuppressWarnings("unused") int a, int b) {
        int i = 100 / b;
        GraalDirectives.sideEffect();
        i += 100 / b;
        return i;
    }

    @Test
    public void test06() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.EarlyGVN, false);
        String s = "snippet6";
        test(opt, s, 10, 3);
        checkHighTierGraph(s, 2, 0, 0, 1);
        checkFinalGraph(s, 1, 0, 0);
    }

    public static int snippet7(int[] arr) {
        int result = 0;
        int len = arr.length;
        for (int i = 0; i < len; i++) {
            int divisor = arr[i];
            int res1 = len / divisor;
            int res2 = len % divisor;
            int res3 = len / divisor;
            result += res1 + res2 + res3;
        }
        return result;
    }

    @Test
    public void test07() {
        String s = "snippet7";
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.EarlyGVN, false);
        test(opt, s, new int[]{1, 2, 3, 4, 5, 6});
        checkHighTierGraph(s, 3, 0, 0, 2);
        // one div is made fixed and is the /0 guard of the other
        checkFinalGraph(s, 2, 0, 0);
    }

    public static int snippet08(int b) {
        return 100 / b;
    }

    @Test
    @Ignore
    public void test08() {
        String s = "snippet08";
        try {
            snippet08(0);
        } catch (ArithmeticException e) {
            // get an exception edge
        }
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.EarlyGVN, false);
        test(opt, s, 1);
        checkHighTierGraph(s, 1, 0, 0, 1);
    }
}
