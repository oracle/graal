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
package jdk.graal.compiler.loop.test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import org.junit.Assert;
import org.junit.Test;

public class LoopInversionRegressionTest extends GraalCompilerTest {

    public static void main() {
        try {
            TT g = new TT();
            g.a();
        } catch (Exception ex) {
        }
    }

    static class TT {
        void a() {
            int c = 1;
            for (; c < 7; c++) {
                D.e();
            }
        }

    }

    static class H {
        static long[] i;
    }

    static class D {
        static long j;

        static void e() {
            int k;
            for (k = -400; k < 3; ++k) {
                j /= H.i[k - 1] = (long) 22.628F;
            }
        }
    }

    @Test
    public void testFuzz() {
        test("main");
    }

    public static int tailCountedLoop(int limit) {
        int i = 0;
        do {
            i++;
        } while (i < limit);
        return i;
    }

    /**
     * Builds the inverted-loop shape used to test the first body update. For example:
     *
     * <pre>
     * int i = Integer.MAX_VALUE;
     * do {
     *     body(i);
     * } while (++i &lt; limit);
     * </pre>
     *
     * The body/root IV is {@code i}; the checked IV is its next value {@code i + 1}. The first increment
     * overflows to {@code Integer.MIN_VALUE} before the condition is tested. A proof about subsequent checked
     * increments must not imply that this first update was safe.
     *
     * <p>Mark the parsed loop as compiler-inverted and assert that counted-loop analysis finds distinct body
     * and checked IVs and proves the checked IV safe. The calling tests then check the body IV separately: an
     * entry comparison can make its first update safe, while the boundary-start examples must fail that
     * proof.
     */
    private Loop getInvertedCountedLoop(String snippet) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        LoopsData loopsData = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph);
        Assert.assertEquals(1, loopsData.loops().size());
        Loop loop = loopsData.loops().get(0);
        loop.loopBegin().setCompilerInverted();
        Assert.assertTrue(loop.detectCounted());
        Assert.assertTrue(loop.counted().isInverted());
        Assert.assertNotSame(loop.counted().getLimitCheckedIV(), loop.counted().getBodyIV());
        Assert.assertTrue(loop.counted().counterNeverOverflows());
        return loop;
    }

    public static int entryGuardedTailCountedUp(int start, int limit) {
        if (start >= limit) {
            return 0;
        }
        int i = start;
        do {
            i++;
        } while (i < limit);
        return i;
    }

    public static int entryGuardedTailCountedDown(int start, int limit) {
        if (start <= limit) {
            return 0;
        }
        int i = start;
        do {
            i--;
        } while (i > limit);
        return i;
    }

    @Test
    public void testTailCountedDetection() {
        Loop loop = getInvertedCountedLoop("tailCountedLoop");
        Assert.assertTrue(loop.counted().ivCanNeverOverflow(loop.counted().getBodyIV()));
        Loop up = getInvertedCountedLoop("entryGuardedTailCountedUp");
        Assert.assertTrue(up.counted().ivCanNeverOverflow(up.counted().getBodyIV()));
        Loop down = getInvertedCountedLoop("entryGuardedTailCountedDown");
        Assert.assertTrue(down.counted().ivCanNeverOverflow(down.counted().getBodyIV()));
    }

    public static int initiallyWrappingUp(int limit) {
        int i = Integer.MAX_VALUE;
        int negativeIterations = 0;
        do {
            if (i < 0) {
                negativeIterations++;
            }
        } while (++i < limit);
        return negativeIterations;
    }

    @Test
    public void testInitiallyWrappingUp() {
        Loop loop = getInvertedCountedLoop("initiallyWrappingUp");
        Assert.assertFalse(loop.counted().ivCanNeverOverflow(loop.counted().getBodyIV()));
        test("initiallyWrappingUp", Integer.MIN_VALUE + 2);
    }

    public static int initiallyWrappingDown(int limit) {
        int i = Integer.MIN_VALUE;
        int positiveIterations = 0;
        do {
            if (i > 0) {
                positiveIterations++;
            }
        } while (--i > limit);
        return positiveIterations;
    }

    @Test
    public void testInitiallyWrappingDown() {
        Loop loop = getInvertedCountedLoop("initiallyWrappingDown");
        Assert.assertFalse(loop.counted().ivCanNeverOverflow(loop.counted().getBodyIV()));
        test("initiallyWrappingDown", Integer.MAX_VALUE - 2);
    }

}
