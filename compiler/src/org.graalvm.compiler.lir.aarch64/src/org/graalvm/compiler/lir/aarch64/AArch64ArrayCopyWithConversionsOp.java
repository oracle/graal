/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.aarch64;

import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isIllegal;
import static org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.PREFERRED_BRANCH_TARGET_ALIGNMENT;
import static org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.asElementSize;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import java.util.Arrays;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64ASIMDAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Address.AddressingMode;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ShiftType;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.core.common.StrideUtil;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * Arraycopy operation for arbitrary source and destination arrays, with arbitrary byte offset, with
 * support for arbitrary compression and inflation of {@link Stride#S1 8 bit}, {@link Stride#S2 16
 * bit} or {@link Stride#S4 32 bit} array elements.
 */
@Opcode("ARRAY_COPY_WITH_CONVERSIONS")
public final class AArch64ArrayCopyWithConversionsOp extends AArch64ComplexVectorOp {
    public static final LIRInstructionClass<AArch64ArrayCopyWithConversionsOp> TYPE = LIRInstructionClass.create(AArch64ArrayCopyWithConversionsOp.class);

    private final Stride argStrideSrc;
    private final Stride argStrideDst;

    @Alive({REG}) protected Value arrayDstValue;
    @Alive({REG}) protected Value offsetDstValue;
    @Alive({REG}) protected Value arraySrcValue;
    @Alive({REG}) protected Value offsetSrcValue;
    @Alive({REG}) protected Value lengthValue;
    @Alive({REG, ILLEGAL}) private Value dynamicStridesValue;
    @Temp({REG}) protected AllocatableValue[] temp;
    @Temp({REG}) protected AllocatableValue[] vectorTemp;

    public AArch64ArrayCopyWithConversionsOp(LIRGeneratorTool tool, Stride strideSrc, Stride strideDst, Value arrayDst, Value offsetDst, Value arraySrc, Value offsetSrc,
                    Value length, Value dynamicStrides) {
        super(TYPE);
        this.argStrideSrc = strideSrc;
        this.argStrideDst = strideDst;

        assert arrayDst.getPlatformKind() == AArch64Kind.QWORD && arrayDst.getPlatformKind() == arraySrc.getPlatformKind();
        assert offsetDst == null || offsetDst.getPlatformKind() == AArch64Kind.QWORD;
        assert offsetSrc == null || offsetSrc.getPlatformKind() == AArch64Kind.QWORD;
        assert length.getPlatformKind() == AArch64Kind.DWORD;

        this.arrayDstValue = arrayDst;
        this.offsetDstValue = offsetDst;
        this.arraySrcValue = arraySrc;
        this.offsetSrcValue = offsetSrc;
        this.lengthValue = length;
        this.dynamicStridesValue = dynamicStrides == null ? Value.ILLEGAL : dynamicStrides;

        temp = allocateTempRegisters(tool, 3);
        vectorTemp = allocateVectorRegisters(tool, dynamicStrides != null || Math.abs(strideSrc.log2 - strideDst.log2) == 2 ? 4 : 2);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register arrayDst = asRegister(temp[0]);
        Register arraySrc = asRegister(temp[1]);
        Label end = new Label();

        // Load array base addresses.
        masm.add(64, arrayDst, asRegister(arrayDstValue), asRegister(offsetDstValue));
        masm.add(64, arraySrc, asRegister(arraySrcValue), asRegister(offsetSrcValue));

        try (ScratchRegister sc1 = masm.getScratchRegister(); ScratchRegister sc2 = masm.getScratchRegister()) {
            Register tmp = sc1.getRegister();
            Register length = sc2.getRegister();

            // Get array length and store as a 64-bit value.
            masm.mov(32, length, asRegister(lengthValue));

            if (withDynamicStrides()) {
                Label[] variants = new Label[9];
                for (int i = 0; i < variants.length; i++) {
                    variants[i] = new Label();
                }
                AArch64ControlFlow.RangeTableSwitchOp.emitJumpTable(crb, masm, tmp, asRegister(dynamicStridesValue), 0, 8, Arrays.stream(variants));

                // use the 1-byte-1-byte stride variant for the 2-2 and 4-4 cases by simply shifting
                // the length
                masm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
                masm.bind(variants[StrideUtil.getDirectStubCallIndex(Stride.S4, Stride.S4)]);
                masm.lsl(64, length, length, 1);
                masm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
                masm.bind(variants[StrideUtil.getDirectStubCallIndex(Stride.S2, Stride.S2)]);
                masm.lsl(64, length, length, 1);
                masm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
                masm.bind(variants[StrideUtil.getDirectStubCallIndex(Stride.S1, Stride.S1)]);
                emitArrayCopy(masm, Stride.S1, Stride.S1, arrayDst, arraySrc, length, tmp, end);
                masm.jmp(end);

                for (Stride stride1 : new Stride[]{Stride.S1, Stride.S2, Stride.S4}) {
                    for (Stride stride2 : new Stride[]{Stride.S1, Stride.S2, Stride.S4}) {
                        if (stride1.log2 == stride2.log2) {
                            continue;
                        }
                        masm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
                        masm.bind(variants[StrideUtil.getDirectStubCallIndex(stride1, stride2)]);
                        emitArrayCopy(masm, stride2, stride1, arrayDst, arraySrc, length, tmp, end);
                        masm.jmp(end);
                    }
                }
            } else {
                emitArrayCopy(masm, argStrideDst, argStrideSrc, arrayDst, arraySrc, length, tmp, end);
            }
            masm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
            masm.bind(end);
        }
    }

    private boolean withDynamicStrides() {
        return !isIllegal(dynamicStridesValue);
    }

    private void emitArrayCopy(AArch64MacroAssembler masm, Stride strideDst, Stride strideSrc, Register arrayDst, Register arraySrc, Register length, Register tmp, Label end) {
        Label scalarCopy = new Label();

        int chunkSize = strideSrc == strideDst ? 32 : 16;
        masm.compare(32, length, chunkSize >> Stride.min(strideSrc, strideDst).log2);
        masm.branchConditionally(ConditionFlag.LE, scalarCopy);

        emitSIMDCopy(masm, strideDst, strideSrc, arrayDst, arraySrc, length, tmp, end);

        masm.bind(scalarCopy);
        if (strideDst == strideSrc) {
            emitSameStrideScalarCopy(masm, strideDst, arrayDst, arraySrc, length, tmp, end);
        } else {
            emitMixedStrideScalarCopy(masm, strideDst, strideSrc, arrayDst, arraySrc, length, tmp, end);
        }
    }

    private void emitSameStrideScalarCopy(AArch64MacroAssembler masm, Stride stride, Register arrayDst, Register arraySrc, Register length,
                    Register lengthTail, Label end) {
        // convert length to byte-length
        if (stride.log2 > 0) {
            masm.lsl(64, length, length, stride.log2);
        }
        masm.mov(64, lengthTail, length); // copy

        emit8ByteCopy(masm, arrayDst, arraySrc, length, lengthTail, end);
        emitTailCopies(masm, stride, arrayDst, arraySrc, lengthTail, end);
    }

    /**
     * Vector size used in {@link #emit8ByteCopy}.
     */
    private static final int VECTOR_SIZE = 8;

    private void emit8ByteCopy(AArch64MacroAssembler masm, Register arrayDst, Register arraySrc, Register length, Register lengthTail, Label end) {
        Label loop = new Label();
        Label compareTail = new Label();

        Register tmp = asRegister(temp[2]);

        masm.and(64, lengthTail, lengthTail, VECTOR_SIZE - 1); // tail count (in bytes)
        masm.ands(64, length, length, -VECTOR_SIZE);  // vector count (in bytes)
        masm.branchConditionally(ConditionFlag.EQ, compareTail);

        masm.align(AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT);
        masm.bind(loop);
        masm.ldr(64, tmp, AArch64Address.createImmediateAddress(64, AddressingMode.IMMEDIATE_POST_INDEXED, arraySrc, VECTOR_SIZE));
        masm.str(64, tmp, AArch64Address.createImmediateAddress(64, AddressingMode.IMMEDIATE_POST_INDEXED, arrayDst, VECTOR_SIZE));
        masm.sub(64, length, length, VECTOR_SIZE);
        masm.cbnz(64, length, loop);

        masm.cbz(64, lengthTail, end);

        masm.sub(64, lengthTail, lengthTail, VECTOR_SIZE);
        masm.ldr(64, tmp, AArch64Address.createRegisterOffsetAddress(64, arraySrc, lengthTail, false));
        masm.str(64, tmp, AArch64Address.createRegisterOffsetAddress(64, arrayDst, lengthTail, false));
        masm.jmp(end);

        masm.bind(compareTail);
    }

    private void emitTailCopies(AArch64MacroAssembler masm, Stride stride, Register arrayDst, Register arraySrc, Register lengthTail, Label end) {
        Label copy2Bytes = new Label();
        Label copy1Byte = new Label();

        Register tmp = asRegister(temp[2]);

        if (stride.value <= 4) {
            // Copy trailing 4 bytes, if any.
            tailCopy(masm, arrayDst, arraySrc, lengthTail, copy2Bytes, tmp, 4);

            masm.bind(copy2Bytes);
            if (stride.value <= 2) {
                // Copy trailing 2 bytes, if any.
                tailCopy(masm, arrayDst, arraySrc, lengthTail, copy1Byte, tmp, 2);

                masm.bind(copy1Byte);
                if (stride.value <= 1) {
                    // Copy trailing byte, if any.
                    tailCopy(masm, arrayDst, arraySrc, lengthTail, end, tmp, 1);
                }
            }
        }
    }

    private static void tailCopy(AArch64MacroAssembler masm, Register arrayDst, Register arraySrc, Register lengthTail, Label nextTail, Register tmp, int nBytes) {
        int srcSize = nBytes * 8;
        masm.ands(32, zr, lengthTail, nBytes);
        masm.branchConditionally(ConditionFlag.EQ, nextTail);
        masm.ldr(srcSize, tmp, AArch64Address.createImmediateAddress(srcSize, AddressingMode.IMMEDIATE_POST_INDEXED, arraySrc, nBytes));
        masm.str(srcSize, tmp, AArch64Address.createImmediateAddress(srcSize, AddressingMode.IMMEDIATE_POST_INDEXED, arrayDst, nBytes));
    }

    private static void emitMixedStrideScalarCopy(AArch64MacroAssembler masm, Stride strideDst, Stride strideSrc, Register arrayDst, Register arraySrc, Register length, Register tmp, Label end) {
        Label loop = new Label();

        // check for length == 0
        masm.cbz(64, length, end);

        // main loop
        masm.align(AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT);
        masm.bind(loop);
        masm.ldr(strideSrc.getBitCount(), tmp, AArch64Address.createImmediateAddress(strideSrc.getBitCount(), AddressingMode.IMMEDIATE_POST_INDEXED, arraySrc, strideSrc.value));
        masm.str(strideDst.getBitCount(), tmp, AArch64Address.createImmediateAddress(strideDst.getBitCount(), AddressingMode.IMMEDIATE_POST_INDEXED, arrayDst, strideDst.value));
        masm.sub(64, length, length, 1);
        masm.cbnz(64, length, loop);
    }

    private void emitSIMDCopy(AArch64MacroAssembler masm, Stride strideDst, Stride strideSrc, Register arrayDst, Register arraySrc, Register length,
                    Register arrayAlignment, Label endLabel) {

        Label loopHead = new Label();
        Label tail = new Label();

        Stride maxStride = Stride.max(strideSrc, strideDst);
        Stride minStride = Stride.min(strideSrc, strideDst);
        Register maxStrideArray = strideSrc.value < strideDst.value ? arrayDst : arraySrc;
        Register minStrideArray = strideSrc.value < strideDst.value ? arraySrc : arrayDst;

        int chunkSize = strideSrc == strideDst ? 32 : 16;
        Register refAddress = length;
        masm.add(64, refAddress, minStrideArray, length, ShiftType.LSL, minStride.log2);
        masm.sub(64, refAddress, refAddress, chunkSize);

        // peeled first loop iteration
        simdCopy(masm, strideDst, strideSrc, arrayDst, arraySrc);
        masm.cmp(64, refAddress, minStrideArray);
        masm.branchConditionally(ConditionFlag.LS, tail);

        // align addresses to chunk size
        masm.and(64, arrayAlignment, minStrideArray, chunkSize - 1);
        masm.sub(64, maxStrideArray, maxStrideArray, arrayAlignment, ShiftType.LSL, maxStride.log2 - minStride.log2);
        masm.bic(64, minStrideArray, minStrideArray, chunkSize - 1);

        // main loop
        masm.align(AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT);
        masm.bind(loopHead);
        simdCopy(masm, strideDst, strideSrc, arrayDst, arraySrc);
        masm.cmp(64, minStrideArray, refAddress);
        masm.branchConditionally(ConditionFlag.LO, loopHead);

        masm.bind(tail);
        /* Adjust array1 and array2 to access last 32 bytes. */
        masm.sub(64, arrayAlignment, minStrideArray, refAddress);
        masm.mov(64, minStrideArray, refAddress);
        masm.sub(64, maxStrideArray, maxStrideArray, arrayAlignment, ShiftType.LSL, maxStride.log2 - minStride.log2);
        simdCopy(masm, strideDst, strideSrc, arrayDst, arraySrc);
        masm.jmp(endLabel);
    }

    private Register v(int index) {
        return asRegister(vectorTemp[index]);
    }

    private void simdCopy(AArch64MacroAssembler masm,
                    Stride strideDst,
                    Stride strideSrc,
                    Register arrayDst,
                    Register arraySrc) {
        AArch64ASIMDAssembler.ElementSize dstESize = asElementSize(strideDst);
        AArch64ASIMDAssembler.ElementSize srcESize = asElementSize(strideSrc);
        AArch64Address addressLoad = AArch64Address.createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arraySrc, 32);
        AArch64Address addressStore = AArch64Address.createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arrayDst, 32);
        switch (strideDst.log2 - strideSrc.log2) {
            case -2:
                // 4 -> 1 byte compression
                AArch64ASIMDAssembler.ElementSize dstESizeDouble = dstESize.expand();
                masm.fldp(128, v(0), v(1), addressLoad);
                masm.fldp(128, v(2), v(3), addressLoad);
                masm.neon.xtnVV(dstESizeDouble, v(0), v(0));
                masm.neon.xtnVV(dstESizeDouble, v(2), v(2));
                masm.neon.xtn2VV(dstESizeDouble, v(0), v(1));
                masm.neon.xtn2VV(dstESizeDouble, v(2), v(3));
                masm.neon.xtnVV(dstESize, v(0), v(0));
                masm.neon.xtn2VV(dstESize, v(0), v(2));
                masm.fstr(128, v(0), AArch64Address.createImmediateAddress(128, AddressingMode.IMMEDIATE_POST_INDEXED, arrayDst, 16));
                break;
            case -1:
                // 2 -> 1 byte compression
                masm.fldp(128, v(0), v(1), addressLoad);
                masm.neon.xtnVV(dstESize, v(0), v(0));
                masm.neon.xtn2VV(dstESize, v(0), v(1));
                masm.fstr(128, v(0), AArch64Address.createImmediateAddress(128, AddressingMode.IMMEDIATE_POST_INDEXED, arrayDst, 16));
                break;
            case 0:
                // direct copy
                masm.fldp(128, v(0), v(1), addressLoad);
                masm.fstp(128, v(0), v(1), addressStore);
                break;
            case 1:
                // 1 -> 2 byte inflation
                masm.fldr(128, v(0), AArch64Address.createImmediateAddress(128, AddressingMode.IMMEDIATE_POST_INDEXED, arraySrc, 16));
                masm.neon.uxtl2VV(srcESize, v(1), v(0));
                masm.neon.uxtlVV(srcESize, v(0), v(0));
                masm.fstp(128, v(0), v(1), addressStore);
                break;
            case 2:
                // 1 -> 4 byte inflation
                AArch64ASIMDAssembler.ElementSize srcESizeDouble = srcESize.expand();
                masm.fldr(128, v(0), AArch64Address.createImmediateAddress(128, AddressingMode.IMMEDIATE_POST_INDEXED, arraySrc, 16));
                masm.neon.uxtl2VV(srcESize, v(2), v(0));
                masm.neon.uxtlVV(srcESize, v(0), v(0));
                masm.neon.uxtl2VV(srcESizeDouble, v(1), v(0));
                masm.neon.uxtl2VV(srcESizeDouble, v(3), v(2));
                masm.neon.uxtlVV(srcESizeDouble, v(0), v(0));
                masm.neon.uxtlVV(srcESizeDouble, v(2), v(2));
                masm.fstp(128, v(0), v(1), addressStore);
                masm.fstp(128, v(2), v(3), addressStore);
                break;
            default:
                throw GraalError.unimplemented("conversion from " + strideSrc + " to " + strideDst + " not implemented");
        }
    }
}
