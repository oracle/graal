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

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.loop.phases.CountedStripMiningPhase;
import jdk.graal.compiler.loop.phases.RangeCheckEliminationPhase;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.loop.DerivedConvertedInductionVariable;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;

/**
 * Test special induction variables with {@link RangeCheckEliminationPhase} that do not
 * optimize generally.
 */
public class RangeCheckEliminationIVTest extends GraalCompilerTest {

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        LoopsData ld = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph);
        ld.detectCountedLoops();
        boolean foundPINodeIV = false;
        for (Loop lex : ld.countedLoops()) {
            for (InductionVariable iv : lex.getInductionVariables().getValues()) {
                if (iv instanceof DerivedConvertedInductionVariable) {
                    if (((DerivedConvertedInductionVariable) iv).valueNode() instanceof PiNode) {
                        foundPINodeIV = true;
                    }
                }
            }
        }
        Assert.assertTrue("Must find a pi IV among others", foundPINodeIV);
    }

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        super.checkLowTierGraph(graph);
        boolean foundStripMined = false;
        for (LoopBeginNode lb : graph.getNodes(LoopBeginNode.TYPE)) {
            if (lb.isAnyStripMinedInner()) {
                foundStripMined = true;
            }
        }
        Assert.assertTrue("Must strip mine loops", foundStripMined);
    }

    @SuppressWarnings("all")
    public static int lastIndexOf(char[] source, int sourceOffset, int sourceCount,
                    char[] target, int targetOffset, int targetCount,
                    int fromIndex) {
        /*
         * Check arguments; return immediately where possible. For consistency, don't check for null
         * str.
         */
        int rightIndex = sourceCount - targetCount;
        if (fromIndex < 0) {
            return -1;
        }
        if (fromIndex > rightIndex) {
            fromIndex = rightIndex;
        }
        /* Empty string always matches. */
        if (targetCount == 0) {
            return fromIndex;
        }

        int strLastIndex = targetOffset + targetCount - 1;
        char strLastChar = target[strLastIndex];
        int min = sourceOffset + targetCount - 1;
        int i = min + fromIndex;
        startSearchForLastChar: while (true) {
            while (i >= min && source[i] != strLastChar) {
                GraalDirectives.sideEffect(1);
                i--;
            }
            if (i < min) {
                return -1;
            }
            int j = i - 1;
            int start = j - (targetCount - 1);
            int k = strLastIndex - 1;
            long ivLong = 0;

            while (j > start) {
                if (GraalDirectives.positivePi(ivLong) > SomeLongConstant) {
                    GraalDirectives.deoptimizeAndInvalidate();
                }
                if (source[j--] != target[k--]) {
                    i--;
                    continue startSearchForLastChar;
                }
                ivLong++;
            }
            return start - sourceOffset + 1;
        }
    }

    public static long SomeLongConstant = 1000;

    public static String simple(String simpleName) {
        char[] value = simpleName.toCharArray();
        char[] target = ".".toCharArray();
        int lastDotIndex = lastIndexOf(value, 0, value.length,
                        target, 0, target.length, value.length);
        if (lastDotIndex < 0) {
            return null;
        }
        GraalDirectives.deoptimize();
        return simpleName.substring(0, lastDotIndex);
    }

    @Override
    protected OptimisticOptimizations getOptimisticOptimizations() {
        // Disable profile based optimizations
        return OptimisticOptimizations.NONE;
    }

    @Test
    public void testConditionalExit() {
        OptionValues opt = new OptionValues(getInitialOptions(), CountedStripMiningPhase.Options.StripMineALot, true);
        test(opt, "simple", Object.class.getName());
    }
}
