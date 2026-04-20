/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package jdk.graal.compiler.phases.common.test;

import static jdk.graal.compiler.phases.common.test.DFAnalysisLSCCPTest.conditionalConstant;

import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.dfanalysis.DFAnalysis;

public class DFAnalysisFullStampsTest extends DFAnalysisBaseTest {

    @Override
    protected OptionValues modifyOptions(OptionValues current) {
        return new OptionValues(current,
                        GraalOptions.ConditionalConstantPropagation, false,
                        GraalOptions.FullStampAnalysis, true,
                        GraalOptions.PentagonalAnalysis, false,
                        DFAnalysis.Options.DFA_EvalAll, true);
    }

    @Test
    public void rangeCC() {
        test("rangeCCSnippet", 7);
    }

    @DFATestSnippet(anchors = 0)
    public static int rangeCCSnippet(int a) {
        int x = 1;
        int y = 2;
        int z = a;
        do {
            // actually x in [1..2], but [0..2] pessimistically
            if (x == 0) {
                y = 9;
                GraalDirectives.controlFlowAnchor();
                x = 0;
            } else {
                x = 2;
            }
        } while (z-- > 0);
        return y;
    }

    @Test
    public void duplicateIfs() {
        test("duplicateIfsSnippet", 5, 3);
    }

    @DFATestSnippet(anchors = 2, deopts = 0)
    public static void duplicateIfsSnippet(int a, int b) {
        int constantOne = conditionalConstant(a, 1);
        GraalDirectives.controlFlowAnchor();
        if (b < constantOne) {
            GraalDirectives.controlFlowAnchor();
            if (b < 1) {
                GraalDirectives.sideEffect(b);
            } else {
                GraalDirectives.deoptimize();
            }
        }
    }

    @Test
    public void checkDynamicPi() {
        test("checkDynamicPiSnippet", 1);
    }

    private static class SomeTypeA {
    }

    private static class SomeTypeB extends SomeTypeA {
    }

    @DFATestSnippet(anchors = 0, returns = "i32 [2]")
    public static int checkDynamicPiSnippet(int a) {
        int constantOne = conditionalConstant(a, 1);
        Class<?> c = switch (a) {
            case 1 -> GraalDirectives.opaque(SomeTypeB.class);
            default -> GraalDirectives.opaque(SomeTypeA.class);
        };
        // here StampAnalysisPhase can conclude that sth = new SomeTypeB()
        Object sth = switch (constantOne) {
            case 1 -> new SomeTypeB();
            default -> new SomeTypeA();
        };
        // creates a dynamic pi node through which we should be able to propagate that sth is of
        // type SomeTypeB
        Object o = c.cast(sth);
        if (o instanceof SomeTypeB) {
            return 2;
        } else {
            GraalDirectives.controlFlowAnchor();
            return constantOne;
        }
    }

    @Test
    public void latePiResetting() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.PartialUnroll, false, DFAnalysis.Options.DFA_AllowInferences, false);
        test(opt, "latePiResettingSnippet", 1);
    }

    @DFATestSnippet(loops = 1)
    public static void latePiResettingSnippet(int a) {
        for (int i = -1; i < 1000; i++) {
            /*
             * Simulate a branch (i >= 0) that is unreachable in the first iteration of the loop but
             * is not detected as such by the analysis.
             */
            if (GraalDirectives.opaque(i >= 0)) {
                // we must not reset this PI after the PHI below has already been evaluated
                int cnt = GraalDirectives.positivePi(i);
                int x;
                if (cnt == 0) {
                    GraalDirectives.controlFlowAnchor();
                    x = 1;
                } else {
                    GraalDirectives.controlFlowAnchor();
                    x = 2;
                }
                GraalDirectives.controlFlowAnchor();
                /*
                 * In the first loop iteration this PHI is detected as reachable here and has 2
                 * reachable inputs which results in the range [1..2]. In the second iteration this
                 * PHI has only 1 reachable input (because now the PI above restricts the range of
                 * cnt from [-1..0] to [0]) resulting in [1] which might break the analysis.
                 */
                GraalDirectives.blackhole(x);
            }
        }
    }

    @Test
    public void lessThanOne() {
        test("lessThanOneSnippet", 5, 3);
    }

    @DFATestSnippet(deopts = 0)
    public static void lessThanOneSnippet(int a, int b) {
        int constantOne = conditionalConstant(a, 1);
        GraalDirectives.controlFlowAnchor();
        if (b < constantOne) {
            GraalDirectives.controlFlowAnchor();
            if (b < 1) {
                GraalDirectives.sideEffect(b);
            } else {
                GraalDirectives.deoptimize();
            }
        }
    }
}
