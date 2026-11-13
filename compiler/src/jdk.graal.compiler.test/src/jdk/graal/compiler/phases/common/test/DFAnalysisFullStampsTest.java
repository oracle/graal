/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.phases.common.test;

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
            /*
             * StampAnalysis finds that x is in [1..2], instead of [0..2], which is the stamp for
             * this value in the graph.
             */
            if (x == 0) {
                y = 9;
                // this branch is found to be unreachable and the anchor is removed
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
        int constantOne = DFAnalysisLSCCPTest.conditionalConstant(a, 1);
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

    private static final class SomeTypeB extends SomeTypeA {
    }

    @DFATestSnippet(anchors = 0, returns = "i32 [2]")
    public static int checkDynamicPiSnippet(int a) {
        int constantOne = DFAnalysisLSCCPTest.conditionalConstant(a, 1);
        Class<?> c = switch (a) {
            case 1 -> GraalDirectives.opaque(SomeTypeB.class);
            default -> GraalDirectives.opaque(SomeTypeA.class);
        };
        // here StampAnalysisPhase can conclude that sth = new SomeTypeB()
        Object sth = switch (constantOne) {
            case 1 -> new SomeTypeB();
            default -> new SomeTypeA();
        };
        // creates a dynamic pi node through which we are able to propagate that sth is of type
        // SomeTypeB
        Object o = c.cast(sth);
        if (o instanceof SomeTypeB) {
            return 2;
        } else {
            // this anchor is removed
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
             * is not detected as such by the analysis. Furthermore, the call to 'opaque' blocks the
             * insertion of an InferredFactNode, which would only be scheduled in reachable branches
             * and would therefore fix the imprecision incurred in this example.
             */
            if (GraalDirectives.opaque(i >= 0)) {
                // we must not reset this PI after the PHI below has already been evaluated
                int cnt = GraalDirectives.positivePi(i);
                /*
                 * In the first loop iteration, we obtain an impossible value for 'i' (improving the
                 * range [-1,-1] with [0,intmax] yields an empty range). Since the transfer function
                 * must never return an UNEVALUATED result (see DFAnalysis), we default to returning
                 * the input value (i.e., the range [-1,-1]).
                 *
                 * Combining the information of the PiNode with the incoming range [-1,0] in the
                 * second loop iteration would yield the range [0,0], which is not comparable to the
                 * previous result [-1,-1] and therefore not weaker. This means, monotonicity would
                 * not be upheld under these circumstances. Therefore, we include the previous
                 * result in the calculation, merging [-1,-1] with the new result [0,0], obtaining
                 * [-1,0] as the result in the second loop iteration.
                 *
                 * Omitting the call to 'opaque' earlier, an InferredFactNode would be placed above
                 * the PiNode delaying the evaluation of the PiNode until the branch is detected as
                 * reachable. This would under normal circumstances fix the issue with reduced
                 * precision by cutting out the evaluation of the first iteration, which pollutes
                 * the result.
                 */
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
                 * reachable inputs which results in the range [1,2]. In the second iteration this
                 * PHI would only 1 reachable input [1,1] if the transfer function for the PiNode
                 * would not comply with monotonicity. This PHI acts as an error detection mechanism
                 * by creating a visible violation of monotonicity at a control-flow-associated
                 * node.
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
        int constantOne = DFAnalysisLSCCPTest.conditionalConstant(a, 1);
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
