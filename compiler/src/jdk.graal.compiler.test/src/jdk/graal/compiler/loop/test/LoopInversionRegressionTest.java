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

    @Test
    public void testTailCountedDetection() {
        StructuredGraph graph = parseEager("tailCountedLoop", AllowAssumptions.YES);
        LoopsData loopsData = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph);
        Assert.assertEquals(1, loopsData.loops().size());
        Loop loop = loopsData.loops().get(0);
        Assert.assertTrue(loop.detectCounted());
        Assert.assertNotNull(loop.counted().getTripCountLimit());
        loop.loopBegin().setCompilerInverted();
        loop.resetCounted();
        Assert.assertTrue(loop.detectCounted());
        Assert.assertTrue(loop.counted().isInverted());
    }

}
