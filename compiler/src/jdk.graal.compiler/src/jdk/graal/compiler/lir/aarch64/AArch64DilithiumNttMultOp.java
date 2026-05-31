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

import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED;
import static jdk.graal.compiler.lir.aarch64.AArch64LIRHelper.loadExternalAddress;
import static jdk.graal.compiler.lir.aarch64.AArch64LIRHelper.pointerConstant;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/c59e44a7aa2aeff0823830b698d524523b996650/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L6808-L6871",
          sha1 = "eff37983760e0f04abdc02daf7a2c21428387376")
// @formatter:on
public final class AArch64DilithiumNttMultOp extends AArch64LIRInstruction {

    public static final LIRInstructionClass<AArch64DilithiumNttMultOp> TYPE = LIRInstructionClass.create(AArch64DilithiumNttMultOp.class);

    private static final int DILITHIUM_Q = 8380417;
    private static final int DILITHIUM_Q_INV_MOD_R = 58728449;
    private static final int DILITHIUM_R_SQUARE_MOD_Q = 2365951;

    private static final ArrayDataPointerConstant DILITHIUM_CONSTS = pointerConstant(16, new int[]{
                    DILITHIUM_Q_INV_MOD_R, DILITHIUM_Q_INV_MOD_R, DILITHIUM_Q_INV_MOD_R, DILITHIUM_Q_INV_MOD_R,
                    DILITHIUM_Q, DILITHIUM_Q, DILITHIUM_Q, DILITHIUM_Q,
                    0, 0, 0, 0,
                    DILITHIUM_R_SQUARE_MOD_Q, DILITHIUM_R_SQUARE_MOD_Q, DILITHIUM_R_SQUARE_MOD_Q, DILITHIUM_R_SQUARE_MOD_Q,
                    0, 0, 0, 0,
    });

    private static final Register[] VS1 = {AArch64.v0, AArch64.v1, AArch64.v2, AArch64.v3, AArch64.v4, AArch64.v5, AArch64.v6, AArch64.v7};
    private static final Register[] VS2 = {AArch64.v16, AArch64.v17, AArch64.v18, AArch64.v19, AArch64.v20, AArch64.v21, AArch64.v22, AArch64.v23};
    private static final Register[] VRSQUARE = {AArch64.v29, AArch64.v29, AArch64.v29, AArch64.v29, AArch64.v29, AArch64.v29, AArch64.v29, AArch64.v29};

    @Def({REG}) private Value resultValue;

    @Use({REG}) private Value productValue;
    @Use({REG}) private Value coeffs1Value;
    @Use({REG}) private Value coeffs2Value;

    @Temp({REG}) private Value[] temps;

    public AArch64DilithiumNttMultOp(AllocatableValue resultValue, AllocatableValue productValue, AllocatableValue coeffs1Value, AllocatableValue coeffs2Value) {
        super(TYPE);
        GraalError.guarantee(asRegister(resultValue).equals(AArch64.r0), "expect resultValue at r0, but was %s", resultValue);
        GraalError.guarantee(asRegister(productValue).equals(AArch64.r0), "expect productValue at r0, but was %s", productValue);
        GraalError.guarantee(asRegister(coeffs1Value).equals(AArch64.r1), "expect coeffs1Value at r1, but was %s", coeffs1Value);
        GraalError.guarantee(asRegister(coeffs2Value).equals(AArch64.r2), "expect coeffs2Value at r2, but was %s", coeffs2Value);
        this.resultValue = resultValue;
        this.productValue = productValue;
        this.coeffs1Value = coeffs1Value;
        this.coeffs2Value = coeffs2Value;

        this.temps = new Value[]{
                        AArch64.r0.asValue(), // product is clobbered by post-indexed stores
                        AArch64.r1.asValue(), // coeffs1 is clobbered by post-indexed loads
                        AArch64.r2.asValue(), // coeffs2 is clobbered by post-indexed loads
                        AArch64.r11.asValue(),
                        AArch64.r14.asValue(),
                        AArch64.v0.asValue(),
                        AArch64.v1.asValue(),
                        AArch64.v2.asValue(),
                        AArch64.v3.asValue(),
                        AArch64.v4.asValue(),
                        AArch64.v5.asValue(),
                        AArch64.v6.asValue(),
                        AArch64.v7.asValue(),
                        AArch64.v16.asValue(),
                        AArch64.v17.asValue(),
                        AArch64.v18.asValue(),
                        AArch64.v19.asValue(),
                        AArch64.v20.asValue(),
                        AArch64.v21.asValue(),
                        AArch64.v22.asValue(),
                        AArch64.v23.asValue(),
                        AArch64.v24.asValue(),
                        AArch64.v25.asValue(),
                        AArch64.v26.asValue(),
                        AArch64.v27.asValue(),
                        AArch64.v29.asValue(),
                        AArch64.v30.asValue(),
                        AArch64.v31.asValue(),
        };
    }

    private static void loadVSeq8Post(AArch64MacroAssembler masm, Register[] vseq, Register base) {
        for (int i = 0; i < 8; i += 2) {
            masm.fldp(128, vseq[i], vseq[i + 1], AArch64Address.createImmediateAddress(128, IMMEDIATE_PAIR_POST_INDEXED, base, 32));
        }
    }

    private static void storeVSeq8Post(AArch64MacroAssembler masm, Register[] vseq, Register base) {
        for (int i = 0; i < 8; i += 2) {
            masm.fstp(128, vseq[i], vseq[i + 1], AArch64Address.createImmediateAddress(128, IMMEDIATE_PAIR_POST_INDEXED, base, 32));
        }
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        GraalError.guarantee(resultValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid resultValue kind: %s", resultValue);
        GraalError.guarantee(productValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid productValue kind: %s", productValue);
        GraalError.guarantee(coeffs1Value.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid coeffs1Value kind: %s", coeffs1Value);
        GraalError.guarantee(coeffs2Value.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid coeffs2Value kind: %s", coeffs2Value);

        Register result = asRegister(productValue);
        Register poly1 = asRegister(coeffs1Value);
        Register poly2 = asRegister(coeffs2Value);
        Register len = AArch64.r11;
        Register dilithiumConsts = AArch64.r14;

        loadExternalAddress(crb, masm, dilithiumConsts, DILITHIUM_CONSTS);

        // load constants q, qinv
        AArch64DilithiumSupport.loadQInvQ(masm, dilithiumConsts);
        // load constant rSquare into v29
        masm.fldr(128, AArch64.v29, AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, dilithiumConsts, 48));

        masm.mov(64, len, zr);
        masm.add(64, len, len, 1024);

        Label loop = new Label();
        masm.bind(loop);

        // b load 32 (8x4S) next inputs from poly1
        loadVSeq8Post(masm, VS1, poly1);
        // c load 32 (8x4S) next inputs from poly2
        loadVSeq8Post(masm, VS2, poly2);
        // compute a = b montmul c
        AArch64DilithiumSupport.emitMontMul32(masm, VS2, VS1, VS2, AArch64DilithiumSupport.VTMP, AArch64DilithiumSupport.VQ);
        // compute a = rsquare montmul a
        AArch64DilithiumSupport.emitMontMul32(masm, VS2, VRSQUARE, VS2, AArch64DilithiumSupport.VTMP, AArch64DilithiumSupport.VQ);
        // save a 32 (8x4S) results
        storeVSeq8Post(masm, VS2, result);

        masm.sub(64, len, len, 128);
        masm.compare(64, len, 128);
        masm.branchConditionally(AArch64Assembler.ConditionFlag.GE, loop);

        masm.mov(32, asRegister(resultValue), zr);
    }
}
