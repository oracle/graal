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

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPADDW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMULHW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMULLW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPSUBW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp.EVPSRAW;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.pointerConstant;
import static jdk.vm.ci.amd64.AMD64.xmm0;
import static jdk.vm.ci.amd64.AMD64.xmm1;
import static jdk.vm.ci.amd64.AMD64.xmm10;
import static jdk.vm.ci.amd64.AMD64.xmm11;
import static jdk.vm.ci.amd64.AMD64.xmm12;
import static jdk.vm.ci.amd64.AMD64.xmm13;
import static jdk.vm.ci.amd64.AMD64.xmm14;
import static jdk.vm.ci.amd64.AMD64.xmm15;
import static jdk.vm.ci.amd64.AMD64.xmm16;
import static jdk.vm.ci.amd64.AMD64.xmm17;
import static jdk.vm.ci.amd64.AMD64.xmm18;
import static jdk.vm.ci.amd64.AMD64.xmm19;
import static jdk.vm.ci.amd64.AMD64.xmm2;
import static jdk.vm.ci.amd64.AMD64.xmm20;
import static jdk.vm.ci.amd64.AMD64.xmm21;
import static jdk.vm.ci.amd64.AMD64.xmm22;
import static jdk.vm.ci.amd64.AMD64.xmm23;
import static jdk.vm.ci.amd64.AMD64.xmm24;
import static jdk.vm.ci.amd64.AMD64.xmm25;
import static jdk.vm.ci.amd64.AMD64.xmm26;
import static jdk.vm.ci.amd64.AMD64.xmm27;
import static jdk.vm.ci.amd64.AMD64.xmm28;
import static jdk.vm.ci.amd64.AMD64.xmm29;
import static jdk.vm.ci.amd64.AMD64.xmm3;
import static jdk.vm.ci.amd64.AMD64.xmm30;
import static jdk.vm.ci.amd64.AMD64.xmm31;
import static jdk.vm.ci.amd64.AMD64.xmm4;
import static jdk.vm.ci.amd64.AMD64.xmm5;
import static jdk.vm.ci.amd64.AMD64.xmm6;
import static jdk.vm.ci.amd64.AMD64.xmm7;
import static jdk.vm.ci.amd64.AMD64.xmm8;
import static jdk.vm.ci.amd64.AMD64.xmm9;

import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.vm.ci.code.Register;

final class AMD64KyberSupport {
    static final Register[] XMM0_3 = {xmm0, xmm1, xmm2, xmm3};
    static final Register[] XMM0_7 = {xmm0, xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7};
    static final Register[] XMM0145 = {xmm0, xmm1, xmm4, xmm5};
    static final Register[] XMM0246 = {xmm0, xmm2, xmm4, xmm6};
    static final Register[] XMM0829 = {xmm0, xmm8, xmm2, xmm9};
    static final Register[] XMM1001 = {xmm1, xmm0, xmm0, xmm1};
    static final Register[] XMM1357 = {xmm1, xmm3, xmm5, xmm7};
    static final Register[] XMM2367 = {xmm2, xmm3, xmm6, xmm7};
    static final Register[] XMM2_0_10_8 = {xmm2, xmm0, xmm10, xmm8};
    static final Register[] XMM3223 = {xmm3, xmm2, xmm2, xmm3};
    static final Register[] XMM4_7 = {xmm4, xmm5, xmm6, xmm7};
    static final Register[] XMM5454 = {xmm5, xmm4, xmm5, xmm4};
    static final Register[] XMM7676 = {xmm7, xmm6, xmm7, xmm6};
    static final Register[] XMM8_11 = {xmm8, xmm9, xmm10, xmm11};
    static final Register[] XMM8_15 = {xmm8, xmm9, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15};
    static final Register[] XMM12_15 = {xmm12, xmm13, xmm14, xmm15};
    static final Register[] XMM16_19 = {xmm16, xmm17, xmm18, xmm19};
    static final Register[] XMM16_23 = {xmm16, xmm17, xmm18, xmm19, xmm20, xmm21, xmm22, xmm23};
    static final Register[] XMM20_23 = {xmm20, xmm21, xmm22, xmm23};
    static final Register[] XMM23_23 = {xmm23, xmm23, xmm23, xmm23};
    static final Register[] XMM24_27 = {xmm24, xmm25, xmm26, xmm27};
    static final Register[] XMM28_31 = {xmm28, xmm29, xmm30, xmm31};
    static final Register[] XMM29_29 = {xmm29, xmm29, xmm29, xmm29};

    static final ArrayDataPointerConstant Q_INV_MOD_R = pointerConstant(8, new long[]{0xF301F301F301F301L});
    static final ArrayDataPointerConstant Q = pointerConstant(8, new long[]{0x0D010D010D010D01L});
    static final ArrayDataPointerConstant BARRETT_MULTIPLIER = pointerConstant(8, new long[]{0x4EBF4EBF4EBF4EBFL});
    static final ArrayDataPointerConstant DIM_HALF_INVERSE = pointerConstant(8, new long[]{0x0200020002000200L});
    static final ArrayDataPointerConstant MONT_R_SQUARE_MOD_Q = pointerConstant(8, new long[]{0x0549054905490549L});
    static final ArrayDataPointerConstant F00 = pointerConstant(8, new long[]{0x0F000F000F000F00L});

    static final ArrayDataPointerConstant[] NTT_PERMS = {
                    pointerConstant(64, new long[]{0x0013001200110010L, 0x0017001600150014L, 0x001B001A00190018L, 0x001F001E001D001CL, 0x0033003200310030L, 0x0037003600350034L, 0x003B003A00390038L,
                                    0x003F003E003D003CL}),
                    pointerConstant(64, new long[]{0x0003000200010000L, 0x0007000600050004L, 0x000B000A00090008L, 0x000F000E000D000CL, 0x0023002200210020L, 0x0027002600250024L, 0x002B002A00290028L,
                                    0x002F002E002D002CL}),
                    pointerConstant(64, new long[]{0x0003000200010000L, 0x0007000600050004L, 0x0023002200210020L, 0x0027002600250024L, 0x0013001200110010L, 0x0017001600150014L, 0x0033003200310030L,
                                    0x0037003600350034L}),
                    pointerConstant(64, new long[]{0x000B000A00090008L, 0x000F000E000D000CL, 0x002B002A00290028L, 0x002F002E002D002CL, 0x001B001A00190018L, 0x001F001E001D001CL, 0x003B003A00390038L,
                                    0x003F003E003D003CL}),
                    pointerConstant(64, new long[]{0x0003000200010000L, 0x0023002200210020L, 0x000B000A00090008L, 0x002B002A00290028L, 0x0013001200110010L, 0x0033003200310030L, 0x001B001A00190018L,
                                    0x003B003A00390038L}),
                    pointerConstant(64, new long[]{0x0007000600050004L, 0x0027002600250024L, 0x000F000E000D000CL, 0x002F002E002D002CL, 0x0017001600150014L, 0x0037003600350034L, 0x001F001E001D001CL,
                                    0x003F003E003D003CL}),
                    pointerConstant(64, new long[]{0x0021002000010000L, 0x0025002400050004L, 0x0029002800090008L, 0x002D002C000D000CL, 0x0031003000110010L, 0x0035003400150014L, 0x0039003800190018L,
                                    0x003D003C001D001CL}),
                    pointerConstant(64, new long[]{0x0023002200030002L, 0x0027002600070006L, 0x002B002A000B000AL, 0x002F002E000F000EL, 0x0033003200130012L, 0x0037003600170016L, 0x003B003A001B001AL,
                                    0x003F003E001F001EL}),
                    pointerConstant(64, new long[]{0x0031003000110010L, 0x0033003200130012L, 0x0035003400150014L, 0x0037003600170016L, 0x0039003800190018L, 0x003B003A001B001AL, 0x003D003C001D001CL,
                                    0x003F003E001F001EL}),
                    pointerConstant(64, new long[]{0x0021002000010000L, 0x0023002200030002L, 0x0025002400050004L, 0x0027002600070006L, 0x0029002800090008L, 0x002B002A000B000AL, 0x002D002C000D000CL,
                                    0x002F002E000F000EL}),
    };

    static final ArrayDataPointerConstant[] INVERSE_NTT_PERMS = {
                    pointerConstant(64, new long[]{0x0007000600030002L, 0x000F000E000B000AL, 0x0017001600130012L, 0x001F001E001B001AL, 0x0027002600230022L, 0x002F002E002B002AL, 0x0037003600330032L,
                                    0x003F003E003B003AL}),
                    pointerConstant(64, new long[]{0x0005000400010000L, 0x000D000C00090008L, 0x0015001400110010L, 0x001D001C00190018L, 0x0025002400210020L, 0x002D002C00290028L, 0x0035003400310030L,
                                    0x003D003C00390038L}),
                    pointerConstant(64, new long[]{0x0021002000010000L, 0x0025002400050004L, 0x0029002800090008L, 0x002D002C000D000CL, 0x0031003000110010L, 0x0035003400150014L, 0x0039003800190018L,
                                    0x003D003C001D001CL}),
                    pointerConstant(64, new long[]{0x0023002200030002L, 0x0027002600070006L, 0x002B002A000B000AL, 0x002F002E000F000EL, 0x0033003200130012L, 0x0037003600170016L, 0x003B003A001B001AL,
                                    0x003F003E001F001EL}),
                    pointerConstant(64, new long[]{0x0003000200010000L, 0x0023002200210020L, 0x000B000A00090008L, 0x002B002A00290028L, 0x0013001200110010L, 0x0033003200310030L, 0x001B001A00190018L,
                                    0x003B003A00390038L}),
                    pointerConstant(64, new long[]{0x0007000600050004L, 0x0027002600250024L, 0x000F000E000D000CL, 0x002F002E002D002CL, 0x0017001600150014L, 0x0037003600350034L, 0x001F001E001D001CL,
                                    0x003F003E003D003CL}),
                    pointerConstant(64, new long[]{0x0003000200010000L, 0x0007000600050004L, 0x0023002200210020L, 0x0027002600250024L, 0x0013001200110010L, 0x0017001600150014L, 0x0033003200310030L,
                                    0x0037003600350034L}),
                    pointerConstant(64, new long[]{0x000B000A00090008L, 0x000F000E000D000CL, 0x002B002A00290028L, 0x002F002E002D002CL, 0x001B001A00190018L, 0x001F001E001D001CL, 0x003B003A00390038L,
                                    0x003F003E003D003CL}),
                    pointerConstant(64, new long[]{0x0013001200110010L, 0x0017001600150014L, 0x001B001A00190018L, 0x001F001E001D001CL, 0x0033003200310030L, 0x0037003600350034L, 0x003B003A00390038L,
                                    0x003F003E003D003CL}),
                    pointerConstant(64, new long[]{0x0003000200010000L, 0x0007000600050004L, 0x000B000A00090008L, 0x000F000E000D000CL, 0x0023002200210020L, 0x0027002600250024L, 0x002B002A00290028L,
                                    0x002F002E002D002CL}),
    };

    static final ArrayDataPointerConstant[] NTT_MULT_PERMS = {
                    pointerConstant(64, new long[]{0x0006000400020000L, 0x000E000C000A0008L, 0x0016001400120010L, 0x001E001C001A0018L, 0x0026002400220020L, 0x002E002C002A0028L, 0x0036003400320030L,
                                    0x003E003C003A0038L}),
                    pointerConstant(64, new long[]{0x0007000500030001L, 0x000F000D000B0009L, 0x0017001500130011L, 0x001F001D001B0019L, 0x0027002500230021L, 0x002F002D002B0029L, 0x0037003500330031L,
                                    0x003F003D003B0039L}),
                    pointerConstant(64, new long[]{0x0021000100200000L, 0x0023000300220002L, 0x0025000500240004L, 0x0027000700260006L, 0x0029000900280008L, 0x002B000B002A000AL, 0x002D000D002C000CL,
                                    0x002F000F002E000EL}),
                    pointerConstant(64, new long[]{0x0031001100300010L, 0x0033001300320012L, 0x0035001500340014L, 0x0037001700360016L, 0x0039001900380018L, 0x003B001B003A001AL, 0x003D001D003C001CL,
                                    0x003F001F003E001EL}),
    };

    static final ArrayDataPointerConstant PERMS_12_TO_16_TABLE = pointerConstant(64, new long[]{
                    0x0009000600030000L, 0x00150012000F000CL, 0x0021001E001B0018L, 0x002D002A00270024L, 0x0039003600330030L, 0x00000000003F003CL, 0x0000000000000000L, 0x0000000000000000L,
                    0x000A000700040001L, 0x001600130010000DL, 0x0022001F001C0019L, 0x002E002B00280025L, 0x003A003700340031L, 0x000000000000003DL, 0x0000000000000000L, 0x0000000000000000L,
                    0x000B000800050002L, 0x001700140011000EL, 0x00230020001D001AL, 0x002F002C00290026L, 0x003B003800350032L, 0x000000000000003EL, 0x0000000000000000L, 0x0000000000000000L,
                    0x0003000200010000L, 0x0007000600050004L, 0x000B000A00090008L, 0x000F000E000D000CL, 0x0013001200110010L, 0x0025002200150014L, 0x0031002E002B0028L, 0x003D003A00370034L,
                    0x0003000200010000L, 0x0007000600050004L, 0x000B000A00090008L, 0x000F000E000D000CL, 0x0013001200110010L, 0x0026002300200014L, 0x0032002F002C0029L, 0x003E003B00380035L,
                    0x0003000200010000L, 0x0007000600050004L, 0x000B000A00090008L, 0x000F000E000D000CL, 0x0013001200110010L, 0x0027002400210014L, 0x00330030002D002AL, 0x003F003C00390036L,
                    0x0021000100200000L, 0x0023000300220002L, 0x0025000500240004L, 0x0027000700260006L, 0x0029000900280008L, 0x002B000B002A000AL, 0x002D000D002C000CL, 0x002F000F002E000EL,
                    0x0031001100300010L, 0x0033001300320012L, 0x0035001500340014L, 0x0037001700360016L, 0x0039001900380018L, 0x003B001B003A001AL, 0x003D001D003C001CL, 0x003F001F003E001EL});

    private AMD64KyberSupport() {
    }

    static void load4regs(AMD64MacroAssembler masm, Register[] regs, Register base, int offset) {
        for (int i = 0; i < 4; i++) {
            masm.evmovdqu16(regs[i], new AMD64Address(base, offset + i * 64));
        }
    }

    static void store4regs(AMD64MacroAssembler masm, Register base, int offset, Register[] regs) {
        for (int i = 0; i < 4; i++) {
            masm.evmovdqu16(new AMD64Address(base, offset + i * 64), regs[i]);
        }
    }

    static void montmul(AMD64MacroAssembler masm, Register[] output, Register[] input1, Register[] input2, Register[] scratch1, Register[] scratch2) {
        for (int i = 0; i < 4; i++) {
            EVPMULLW.emit(masm, AVXSize.ZMM, scratch1[i], input1[i], input2[i]);
        }
        for (int i = 0; i < 4; i++) {
            EVPMULHW.emit(masm, AVXSize.ZMM, scratch2[i], input1[i], input2[i]);
        }
        for (int i = 0; i < 4; i++) {
            EVPMULLW.emit(masm, AVXSize.ZMM, scratch1[i], scratch1[i], xmm31);
        }
        for (int i = 0; i < 4; i++) {
            EVPMULHW.emit(masm, AVXSize.ZMM, scratch1[i], scratch1[i], xmm30);
        }
        for (int i = 0; i < 4; i++) {
            EVPSUBW.emit(masm, AVXSize.ZMM, output[i], scratch2[i], scratch1[i]);
        }
    }

    static void subAdd(AMD64MacroAssembler masm, Register[] subResult, Register[] addResult, Register[] input1, Register[] input2) {
        for (int i = 0; i < 4; i++) {
            EVPSUBW.emit(masm, AVXSize.ZMM, subResult[i], input1[i], input2[i]);
            EVPADDW.emit(masm, AVXSize.ZMM, addResult[i], input1[i], input2[i]);
        }
    }

    static void permute(AMD64MacroAssembler masm, Register[] result1, Register[] result2, Register[] input2, Register perm2) {
        for (int i = 1; i < 4; i++) {
            masm.evmovdqu16(result1[i], result1[0]);
        }
        for (int i = 0; i < 4; i++) {
            masm.evpermi2w(result1[i], result2[i], input2[i]);
            masm.evpermt2w(result2[i], perm2, input2[i]);
        }
    }

    static void barrettReduce(AMD64MacroAssembler masm) {
        for (int i = 0; i < 8; i++) {
            EVPMULHW.emit(masm, AVXSize.ZMM, XMM8_15[i], XMM0_7[i], xmm16);
        }
        for (int i = 0; i < 8; i++) {
            EVPSRAW.emit(masm, AVXSize.ZMM, XMM8_15[i], XMM8_15[i], 10);
        }
        for (int i = 0; i < 8; i++) {
            EVPMULLW.emit(masm, AVXSize.ZMM, XMM8_15[i], XMM8_15[i], xmm17);
        }
        for (int i = 0; i < 8; i++) {
            EVPSUBW.emit(masm, AVXSize.ZMM, XMM0_7[i], XMM0_7[i], XMM8_15[i]);
        }
    }
}
