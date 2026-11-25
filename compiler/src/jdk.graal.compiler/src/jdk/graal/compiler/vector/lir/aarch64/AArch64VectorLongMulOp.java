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
package jdk.graal.compiler.vector.lir.aarch64;

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.aarch64.AArch64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * Lane-wise multiplication of vectors with 64-bit integer elements. NEON has no {@code MUL} for
 * {@code .2D} arrangements, so the product is composed of 32-bit multiplications.
 * With {@code x = xl + 2^32 * xh} and {@code y = yl + 2^32 * yh}:
 *
 * <pre>
 * x * y mod 2^64 = xl * yl + 2^32 * (xl * yh + xh * yl)
 * </pre>
 *
 * <pre>
 * rev64 t1.4s, b.4s         // t1 = |yl|yh| per 64-bit lane
 * mul   t1.4s, t1.4s, a.4s  // t1 = |xh*yl|xl*yh| (low 32 bits of each product)
 * xtn   t2.2s, a.2d         // t2 = xl of both lanes
 * xtn   t3.2s, b.2d         // t3 = yl of both lanes
 * addp  t1.4s, t1.4s, t1.4s // t1.2s = cross product sums of both lanes
 * ushll dst.2d, t1.2s, #0   // zero-extend the sums to 64 bits
 * shl   dst.2d, dst.2d, #32 // move them into the high halves
 * umlal dst.2d, t2.2s, t3.2s // dst += xl * yl (full 64-bit products)
 * </pre>
 */
@Opcode("VMUL_LONG")
public final class AArch64VectorLongMulOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64VectorLongMulOp> TYPE = LIRInstructionClass.create(AArch64VectorLongMulOp.class);

    @Def({REG}) protected AllocatableValue result;
    /*
     * The inputs are read after the temporaries are written, so they must not share registers with
     * them.
     */
    @Alive({REG}) protected AllocatableValue a;
    @Alive({REG}) protected AllocatableValue b;
    @Temp({REG}) protected AllocatableValue cross;
    @Temp({REG}) protected AllocatableValue aLow;
    @Temp({REG}) protected AllocatableValue bLow;

    public AArch64VectorLongMulOp(AllocatableValue result, AllocatableValue a, AllocatableValue b, AllocatableValue cross, AllocatableValue aLow, AllocatableValue bLow) {
        super(TYPE);
        this.result = result;
        this.a = a;
        this.b = b;
        this.cross = cross;
        this.aLow = aLow;
        this.bLow = bLow;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        AArch64Kind kind = (AArch64Kind) result.getPlatformKind();
        assert kind.getScalar() == AArch64Kind.QWORD : kind;
        assert kind.getVectorLength() == 1 || kind.getVectorLength() == 2 : kind;

        Register dst = asRegister(result);
        Register x = asRegister(a);
        Register y = asRegister(b);
        Register t1 = asRegister(cross);
        Register t2 = asRegister(aLow);
        Register t3 = asRegister(bLow);
        assert dst.getRegisterCategory().equals(AArch64.SIMD) : dst;

        /*
         * The sequence always operates on the full 128-bit register. For a single 64-bit lane, the
         * upper lane of the result is computed from (ignored) upper input bits.
         */
        masm.neon.rev64VV(ASIMDSize.FullReg, ElementSize.Word, t1, y);
        masm.neon.mulVVV(ASIMDSize.FullReg, ElementSize.Word, t1, t1, x);
        masm.neon.xtnVV(ElementSize.Word, t2, x);
        masm.neon.xtnVV(ElementSize.Word, t3, y);
        masm.neon.addpVVV(ASIMDSize.FullReg, ElementSize.Word, t1, t1, t1);
        masm.neon.ushllVVI(ElementSize.Word, dst, t1, 0);
        masm.neon.shlVVI(ASIMDSize.FullReg, ElementSize.DoubleWord, dst, dst, 32);
        masm.neon.umlalVVV(ElementSize.Word, dst, t2, t3);
    }
}
