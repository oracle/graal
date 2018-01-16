/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.compiler.truffle.test;

import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.TruffleInlining;
import org.graalvm.compiler.truffle.runtime.TruffleInliningDecision;
import org.junit.Assert;
import org.junit.Test;

public class PerformanceTruffleInliningTest extends TruffleInliningTest {

    @Test
    public void testThreeTangledRecursions() {
        // @formatter:off
        OptimizedCallTarget target = builder.
                target("three").
                    calls("three").
                    calls("two").
                    calls("one").
                target("two").
                    calls("two").
                    calls("one").
                    calls("three").
                target("one").
                    calls("one").
                    calls("two").
                    calls("three").
                buildTarget();
        // @formatter:on
        final TruffleInlining truffleInliningDecisions = assertNumberOfDecisions(target, 1170);
        assertOnlyOneCallSiteExplored(truffleInliningDecisions);
    }

    @Test
    public void testFourTangledRecursions() {
        // @formatter:off
        OptimizedCallTarget target = builder.
                target("four").
                    calls("four").
                    calls("three").
                    calls("two").
                    calls("one").
                target("three").
                    calls("three").
                    calls("two").
                    calls("one").
                    calls("four").
                target("two").
                    calls("two").
                    calls("one").
                    calls("three").
                    calls("four").
                target("one").
                    calls("one").
                    calls("two").
                    calls("three").
                    calls("four").
                buildTarget();
        // @formatter:on
        final TruffleInlining truffleInliningDecisions = assertNumberOfDecisions(target, 612);
        assertOnlyOneCallSiteExplored(truffleInliningDecisions);
    }

    @Test
    public void testTangledGraph() {
        int depth = 15;
        for (int i = 0; i < depth; i++) {
            builder.target(Integer.toString(i));
            for (int j = i; j < depth; j++) {
                builder.calls(Integer.toString(j));
            }
        }
        OptimizedCallTarget target = builder.target("main").calls("0").buildTarget();
        final TruffleInlining truffleInliningDecisions = assertNumberOfDecisions(target, 569);
        assertOnlyOneCallSiteExplored(truffleInliningDecisions);

    }

    long targetCount = 0;

    private void hugeGraphBuilderHelper(final int depth, final int width, final String targetIndex) {
        builder.target(targetIndex);
        targetCount++;
        if (depth == 0) {
            return;
        }
        for (int i = 0; i < width; i++) {
            builder.calls(targetIndex + i);
        }
        for (int i = 0; i < width; i++) {
            hugeGraphBuilderHelper(depth - 1, width, targetIndex + i);
        }
    }

    @Test
    public void testHugeGraph() {
        hugeGraphBuilderHelper(10, 4, "1");
        OptimizedCallTarget target = builder.target("main").calls("1").buildTarget();
        final TruffleInlining truffleInliningDecisions = assertNumberOfDecisions(target, 1045);
        assertOnlyOneCallSiteExplored(truffleInliningDecisions);

    }

    // This is used as a replacement for timed tests as they have been shown to be unstable
    private TruffleInlining assertNumberOfDecisions(OptimizedCallTarget target, int count) {
        TruffleInlining decisions = new TruffleInlining(target, policy);
        Assert.assertEquals("Wrong number of decisions!", count, countDecisions(decisions));
        return decisions;
    }

    private static int countDecisions(TruffleInlining decisions) {
        int count = 0;
        for (TruffleInliningDecision decision : decisions) {
            count++;
            count += countDecisions(decision);
        }
        return count;
    }

    private static void assertOnlyOneCallSiteExplored(TruffleInlining truffleInliningDecisions) {
        int knowsCallSites = 0;
        for (TruffleInliningDecision decision : truffleInliningDecisions) {
            if (decision.getCallSites().size() > 0) {
                knowsCallSites++;
            }
        }
        // The exploration brudged should be blown before exploring the other 2 call sites of the
        // root
        Assert.assertEquals("Only one target should not know about it's call sites!", 1, knowsCallSites);
    }
}
