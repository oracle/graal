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
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.guaranteeFixedRegister;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.Q;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.XMM0_3;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.XMM0_7;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.XMM4_7;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.XMM8_15;
import static jdk.graal.compiler.lir.amd64.AMD64KyberSupport.store4regs;
import static jdk.vm.ci.amd64.AMD64.rax;
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

import jdk.graal.compiler.asm.amd64.AMD64Address;
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
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/d8d51f3327b133af4f21cd6dd248324e4fc2a482/src/hotspot/cpu/x86/stubGenerator_x86_64_kyber.cpp#L709-L747",
          sha1 = "82c7aa8d0b11e50862fd67e5bf7adb4cae219a36")
// @formatter:on
public final class AMD64KyberAddPoly2Op extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64KyberAddPoly2Op> TYPE = LIRInstructionClass.create(AMD64KyberAddPoly2Op.class);

    @Def({REG}) private Value resultValue;
    @Use({REG}) private Value resultArrayValue;
    @Use({REG}) private Value aValue;
    @Use({REG}) private Value bValue;
    @Temp({REG}) private Value[] temps;

    public AMD64KyberAddPoly2Op(AllocatableValue resultValue, AllocatableValue resultArrayValue, AllocatableValue aValue, AllocatableValue bValue) {
        super(TYPE);
        guaranteeFixedRegister(resultValue, rax, "resultValue");
        guaranteeFixedRegister(resultArrayValue, rdi, "resultArrayValue");
        guaranteeFixedRegister(aValue, rsi, "aValue");
        guaranteeFixedRegister(bValue, rdx, "bValue");
        this.resultValue = resultValue;
        this.resultArrayValue = resultArrayValue;
        this.aValue = aValue;
        this.bValue = bValue;
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
        GraalError.guarantee(resultArrayValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid %s kind: %s", "resultArrayValue", resultArrayValue);
        Register resultArray = asRegister(resultArrayValue);
        GraalError.guarantee(aValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid %s kind: %s", "aValue", aValue);
        Register a = asRegister(aValue);
        GraalError.guarantee(bValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid %s kind: %s", "bValue", bValue);
        Register b = asRegister(bValue);

        EVPBROADCASTQ.emit(masm, AVXSize.ZMM, xmm31, recordExternalAddress(crb, Q));
        for (int i = 0; i < 8; i++) {
            masm.evmovdqu16(XMM0_7[i], new AMD64Address(a, 64 * i));
            masm.evmovdqu16(XMM8_15[i], new AMD64Address(b, 64 * i));
        }
        for (int i = 0; i < 8; i++) {
            EVPADDW.emit(masm, AVXSize.ZMM, XMM0_7[i], XMM0_7[i], XMM8_15[i]);
        }
        for (int i = 0; i < 8; i++) {
            EVPADDW.emit(masm, AVXSize.ZMM, XMM0_7[i], XMM0_7[i], xmm31);
        }
        store4regs(masm, resultArray, 0, XMM0_3);
        store4regs(masm, resultArray, 256, XMM4_7);
        masm.xorl(result, result);
    }
}
