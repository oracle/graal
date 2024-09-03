/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.SSEOp.UCOMIS;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

/**
 * Floating point to integer conversion according to Java semantics. This wraps an AMD64 conversion
 * instruction and adjusts its result as needed. According to Java semantics, NaN inputs should be
 * mapped to 0, and values outside the integer type's range should be mapped to {@code MIN_VALUE} or
 * {@code MAX_VALUE} according to the input's sign. The AMD64 instructions produce {@code MIN_VALUE}
 * for NaNs and all values outside the integer type's range. So we need to fix up the result for
 * NaNs and positive overflowing values. Negative overflowing values keep {@code MIN_VALUE}.
 */
public class AMD64ConvertFloatToIntegerOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64ConvertFloatToIntegerOp> TYPE = LIRInstructionClass.create(AMD64ConvertFloatToIntegerOp.class);

    @Def({REG}) protected Value dstValue;
    @Alive({REG}) protected Value srcValue;
    @Temp({REG, ILLEGAL}) protected Value tmpValue;

    private final OpcodeEmitter opcode;
    private final boolean canBeNaN;
    private final boolean canOverflow;

    @FunctionalInterface
    public interface OpcodeEmitter {
        /** Emit the actual conversion instruction. */
        void emit(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register dst, Register src);
    }

    public AMD64ConvertFloatToIntegerOp(LIRGeneratorTool tool, OpcodeEmitter opcode, Value dstValue, Value srcValue, boolean canBeNaN, boolean canOverflow) {
        super(TYPE);
        this.dstValue = dstValue;
        this.srcValue = srcValue;
        this.opcode = opcode;
        this.tmpValue = canOverflow ? tool.newVariable(srcValue.getValueKind()) : Value.ILLEGAL;
        this.canBeNaN = canBeNaN;
        this.canOverflow = canOverflow;

        GraalError.guarantee(srcValue.getPlatformKind() instanceof AMD64Kind kind && kind.getVectorLength() == 1 && kind.isXMM(), "source must be scalar floating-point: %s", srcValue);
        GraalError.guarantee(dstValue.getPlatformKind() instanceof AMD64Kind kind && kind.getVectorLength() == 1 && kind.isInteger(), "destination must be integer: %s", dstValue);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register dst = asRegister(dstValue);
        Register src = asRegister(srcValue);
        Label fixupPath = new Label();
        Label done = new Label();

        opcode.emit(crb, masm, dst, src);

        if (!canBeNaN && !canOverflow) {
            /* No fixup needed. */
            return;
        }

        int integerBytes = dstValue.getPlatformKind().getSizeInBytes();
        GraalError.guarantee(integerBytes == 4 || integerBytes == 8, "unexpected target %s", dstValue);
        OperandSize floatSize = switch (srcValue.getPlatformKind().getSizeInBytes()) {
            case 4 -> OperandSize.PS;
            case 8 -> OperandSize.PD;
            default -> throw GraalError.shouldNotReachHere("unexpected input %s".formatted(srcValue));
        };

        /*
         * if (dst == MIN_VALUE) { goto fixupPath; }
         */
        if (integerBytes == 4) {
            masm.cmplAndJcc(dst, Integer.MIN_VALUE, AMD64Assembler.ConditionFlag.Equal, fixupPath, false);
        } else {
            masm.cmpq(dst, (AMD64Address) crb.asLongConstRef(JavaConstant.forLong(Long.MIN_VALUE)));
            masm.jcc(AMD64Assembler.ConditionFlag.Equal, fixupPath);
        }

        crb.getLIR().addSlowPath(this, () -> {
            masm.bind(fixupPath);

            if (canBeNaN) {
                /*
                 * if (isNaN(src)) { result = 0; goto done; }
                 *
                 * The isNaN check is implemented as src != src. C2's fixup stubs check for a NaN
                 * bit pattern directly, using the same number of cycles but using an extra general
                 * purpose register.
                 */
                Label isNotNaN = new Label();
                UCOMIS.emit(masm, floatSize, src, src);
                masm.jcc(AMD64Assembler.ConditionFlag.NoParity, isNotNaN, true);
                masm.movl(dst, 0);
                masm.jmp(done);
                masm.bind(isNotNaN);
            }

            if (canOverflow) {
                /*
                 * if (src > 0.0) { result = MAX_VALUE; }
                 *
                 * We use an actual floating point compare, C2's stubs check the sign bit in a GPR.
                 */
                Register zero = asRegister(tmpValue);
                masm.pxor(AVXKind.AVXSize.XMM, zero, zero);
                UCOMIS.emit(masm, floatSize, src, zero);
                masm.jcc(AMD64Assembler.ConditionFlag.BelowEqual, done);
                /*
                 * MAX_VALUE is the bitwise negation of MIN_VALUE, which is already in dst. A
                 * negation takes the same number of cycles as a move, but its encoding is shorter.
                 */
                if (integerBytes == 4) {
                    masm.notl(dst);
                } else {
                    masm.notq(dst);
                }
            }

            /* Return to inline code. */
            masm.jmp(done);
        });

        masm.bind(done);
    }
}
