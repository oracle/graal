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
package jdk.graal.compiler.lir.aarch64;

import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD2_MULTIPLE_2R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD3_MULTIPLE_3R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.ST2_MULTIPLE_2R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize.HalfWord;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PAIR_SIGNED_SCALED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_SIGNED_UNSCALED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createImmediateAddress;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createStructureImmediatePostIndexAddress;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createStructureNoOffsetAddress;
import static jdk.graal.compiler.lir.aarch64.AArch64LIRHelper.pointerConstant;
import static jdk.vm.ci.aarch64.AArch64.simdRegisters;

import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.vm.ci.code.Register;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fe611a2a3386d097f636c15bd4d396a82dc695e/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L4724-L5197",
          sha1 = "3ba3fd60bf5c19bc610af87581343dcffbf0fd27")
// @formatter:on
final class AArch64KyberSupport {

    static final ArrayDataPointerConstant KYBER_CONSTS_DATA = pointerConstant(16, new long[]{
                    0xF301F301F301F301L, 0xF301F301F301F301L,
                    0x0D010D010D010D01L, 0x0D010D010D010D01L,
                    0x4EBF4EBF4EBF4EBFL, 0x4EBF4EBF4EBF4EBFL,
                    0x0200020002000200L, 0x0200020002000200L,
                    0x0549054905490549L, 0x0549054905490549L,
    });

    static final int[] OFFSETS_0 = {0, 256};
    static final int[] OFFSETS_1 = {0, 32, 128, 160};
    static final int[] OFFSETS_2 = {0, 64, 128, 192};
    static final int[] OFFSETS_3 = {0, 32, 64, 96, 128, 160, 192, 224};
    static final int[] OFFSETS_4 = {0, 32, 64, 96};

    private AArch64KyberSupport() {
    }

    /*
     * Kyber helpers work on contiguous and strided SIMD register groups. VectorRegisterSeq keeps
     * those HotSpot register ranges explicit without allocating arrays at each helper call.
     */
    record VectorRegisterSeq(int base, int delta, int length) {
        Register get(int index) {
            return simdRegisters.get(base + index * delta);
        }

        static VectorRegisterSeq create(int base, int length) {
            return new VectorRegisterSeq(base, 1, length);
        }

        static VectorRegisterSeq create(Register base, int delta, int length) {
            return new VectorRegisterSeq(base.encoding, delta, length);
        }

        VectorRegisterSeq front() {
            return new VectorRegisterSeq(base, delta, length / 2);
        }

        VectorRegisterSeq back() {
            return new VectorRegisterSeq(base + delta * length / 2, delta, length / 2);
        }

        VectorRegisterSeq even() {
            return new VectorRegisterSeq(base, delta * 2, length / 2);
        }

        VectorRegisterSeq odd() {
            return new VectorRegisterSeq(base + delta, delta * 2, length / 2);
        }

        VectorRegisterSeq reverse() {
            return new VectorRegisterSeq(base + delta * (length - 1), -delta, length);
        }
    }

    static void vsAddv(AArch64MacroAssembler masm, VectorRegisterSeq v, VectorRegisterSeq v1, VectorRegisterSeq v2) {
        for (int i = 0; i < v.length(); i++) {
            masm.neon.addVVV(FullReg, HalfWord, v.get(i), v1.get(i), v2.get(i));
        }
    }

    static void vsSubv(AArch64MacroAssembler masm, VectorRegisterSeq v, VectorRegisterSeq v1, VectorRegisterSeq v2) {
        for (int i = 0; i < v.length(); i++) {
            masm.neon.subVVV(FullReg, HalfWord, v.get(i), v1.get(i), v2.get(i));
        }
    }

    static void vsSqdmulh(AArch64MacroAssembler masm, VectorRegisterSeq v, VectorRegisterSeq v1, VectorRegisterSeq v2) {
        for (int i = 0; i < v.length(); i++) {
            masm.neon.sqdmulhVVV(FullReg, HalfWord, v.get(i), v1.get(i), v2.get(i));
        }
    }

    static void vsSshr(AArch64MacroAssembler masm, VectorRegisterSeq v, VectorRegisterSeq v1, int shift) {
        for (int i = 0; i < v.length(); i++) {
            masm.neon.sshrVVI(FullReg, HalfWord, v.get(i), v1.get(i), shift);
        }
    }

    static void vsMlsv(AArch64MacroAssembler masm, VectorRegisterSeq v, VectorRegisterSeq v1, VectorRegisterSeq v2) {
        for (int i = 0; i < v.length(); i++) {
            masm.neon.mlsVVV(FullReg, HalfWord, v.get(i), v1.get(i), v2.get(i));
        }
    }

    static void vsLdpq(AArch64MacroAssembler masm, VectorRegisterSeq v, Register base) {
        for (int i = 0; i < v.length(); i += 2) {
            masm.fldp(128, v.get(i), v.get(i + 1), createImmediateAddress(128, IMMEDIATE_PAIR_SIGNED_SCALED, base, 32 * i));
        }
    }

    static void vsLdpqPost(AArch64MacroAssembler masm, VectorRegisterSeq v, Register base) {
        for (int i = 0; i < v.length(); i += 2) {
            masm.fldp(128, v.get(i), v.get(i + 1), createImmediateAddress(128, IMMEDIATE_PAIR_POST_INDEXED, base, 32));
        }
    }

    static void vsStpqPost(AArch64MacroAssembler masm, VectorRegisterSeq v, Register base) {
        for (int i = 0; i < v.length(); i += 2) {
            masm.fstp(128, v.get(i), v.get(i + 1), createImmediateAddress(128, IMMEDIATE_PAIR_POST_INDEXED, base, 32));
        }
    }

    static void vsLd2Post(AArch64MacroAssembler masm, VectorRegisterSeq v, ASIMDSize size, ElementSize eSize, Register base) {
        for (int i = 0; i < v.length(); i += 2) {
            masm.neon.ld2MultipleVV(size, eSize, v.get(i), v.get(i + 1),
                            createStructureImmediatePostIndexAddress(LD2_MULTIPLE_2R, size, eSize, base, 32));
        }
    }

    static void vsLd3Post(AArch64MacroAssembler masm, VectorRegisterSeq v, ASIMDSize size, ElementSize eSize, Register base) {
        int immediate = size.bytes() * 3;
        for (int i = 0; i < v.length(); i += 3) {
            masm.neon.ld3MultipleVVV(size, eSize, v.get(i), v.get(i + 1), v.get(i + 2),
                            createStructureImmediatePostIndexAddress(LD3_MULTIPLE_3R, size, eSize, base, immediate));
        }
    }

    static void vsSt2Post(AArch64MacroAssembler masm, VectorRegisterSeq v, ASIMDSize size, ElementSize eSize, Register base) {
        for (int i = 0; i < v.length(); i += 2) {
            masm.neon.st2MultipleVV(size, eSize, v.get(i), v.get(i + 1),
                            createStructureImmediatePostIndexAddress(ST2_MULTIPLE_2R, size, eSize, base, 32));
        }
    }

    static void vsLdpqIndexed(AArch64MacroAssembler masm, VectorRegisterSeq v, Register base, int start, int[] offsets) {
        for (int i = 0; i < v.length() / 2; i++) {
            masm.fldp(128, v.get(2 * i), v.get(2 * i + 1),
                            createImmediateAddress(128, IMMEDIATE_PAIR_SIGNED_SCALED, base, start + offsets[i]));
        }
    }

    static void vsStpqIndexed(AArch64MacroAssembler masm, VectorRegisterSeq v, Register base, int start, int[] offsets) {
        for (int i = 0; i < v.length() / 2; i++) {
            masm.fstp(128, v.get(2 * i), v.get(2 * i + 1),
                            createImmediateAddress(128, IMMEDIATE_PAIR_SIGNED_SCALED, base, start + offsets[i]));
        }
    }

    static void vsLdrIndexed(AArch64MacroAssembler masm, VectorRegisterSeq v, Register base, int start, int[] offsets) {
        for (int i = 0; i < v.length(); i++) {
            masm.fldr(128, v.get(i),
                            createImmediateAddress(128, start + offsets[i] >= 0 ? IMMEDIATE_UNSIGNED_SCALED : IMMEDIATE_SIGNED_UNSCALED, base, start + offsets[i]));
        }
    }

    static void vsStrIndexed(AArch64MacroAssembler masm, VectorRegisterSeq v, Register base, int start, int[] offsets) {
        for (int i = 0; i < v.length(); i++) {
            masm.fstr(128, v.get(i),
                            createImmediateAddress(128, start + offsets[i] >= 0 ? IMMEDIATE_UNSIGNED_SCALED : IMMEDIATE_SIGNED_UNSCALED, base, start + offsets[i]));
        }
    }

    static void vsLd2Indexed(AArch64MacroAssembler masm, VectorRegisterSeq v, ASIMDSize size, ElementSize eSize, Register base, Register tmp, int start, int[] offsets) {
        for (int i = 0; i < v.length() / 2; i++) {
            masm.add(64, tmp, base, start + offsets[i]);
            masm.neon.ld2MultipleVV(size, eSize, v.get(2 * i), v.get(2 * i + 1), createStructureNoOffsetAddress(tmp));
        }
    }

    static void vsSt2Indexed(AArch64MacroAssembler masm, VectorRegisterSeq v, ASIMDSize size, ElementSize eSize, Register base, Register tmp, int start, int[] offsets) {
        for (int i = 0; i < v.length() / 2; i++) {
            masm.add(64, tmp, base, start + offsets[i]);
            masm.neon.st2MultipleVV(size, eSize, v.get(2 * i), v.get(2 * i + 1), createStructureNoOffsetAddress(tmp));
        }
    }

    static void vsMontmul4(AArch64MacroAssembler masm, VectorRegisterSeq va, VectorRegisterSeq vb, VectorRegisterSeq vc, VectorRegisterSeq vtmp, VectorRegisterSeq vq) {
        for (int i = 0; i < 4; i++) {
            masm.neon.sqdmulhVVV(FullReg, HalfWord, vtmp.get(i), vb.get(i), vc.get(i));
            masm.neon.mulVVV(FullReg, HalfWord, va.get(i), vb.get(i), vc.get(i));
        }
        for (int i = 0; i < 4; i++) {
            masm.neon.mulVVV(FullReg, HalfWord, va.get(i), va.get(i), vq.get(0));
        }
        for (int i = 0; i < 4; i++) {
            masm.neon.sqdmulhVVV(FullReg, HalfWord, va.get(i), va.get(i), vq.get(1));
        }
        for (int i = 0; i < 4; i++) {
            masm.neon.shsubVVV(FullReg, HalfWord, va.get(i), vtmp.get(i), va.get(i));
        }
    }

    static void kyberMontmul16(AArch64MacroAssembler masm, VectorRegisterSeq va, VectorRegisterSeq vb, VectorRegisterSeq vc, VectorRegisterSeq vtmp, VectorRegisterSeq vq) {
        for (int i = 0; i < 2; i++) {
            masm.neon.sqdmulhVVV(FullReg, HalfWord, vtmp.get(i), vb.get(i), vc.get(i));
            masm.neon.mulVVV(FullReg, HalfWord, va.get(i), vb.get(i), vc.get(i));
        }
        for (int i = 0; i < 2; i++) {
            masm.neon.mulVVV(FullReg, HalfWord, va.get(i), va.get(i), vq.get(0));
        }
        for (int i = 0; i < 2; i++) {
            masm.neon.sqdmulhVVV(FullReg, HalfWord, va.get(i), va.get(i), vq.get(1));
        }
        for (int i = 0; i < 2; i++) {
            masm.neon.shsubVVV(FullReg, HalfWord, va.get(i), vtmp.get(i), va.get(i));
        }
    }

    static void kyberMontmul64(AArch64MacroAssembler masm, VectorRegisterSeq va, VectorRegisterSeq vb, VectorRegisterSeq vc, VectorRegisterSeq vtmp, VectorRegisterSeq vq) {
        vsMontmul4(masm, va.front(), vb.front(), vc.front(), vtmp, vq);
        vsMontmul4(masm, va.back(), vb.back(), vc.back(), vtmp, vq);
    }

    static void kyberMontmul32SubAdd(AArch64MacroAssembler masm, VectorRegisterSeq va0, VectorRegisterSeq va1, VectorRegisterSeq vc, VectorRegisterSeq vtmp, VectorRegisterSeq vq) {
        vsMontmul4(masm, vc, va1, vc, vtmp, vq);
        vsSubv(masm, va1, va0, vc);
        vsAddv(masm, va0, va0, vc);
    }

    static void kyberSubAddMontmul32(AArch64MacroAssembler masm, VectorRegisterSeq va0, VectorRegisterSeq va1, VectorRegisterSeq vb, VectorRegisterSeq vtmp1, VectorRegisterSeq vtmp2,
                    VectorRegisterSeq vq) {
        vsSubv(masm, vtmp1, va0, va1);
        vsAddv(masm, va0, va0, va1);
        vsMontmul4(masm, va1, vtmp1, vb, vtmp2, vq);
    }

}
