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
package jdk.graal.compiler.duplication.test;

import static jdk.graal.compiler.api.directives.GraalDirectives.injectBranchProbability;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.duplication.phases.simulation.DuplicationOptions;
import jdk.graal.compiler.duplication.phases.simulation.FixedDuplicationSimulationConfig;
import jdk.graal.compiler.duplication.phases.simulation.HighTierDuplicationSimulationPhase;
import jdk.graal.compiler.duplication.phases.simulation.SimulationConfig;
import jdk.graal.compiler.duplication.phases.simulation.SimulationEndInfo;
import jdk.graal.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;

public class DuplicationDBDSTest extends GraalCompilerTest {

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD})
    @interface SimulationResult {
        String errorMsg()

        default "";

        /**
         *
         * @return how many canonicalizations did the simulation find
         */
        int canonicalizations()

        default 0;

        /**
         *
         * @return how many branches could be killed for the entire graph if duplication of all
         *         merges happens
         */
        int killedBranches()

        default 0;

        /**
         *
         * @return should the simulation be recursive and simulate duplications in already
         *         duplicated paths
         */
        boolean enableRecursiveSimulation()

        default false;

        /**
         *
         * @return how deep should the simulation go into duplication recursion
         */
        int blockDepth()

        default 0;

        boolean stopAtCF() default false;

    }

    public static Object field;

    @SimulationResult(canonicalizations = 2)
    @SuppressWarnings("all")
    public static int test1Snippet(int a) {
        int x = 0;
        if (a > 0) {
            x = 1;
            field = null;
        } else {
            x = 2;
            field = null;
        }
        field = null;
        // will get a in the true branch and << 1 in the false branch
        return a * x;
    }

    @SimulationResult(canonicalizations = 4)
    @SuppressWarnings("all")
    public static int test2Snippet(int a) {
        // one canonicalization opens the opportunity for another one
        int x = 0;
        if (a > 0) {
            x = 1;
            field = null;
        } else {
            x = 2;
            field = null;
        }
        field = null;
        int res = a * x;
        return res / a;
    }

    @SimulationResult(killedBranches = 2, canonicalizations = 4)
    @SuppressWarnings("all")
    public static int test3Snippet(int a) {
        // recursive condition eliminated
        int x = 0;
        if (a > 0) {
            x = 1;
            field = null;
        } else {
            x = 2;
            field = null;
        }
        field = null;

        if (x == 1) {
            // canonicalize in 2 branches after duplication
            x = 4;
            field = null;
        } else {
            // canonicalize in 2 branches after duplication
            x = 8;
            field = null;
        }

        return a * x;
    }

    @SimulationResult(canonicalizations = 2, enableRecursiveSimulation = true, blockDepth = 3)
    @SuppressWarnings("all")
    public static int test4Snippet(int a, int b) {
        // canonicalization generates additional synonym
        int x = 0;
        if (a > 0) {
            x = 1;
            field = null;
        } else {
            x = 2;
            field = null;
        }
        field = null;

        if (b > 12) {
            field = null;
        } else {
            // in 2 branches after duplication
            x = x * 8;
            field = null;
        }
        // in 2 branches after recursive simulation
        return a * x;
    }

    @SimulationResult(canonicalizations = 8, killedBranches = 2, enableRecursiveSimulation = true, blockDepth = 3)
    @SuppressWarnings("all")
    public static int test5Snippet(int a) {
        // recursive condition eliminated
        int x = 0;
        if (a > 0) {
            x = 1;
            field = null;
        } else {
            x = 2;
            field = null;
        }
        field = null;

        if (x == 1) {
            // in 2 after duplication
            x = x * 4;
            field = null;
        } else {
            // in 2 after duplication
            x = x * 8;
            field = null;
        }
        // in 4 after recursive duplication
        return a * x;
    }

    @SimulationResult(canonicalizations = 2, blockDepth = 1)
    @SuppressWarnings("all")
    public static int test6Snippet(int a, int b, int c, int d, int e, int f) {
        int z = 0;
        int y = 0;
        int x = 0;
        int w = 0;
        int v = 0;
        int u = 0;

        if (a > 0) {
            z = 1;
            field = null;
        } else {
            z = 2;
            field = null;
        }
        field = null;

        if (b > 0) {
            y = 1;
            field = null;
        } else {
            y = 2;
            field = null;
        }
        field = null;

        if (c > 0) {
            x = 1;
            field = null;
        } else {
            x = 2;
            field = null;
        }
        field = null;

        if (d > 0) {
            w = 1;
            field = null;
        } else {
            w = 2;
            field = null;
        }
        field = null;

        if (e > 0) {
            v = 1;
            field = null;
        } else {
            v = 2;
            field = null;
        }
        field = null;

        if (f > 0) {
            u = 1;
            field = null;
        } else {
            u = 2;
            field = null;
        }
        field = null;

        return z * a + y * b + x * c + w * d + v * e + u * f;
    }

    @SimulationResult(canonicalizations = 4, blockDepth = 2)
    @SuppressWarnings("all")
    public static int test7Snippet(int a) {
        // case that after duplication a canonicalization generates a new synonym for a subsequent
        // canonicalization
        int x = 0;
        if (a > 0) {
            x = 6;
            field = null;
        } else {
            x = 4;
            field = null;
        }
        int y = x / 2;
        return a * y;
    }

    @SimulationResult(canonicalizations = 1, blockDepth = 2)
    @SuppressWarnings("all")
    public static int test8Snippet(int a) {
        // too many fixed for a sinking improvement
        int x = 0;
        if (a > 0) {
            x = 19;
            field = null;
        } else {
            x = 4;
            field = null;
        }
        field = null;
        field = null;
        field = null;
        field = null;
        field = null;
        field = null;
        field = null;
        field = null;
        field = null;
        field = null;
        field = null;
        field = null;
        return a * x;
    }

    @SimulationResult
    @SuppressWarnings("all")
    public static int test9Snippet(int a) {
        field = null;
        return a;
    }

    @SimulationResult(canonicalizations = 1, blockDepth = 2)
    @SuppressWarnings("all")
    public static int test10Snippet(int a) {
        // too many fixed for a sinking improvement
        int x = 0;
        if (a > 0) {
            x = 19;
            field = null;
        } else {
            x = 4;
            field = null;
        }
        // 8 store field + merge + return = 10 fixed
        field = null;
        field = null;
        field = null;
        field = null;
        field = null;
        field = null;
        field = null;
        field = null;
        return a * x;
    }

    @SimulationResult(canonicalizations = 4)
    @SuppressWarnings("all")
    public static int test11Snippet(int a) {
        int x = 0;
        switch (a) {
            case 1:
                x = 1;
                field = null;
                break;
            case 2:
                x = 2;
                field = null;
                break;
            case 3:
                x = 4;
                field = null;
                break;
            default:
                x = 8;
                field = null;
                break;
        }
        field = null;
        return a * x;
    }

    @SimulationResult(canonicalizations = 12, enableRecursiveSimulation = true, blockDepth = -1)
    @SuppressWarnings("all")
    public static int test12Snippet(int a, int b, int c, int d, int e, int f) {
        int z = 0;
        int y = 0;
        int x = 0;
        int w = 0;
        int v = 0;
        int u = 0;

        int depth2 = 0;
        int depth3 = 0;

        if (injectBranchProbability(0.1, a > 0)) {
            z = 1;
            depth2 = 1;
            depth3 = 2;
            field = null;
        } else {
            z = 2;
            depth2 = 2;
            depth3 = 3;
            field = null;
        }
        field = null;

        if (injectBranchProbability(0.1, b > 0)) {
            y = 1;
            field = null;
        } else {
            y = 2;
            field = null;
        }
        field = null;

        // cutoff
        field = depth2;

        if (injectBranchProbability(0.1, c > 0)) {
            x = 1;
            field = null;
        } else {
            x = 2;
            field = null;
        }
        field = null;

        // cutoff
        field = depth3;

        if (injectBranchProbability(0.1, d > 0)) {
            w = 1;
            field = null;
        } else {
            w = 2;
            field = null;
        }
        field = null;

        if (injectBranchProbability(0.1, e > 0)) {
            v = 1;
            field = null;
        } else {
            v = 2;
            field = null;
        }
        field = null;

        if (injectBranchProbability(0.1, f > 0)) {
            u = 1;
            field = null;
        } else {
            u = 2;
            field = null;
        }
        field = null;

        return z * a + y * b + x * c + w * d + v * e + u * f;
    }

    @SimulationResult(canonicalizations = 0, enableRecursiveSimulation = true)
    @SuppressWarnings("all")
    public static int test13Snippet(int a) {
        class A {
            int i;

            public int hashCode() {
                return i;
            };
        }
        class B extends A {
            int j;

            @Override
            public int hashCode() {
                return i + j;
            }
        }
        Object phi = null;
        if (a == 0) {
            phi = new A();
        } else {
            phi = new B();
        }
        // field = phi.hashCode();
        GraalDirectives.blackhole(phi.hashCode());
        return a;
    }

    class Read {
        int i;

        Read(int i) {
            this.i = i;
        }
    }

    @SimulationResult(canonicalizations = 0, enableRecursiveSimulation = true)
    @SuppressWarnings("all")
    public static int test14Snippet(Read r, int a) {
        int x = a;
        if (a > 0) {
            x = r.i;
            GraalDirectives.blackhole(x);
        } else {
            x = a;
            GraalDirectives.blackhole(x);
        }
        // escape x
        GraalDirectives.blackhole(x);
        x = r.i;
        return x;
    }

    @SimulationResult(canonicalizations = 0, killedBranches = 0, enableRecursiveSimulation = false, blockDepth = 2)
    @SuppressWarnings("all")
    public static int test15Snippet(int a) {
        int x = 0;
        if (a == 10) {
            x = a / 3;
        }
        field = null;
        return x;
    }

    @SimulationResult(canonicalizations = 0, killedBranches = 0, enableRecursiveSimulation = false, blockDepth = 2)
    @SuppressWarnings("all")
    public static int test16Snippet(int a) {
        int sum = 0;
        for (int i = 0; i < a; i++) {
            sum += i;
            field = null;
        }
        field = null;
        for (int i = 0; i < a; i++) {
            sum += i;
            field = null;
        }
        field = null;
        return sum;
    }

    @SimulationResult(canonicalizations = 2, enableRecursiveSimulation = true, blockDepth = 3, stopAtCF = true)
    @SuppressWarnings("all")
    public static int test17Snippet(int a, int b) {
        // canonicalization generates additional synonym
        int x = 0;
        if (a > 0) {
            x = 1;
            field = null;
        } else {
            x = 2;
            field = null;
        }
        field = null;

        if (b > 12) {
            field = null;
        } else {
            // in 2 branches after duplication
            x = x * 8;
            field = null;
        }
        // in 2 branches after recursive simulation
        return a * x;
    }

    public static int SideEffectI;

    @SimulationResult(canonicalizations = 0, blockDepth = 4, enableRecursiveSimulation = false, stopAtCF = true)
    public static int test18Snippet(int a, int b) {
        // canonicalization generates additional synonym
        int x = 0;
        if (a > 0) {
            x = 1;
            field = null;
        } else {
            x = 2;
            field = null;
        }
        field = null;
        int f = SideEffectI;
        int c = f * 77 + f * a;
        if (b > c) {
            field = null;
            if (a - b > 14) {
                field = null;
                return a * x;
            } else {
                // in 2 branches after duplication
                x = x * 8;
                field = null;
                return a * x;
            }
        } else {
            // in 2 branches after duplication
            x = x * 8;
            field = null;
            if (a + b > 14) {
                field = null;
                return a * x;
            } else {
                // in 2 branches after duplication
                x = x * 8;
                field = null;
                return a * x;
            }
        }

    }

    @Test
    public void test1() {
        testConditionalEliminationSimulation("test1Snippet");
    }

    @Test
    @Ignore("Div node currently does not check for a/a if stamp(a) != 0 and also not for the case 2*a/a if stamp(a) == 0")
    public void test2() {
        testConditionalEliminationSimulation("test2Snippet");
    }

    @Test
    public void test3() {
        testConditionalEliminationSimulation("test3Snippet");
    }

    @Test
    @Ignore("new simulation does not support recursive simulation")
    public void test4() {
        testConditionalEliminationSimulation("test4Snippet");
    }

    @Test
    @Ignore("new simulation does not support recursive simulation")
    public void test5() {
        testConditionalEliminationSimulation("test5Snippet");
    }

    @Test
    public void test6() {
        testConditionalEliminationSimulation("test6Snippet");
    }

    @Test
    public void test7() {
        testConditionalEliminationSimulation("test7Snippet");
    }

    @Test
    public void test8() {
        testConditionalEliminationSimulation("test8Snippet");
    }

    @Test
    public void test9() {
        testConditionalEliminationSimulation("test9Snippet");
    }

    @Test
    public void test10() {
        testConditionalEliminationSimulation("test10Snippet");
    }

    @Test
    public void test11() {
        testConditionalEliminationSimulation("test11Snippet");
    }

    @Ignore("new simulation does not support recursive simulation")
    @Test
    public void test12() {
        testConditionalEliminationSimulation("test12Snippet");
    }

    @Ignore("new simulation does not support recursive simulation")
    @Test
    public void test13() {
        testConditionalEliminationSimulation("test13Snippet");
    }

    @Ignore("new simulation does not support recursive simulation")
    @Test
    public void test14() {
        testConditionalEliminationSimulation("test14Snippet");
    }

    @Test
    public void test15() {
        testConditionalEliminationSimulation("test15Snippet");
    }

    @Test
    public void test16() {
        testConditionalEliminationSimulation("test16Snippet");
    }

    @Ignore("new simulation does not support recursive simulation")
    @Test
    public void test17() {
        testConditionalEliminationSimulation("test17Snippet");
    }

    @Test
    public void test18() {
        testConditionalEliminationSimulation("test18Snippet");
    }

    public StructuredGraph testConditionalEliminationSimulation(String snippet) {
        CoreProviders context = getProviders();
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();

        SimulationResult expectedResult = getMethod(this.getClass(), snippet).getDeclaredAnnotation(SimulationResult.class);
        int canonicalizations = 0;
        int killedBranches = 0;

        OptionValues options = DuplicationTestOptions.initial().derive(DuplicationOptions.ScheduledDuplicationSimulation, true);
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES, options);
        DebugContext debug = graph.getDebug();
        debug.dump(DebugContext.INFO_LEVEL, graph, "Graph");
        try (DebugContext.Scope _ = debug.scope("SimulateDCE", graph)) {
            canonicalizer.apply(graph, context);
            new ConvertDeoptimizeToGuardPhase(canonicalizer).apply(graph, context);
            CanonicalizerTool canonicalizerTool = GraphUtil.getDefaultSimplifier(context, canonicalizer.getCanonicalizeReads(), graph.getAssumptions(), options);

            SimulationConfig config = new FixedDuplicationSimulationConfig(expectedResult.blockDepth(), true, true,
                            expectedResult.stopAtCF()) {
                @Override
                public String toString() {
                    return "COND_SIM_TEST";
                }

                @Override
                public int maxSimulationBlockDepth(@SuppressWarnings("hiding") OptionValues options) {
                    return 16;
                }
            };

            HighTierDuplicationSimulationPhase newDBDS = new HighTierDuplicationSimulationPhase(options, config, canonicalizerTool);
            newDBDS.apply(graph, context);
            List<SimulationEndInfo> explodedResult = Arrays.asList(newDBDS.getImprovements());

            for (SimulationEndInfo simNode : explodedResult) {
                debug.log(simNode.toString());
                canonicalizations += simNode.getCanonicalizationImprovements();
                killedBranches += simNode.getKilledBranches();
            }

            debug.log("Result set size %d", explodedResult.size());

            canonicalizer.apply(graph, context);

        } catch (Throwable t) {
            debug.handle(t);
        }

        Assert.assertEquals("Possible canonicalizations did not match", expectedResult.canonicalizations(), canonicalizations);
        Assert.assertEquals("Possible killed branches did not match", expectedResult.killedBranches(), killedBranches);
        return graph;
    }

}
