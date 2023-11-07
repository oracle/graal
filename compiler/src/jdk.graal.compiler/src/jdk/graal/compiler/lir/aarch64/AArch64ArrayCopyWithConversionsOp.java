/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isIllegal;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createBaseRegisterOnlyAddress;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createImmediateAddress;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createPairBaseRegisterOnlyAddress;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createRegisterOffsetAddress;
import static jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler.PREFERRED_BRANCH_TARGET_ALIGNMENT;
import static jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize.fromStride;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;

import java.util.Arrays;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ShiftType;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.common.StrideUtil;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
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
    @Temp({REG}) protected Value[] temp;
    @Temp({REG}) protected Value[] vectorTemp;

    public AArch64ArrayCopyWithConversionsOp(LIRGeneratorTool tool, Stride strideSrc, Stride strideDst, Value arrayDst, Value offsetDst, Value arraySrc, Value offsetSrc,
                    Value length, Value dynamicStrides) {
        super(TYPE);
        this.argStrideSrc = strideSrc;
        this.argStrideDst = strideDst;

        GraalError.guarantee(arrayDst.getPlatformKind() == AArch64Kind.QWORD && arrayDst.getPlatformKind() == arraySrc.getPlatformKind(), "64 bit array pointers expected");
        GraalError.guarantee(offsetDst.getPlatformKind() == AArch64Kind.QWORD, "long value expected");
        GraalError.guarantee(offsetSrc.getPlatformKind() == AArch64Kind.QWORD, "long value expected");
        GraalError.guarantee(length.getPlatformKind() == AArch64Kind.DWORD, "int value expected");
        GraalError.guarantee(strideSrc != Stride.S8 && strideDst != Stride.S8, "8 byte stride is not supported");

        this.arrayDstValue = arrayDst;
        this.offsetDstValue = offsetDst;
        this.arraySrcValue = arraySrc;
        this.offsetSrcValue = offsetSrc;
        this.lengthValue = length;
        this.dynamicStridesValue = dynamicStrides == null ? Value.ILLEGAL : dynamicStrides;

        temp = allocateTempRegisters(tool, withDynamicStrides() ? 3 : 2);
        vectorTemp = allocateVectorRegisters(tool, 4);
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
                Register tmp2 = asRegister(temp[2]);
                masm.mov(32, tmp2, asRegister(dynamicStridesValue));
                AArch64ControlFlow.RangeTableSwitchOp.emitJumpTable(crb, masm, tmp, tmp2, 0, 8, Arrays.stream(variants));

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

                for (Stride strideSrc : new Stride[]{Stride.S1, Stride.S2, Stride.S4}) {
                    for (Stride strideDst : new Stride[]{Stride.S1, Stride.S2, Stride.S4}) {
                        if (strideSrc == strideDst) {
                            continue;
                        }
                        masm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
                        masm.bind(variants[StrideUtil.getDirectStubCallIndex(strideSrc, strideDst)]);
                        emitArrayCopy(masm, strideDst, strideSrc, arrayDst, arraySrc, length, tmp, end);
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

    private void emitArrayCopy(AArch64MacroAssembler asm, Stride strideDst, Stride strideSrc, Register arrayDst, Register arraySrc, Register len, Register tmp, Label end) {
        Label tailLessThan64 = new Label();
        Label tailLessThan32 = new Label();
        Label tailLessThan16 = new Label();
        Label tailLessThan8 = new Label();
        Label tailLessThan4 = new Label();
        Label tailLessThan2 = new Label();

        Label vectorLoop = new Label();
        Label tail = new Label();

        Register maxStrideArray = strideSrc.value < strideDst.value ? arrayDst : arraySrc;
        Register minStrideArray = strideSrc.value < strideDst.value ? arraySrc : arrayDst;

        Stride strideMax = Stride.max(strideSrc, strideDst);
        Stride strideMin = Stride.min(strideSrc, strideDst);

        // subtract 32 from len. if the result is negative, jump to branch for less than 32 bytes
        asm.subs(64, len, len, 64 >> strideMax.log2);
        asm.branchConditionally(ConditionFlag.MI, tailLessThan64);

        Register refAddress = len;
        asm.add(64, refAddress, maxStrideArray, len, ShiftType.LSL, strideMax.log2);
        simdCopy64(asm, strideDst, strideSrc, arrayDst, arraySrc);

        asm.cmp(64, refAddress, maxStrideArray);
        asm.branchConditionally(ConditionFlag.LS, tail);

        // align addresses to chunk size
        asm.and(64, tmp, maxStrideArray, 63);
        asm.sub(64, minStrideArray, minStrideArray, tmp, ShiftType.LSR, strideMax.log2 - strideMin.log2);
        asm.bic(64, maxStrideArray, maxStrideArray, 63);

        // 64 byte loop
        asm.align(PREFERRED_LOOP_ALIGNMENT);
        asm.bind(vectorLoop);
        simdCopy64(asm, strideDst, strideSrc, arrayDst, arraySrc);
        asm.cmp(64, maxStrideArray, refAddress);
        asm.branchConditionally(ConditionFlag.LO, vectorLoop);

        asm.bind(tail);
        // 32 byte loop tail
        asm.sub(64, tmp, maxStrideArray, refAddress);
        asm.mov(64, maxStrideArray, refAddress);
        asm.sub(64, minStrideArray, minStrideArray, tmp, ShiftType.LSR, strideMax.log2 - strideMin.log2);

        simdCopy64(asm, strideDst, strideSrc, arrayDst, arraySrc);
        asm.jmp(end);

        // tail for 32 - 63 bytes
        tail32(asm, strideDst, strideSrc, arrayDst, arraySrc, len, tmp, tailLessThan64, tailLessThan32, end);
        // tail for 16 - 31 bytes
        tailLessThan32(asm, strideDst, strideSrc, arrayDst, arraySrc, len, tailLessThan32, tailLessThan16, end, 16);
        // tail for 8 - 15 bytes
        tailLessThan32(asm, strideDst, strideSrc, arrayDst, arraySrc, len, tailLessThan16, tailLessThan8, end, 8);
        // tail for 4 - 7 bytes
        tailLessThan32(asm, strideDst, strideSrc, arrayDst, arraySrc, len, tailLessThan8, tailLessThan4, end, 4);
        // tail for 2 - 3 bytes
        tailLessThan32(asm, strideDst, strideSrc, arrayDst, arraySrc, len, tailLessThan4, tailLessThan2, end, 2);
        // tail for 0 - 1 bytes
        tailLessThan32(asm, strideDst, strideSrc, arrayDst, arraySrc, len, tailLessThan2, end, end, 1);
    }

    private Register v(int index) {
        return asRegister(vectorTemp[index]);
    }

    private void simdCopy64(AArch64MacroAssembler asm,
                    Stride strideDst,
                    Stride strideSrc,
                    Register arrayDst,
                    Register arraySrc) {
        ElementSize dstESize = fromStride(strideDst);
        ElementSize srcESize = fromStride(strideSrc);
        switch (strideDst.log2 - strideSrc.log2) {
            case -2:
                // 4 -> 1 byte compression
                asm.fldp(128, v(0), v(1), createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arraySrc, 32));
                asm.fldp(128, v(2), v(3), createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arraySrc, 32));
                asm.neon.uzp1VVV(FullReg, srcESize.narrow(), v(0), v(0), v(1));
                asm.neon.uzp1VVV(FullReg, srcESize.narrow(), v(2), v(2), v(3));
                asm.neon.uzp1VVV(FullReg, dstESize, v(0), v(0), v(2));
                asm.fstr(128, v(0), createImmediateAddress(128, AddressingMode.IMMEDIATE_POST_INDEXED, arrayDst, 16));
                break;
            case -1:
                // 2 -> 1 byte compression
                asm.fldp(128, v(0), v(1), createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arraySrc, 32));
                asm.fldp(128, v(2), v(3), createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arraySrc, 32));
                asm.neon.uzp1VVV(FullReg, dstESize, v(0), v(0), v(1));
                asm.neon.uzp1VVV(FullReg, dstESize, v(2), v(2), v(3));
                asm.fstp(128, v(0), v(2), createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arrayDst, 32));
                break;
            case 0:
                // direct copy
                asm.fldp(128, v(0), v(1), createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arraySrc, 32));
                asm.fldp(128, v(2), v(3), createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arraySrc, 32));
                asm.fstp(128, v(0), v(1), createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arrayDst, 32));
                asm.fstp(128, v(2), v(3), createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arrayDst, 32));
                break;
            case 1:
                // 1 -> 2 byte inflation
                asm.fldp(128, v(0), v(2), createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arraySrc, 32));
                asm.neon.uxtl2VV(srcESize, v(1), v(0));
                asm.neon.uxtl2VV(srcESize, v(3), v(2));
                asm.neon.uxtlVV(srcESize, v(0), v(0));
                asm.neon.uxtlVV(srcESize, v(2), v(2));
                asm.fstp(128, v(0), v(1), createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arrayDst, 32));
                asm.fstp(128, v(2), v(3), createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arrayDst, 32));
                break;
            case 2:
                // 1 -> 4 byte inflation
                asm.fldr(128, v(0), createImmediateAddress(128, AddressingMode.IMMEDIATE_POST_INDEXED, arraySrc, 16));
                asm.neon.uxtl2VV(srcESize, v(2), v(0));
                asm.neon.uxtlVV(srcESize, v(0), v(0));
                asm.neon.uxtl2VV(srcESize.expand(), v(1), v(0));
                asm.neon.uxtl2VV(srcESize.expand(), v(3), v(2));
                asm.neon.uxtlVV(srcESize.expand(), v(0), v(0));
                asm.neon.uxtlVV(srcESize.expand(), v(2), v(2));
                asm.fstp(128, v(0), v(1), createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arrayDst, 32));
                asm.fstp(128, v(2), v(3), createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arrayDst, 32));
                break;
            default:
                throw GraalError.unimplemented("conversion from " + strideSrc + " to " + strideDst + " not implemented"); // ExcludeFromJacocoGeneratedReport
        }
    }

    private void tail32(AArch64MacroAssembler asm,
                    Stride strideDst,
                    Stride strideSrc,
                    Register arrayDst,
                    Register arraySrc,
                    Register len,
                    Register tmp,
                    Label entry,
                    Label nextTail,
                    Label end) {
        Stride strideMax = Stride.max(strideSrc, strideDst);

        asm.bind(entry);
        asm.adds(64, len, len, 32 >> strideMax.log2);
        asm.branchConditionally(ConditionFlag.MI, nextTail);

        ElementSize dstESize = fromStride(strideDst);
        ElementSize srcESize = fromStride(strideSrc);
        switch (strideDst.log2 - strideSrc.log2) {
            case -2:
                // 4 -> 1 byte compression
                asm.fldp(128, v(0), v(1), createPairBaseRegisterOnlyAddress(128, arraySrc));
                asm.add(64, arraySrc, arraySrc, len, ShiftType.LSL, strideSrc.log2);
                asm.fldp(128, v(2), v(3), createPairBaseRegisterOnlyAddress(128, arraySrc));
                asm.neon.uzp1VVV(FullReg, srcESize.narrow(), v(0), v(0), v(1));
                asm.neon.uzp1VVV(FullReg, srcESize.narrow(), v(2), v(2), v(3));
                asm.neon.xtnVV(dstESize, v(0), v(0));
                asm.neon.xtnVV(dstESize, v(2), v(2));
                assert strideDst.value == 1 : strideDst;
                asm.fstr(64, v(0), createBaseRegisterOnlyAddress(64, arrayDst));
                asm.fstr(64, v(2), createRegisterOffsetAddress(64, arrayDst, len, false));
                break;
            case -1:
                // 2 -> 1 byte compression
                asm.fldp(128, v(0), v(1), createPairBaseRegisterOnlyAddress(128, arraySrc));
                asm.add(64, arraySrc, arraySrc, len, ShiftType.LSL, strideSrc.log2);
                asm.fldp(128, v(2), v(3), createPairBaseRegisterOnlyAddress(128, arraySrc));
                asm.lsl(64, tmp, len, strideDst.log2);
                asm.neon.uzp1VVV(FullReg, dstESize, v(0), v(0), v(1));
                asm.neon.uzp1VVV(FullReg, dstESize, v(2), v(2), v(3));
                asm.fstr(128, v(0), createBaseRegisterOnlyAddress(128, arrayDst));
                asm.fstr(128, v(2), createRegisterOffsetAddress(128, arrayDst, tmp, false));
                break;
            case 0:
                // direct copy
                asm.fldp(128, v(0), v(1), createPairBaseRegisterOnlyAddress(128, arraySrc));
                asm.add(64, arraySrc, arraySrc, len, ShiftType.LSL, strideSrc.log2);
                asm.fldp(128, v(2), v(3), createPairBaseRegisterOnlyAddress(128, arraySrc));
                asm.fstp(128, v(0), v(1), createPairBaseRegisterOnlyAddress(128, arrayDst));
                asm.add(64, arrayDst, arrayDst, len, ShiftType.LSL, strideDst.log2);
                asm.fstp(128, v(2), v(3), createPairBaseRegisterOnlyAddress(128, arrayDst));
                break;
            case 1:
                // 1 -> 2 byte inflation
                asm.lsl(64, tmp, len, strideSrc.log2);
                asm.fldr(128, v(0), createBaseRegisterOnlyAddress(128, arraySrc));
                asm.fldr(128, v(2), createRegisterOffsetAddress(128, arraySrc, tmp, false));
                asm.neon.uxtl2VV(srcESize, v(1), v(0));
                asm.neon.uxtl2VV(srcESize, v(3), v(2));
                asm.neon.uxtlVV(srcESize, v(0), v(0));
                asm.neon.uxtlVV(srcESize, v(2), v(2));
                asm.fstp(128, v(0), v(1), createPairBaseRegisterOnlyAddress(128, arrayDst));
                asm.add(64, arrayDst, arrayDst, len, ShiftType.LSL, strideDst.log2);
                asm.fstp(128, v(2), v(3), createPairBaseRegisterOnlyAddress(128, arrayDst));
                break;
            case 2:
                // 1 -> 4 byte inflation
                assert strideSrc.value == 1 : strideSrc;
                asm.fldr(64, v(0), createBaseRegisterOnlyAddress(64, arraySrc));
                asm.fldr(64, v(2), createRegisterOffsetAddress(64, arraySrc, len, false));
                asm.neon.uxtlVV(srcESize, v(0), v(0));
                asm.neon.uxtlVV(srcESize, v(2), v(2));
                asm.neon.uxtl2VV(srcESize.expand(), v(1), v(0));
                asm.neon.uxtl2VV(srcESize.expand(), v(3), v(2));
                asm.neon.uxtlVV(srcESize.expand(), v(0), v(0));
                asm.neon.uxtlVV(srcESize.expand(), v(2), v(2));
                asm.fstp(128, v(0), v(1), createPairBaseRegisterOnlyAddress(128, arrayDst));
                asm.add(64, arrayDst, arrayDst, len, ShiftType.LSL, strideDst.log2);
                asm.fstp(128, v(2), v(3), createPairBaseRegisterOnlyAddress(128, arrayDst));
                break;
            default:
                throw GraalError.unimplemented("conversion from " + strideSrc + " to " + strideDst + " not implemented"); // ExcludeFromJacocoGeneratedReport
        }
        asm.jmp(end);
    }

    private void tailLessThan32(AArch64MacroAssembler asm,
                    Stride strideDst,
                    Stride strideSrc,
                    Register arrayDst,
                    Register arraySrc,
                    Register len,
                    Label entry,
                    Label nextTail,
                    Label end,
                    int nBytes) {
        Stride strideMax = Stride.max(strideSrc, strideDst);
        if (strideMax.value > nBytes) {
            return;
        }
        asm.bind(entry);
        asm.adds(64, len, len, nBytes >> strideMax.log2);
        asm.branchConditionally(ConditionFlag.MI, strideMax.value == nBytes ? end : nextTail);

        ElementSize dstESize = fromStride(strideDst);
        ElementSize srcESize = fromStride(strideSrc);
        int op = strideDst.log2 - strideSrc.log2;
        int bits = nBytes << 3;
        int loadBits = bits >> Math.max(0, op);
        int storeBits = bits >> Math.max(0, -op);
        if (strideMax.value == nBytes) {
            asm.ldr(loadBits, len, createBaseRegisterOnlyAddress(loadBits, arraySrc));
            asm.str(storeBits, len, createBaseRegisterOnlyAddress(storeBits, arrayDst));
            asm.jmp(end);
            return;
        }

        asm.fldr(loadBits, v(0), createBaseRegisterOnlyAddress(loadBits, arraySrc));
        asm.add(64, arraySrc, arraySrc, len, ShiftType.LSL, strideSrc.log2);
        asm.fldr(loadBits, v(1), createBaseRegisterOnlyAddress(loadBits, arraySrc));
        switch (op) {
            case -2:
                // 4 -> 1 byte compression
                asm.neon.xtnVV(dstESize.expand(), v(0), v(0));
                asm.neon.xtnVV(dstESize.expand(), v(1), v(1));
                asm.neon.xtnVV(dstESize, v(0), v(0));
                asm.neon.xtnVV(dstESize, v(1), v(1));
                break;
            case -1:
                // 2 -> 1 byte compression
                asm.neon.xtnVV(dstESize, v(0), v(0));
                asm.neon.xtnVV(dstESize, v(1), v(1));
                break;
            case 0:
                // direct copy
                break;
            case 1:
                // 1 -> 2 byte inflation
                asm.neon.uxtlVV(srcESize, v(0), v(0));
                asm.neon.uxtlVV(srcESize, v(1), v(1));
                break;
            case 2:
                // 1 -> 4 byte inflation
                asm.neon.uxtlVV(srcESize, v(0), v(0));
                asm.neon.uxtlVV(srcESize, v(1), v(1));
                asm.neon.uxtlVV(srcESize.expand(), v(0), v(0));
                asm.neon.uxtlVV(srcESize.expand(), v(1), v(1));
                break;
            default:
                throw GraalError.unimplemented("conversion from " + strideSrc + " to " + strideDst + " not implemented"); // ExcludeFromJacocoGeneratedReport
        }
        asm.fstr(storeBits, v(0), createBaseRegisterOnlyAddress(storeBits, arrayDst));
        asm.add(64, arrayDst, arrayDst, len, ShiftType.LSL, strideDst.log2);
        asm.fstr(storeBits, v(1), createBaseRegisterOnlyAddress(storeBits, arrayDst));
        asm.jmp(end);
    }
}
