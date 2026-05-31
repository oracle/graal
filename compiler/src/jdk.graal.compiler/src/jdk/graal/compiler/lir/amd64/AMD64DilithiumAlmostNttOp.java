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

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.EVMOVDQU32;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPBROADCASTD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPERMI2D;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.ZMM;
import static jdk.graal.compiler.lir.amd64.AMD64AESEncryptOp.asXMMRegister;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.DILITHIUM_Q;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.MONT_MUL_PERMS;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.MONT_Q_INV_MOD_R;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.NTT_L4_PERMS_0;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.NTT_L4_PERMS_1;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.NTT_L5_PERMS_0;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.NTT_L5_PERMS_1;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.NTT_L6_PERMS_0;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.NTT_L6_PERMS_1;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.NTT_L7_PERMS_0;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.NTT_L7_PERMS_1;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.NTT_L7_PERMS_2;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.NTT_L7_PERMS_3;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.XMM_0_3;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.XMM_0145;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.XMM_0246;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.XMM_12_15;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.XMM_1357;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.XMM_16_19;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.XMM_16_27;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.XMM_20222426;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.XMM_21232527;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.XMM_2367;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.XMM_24_27;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.XMM_29_29;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.XMM_4_20_24;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.XMM_4_7;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.XMM_8_11;
import static jdk.graal.compiler.lir.amd64.AMD64DilithiumSupport.XMM_BYTES;
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
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/c59e44a7aa2aeff0823830b698d524523b996650/src/hotspot/cpu/x86/stubGenerator_x86_64_dilithium.cpp#L45-L263",
          sha1 = "55f1b28b5d1653df990a2a470d3ed236b34abbf1")
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/c59e44a7aa2aeff0823830b698d524523b996650/src/hotspot/cpu/x86/stubGenerator_x86_64_dilithium.cpp#L265-L455",
          sha1 = "619c0905c7134ca129179e0473e03fbf9ac913f3")
// @formatter:on
public final class AMD64DilithiumAlmostNttOp extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64DilithiumAlmostNttOp> TYPE = LIRInstructionClass.create(AMD64DilithiumAlmostNttOp.class);

    @Use({OperandFlag.REG}) private Value coeffsValue;
    @Use({OperandFlag.REG}) private Value zetasValue;

    @Def({OperandFlag.REG}) private Value resultValue;

    @Temp({OperandFlag.REG}) private Value[] temps;

    public AMD64DilithiumAlmostNttOp(AllocatableValue resultValue, AllocatableValue coeffsValue, AllocatableValue zetasValue) {
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

        EVMOVDQU32.emit(masm, ZMM, xmm28, recordExternalAddress(crb, MONT_MUL_PERMS));

        // Each level represents one iteration of the outer for loop of the Java version
        // In each of these iterations half of the coefficients are (Montgomery)
        // multiplied by a zeta corresponding to the coefficient and then these
        // products will be added to and subtracted from the other half of the
        // coefficients. In each level we just collect the coefficients (using
        // evpermi2d() instructions where necessary, i.e. in levels 4-7) that need to
        // be multiplied by the zetas in one set, the rest to another set of vector
        // registers, then redistribute the addition/substraction results.

        // For levels 0 and 1 the zetas are not different within the 4 xmm registers
        // that we would use for them, so we use only one, xmm29.
        loadXmm29(masm, zetas, 0);
        EVPBROADCASTD.emit(masm, ZMM, xmm30, recordExternalAddress(crb, MONT_Q_INV_MOD_R));
        EVPBROADCASTD.emit(masm, ZMM, xmm31, recordExternalAddress(crb, DILITHIUM_Q));

        // load all coefficients into the vector registers Zmm_0-Zmm_15,
        // 16 coefficients into each
        load4Xmms(masm, XMM_0_3, coeffs, 0);
        load4Xmms(masm, XMM_4_7, coeffs, 4 * XMM_BYTES);
        load4Xmms(masm, XMM_8_11, coeffs, 8 * XMM_BYTES);
        load4Xmms(masm, XMM_12_15, coeffs, 12 * XMM_BYTES);

        // level 0 and 1 can be done entirely in registers as the zetas on these
        // levels are the same for all the montmuls that we can do in parallel

        // level 0
        montMul64(masm, XMM_16_19, XMM_8_11, XMM_29_29, XMM_16_27);
        subAdd(masm, XMM_8_11, XMM_0_3, XMM_0_3, XMM_16_19);
        montMul64(masm, XMM_16_19, XMM_12_15, XMM_29_29, XMM_16_27);
        loadXmm29(masm, zetas, 512); // for level 1
        subAdd(masm, XMM_12_15, XMM_4_7, XMM_4_7, XMM_16_19);

        // level 1
        montMul64(masm, XMM_16_19, XMM_4_7, XMM_29_29, XMM_16_27);
        loadXmm29(masm, zetas, 768);
        subAdd(masm, XMM_4_7, XMM_0_3, XMM_0_3, XMM_16_19);
        montMul64(masm, XMM_16_19, XMM_12_15, XMM_29_29, XMM_16_27);
        subAdd(masm, XMM_12_15, XMM_8_11, XMM_8_11, XMM_16_19);

        // levels 2 to 7 are done in 2 batches, by first saving half of the coefficients
        // from level 1 into memory, doing all the level 2 to level 7 computations
        // on the remaining half in the vector registers, saving the result to
        // memory after level 7, then loading back the coefficients that we saved after
        // level 1 and do the same computation with those
        store4Xmms(masm, coeffs, 8 * XMM_BYTES, XMM_8_11);
        store4Xmms(masm, coeffs, 12 * XMM_BYTES, XMM_12_15);

        masm.movl(iterations, 2);

        masm.align(preferredLoopAlignment(crb));
        masm.bind(loop);

        masm.subl(iterations, 1);

        // level 2
        load4Xmms(masm, XMM_12_15, zetas, 2 * 512);
        montMul64(masm, XMM_16_19, XMM_2367, XMM_12_15, XMM_16_27);
        load4Xmms(masm, XMM_12_15, zetas, 3 * 512); // for level 3
        subAdd(masm, XMM_2367, XMM_0145, XMM_0145, XMM_16_19);

        // level 3
        montMul64(masm, XMM_16_19, XMM_1357, XMM_12_15, XMM_16_27);
        subAdd(masm, XMM_1357, XMM_0246, XMM_0246, XMM_16_19);

        // level 4
        loadPerm(crb, masm, NTT_L4_PERMS_0, XMM_16_19);
        loadPerm(crb, masm, NTT_L4_PERMS_1, XMM_12_15);
        load4Xmms(masm, XMM_24_27, zetas, 4 * 512);

        for (int i = 0; i < 8; i += 2) {
            masm.emit(EVPERMI2D, asXMMRegister(i / 2 + 16), asXMMRegister(i), asXMMRegister(i + 1), ZMM);
        }
        for (int i = 0; i < 8; i += 2) {
            masm.emit(EVPERMI2D, asXMMRegister(i / 2 + 12), asXMMRegister(i), asXMMRegister(i + 1), ZMM);
        }

        montMul64(masm, XMM_12_15, XMM_12_15, XMM_24_27, XMM_4_20_24);
        subAdd(masm, XMM_1357, XMM_0246, XMM_16_19, XMM_12_15);

        // level 5
        loadPerm(crb, masm, NTT_L5_PERMS_0, XMM_16_19);
        loadPerm(crb, masm, NTT_L5_PERMS_1, XMM_12_15);
        load4Xmms(masm, XMM_24_27, zetas, 5 * 512);

        for (int i = 0; i < 8; i += 2) {
            masm.emit(EVPERMI2D, asXMMRegister(i / 2 + 16), asXMMRegister(i), asXMMRegister(i + 1), ZMM);
        }
        for (int i = 0; i < 8; i += 2) {
            masm.emit(EVPERMI2D, asXMMRegister(i / 2 + 12), asXMMRegister(i), asXMMRegister(i + 1), ZMM);
        }

        montMul64(masm, XMM_12_15, XMM_12_15, XMM_24_27, XMM_4_20_24);
        subAdd(masm, XMM_1357, XMM_0246, XMM_16_19, XMM_12_15);

        // level 6
        loadPerm(crb, masm, NTT_L6_PERMS_0, XMM_16_19);
        loadPerm(crb, masm, NTT_L6_PERMS_1, XMM_12_15);
        load4Xmms(masm, XMM_24_27, zetas, 6 * 512);

        for (int i = 0; i < 8; i += 2) {
            masm.emit(EVPERMI2D, asXMMRegister(i / 2 + 16), asXMMRegister(i), asXMMRegister(i + 1), ZMM);
        }
        for (int i = 0; i < 8; i += 2) {
            masm.emit(EVPERMI2D, asXMMRegister(i / 2 + 12), asXMMRegister(i), asXMMRegister(i + 1), ZMM);
        }

        montMul64(masm, XMM_12_15, XMM_12_15, XMM_24_27, XMM_4_20_24);
        subAdd(masm, XMM_1357, XMM_0246, XMM_16_19, XMM_12_15);

        // level 7
        loadPerm(crb, masm, NTT_L7_PERMS_0, XMM_16_19);
        loadPerm(crb, masm, NTT_L7_PERMS_1, XMM_12_15);
        load4Xmms(masm, XMM_24_27, zetas, 7 * 512);

        for (int i = 0; i < 8; i += 2) {
            masm.emit(EVPERMI2D, asXMMRegister(i / 2 + 16), asXMMRegister(i), asXMMRegister(i + 1), ZMM);
        }
        for (int i = 0; i < 8; i += 2) {
            masm.emit(EVPERMI2D, asXMMRegister(i / 2 + 12), asXMMRegister(i), asXMMRegister(i + 1), ZMM);
        }

        montMul64(masm, XMM_12_15, XMM_12_15, XMM_24_27, XMM_4_20_24, true);
        loadPerm(crb, masm, NTT_L7_PERMS_2, XMM_0246);
        loadPerm(crb, masm, NTT_L7_PERMS_3, XMM_1357);
        subAdd(masm, XMM_21232527, XMM_20222426, XMM_16_19, XMM_12_15);

        for (int i = 0; i < 8; i += 2) {
            masm.emit(EVPERMI2D, asXMMRegister(i), asXMMRegister(i + 20), asXMMRegister(i + 21), ZMM);
            masm.emit(EVPERMI2D, asXMMRegister(i + 1), asXMMRegister(i + 20), asXMMRegister(i + 21), ZMM);
        }

        masm.cmpl(iterations, 0);
        masm.jcc(ConditionFlag.Equal, end);

        store4Xmms(masm, coeffs, 0, XMM_0_3);
        store4Xmms(masm, coeffs, 4 * XMM_BYTES, XMM_4_7);

        load4Xmms(masm, XMM_0_3, coeffs, 8 * XMM_BYTES);
        load4Xmms(masm, XMM_4_7, coeffs, 12 * XMM_BYTES);

        masm.addq(zetas, 4 * XMM_BYTES);
        masm.jmp(loop);

        masm.bind(end);

        store4Xmms(masm, coeffs, 8 * XMM_BYTES, XMM_0_3);
        store4Xmms(masm, coeffs, 12 * XMM_BYTES, XMM_4_7);

        masm.xorl(result, result);
    }
}
