/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test.nodes.explosion;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.debug.BlackholeNode;
import org.graalvm.compiler.truffle.test.nodes.AbstractTestNode;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;

import jdk.vm.ci.meta.JavaConstant;

public class UnrollingTestNode {

    public static volatile int SideEffect;
    public static volatile int SideEffect1;
    public static volatile int SideEffect2;
    public static volatile int SideEffect3;

    private final int count;

    public UnrollingTestNode(int count) {
        this.count = count;
    }

    public static final String OUTSIDE_LOOP_MARKER = "OUTSIDE_LOOP_MARKER";
    public static final String INSIDE_LOOP_MARKER = "INSIDE_LOOP_MARKER";
    public static final String INSIDE_LOOP_MARKER_2 = "INSIDE_LOOP_MARKER_2";
    public static final String OUTER_LOOP_INSIDE_LOOP_MARKER = "OUTER_LOOP_INSIDE_LOOP_MARKER";
    public static final String CONTINUE_LOOP_MARKER = "CONTINUE_LOOP_MARKER";
    public static final String AFTER_LOOP_MARKER = "AFTER_LOOP_MARKER";

    public static int countBlackholeNodes(StructuredGraph graph, String val) {
        int count = 0;
        for (BlackholeNode bh : graph.getNodes().filter(BlackholeNode.class)) {
            ValueNode v = bh.getValue();
            if (v.isConstant()) {
                JavaConstant jc = v.asJavaConstant();
                if (jc.toValueString().replaceAll("\"", "").equals(val)) {
                    count++;
                }
            }
        }
        return count;
    }

    public class UnrollOnlyExample extends AbstractTestNode {

        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL)
        @Override
        public int execute(VirtualFrame frame) {
            for (int i = 0; i < count; i++) {
                GraalDirectives.blackhole(INSIDE_LOOP_MARKER);
            }
            GraalDirectives.blackhole(AFTER_LOOP_MARKER);
            return count;
        }
    }

    public class ExplodeAlongLoopEndExample extends AbstractTestNode {

        @ExplodeLoop(kind = LoopExplosionKind.FULL_EXPLODE)
        @Override
        public int execute(VirtualFrame frame) {
            for (int i = 0; i < count; i++) {
                GraalDirectives.blackhole(INSIDE_LOOP_MARKER);
                SideEffect = i;
                if (i == SideEffect2) {
                    // exit the loop
                    GraalDirectives.blackhole(OUTSIDE_LOOP_MARKER);
                    GraalDirectives.blackhole(i);
                    continue;
                }
            }
            GraalDirectives.blackhole(AFTER_LOOP_MARKER);
            return count;
        }
    }

    public class FullUnrollUntilReturnExample extends AbstractTestNode {

        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
        @Override
        public int execute(VirtualFrame frame) {
            for (int i = 0; i < count; i++) {
                GraalDirectives.blackhole(INSIDE_LOOP_MARKER);
                SideEffect = i;
                if (i == SideEffect2) {
                    // exit the loop
                    GraalDirectives.blackhole(OUTSIDE_LOOP_MARKER);
                    GraalDirectives.blackhole(i);
                    CompilerAsserts.partialEvaluationConstant(i);
                    return i;
                }
                // random code
                // loop end -> unroll
            }
            GraalDirectives.blackhole(AFTER_LOOP_MARKER);
            return count;
        }
    }

    public class FullUnrollUntilReturnNoLoop extends AbstractTestNode {

        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
        @Override
        public int execute(VirtualFrame frame) {
            return count;
        }
    }

    public class FullUnrollUntilReturnConsecutiveLoops extends AbstractTestNode {

        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
        @Override
        public int execute(VirtualFrame frame) {
            for (int i = 0; i < count; i++) {
                GraalDirectives.blackhole(INSIDE_LOOP_MARKER);
                SideEffect = i;
                if (i == SideEffect2) {
                    // exit the loop
                    GraalDirectives.blackhole(OUTSIDE_LOOP_MARKER);
                    GraalDirectives.blackhole(i);
                    CompilerAsserts.partialEvaluationConstant(i);
                    return i;
                }
                // random code
                // loop end -> unroll
            }
            for (int i = 0; i < count; i++) {
                GraalDirectives.blackhole(INSIDE_LOOP_MARKER);
                SideEffect = i;
                if (i == SideEffect2) {
                    // exit the loop
                    GraalDirectives.blackhole(OUTSIDE_LOOP_MARKER);
                    GraalDirectives.blackhole(i);
                    CompilerAsserts.partialEvaluationConstant(i);
                    return i;
                }
                // random code
                // loop end -> unroll
            }
            GraalDirectives.blackhole(AFTER_LOOP_MARKER);
            return count;
        }
    }

    public class FullUnrollUntilReturnNestedLoops extends AbstractTestNode {

        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
        @Override
        public int execute(VirtualFrame frame) {
            for (int i = 0; i < count; i++) {
                SideEffect = i;
                for (int j = 0; j < count; j++) {
                    GraalDirectives.blackhole(INSIDE_LOOP_MARKER);
                    SideEffect = j;
                    if (i == SideEffect2) {
                        // exit the loop
                        GraalDirectives.blackhole(OUTSIDE_LOOP_MARKER);
                        GraalDirectives.blackhole(i);
                        CompilerAsserts.partialEvaluationConstant(i);
                        return j;
                    }
                    // random code
                    // loop end -> unroll
                }
            }
            GraalDirectives.blackhole(AFTER_LOOP_MARKER);
            return count;
        }
    }

    public class FullUnrollUntilReturnNestedLoopsBreakInner extends AbstractTestNode {

        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
        @Override
        public int execute(VirtualFrame frame) {
            for (int i = 0; i < count; i++) {
                SideEffect = i;
                for (int j = 0; j < count; j++) {
                    GraalDirectives.blackhole(INSIDE_LOOP_MARKER);
                    SideEffect = j;
                    if (i == SideEffect2) {
                        // exit the loop
                        GraalDirectives.blackhole(OUTSIDE_LOOP_MARKER);
                        GraalDirectives.blackhole(i);
                        CompilerAsserts.partialEvaluationConstant(i);
                        break;
                    }
                    // random code
                    // loop end -> unroll
                }
            }
            GraalDirectives.blackhole(AFTER_LOOP_MARKER);
            return count;
        }
    }

    public class FullUnrollUntilReturnNestedLoopsContinueOuter01 extends AbstractTestNode {

        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
        @Override
        public int execute(VirtualFrame frame) {
            /*
             * Special case full unroll until return with a continue statement to the outer loop.
             * UNTIL_RETURN implies the duplication of code paths along loop exits. Continue
             * statements to outer loops create loop exits of the inner loop and continues of the
             * outer loop.
             */
            int i = 0;
            while (true) {
                if (i >= count) {
                    break;
                }
                CompilerAsserts.partialEvaluationConstant(i);
                GraalDirectives.blackhole(OUTER_LOOP_INSIDE_LOOP_MARKER);
                int j = 0;
                inner: while (true) {
                    if (j >= count) {
                        break;
                    }
                    CompilerAsserts.partialEvaluationConstant(j);
                    GraalDirectives.blackhole(INSIDE_LOOP_MARKER);
                    if (SideEffect3 == j) {
                        CompilerAsserts.partialEvaluationConstant(j);
                        /* continue to outer loop */
                        break inner;
                    }
                    GraalDirectives.blackhole(INSIDE_LOOP_MARKER_2);
                    j++;
                }
                i++;
            }
            GraalDirectives.blackhole(AFTER_LOOP_MARKER);
            return count;
        }
    }

    public class FullUnrollUntilReturnNestedLoopsContinueOuter02 extends AbstractTestNode {

        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
        @Override
        public int execute(VirtualFrame frame) {
            int i = 0;
            outer: while (true) {
                if (i >= count) {
                    break;
                }
                CompilerAsserts.partialEvaluationConstant(i);
                GraalDirectives.blackhole(OUTER_LOOP_INSIDE_LOOP_MARKER);
                int j = 0;
                while (true) {
                    if (j >= count) {
                        break;
                    }
                    CompilerAsserts.partialEvaluationConstant(j);
                    GraalDirectives.blackhole(INSIDE_LOOP_MARKER);
                    if (SideEffect3 == j) {
                        i++;
                        GraalDirectives.blackhole(INSIDE_LOOP_MARKER);
                        SideEffect = i;
                        continue outer;
                    } else {
                        GraalDirectives.blackhole(INSIDE_LOOP_MARKER_2);
                    }
                    GraalDirectives.blackhole(INSIDE_LOOP_MARKER_2);
                    j++;
                }
                i++;
            }
            GraalDirectives.blackhole(AFTER_LOOP_MARKER);
            return count;
        }
    }

    public class FullUnrollUntilReturnNestedLoopsContinueOuter03 extends AbstractTestNode {

        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
        @Override
        public int execute(VirtualFrame frame) {
            outer: for (int i = 0; i < count; i++) {
                GraalDirectives.blackhole(OUTER_LOOP_INSIDE_LOOP_MARKER);
                CompilerAsserts.partialEvaluationConstant(i);
                for (int j = 0; j < count; j++) {
                    GraalDirectives.blackhole(INSIDE_LOOP_MARKER);
                    if (j == SideEffect3) {
                        GraalDirectives.blackhole(CONTINUE_LOOP_MARKER);
                        CompilerAsserts.partialEvaluationConstant(j);
                        continue outer;
                    }
                }
            }
            GraalDirectives.blackhole(AFTER_LOOP_MARKER);
            return count;
        }
    }

    public class FullUnrollUntilReturnNestedLoopsContinueOuter04 extends AbstractTestNode {

        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
        @Override
        public int execute(VirtualFrame frame) {
            outer: for (int i = 0; i < count; i++) {
                GraalDirectives.blackhole(OUTER_LOOP_INSIDE_LOOP_MARKER);
                CompilerAsserts.partialEvaluationConstant(i);
                for (int j = 0; j < count; j++) {
                    GraalDirectives.blackhole(INSIDE_LOOP_MARKER);
                    if (j == SideEffect3) {
                        GraalDirectives.blackhole(CONTINUE_LOOP_MARKER);
                        CompilerAsserts.partialEvaluationConstant(j);
                        int x = i * j + 2 * i;
                        CompilerAsserts.partialEvaluationConstant(x);
                        SideEffect = x;
                        SideEffect1 = x;
                        continue outer;
                    }
                }
            }
            GraalDirectives.blackhole(AFTER_LOOP_MARKER);
            return count;
        }
    }

    public class FullUnrollUntilReturnNestedLoopsContinueOuter05 extends AbstractTestNode {

        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
        @Override
        public int execute(VirtualFrame frame) {
            for (int i = 0; i < count; i++) {
                GraalDirectives.blackhole(INSIDE_LOOP_MARKER);
                GraalDirectives.blackhole(OUTSIDE_LOOP_MARKER);
                GraalDirectives.blackhole(i);
                CompilerAsserts.partialEvaluationConstant(i);
            }
            outer: for (int i = 0; i < count; i++) {
                GraalDirectives.blackhole(OUTER_LOOP_INSIDE_LOOP_MARKER);
                CompilerAsserts.partialEvaluationConstant(i);
                for (int j = 0; j < count; j++) {
                    GraalDirectives.blackhole(INSIDE_LOOP_MARKER);
                    if (j == SideEffect3) {
                        GraalDirectives.blackhole(CONTINUE_LOOP_MARKER);
                        CompilerAsserts.partialEvaluationConstant(j);
                        int x = i * j + 2 * i;
                        CompilerAsserts.partialEvaluationConstant(x);
                        SideEffect = x;
                        SideEffect1 = x;
                        continue outer;
                    }
                    if (j == SideEffect2) {
                        GraalDirectives.blackhole(CONTINUE_LOOP_MARKER);
                        CompilerAsserts.partialEvaluationConstant(j);
                        int x = i * j + 2 * i;
                        CompilerAsserts.partialEvaluationConstant(x);
                        SideEffect = x;
                        SideEffect1 = x;
                        break;
                    }
                    if (j == SideEffect1) {
                        GraalDirectives.blackhole(CONTINUE_LOOP_MARKER);
                        CompilerAsserts.partialEvaluationConstant(j);
                        int x = i * j + 2 * i;
                        CompilerAsserts.partialEvaluationConstant(x);
                        SideEffect = x;
                        SideEffect1 = x;
                        continue outer;
                    }
                }
            }
            GraalDirectives.blackhole(AFTER_LOOP_MARKER);
            return count;
        }
    }

    public static class ExecutingUnrollUntilReturnTest {
        public static volatile int ExecutedSideEffect1;
        public static volatile int ExecutedSideEffect2;
        public static volatile int ExecutedSideEffect3;
        public static volatile int ExecutedSideEffect4;
        public static volatile int ExecutedSideEffect5;
        public static volatile int ExecutedSideEffect6;

        public static volatile int SpecialSideEffect1;
        public static volatile int SpecialSideEffect2;
        public static volatile int SpecialSideEffect3;
        public static volatile int SpecialSideEffect4;
        public static volatile int SpecialSideEffect5;
        public static volatile int SpecialSideEffect6;
        public static volatile int SpecialSideEffect7;
        public static volatile int SpecialSideEffect8;
        public static volatile int SpecialSideEffect9;
        public static volatile int SpecialSideEffect10;
        public static volatile int SpecialSideEffect11;
        public static volatile int SpecialSideEffect12;

        public static int specialIterationNumber = 6;

        @TruffleBoundary
        public static int result() {
            return ExecutedSideEffect1 + ExecutedSideEffect2 + ExecutedSideEffect3 + ExecutedSideEffect4 + ExecutedSideEffect5 + ExecutedSideEffect6 + SpecialSideEffect1 + SpecialSideEffect2 +
                            SpecialSideEffect3 + SpecialSideEffect4 + SpecialSideEffect5 + SpecialSideEffect6 + SpecialSideEffect7 + SpecialSideEffect8 + SpecialSideEffect9 + SpecialSideEffect10 +
                            SpecialSideEffect11 + SpecialSideEffect12;
        }

        public static void clearSpecialEffect() {

            /*
             * Special Unit test:
             *
             * In order to not only track how many times a certain loop exit marker is duplicated we
             * also test the exact times a branch is executed at runtime. Therefore, we compare the
             * number of times a field has been written in an interpreted execution of a method and
             * a partial evaluated compiled execution of the method.
             *
             * We reset the test fields in between.
             */

            ExecutedSideEffect1 = specialIterationNumber - 3;
            ExecutedSideEffect2 = specialIterationNumber - 2;
            ExecutedSideEffect3 = specialIterationNumber - 1;
            ExecutedSideEffect4 = specialIterationNumber - 1;
            ExecutedSideEffect5 = specialIterationNumber - 2;
            ExecutedSideEffect6 = specialIterationNumber - 3;

            SpecialSideEffect1 = 0;
            SpecialSideEffect2 = 0;
            SpecialSideEffect3 = 0;
            SpecialSideEffect4 = 0;
            SpecialSideEffect5 = 0;
            SpecialSideEffect6 = 0;
            SpecialSideEffect7 = 0;
            SpecialSideEffect8 = 0;
            SpecialSideEffect9 = 0;
            SpecialSideEffect10 = 0;
            SpecialSideEffect11 = 0;
            SpecialSideEffect12 = 0;
        }

        public static int compiledInvocationCounts;
        public static int interpretedInvocationCounts;

    }

    public class FullUnrollUntilReturnNestedLoopsContinueOuter06 extends AbstractTestNode {

        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
        @Override
        public int execute(VirtualFrame frame) {
            l1: for (int i1 = 0; i1 < count; i1++) {
                GraalDirectives.blackhole(OUTER_LOOP_INSIDE_LOOP_MARKER);
                CompilerAsserts.partialEvaluationConstant(i1);
                l2: for (int i2 = 0; i2 < count; i2++) {
                    GraalDirectives.blackhole(OUTER_LOOP_INSIDE_LOOP_MARKER);
                    CompilerAsserts.partialEvaluationConstant(2);
                    l3: for (int i3 = 0; i3 < count; i3++) {
                        GraalDirectives.blackhole(OUTER_LOOP_INSIDE_LOOP_MARKER);
                        CompilerAsserts.partialEvaluationConstant(3);
                        for (int i4 = 0; i4 < count; i4++) {
                            GraalDirectives.blackhole(OUTER_LOOP_INSIDE_LOOP_MARKER);
                            CompilerAsserts.partialEvaluationConstant(4);
                            if (i4 == ExecutingUnrollUntilReturnTest.ExecutedSideEffect1) {
                                GraalDirectives.blackhole(CONTINUE_LOOP_MARKER);
                                CompilerAsserts.partialEvaluationConstant(i4);
                                int x = i3 + i1 + 2 + i2 + i4;
                                CompilerAsserts.partialEvaluationConstant(x);
                                ExecutingUnrollUntilReturnTest.SpecialSideEffect1 += x;
                                ExecutingUnrollUntilReturnTest.SpecialSideEffect2 += x;
                                ExecutingUnrollUntilReturnTest.ExecutedSideEffect1 = 1024;
                                continue l1;
                            }
                            if (i4 == ExecutingUnrollUntilReturnTest.ExecutedSideEffect2) {
                                GraalDirectives.blackhole(CONTINUE_LOOP_MARKER);
                                CompilerAsserts.partialEvaluationConstant(i4);
                                int x = i3 + i1 + 2 + i2 + i4;
                                CompilerAsserts.partialEvaluationConstant(x);
                                ExecutingUnrollUntilReturnTest.SpecialSideEffect3 += x;
                                ExecutingUnrollUntilReturnTest.SpecialSideEffect4 += x;
                                ExecutingUnrollUntilReturnTest.ExecutedSideEffect2 = 1024;
                                continue l2;
                            }
                            if (i4 == ExecutingUnrollUntilReturnTest.ExecutedSideEffect3) {
                                GraalDirectives.blackhole(CONTINUE_LOOP_MARKER);
                                CompilerAsserts.partialEvaluationConstant(i4);
                                int x = i3 + i1 + 2 + i2 + i4;
                                CompilerAsserts.partialEvaluationConstant(x);
                                ExecutingUnrollUntilReturnTest.SpecialSideEffect5 += x;
                                ExecutingUnrollUntilReturnTest.SpecialSideEffect6 += x;
                                ExecutingUnrollUntilReturnTest.ExecutedSideEffect3 = 1024;
                                continue l3;
                            }
                        }
                        if (i3 == ExecutingUnrollUntilReturnTest.ExecutedSideEffect4) {
                            GraalDirectives.blackhole(CONTINUE_LOOP_MARKER);
                            CompilerAsserts.partialEvaluationConstant(i3);
                            int x = i3 + i1 + 2 + i2;
                            CompilerAsserts.partialEvaluationConstant(x);
                            ExecutingUnrollUntilReturnTest.SpecialSideEffect7 += x;
                            ExecutingUnrollUntilReturnTest.SpecialSideEffect8 += x;
                            ExecutingUnrollUntilReturnTest.ExecutedSideEffect4 = 1024;
                            continue l1;
                        }
                        if (i3 == ExecutingUnrollUntilReturnTest.ExecutedSideEffect5) {
                            GraalDirectives.blackhole(CONTINUE_LOOP_MARKER);
                            CompilerAsserts.partialEvaluationConstant(i3);
                            int x = i3 + i1 + 2 + i2;
                            CompilerAsserts.partialEvaluationConstant(x);
                            ExecutingUnrollUntilReturnTest.SpecialSideEffect9 += x;
                            ExecutingUnrollUntilReturnTest.SpecialSideEffect10 += x;
                            ExecutingUnrollUntilReturnTest.ExecutedSideEffect5 = 1024;
                            continue l2;
                        }
                    }
                    if (i2 == ExecutingUnrollUntilReturnTest.ExecutedSideEffect6) {
                        GraalDirectives.blackhole(CONTINUE_LOOP_MARKER);
                        CompilerAsserts.partialEvaluationConstant(i2);
                        int x = i2 + i1 + 2 + i2;
                        CompilerAsserts.partialEvaluationConstant(x);
                        ExecutingUnrollUntilReturnTest.SpecialSideEffect11 += x;
                        ExecutingUnrollUntilReturnTest.SpecialSideEffect12 += x;
                        ExecutingUnrollUntilReturnTest.ExecutedSideEffect6 = 1024;
                        continue l1;
                    }
                }
            }
            GraalDirectives.blackhole(AFTER_LOOP_MARKER);
            if (!CompilerDirectives.inCompiledCode()) {
                ExecutingUnrollUntilReturnTest.interpretedInvocationCounts++;
            } else {
                ExecutingUnrollUntilReturnTest.compiledInvocationCounts++;
            }
            return ExecutingUnrollUntilReturnTest.result();
        }
    }

    public class Unroll0 extends AbstractTestNode {

        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL)
        @Override
        public int execute(VirtualFrame frame) {
            int i = 0;
            while (true) {
                if (i >= count) {
                    // LEX 1 -> constant number of loop iterations
                    break;
                }
                GraalDirectives.blackhole(INSIDE_LOOP_MARKER);
                SideEffect = i;
                if (i == SideEffect2) {
                    // LEX 2 (1) -> continue OUTSIDE_LOOP_MARKER
                    /*
                     * Outside of the loop, duplicate for each unrolling until the return
                     */
                    GraalDirectives.blackhole(OUTSIDE_LOOP_MARKER);
                    GraalDirectives.blackhole(i);
                    CompilerAsserts.partialEvaluationConstant(i);
                    return i;
                }
                if (i == SideEffect3) {
                    // LEN 1 -> continue at header
                    // that is the difference of unroll vs explode:unrolling will merge the ends,
                    // explode will duplicate the next loop iterations per loop end
                    i++;
                    continue;
                }
                if (i == SideEffect1) {
                    // LEX 3 (2) -> continue at AFTER_LOOP_MARKER
                    break;
                }
                i++;
                // LEN 2 -> continue at header
            }
            GraalDirectives.blackhole(AFTER_LOOP_MARKER);
            return count;
        }
    }

    public class Unroll01 extends AbstractTestNode {

        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
        @Override
        public int execute(VirtualFrame frame) {
            int i = 0;
            while (true) {
                if (i >= count) {
                    // LEX 1 -> constant number of loop iterations
                    break;
                }
                GraalDirectives.blackhole(INSIDE_LOOP_MARKER);
                SideEffect = i;
                if (i == SideEffect2) {
                    // LEX 2 (1) -> continue OUTSIDE_LOOP_MARKER
                    /*
                     * Outside of the loop, duplicate for each unrolling until the return
                     */
                    GraalDirectives.blackhole(OUTSIDE_LOOP_MARKER);
                    GraalDirectives.blackhole(i);
                    CompilerAsserts.partialEvaluationConstant(i);
                    return i;
                }
                if (i == SideEffect3) {
                    // LEN 1 -> continue at header
                    // that is the difference of unroll vs explode:unrolling will merge the ends,
                    // explode will duplicate the next loop iterations per loop end
                    i++;
                    continue;
                }
                if (i == SideEffect1) {
                    // LEX 3 (2) -> continue at AFTER_LOOP_MARKER
                    break;
                }
                i++;
                // LEN 2 -> continue at header
            }
            GraalDirectives.blackhole(AFTER_LOOP_MARKER);
            return count;
        }
    }

    public class Unroll02 extends AbstractTestNode {

        @ExplodeLoop(kind = LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
        @Override
        public int execute(VirtualFrame frame) {
            int i = 0;
            while (true) {
                if (i >= count) {
                    // LEX 1 -> constant number of loop iterations
                    break;
                }
                GraalDirectives.blackhole(INSIDE_LOOP_MARKER);
                SideEffect = i;
                if (i == SideEffect2) {
                    // LEX 2 -> continue OUTSIDE_LOOP_MARKER
                    /*
                     * Outside of the loop, duplicate for each unrolling until the return
                     */
                    GraalDirectives.blackhole(OUTSIDE_LOOP_MARKER);
                    GraalDirectives.blackhole(i);
                    CompilerAsserts.partialEvaluationConstant(i);
                    return i;
                }
                if (i == SideEffect3) {
                    // LEN 1 -> continue at header
                    // that is the difference of unroll vs explode:unrolling will merge the ends,
                    // explode will duplicate the next loop iterations per loop end
                    i++;
                    continue;
                }
                if (i == SideEffect1) {
                    // LEX 3 -> continue at AFTER_LOOP_MARKER
                    break;
                }
                i++;
                // LEN 2 -> continue at header
            }
            GraalDirectives.blackhole(AFTER_LOOP_MARKER);
            return count;
        }
    }

}
