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
package jdk.graal.compiler.phases.common.priorityinline.test;

import static jdk.graal.compiler.debug.DebugOptions.DumpOnError;

import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;

public class PEAPriorityInliningRecursiveTest extends PriorityInliningTest {
    public static int IntSideEffect;

    public static class A {
        int x;

        A(int x) {
            this.x = x;
        }
    }

    public static class B {
        A x;

        B(A x) {
            this.x = x;
        }
    }

    static B BSideEffect;

    public static int recursiveMethod1(int a) {
        if (GraalDirectives.injectBranchProbability(0.01D, a <= 0)) {
            return 0;
        }
        int res = 0;
        A aO = new A(12);
        for (int i = 0; GraalDirectives.injectIterationCount(100000000, i < a); i++) {
            res += new A(recursiveMethod2(a - 1, new A(i))).x;
            B o;
            if (IntSideEffect > 0) {
                o = BSideEffect;
            } else {
                o = new B(aO);
            }
            res += o.x.x;
        }
        return res;
    }

    public static Object OSideEffect;

    public static int recursiveMethod2(int a, A o) {
        if (GraalDirectives.injectBranchProbability(0.01D, a <= 0)) {
            return 0;
        }
        int res = o.x;
        for (int i = 0; GraalDirectives.injectIterationCount(100000000, i < a); i++) {
            res += new A(recursiveMethod1(a - 1)).x + o.x;
        }
        OSideEffect = o;
        return res;
    }

    @Test
    public void testRecursiveInlining() {
        try (AutoCloseable _ = new TTY.Filter()) {
            OptionValues options = new OptionValues(getInitialOptions(), DumpOnError, false, GraalOptions.EscapeAnalysisLoopCutoff, 15);
            StructuredGraph g = parseEager("recursiveMethod1", AllowAssumptions.NO);
            PhaseSuite<HighTierContext> graphBuilderSuite = getDefaultGraphBuilderSuite();
            HighTierContext context = new HighTierContext(getProviders(), graphBuilderSuite, OptimisticOptimizations.ALL);
            createCanonicalizerPhase().apply(g, context);
            new PriorityInliningPhase(createCanonicalizerPhase(), options).apply(g, context);
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }
}
