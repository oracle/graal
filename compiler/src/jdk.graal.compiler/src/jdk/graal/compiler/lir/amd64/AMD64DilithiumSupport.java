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
package jdk.graal.compiler.lir.amd64;

import java.util.Arrays;

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMIOp.EVPSHUFD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.EVMOVDQU32;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPADDD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPERMT2D;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMULDQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMULLD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPSUBD;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.ZMM;
import static jdk.graal.compiler.lir.amd64.AMD64AESEncryptOp.asXMMRegister;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.pointerConstant;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;
import static jdk.vm.ci.amd64.AMD64.xmm28;
import static jdk.vm.ci.amd64.AMD64.xmm29;
import static jdk.vm.ci.amd64.AMD64.xmm30;
import static jdk.vm.ci.amd64.AMD64.xmm31;

import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;

final class AMD64DilithiumSupport {

    static final int XMM_BYTES = 64;

    static final int[] DILITHIUM_AVX512_CONSTS_DATA = new int[]{
                    58728449,
                    8380417,
                    2365951,
                    5373807
    };

    static final ArrayDataPointerConstant DILITHIUM_AVX512_CONSTS = pointerConstant(16, DILITHIUM_AVX512_CONSTS_DATA);

    private static final int MONT_Q_INV_MOD_R_IDX = 0;
    private static final int DILITHIUM_Q_IDX = 4;
    private static final int MONT_R_SQUARE_MOD_Q_IDX = 8;
    private static final int BARRETT_ADDEND_IDX = 12;

    static final int[] DILITHIUM_AVX512_PERMS_DATA = new int[]{
                    17, 1, 19, 3, 21, 5, 23, 7, 25, 9, 27, 11, 29, 13, 31, 15,
                    0, 1, 2, 3, 4, 5, 6, 7, 16, 17, 18, 19, 20, 21, 22, 23,
                    8, 9, 10, 11, 12, 13, 14, 15, 24, 25, 26, 27, 28, 29, 30, 31,
                    0, 1, 2, 3, 16, 17, 18, 19, 8, 9, 10, 11, 24, 25, 26, 27,
                    4, 5, 6, 7, 20, 21, 22, 23, 12, 13, 14, 15, 28, 29, 30, 31,
                    0, 1, 16, 17, 4, 5, 20, 21, 8, 9, 24, 25, 12, 13, 28, 29,
                    2, 3, 18, 19, 6, 7, 22, 23, 10, 11, 26, 27, 14, 15, 30, 31,
                    0, 16, 2, 18, 4, 20, 6, 22, 8, 24, 10, 26, 12, 28, 14, 30,
                    1, 17, 3, 19, 5, 21, 7, 23, 9, 25, 11, 27, 13, 29, 15, 31,
                    0, 16, 1, 17, 2, 18, 3, 19, 4, 20, 5, 21, 6, 22, 7, 23,
                    8, 24, 9, 25, 10, 26, 11, 27, 12, 28, 13, 29, 14, 30, 15, 31,
                    0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30,
                    1, 3, 5, 7, 9, 11, 13, 15, 17, 19, 21, 23, 25, 27, 29, 31,
                    0, 16, 2, 18, 4, 20, 6, 22, 8, 24, 10, 26, 12, 28, 14, 30,
                    1, 17, 3, 19, 5, 21, 7, 23, 9, 25, 11, 27, 13, 29, 15, 31,
                    0, 1, 16, 17, 4, 5, 20, 21, 8, 9, 24, 25, 12, 13, 28, 29,
                    2, 3, 18, 19, 6, 7, 22, 23, 10, 11, 26, 27, 14, 15, 30, 31,
                    0, 1, 2, 3, 16, 17, 18, 19, 8, 9, 10, 11, 24, 25, 26, 27,
                    4, 5, 6, 7, 20, 21, 22, 23, 12, 13, 14, 15, 28, 29, 30, 31,
                    0, 1, 2, 3, 4, 5, 6, 7, 16, 17, 18, 19, 20, 21, 22, 23,
                    8, 9, 10, 11, 12, 13, 14, 15, 24, 25, 26, 27, 28, 29, 30, 31
    };

    static final ArrayDataPointerConstant DILITHIUM_AVX512_PERMS = pointerConstant(16, DILITHIUM_AVX512_PERMS_DATA);

    private static final int MONT_MUL_PERMS_IDX = 0;
    private static final int NTT_L4_PERMS_IDX = 64;
    private static final int NTT_L5_PERMS_IDX = 192;
    private static final int NTT_L6_PERMS_IDX = 320;
    private static final int NTT_L7_PERMS_IDX = 448;
    private static final int NTT_INV_L0_PERMS_IDX = 704;
    private static final int NTT_INV_L1_PERMS_IDX = 832;
    private static final int NTT_INV_L2_PERMS_IDX = 960;
    private static final int NTT_INV_L3_PERMS_IDX = 1088;
    private static final int NTT_INV_L4_PERMS_IDX = 1216;

    static final ArrayDataPointerConstant MONT_Q_INV_MOD_R = intSliceConstant(DILITHIUM_AVX512_CONSTS_DATA, MONT_Q_INV_MOD_R_IDX, Integer.BYTES);
    static final ArrayDataPointerConstant DILITHIUM_Q = intSliceConstant(DILITHIUM_AVX512_CONSTS_DATA, DILITHIUM_Q_IDX, Integer.BYTES);
    static final ArrayDataPointerConstant MONT_R_SQUARE_MOD_Q = intSliceConstant(DILITHIUM_AVX512_CONSTS_DATA, MONT_R_SQUARE_MOD_Q_IDX, Integer.BYTES);
    static final ArrayDataPointerConstant BARRETT_ADDEND = intSliceConstant(DILITHIUM_AVX512_CONSTS_DATA, BARRETT_ADDEND_IDX, Integer.BYTES);

    static final ArrayDataPointerConstant MONT_MUL_PERMS = intSliceConstant(DILITHIUM_AVX512_PERMS_DATA, MONT_MUL_PERMS_IDX, XMM_BYTES);
    static final ArrayDataPointerConstant NTT_L4_PERMS_0 = intSliceConstant(DILITHIUM_AVX512_PERMS_DATA, NTT_L4_PERMS_IDX, XMM_BYTES);
    static final ArrayDataPointerConstant NTT_L4_PERMS_1 = intSliceConstant(DILITHIUM_AVX512_PERMS_DATA, NTT_L4_PERMS_IDX + XMM_BYTES, XMM_BYTES);
    static final ArrayDataPointerConstant NTT_L5_PERMS_0 = intSliceConstant(DILITHIUM_AVX512_PERMS_DATA, NTT_L5_PERMS_IDX, XMM_BYTES);
    static final ArrayDataPointerConstant NTT_L5_PERMS_1 = intSliceConstant(DILITHIUM_AVX512_PERMS_DATA, NTT_L5_PERMS_IDX + XMM_BYTES, XMM_BYTES);
    static final ArrayDataPointerConstant NTT_L6_PERMS_0 = intSliceConstant(DILITHIUM_AVX512_PERMS_DATA, NTT_L6_PERMS_IDX, XMM_BYTES);
    static final ArrayDataPointerConstant NTT_L6_PERMS_1 = intSliceConstant(DILITHIUM_AVX512_PERMS_DATA, NTT_L6_PERMS_IDX + XMM_BYTES, XMM_BYTES);
    static final ArrayDataPointerConstant NTT_L7_PERMS_0 = intSliceConstant(DILITHIUM_AVX512_PERMS_DATA, NTT_L7_PERMS_IDX, XMM_BYTES);
    static final ArrayDataPointerConstant NTT_L7_PERMS_1 = intSliceConstant(DILITHIUM_AVX512_PERMS_DATA, NTT_L7_PERMS_IDX + XMM_BYTES, XMM_BYTES);
    static final ArrayDataPointerConstant NTT_L7_PERMS_2 = intSliceConstant(DILITHIUM_AVX512_PERMS_DATA, NTT_L7_PERMS_IDX + 2 * XMM_BYTES, XMM_BYTES);
    static final ArrayDataPointerConstant NTT_L7_PERMS_3 = intSliceConstant(DILITHIUM_AVX512_PERMS_DATA, NTT_L7_PERMS_IDX + 3 * XMM_BYTES, XMM_BYTES);
    static final ArrayDataPointerConstant NTT_INV_L0_PERMS_0 = intSliceConstant(DILITHIUM_AVX512_PERMS_DATA, NTT_INV_L0_PERMS_IDX, XMM_BYTES);
    static final ArrayDataPointerConstant NTT_INV_L0_PERMS_1 = intSliceConstant(DILITHIUM_AVX512_PERMS_DATA, NTT_INV_L0_PERMS_IDX + XMM_BYTES, XMM_BYTES);
    static final ArrayDataPointerConstant NTT_INV_L1_PERMS_0 = intSliceConstant(DILITHIUM_AVX512_PERMS_DATA, NTT_INV_L1_PERMS_IDX, XMM_BYTES);
    static final ArrayDataPointerConstant NTT_INV_L1_PERMS_1 = intSliceConstant(DILITHIUM_AVX512_PERMS_DATA, NTT_INV_L1_PERMS_IDX + XMM_BYTES, XMM_BYTES);
    static final ArrayDataPointerConstant NTT_INV_L2_PERMS_0 = intSliceConstant(DILITHIUM_AVX512_PERMS_DATA, NTT_INV_L2_PERMS_IDX, XMM_BYTES);
    static final ArrayDataPointerConstant NTT_INV_L2_PERMS_1 = intSliceConstant(DILITHIUM_AVX512_PERMS_DATA, NTT_INV_L2_PERMS_IDX + XMM_BYTES, XMM_BYTES);
    static final ArrayDataPointerConstant NTT_INV_L3_PERMS_0 = intSliceConstant(DILITHIUM_AVX512_PERMS_DATA, NTT_INV_L3_PERMS_IDX, XMM_BYTES);
    static final ArrayDataPointerConstant NTT_INV_L3_PERMS_1 = intSliceConstant(DILITHIUM_AVX512_PERMS_DATA, NTT_INV_L3_PERMS_IDX + XMM_BYTES, XMM_BYTES);
    static final ArrayDataPointerConstant NTT_INV_L4_PERMS_0 = intSliceConstant(DILITHIUM_AVX512_PERMS_DATA, NTT_INV_L4_PERMS_IDX, XMM_BYTES);
    static final ArrayDataPointerConstant NTT_INV_L4_PERMS_1 = intSliceConstant(DILITHIUM_AVX512_PERMS_DATA, NTT_INV_L4_PERMS_IDX + XMM_BYTES, XMM_BYTES);

    static final int[] XMM_0_3 = {0, 1, 2, 3};
    static final int[] XMM_0145 = {0, 1, 4, 5};
    static final int[] XMM_0246 = {0, 2, 4, 6};
    static final int[] XMM_0426 = {0, 4, 2, 6};
    static final int[] XMM_1357 = {1, 3, 5, 7};
    static final int[] XMM_1537 = {1, 5, 3, 7};
    static final int[] XMM_2367 = {2, 3, 6, 7};
    static final int[] XMM_4_7 = {4, 5, 6, 7};
    static final int[] XMM_8_11 = {8, 9, 10, 11};
    static final int[] XMM_12_15 = {12, 13, 14, 15};
    static final int[] XMM_16_19 = {16, 17, 18, 19};
    static final int[] XMM_20_23 = {20, 21, 22, 23};
    static final int[] XMM_20222426 = {20, 22, 24, 26};
    static final int[] XMM_21232527 = {21, 23, 25, 27};
    static final int[] XMM_24_27 = {24, 25, 26, 27};
    static final int[] XMM_4_20_24 = {4, 5, 6, 7, 20, 21, 22, 23, 24, 25, 26, 27};
    static final int[] XMM_16_27 = {16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27};
    static final int[] XMM_29_29 = {29, 29, 29, 29};

    private AMD64DilithiumSupport() {
    }

    static void loadPerm(CompilationResultBuilder crb, AMD64MacroAssembler masm, ArrayDataPointerConstant perm, int[] destinationRegs) {
        EVMOVDQU32.emit(masm, ZMM, asXMMRegister(destinationRegs[0]), recordExternalAddress(crb, perm));
        for (int i = 1; i < 4; i++) {
            EVMOVDQU32.emit(masm, ZMM, asXMMRegister(destinationRegs[i]), asXMMRegister(destinationRegs[0]));
        }
    }

    static void load4Xmms(AMD64MacroAssembler masm, int[] destinationRegs, Register source, int offset) {
        for (int i = 0; i < 4; i++) {
            EVMOVDQU32.emit(masm, ZMM, asXMMRegister(destinationRegs[i]), new AMD64Address(source, offset + i * XMM_BYTES));
        }
    }

    static void loadXmm29(AMD64MacroAssembler masm, Register source, int offset) {
        EVMOVDQU32.emit(masm, ZMM, xmm29, new AMD64Address(source, offset));
    }

    static void store4Xmms(AMD64MacroAssembler masm, Register destination, int offset, int[] xmmRegs) {
        for (int i = 0; i < 4; i++) {
            EVMOVDQU32.emit(masm, ZMM, new AMD64Address(destination, offset + i * XMM_BYTES), asXMMRegister(xmmRegs[i]));
        }
    }

    static void subAdd(AMD64MacroAssembler masm, int[] subResult, int[] addResult, int[] input1, int[] input2) {
        for (int i = 0; i < 4; i++) {
            masm.emit(EVPSUBD, asXMMRegister(subResult[i]), asXMMRegister(input1[i]), asXMMRegister(input2[i]), ZMM);
        }
        for (int i = 0; i < 4; i++) {
            masm.emit(EVPADDD, asXMMRegister(addResult[i]), asXMMRegister(input1[i]), asXMMRegister(input2[i]), ZMM);
        }
    }

    static void montMul64(AMD64MacroAssembler masm, int[] outputRegs, int[] inputRegs1, int[] inputRegs2, int[] scratchRegs) {
        montMul64(masm, outputRegs, inputRegs1, inputRegs2, scratchRegs, false);
    }

    static void montMul64(AMD64MacroAssembler masm, int[] outputRegs, int[] inputRegs1, int[] inputRegs2, int[] scratchRegs, boolean input2NeedsShuffle) {
        for (int i = 0; i < 4; i++) {
            masm.emit(EVPMULDQ, asXMMRegister(scratchRegs[i]), asXMMRegister(inputRegs1[i]), asXMMRegister(inputRegs2[i]), ZMM);
        }
        for (int i = 0; i < 4; i++) {
            masm.emit(EVPMULLD, asXMMRegister(scratchRegs[i + 4]), asXMMRegister(scratchRegs[i]), xmm30, ZMM);
        }
        for (int i = 0; i < 4; i++) {
            masm.emit(EVPMULDQ, asXMMRegister(scratchRegs[i + 4]), asXMMRegister(scratchRegs[i + 4]), xmm31, ZMM);
        }
        for (int i = 0; i < 4; i++) {
            masm.emit(EVPSUBD, asXMMRegister(scratchRegs[i + 4]), asXMMRegister(scratchRegs[i]), asXMMRegister(scratchRegs[i + 4]), ZMM);
        }

        for (int i = 0; i < 4; i++) {
            EVPSHUFD.emit(masm, ZMM, asXMMRegister(inputRegs1[i]), asXMMRegister(inputRegs1[i]), 0xB1);
            if (input2NeedsShuffle) {
                EVPSHUFD.emit(masm, ZMM, asXMMRegister(inputRegs2[i]), asXMMRegister(inputRegs2[i]), 0xB1);
            }
        }

        for (int i = 0; i < 4; i++) {
            masm.emit(EVPMULDQ, asXMMRegister(scratchRegs[i]), asXMMRegister(inputRegs1[i]), asXMMRegister(inputRegs2[i]), ZMM);
        }
        for (int i = 0; i < 4; i++) {
            masm.emit(EVPMULLD, asXMMRegister(scratchRegs[i + 8]), asXMMRegister(scratchRegs[i]), xmm30, ZMM);
        }
        for (int i = 0; i < 4; i++) {
            masm.emit(EVPMULDQ, asXMMRegister(scratchRegs[i + 8]), asXMMRegister(scratchRegs[i + 8]), xmm31, ZMM);
        }
        for (int i = 0; i < 4; i++) {
            masm.emit(EVPSUBD, asXMMRegister(outputRegs[i]), asXMMRegister(scratchRegs[i]), asXMMRegister(scratchRegs[i + 8]), ZMM);
        }

        for (int i = 0; i < 4; i++) {
            masm.emit(EVPERMT2D, asXMMRegister(outputRegs[i]), xmm28, asXMMRegister(scratchRegs[i + 4]), ZMM);
        }
    }

    private static ArrayDataPointerConstant intSliceConstant(int[] data, int byteOffset, int byteLength) {
        return new ArrayDataPointerConstant(Arrays.copyOfRange(data, byteOffset / Integer.BYTES, (byteOffset + byteLength) / Integer.BYTES), 16);
    }
}
