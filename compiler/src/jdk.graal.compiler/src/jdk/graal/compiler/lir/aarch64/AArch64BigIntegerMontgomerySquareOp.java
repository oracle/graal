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

import static jdk.graal.compiler.lir.aarch64.AArch64BigIntegerMontgomeryMultiplyOp.emitMontgomeryMultiply;
import static jdk.vm.ci.aarch64.AArch64.r0;
import static jdk.vm.ci.aarch64.AArch64.r1;
import static jdk.vm.ci.aarch64.AArch64.r11;
import static jdk.vm.ci.aarch64.AArch64.r12;
import static jdk.vm.ci.aarch64.AArch64.r13;
import static jdk.vm.ci.aarch64.AArch64.r14;
import static jdk.vm.ci.aarch64.AArch64.r15;
import static jdk.vm.ci.aarch64.AArch64.r16;
import static jdk.vm.ci.aarch64.AArch64.r17;
import static jdk.vm.ci.aarch64.AArch64.r19;
import static jdk.vm.ci.aarch64.AArch64.r2;
import static jdk.vm.ci.aarch64.AArch64.r20;
import static jdk.vm.ci.aarch64.AArch64.r21;
import static jdk.vm.ci.aarch64.AArch64.r22;
import static jdk.vm.ci.aarch64.AArch64.r24;
import static jdk.vm.ci.aarch64.AArch64.r3;
import static jdk.vm.ci.aarch64.AArch64.r4;
import static jdk.vm.ci.aarch64.AArch64.r5;
import static jdk.vm.ci.aarch64.AArch64.r6;
import static jdk.vm.ci.aarch64.AArch64.r7;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/2fe611a2a3386d097f636c15bd4d396a82dc695e/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L11801-L11807",
          sha1 = "4bec9a4e9268ab0709d89571612275875a730396")
// @formatter:on
public final class AArch64BigIntegerMontgomerySquareOp extends AArch64LIRInstruction {

    public static final LIRInstructionClass<AArch64BigIntegerMontgomerySquareOp> TYPE = LIRInstructionClass.create(AArch64BigIntegerMontgomerySquareOp.class);

    @Use private Value aValue;
    @Use private Value nValue;
    @Use private Value lenValue;
    @Use private Value invValue;
    @Use private Value productValue;

    @Temp private Value[] temps;

    public AArch64BigIntegerMontgomerySquareOp(Value aValue, Value nValue, Value lenValue, Value invValue, Value productValue) {
        super(TYPE);

        GraalError.guarantee(asRegister(aValue).equals(r0), "expect aValue at r0, but was %s", aValue);
        GraalError.guarantee(asRegister(nValue).equals(r1), "expect nValue at r1, but was %s", nValue);
        GraalError.guarantee(asRegister(lenValue).equals(r2), "expect lenValue at r2, but was %s", lenValue);
        GraalError.guarantee(asRegister(invValue).equals(r3), "expect invValue at r3, but was %s", invValue);
        GraalError.guarantee(asRegister(productValue).equals(r4), "expect productValue at r4, but was %s", productValue);

        this.aValue = aValue;
        this.nValue = nValue;
        this.lenValue = lenValue;
        this.invValue = invValue;
        this.productValue = productValue;

        this.temps = new Value[]{
                        r0.asValue(),
                        r1.asValue(),
                        r2.asValue(),
                        r4.asValue(),
                        r5.asValue(),
                        r6.asValue(),
                        r7.asValue(),
                        r11.asValue(),
                        r12.asValue(),
                        r13.asValue(),
                        r14.asValue(),
                        r15.asValue(),
                        r16.asValue(),
                        r17.asValue(),
                        r19.asValue(),
                        r20.asValue(),
                        r21.asValue(),
                        r22.asValue(),
                        r24.asValue(),
        };
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        GraalError.guarantee(aValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid aValue kind: %s", aValue);
        GraalError.guarantee(nValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid nValue kind: %s", nValue);
        GraalError.guarantee(lenValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid lenValue kind: %s", lenValue);
        GraalError.guarantee(invValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid invValue kind: %s", invValue);
        GraalError.guarantee(productValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid productValue kind: %s", productValue);

        // We use generate_multiply() rather than generate_square()
        // because it's faster for the sizes of modulus we care about.
        Register aInts = asRegister(aValue);
        Register nInts = asRegister(nValue);
        Register lenInts = asRegister(lenValue);
        Register inv = asRegister(invValue);

        try (ScratchRegister scratchRegister1 = masm.getScratchRegister();
                        ScratchRegister scratchRegister2 = masm.getScratchRegister()) {
            Register rscratch1 = scratchRegister1.getRegister();
            Register rscratch2 = scratchRegister2.getRegister();
            emitMontgomeryMultiply(masm,
                            new AArch64BigIntegerMontgomeryMultiplyOp.Regs(aInts, aInts, nInts, asRegister(productValue), inv, lenInts,
                                            r5, r6, r7, rscratch1,
                                            rscratch2, r24, r11, r12,
                                            r13, r14, r15,
                                            r16, r17,
                                            r19, r20, r21, r22),
                            true);
        }
    }
}
