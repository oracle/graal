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
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize.DoubleWord;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize.Word;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createImmediateAddress;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.OFFSETS_0;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.OFFSETS_1;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.OFFSETS_2;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.OFFSETS_3;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.OFFSETS_4;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.kyberMontmul64;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.kyberSubAddMontmul32;
import static jdk.graal.compiler.lir.aarch64.AArch64LIRHelper.guaranteeFixedRegister;
import static jdk.graal.compiler.lir.aarch64.AArch64LIRHelper.loadExternalAddress;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.KYBER_CONSTS_DATA;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsAddv;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsLd2Indexed;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsLdpq;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsLdpqIndexed;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsLdpqPost;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsLdrIndexed;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsMlsv;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsSqdmulh;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsSshr;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsSt2Indexed;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsStpqIndexed;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsStpqPost;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsStrIndexed;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsSubv;
import static jdk.vm.ci.aarch64.AArch64.r0;
import static jdk.vm.ci.aarch64.AArch64.r1;
import static jdk.vm.ci.aarch64.AArch64.r11;
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
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fe611a2a3386d097f636c15bd4d396a82dc695e/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L5441-L5717",
          sha1 = "caf64e066d6292818b97620ea69dd0f5df0532cf")
// @formatter:on
public final class AArch64KyberInverseNttOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64KyberInverseNttOp> TYPE = LIRInstructionClass.create(AArch64KyberInverseNttOp.class);

    @Def private Value resultValue;
    @Use private Value polyValue;
    @Use private Value zetasValue;
    @Temp private Value[] temps;

    public AArch64KyberInverseNttOp(AllocatableValue resultValue, AllocatableValue polyValue, AllocatableValue zetasValue) {
        super(TYPE);
        guaranteeFixedRegister(resultValue, r0, "resultValue");
        guaranteeFixedRegister(polyValue, r0, "polyValue");
        guaranteeFixedRegister(zetasValue, r1, "zetasValue");
        this.resultValue = resultValue;
        this.polyValue = polyValue;
        this.zetasValue = zetasValue;
        this.temps = new Value[]{
                        r1.asValue(),
                        r2.asValue(),
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
        Register result = asRegister(resultValue);
        Register coeffs = asRegister(polyValue);
        Register zetas = asRegister(zetasValue);
        Register kyberConsts = r20;
        Register tmpAddr = r11;

        // Kyber Inverse NTT function
        // Implements
        // static int implKyberInverseNtt(short[] poly, short[] zetas) {}
        //
        // coeffs (short[256]) = c_rarg0
        // ntt_zetas (short[256]) = c_rarg1
        VectorRegisterSeq vs1 = VectorRegisterSeq.create(0, 8);
        VectorRegisterSeq vs2 = VectorRegisterSeq.create(16, 8);
        VectorRegisterSeq vs3 = VectorRegisterSeq.create(24, 8);
        VectorRegisterSeq vtmp = vs3.front();      // n.b. tmp registers overlap vs3
        VectorRegisterSeq vq = VectorRegisterSeq.create(30, 2);       // n.b. constants overlap vs3

        loadExternalAddress(crb, masm, kyberConsts, KYBER_CONSTS_DATA);

        // level 0
        // At level 0 related coefficients occur in discrete blocks of size 4 so
        // need to be loaded interleaved using an ld2 operation with arrangement 4S.

        vsLdpq(masm, vq, kyberConsts);
        vsLd2Indexed(masm, vs1, FullReg, Word, coeffs, tmpAddr, 0, OFFSETS_4);
        vsLdpqPost(masm, vs2.front(), zetas);
        kyberSubAddMontmul32(masm, vs1.even(), vs1.odd(), vs2.front(), vs2.back(), vtmp, vq);
        vsSt2Indexed(masm, vs1, FullReg, Word, coeffs, tmpAddr, 0, OFFSETS_4);
        vsLd2Indexed(masm, vs1, FullReg, Word, coeffs, tmpAddr, 128, OFFSETS_4);
        vsLdpqPost(masm, vs2.front(), zetas);
        kyberSubAddMontmul32(masm, vs1.even(), vs1.odd(), vs2.front(), vs2.back(), vtmp, vq);
        vsSt2Indexed(masm, vs1, FullReg, Word, coeffs, tmpAddr, 128, OFFSETS_4);
        vsLd2Indexed(masm, vs1, FullReg, Word, coeffs, tmpAddr, 256, OFFSETS_4);
        vsLdpqPost(masm, vs2.front(), zetas);
        kyberSubAddMontmul32(masm, vs1.even(), vs1.odd(), vs2.front(), vs2.back(), vtmp, vq);
        vsSt2Indexed(masm, vs1, FullReg, Word, coeffs, tmpAddr, 256, OFFSETS_4);
        vsLd2Indexed(masm, vs1, FullReg, Word, coeffs, tmpAddr, 384, OFFSETS_4);
        vsLdpqPost(masm, vs2.front(), zetas);
        kyberSubAddMontmul32(masm, vs1.even(), vs1.odd(), vs2.front(), vs2.back(), vtmp, vq);
        vsSt2Indexed(masm, vs1, FullReg, Word, coeffs, tmpAddr, 384, OFFSETS_4);

        // level 1
        // At level 1 related coefficients occur in discrete blocks of size 8 so
        // need to be loaded interleaved using an ld2 operation with arrangement 2D.

        vsLd2Indexed(masm, vs1, FullReg, DoubleWord, coeffs, tmpAddr, 0, OFFSETS_4);
        vsLdpqPost(masm, vs2.front(), zetas);
        kyberSubAddMontmul32(masm, vs1.even(), vs1.odd(), vs2.front(), vs2.back(), vtmp, vq);
        vsSt2Indexed(masm, vs1, FullReg, DoubleWord, coeffs, tmpAddr, 0, OFFSETS_4);
        vsLd2Indexed(masm, vs1, FullReg, DoubleWord, coeffs, tmpAddr, 128, OFFSETS_4);
        vsLdpqPost(masm, vs2.front(), zetas);
        kyberSubAddMontmul32(masm, vs1.even(), vs1.odd(), vs2.front(), vs2.back(), vtmp, vq);
        vsSt2Indexed(masm, vs1, FullReg, DoubleWord, coeffs, tmpAddr, 128, OFFSETS_4);
        vsLd2Indexed(masm, vs1, FullReg, DoubleWord, coeffs, tmpAddr, 256, OFFSETS_4);
        vsLdpqPost(masm, vs2.front(), zetas);
        kyberSubAddMontmul32(masm, vs1.even(), vs1.odd(), vs2.front(), vs2.back(), vtmp, vq);
        vsSt2Indexed(masm, vs1, FullReg, DoubleWord, coeffs, tmpAddr, 256, OFFSETS_4);
        vsLd2Indexed(masm, vs1, FullReg, DoubleWord, coeffs, tmpAddr, 384, OFFSETS_4);
        vsLdpqPost(masm, vs2.front(), zetas);
        kyberSubAddMontmul32(masm, vs1.even(), vs1.odd(), vs2.front(), vs2.back(), vtmp, vq);
        vsSt2Indexed(masm, vs1, FullReg, DoubleWord, coeffs, tmpAddr, 384, OFFSETS_4);

        // level 2
        // At level 2 coefficients occur in 8 discrete blocks of size 16
        // so they are loaded using employing an ldr at 8 distinct offsets.

        vsLdrIndexed(masm, vs1, coeffs, 0, OFFSETS_3);
        vsLdrIndexed(masm, vs2, coeffs, 16, OFFSETS_3);
        vsAddv(masm, vs3, vs1, vs2); // n.b. trashes vq
        vsSubv(masm, vs1, vs1, vs2);
        vsStrIndexed(masm, vs3, coeffs, 0, OFFSETS_3);
        vsLdpqPost(masm, vs2, zetas);
        vsLdpq(masm, vq, kyberConsts);
        kyberMontmul64(masm, vs2, vs1, vs2, vtmp, vq);
        vsStrIndexed(masm, vs2, coeffs, 16, OFFSETS_3);
        vsLdrIndexed(masm, vs1, coeffs, 256, OFFSETS_3);
        vsLdrIndexed(masm, vs2, coeffs, 256 + 16, OFFSETS_3);
        vsAddv(masm, vs3, vs1, vs2); // n.b. trashes vq
        vsSubv(masm, vs1, vs1, vs2);
        vsStrIndexed(masm, vs3, coeffs, 256, OFFSETS_3);
        vsLdpqPost(masm, vs2, zetas);
        vsLdpq(masm, vq, kyberConsts);
        kyberMontmul64(masm, vs2, vs1, vs2, vtmp, vq);
        vsStrIndexed(masm, vs2, coeffs, 256 + 16, OFFSETS_3);

        // Barrett reduction at indexes where overflow may happen

        // load q and the multiplier for the Barrett reduction
        masm.add(64, tmpAddr, kyberConsts, 16);
        vsLdpq(masm, vq, tmpAddr);
        VectorRegisterSeq vq1 = VectorRegisterSeq.create(vq.get(0), 0, 8); // 2 constant 8 sequences
        VectorRegisterSeq vq2 = VectorRegisterSeq.create(vq.get(1), 0, 8); // for above two kyber constants
        VectorRegisterSeq vq3 = VectorRegisterSeq.create(v29, 0, 8);       // 3rd sequence for const montmul
        vsLdrIndexed(masm, vs1, coeffs, 0, OFFSETS_3);
        vsSqdmulh(masm, vs2, vs1, vq2);
        vsSshr(masm, vs2, vs2, 11);
        vsMlsv(masm, vs1, vs2, vq1);
        vsStrIndexed(masm, vs1, coeffs, 0, OFFSETS_3);
        vsLdrIndexed(masm, vs1, coeffs, 256, OFFSETS_3);
        vsSqdmulh(masm, vs2, vs1, vq2);
        vsSshr(masm, vs2, vs2, 11);
        vsMlsv(masm, vs1, vs2, vq1);
        vsStrIndexed(masm, vs1, coeffs, 256, OFFSETS_3);

        // level 3
        // From level 3 upwards coefficients occur in discrete blocks whose size is
        // some multiple of 32 so can be loaded using ldpq and suitable indexes.

        vsLdpqIndexed(masm, vs1, coeffs, 0, OFFSETS_2);
        vsLdpqIndexed(masm, vs2, coeffs, 32, OFFSETS_2);
        vsAddv(masm, vs3, vs1, vs2); // n.b. trashes vq
        vsSubv(masm, vs1, vs1, vs2);
        vsStpqIndexed(masm, vs3, coeffs, 0, OFFSETS_2);
        vsLdpqPost(masm, vs2, zetas);
        vsLdpq(masm, vq, kyberConsts);
        kyberMontmul64(masm, vs2, vs1, vs2, vtmp, vq);
        vsStpqIndexed(masm, vs2, coeffs, 32, OFFSETS_2);
        vsLdpqIndexed(masm, vs1, coeffs, 256, OFFSETS_2);
        vsLdpqIndexed(masm, vs2, coeffs, 256 + 32, OFFSETS_2);
        vsAddv(masm, vs3, vs1, vs2); // n.b. trashes vq
        vsSubv(masm, vs1, vs1, vs2);
        vsStpqIndexed(masm, vs3, coeffs, 256, OFFSETS_2);
        vsLdpqPost(masm, vs2, zetas);
        vsLdpq(masm, vq, kyberConsts);
        kyberMontmul64(masm, vs2, vs1, vs2, vtmp, vq);
        vsStpqIndexed(masm, vs2, coeffs, 256 + 32, OFFSETS_2);

        // level 4

        vsLdpqIndexed(masm, vs1, coeffs, 0, OFFSETS_1);
        vsLdpqIndexed(masm, vs2, coeffs, 64, OFFSETS_1);
        vsAddv(masm, vs3, vs1, vs2); // n.b. trashes vq
        vsSubv(masm, vs1, vs1, vs2);
        vsStpqIndexed(masm, vs3, coeffs, 0, OFFSETS_1);
        vsLdpqPost(masm, vs2, zetas);
        vsLdpq(masm, vq, kyberConsts);
        kyberMontmul64(masm, vs2, vs1, vs2, vtmp, vq);
        vsStpqIndexed(masm, vs2, coeffs, 64, OFFSETS_1);
        vsLdpqIndexed(masm, vs1, coeffs, 256, OFFSETS_1);
        vsLdpqIndexed(masm, vs2, coeffs, 256 + 64, OFFSETS_1);
        vsAddv(masm, vs3, vs1, vs2); // n.b. trashes vq
        vsSubv(masm, vs1, vs1, vs2);
        vsStpqIndexed(masm, vs3, coeffs, 256, OFFSETS_1);
        vsLdpqPost(masm, vs2, zetas);
        vsLdpq(masm, vq, kyberConsts);
        kyberMontmul64(masm, vs2, vs1, vs2, vtmp, vq);
        vsStpqIndexed(masm, vs2, coeffs, 256 + 64, OFFSETS_1);

        // level 5

        masm.add(64, tmpAddr, coeffs, 0);
        vsLdpqPost(masm, vs1, tmpAddr);
        masm.add(64, tmpAddr, coeffs, 128);
        vsLdpqPost(masm, vs2, tmpAddr);
        vsAddv(masm, vs3, vs1, vs2); // n.b. trashes vq
        vsSubv(masm, vs1, vs1, vs2);
        masm.add(64, tmpAddr, coeffs, 0);
        vsStpqPost(masm, vs3, tmpAddr);
        vsLdpqPost(masm, vs2, zetas);
        vsLdpq(masm, vq, kyberConsts);
        kyberMontmul64(masm, vs2, vs1, vs2, vtmp, vq);
        masm.add(64, tmpAddr, coeffs, 128);
        vsStpqPost(masm, vs2, tmpAddr);
        vsLdpqPost(masm, vs1, tmpAddr);
        masm.add(64, tmpAddr, coeffs, 384);
        vsLdpqPost(masm, vs2, tmpAddr);
        vsAddv(masm, vs3, vs1, vs2); // n.b. trashes vq
        vsSubv(masm, vs1, vs1, vs2);
        masm.add(64, tmpAddr, coeffs, 256);
        vsStpqPost(masm, vs3, tmpAddr);
        vsLdpqPost(masm, vs2, zetas);
        vsLdpq(masm, vq, kyberConsts);
        kyberMontmul64(masm, vs2, vs1, vs2, vtmp, vq);
        masm.add(64, tmpAddr, coeffs, 384);
        vsStpqPost(masm, vs2, tmpAddr);

        // Barrett reduction at indexes where overflow may happen

        // load q and the multiplier for the Barrett reduction
        masm.add(64, tmpAddr, kyberConsts, 16);
        vsLdpq(masm, vq, tmpAddr);
        vsLdpqIndexed(masm, vs1.front(), coeffs, 0, OFFSETS_0);
        vsSqdmulh(masm, vs2, vs1, vq2);
        vsSshr(masm, vs2, vs2, 11);
        vsMlsv(masm, vs1, vs2, vq1);
        vsStpqIndexed(masm, vs1.front(), coeffs, 0, OFFSETS_0);

        // level 6

        masm.add(64, tmpAddr, coeffs, 0);
        vsLdpqPost(masm, vs1, tmpAddr);
        masm.add(64, tmpAddr, coeffs, 256);
        vsLdpqPost(masm, vs2, tmpAddr);
        vsAddv(masm, vs3, vs1, vs2); // n.b. trashes vq
        vsSubv(masm, vs1, vs1, vs2);
        masm.add(64, tmpAddr, coeffs, 0);
        vsStpqPost(masm, vs3, tmpAddr);
        vsLdpqPost(masm, vs2, zetas);
        vsLdpq(masm, vq, kyberConsts);
        kyberMontmul64(masm, vs2, vs1, vs2, vtmp, vq);
        masm.add(64, tmpAddr, coeffs, 256);
        vsStpqPost(masm, vs2, tmpAddr);
        masm.add(64, tmpAddr, coeffs, 128);
        vsLdpqPost(masm, vs1, tmpAddr);
        masm.add(64, tmpAddr, coeffs, 384);
        vsLdpqPost(masm, vs2, tmpAddr);
        vsAddv(masm, vs3, vs1, vs2); // n.b. trashes vq
        vsSubv(masm, vs1, vs1, vs2);
        masm.add(64, tmpAddr, coeffs, 128);
        vsStpqPost(masm, vs3, tmpAddr);
        vsLdpqPost(masm, vs2, zetas);
        vsLdpq(masm, vq, kyberConsts);
        kyberMontmul64(masm, vs2, vs1, vs2, vtmp, vq);
        masm.add(64, tmpAddr, coeffs, 384);
        vsStpqPost(masm, vs2, tmpAddr);

        // multiply by 2^-n

        // load toMont(2^-n mod q)
        masm.add(64, tmpAddr, kyberConsts, 48);
        masm.fldr(128, v29, createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, tmpAddr, 0));
        vsLdpq(masm, vq, kyberConsts);
        masm.add(64, tmpAddr, coeffs, 0);
        vsLdpqPost(masm, vs1, tmpAddr);
        kyberMontmul64(masm, vs2, vs1, vq3, vtmp, vq);
        masm.add(64, tmpAddr, coeffs, 0);
        vsStpqPost(masm, vs2, tmpAddr);
        // now tmpAddr contains coeffs + 128 because store64shorts adjusted it so
        vsLdpqPost(masm, vs1, tmpAddr);
        kyberMontmul64(masm, vs2, vs1, vq3, vtmp, vq);
        masm.add(64, tmpAddr, coeffs, 128);
        vsStpqPost(masm, vs2, tmpAddr);
        // now tmpAddr contains coeffs + 256
        vsLdpqPost(masm, vs1, tmpAddr);
        kyberMontmul64(masm, vs2, vs1, vq3, vtmp, vq);
        masm.add(64, tmpAddr, coeffs, 256);
        vsStpqPost(masm, vs2, tmpAddr);
        // now tmpAddr contains coeffs + 384
        vsLdpqPost(masm, vs1, tmpAddr);
        kyberMontmul64(masm, vs2, vs1, vq3, vtmp, vq);
        masm.add(64, tmpAddr, coeffs, 384);
        vsStpqPost(masm, vs2, tmpAddr);

        masm.mov(32, result, zr);
    }
}
