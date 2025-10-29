/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.lir.amd64.vector;

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.EvexRMIOp.EVFPCLASSPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.EvexRMIOp.EVFPCLASSPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexFloatCompareOp.EVCMPPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexFloatCompareOp.EVCMPPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexFloatCompareOp.VCMPPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexFloatCompareOp.VCMPPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.EVMOVDQU32;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.EVMOVDQU64;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMIOp.VPERMQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMIOp.VPSHUFD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.EVPTERNLOGD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.EVPTERNLOGQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPCMPEQD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPCMPEQQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPCMPEQD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVROp.KANDNW;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.EVEXPrefixConfig;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.NumUtil.Signedness;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorUnary.FloatPointClassTestOp;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

/**
 * Floating point to integer conversion according to Java semantics. This wraps an AMD64 conversion
 * instruction and adjusts its result as needed. According to Java semantics, NaN inputs should be
 * mapped to 0, and values outside the integer type's range should be mapped to {@code MIN_VALUE} or
 * {@code MAX_VALUE} according to the input's sign. The AMD64 instructions produce {@code MIN_VALUE}
 * for NaNs and all values outside the integer type's range. So we need to fix up the result for
 * NaNs and positive overflowing values. Negative overflowing values keep {@code MIN_VALUE}.
 * </p>
 *
 * AVX1 is not supported. On AVX2, only conversions to {@code int} but not {@code long} are
 * available.
 * </p>
 *
 * Unsigned mode ({@link jdk.graal.compiler.lir.amd64.AMD64ConvertFloatToIntegerOp}) is currently
 * not supported.
 */
public class AMD64VectorConvertFloatToIntegerOp extends AMD64VectorInstruction {
    public static final LIRInstructionClass<AMD64VectorConvertFloatToIntegerOp> TYPE = LIRInstructionClass.create(AMD64VectorConvertFloatToIntegerOp.class);

    @Def({REG}) protected Value dstValue;
    @Alive({REG}) protected Value srcValue;
    /** Mask indicating those vector elements that may need to be fixed to match Java semantics. */
    @Temp({REG, ILLEGAL}) protected Value badElementMaskValue;
    /** Mask used for the result of various intermediate operations. */
    @Temp({REG, ILLEGAL}) protected Value compareMaskValue;

    private final OpcodeEmitter emitter;
    private final boolean canBeNaN;
    private final boolean canOverflow;

    @FunctionalInterface
    public interface OpcodeEmitter {
        /** Emit the actual conversion instruction. */
        void emit(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register dst, Register src);
    }

    public AMD64VectorConvertFloatToIntegerOp(LIRGeneratorTool tool, OpcodeEmitter emitter, AVXKind.AVXSize size, Value dstValue, Value srcValue, boolean canBeNaN, boolean canOverflow,
                    Signedness signedness) {
        super(TYPE, size);
        this.dstValue = dstValue;
        this.srcValue = srcValue;
        this.emitter = emitter;
        if (canBeNaN || canOverflow) {
            AMD64Kind maskKind;
            if (((AMD64) tool.target().arch).getFeatures().contains(AMD64.CPUFeature.AVX512F)) {
                GraalError.guarantee(Math.max(dstValue.getPlatformKind().getVectorLength(), srcValue.getPlatformKind().getVectorLength()) <= 16, "expect at most 16-element vectors");
                maskKind = AMD64Kind.MASK16;
            } else {
                maskKind = AVXKind.getAVXKind(AMD64Kind.BYTE, Math.max(srcValue.getPlatformKind().getSizeInBytes(), dstValue.getPlatformKind().getSizeInBytes()));
            }
            this.badElementMaskValue = tool.newVariable(LIRKind.value(maskKind));
            this.compareMaskValue = tool.newVariable(LIRKind.value(maskKind));
        } else {
            this.badElementMaskValue = Value.ILLEGAL;
            this.compareMaskValue = Value.ILLEGAL;
        }
        this.canBeNaN = canBeNaN;
        this.canOverflow = canOverflow;

        GraalError.guarantee(signedness == Signedness.SIGNED, "only signed vector float-to-integer conversions are supported");
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        if (masm.getFeatures().contains(AMD64.CPUFeature.AVX512F)) {
            GraalError.guarantee(masm.supportsFullAVX512(), "expect full AVX-512 support");
            emitAVX512(crb, masm);
        } else {
            emitAVX2(crb, masm);
        }
    }

    private void emitAVX512(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        AMD64Kind srcKind = (AMD64Kind) srcValue.getPlatformKind();
        AVXKind.AVXSize srcSize = AVXKind.getRegisterSize(srcKind);
        AMD64Assembler.VexFloatCompareOp floatCompare = srcKind.getScalar().equals(AMD64Kind.DOUBLE) ? EVCMPPD : EVCMPPS;
        AMD64Assembler.EvexRMIOp floatClassify = srcKind.getScalar().equals(AMD64Kind.DOUBLE) ? EVFPCLASSPD : EVFPCLASSPS;
        AMD64Kind dstKind = (AMD64Kind) dstValue.getPlatformKind();
        AVXKind.AVXSize dstSize = AVXKind.getRegisterSize(dstKind);
        AMD64Assembler.VexRVMOp integerEquals = dstKind.getScalar().equals(AMD64Kind.QWORD) ? EVPCMPEQQ : EVPCMPEQD;
        AMD64Assembler.VexMoveOp integerMove = dstKind.getScalar().equals(AMD64Kind.QWORD) ? EVMOVDQU64 : EVMOVDQU32;
        AMD64Assembler.VexRVMIOp ternlog = dstKind.getScalar().equals(AMD64Kind.QWORD) ? EVPTERNLOGQ : EVPTERNLOGD;

        Register dst = asRegister(dstValue);
        Register src = asRegister(srcValue);

        emitter.emit(crb, masm, dst, src);

        if (!canBeNaN && !canOverflow) {
            /* No fixup needed. */
            return;
        }

        Register badElementMask = asRegister(badElementMaskValue);
        Register compareMask = asRegister(compareMaskValue);
        Label done = new Label();

        /* badElementMask = (dst == MIN_VALUE); (element-wise) */
        AMD64Address minValueVector = minValueVector(crb, dstKind);
        integerEquals.emit(masm, dstSize, badElementMask, dst, minValueVector);
        /* if (!anySet(badElementMask)) { goto done; } */
        masm.ktestw(badElementMask, badElementMask);
        masm.jcc(AMD64Assembler.ConditionFlag.Equal, done, true);

        if (canBeNaN) {
            /* compareMask = !isNaN(src); (element-wise) */
            floatCompare.emit(masm, srcSize, compareMask, src, src, AMD64Assembler.VexFloatCompareOp.Predicate.ORD_Q);
            /* Zero all elements where compareMask is 0, i.e., all elements where src is NaN. */
            integerMove.emit(masm, dstSize, dst, dst, compareMask, EVEXPrefixConfig.Z1, EVEXPrefixConfig.B0);
        }

        if (canOverflow) {
            /* compareMask = !(src >= 0.0); (element-wise) */
            int anyNaN = FloatPointClassTestOp.QUIET_NAN | FloatPointClassTestOp.SIG_NAN;
            int anyNegative = FloatPointClassTestOp.FIN_NEG | FloatPointClassTestOp.NEG_INF | FloatPointClassTestOp.NEG_ZERO;
            floatClassify.emit(masm, srcSize, compareMask, src, anyNaN | anyNegative);
            /* compareMask = (src >= 0.0) & badElement (element-wise) */
            KANDNW.emit(masm, compareMask, compareMask, badElementMask);
            /*
             * Now the compareMask marks just the positive overflown elements. They are MIN_VALUE,
             * we want them to be MAX_VALUE. This is bitwise negation.
             */
            int ternlogNotA = 0x0F;  // Intel SDM, Table 5-1
            ternlog.emit(masm, dstSize, dst, dst, dst, compareMask, ternlogNotA);
        }

        masm.bind(done);
    }

    private void emitAVX2(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        AMD64Kind srcKind = (AMD64Kind) srcValue.getPlatformKind();
        AVXKind.AVXSize srcSize = AVXKind.getRegisterSize(srcKind);
        AMD64Assembler.VexFloatCompareOp floatCompare = srcKind.getScalar().equals(AMD64Kind.DOUBLE) ? VCMPPD : VCMPPS;
        AMD64Kind dstKind = (AMD64Kind) dstValue.getPlatformKind();
        GraalError.guarantee(dstKind.getScalar().equals(AMD64Kind.DWORD), "only expect conversions to int on AVX 2: %s", dstKind);
        AVXKind.AVXSize dstSize = AVXKind.getRegisterSize(dstKind);
        AMD64Assembler.VexRVMOp integerEquals = VPCMPEQD;

        Register dst = asRegister(dstValue);
        Register src = asRegister(srcValue);

        emitter.emit(crb, masm, dst, src);

        if (!canBeNaN && !canOverflow) {
            /* No fixup needed. */
            return;
        }

        Register badElementMask = asRegister(badElementMaskValue);
        Register compareMask = asRegister(compareMaskValue);
        Label done = new Label();

        /* badElementMask = (dst == MIN_VALUE); (element-wise) */
        AMD64Address minValueVector = minValueVector(crb, dstKind);
        integerEquals.emit(masm, dstSize, badElementMask, dst, minValueVector);
        /* if (!anySet(badElementMask)) { goto done; } */
        masm.vptest(badElementMask, badElementMask, dstSize);
        masm.jcc(AMD64Assembler.ConditionFlag.Equal, done, true);

        if (canBeNaN) {
            /* compareMask = !isNaN(src); (element-wise) */
            floatCompare.emit(masm, srcSize, compareMask, src, src, AMD64Assembler.VexFloatCompareOp.Predicate.ORD_Q);
            convertAvx2Mask(masm, compareMask, srcKind, dstKind);
            /* Zero all elements where compareMask is 0, i.e., all elements where src is NaN. */
            masm.vpand(dst, dst, compareMask, dstSize);
        }

        if (canOverflow) {
            /* compareMask = (0.0 <= src); (element-wise) */
            masm.vpxor(compareMask, compareMask, compareMask, srcSize);
            floatCompare.emit(masm, srcSize, compareMask, compareMask, src, AMD64Assembler.VexFloatCompareOp.Predicate.LE_OS);
            convertAvx2Mask(masm, compareMask, srcKind, dstKind);
            /*
             * Negate bitwise all elements that are bad and where the source value is positive and
             * not NaN (i.e., compareMask is set). Bitwise negation will flip these elements from
             * MIN_VALUE to MAX_VALUE as required.
             */
            masm.vpand(compareMask, compareMask, badElementMask, dstSize);
            masm.vpxor(dst, dst, compareMask, dstSize);
        }

        masm.bind(done);
    }

    /**
     * Returns the address of a constant of vector kind {@code dstKind} where each element is the
     * minimal value for the underlying scalar kind. For example, if {@code dstKind} is the kind
     * representing a vector of 4 {@code int}s, then the result will be a 4 * 4 byte constant
     * containing the bytes {@code 0x00, 0x00, 0x00, 0x80} ({@link Integer#MIN_VALUE} in a
     * little-endian representation) four times.
     */
    private static AMD64Address minValueVector(CompilationResultBuilder crb, AMD64Kind dstKind) {
        byte[] minValueBytes = new byte[dstKind.getSizeInBytes()];
        int elementBytes = dstKind.getScalar().getSizeInBytes();
        GraalError.guarantee(dstKind.getScalar().isInteger() && (elementBytes == Integer.BYTES || elementBytes == Long.BYTES), "unexpected destination: %s", dstKind);
        ByteBuffer buffer = ByteBuffer.wrap(minValueBytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < dstKind.getVectorLength(); i++) {
            if (elementBytes == Integer.BYTES) {
                buffer.putInt(Integer.MIN_VALUE);
            } else {
                buffer.putLong(Long.MIN_VALUE);
            }
        }
        int alignment = crb.dataBuilder.ensureValidDataAlignment(minValueBytes.length);
        return (AMD64Address) crb.recordDataReferenceInCode(minValueBytes, alignment);
    }

    /**
     * If the {@code fromKind}'s element kind is larger than the {@code toKind}'s element kind,
     * narrow the mask in {@code maskRegister} from the wider size to the narrower one. The
     * conversion is done in place.
     */
    private static void convertAvx2Mask(AMD64MacroAssembler masm, Register maskRegister, AMD64Kind fromKind, AMD64Kind toKind) {
        GraalError.guarantee(fromKind.getVectorLength() == toKind.getVectorLength(), "vector length mismatch: %s, %s", fromKind, toKind);
        int fromBytes = fromKind.getScalar().getSizeInBytes();
        int toBytes = toKind.getScalar().getSizeInBytes();
        GraalError.guarantee((fromBytes == Integer.BYTES || fromBytes == Long.BYTES) && toBytes == Integer.BYTES, "unexpected sizes: %s, %s", fromKind, toKind);
        if (fromBytes > toBytes) {
            AVXKind.AVXSize shuffleSize = AVXKind.getRegisterSize(fromKind);
            /* Narrow using shuffles. */
            VPSHUFD.emit(masm, shuffleSize, maskRegister, maskRegister, 0x08);
            if (shuffleSize == AVXKind.AVXSize.YMM) {
                VPERMQ.emit(masm, shuffleSize, maskRegister, maskRegister, 0x08);
            }
        }
    }
}
