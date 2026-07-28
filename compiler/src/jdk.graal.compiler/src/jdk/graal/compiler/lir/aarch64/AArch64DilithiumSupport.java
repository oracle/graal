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

import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PAIR_SIGNED_SCALED;

import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Register;

final class AArch64DilithiumSupport {

    static final Register[] VQ = {AArch64.v30, AArch64.v31};
    static final Register[] VTMP = {AArch64.v24, AArch64.v25, AArch64.v26, AArch64.v27};

    private AArch64DilithiumSupport() {
    }

    static void loadQInvQ(AArch64MacroAssembler masm, Register dilithiumConsts) {
        masm.fldp(128, AArch64.v30, AArch64.v31, AArch64Address.createImmediateAddress(128, IMMEDIATE_PAIR_SIGNED_SCALED, dilithiumConsts, 0));
    }

    // Perform 16 32-bit Montgomery multiplications in parallel.
    static void emitMontMul16(AArch64MacroAssembler masm,
                    Register[] va,
                    Register[] vb,
                    Register[] vc,
                    Register[] vtmp,
                    Register[] vq) {
        for (int i = 0; i < 4; i++) {
            masm.neon.sqdmulhVVV(FullReg, ElementSize.Word, vtmp[i], vb[i], vc[i]); // aHigh = hi32(2 * b * c)
            masm.neon.mulVVV(FullReg, ElementSize.Word, va[i], vb[i], vc[i]);       // aLow = lo32(b * c)
        }

        for (int i = 0; i < 4; i++) {
            masm.neon.mulVVV(FullReg, ElementSize.Word, va[i], va[i], vq[0]);        // m = aLow * qinv
        }

        for (int i = 0; i < 4; i++) {
            masm.neon.sqdmulhVVV(FullReg, ElementSize.Word, va[i], va[i], vq[1]);   // n = hi32(2 * m * q)
        }

        for (int i = 0; i < 4; i++) {
            masm.neon.shsubVVV(FullReg, ElementSize.Word, va[i], vtmp[i], va[i]);   // a = (aHigh - n) / 2
        }
    }

    // Perform 2x16 32-bit Montgomery multiplications in parallel.
    static void emitMontMul32(AArch64MacroAssembler masm,
                    Register[] va,
                    Register[] vb,
                    Register[] vc,
                    Register[] vtmp,
                    Register[] vq) {
        emitMontMul16(masm, front(va), front(vb), front(vc), vtmp, vq);
        emitMontMul16(masm, back(va), back(vb), back(vc), vtmp, vq);
    }

    // Perform combined montmul then add/sub on 4x4S vectors.
    static void emitMontMul16SubAdd(AArch64MacroAssembler masm,
                    Register[] va0,
                    Register[] va1,
                    Register[] vc,
                    Register[] vtmp,
                    Register[] vq) {
        emitMontMul16(masm, vc, va1, vc, vtmp, vq);
        for (int i = 0; i < 4; i++) {
            masm.neon.subVVV(FullReg, ElementSize.Word, va1[i], va0[i], vc[i]);
        }
        for (int i = 0; i < 4; i++) {
            masm.neon.addVVV(FullReg, ElementSize.Word, va0[i], va0[i], vc[i]);
        }
    }

    // Perform combined add/sub then montmul on 4x4S vectors.
    static void emitSubAddMontMul16(AArch64MacroAssembler masm,
                    Register[] va0,
                    Register[] va1,
                    Register[] vb,
                    Register[] vtmp1,
                    Register[] vtmp2,
                    Register[] vq) {
        for (int i = 0; i < 4; i++) {
            masm.neon.subVVV(FullReg, ElementSize.Word, vtmp1[i], va0[i], va1[i]);
        }
        for (int i = 0; i < 4; i++) {
            masm.neon.addVVV(FullReg, ElementSize.Word, va0[i], va0[i], va1[i]);
        }
        emitMontMul16(masm, va1, vtmp1, vb, vtmp2, vq);
    }

    private static Register[] front(Register[] registers) {
        return new Register[]{registers[0], registers[1], registers[2], registers[3]};
    }

    private static Register[] back(Register[] registers) {
        return new Register[]{registers[4], registers[5], registers[6], registers[7]};
    }
}
