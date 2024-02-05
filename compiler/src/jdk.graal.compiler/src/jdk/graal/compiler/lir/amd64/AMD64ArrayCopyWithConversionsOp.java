/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.amd64.AMD64.r8;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isIllegal;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.XMM;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.YMM;

import java.util.Arrays;
import java.util.EnumSet;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMIOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.common.StrideUtil;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

/**
 * Arraycopy operation for arbitrary source and destination arrays, with arbitrary byte offset, with
 * support for arbitrary compression and inflation of {@link Stride#S1 8 bit}, {@link Stride#S2 16
 * bit} or {@link Stride#S4 32 bit} array elements.
 * <p>
 * CAUTION: the compression implementation assumes that the upper bytes of {@code char}/{@code int}
 * values to be compressed are zero. If this assumption is broken, compression will yield incorrect
 * results!
 */
@Opcode("ARRAY_COPY_WITH_CONVERSIONS")
public final class AMD64ArrayCopyWithConversionsOp extends AMD64ComplexVectorOp {
    public static final LIRInstructionClass<AMD64ArrayCopyWithConversionsOp> TYPE = LIRInstructionClass.create(AMD64ArrayCopyWithConversionsOp.class);

    private enum Op {
        compressCharToByte,
        compressIntToChar,
        compressIntToByte,
        inflateByteToChar,
        inflateByteToInt,
        inflateCharToInt,
        copy
    }

    private static final Register REG_ARRAY_SRC = rsi;
    private static final Register REG_OFFSET_SRC = rax;
    private static final Register REG_ARRAY_DST = rdi;
    private static final Register REG_OFFSET_DST = rcx;
    private static final Register REG_LENGTH = rdx;
    private static final Register REG_STRIDE = r8;

    private final Stride strideSrcConst;
    private final Stride strideDstConst;
    private final AMD64MacroAssembler.ExtendMode extendMode;

    @Use({OperandFlag.REG}) private Value arraySrc;
    @Use({OperandFlag.REG}) private Value offsetSrc;
    @Use({OperandFlag.REG}) private Value arrayDst;
    @Use({OperandFlag.REG}) private Value offsetDst;
    @Use({OperandFlag.REG}) private Value length;
    @Use({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value dynamicStrides;

    @Temp({OperandFlag.REG}) private Value arraySrcTmp;
    @Temp({OperandFlag.REG}) private Value offsetSrcTmp;
    @Temp({OperandFlag.REG}) private Value arrayDstTmp;
    @Temp({OperandFlag.REG}) private Value offsetDstTmp;
    @Temp({OperandFlag.REG}) private Value lengthTmp;
    @Temp({OperandFlag.REG, OperandFlag.ILLEGAL}) private Value dynamicStridesTmp;

    @Temp({OperandFlag.REG}) private Value[] vectorTemp;

    /**
     * Arraycopy operation for arbitrary source and destination arrays, with arbitrary byte offset,
     * with support for arbitrary compression and inflation of {@link JavaKind#Byte 8 bit},
     * {@link JavaKind#Char 16 bit} or {@link JavaKind#Int 32 bit} array elements.
     *
     * @param strideSrc source stride. May be {@link JavaKind#Byte 8 bit}, {@link JavaKind#Char 16
     *            bit} or {@link JavaKind#Int 32 bit}.
     * @param strideDst target stride. May be {@link JavaKind#Byte 8 bit}, {@link JavaKind#Char 16
     *            bit} or {@link JavaKind#Int 32 bit}.
     * @param arraySrc source array.
     * @param offsetSrc offset to be added to arraySrc, in bytes. Must include array base offset!
     * @param arrayDst destination array.
     * @param offsetDst offset to be added to arrayDst, in bytes. Must include array base offset!
     * @param length length of the region to copy, scaled to strideDst.
     * @param extendMode sign- or zero-extend array elements when inflating to a bigger stride.
     * @param dynamicStrides dynamic stride dispatch as described in {@link StrideUtil}.
     */
    private AMD64ArrayCopyWithConversionsOp(LIRGeneratorTool tool, Stride strideSrc, Stride strideDst, EnumSet<CPUFeature> runtimeCheckedCPUFeatures,
                    Value arraySrc, Value offsetSrc, Value arrayDst, Value offsetDst, Value length, Value dynamicStrides, AMD64MacroAssembler.ExtendMode extendMode) {
        super(TYPE, tool, runtimeCheckedCPUFeatures, YMM);
        this.extendMode = extendMode;

        GraalError.guarantee(supports(tool.target(), runtimeCheckedCPUFeatures, CPUFeature.SSE2), "needs at least SSE2 support");

        this.arraySrcTmp = this.arraySrc = arraySrc;
        this.offsetSrcTmp = this.offsetSrc = offsetSrc;
        this.arrayDstTmp = this.arrayDst = arrayDst;
        this.offsetDstTmp = this.offsetDst = offsetDst;
        this.lengthTmp = this.length = length;
        this.dynamicStridesTmp = this.dynamicStrides = dynamicStrides;

        if (StrideUtil.useConstantStrides(dynamicStrides)) {
            this.strideSrcConst = strideSrc;
            this.strideDstConst = strideDst;
            this.vectorTemp = new Value[getNumberOfRequiredVectorRegisters(getOp(strideDstConst, strideSrcConst))];
        } else {
            strideSrcConst = null;
            strideDstConst = null;
            this.vectorTemp = new Value[5];
        }
        for (int i = 0; i < vectorTemp.length; i++) {
            vectorTemp[i] = tool.newVariable(LIRKind.value(getVectorKind(JavaKind.Byte)));
        }
    }

    public static AMD64ArrayCopyWithConversionsOp movParamsAndCreate(LIRGeneratorTool tool, Stride strideSrc, Stride strideDst,
                    EnumSet<CPUFeature> runtimeCheckedCPUFeatures, Value arraySrc, Value offsetSrc, Value arrayDst, Value offsetDst, Value length, AMD64MacroAssembler.ExtendMode extendMode) {
        return movParamsAndCreate(tool, strideSrc, strideDst, runtimeCheckedCPUFeatures, arraySrc, offsetSrc, arrayDst, offsetDst, length, Value.ILLEGAL, extendMode);
    }

    public static AMD64ArrayCopyWithConversionsOp movParamsAndCreate(LIRGeneratorTool tool, EnumSet<CPUFeature> runtimeCheckedCPUFeatures,
                    Value arraySrc, Value offsetSrc, Value arrayDst, Value offsetDst, Value length, Value stride, AMD64MacroAssembler.ExtendMode extendMode) {
        return movParamsAndCreate(tool, null, null, runtimeCheckedCPUFeatures, arraySrc, offsetSrc, arrayDst, offsetDst, length, stride, extendMode);
    }

    private static AMD64ArrayCopyWithConversionsOp movParamsAndCreate(LIRGeneratorTool tool, Stride strideSrc, Stride strideDst,
                    EnumSet<CPUFeature> runtimeCheckedCPUFeatures, Value arraySrc, Value offsetSrc, Value arrayDst, Value offsetDst, Value length, Value dynamicStrides,
                    AMD64MacroAssembler.ExtendMode extendMode) {
        RegisterValue regArraySrc = REG_ARRAY_SRC.asValue(arraySrc.getValueKind());
        RegisterValue regOffsetSrc = REG_OFFSET_SRC.asValue(offsetSrc.getValueKind());
        RegisterValue regArrayDst = REG_ARRAY_DST.asValue(arrayDst.getValueKind());
        RegisterValue regOffsetDst = REG_OFFSET_DST.asValue(offsetDst.getValueKind());
        RegisterValue regLength = REG_LENGTH.asValue(length.getValueKind());
        final Value regDynamicStrides;
        tool.emitConvertNullToZero(regArraySrc, arraySrc);
        tool.emitMove(regOffsetSrc, offsetSrc);
        tool.emitConvertNullToZero(regArrayDst, arrayDst);
        tool.emitMove(regOffsetDst, offsetDst);
        tool.emitMove(regLength, length);
        if (isIllegal(dynamicStrides)) {
            regDynamicStrides = Value.ILLEGAL;
        } else {
            regDynamicStrides = REG_STRIDE.asValue(dynamicStrides.getValueKind());
            tool.emitMove((RegisterValue) regDynamicStrides, dynamicStrides);
        }
        return new AMD64ArrayCopyWithConversionsOp(tool, strideSrc, strideDst, runtimeCheckedCPUFeatures, regArraySrc, regOffsetSrc, regArrayDst, regOffsetDst, regLength, regDynamicStrides,
                        extendMode);
    }

    private static Op getOp(Stride strideDst, Stride strideSrc) {
        if (strideDst.value == strideSrc.value) {
            return Op.copy;
        }
        if (strideDst.value < strideSrc.value) {
            switch (strideSrc) {
                case S2:
                    assert strideDst == Stride.S1 : strideDst;
                    return Op.compressCharToByte;
                case S4:
                    switch (strideDst) {
                        case S1:
                            return Op.compressIntToByte;
                        case S2:
                            return Op.compressIntToChar;
                        default:
                            throw new UnsupportedOperationException();
                    }
                default:
                    throw new UnsupportedOperationException();
            }
        }
        switch (strideSrc) {
            case S1:
                switch (strideDst) {
                    case S2:
                        return Op.inflateByteToChar;
                    case S4:
                        return Op.inflateByteToInt;
                    default:
                        throw new UnsupportedOperationException();
                }
            case S2:
                assert strideDst == Stride.S4 : strideDst;
                return Op.inflateCharToInt;
            default:
                throw new UnsupportedOperationException();
        }
    }

    private static int getNumberOfRequiredVectorRegisters(Op op) {
        switch (op) {
            case compressCharToByte:
            case compressIntToChar:
                return 2;
            case compressIntToByte:
                return 5;
            default:
                return 1;
        }
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler asm) {
        Register src = asRegister(arraySrc);
        Register sro = asRegister(offsetSrc);
        Register dst = asRegister(arrayDst);
        Register dso = asRegister(offsetDst);
        Register len = asRegister(length);

        asm.leaq(src, new AMD64Address(src, sro, Stride.S1));
        asm.leaq(dst, new AMD64Address(dst, dso, Stride.S1));

        if (isIllegal(dynamicStrides)) {
            emitOp(crb, asm, this.strideSrcConst, this.strideDstConst, src, sro, dst, len);
        } else {
            Label[] variants = new Label[9];
            Label end = new Label();
            for (int i = 0; i < variants.length; i++) {
                variants[i] = new Label();
            }
            AMD64ControlFlow.RangeTableSwitchOp.emitJumpTable(crb, asm, dso, asRegister(dynamicStrides), 0, 8, Arrays.stream(variants));

            // use the 1-byte-1-byte stride variant for the 2-2 and 4-4 cases by simply shifting the
            // length
            asm.align(preferredBranchTargetAlignment(crb));
            asm.bind(variants[StrideUtil.getDirectStubCallIndex(Stride.S4, Stride.S4)]);
            asm.maybeEmitIndirectTargetMarker();
            asm.shll(len, 1);
            asm.align(preferredBranchTargetAlignment(crb));
            asm.bind(variants[StrideUtil.getDirectStubCallIndex(Stride.S2, Stride.S2)]);
            asm.maybeEmitIndirectTargetMarker();
            asm.shll(len, 1);
            asm.align(preferredBranchTargetAlignment(crb));
            asm.bind(variants[StrideUtil.getDirectStubCallIndex(Stride.S1, Stride.S1)]);
            asm.maybeEmitIndirectTargetMarker();
            emitOp(crb, asm, Stride.S1, Stride.S1, src, sro, dst, len);
            asm.jmp(end);

            for (Stride strideSrc : new Stride[]{Stride.S1, Stride.S2, Stride.S4}) {
                for (Stride strideDst : new Stride[]{Stride.S1, Stride.S2, Stride.S4}) {
                    if (strideSrc == strideDst) {
                        continue;
                    }
                    asm.align(preferredBranchTargetAlignment(crb));
                    asm.bind(variants[StrideUtil.getDirectStubCallIndex(strideSrc, strideDst)]);
                    asm.maybeEmitIndirectTargetMarker();
                    emitOp(crb, asm, strideSrc, strideDst, src, sro, dst, len);
                    asm.jmp(end);
                }
            }
            asm.bind(end);
        }
    }

    private void emitOp(CompilationResultBuilder crb, AMD64MacroAssembler asm, Stride strideSrc, Stride strideDst, Register src, Register sro, Register dst, Register len) {
        if (strideSrc.value < strideDst.value) {
            emitInflate(crb, asm, strideSrc, strideDst, src, dst, len, sro);
        } else if (strideSrc.value == strideDst.value) {
            emitCopy(crb, asm, strideSrc, strideDst, src, dst, len, sro);
        } else {
            emitCompress(crb, asm, strideSrc, strideDst, src, dst, len, sro);
        }
    }

    /**
     * Compress array {@code src} into array {@code dst} using {@code PACKUSWB}/{@code PACKUSDW}.
     * CAUTION: This operation assumes that the upper bytes (which are remove by the compression) of
     * {@code src} are zero. Breaking this assumption will lead to incorrect results.
     */
    private void emitCompress(CompilationResultBuilder crb, AMD64MacroAssembler asm,
                    Stride strideSrc,
                    Stride strideDst,
                    Register src,
                    Register dst,
                    Register len,
                    Register tmp) {
        Op op = getOp(strideDst, strideSrc);
        Label labelScalarLoop = new Label();
        Label labelDone = new Label();

        if (asm.supports(CPUFeature.SSE4_2)) {
            Label labelPackVectorLoop = new Label();
            Label labelPack16Bytes = new Label();
            Label labelPack8Bytes = new Label();
            Label labelCopyTail = new Label();

            int vectorLength = vectorSize.getBytes() / strideDst.value;

            asm.movl(tmp, len);

            // tmp = tail count (in strideDst)
            asm.andl(tmp, vectorLength - 1);
            // len = vector count (in strideDst)
            asm.andlAndJcc(len, -vectorLength, ConditionFlag.Zero, supportsAVX2AndYMM() ? labelPack16Bytes : labelPack8Bytes, false);

            if (supportsAVX2AndYMM() && op == Op.compressIntToByte) {
                loadMask(crb, asm, asRegister(vectorTemp[4]), getAVX2IntToBytePackingUnscrambleMap());
            }

            asm.leaq(src, new AMD64Address(src, len, strideSrc));
            asm.leaq(dst, new AMD64Address(dst, len, strideDst));
            asm.negq(len);

            // vectorized loop
            asm.align(preferredLoopAlignment(crb));
            asm.bind(labelPackVectorLoop);
            packVector(asm, vectorSize, op, strideSrc, strideDst, src, dst, len, 0, false);
            asm.addqAndJcc(len, vectorLength, ConditionFlag.NotZero, labelPackVectorLoop, true);

            // vectorized tail
            packVector(asm, vectorSize, op, strideSrc, strideDst, src, dst, tmp, -vectorSize.getBytes(), false);
            asm.jmp(labelDone);

            if (supportsAVX2AndYMM()) {
                asm.bind(labelPack16Bytes);
                int vectorSizeXMM = XMM.getBytes() / strideDst.value;
                asm.cmplAndJcc(tmp, vectorSizeXMM, ConditionFlag.Less, labelPack8Bytes, true);
                packVector(asm, XMM, op, strideSrc, strideDst, src, dst, len, 0, true);
                packVector(asm, XMM, op, strideSrc, strideDst, src, dst, tmp, -XMM.getBytes(), false);
                asm.jmpb(labelDone);
            }

            asm.bind(labelPack8Bytes);
            int vectorSizeQ = 8 / strideDst.value;
            asm.cmplAndJcc(tmp, vectorSizeQ, ConditionFlag.Less, labelCopyTail, true);

            // array is too small for vectorized loop, use half vectors
            // pack aligned to beginning
            pack8Bytes(asm, op, strideSrc, strideDst, src, dst, len, 0, true);
            // pack aligned to end
            pack8Bytes(asm, op, strideSrc, strideDst, src, dst, tmp, -8, false);
            asm.jmpb(labelDone);

            asm.bind(labelCopyTail);
            asm.movl(len, tmp);
        }

        asm.testlAndJcc(len, len, ConditionFlag.Zero, labelDone, true);
        asm.leaq(src, new AMD64Address(src, len, strideSrc));
        asm.leaq(dst, new AMD64Address(dst, len, strideDst));
        asm.negq(len);

        // scalar loop
        asm.bind(labelScalarLoop);
        switch (op) {
            case compressCharToByte:
                asm.movzwl(tmp, new AMD64Address(src, len, strideSrc));
                asm.movb(new AMD64Address(dst, len, strideDst), tmp);
                break;
            case compressIntToChar:
                asm.movl(tmp, new AMD64Address(src, len, strideSrc));
                asm.movw(new AMD64Address(dst, len, strideDst), tmp);
                break;
            case compressIntToByte:
                asm.movl(tmp, new AMD64Address(src, len, strideSrc));
                asm.movb(new AMD64Address(dst, len, strideDst), tmp);
                break;
        }
        asm.incqAndJcc(len, ConditionFlag.NotZero, labelScalarLoop, true);
        asm.bind(labelDone);
    }

    private void packVector(AMD64MacroAssembler asm, AVXSize vecSize, Op op, Stride strideSrc, Stride strideDst, Register src, Register dst, Register index, int displacement, boolean direct) {
        int displacementSrc = displacement << strideSrc.log2 - strideDst.log2;
        Register vec1 = asRegister(vectorTemp[0]);
        Register vec2 = asRegister(vectorTemp[1]);
        asm.movdqu(vecSize, vec1, indexAddressOrDirect(strideSrc, src, index, displacementSrc, 0, direct));
        asm.movdqu(vecSize, vec2, indexAddressOrDirect(strideSrc, src, index, displacementSrc, vecSize.getBytes(), direct));
        switch (op) {
            case compressCharToByte:
                asm.packuswb(vecSize, vec1, vec2);
                break;
            case compressIntToChar:
                asm.packusdw(vecSize, vec1, vec2);
                break;
            case compressIntToByte:
                Register vec3 = asRegister(vectorTemp[2]);
                Register vec4 = asRegister(vectorTemp[3]);
                asm.movdqu(vecSize, vec3, indexAddressOrDirect(strideSrc, src, index, displacementSrc, vecSize.getBytes() * 2, direct));
                asm.movdqu(vecSize, vec4, indexAddressOrDirect(strideSrc, src, index, displacementSrc, vecSize.getBytes() * 3, direct));
                asm.packusdw(vecSize, vec1, vec2);
                asm.packusdw(vecSize, vec3, vec4);
                asm.packuswb(vecSize, vec1, vec3);
                break;
        }
        if (vecSize == YMM) {
            if (op == Op.compressIntToByte) {
                VexRVMOp.VPERMD.emit(asm, vecSize, vec1, asRegister(vectorTemp[4]), vec1);
            } else {
                VexRMIOp.VPERMQ.emit(asm, vecSize, vec1, vec1, 0b11011000);
            }
        }
        asm.movdqu(vecSize, new AMD64Address(dst, index, strideDst, displacement), vec1);
    }

    private void pack8Bytes(AMD64MacroAssembler masm, Op op, Stride strideSrc, Stride strideDst, Register src, Register dst, Register index, int displacement, boolean direct) {
        Register vec1 = asRegister(vectorTemp[0]);
        Register vec2 = asRegister(vectorTemp[1]);
        int displacementSrc = displacement << strideSrc.log2 - strideDst.log2;
        masm.movdqu(vec1, indexAddressOrDirect(strideSrc, src, index, displacementSrc, 0, direct));
        switch (op) {
            case compressCharToByte:
                masm.pxor(vec2, vec2);
                masm.packuswb(vec1, vec2);
                break;
            case compressIntToChar:
                masm.pxor(vec2, vec2);
                masm.packusdw(vec1, vec2);
                break;
            case compressIntToByte:
                masm.movdqu(vec2, indexAddressOrDirect(strideSrc, src, index, displacementSrc, 16, direct));
                masm.packusdw(vec1, vec2);
                masm.packuswb(vec1, vec2);
                break;
        }
        masm.movq(new AMD64Address(dst, index, strideDst, displacement), vec1);
    }

    private static AMD64Address indexAddressOrDirect(Stride strideSrc, Register array, Register index, int baseOffset, int displacement, boolean direct) {
        return direct ? new AMD64Address(array, displacement) : new AMD64Address(array, index, strideSrc, baseOffset + displacement);
    }

    private void emitInflate(CompilationResultBuilder crb, AMD64MacroAssembler asm,
                    Stride strideSrc,
                    Stride strideDst,
                    Register src,
                    Register dst,
                    Register len,
                    Register tmp) {
        Op op = getOp(strideDst, strideSrc);
        Register vec = asRegister(vectorTemp[0]);
        Label labelScalarLoop = new Label();
        Label labelDone = new Label();

        asm.movl(tmp, len);

        if (asm.supports(CPUFeature.SSE4_2)) {
            Label labelMainLoop = new Label();
            Label labelSkipXMMHalf = new Label();
            Label labelXMMTail = new Label();
            Label labelTail = new Label();

            int vectorLength = vectorSize.getBytes() / strideDst.value;
            int vectorLengthXMM = XMM.getBytes() / strideDst.value;

            int scaleDelta = strideDst.log2 - strideSrc.log2;

            asm.andl(tmp, vectorLength - 1);
            asm.andlAndJcc(len, -vectorLength, ConditionFlag.Zero, supportsAVX2AndYMM() ? labelXMMTail : labelTail, true);

            asm.leaq(src, new AMD64Address(src, len, strideSrc));
            asm.leaq(dst, new AMD64Address(dst, len, strideDst));
            asm.negq(len);

            // vectorized loop
            asm.align(preferredLoopAlignment(crb));
            asm.bind(labelMainLoop);
            asm.pmovSZx(vectorSize, extendMode, vec, strideDst, new AMD64Address(src, len, strideSrc), strideSrc);
            asm.movdqu(vectorSize, new AMD64Address(dst, len, strideDst), vec);
            asm.addqAndJcc(len, vectorLength, ConditionFlag.NotZero, labelMainLoop, true);

            // vectorized tail
            asm.pmovSZx(vectorSize, extendMode, vec, strideDst, new AMD64Address(src, tmp, strideSrc, -vectorSize.getBytes() >> scaleDelta), strideSrc);
            asm.movdqu(vectorSize, new AMD64Address(dst, tmp, strideDst, -vectorSize.getBytes()), vec);
            asm.jmpb(labelDone);

            if (supportsAVX2AndYMM()) {
                asm.bind(labelXMMTail);
                asm.cmplAndJcc(tmp, vectorLengthXMM, ConditionFlag.Less, labelTail, true);

                // half vector size
                asm.pmovSZx(XMM, extendMode, vec, strideDst, new AMD64Address(src), strideSrc);
                asm.movdqu(new AMD64Address(dst), vec);

                // half vector size tail
                asm.pmovSZx(XMM, extendMode, vec, strideDst, new AMD64Address(src, tmp, strideSrc, -16 >> scaleDelta), strideSrc);
                asm.movdqu(new AMD64Address(dst, tmp, strideDst, -16), vec);
                asm.jmpb(labelDone);
            }

            asm.bind(labelTail);
            asm.movl(len, tmp);

            // xmm half vector size
            if (op != Op.inflateByteToInt) {
                assert scaleDelta == 1 : scaleDelta;
                asm.cmplAndJcc(len, 4 >> strideSrc.log2, ConditionFlag.Less, labelSkipXMMHalf, true);

                asm.movdl(vec, new AMD64Address(src));
                asm.pmovSZx(XMM, extendMode, vec, strideDst, vec, strideSrc);
                asm.movq(new AMD64Address(dst), vec);

                asm.movdl(vec, new AMD64Address(src, len, strideSrc, -4));
                asm.pmovSZx(XMM, extendMode, vec, strideDst, vec, strideSrc);
                asm.movq(new AMD64Address(dst, len, strideDst, -8), vec);
                asm.jmpb(labelDone);
            }

            asm.bind(labelSkipXMMHalf);
        }

        asm.testlAndJcc(len, len, ConditionFlag.Zero, labelDone, true);
        asm.leaq(src, new AMD64Address(src, len, strideSrc));
        asm.leaq(dst, new AMD64Address(dst, len, strideDst));
        asm.negq(len);

        // scalar loop
        asm.bind(labelScalarLoop);
        switch (op) {
            case inflateByteToChar:
                asm.movzbl(tmp, new AMD64Address(src, len, strideSrc));
                asm.movw(new AMD64Address(dst, len, strideDst), tmp);
                break;
            case inflateByteToInt:
                asm.movzbl(tmp, new AMD64Address(src, len, strideSrc));
                asm.movl(new AMD64Address(dst, len, strideDst), tmp);
                break;
            case inflateCharToInt:
                asm.movzwl(tmp, new AMD64Address(src, len, strideSrc));
                asm.movl(new AMD64Address(dst, len, strideDst), tmp);
                break;
        }
        asm.incqAndJcc(len, ConditionFlag.NotZero, labelScalarLoop, true);

        asm.bind(labelDone);
    }

    private void emitCopy(CompilationResultBuilder crb, AMD64MacroAssembler masm,
                    Stride strideSrc,
                    Stride strideDst,
                    Register src,
                    Register dst,
                    Register len,
                    Register tmp) {
        Register vec = asRegister(vectorTemp[0]);
        Label labelTailXMM = new Label();
        Label labelTailQWORD = new Label();
        Label labelTailDWORD = new Label();
        Label labelTailWORD = new Label();
        Label labelTailBYTE = new Label();
        Label labelDone = new Label();

        int vectorLength = vectorSize.getBytes() / strideDst.value;

        masm.movl(tmp, len);

        masm.andl(tmp, vectorLength - 1);
        masm.andlAndJcc(len, -vectorLength, ConditionFlag.Zero, supportsAVX2AndYMM() ? labelTailXMM : labelTailQWORD, true);

        masm.leaq(src, new AMD64Address(src, len, strideSrc));
        masm.leaq(dst, new AMD64Address(dst, len, strideDst));
        masm.negq(len);

        Label labelYMMLoop = new Label();
        Label labelXMMLoop = new Label();

        if (supportsAVX2AndYMM()) {
            // vectorized loop
            masm.align(preferredLoopAlignment(crb));
            masm.bind(labelYMMLoop);
            masm.vmovdqu(vec, new AMD64Address(src, len, strideSrc));
            masm.vmovdqu(new AMD64Address(dst, len, strideDst), vec);
            masm.addqAndJcc(len, vectorLength, ConditionFlag.NotZero, labelYMMLoop, true);

            // vectorized tail
            masm.vmovdqu(vec, new AMD64Address(src, tmp, strideSrc, -32));
            masm.vmovdqu(new AMD64Address(dst, tmp, strideDst, -32), vec);
            masm.jmpb(labelDone);

            // half vector size
            masm.bind(labelTailXMM);
            masm.cmplAndJcc(tmp, 16 / strideDst.value, ConditionFlag.Less, labelTailQWORD, true);
            masm.movdqu(vec, new AMD64Address(src));
            masm.movdqu(new AMD64Address(dst), vec);

            // half vector size tail
            masm.movdqu(vec, new AMD64Address(src, tmp, strideSrc, -16));
            masm.movdqu(new AMD64Address(dst, tmp, strideDst, -16), vec);
            masm.jmpb(labelDone);

        } else {
            // xmm vectorized loop
            masm.align(preferredLoopAlignment(crb));
            masm.bind(labelXMMLoop);
            masm.movdqu(vec, new AMD64Address(src, len, strideSrc));
            masm.movdqu(new AMD64Address(dst, len, strideDst), vec);
            masm.addqAndJcc(len, vectorLength, ConditionFlag.NotZero, labelXMMLoop, true);

            // xmm vectorized tail
            masm.movdqu(vec, new AMD64Address(src, tmp, strideSrc, -16));
            masm.movdqu(new AMD64Address(dst, tmp, strideDst, -16), vec);
            masm.jmpb(labelDone);
        }

        /*
         * Tails for less than XMM size
         *
         * Since the initial vector length check (len & -vectorLength) sets len to zero, and tmp was
         * set to the tail length (tmp & (vectorLength - 1), equal to array length if this code path
         * is hit), from this point on, tmp holds the array length, and len is used as the temporary
         * register for copying data.
         */

        masm.bind(labelTailQWORD);
        masm.cmplAndJcc(tmp, 8 / strideDst.value, ConditionFlag.Less, labelTailDWORD, true);
        masm.movq(len, new AMD64Address(src));
        masm.movq(new AMD64Address(dst), len);

        masm.movq(len, new AMD64Address(src, tmp, strideSrc, -8));
        masm.movq(new AMD64Address(dst, tmp, strideDst, -8), len);
        masm.jmpb(labelDone);

        masm.bind(labelTailDWORD);
        if (strideDst.value < 4) {
            masm.cmplAndJcc(tmp, 4 / strideDst.value, ConditionFlag.Less, labelTailWORD, true);
        } else {
            masm.testlAndJcc(tmp, tmp, ConditionFlag.Zero, labelDone, true);
        }
        masm.movl(len, new AMD64Address(src));
        masm.movl(new AMD64Address(dst), len);
        masm.movl(len, new AMD64Address(src, tmp, strideSrc, -4));
        masm.movl(new AMD64Address(dst, tmp, strideDst, -4), len);

        if (strideDst.value < 4) {
            masm.jmpb(labelDone);
            masm.bind(labelTailWORD);
            if (strideDst.value < 2) {
                masm.cmplAndJcc(tmp, 2 / strideDst.value, ConditionFlag.Less, labelTailBYTE, true);
            } else {
                masm.testlAndJcc(tmp, tmp, ConditionFlag.Zero, labelDone, true);
            }
            masm.movw(len, new AMD64Address(src));
            masm.movw(new AMD64Address(dst), len);
            masm.movw(len, new AMD64Address(src, tmp, strideSrc, -2));
            masm.movw(new AMD64Address(dst, tmp, strideDst, -2), len);
        }
        if (strideDst.value < 2) {
            masm.jmpb(labelDone);
            masm.bind(labelTailBYTE);
            masm.testlAndJcc(tmp, tmp, ConditionFlag.Zero, labelDone, true);
            masm.movb(len, new AMD64Address(src));
            masm.movb(new AMD64Address(dst), len);
        }
        masm.bind(labelDone);
    }
}
