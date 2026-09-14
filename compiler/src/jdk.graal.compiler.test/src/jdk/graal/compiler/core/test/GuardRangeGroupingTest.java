/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.phases.HighTier;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase;
import jdk.vm.ci.code.InstalledCode;

/// Tests [jdk.graal.compiler.guards.GuardRangeGroupingPhase] with bounds around integer overflows.
public class GuardRangeGroupingTest extends GraalCompilerTest {

    /// Expected number of deoptimization nodes while a snippet graph is being checked.
    int expectedDeopts = -1;

    @BytecodeParserForceInline
    public static int piStamped(int i) {
        if (i < 0x100 || i > 0xFFF00) {
            GraalDirectives.deoptimizeAndInvalidate();
        }
        return i & 0xFFF00;
    }

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        super.checkLowTierGraph(graph);
        if (expectedDeopts > 0) {
            Assert.assertEquals(expectedDeopts, graph.getNodes(DeoptimizeNode.TYPE).count());
        }
    }

    /// Supplies bounds checks for the range grouping scenarios.
    static int[] array = new int[10];

    @BytecodeParserNeverInline
    static int testSnippet01(int i) {
        int res = 0;
        res += array[i - Integer.MAX_VALUE];
        res += array[i + Integer.MAX_VALUE];

        /*
         * Assumption is that this range check can be eliminated because offset 0 is between
         * -Integer.MAX_VALUE <= 0 <= Integer.MAX_VALUE. However, for i = Integer.MAX_VALUE + 2 the
         * addition value results are out of bounds because they overflow. Thus, GuardRangeGrouping
         * must not remove this guard because of the other two guards.
         */
        res += array[i];

        return res;
    }

    static void caller01() {
        try {
            testSnippet01(Integer.MAX_VALUE + 2);
            throw new RuntimeException("No ArrayIndexOutOfBoundsException thrown for test1!");
        } catch (ArrayIndexOutOfBoundsException e) {
            // Expected
        }
    }

    @BytecodeParserNeverInline
    static int testSnippet02(int i) {
        int res = 0;
        /*
         * Predicates for i = -2147483647 are the following: i + -2147483646 >= 0 for the lower
         * portion and i + Integer.MAX_VALUE < length (10) for the higher portion. But with i =
         * -Integer.MAX_VALUE the first predicate underflows to 2 which satisfies >= 0. When
         * optimizing away the last check we are simply missing correct bounds checks because with
         * the overflows the other 2 guards checked other bounds.
         */
        res += array[i - 2147483647];
        res += array[i + Integer.MAX_VALUE];
        res += array[i + -2];
        return res;
    }

    static void caller02() {
        try {
            testSnippet02(-Integer.MAX_VALUE);
            throw new RuntimeException("No ArrayIndexOutOfBoundsException thrown for test2!");
        } catch (ArrayIndexOutOfBoundsException e) {
            // Expected
        }
    }

    @BytecodeParserNeverInline
    static int testSnippet03(int i) {
        int res = 0;
        int effectiveI = piStamped(i);
        /*
         * We allow optimizing the third guard here because we now that effectiveI has a stamp where
         * -22 and +44 will not overflow in either direction.
         */
        res += array[effectiveI - 22];
        res += array[effectiveI + 44];
        res += array[effectiveI];
        return res;
    }

    static void caller03() {
        try {
            testSnippet03(Integer.MAX_VALUE + 2);
            throw new RuntimeException("No ArrayIndexOutOfBoundsException thrown for test1!");
        } catch (ArrayIndexOutOfBoundsException e) {
            // Expected
        }
    }

    OptionValues getOptionsNoInline() {
        return new OptionValues(getInitialOptions(), GraalOptions.OptDeoptimizationGrouping, false, PriorityInliningPhase.Options.UsePriorityInlining, false, HighTier.Options.Inline, false);
    }

    @Test
    public void testMustNotFold01() {
        expectedDeopts = 3;
        InstalledCode c = getCode(getResolvedJavaMethod("testSnippet01"), null, true, true, getOptionsNoInline());
        expectedDeopts = -1;
        test(getOptionsNoInline(), "caller01");
        assert !c.isValid() : "Must have deopted because of IOOBE";
    }

    @Test
    public void testMustNotFold02() {
        expectedDeopts = 3;
        InstalledCode c = getCode(getResolvedJavaMethod("testSnippet02"), null, true, true, getOptionsNoInline());
        expectedDeopts = -1;
        test(getOptionsNoInline(), "caller02");
        assert !c.isValid() : "Must have deopted because of IOOBE";
    }

    @Test
    public void testMustFold01() {
        expectedDeopts = 3;
        InstalledCode c = getCode(getResolvedJavaMethod("testSnippet03"), null, true, true, getOptionsNoInline());
        expectedDeopts = -1;
        test(getOptionsNoInline(), "caller03");
        assert !c.isValid() : "Must have deopted because of IOOBE";
    }

}
