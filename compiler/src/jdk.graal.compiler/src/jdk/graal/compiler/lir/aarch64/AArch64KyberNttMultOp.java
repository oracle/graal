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

import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.ST2_MULTIPLE_2R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize.HalfWord;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createImmediateAddress;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createStructureImmediatePostIndexAddress;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.kyberMontmul16;
import static jdk.graal.compiler.lir.aarch64.AArch64LIRHelper.guaranteeFixedRegister;
import static jdk.graal.compiler.lir.aarch64.AArch64LIRHelper.loadExternalAddress;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.KYBER_CONSTS_DATA;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsAddv;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsLd2Post;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsLdpq;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsLdpqPost;
import static jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.vsMontmul4;
import static jdk.vm.ci.aarch64.AArch64.r0;
import static jdk.vm.ci.aarch64.AArch64.r1;
import static jdk.vm.ci.aarch64.AArch64.r11;
import static jdk.vm.ci.aarch64.AArch64.r20;
import static jdk.vm.ci.aarch64.AArch64.r2;
import static jdk.vm.ci.aarch64.AArch64.r3;
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

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.aarch64.AArch64KyberSupport.VectorRegisterSeq;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fe611a2a3386d097f636c15bd4d396a82dc695e/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L5728-L5836",
          sha1 = "3a2c09e79ecb1d2b93d8948bd95fdf574bc5f079")
// @formatter:on
public final class AArch64KyberNttMultOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64KyberNttMultOp> TYPE = LIRInstructionClass.create(AArch64KyberNttMultOp.class);

    @Def private Value resultValue;
    @Use private Value resultPointerValue;
    @Use private Value nttaValue;
    @Use private Value nttbValue;
    @Use private Value zetasValue;
    @Temp private Value[] temps;

    public AArch64KyberNttMultOp(AllocatableValue resultValue, AllocatableValue resultPointerValue, AllocatableValue nttaValue, AllocatableValue nttbValue, AllocatableValue zetasValue) {
        super(TYPE);
        guaranteeFixedRegister(resultValue, r0, "resultValue");
        guaranteeFixedRegister(resultPointerValue, r0, "resultPointerValue");
        guaranteeFixedRegister(nttaValue, r1, "nttaValue");
        guaranteeFixedRegister(nttbValue, r2, "nttbValue");
        guaranteeFixedRegister(zetasValue, r3, "zetasValue");
        this.resultValue = resultValue;
        this.resultPointerValue = resultPointerValue;
        this.nttaValue = nttaValue;
        this.nttbValue = nttbValue;
        this.zetasValue = zetasValue;
        this.temps = new Value[]{
                        r0.asValue(),
                        r1.asValue(),
                        r2.asValue(),
                        r3.asValue(),
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
        Register result = asRegister(resultPointerValue);
        Register ntta = asRegister(nttaValue);
        Register nttb = asRegister(nttbValue);
        Register zetas = asRegister(zetasValue);
        Register kyberConsts = r20;
        Register limit = r11;

        // Kyber multiply polynomials in the NTT domain.
        // Implements
        // static int implKyberNttMult(
        //              short[] result, short[] ntta, short[] nttb, short[] zetas) {}
        //
        // result (short[256]) = c_rarg0
        // ntta (short[256]) = c_rarg1
        // nttb (short[256]) = c_rarg2
        // zetas (short[128]) = c_rarg3
        VectorRegisterSeq vs1 = VectorRegisterSeq.create(0, 4);   // 4 sets of 8x8H inputs/outputs/tmps
        VectorRegisterSeq vs2 = VectorRegisterSeq.create(4, 4);
        VectorRegisterSeq vs3 = VectorRegisterSeq.create(16, 4);
        VectorRegisterSeq vs4 = VectorRegisterSeq.create(20, 4);
        VectorRegisterSeq vq = VectorRegisterSeq.create(30, 2);   // pair of constants for montmul: q, qinv
        VectorRegisterSeq vz = VectorRegisterSeq.create(28, 2);   // pair of zetas
        VectorRegisterSeq vc = VectorRegisterSeq.create(v27, 0, 4); // constant sequence for montmul: montRSquareModQ

        loadExternalAddress(crb, masm, kyberConsts, KYBER_CONSTS_DATA);
        Label loop = new Label();

        masm.add(64, limit, result, 512);

        // load q and qinv
        vsLdpq(masm, vq, kyberConsts);

        // load R^2 mod q (to convert back from Montgomery representation)
        masm.add(64, kyberConsts, kyberConsts, 64);
        masm.fldr(128, v27, createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, kyberConsts, 0));

        masm.bind(loop);
        // load 16 zetas
        vsLdpqPost(masm, vz, zetas);
        vsLd2Post(masm, vs1.front(), FullReg, HalfWord, ntta);
        vsLd2Post(masm, vs1.back(), FullReg, HalfWord, nttb);
        vsLd2Post(masm, vs4.front(), FullReg, HalfWord, ntta);
        vsLd2Post(masm, vs4.back(), FullReg, HalfWord, nttb);

        kyberMontmul16(masm, vs3.front(), vs1.front(), vs1.back(), vs2.front(), vq);
        kyberMontmul16(masm, vs3.back(), vs1.front(), vs1.back().reverse(), vs2.back(), vq);
        kyberMontmul16(masm, vs1.front(), vs4.front(), vs4.back(), vs2.front(), vq);
        kyberMontmul16(masm, vs1.back(), vs4.front(), vs4.back().reverse(), vs2.back(), vq);

        int delta = vs1.get(1).encoding - vs3.get(1).encoding;
        VectorRegisterSeq vs5 = VectorRegisterSeq.create(vs3.get(1), delta, 2);
        kyberMontmul16(masm, vs5, vz, vs5, vs2.front(), vq);
        vsAddv(masm, vs3.front(), vs3.even(), vs3.odd());
        vsAddv(masm, vs3.back(), vs1.even(), vs1.odd());
        vsMontmul4(masm, vs1, vs3, vc, vs2, vq);
        for (int i = 0; i < vs1.length(); i += 2) {
            masm.neon.st2MultipleVV(FullReg, HalfWord, vs1.get(i), vs1.get(i + 1),
                            createStructureImmediatePostIndexAddress(ST2_MULTIPLE_2R, FullReg, HalfWord, result, 32));
        }

        masm.cmp(64, result, limit);
        masm.branchConditionally(ConditionFlag.NE, loop);

        masm.mov(32, resultRegister, zr);
    }
}
