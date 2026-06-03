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
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPADDW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMULHW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMULLW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPSUBW;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.guaranteeFixedRegister;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.MONT_R_SQUARE_MOD_Q;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.NTT_MULT_PERMS;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.Q;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.Q_INV_MOD_R;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.XMM0_3;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.XMM0145;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.XMM0829;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.XMM1001;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.XMM12_15;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.XMM1357;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.XMM16_19;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.XMM23_23;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.XMM3223;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.XMM5454;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.XMM7676;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.montmul;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.store4regs;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.r12;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
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

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
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
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/d8d51f3327b133af4f21cd6dd248324e4fc2a482/src/hotspot/cpu/x86/stubGenerator_x86_64_kyber.cpp#L589-L702",
          sha1 = "c09485388c16845bf7c338bf206513d58d2cc223")
// @formatter:on
public final class AMD64KyberNttMultOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64KyberNttMultOp> TYPE = LIRInstructionClass.create(AMD64KyberNttMultOp.class);

    @Def({REG}) private Value resultValue;
    @Use({REG}) private Value resultArrayValue;
    @Use({REG}) private Value nttaValue;
    @Use({REG}) private Value nttbValue;
    @Use({REG}) private Value zetasValue;
    @Temp({REG}) private Value[] temps;

    public AMD64KyberNttMultOp(AllocatableValue resultValue, AllocatableValue resultArrayValue, AllocatableValue nttaValue, AllocatableValue nttbValue,
                    AllocatableValue zetasValue) {
        super(TYPE);
        guaranteeFixedRegister(resultValue, rax, "resultValue");
        guaranteeFixedRegister(resultArrayValue, rdi, "resultArrayValue");
        guaranteeFixedRegister(nttaValue, rsi, "nttaValue");
        guaranteeFixedRegister(nttbValue, rdx, "nttbValue");
        guaranteeFixedRegister(zetasValue, rcx, "zetasValue");
        this.resultValue = resultValue;
        this.resultArrayValue = resultArrayValue;
        this.nttaValue = nttaValue;
        this.nttbValue = nttbValue;
        this.zetasValue = zetasValue;
        this.temps = new Value[]{
                        rdi.asValue(),
                        rsi.asValue(),
                        rdx.asValue(),
                        rcx.asValue(),
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
        GraalError.guarantee(resultArrayValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid %s kind: %s", "resultArrayValue", resultArrayValue);
        Register resultArray = asRegister(resultArrayValue);
        GraalError.guarantee(nttaValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid %s kind: %s", "nttaValue", nttaValue);
        Register ntta = asRegister(nttaValue);
        GraalError.guarantee(nttbValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid %s kind: %s", "nttbValue", nttbValue);
        Register nttb = asRegister(nttbValue);
        GraalError.guarantee(zetasValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid %s kind: %s", "zetasValue", zetasValue);
        Register zetas = asRegister(zetasValue);

        Register loopCnt = r12;
        Label loop = new Label();

        masm.push(r12);
        masm.movl(loopCnt, 2);
        masm.evmovdqu16(xmm26, recordExternalAddress(crb, NTT_MULT_PERMS[0]));
        masm.evmovdqu16(xmm27, recordExternalAddress(crb, NTT_MULT_PERMS[1]));
        masm.evmovdqu16(xmm28, recordExternalAddress(crb, NTT_MULT_PERMS[2]));
        masm.evmovdqu16(xmm29, recordExternalAddress(crb, NTT_MULT_PERMS[3]));

        EVPBROADCASTQ.emit(masm, AVXSize.ZMM, xmm31, recordExternalAddress(crb, Q_INV_MOD_R));
        EVPBROADCASTQ.emit(masm, AVXSize.ZMM, xmm30, recordExternalAddress(crb, Q));
        EVPBROADCASTQ.emit(masm, AVXSize.ZMM, xmm23, recordExternalAddress(crb, MONT_R_SQUARE_MOD_Q));

        masm.bind(loop);
        masm.evmovdqu16(xmm1, new AMD64Address(ntta, 0));
        masm.evmovdqu16(xmm8, new AMD64Address(ntta, 64));
        masm.evmovdqu16(xmm3, new AMD64Address(ntta, 128));
        masm.evmovdqu16(xmm9, new AMD64Address(ntta, 192));

        masm.evmovdqu16(xmm5, new AMD64Address(nttb, 0));
        masm.evmovdqu16(xmm10, new AMD64Address(nttb, 64));
        masm.evmovdqu16(xmm7, new AMD64Address(nttb, 128));
        masm.evmovdqu16(xmm11, new AMD64Address(nttb, 192));

        masm.evmovdqu16(xmm0, xmm26);
        masm.evmovdqu16(xmm2, xmm26);
        masm.evmovdqu16(xmm4, xmm26);
        masm.evmovdqu16(xmm6, xmm26);

        masm.evpermi2w(xmm0, xmm1, xmm8);
        masm.evpermt2w(xmm1, xmm27, xmm8);
        masm.evpermi2w(xmm2, xmm3, xmm9);
        masm.evpermt2w(xmm3, xmm27, xmm9);

        masm.evpermi2w(xmm4, xmm5, xmm10);
        masm.evpermt2w(xmm5, xmm27, xmm10);
        masm.evpermi2w(xmm6, xmm7, xmm11);
        masm.evpermt2w(xmm7, xmm27, xmm11);

        masm.evmovdqu16(xmm24, new AMD64Address(zetas, 0));
        masm.evmovdqu16(xmm25, new AMD64Address(zetas, 64));

        montmul(masm, XMM16_19, XMM1001, XMM5454, XMM16_19, XMM12_15);
        montmul(masm, XMM0145, XMM3223, XMM7676, XMM0145, XMM12_15);

        EVPMULLW.emit(masm, AVXSize.ZMM, xmm2, xmm16, xmm24);
        EVPMULLW.emit(masm, AVXSize.ZMM, xmm3, xmm0, xmm25);
        EVPMULHW.emit(masm, AVXSize.ZMM, xmm12, xmm16, xmm24);
        EVPMULHW.emit(masm, AVXSize.ZMM, xmm13, xmm0, xmm25);
        EVPMULLW.emit(masm, AVXSize.ZMM, xmm2, xmm2, xmm31);
        EVPMULLW.emit(masm, AVXSize.ZMM, xmm3, xmm3, xmm31);
        EVPMULHW.emit(masm, AVXSize.ZMM, xmm2, xmm30, xmm2);
        EVPMULHW.emit(masm, AVXSize.ZMM, xmm3, xmm30, xmm3);
        EVPSUBW.emit(masm, AVXSize.ZMM, xmm2, xmm12, xmm2);
        EVPSUBW.emit(masm, AVXSize.ZMM, xmm3, xmm13, xmm3);

        EVPADDW.emit(masm, AVXSize.ZMM, xmm0, xmm2, xmm17);
        EVPADDW.emit(masm, AVXSize.ZMM, xmm8, xmm3, xmm1);
        EVPADDW.emit(masm, AVXSize.ZMM, xmm2, xmm18, xmm19);
        EVPADDW.emit(masm, AVXSize.ZMM, xmm9, xmm4, xmm5);

        montmul(masm, XMM1357, XMM0829, XMM23_23, XMM1357, XMM0829);

        masm.evmovdqu16(xmm0, xmm28);
        masm.evmovdqu16(xmm2, xmm28);
        masm.evpermi2w(xmm0, xmm1, xmm5);
        masm.evpermt2w(xmm1, xmm29, xmm5);
        masm.evpermi2w(xmm2, xmm3, xmm7);
        masm.evpermt2w(xmm3, xmm29, xmm7);

        store4regs(masm, resultArray, 0, XMM0_3);

        masm.addq(ntta, 256);
        masm.addq(nttb, 256);
        masm.addq(resultArray, 256);
        masm.addq(zetas, 128);
        masm.sublAndJcc(loopCnt, 1, ConditionFlag.Greater, loop, false);
        masm.pop(r12);
        masm.xorl(result, result);
    }

    @Override
    public boolean modifiesStackPointer() {
        return true;
    }
}
