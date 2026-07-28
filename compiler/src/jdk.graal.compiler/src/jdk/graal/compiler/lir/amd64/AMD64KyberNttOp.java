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

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPBROADCASTQ;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.guaranteeFixedRegister;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.NTT_PERMS;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.Q;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.Q_INV_MOD_R;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.XMM0_3;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.XMM0145;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.XMM0246;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.XMM12_15;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.XMM1357;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.XMM16_19;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.XMM20_23;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.XMM2367;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.XMM24_27;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.XMM4_7;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.XMM8_11;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.load4regs;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.montmul;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.permute;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.store4regs;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.subAdd;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rsi;
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
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/d8d51f3327b133af4f21cd6dd248324e4fc2a482/src/hotspot/cpu/x86/stubGenerator_x86_64_kyber.cpp#L368-L458",
          sha1 = "a6ea3352abcd3e9399bb0a86263e7a4559fdc51c")
// @formatter:on
public final class AMD64KyberNttOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64KyberNttOp> TYPE = LIRInstructionClass.create(AMD64KyberNttOp.class);

    @Def({REG}) private Value resultValue;
    @Use({REG}) private Value polyValue;
    @Use({REG}) private Value zetasValue;
    @Temp({REG}) private Value[] temps;

    public AMD64KyberNttOp(AllocatableValue resultValue, AllocatableValue polyValue, AllocatableValue zetasValue) {
        super(TYPE);
        guaranteeFixedRegister(resultValue, rax, "resultValue");
        guaranteeFixedRegister(polyValue, rdi, "polyValue");
        guaranteeFixedRegister(zetasValue, rsi, "zetasValue");
        this.resultValue = resultValue;
        this.polyValue = polyValue;
        this.zetasValue = zetasValue;
        this.temps = new Value[]{
                        xmm0.asValue(),
                        xmm1.asValue(),
                        xmm2.asValue(),
                        xmm3.asValue(),
                        xmm4.asValue(),
                        xmm5.asValue(),
                        xmm6.asValue(),
                        xmm7.asValue(),
                        xmm8.asValue(),
                        xmm9.asValue(),
                        xmm10.asValue(),
                        xmm11.asValue(),
                        xmm12.asValue(),
                        xmm13.asValue(),
                        xmm14.asValue(),
                        xmm15.asValue(),
                        xmm16.asValue(),
                        xmm17.asValue(),
                        xmm18.asValue(),
                        xmm19.asValue(),
                        xmm20.asValue(),
                        xmm21.asValue(),
                        xmm22.asValue(),
                        xmm23.asValue(),
                        xmm24.asValue(),
                        xmm25.asValue(),
                        xmm26.asValue(),
                        xmm27.asValue(),
                        xmm28.asValue(),
                        xmm29.asValue(),
                        xmm30.asValue(),
                        xmm31.asValue(),
        };
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        GraalError.guarantee(resultValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid %s kind: %s", "resultValue", resultValue);
        Register result = asRegister(resultValue);
        GraalError.guarantee(polyValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid %s kind: %s", "polyValue", polyValue);
        Register coeffs = asRegister(polyValue);
        GraalError.guarantee(zetasValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid %s kind: %s", "zetasValue", zetasValue);
        Register zetas = asRegister(zetasValue);

        load4regs(masm, XMM4_7, coeffs, 256);
        load4regs(masm, XMM20_23, zetas, 0);
        EVPBROADCASTQ.emit(masm, AVXSize.ZMM, xmm30, recordExternalAddress(crb, Q));
        EVPBROADCASTQ.emit(masm, AVXSize.ZMM, xmm31, recordExternalAddress(crb, Q_INV_MOD_R));
        load4regs(masm, XMM0_3, coeffs, 0);

        montmul(masm, XMM8_11, XMM4_7, XMM20_23, XMM8_11, XMM4_7);
        load4regs(masm, XMM20_23, zetas, 256);
        subAdd(masm, XMM4_7, XMM0_3, XMM0_3, XMM8_11);

        montmul(masm, XMM12_15, XMM2367, XMM20_23, XMM12_15, XMM8_11);
        load4regs(masm, XMM20_23, zetas, 512);
        subAdd(masm, XMM2367, XMM0145, XMM0145, XMM12_15);

        montmul(masm, XMM8_11, XMM1357, XMM20_23, XMM12_15, XMM8_11);
        masm.evmovdqu16(xmm12, recordExternalAddress(crb, NTT_PERMS[0]));
        masm.evmovdqu16(xmm16, recordExternalAddress(crb, NTT_PERMS[1]));
        load4regs(masm, XMM20_23, zetas, 768);
        subAdd(masm, XMM1357, XMM0246, XMM0246, XMM8_11);

        permute(masm, XMM12_15, XMM0246, XMM1357, xmm16);
        montmul(masm, XMM8_11, XMM12_15, XMM20_23, XMM16_19, XMM8_11);
        masm.evmovdqu16(xmm16, recordExternalAddress(crb, NTT_PERMS[2]));
        masm.evmovdqu16(xmm24, recordExternalAddress(crb, NTT_PERMS[3]));
        load4regs(masm, XMM20_23, zetas, 1024);
        subAdd(masm, XMM1357, XMM0246, XMM0246, XMM8_11);

        permute(masm, XMM16_19, XMM0246, XMM1357, xmm24);
        montmul(masm, XMM8_11, XMM0246, XMM20_23, XMM24_27, XMM8_11);
        masm.evmovdqu16(xmm1, recordExternalAddress(crb, NTT_PERMS[4]));
        masm.evmovdqu16(xmm24, recordExternalAddress(crb, NTT_PERMS[5]));
        load4regs(masm, XMM20_23, zetas, 1280);
        subAdd(masm, XMM12_15, XMM0246, XMM16_19, XMM8_11);

        permute(masm, XMM1357, XMM0246, XMM12_15, xmm24);
        montmul(masm, XMM16_19, XMM0246, XMM20_23, XMM16_19, XMM8_11);
        masm.evmovdqu16(xmm12, recordExternalAddress(crb, NTT_PERMS[6]));
        masm.evmovdqu16(xmm8, recordExternalAddress(crb, NTT_PERMS[7]));
        load4regs(masm, XMM20_23, zetas, 1536);
        subAdd(masm, XMM24_27, XMM0246, XMM1357, XMM16_19);

        permute(masm, XMM12_15, XMM0246, XMM24_27, xmm8);
        masm.evmovdqu16(xmm1, recordExternalAddress(crb, NTT_PERMS[8]));
        masm.evmovdqu16(xmm24, recordExternalAddress(crb, NTT_PERMS[9]));
        montmul(masm, XMM16_19, XMM0246, XMM20_23, XMM16_19, XMM8_11);
        subAdd(masm, XMM20_23, XMM0246, XMM12_15, XMM16_19);
        permute(masm, XMM1357, XMM0246, XMM20_23, xmm24);

        store4regs(masm, coeffs, 0, XMM0_3);
        store4regs(masm, coeffs, 256, XMM4_7);
        masm.xorl(result, result);
    }
}
