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
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize.HalfWord;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createImmediateAddress;
import static jdk.graal.compiler.lir.aarch64.AArch64LIRHelper.guaranteeFixedRegister;
import static jdk.graal.compiler.lir.aarch64.AArch64LIRHelper.loadExternalAddress;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.KYBER_CONSTS_DATA;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsAddv;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsLdpqPost;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsStpqPost;
import static jdk.vm.ci.aarch64.AArch64.r0;
import static jdk.vm.ci.aarch64.AArch64.r1;
import static jdk.vm.ci.aarch64.AArch64.r20;
import static jdk.vm.ci.aarch64.AArch64.r2;
import static jdk.vm.ci.aarch64.AArch64.v0;
import static jdk.vm.ci.aarch64.AArch64.v10;
import static jdk.vm.ci.aarch64.AArch64.v11;
import static jdk.vm.ci.aarch64.AArch64.v12;
import static jdk.vm.ci.aarch64.AArch64.v13;
import static jdk.vm.ci.aarch64.AArch64.v14;
import static jdk.vm.ci.aarch64.AArch64.v15;
import static jdk.vm.ci.aarch64.AArch64.v16;
import static jdk.vm.ci.aarch64.AArch64.v17;
import static jdk.vm.ci.aarch64.AArch64.v18;
import static jdk.vm.ci.aarch64.AArch64.v19;
import static jdk.vm.ci.aarch64.AArch64.v1;
import static jdk.vm.ci.aarch64.AArch64.v20;
import static jdk.vm.ci.aarch64.AArch64.v21;
import static jdk.vm.ci.aarch64.AArch64.v22;
import static jdk.vm.ci.aarch64.AArch64.v23;
import static jdk.vm.ci.aarch64.AArch64.v24;
import static jdk.vm.ci.aarch64.AArch64.v25;
import static jdk.vm.ci.aarch64.AArch64.v26;
import static jdk.vm.ci.aarch64.AArch64.v27;
import static jdk.vm.ci.aarch64.AArch64.v28;
import static jdk.vm.ci.aarch64.AArch64.v29;
import static jdk.vm.ci.aarch64.AArch64.v2;
import static jdk.vm.ci.aarch64.AArch64.v30;
import static jdk.vm.ci.aarch64.AArch64.v31;
import static jdk.vm.ci.aarch64.AArch64.v3;
import static jdk.vm.ci.aarch64.AArch64.v4;
import static jdk.vm.ci.aarch64.AArch64.v5;
import static jdk.vm.ci.aarch64.AArch64.v6;
import static jdk.vm.ci.aarch64.AArch64.v7;
import static jdk.vm.ci.aarch64.AArch64.v8;
import static jdk.vm.ci.aarch64.AArch64.v9;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.VectorRegisterSeq;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fe611a2a3386d097f636c15bd4d396a82dc695e/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L5845-L5920",
          sha1 = "9fe5f9df7e14c593773b9385296bebbae15fa72f")
// @formatter:on
public final class AArch64KyberAddPoly2Op extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64KyberAddPoly2Op> TYPE = LIRInstructionClass.create(AArch64KyberAddPoly2Op.class);

    @Def private Value resultValue;
    @Use private Value resultPointerValue;
    @Use private Value aValue;
    @Use private Value bValue;
    @Temp private Value[] temps;

    public AArch64KyberAddPoly2Op(AllocatableValue resultValue, AllocatableValue resultPointerValue, AllocatableValue aValue, AllocatableValue bValue) {
        super(TYPE);
        guaranteeFixedRegister(resultValue, r0, "resultValue");
        guaranteeFixedRegister(resultPointerValue, r0, "resultPointerValue");
        guaranteeFixedRegister(aValue, r1, "aValue");
        guaranteeFixedRegister(bValue, r2, "bValue");
        this.resultValue = resultValue;
        this.resultPointerValue = resultPointerValue;
        this.aValue = aValue;
        this.bValue = bValue;
        this.temps = new Value[]{
                        r0.asValue(),
                        r1.asValue(),
                        r2.asValue(),
                        r20.asValue(),
                        v0.asValue(),
                        v1.asValue(),
                        v2.asValue(),
                        v3.asValue(),
                        v4.asValue(),
                        v5.asValue(),
                        v6.asValue(),
                        v7.asValue(),
                        v8.asValue(),
                        v9.asValue(),
                        v10.asValue(),
                        v11.asValue(),
                        v12.asValue(),
                        v13.asValue(),
                        v14.asValue(),
                        v15.asValue(),
                        v16.asValue(),
                        v17.asValue(),
                        v18.asValue(),
                        v19.asValue(),
                        v20.asValue(),
                        v21.asValue(),
                        v22.asValue(),
                        v23.asValue(),
                        v24.asValue(),
                        v25.asValue(),
                        v26.asValue(),
                        v27.asValue(),
                        v28.asValue(),
                        v29.asValue(),
                        v30.asValue(),
                        v31.asValue(),
        };
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register result = asRegister(resultValue);
        Register resultPointer = asRegister(resultPointerValue);
        Register a = asRegister(aValue);
        Register b = asRegister(bValue);

        // Kyber add 2 polynomials.
        // Implements
        // static int implKyberAddPoly(short[] result, short[] a, short[] b) {}
        //
        // result (short[256]) = c_rarg0
        // a (short[256]) = c_rarg1
        // b (short[256]) = c_rarg2

        // We sum 256 sets of values in total i.e. 32 x 8H quadwords.
        // So, we can load, add and store the data in 3 groups of 11,
        // 11 and 10 at a time i.e. we need to map sets of 10 or 11
        // registers. A further constraint is that the mapping needs
        // to skip callee saves. So, we allocate the register
        // sequences using two 8 sequences, two 2 sequences and two
        // single registers.
        VectorRegisterSeq vs1A = VectorRegisterSeq.create(0, 8);
        VectorRegisterSeq vs1B = VectorRegisterSeq.create(16, 2);
        Register vs1C = v28;
        VectorRegisterSeq vs2A = VectorRegisterSeq.create(18, 8);
        VectorRegisterSeq vs2B = VectorRegisterSeq.create(26, 2);
        Register vs2C = v29;

        // two constant vector sequences
        VectorRegisterSeq vcA = VectorRegisterSeq.create(v31, 0, 8);
        VectorRegisterSeq vcB = VectorRegisterSeq.create(v31, 0, 2);
        Register vcC = v31;

        loadExternalAddress(crb, masm, r20, KYBER_CONSTS_DATA);
        masm.fldr(128, vcC, createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, r20, 16));
        for (int i = 0; i < 3; i++) {
            // load 80 or 88 values from a into vs1A/2/3
            vsLdpqPost(masm, vs1A, a);
            vsLdpqPost(masm, vs1B, a);
            if (i < 2) {
                masm.fldr(128, vs1C, createImmediateAddress(128, IMMEDIATE_POST_INDEXED, a, 16));
            }
            // load 80 or 88 values from b into vs2A/2/3
            vsLdpqPost(masm, vs2A, b);
            vsLdpqPost(masm, vs2B, b);
            if (i < 2) {
                masm.fldr(128, vs2C, createImmediateAddress(128, IMMEDIATE_POST_INDEXED, b, 16));
            }
            // sum 80 or 88 values across vs1 and vs2 into vs1
            vsAddv(masm, vs1A, vs1A, vs2A);
            vsAddv(masm, vs1B, vs1B, vs2B);
            if (i < 2) {
                masm.neon.addVVV(FullReg, HalfWord, vs1C, vs1C, vs2C);
            }
            // add constant to all 80 or 88 results
            vsAddv(masm, vs1A, vs1A, vcA);
            vsAddv(masm, vs1B, vs1B, vcB);
            if (i < 2) {
                masm.neon.addVVV(FullReg, HalfWord, vs1C, vs1C, vcC);
            }
            // store 80 or 88 values
            vsStpqPost(masm, vs1A, resultPointer);
            vsStpqPost(masm, vs1B, resultPointer);
            if (i < 2) {
                masm.fstr(128, vs1C, createImmediateAddress(128, IMMEDIATE_POST_INDEXED, resultPointer, 16));
            }
        }

        masm.mov(32, result, zr);
    }
}
