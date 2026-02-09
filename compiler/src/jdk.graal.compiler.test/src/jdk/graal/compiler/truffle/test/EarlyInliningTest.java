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

import org.junit.Test;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.EarlyEscapeAnalysis;
import com.oracle.truffle.api.CompilerDirectives.EarlyInline;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;

public class EarlyInliningTest extends PartialEvaluationTest {

    public static int expected(int arg) {
        int v = 0;
        while (v < arg) {
            v++;
        }
        return v;
    }

    @Test
    public void testExplodeLoopBasic() {
        assertPartialEvalEquals(EarlyInliningTest::expected, (a) -> explodeBasic(a), 42);
    }

    static final int SET = 1;
    static final int BRANCH_LT = 2;
    static final int BRANCH_BACKWARD = 3;
    static final int INCREMENT = 4;
    static final int RETURN = 5;

    @CompilationFinal(dimensions = 1) private final int[] bytecodes = new int[]{
                    // 0
                    SET, 0,
                    // 2
                    BRANCH_LT, 7,
                    // 4
                    INCREMENT,
                    // 5
                    BRANCH_BACKWARD,
                    2,
                    // 7
                    RETURN};

    static final class Stack {

        int accumulator;

    }

    @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
    private int explodeBasic(int arg) {
        int[] bc = this.bytecodes;
        int bci = 0;
        Frame frame = new Frame();
        while (true) {
            CompilerAsserts.partialEvaluationConstant(bci);
            switch (bc[bci]) {
                case SET:
                    frame.accumulator = bc[bci + 1];
                    bci += 2;
                    break;
                case RETURN:
                    return frame.accumulator;
                case BRANCH_LT:
                    bci = branchLT(bc, bci, frame, arg);
                    break;
                case INCREMENT:
                    frame.accumulator++;
                    bci = bci + 1;
                    break;
                case BRANCH_BACKWARD:
                    bci = branchBackward(bc, bci);
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }

    @EarlyInline
    private static int branchLT(int[] bc, int bci, Frame frame, int a) {
        if (frame.accumulator < a) {
            return bci + 2;
        } else {
            return bc[bci + 1];
        }
    }

    @EarlyInline
    private static int branchBackward(int[] bc, int bci) {
        return bc[bci + 1];
    }

    @ValueType
    static class BytecodeIndex {
        int bci;

        @EarlyInline
        BytecodeIndex() {
        }
    }

    static class Frame {
        int accumulator;
    }

    @Test
    public void testExplodeLoopEscapeAnalysis() {
        assertPartialEvalEquals(EarlyInliningTest::expected, (a) -> explodeEscapeAnalysis(a, new Frame()), 42);
    }

    @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
    @EarlyEscapeAnalysis
    private int explodeEscapeAnalysis(int arg, Frame frame) {
        int[] bc = this.bytecodes;
        BytecodeIndex index = new BytecodeIndex();
        while (true) {
            CompilerAsserts.partialEvaluationConstant(index.bci);
            switch (bc[index.bci]) {
                case SET:
                    frame.accumulator = bc[index.bci + 1];
                    index.bci = index.bci + 2;
                    break;
                case RETURN:
                    return frame.accumulator;
                case BRANCH_LT:
                    index.bci = branchLT2(bc, index, frame, arg);
                    break;
                case INCREMENT:
                    frame.accumulator++;
                    index.bci = index.bci + 1;
                    break;
                case BRANCH_BACKWARD:
                    index.bci = branchBackward2(bc, index);
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }

    @EarlyInline
    private static int branchLT2(int[] bc, BytecodeIndex index, Frame frame, int arg) {
        if (frame.accumulator < arg) {
            return index.bci + 2;
        } else {
            return bc[index.bci + 1];
        }
    }

    @EarlyInline
    private static int branchBackward2(int[] bc, BytecodeIndex index) {
        return bc[index.bci + 1];
    }

}
