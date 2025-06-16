/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.SSEOp.MOVSD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.SSEOp.MOVSS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.SSEOp.SUB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.SSEOp.UCOMIS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.SSEOp.XOR;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VUCOMISD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VUCOMISS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPXOR;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VSUBSD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VSUBSS;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.XMM;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64SIMDInstructionEncoding;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.SSEOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.NumUtil.Signedness;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.vm.ci.amd64.AMD64;
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
 * <p/>
 *
 * In unsigned mode, these conversions are done according to the Truffle API's reference semantics:
 * Negative values and NaN map to 0, positive values outside the integer's range map to the unsigned
 * maximum value (all bits set).
 */
public class AMD64ConvertFloatToIntegerOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64ConvertFloatToIntegerOp> TYPE = LIRInstructionClass.create(AMD64ConvertFloatToIntegerOp.class);

    @Def({REG}) protected Value dstValue;
    @Alive({REG}) protected Value srcValue;
    @Temp({REG, ILLEGAL}) protected Value zeroTmp;
    @Temp({REG, ILLEGAL}) protected Value unsignedOverflowTmp;

    private final OpcodeEmitter opcodeEmitter;
    private final boolean canBeNaN;
    private final boolean canOverflow;
    /** The signedness of the destination integer type. */
    private final Signedness signedness;
    private final int integerBytes;
    /**
     * The size of the input float operand. We only emit scalar instructions, but UCOMIS wants to be
     * encoded with a packed size.
     */
    private final OperandSize packedSize;
    /** The size of the input float operand. */
    private final OperandSize scalarSize;
    /** Encoding of the target code. Ignored for SSE. */
    private final AMD64SIMDInstructionEncoding encoding;

    @FunctionalInterface
    public interface OpcodeEmitter {
        /** Emit the actual conversion instruction. */
        void emit(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register dst, Register src);
    }

    public AMD64ConvertFloatToIntegerOp(LIRGeneratorTool tool, OpcodeEmitter opcodeEmitter, Value dstValue, Value srcValue, boolean canBeNaN,
                    boolean canOverflow, Signedness signedness) {
        super(TYPE);
        this.opcodeEmitter = opcodeEmitter;
        this.encoding = AMD64SIMDInstructionEncoding.forFeatures(((AMD64) tool.target().arch).getFeatures());
        this.dstValue = dstValue;
        this.srcValue = srcValue;
        this.zeroTmp = needZeroTmp(canOverflow, canBeNaN, signedness, encoding) ? tool.newVariable(srcValue.getValueKind()) : Value.ILLEGAL;
        this.unsignedOverflowTmp = signedness == Signedness.UNSIGNED && canOverflow ? tool.newVariable(srcValue.getValueKind()) : Value.ILLEGAL;
        this.canBeNaN = canBeNaN;
        this.canOverflow = canOverflow;
        this.signedness = signedness;
        this.integerBytes = dstValue.getPlatformKind().getSizeInBytes();
        GraalError.guarantee(integerBytes == 4 || integerBytes == 8, "unexpected target %s", dstValue);
        switch (srcValue.getPlatformKind().getSizeInBytes()) {
            case 4:
                this.packedSize = OperandSize.PS;
                this.scalarSize = OperandSize.SS;
                break;
            case 8:
                this.packedSize = OperandSize.PD;
                this.scalarSize = OperandSize.SD;
                break;
            default:
                throw GraalError.shouldNotReachHere("unexpected input %s".formatted(srcValue));
        }

        GraalError.guarantee(srcValue.getPlatformKind() instanceof AMD64Kind kind && kind.getVectorLength() == 1 && kind.isXMM(), "source must be scalar floating-point: %s", srcValue);
        GraalError.guarantee(dstValue.getPlatformKind() instanceof AMD64Kind kind && kind.getVectorLength() == 1 && kind.isInteger(), "destination must be integer: %s", dstValue);
    }

    private static boolean needZeroTmp(boolean canOverflow, boolean canBeNaN, Signedness signedness, AMD64SIMDInstructionEncoding encoding) {
        return canOverflow || (signedness == Signedness.UNSIGNED && encoding == AMD64SIMDInstructionEncoding.EVEX && (canOverflow || canBeNaN));
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        if (signedness == Signedness.SIGNED) {
            emitSigned(crb, masm);
        } else {
            GraalError.guarantee(signedness == Signedness.UNSIGNED, "signedness must be UNSIGNED: %s", signedness);
            if (masm.getFeatures().contains(AMD64.CPUFeature.AVX512F)) {
                emitUnsignedAVX512(crb, masm);
            } else {
                emitUnsignedSSEAVX(crb, masm);
            }
        }
    }

    private void emitSigned(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register dst = asRegister(dstValue);
        Register src = asRegister(srcValue);
        Label fixupPath = new Label();
        Label done = new Label();

        opcodeEmitter.emit(crb, masm, dst, src);

        if (!canBeNaN && !canOverflow) {
            /* No fixup needed. */
            return;
        }

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
            emitSignedFixups(masm, src, dst, done, canBeNaN);
            masm.jmp(done);
        });

        masm.bind(done);
    }

    /**
     * Emits the necessary fixup cases for the <em>signed</em> case. Should only be called from slow
     * paths. Note the use of the {@code canNowBeNaN} parameter instead of this instance's
     * {@link #canBeNaN} field, as this can be called for an unsigned conversion once we have
     * excluded that the value to be converted can be NaN.
     */
    private void emitSignedFixups(AMD64MacroAssembler masm, Register src, Register dst, Label done, boolean canNowBeNaN) {
        if (canNowBeNaN) {
            /*
             * if (isNaN(src)) { result = 0; goto done; }
             *
             * The isNaN check is implemented as src != src. C2's fixup stubs check for a NaN bit
             * pattern directly, using the same number of cycles but using an extra general purpose
             * register.
             */
            Label isNotNaN = new Label();
            compare(masm, src, src);
            masm.jcc(AMD64Assembler.ConditionFlag.NoParity, isNotNaN, true);
            masm.moveInt(dst, 0);
            masm.jmp(done);
            masm.bind(isNotNaN);
        }

        if (canOverflow) {
            /*
             * if (src > 0.0) { result = MAX_VALUE; }
             *
             * We use an actual floating point compare, C2's stubs check the sign bit in a GPR.
             */
            Register zero = asRegister(zeroTmp);
            clearRegister(masm, zero);
            compare(masm, src, zero);
            masm.jcc(AMD64Assembler.ConditionFlag.BelowEqual, done);
            /*
             * MAX_VALUE is the bitwise negation of MIN_VALUE, which is already in dst. A negation
             * takes the same number of cycles as a move, but its encoding is shorter.
             */
            if (integerBytes == 4) {
                masm.notl(dst);
            } else {
                masm.notq(dst);
            }
        }
    }

    private void emitUnsignedAVX512(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register dst = asRegister(dstValue);
        Register src = asRegister(srcValue);
        Label done = new Label();

        opcodeEmitter.emit(crb, masm, dst, src);

        /*
         * Fixup: if input >= 0, we're done. A positive overflow will have been truncated to the
         * maximum unsigned value. Otherwise (input < 0 or NaN), return 0.
         */
        if (canOverflow || canBeNaN) {
            Register zero = asRegister(zeroTmp);
            clearRegister(masm, zero);
            compare(masm, src, zero);
            masm.jcc(AMD64Assembler.ConditionFlag.AboveEqual, done, true);
            masm.xorq(dst, dst);
            masm.bind(done);
        }
    }

    private void clearRegister(AMD64MacroAssembler masm, Register register) {
        if (masm.isAVX()) {
            VPXOR.encoding(encoding).emit(masm, XMM, register, register, register);
        } else {
            XOR.emit(masm, packedSize, register, register);
        }
    }

    private void compare(AMD64MacroAssembler masm, Register x, Register y) {
        if (masm.isAVX()) {
            var ucomis = scalarSize == OperandSize.SS ? VUCOMISS : VUCOMISD;
            ucomis.encoding(encoding).emit(masm, XMM, x, y);
        } else {
            UCOMIS.emit(masm, packedSize, x, y);
        }
    }

    private void compare(AMD64MacroAssembler masm, Register x, AMD64Address y) {
        if (masm.isAVX()) {
            var comparison = scalarSize == OperandSize.SS ? VUCOMISS : VUCOMISD;
            comparison.encoding(encoding).emit(masm, XMM, x, y);
        } else {
            UCOMIS.emit(masm, packedSize, x, y);
        }
    }

    private void sub(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register dst, Register x, JavaConstant y) {
        GraalError.guarantee(!dst.equals(x), "don't modify input");
        if (masm.isAVX()) {
            VexRVMOp subOp = scalarSize == OperandSize.SS ? VSUBSS : VSUBSD;
            subOp.encoding(encoding).emit(masm, XMM, dst, x, constRef(crb, y));
        } else {
            SSEOp move = scalarSize == OperandSize.SS ? MOVSS : MOVSD;
            move.emit(masm, scalarSize, dst, x);
            SUB.emit(masm, scalarSize, dst, constRef(crb, y));
        }
    }

    private JavaConstant signedUpperBoundValue() {
        return scalarSize == OperandSize.SS ? JavaConstant.forFloat((integerBytes == 4 ? 0x1p31f : 0x1p63f))
                        : JavaConstant.forDouble((integerBytes == 4 ? 0x1p31d : 0x1p63d));
    }

    private AMD64Address constRef(CompilationResultBuilder crb, JavaConstant value) {
        return (AMD64Address) (scalarSize == OperandSize.SS ? crb.asFloatConstRef(value) : crb.asDoubleConstRef(value));
    }

    private void emitUnsignedSSEAVX(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register dst = asRegister(dstValue);
        Register src = asRegister(srcValue);
        Label done = new Label();

        opcodeEmitter.emit(crb, masm, dst, src);
        /*
         * Check for the happiest path: If the result is >= 0, the input was in the range
         * 0..signed_max, and we are done.
         */
        masm.cmpAndJcc(integerBytes == 4 ? OperandSize.DWORD : OperandSize.QWORD, dst, 0, AMD64Assembler.ConditionFlag.GreaterEqual, done, true);
        if (canOverflow) {
            /*-
             * If we're above the signed range, subtract the signed max value + 1 (as a float),
             * convert according to signed semantics, then add the signed max value + 1 back (as an
             * integer). If the value after subtraction is still outside the signed range, the
             * result of the signed conversion will be signed_max, and the addition will take us to
             * unsigned_max as required.
             *
             * if (src >= signed_max_plus_1) {
             *     long signedResult = (long) (src - signed_max_plus_1);
             *     return signedResult + (1 << 63);
             * }
             */
            JavaConstant upperBoundValue = signedUpperBoundValue();
            compare(masm, src, constRef(crb, upperBoundValue));
            Label notAboveSignedMax = new Label();
            masm.jcc(AMD64Assembler.ConditionFlag.Below, notAboveSignedMax);

            Register overflowTmp = asRegister(unsignedOverflowTmp);
            sub(crb, masm, overflowTmp, src, upperBoundValue);
            opcodeEmitter.emit(crb, masm, dst, overflowTmp);
            Label fixupDone = new Label();
            if (integerBytes == 4) {
                masm.cmplAndJcc(dst, Integer.MIN_VALUE, AMD64Assembler.ConditionFlag.NotEqual, fixupDone, true);
            } else {
                masm.cmpq(dst, (AMD64Address) crb.asLongConstRef(JavaConstant.forLong(Long.MIN_VALUE)));
                masm.jcc(AMD64Assembler.ConditionFlag.NotEqual, fixupDone, true);
            }
            emitSignedFixups(masm, overflowTmp, dst, fixupDone, false);
            masm.bind(fixupDone);
            /*
             * Add 0x800..000. This amounts to setting the most significant bit, since the input of
             * the conversion is non-negative.
             */
            masm.btsq(dst, integerBytes * Byte.SIZE - 1);
            masm.jmp(done);

            masm.bind(notAboveSignedMax);
            opcodeEmitter.emit(crb, masm, dst, src);
            /*-
             * else {
             *     // x < signed_max_plus_1 or NaN
             *     long signedResult = (long) x;
             *     return max(result, 0)
             * }
             */
            masm.cmpAndJcc(integerBytes == 4 ? OperandSize.DWORD : OperandSize.QWORD, dst, 0, AMD64Assembler.ConditionFlag.GreaterEqual, done, true);
        }
        /*
         * If we get here, the result of the raw conversion was negative or NaN. In either case we
         * want to truncate to 0.
         */
        masm.xorq(dst, dst);

        masm.bind(done);
    }
}
