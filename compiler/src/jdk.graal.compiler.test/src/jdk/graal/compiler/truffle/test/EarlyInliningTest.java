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
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;

public class EarlyInliningTest extends PartialEvaluationTest {

    @Test
    public void testExplodeLoopBasic() {
        assertPartialEvalEquals((v) -> v ? 42 : 41, (v) -> testExplodeLoopBasic(v), Boolean.TRUE);
    }

    // 0: nop;
    // 1: branchTrue 5
    // 3: return 41
    // 4: return 42
    @CompilationFinal(dimensions = 1) private final byte[] bytecodes = new byte[]{0, 1, 4, 2, 3};

    @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
    private int testExplodeLoopBasic(Boolean v) {
        byte[] bc = this.bytecodes;
        int bci = 0;
        while (true) {
            CompilerAsserts.partialEvaluationConstant(bci);
            switch (bc[bci]) {
                case 0:
                    bci++;
                    break;
                case 1:
                    bci = branchTrue1(bc, bci, v);
                    break;
                case 2:
                    return 41;
                case 3:
                    return 42;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }

    @EarlyInline
    private static int branchTrue1(byte[] bc, int bci, Boolean v) {
        if (v) {
            return bc[bci + 1];
        } else {
            return bci + 2;
        }
    }

    static class BytecodeIndex {
        int bci;
    }

    @Test
    public void testExplodeLoopEscapeAnalysis() {
        assertPartialEvalEquals((v) -> v ? 42 : 41, (v) -> testExplodeLoopEscapeAnalysis(v), Boolean.TRUE);
    }

    @SuppressWarnings("static-method")
    @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
    @EarlyEscapeAnalysis
    private int testExplodeLoopEscapeAnalysis(Boolean v) {
        byte[] bc = this.bytecodes;
        BytecodeIndex index = new BytecodeIndex();
        while (true) {
            CompilerAsserts.partialEvaluationConstant(index.bci);
            switch (bc[index.bci]) {
                case 0:
                    index.bci++;
                    break;
                case 1:
                    branchTrue2(bc, index, v);
                    break;
                case 2:
                    return 41;
                case 3:
                    return 42;
                case 4:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }

    @EarlyInline
    private static void branchTrue2(byte[] bc, BytecodeIndex index, Boolean v) {
        if (v) {
            index.bci = bc[index.bci + 1];
        } else {
            index.bci = index.bci + 2;
        }
    }

}
