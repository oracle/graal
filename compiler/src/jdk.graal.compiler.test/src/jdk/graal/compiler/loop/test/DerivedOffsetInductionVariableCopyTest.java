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

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.loop.DerivedOffsetInductionVariable;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.InductionVariableHelper;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerSubExactNode;

public class DerivedOffsetInductionVariableCopyTest extends GraalCompilerTest {

    public static int subtractOffset(int n) {
        int sum = 0;
        for (int i = 5; i != n; i++) {
            sum += Math.subtractExact(i, 5);
        }
        return sum;
    }

    public static int subtractBase(int n) {
        int sum = 0;
        for (int i = 5; i != n; i++) {
            sum += Math.subtractExact(5, i);
        }
        return sum;
    }

    private DerivedOffsetInductionVariable getOffsetIV(String snippet) {
        StructuredGraph graph = parseEager(snippet, StructuredGraph.AllowAssumptions.NO);
        LoopsData loops = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph);
        Assert.assertEquals(1, loops.loops().size());
        Loop loop = loops.loops().get(0);
        for (InductionVariable iv : loop.getInductionVariables().getValues()) {
            if (iv instanceof DerivedOffsetInductionVariable derived && derived.valueNode() instanceof IntegerSubExactNode) {
                return derived;
            }
        }
        throw new AssertionError("Expected an exact-subtraction induction variable");
    }

    private void checkCopy(String snippet, boolean previous) {
        DerivedOffsetInductionVariable iv = getOffsetIV(snippet);
        Assert.assertEquals(0L, iv.constantInit());
        long stride = iv.constantStride();
        InductionVariable copy = previous ? InductionVariableHelper.previousIteration(iv) : InductionVariableHelper.nextIteration(iv);
        long expected = previous ? -stride : stride;
        Assert.assertEquals(expected, copy.constantInit());
        Assert.assertEquals(expected, copy.initNode().asJavaConstant().asLong());
        Assert.assertEquals(stride, copy.constantStride());
    }

    @Test
    public void previousSubtractOffset() {
        checkCopy("subtractOffset", true);
    }

    @Test
    public void nextSubtractOffset() {
        checkCopy("subtractOffset", false);
    }

    @Test
    public void previousSubtractBase() {
        checkCopy("subtractBase", true);
    }

    @Test
    public void nextSubtractBase() {
        checkCopy("subtractBase", false);
    }

    public static long boundsSnippet(int b) {
        long sum = 0;
        int[] array = new int[3000];
        int limit = -b;
        int i = Integer.MIN_VALUE + 5;
        while (i != limit) {
            sum += array[Math.subtractExact(i, Integer.MIN_VALUE + 5)];
            i++;
        }
        return sum * 31 + i * 7;
    }

    @Test
    public void compiledBoundsException() {
        for (int i = 0; i < 100; i++) {
            boundsSnippet(Integer.MAX_VALUE - 104);
        }
        test("boundsSnippet", Integer.MAX_VALUE - 3005);
    }
}
