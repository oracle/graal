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
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PAIR_SIGNED_SCALED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createImmediateAddress;
import static jdk.graal.compiler.lir.aarch64.AArch64LIRHelper.guaranteeFixedRegister;
import static jdk.graal.compiler.lir.aarch64.AArch64LIRHelper.loadExternalAddress;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.KYBER_CONSTS_DATA;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsLdpqPost;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsMlsv;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsSqdmulh;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsSshr;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsStpqPost;
import static jdk.vm.ci.aarch64.AArch64.r0;
import static jdk.vm.ci.aarch64.AArch64.r11;
import static jdk.vm.ci.aarch64.AArch64.r20;
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
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fe611a2a3386d097f636c15bd4d396a82dc695e/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L6241-L6327",
          sha1 = "74802f0554cf1eaf4e7604bbd9319037730658f4")
// @formatter:on
public final class AArch64KyberBarrettReduceOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64KyberBarrettReduceOp> TYPE = LIRInstructionClass.create(AArch64KyberBarrettReduceOp.class);

    @Def private Value resultValue;
    @Use private Value coeffsValue;
    @Temp private Value[] temps;

    public AArch64KyberBarrettReduceOp(AllocatableValue resultValue, AllocatableValue coeffsValue) {
        super(TYPE);
        guaranteeFixedRegister(resultValue, r0, "resultValue");
        guaranteeFixedRegister(coeffsValue, r0, "coeffsValue");
        this.resultValue = resultValue;
        this.coeffsValue = coeffsValue;
        this.temps = new Value[]{
                        r0.asValue(),
                        r20.asValue(),
                        r11.asValue(),
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
        Register resultRegister = asRegister(resultValue);
        Register coeffs = asRegister(coeffsValue);
        Register kyberConsts = r20;
        Register result = r11;

        // Kyber Barrett reduce function.
        // Implements
        // static int implKyberBarrettReduce(short[] coeffs) {}
        //
        // coeffs (short[256]) = c_rarg0
        // As above we process 256 sets of values in total i.e. 32 x
        // 8H quadwords. So, we can load, add and store the data in 3
        // groups of 11, 11 and 10 at a time i.e. we need to map sets
        // of 10 or 11 registers. A further constraint is that the
        // mapping needs to skip callee saves. So, we allocate the
        // register sequences using two 8 sequences, two 2 sequences
        // and two single registers.
        VectorRegisterSeq vs1A = VectorRegisterSeq.create(0, 8);
        VectorRegisterSeq vs1B = VectorRegisterSeq.create(16, 2);
        Register vs1C = v28;
        VectorRegisterSeq vs2A = VectorRegisterSeq.create(18, 8);
        VectorRegisterSeq vs2B = VectorRegisterSeq.create(26, 2);
        Register vs2C = v29;

        // we also need a pair of corresponding constant sequences
        VectorRegisterSeq vc1A = VectorRegisterSeq.create(v30, 0, 8);
        VectorRegisterSeq vc1B = VectorRegisterSeq.create(v30, 0, 2);
        Register vc1C = v30; // for kyber_q
        VectorRegisterSeq vc2A = VectorRegisterSeq.create(v31, 0, 8);
        VectorRegisterSeq vc2B = VectorRegisterSeq.create(v31, 0, 2);
        Register vc2C = v31; // for kyberBarrettMultiplier

        masm.add(64, result, coeffs, 0);
        loadExternalAddress(crb, masm, kyberConsts, KYBER_CONSTS_DATA);

        // load q and the multiplier for the Barrett reduction
        masm.add(64, kyberConsts, kyberConsts, 16);
        masm.fldp(128, vc1C, vc2C, createImmediateAddress(128, IMMEDIATE_PAIR_SIGNED_SCALED, kyberConsts, 0));

        for (int i = 0; i < 3; i++) {
            // load 80 or 88 coefficients
            vsLdpqPost(masm, vs1A, coeffs);
            vsLdpqPost(masm, vs1B, coeffs);
            if (i < 2) {
                masm.fldr(128, vs1C, createImmediateAddress(128, IMMEDIATE_POST_INDEXED, coeffs, 16));
            }

            // vs2 <- (2 * vs1 * kyberBarrettMultiplier) >> 16
            vsSqdmulh(masm, vs2A, vs1A, vc2A);
            vsSqdmulh(masm, vs2B, vs1B, vc2B);
            if (i < 2) {
                masm.neon.sqdmulhVVV(FullReg, HalfWord, vs2C, vs1C, vc2C);
            }

            // vs2 <- (vs1 * kyberBarrettMultiplier) >> 26
            vsSshr(masm, vs2A, vs2A, 11);
            vsSshr(masm, vs2B, vs2B, 11);
            if (i < 2) {
                masm.neon.sshrVVI(FullReg, HalfWord, vs2C, vs2C, 11);
            }

            // vs1 <- vs1 - vs2 * kyber_q
            vsMlsv(masm, vs1A, vs2A, vc1A);
            vsMlsv(masm, vs1B, vs2B, vc1B);
            if (i < 2) {
                masm.neon.mlsVVV(FullReg, HalfWord, vs1C, vs2C, vc1C);
            }

            vsStpqPost(masm, vs1A, result);
            vsStpqPost(masm, vs1B, result);
            if (i < 2) {
                masm.fstr(128, vs1C, createImmediateAddress(128, IMMEDIATE_POST_INDEXED, result, 16));
            }
        }

        masm.mov(32, resultRegister, zr);
    }
}
