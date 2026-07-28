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

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPBROADCASTD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.EVMOVDQU32;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPADDD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPERMI2D;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPSUBD;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.ZMM;
import static jdk.graal.compiler.lir.amd64.AMD64AESEncryptOp.asXMMRegister;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.load4Xmms;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.loadPerm;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.loadXmm29;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.montMul64;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.store4Xmms;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.subAdd;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;
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

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/c59e44a7aa2aeff0823830b698d524523b996650/src/hotspot/cpu/x86/stubGenerator_x86_64_dilithium.cpp#L457-L634",
          sha1 = "8aa7b7141e5ad78862676946bd0c7f36e3eae7bc")
// @formatter:on
public final class AMD64DilithiumAlmostInverseNttOp extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64DilithiumAlmostInverseNttOp> TYPE = LIRInstructionClass.create(AMD64DilithiumAlmostInverseNttOp.class);

    @Use({OperandFlag.REG}) private Value coeffsValue;
    @Use({OperandFlag.REG}) private Value zetasValue;

    @Def({OperandFlag.REG}) private Value resultValue;

    @Temp({OperandFlag.REG}) private Value[] temps;

    public AMD64DilithiumAlmostInverseNttOp(AllocatableValue resultValue, AllocatableValue coeffsValue, AllocatableValue zetasValue) {
        super(TYPE);

        GraalError.guarantee(asRegister(resultValue).equals(rax), "expect resultValue at rax, but was %s", resultValue);
        GraalError.guarantee(asRegister(coeffsValue).equals(rdi), "expect coeffsValue at rdi, but was %s", coeffsValue);
        GraalError.guarantee(asRegister(zetasValue).equals(rsi), "expect zetasValue at rsi, but was %s", zetasValue);

        this.resultValue = resultValue;
        this.coeffsValue = coeffsValue;
        this.zetasValue = zetasValue;

        this.temps = new Value[]{
                        rsi.asValue(), // zetas is clobbered by pointer adjustment
                        rdx.asValue(),
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
        GraalError.guarantee(coeffsValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid coeffsValue kind: %s", coeffsValue);
        GraalError.guarantee(zetasValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid zetasValue kind: %s", zetasValue);

        Register coeffs = asRegister(coeffsValue);
        Register zetas = asRegister(zetasValue);
        Register iterations = rdx;

        Register result = asRegister(resultValue);

        Label loop = new Label();
        Label end = new Label();

        EVMOVDQU32.emit(masm, ZMM, xmm28, recordExternalAddress(crb, AMD64DilithiumSupport.MONT_MUL_PERMS));
        EVPBROADCASTD.emit(masm, ZMM, xmm30, recordExternalAddress(crb, AMD64DilithiumSupport.MONT_Q_INV_MOD_R));
        EVPBROADCASTD.emit(masm, ZMM, xmm31, recordExternalAddress(crb, AMD64DilithiumSupport.DILITHIUM_Q));

        // Each level represents one iteration of the outer for loop of the
        // Java version.
        // In each of these iterations half of the coefficients are added to and
        // subtracted from the other half of the coefficients then the result of
        // the substartion is (Montgomery) multiplied by the corresponding zetas.
        // In each level we just collect the coefficients (using evpermi2d()
        // instructions where necessary, i.e. on levels 0-4) so that the results of
        // the additions and subtractions go to the vector registers so that they
        // align with each other and the zetas.

        // We do levels 0-6 in two batches, each batch entirely in the vector registers
        load4Xmms(masm, AMD64DilithiumSupport.XMM_0_3, coeffs, 0);
        load4Xmms(masm, AMD64DilithiumSupport.XMM_4_7, coeffs, 4 * AMD64DilithiumSupport.XMM_BYTES);

        masm.movl(iterations, 2);

        masm.align(preferredLoopAlignment(crb));
        masm.bind(loop);

        masm.subl(iterations, 1);

        // level 0
        loadPerm(crb, masm, AMD64DilithiumSupport.NTT_INV_L0_PERMS_0, AMD64DilithiumSupport.XMM_8_11);
        loadPerm(crb, masm, AMD64DilithiumSupport.NTT_INV_L0_PERMS_1, AMD64DilithiumSupport.XMM_12_15);

        for (int i = 0; i < 8; i += 2) {
            masm.emit(EVPERMI2D, asXMMRegister(i / 2 + 8), asXMMRegister(i), asXMMRegister(i + 1), ZMM);
            masm.emit(EVPERMI2D, asXMMRegister(i / 2 + 12), asXMMRegister(i), asXMMRegister(i + 1), ZMM);
        }

        load4Xmms(masm, AMD64DilithiumSupport.XMM_4_7, zetas, 0);
        subAdd(masm, AMD64DilithiumSupport.XMM_24_27, AMD64DilithiumSupport.XMM_0_3, AMD64DilithiumSupport.XMM_8_11,
                        AMD64DilithiumSupport.XMM_12_15);
        montMul64(masm, AMD64DilithiumSupport.XMM_4_7, AMD64DilithiumSupport.XMM_4_7, AMD64DilithiumSupport.XMM_24_27,
                        AMD64DilithiumSupport.XMM_16_27, true);

        // level 1
        loadPerm(crb, masm, AMD64DilithiumSupport.NTT_INV_L1_PERMS_0, AMD64DilithiumSupport.XMM_8_11);
        loadPerm(crb, masm, AMD64DilithiumSupport.NTT_INV_L1_PERMS_1, AMD64DilithiumSupport.XMM_12_15);

        for (int i = 0; i < 4; i++) {
            masm.emit(EVPERMI2D, asXMMRegister(i + 8), asXMMRegister(i), asXMMRegister(i + 4), ZMM);
            masm.emit(EVPERMI2D, asXMMRegister(i + 12), asXMMRegister(i), asXMMRegister(i + 4), ZMM);
        }

        load4Xmms(masm, AMD64DilithiumSupport.XMM_4_7, zetas, 512);
        subAdd(masm, AMD64DilithiumSupport.XMM_24_27, AMD64DilithiumSupport.XMM_0_3, AMD64DilithiumSupport.XMM_8_11,
                        AMD64DilithiumSupport.XMM_12_15);
        montMul64(masm, AMD64DilithiumSupport.XMM_4_7, AMD64DilithiumSupport.XMM_24_27, AMD64DilithiumSupport.XMM_4_7,
                        AMD64DilithiumSupport.XMM_16_27);

        // level 2
        loadPerm(crb, masm, AMD64DilithiumSupport.NTT_INV_L2_PERMS_0, AMD64DilithiumSupport.XMM_8_11);
        loadPerm(crb, masm, AMD64DilithiumSupport.NTT_INV_L2_PERMS_1, AMD64DilithiumSupport.XMM_12_15);

        for (int i = 0; i < 4; i++) {
            masm.emit(EVPERMI2D, asXMMRegister(i + 8), asXMMRegister(i), asXMMRegister(i + 4), ZMM);
            masm.emit(EVPERMI2D, asXMMRegister(i + 12), asXMMRegister(i), asXMMRegister(i + 4), ZMM);
        }

        load4Xmms(masm, AMD64DilithiumSupport.XMM_4_7, zetas, 2 * 512);
        subAdd(masm, AMD64DilithiumSupport.XMM_24_27, AMD64DilithiumSupport.XMM_0_3, AMD64DilithiumSupport.XMM_8_11,
                        AMD64DilithiumSupport.XMM_12_15);
        montMul64(masm, AMD64DilithiumSupport.XMM_4_7, AMD64DilithiumSupport.XMM_24_27, AMD64DilithiumSupport.XMM_4_7,
                        AMD64DilithiumSupport.XMM_16_27);

        // level 3
        loadPerm(crb, masm, AMD64DilithiumSupport.NTT_INV_L3_PERMS_0, AMD64DilithiumSupport.XMM_8_11);
        loadPerm(crb, masm, AMD64DilithiumSupport.NTT_INV_L3_PERMS_1, AMD64DilithiumSupport.XMM_12_15);

        for (int i = 0; i < 4; i++) {
            masm.emit(EVPERMI2D, asXMMRegister(i + 8), asXMMRegister(i), asXMMRegister(i + 4), ZMM);
            masm.emit(EVPERMI2D, asXMMRegister(i + 12), asXMMRegister(i), asXMMRegister(i + 4), ZMM);
        }

        load4Xmms(masm, AMD64DilithiumSupport.XMM_4_7, zetas, 3 * 512);
        subAdd(masm, AMD64DilithiumSupport.XMM_24_27, AMD64DilithiumSupport.XMM_0_3, AMD64DilithiumSupport.XMM_8_11,
                        AMD64DilithiumSupport.XMM_12_15);
        montMul64(masm, AMD64DilithiumSupport.XMM_4_7, AMD64DilithiumSupport.XMM_24_27, AMD64DilithiumSupport.XMM_4_7,
                        AMD64DilithiumSupport.XMM_16_27);

        // level 4
        loadPerm(crb, masm, AMD64DilithiumSupport.NTT_INV_L4_PERMS_0, AMD64DilithiumSupport.XMM_8_11);
        loadPerm(crb, masm, AMD64DilithiumSupport.NTT_INV_L4_PERMS_1, AMD64DilithiumSupport.XMM_12_15);

        for (int i = 0; i < 4; i++) {
            masm.emit(EVPERMI2D, asXMMRegister(i + 8), asXMMRegister(i), asXMMRegister(i + 4), ZMM);
            masm.emit(EVPERMI2D, asXMMRegister(i + 12), asXMMRegister(i), asXMMRegister(i + 4), ZMM);
        }

        load4Xmms(masm, AMD64DilithiumSupport.XMM_4_7, zetas, 4 * 512);
        subAdd(masm, AMD64DilithiumSupport.XMM_24_27, AMD64DilithiumSupport.XMM_0_3, AMD64DilithiumSupport.XMM_8_11,
                        AMD64DilithiumSupport.XMM_12_15);
        montMul64(masm, AMD64DilithiumSupport.XMM_4_7, AMD64DilithiumSupport.XMM_24_27, AMD64DilithiumSupport.XMM_4_7,
                        AMD64DilithiumSupport.XMM_16_27);

        // level 5
        load4Xmms(masm, AMD64DilithiumSupport.XMM_12_15, zetas, 5 * 512);
        subAdd(masm, AMD64DilithiumSupport.XMM_8_11, AMD64DilithiumSupport.XMM_0_3, AMD64DilithiumSupport.XMM_0426,
                        AMD64DilithiumSupport.XMM_1537);
        montMul64(masm, AMD64DilithiumSupport.XMM_4_7, AMD64DilithiumSupport.XMM_8_11, AMD64DilithiumSupport.XMM_12_15,
                        AMD64DilithiumSupport.XMM_16_27);

        // level 6
        load4Xmms(masm, AMD64DilithiumSupport.XMM_12_15, zetas, 6 * 512);
        subAdd(masm, AMD64DilithiumSupport.XMM_8_11, AMD64DilithiumSupport.XMM_0_3, AMD64DilithiumSupport.XMM_0145,
                        AMD64DilithiumSupport.XMM_2367);
        montMul64(masm, AMD64DilithiumSupport.XMM_4_7, AMD64DilithiumSupport.XMM_8_11, AMD64DilithiumSupport.XMM_12_15,
                        AMD64DilithiumSupport.XMM_16_27);

        masm.cmpl(iterations, 0);
        masm.jcc(ConditionFlag.Equal, end);

        // save the coefficients of the first batch, adjust the zetas
        // and load the second batch of coefficients
        store4Xmms(masm, coeffs, 0, AMD64DilithiumSupport.XMM_0_3);
        store4Xmms(masm, coeffs, 4 * AMD64DilithiumSupport.XMM_BYTES, AMD64DilithiumSupport.XMM_4_7);

        masm.addq(zetas, 4 * AMD64DilithiumSupport.XMM_BYTES);

        load4Xmms(masm, AMD64DilithiumSupport.XMM_0_3, coeffs, 8 * AMD64DilithiumSupport.XMM_BYTES);
        load4Xmms(masm, AMD64DilithiumSupport.XMM_4_7, coeffs, 12 * AMD64DilithiumSupport.XMM_BYTES);

        masm.jmp(loop);

        masm.bind(end);

        // load the coeffs of the first batch of coefficients that were saved after
        // level 6 into Zmm_8-Zmm_15 and do the last level entirely in the vector
        // registers
        load4Xmms(masm, AMD64DilithiumSupport.XMM_8_11, coeffs, 0);
        load4Xmms(masm, AMD64DilithiumSupport.XMM_12_15, coeffs, 4 * AMD64DilithiumSupport.XMM_BYTES);

        // level 7
        loadXmm29(masm, zetas, 7 * 512);

        for (int i = 0; i < 8; i++) {
            masm.emit(EVPADDD, asXMMRegister(i + 16), asXMMRegister(i), asXMMRegister(i + 8), ZMM);
        }
        for (int i = 0; i < 8; i++) {
            masm.emit(EVPSUBD, asXMMRegister(i), asXMMRegister(i + 8), asXMMRegister(i), ZMM);
        }

        store4Xmms(masm, coeffs, 0, AMD64DilithiumSupport.XMM_16_19);
        store4Xmms(masm, coeffs, 4 * AMD64DilithiumSupport.XMM_BYTES, AMD64DilithiumSupport.XMM_20_23);
        montMul64(masm, AMD64DilithiumSupport.XMM_0_3, AMD64DilithiumSupport.XMM_0_3, AMD64DilithiumSupport.XMM_29_29,
                        AMD64DilithiumSupport.XMM_16_27);
        montMul64(masm, AMD64DilithiumSupport.XMM_4_7, AMD64DilithiumSupport.XMM_4_7, AMD64DilithiumSupport.XMM_29_29,
                        AMD64DilithiumSupport.XMM_16_27);
        store4Xmms(masm, coeffs, 8 * AMD64DilithiumSupport.XMM_BYTES, AMD64DilithiumSupport.XMM_0_3);
        store4Xmms(masm, coeffs, 12 * AMD64DilithiumSupport.XMM_BYTES, AMD64DilithiumSupport.XMM_4_7);

        masm.xorl(result, result);
    }
}
