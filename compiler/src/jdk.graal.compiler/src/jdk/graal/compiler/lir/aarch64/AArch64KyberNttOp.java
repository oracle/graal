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
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.OFFSETS_1;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.OFFSETS_2;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.OFFSETS_3;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.OFFSETS_4;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.kyberMontmul32SubAdd;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.kyberMontmul64;
import static jdk.graal.compiler.lir.aarch64.AArch64LIRHelper.guaranteeFixedRegister;
import static jdk.graal.compiler.lir.aarch64.AArch64LIRHelper.loadExternalAddress;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.KYBER_CONSTS_DATA;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsAddv;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsLd2Indexed;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsLdpq;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsLdpqIndexed;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsLdpqPost;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsLdrIndexed;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsSt2Indexed;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsStpqIndexed;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsStpqPost;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsStrIndexed;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsSubv;
import static jdk.vm.ci.aarch64.AArch64.r0;
import static jdk.vm.ci.aarch64.AArch64.r1;
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
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fe611a2a3386d097f636c15bd4d396a82dc695e/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L5203-L5433",
          sha1 = "6d6750525ed4f484ddc5a1c119724add3018326c")
// @formatter:on
public final class AArch64KyberNttOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64KyberNttOp> TYPE = LIRInstructionClass.create(AArch64KyberNttOp.class);

    @Def private Value resultValue;
    @Use private Value polyValue;
    @Use private Value zetasValue;
    @Temp private Value[] temps;

    public AArch64KyberNttOp(AllocatableValue resultValue, AllocatableValue polyValue, AllocatableValue zetasValue) {
        super(TYPE);
        guaranteeFixedRegister(resultValue, r0, "resultValue");
        guaranteeFixedRegister(polyValue, r0, "polyValue");
        guaranteeFixedRegister(zetasValue, r1, "zetasValue");
        this.resultValue = resultValue;
        this.polyValue = polyValue;
        this.zetasValue = zetasValue;
        this.temps = new Value[]{
                        r1.asValue(),
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

        // Kyber NTT function.
        // Implements
        // static int implKyberNtt(short[] poly, short[] ntt_zetas) {}
        //
        // coeffs (short[256]) = c_rarg0
        // ntt_zetas (short[256]) = c_rarg1
        VectorRegisterSeq vs1 = VectorRegisterSeq.create(0, 8);
        VectorRegisterSeq vs2 = VectorRegisterSeq.create(16, 8);
        VectorRegisterSeq vs3 = VectorRegisterSeq.create(24, 8);
        VectorRegisterSeq vtmp = vs3.front();      // n.b. tmp registers overlap vs3
        VectorRegisterSeq vq = VectorRegisterSeq.create(30, 2);       // n.b. constants overlap vs3

        loadExternalAddress(crb, masm, kyberConsts, KYBER_CONSTS_DATA);
        // load the montmul constants
        vsLdpq(masm, vq, kyberConsts);

        // Each level corresponds to an iteration of the outermost loop of the
        // Java method seilerNTT(int[] coeffs). There are some differences
        // from what is done in the seilerNTT() method, though:
        // 1. The computation is using 16-bit signed values, we do not convert them
        // to ints here.
        // 2. The zetas are delivered in a bigger array, 128 zetas are stored in
        // this array for each level, it is easier that way to fill up the vector
        // registers.
        // 3. In the seilerNTT() method we use R = 2^20 for the Montgomery
        // multiplications (this is because that way there should not be any
        // overflow during the inverse NTT computation), here we usr R = 2^16 so
        // that we can use the 16-bit arithmetic in the vector unit.
        //
        // On each level, we fill up the vector registers in such a way that the
        // array elements that need to be multiplied by the zetas go into one
        // set of vector registers while the corresponding ones that don't need to
        // be multiplied, go into another set.
        // We can do 32 Montgomery multiplications in parallel, using 12 vector
        // registers interleaving the steps of 4 identical computations,
        // each done on 8 16-bit values per register.

        // At levels 0-3 the coefficients multiplied by or added/subtracted
        // to the zetas occur in discrete blocks whose size is some multiple
        // of 32.

        // level 0
        masm.add(64, tmpAddr, coeffs, 256);
        vsLdpqPost(masm, vs1, tmpAddr);
        vsLdpqPost(masm, vs2, zetas);
        kyberMontmul64(masm, vs2, vs1, vs2, vtmp, vq);
        masm.add(64, tmpAddr, coeffs, 0);
        vsLdpqPost(masm, vs1, tmpAddr);
        vsSubv(masm, vs3, vs1, vs2); // n.b. trashes vq
        vsAddv(masm, vs1, vs1, vs2);
        masm.add(64, tmpAddr, coeffs, 0);
        vsStpqPost(masm, vs1, tmpAddr);
        masm.add(64, tmpAddr, coeffs, 256);
        vsStpqPost(masm, vs3, tmpAddr);
        // restore montmul constants
        vsLdpq(masm, vq, kyberConsts);
        vsLdpqPost(masm, vs1, tmpAddr);
        vsLdpqPost(masm, vs2, zetas);
        kyberMontmul64(masm, vs2, vs1, vs2, vtmp, vq);
        masm.add(64, tmpAddr, coeffs, 128);
        vsLdpqPost(masm, vs1, tmpAddr);
        vsSubv(masm, vs3, vs1, vs2); // n.b. trashes vq
        vsAddv(masm, vs1, vs1, vs2);
        masm.add(64, tmpAddr, coeffs, 128);
        vsStpqPost(masm, vs1, tmpAddr);
        masm.add(64, tmpAddr, coeffs, 384);
        vsStpqPost(masm, vs3, tmpAddr);

        // level 1
        // restore montmul constants
        vsLdpq(masm, vq, kyberConsts);
        masm.add(64, tmpAddr, coeffs, 128);
        vsLdpqPost(masm, vs1, tmpAddr);
        vsLdpqPost(masm, vs2, zetas);
        kyberMontmul64(masm, vs2, vs1, vs2, vtmp, vq);
        masm.add(64, tmpAddr, coeffs, 0);
        vsLdpqPost(masm, vs1, tmpAddr);
        vsSubv(masm, vs3, vs1, vs2); // n.b. trashes vq
        vsAddv(masm, vs1, vs1, vs2);
        masm.add(64, tmpAddr, coeffs, 0);
        vsStpqPost(masm, vs1, tmpAddr);
        vsStpqPost(masm, vs3, tmpAddr);
        vsLdpq(masm, vq, kyberConsts);
        masm.add(64, tmpAddr, coeffs, 384);
        vsLdpqPost(masm, vs1, tmpAddr);
        vsLdpqPost(masm, vs2, zetas);
        kyberMontmul64(masm, vs2, vs1, vs2, vtmp, vq);
        masm.add(64, tmpAddr, coeffs, 256);
        vsLdpqPost(masm, vs1, tmpAddr);
        vsSubv(masm, vs3, vs1, vs2); // n.b. trashes vq
        vsAddv(masm, vs1, vs1, vs2);
        masm.add(64, tmpAddr, coeffs, 256);
        vsStpqPost(masm, vs1, tmpAddr);
        vsStpqPost(masm, vs3, tmpAddr);

        // level 2
        vsLdpq(masm, vq, kyberConsts);
        vsLdpqIndexed(masm, vs1, coeffs, 64, OFFSETS_1);
        vsLdpqPost(masm, vs2, zetas);
        kyberMontmul64(masm, vs2, vs1, vs2, vtmp, vq);
        vsLdpqIndexed(masm, vs1, coeffs, 0, OFFSETS_1);
        // kyber_subv_addv64();
        vsSubv(masm, vs3, vs1, vs2); // n.b. trashes vq
        vsAddv(masm, vs1, vs1, vs2);
        masm.add(64, tmpAddr, coeffs, 0);
        vsStpqPost(masm, vs1.front(), tmpAddr);
        vsStpqPost(masm, vs3.front(), tmpAddr);
        vsStpqPost(masm, vs1.back(), tmpAddr);
        vsStpqPost(masm, vs3.back(), tmpAddr);
        vsLdpq(masm, vq, kyberConsts);
        vsLdpqIndexed(masm, vs1, tmpAddr, 64, OFFSETS_1);
        vsLdpqPost(masm, vs2, zetas);
        kyberMontmul64(masm, vs2, vs1, vs2, vtmp, vq);
        vsLdpqIndexed(masm, vs1, coeffs, 256, OFFSETS_1);
        // kyber_subv_addv64();
        vsSubv(masm, vs3, vs1, vs2); // n.b. trashes vq
        vsAddv(masm, vs1, vs1, vs2);
        masm.add(64, tmpAddr, coeffs, 256);
        vsStpqPost(masm, vs1.front(), tmpAddr);
        vsStpqPost(masm, vs3.front(), tmpAddr);
        vsStpqPost(masm, vs1.back(), tmpAddr);
        vsStpqPost(masm, vs3.back(), tmpAddr);

        // level 3
        vsLdpq(masm, vq, kyberConsts);
        vsLdpqIndexed(masm, vs1, coeffs, 32, OFFSETS_2);
        vsLdpqPost(masm, vs2, zetas);
        kyberMontmul64(masm, vs2, vs1, vs2, vtmp, vq);
        vsLdpqIndexed(masm, vs1, coeffs, 0, OFFSETS_2);
        vsSubv(masm, vs3, vs1, vs2); // n.b. trashes vq
        vsAddv(masm, vs1, vs1, vs2);
        vsStpqIndexed(masm, vs1, coeffs, 0, OFFSETS_2);
        vsStpqIndexed(masm, vs3, coeffs, 32, OFFSETS_2);
        vsLdpq(masm, vq, kyberConsts);
        vsLdpqIndexed(masm, vs1, coeffs, 256 + 32, OFFSETS_2);
        vsLdpqPost(masm, vs2, zetas);
        kyberMontmul64(masm, vs2, vs1, vs2, vtmp, vq);
        vsLdpqIndexed(masm, vs1, coeffs, 256, OFFSETS_2);
        vsSubv(masm, vs3, vs1, vs2); // n.b. trashes vq
        vsAddv(masm, vs1, vs1, vs2);
        vsStpqIndexed(masm, vs1, coeffs, 256, OFFSETS_2);
        vsStpqIndexed(masm, vs3, coeffs, 256 + 32, OFFSETS_2);

        // level 4
        // At level 4 coefficients occur in 8 discrete blocks of size 16
        // so they are loaded using employing an ldr at 8 distinct offsets.

        vsLdpq(masm, vq, kyberConsts);
        vsLdrIndexed(masm, vs1, coeffs, 16, OFFSETS_3);
        vsLdpqPost(masm, vs2, zetas);
        kyberMontmul64(masm, vs2, vs1, vs2, vtmp, vq);
        vsLdrIndexed(masm, vs1, coeffs, 0, OFFSETS_3);
        vsSubv(masm, vs3, vs1, vs2); // n.b. trashes vq
        vsAddv(masm, vs1, vs1, vs2);
        vsStrIndexed(masm, vs1, coeffs, 0, OFFSETS_3);
        vsStrIndexed(masm, vs3, coeffs, 16, OFFSETS_3);
        vsLdpq(masm, vq, kyberConsts);
        vsLdrIndexed(masm, vs1, coeffs, 256 + 16, OFFSETS_3);
        vsLdpqPost(masm, vs2, zetas);
        kyberMontmul64(masm, vs2, vs1, vs2, vtmp, vq);
        vsLdrIndexed(masm, vs1, coeffs, 256, OFFSETS_3);
        vsSubv(masm, vs3, vs1, vs2); // n.b. trashes vq
        vsAddv(masm, vs1, vs1, vs2);
        vsStrIndexed(masm, vs1, coeffs, 256, OFFSETS_3);
        vsStrIndexed(masm, vs3, coeffs, 256 + 16, OFFSETS_3);

        // level 5
        // At level 5 related coefficients occur in discrete blocks of size 8 so
        // need to be loaded interleaved using an ld2 operation with arrangement 2D.

        vsLdpq(masm, vq, kyberConsts);
        vsLd2Indexed(masm, vs1, FullReg, DoubleWord, coeffs, tmpAddr, 0, OFFSETS_4);
        vsLdpqPost(masm, vs2.front(), zetas);
        kyberMontmul32SubAdd(masm, vs1.even(), vs1.odd(), vs2.front(), vtmp, vq);
        vsSt2Indexed(masm, vs1, FullReg, DoubleWord, coeffs, tmpAddr, 0, OFFSETS_4);
        vsLd2Indexed(masm, vs1, FullReg, DoubleWord, coeffs, tmpAddr, 128, OFFSETS_4);
        vsLdpqPost(masm, vs2.front(), zetas);
        kyberMontmul32SubAdd(masm, vs1.even(), vs1.odd(), vs2.front(), vtmp, vq);
        vsSt2Indexed(masm, vs1, FullReg, DoubleWord, coeffs, tmpAddr, 128, OFFSETS_4);
        vsLd2Indexed(masm, vs1, FullReg, DoubleWord, coeffs, tmpAddr, 256, OFFSETS_4);
        vsLdpqPost(masm, vs2.front(), zetas);
        kyberMontmul32SubAdd(masm, vs1.even(), vs1.odd(), vs2.front(), vtmp, vq);
        vsSt2Indexed(masm, vs1, FullReg, DoubleWord, coeffs, tmpAddr, 256, OFFSETS_4);
        vsLd2Indexed(masm, vs1, FullReg, DoubleWord, coeffs, tmpAddr, 384, OFFSETS_4);
        vsLdpqPost(masm, vs2.front(), zetas);
        kyberMontmul32SubAdd(masm, vs1.even(), vs1.odd(), vs2.front(), vtmp, vq);
        vsSt2Indexed(masm, vs1, FullReg, DoubleWord, coeffs, tmpAddr, 384, OFFSETS_4);

        // level 6
        // At level 6 related coefficients occur in discrete blocks of size 4 so
        // need to be loaded interleaved using an ld2 operation with arrangement 4S.

        vsLd2Indexed(masm, vs1, FullReg, Word, coeffs, tmpAddr, 0, OFFSETS_4);
        vsLdpqPost(masm, vs2.front(), zetas);
        kyberMontmul32SubAdd(masm, vs1.even(), vs1.odd(), vs2.front(), vtmp, vq);
        vsSt2Indexed(masm, vs1, FullReg, Word, coeffs, tmpAddr, 0, OFFSETS_4);
        vsLd2Indexed(masm, vs1, FullReg, Word, coeffs, tmpAddr, 128, OFFSETS_4);
        vsLdpqPost(masm, vs2.front(), zetas);
        kyberMontmul32SubAdd(masm, vs1.even(), vs1.odd(), vs2.front(), vtmp, vq);
        vsSt2Indexed(masm, vs1, FullReg, Word, coeffs, tmpAddr, 128, OFFSETS_4);
        vsLd2Indexed(masm, vs1, FullReg, Word, coeffs, tmpAddr, 256, OFFSETS_4);
        vsLdpqPost(masm, vs2.front(), zetas);
        kyberMontmul32SubAdd(masm, vs1.even(), vs1.odd(), vs2.front(), vtmp, vq);
        vsSt2Indexed(masm, vs1, FullReg, Word, coeffs, tmpAddr, 256, OFFSETS_4);
        vsLd2Indexed(masm, vs1, FullReg, Word, coeffs, tmpAddr, 384, OFFSETS_4);
        vsLdpqPost(masm, vs2.front(), zetas);
        kyberMontmul32SubAdd(masm, vs1.even(), vs1.odd(), vs2.front(), vtmp, vq);
        vsSt2Indexed(masm, vs1, FullReg, Word, coeffs, tmpAddr, 384, OFFSETS_4);

        masm.mov(32, result, zr);
    }
}
