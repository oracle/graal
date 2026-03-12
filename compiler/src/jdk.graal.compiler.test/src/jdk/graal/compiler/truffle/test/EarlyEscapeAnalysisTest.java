/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.EarlyEscapeAnalysis;
import com.oracle.truffle.api.CompilerDirectives.EarlyInline;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;

import jdk.graal.compiler.nodes.virtual.VirtualInstanceNode;

public class EarlyEscapeAnalysisTest extends PartialEvaluationTest {

    @Test
    public void testEarlyEscapeAnalysis() {
        assertPartialEvalEquals(() -> 42, EarlyEscapeAnalysisTest::testEarlyEscapeBasic);
        assertPartialEvalEquals(() -> 42, EarlyEscapeAnalysisTest::testEarlyEscapeReassign);
        assertPartialEvalEquals(() -> 42, EarlyEscapeAnalysisTest::testEarlyEscapeEarlyInline);

        assertPartialEvalEquals(EarlyEscapeAnalysisTest::earlyNotEscaping1Expected,
                        EarlyEscapeAnalysisTest::earlyNotEscaping1Actual);
    }

    @EarlyEscapeAnalysis
    private static int testEarlyEscapeBasic() {
        TestEscape v = new TestEscape(42);
        CompilerAsserts.partialEvaluationConstant(v.value);
        return v.value;
    }

    @EarlyEscapeAnalysis
    private static int testEarlyEscapeReassign() {
        TestEscape v = new TestEscape(41);
        v.value = 42;
        CompilerAsserts.partialEvaluationConstant(v.value);
        return v.value;
    }

    @EarlyEscapeAnalysis
    private static int testEarlyEscapeEarlyInline() {
        TestEscape v = new TestEscape(41);
        earlyInline(v);
        CompilerAsserts.partialEvaluationConstant(v.value);
        return v.value;
    }

    @EarlyInline
    private static void earlyInline(TestEscape escape) {
        escape.value = 42;
    }

    private static int earlyNotEscaping1Expected() {
        // same as not escape analysed
        TestEscape v = new TestEscape(42);
        escape(v);
        return v.value;
    }

    @EarlyEscapeAnalysis
    private static int earlyNotEscaping1Actual() {
        TestEscape v = new TestEscape(42);
        escape(v); // escapes the value, hence not escape analysied
        return v.value;
    }

    private static void escape(@SuppressWarnings("unused") TestEscape escape) {
    }

    static class TestEscape {

        int value;

        TestEscape(int value) {
            this.value = value;
        }

    }

    @Test
    public void testEarlyEscapeLoopExplosion() {
        /*
         * Tests VirtualObjects that are created above a merge exploded loop but only used inside
         * the loop. The VirtualObjects must not be duplicated by PE.
         */
        testMergeExplodeHelper(earlyPEAAboveExplodedLoopRoot(), 1, -84);
        /*
         * Tests VirtualObjects that are created in exploded loop but only used inside the loop. The
         * VirtualObjects must be duplicated by PE.
         */
        testMergeExplodeHelper(earlyPEAInExplodedLoopRoot(), 3, 3);
    }

    private void testMergeExplodeHelper(RootNode root, int expectedVirtualInstancesAfterPE, int expectedResult) {
        var graph = partialEval(root);

        int virtualInstanceCount = 0;
        for (var vi : graph.getNodes(VirtualInstanceNode.TYPE)) {
            if (vi.type().toJavaName().equals(TestEscape.class.getName())) {
                virtualInstanceCount++;
            }
        }
        Assert.assertEquals("Incorrect number of virtual instanceof of type " + TestEscape.class.getSimpleName() + " after PEA and PE.", expectedVirtualInstancesAfterPE, virtualInstanceCount);

        OptimizedCallTarget target = compileHelper("snippet", root, new Object[0]);
        Assert.assertEquals(expectedResult, target.call());
    }

    @CompilerDirectives.TruffleBoundary
    private static int boundary(int val) {
        return val;
    }

    private static RootNode earlyPEAAboveExplodedLoopRoot() {
        return new RootNode(null) {

            @Override
            public Object execute(VirtualFrame frame) {
                return testMergeExplode();
            }

            @EarlyEscapeAnalysis
            @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
            private static int testMergeExplode() {
                int[] states = new int[]{0, 1, 1, 1};
                int result = 0;

                TestEscape v = new TestEscape(42);

                for (int state : states) {
                    switch (state) {
                        case 0:
                            result += boundary(v.value);
                            break;
                        case 1:
                            result -= boundary(v.value);
                            break;
                    }
                }

                return result;
            }
        };
    }

    private static RootNode earlyPEAInExplodedLoopRoot() {
        return new RootNode(null) {

            @Override
            public Object execute(VirtualFrame frame) {
                return testMergeExplode();
            }

            @EarlyEscapeAnalysis
            @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
            private static Integer testMergeExplode() {
                int[] states = new int[]{0, 0, 0, 1};
                int result = 0;
                TestEscape[] frame = new TestEscape[10];
                int sp = 0;

                for (int state : states) {
                    switch (state) {
                        case 0:
                            frame[sp++] = new TestEscape(boundary(sp));
                            break;
                        case 1:
                            if (frame[sp - 1] == frame[sp - 2]) {
                                Assert.fail("VirtualObjects are identical after merge explosion!");
                            }
                            result = frame[--sp].value;
                            break;
                    }
                }

                return result;
            }
        };
    }

}
