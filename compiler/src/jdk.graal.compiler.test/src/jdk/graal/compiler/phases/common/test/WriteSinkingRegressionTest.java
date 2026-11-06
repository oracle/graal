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

package jdk.graal.compiler.phases.common.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.phases.LowTier;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.loop.phases.LoopInversionPhase;
import jdk.graal.compiler.loop.phases.NonCountedStripMiningPhase;
import jdk.graal.compiler.options.OptionValues;

/**
 * A small set of test checking the capabilities of the write sinking optimization.
 * <p>
 * In particular, it performs both behavior tests (in which graph shapes are checked) and
 * correctness tests.
 */
public class WriteSinkingRegressionTest extends GraalCompilerTest {

    static final class HashMapFromScala {
        Object[] arrayStack = new Object[6]; // array stack
        int[] posStack = new int[6];
        int posD = 0;
        int depth;

        Object[] arrayD;
        Object[] elems1;

        Object next() {
            return next0();
        }

        Object next0() {
            Object[] elems = arrayD;
            int i = posD;

            while (true) {

                if (i == elems.length - 1) { // reached end of level, pop stack
                    depth -= 1;
                    if (depth >= 0) {
                        arrayD = (Object[]) arrayStack[depth];
                        posD = posStack[depth];
                        arrayStack[depth] = null;
                    } else {
                        arrayD = null;
                        posD = 0;
                    }
                } else {
                    posD += 1;
                }

                if (elems != null && i < elems.length && elems[i] != null && elems[i] instanceof HashMapFromScala) {
                    if (depth >= 0) {
                        arrayStack[depth] = arrayD;
                        posStack[depth] = posD;
                    }
                    depth += 1;
                    Object[] tmp = ((HashMapFromScala) elems[i]).elems1;
                    arrayD = tmp;
                    posD = 0;
                    elems = tmp;
                    i = 0;
                    continue;
                }
                return null;
            }
        }
    }

    public static void testSnippet(HashMapFromScala hm) {
        hm.next();
    }

    @Test
    public void test() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.StressExplicitExceptionCode, true, GraalOptions.LoopPeeling, false, LoopInversionPhase.Options.LoopInversion, false,
                        GraalOptions.SpeculativeGuardMovement, false, GraalOptions.OptConvertDeoptsToGuards, false, NonCountedStripMiningPhase.Options.NonCountedStripMiningForceStripAll, true,
                        GraalOptions.OptFloatingReads, true);

        HashMapFromScala hmf = init(new Object[]{new Object()}, true);
        hmf.arrayD[0] = init(new Object[]{new Object()}, true);
        testSnippet(hmf);

        test(opt, "testSnippet", new GraalCompilerTest.ArgSupplier() {
            @SuppressWarnings("hiding")
            @Override
            public Object get() {
                HashMapFromScala hmf = init(new Object[]{new Object()}, true);
                hmf.arrayD[0] = init(new Object[]{new Object()}, true);
                hmf.elems1 = new Object[]{hmf};
                return hmf;
            }
        });

        test(opt, "testSnippet", new GraalCompilerTest.ArgSupplier() {
            @SuppressWarnings("hiding")
            @Override
            public Object get() {
                HashMapFromScala hmf = init(new Object[]{new Object()}, true);
                hmf.arrayD[0] = init(new Object[]{new Object()}, true);
                return hmf;
            }
        });

        test(opt, "testSnippet", new GraalCompilerTest.ArgSupplier() {
            @SuppressWarnings("hiding")

            @Override
            public Object get() {
                HashMapFromScala hmf = init(new Object[]{null}, true);
                return hmf;
            }
        });
        test(opt, "testSnippet", new GraalCompilerTest.ArgSupplier() {
            @SuppressWarnings("hiding")
            @Override
            public Object get() {
                HashMapFromScala hmf = init(new Object[]{null}, false);
                return hmf;
            }
        });

        OptionValues opt2 = new OptionValues(getInitialOptions(), GraalOptions.StressExplicitExceptionCode, false, GraalOptions.LoopPeeling, false, LoopInversionPhase.Options.LoopInversion, false,
                        GraalOptions.SpeculativeGuardMovement, false, GraalOptions.OptConvertDeoptsToGuards, false, NonCountedStripMiningPhase.Options.NonCountedStripMiningForceStripAll, false,
                        GraalOptions.OptFloatingReads, true, GraalOptions.OptDuplication, false, GraalOptions.PartialUnroll, false);
        test(opt2, "foo", new X(), 10);
    }

    static int foo(X x, int limit) {
        if (x == null) {
            return 0;
        }
        int res = 0;
        for (int i = 0; i < limit; i++) {
            if (i == 43) {
                // do nothing
            } else {
                x.x = i;
            }
            if (i == 2) {
                res = x.x;
                GraalDirectives.deoptimize();
            }
            S = 12;
            x.x = i;
        }
        return res;
    }

    static int S;

    static class X {
        int x;
    }

    public static void nestedLoopWrite(X x, int[] counts, int limit) {
        for (int j = 0; j < limit; j++) {
            for (int i = 0; i < counts[j]; i++) {
                x.x = i;
            }
        }
    }

    @Test
    public void testGR81079NestedLoopZeroTrip() {
        OptionValues options = new OptionValues(getInitialOptions(), GraalOptions.GenLoopSafepoints, false, LowTier.Options.OptWriteSinking, true);
        var method = getResolvedJavaMethod("nestedLoopWrite");
        int[][] counts = {{}, {0, 0, 0}, {0, 2, 0}, {2, 0, 0}, {0, 0, 2}, {0, 0, 0, 0}, {0, 2, 0, 0}};
        int[] expectedValues = {7, 7, 1, 1, 1, 7, 1};
        for (int caseIndex = 0; caseIndex < counts.length; caseIndex++) {
            X expected = new X();
            expected.x = 7;
            X actual = new X();
            actual.x = 7;
            Result expectedResult = executeExpected(method, null, expected, counts[caseIndex], counts[caseIndex].length);
            Result actualResult = executeActual(options, method, null, actual, counts[caseIndex], counts[caseIndex].length);
            assertEquals(expectedResult, actualResult);
            Assert.assertEquals(expectedValues[caseIndex], expected.x);
            Assert.assertEquals(expected.x, actual.x);
        }
    }

    public static void adjacentInnerLoopWrites(X x, int[] first, int[] second, int limit) {
        for (int j = 0; j < limit; j++) {
            for (int i = 0; i < first[j]; i++) {
                x.x = i;
            }
            for (int i = 0; i < second[j]; i++) {
                x.x = i + 10;
            }
        }
    }

    @Test
    public void testGR81079AdjacentInnerLoops() {
        OptionValues options = new OptionValues(getInitialOptions(), GraalOptions.GenLoopSafepoints, false, LowTier.Options.OptWriteSinking, true);
        var method = getResolvedJavaMethod("adjacentInnerLoopWrites");
        int[][] first = {{0, 0, 0}, {0, 1, 0}, {0, 0, 0}, {1, 0, 0}, {0, 1, 0}, {1, 0, 0}, {0, 0, 1}};
        int[][] second = {{0, 0, 0}, {0, 0, 0}, {0, 1, 0}, {0, 0, 0}, {0, 1, 0}, {0, 1, 0}, {1, 0, 0}};
        int[] expectedValues = {7, 0, 10, 0, 10, 10, 0};
        for (int caseIndex = 0; caseIndex < first.length; caseIndex++) {
            X expected = new X();
            expected.x = 7;
            X actual = new X();
            actual.x = 7;
            Result expectedResult = executeExpected(method, null, expected, first[caseIndex], second[caseIndex], 3);
            Result actualResult = executeActual(options, method, null, actual, first[caseIndex], second[caseIndex], 3);
            assertEquals(expectedResult, actualResult);
            Assert.assertEquals(expectedValues[caseIndex], expected.x);
            Assert.assertEquals(expected.x, actual.x);
        }
    }

    private static HashMapFromScala init(Object[] objects, boolean assign) {
        HashMapFromScala h = new HashMapFromScala();
        h.arrayD = objects;
        if (assign) {
            h.elems1 = objects;
            h.depth = 1;
        }
        return h;
    }

    static int deopt(X x, int limit) {
        for (int i = 0; i < limit; i++) {
            x.x = i;
            if (i == 123) {
                GraalDirectives.deoptimizeAndInvalidate();
            }
        }
        return x.x;
    }

    @Test
    public void test01() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.StressExplicitExceptionCode, false, GraalOptions.LoopPeeling, false, LoopInversionPhase.Options.LoopInversion, false,
                        GraalOptions.SpeculativeGuardMovement, false, GraalOptions.OptConvertDeoptsToGuards, false, NonCountedStripMiningPhase.Options.NonCountedStripMiningForceStripAll, false,
                        GraalOptions.OptFloatingReads, true, GraalOptions.OptDuplication, false, GraalOptions.PartialUnroll, false);
        test(opt, "deopt", new X(), 10);
    }

}
