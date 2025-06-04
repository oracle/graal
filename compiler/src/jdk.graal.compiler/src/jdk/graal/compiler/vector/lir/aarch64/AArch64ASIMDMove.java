/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.HINT;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.STACK;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.UNINITIALIZED;
import static jdk.graal.compiler.lir.aarch64.AArch64Move.move;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDMacroAssembler;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.code.DataSection;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.StandardOp.LoadConstantOp;
import jdk.graal.compiler.lir.aarch64.AArch64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.Value;

public class AArch64ASIMDMove {

    public static class LoadInlineConstant extends AArch64LIRInstruction implements LoadConstantOp {
        public static final LIRInstructionClass<LoadInlineConstant> TYPE = LIRInstructionClass.create(LoadInlineConstant.class);

        @Def({REG}) protected AllocatableValue result;
        protected SimdConstant simdConstant;

        public LoadInlineConstant(AllocatableValue result, SimdConstant simdConstant) {
            super(TYPE);
            this.result = result;
            this.simdConstant = simdConstant;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }

        @Override
        public Constant getConstant() {
            return simdConstant;
        }

        @Override
        protected void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            simdConst2Reg(crb, masm, result, simdConstant);
        }

        private static boolean tryEncodeSimdConst(AArch64MacroAssembler masm, Value result, SimdConstant simdConstant, ASIMDSize size, AArch64Kind elementKind) {
            int length = result.getPlatformKind().getVectorLength();
            assert length > 0 : length;
            int vectorLength = simdConstant.getVectorLength();
            boolean specialCaseTwoBytes = result.getPlatformKind().equals(AArch64Kind.V32_BYTE) && simdConstant.getSerializedSize() == 2;
            assert specialCaseTwoBytes || length <= vectorLength : "length>=" + vectorLength;

            if (simdConstant.isAllSame()) {
                ElementSize eSize = ElementSize.fromKind(elementKind);
                PrimitiveConstant constant = (PrimitiveConstant) simdConstant.getValue(0);
                switch (elementKind) {
                    case BYTE:
                    case WORD:
                    case DWORD:
                    case QWORD: {
                        long longVal = constant.asLong();
                        if (AArch64ASIMDMacroAssembler.isMoveImmediate(eSize, longVal)) {
                            masm.neon.moveVI(size, eSize, asRegister(result), longVal);
                            return true;
                        }
                        break;
                    }
                    case SINGLE: {
                        Float floatVal = constant.asFloat();
                        if (AArch64ASIMDMacroAssembler.isMoveImmediate(floatVal)) {
                            masm.neon.moveVI(size, asRegister(result), floatVal);
                            return true;
                        }
                        break;
                    }
                    case DOUBLE: {
                        double doubleVal = constant.asDouble();
                        if (AArch64ASIMDMacroAssembler.isMoveImmediate(doubleVal)) {
                            masm.neon.moveVI(size, asRegister(result), doubleVal);
                            return true;
                        }
                        break;
                    }
                }
            }
            return false;
        }

        private static void simdConst2Reg(CompilationResultBuilder crb, AArch64MacroAssembler masm, Value result, SimdConstant simdConstant) {
            ASIMDSize size = ASIMDSize.fromVectorKind(result.getPlatformKind());
            Register dst = asRegister(result);
            AArch64Kind elementKind = ((AArch64Kind) result.getPlatformKind()).getScalar();
            if (simdConstant.isDefaultForKind()) {
                masm.neon.moviVI(size, dst, 0);
            } else if (SimdConstant.isAllOnes(simdConstant)) {
                masm.neon.mvniVI(size, dst, 0);
            } else if (tryEncodeSimdConst(masm, result, simdConstant, size, elementKind)) {
                /* Do nothing - was able to successfully encode constant. */
            } else {
                /* Encode constant in data section. */
                try (AArch64MacroAssembler.ScratchRegister scr = masm.getScratchRegister()) {
                    Register scratch = scr.getRegister();
                    SimdConstant constantInMemory = simdConstant;
                    if (size.bytes() <= ASIMDSize.HalfReg.bytes() && constantInMemory.getSerializedSize() < size.bytes()) {
                        constantInMemory = padConstant(constantInMemory, size.bytes());
                    }
                    DataSection.Data data = crb.dataBuilder.createMultiDataItem(constantInMemory.getValues());
                    crb.dataBuilder.updateAlignment(data, size.bytes());
                    crb.recordDataSectionReference(data);
                    masm.adrpAdd(scratch);
                    masm.fldr(size.bits(), asRegister(result), AArch64Address.createBaseRegisterOnlyAddress(size.bits(), scratch));
                }
            }

        }

        /** Pad the given constant to the given number of bytes. */
        private static SimdConstant padConstant(SimdConstant simdConstant, int toBytes) {
            GraalError.guarantee(simdConstant.getSerializedSize() < toBytes, "can't pad %s to %s bytes, it's already big enough", simdConstant, toBytes);
            Constant[] constants = new Constant[simdConstant.getVectorLength() + (toBytes - simdConstant.getSerializedSize())];
            int i;
            for (i = 0; i < simdConstant.getVectorLength(); i++) {
                constants[i] = simdConstant.getValue(i);
            }
            for (; i < constants.length; i++) {
                constants[i] = JavaConstant.forByte((byte) (i % 2 == 0 ? 0xc0 : 0xfe));
            }
            return new SimdConstant(constants);
        }

        @Override
        public boolean canRematerializeToStack() {
            return false;
        }
    }

    @Opcode("VSTACKMOVE")
    public static class StackMoveOp extends AArch64LIRInstruction implements StandardOp.ValueMoveOp {
        public static final LIRInstructionClass<StackMoveOp> TYPE = LIRInstructionClass.create(StackMoveOp.class);

        @Def({STACK}) protected AllocatableValue result;
        @Use({STACK, HINT}) protected AllocatableValue input;
        @Alive({STACK, UNINITIALIZED}) private AllocatableValue backupSlot;

        private final Register scratch;
        AArch64Kind moveKind;

        public StackMoveOp(AllocatableValue result, AllocatableValue input, Register scratch, AllocatableValue backupSlot) {
            super(TYPE);
            this.result = result;
            this.input = input;
            this.backupSlot = backupSlot;
            this.scratch = scratch;
            assert result.getPlatformKind().getSizeInBytes() <= input.getPlatformKind().getSizeInBytes() : "cannot move " + input + " into a larger Value " + result;
            moveKind = (AArch64Kind) result.getPlatformKind();
        }

        @Override
        public AllocatableValue getInput() {
            return input;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            // backup scratch register
            move(moveKind, crb, masm, backupSlot, scratch.asValue(backupSlot.getValueKind()));
            // move stack slot
            move(moveKind, crb, masm, scratch.asValue(getInput().getValueKind()), getInput());
            move(moveKind, crb, masm, getResult(), scratch.asValue(getResult().getValueKind()));
            // restore scratch register
            move(moveKind, crb, masm, scratch.asValue(backupSlot.getValueKind()), backupSlot);
        }

    }

    @Opcode("VFILL")
    public static class VectorFill extends AArch64LIRInstruction {
        public static final LIRInstructionClass<VectorFill> TYPE = LIRInstructionClass.create(VectorFill.class);

        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue input;

        public VectorFill(AllocatableValue result, AllocatableValue input) {
            super(TYPE);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            Register dst = asRegister(result);
            Register src = asRegister(input);

            assert dst.getRegisterCategory().equals(AArch64.SIMD) : "Dst reg category must be SIMD " + dst.getRegisterCategory();

            ASIMDSize size = ASIMDSize.fromVectorKind(result.getPlatformKind());
            ElementSize eSize = ElementSize.fromKind(result.getPlatformKind());
            if (src.getRegisterCategory().equals(AArch64.CPU)) {
                masm.neon.dupVG(size, eSize, dst, src);
            } else {
                assert src.getRegisterCategory().equals(AArch64.SIMD) : "Src reg category must be SIMD " + src.getRegisterCategory();
                masm.neon.dupVX(size, eSize, dst, src, 0);
            }
        }
    }

    @Opcode("ConstantVFill")
    public static class ConstantVectorFillOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<ConstantVectorFillOp> TYPE = LIRInstructionClass.create(ConstantVectorFillOp.class);

        @Def({REG}) protected AllocatableValue result;

        protected final JavaConstant constant;

        public ConstantVectorFillOp(AllocatableValue result, JavaConstant constant) {
            super(TYPE);
            this.result = result;
            this.constant = constant;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            assert asRegister(result).getRegisterCategory().equals(AArch64.SIMD) : "Result reg category must be SIMD " + asRegister(result).getRegisterCategory();

            ASIMDSize size = ASIMDSize.fromVectorKind(result.getPlatformKind());
            AArch64Kind scalarKind = ((AArch64Kind) result.getPlatformKind()).getScalar();
            ElementSize eSize = ElementSize.fromKind(scalarKind);
            switch (scalarKind) {
                case BYTE:
                case WORD:
                case DWORD:
                case QWORD:
                    masm.neon.moveVI(size, eSize, asRegister(result), constant.asLong());
                    break;
                case SINGLE:
                    masm.neon.moveVI(size, asRegister(result), constant.asFloat());
                    break;
                case DOUBLE:
                    masm.neon.moveVI(size, asRegister(result), constant.asDouble());
                    break;
                default:
                    throw GraalError.shouldNotReachHereUnexpectedValue(scalarKind); // ExcludeFromJacocoGeneratedReport
            }
        }
    }

    /**
     * Moves an scalar value into a SIMD register and zeros out the rest of the register.
     */
    public static class ScalarToSIMDOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<ScalarToSIMDOp> TYPE = LIRInstructionClass.create(ScalarToSIMDOp.class);

        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue input;

        public ScalarToSIMDOp(AllocatableValue result, AllocatableValue input) {
            super(TYPE);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            Register dst = asRegister(result);
            Register src = asRegister(input);

            assert dst.getRegisterCategory().equals(AArch64.SIMD) : dst.getRegisterCategory();

            AArch64Kind scalarKind = ((AArch64Kind) result.getPlatformKind()).getScalar();
            ElementSize eSize = ElementSize.fromKind(scalarKind);
            ElementSize fromKindElemSize = ElementSize.fromKind(input.getPlatformKind());
            assert eSize == fromKindElemSize : eSize + "!=" + input.getPlatformKind() + " " + fromKindElemSize;

            /*
             * Note that even if, in the case of a floating point input, dst and src are the same
             * register, the fmov must still be issued to guarantee the rest of the register is
             * zeroed out; it's possible for values to exist in upper parts of the register through
             * a cut or CastValue.
             */
            masm.fmov(eSize.bits() == 64 ? 64 : 32, dst, src);
        }
    }

    /**
     * This operand takes the specific (index, length) subset of the original vector and moves it to
     * offset 0 of the result vector.
     */
    @Opcode("VCUT")
    public static class VectorCut extends AArch64LIRInstruction {
        public static final LIRInstructionClass<VectorCut> TYPE = LIRInstructionClass.create(VectorCut.class);

        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue input;

        private int startIdx;

        public VectorCut(AllocatableValue result, AllocatableValue input, int startIdx) {
            super(TYPE);
            this.result = result;
            this.input = input;
            this.startIdx = startIdx;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            Register src = asRegister(input);
            Register dst = asRegister(result);
            assert src.getRegisterCategory().equals(AArch64.SIMD) : "Result reg category must be SIMD " + src.getRegisterCategory();

            ElementSize dstESize = ElementSize.fromKind(result.getPlatformKind());
            ElementSize srcESize = ElementSize.fromKind(input.getPlatformKind());
            assert result.getPlatformKind().getVectorLength() == 1 || srcESize == dstESize : "Result " + result.getPlatformKind() + " " + srcESize + " " + dstESize;

            PlatformKind resultPlatformKind = result.getPlatformKind();

            if (resultPlatformKind.getVectorLength() == 1) {
                /* Only moving 1 element. */
                masm.neon.moveFromIndex(dstESize, srcESize, dst, src, startIdx);
            } else if (this.startIdx == 0) {
                /* No transpose necessary, can just move the value. */
                masm.neon.moveVV(ASIMDSize.fromVectorKind(resultPlatformKind), dst, src);
            } else {
                /*
                 * Can view a cut as a element right rotate. Using input platform kind because it is
                 * guaranteed to be at least as large as the result platform kind.
                 */
                masm.neon.elementRor(ASIMDSize.fromVectorKind(input.getPlatformKind()), dstESize, dst, src, startIdx);
            }
        }
    }

    public static final class VectorInsert extends AArch64LIRInstruction {
        public static final LIRInstructionClass<VectorInsert> TYPE = LIRInstructionClass.create(VectorInsert.class);

        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue vector;
        @Alive({REG}) protected AllocatableValue val;
        private final int offset;

        public VectorInsert(AllocatableValue result, AllocatableValue vector, AllocatableValue val, int offset) {
            super(TYPE);
            this.result = result;
            this.vector = vector;
            this.val = val;
            this.offset = offset;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            AArch64Kind vecKind = (AArch64Kind) vector.getPlatformKind();
            int elementBits = vecKind.getScalar().getSizeInBytes() * Byte.SIZE;
            masm.neon.moveVV(vecKind.getSizeInBytes() == 16 ? ASIMDSize.FullReg : ASIMDSize.HalfReg, asRegister(result), asRegister(vector));
            masm.neon.moveToIndex(ElementSize.fromSize(elementBits), asRegister(result), asRegister(val), offset);
        }
    }

    public static final class VectorToBitMask extends AArch64LIRInstruction {
        public static final LIRInstructionClass<VectorToBitMask> TYPE = LIRInstructionClass.create(VectorToBitMask.class);

        @Def({REG}) protected AllocatableValue result;
        @Alive({REG}) protected AllocatableValue vector;
        @Temp({REG}) protected AllocatableValue xtmp;

        public VectorToBitMask(LIRGeneratorTool tool, AllocatableValue result, AllocatableValue vector) {
            super(TYPE);
            this.result = result;
            this.vector = vector;
            this.xtmp = tool.newVariable(vector.getValueKind());
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            AArch64Kind vecKind = (AArch64Kind) vector.getPlatformKind();
            ElementSize eSize = ElementSize.fromKind(vecKind.getScalar());
            if (vecKind.getVectorLength() == 16) {
                // Must be a vector of 16 bytes
                // Convert 0xFF to 0x81
                masm.neon.moveVI(ASIMDSize.FullReg, ElementSize.Byte, asRegister(xtmp), (byte) 0x81);
                masm.neon.andVVV(ASIMDSize.FullReg, asRegister(xtmp), asRegister(xtmp), asRegister(vector));
                // This compress 2 adjacent mask element into 1
                // 0x0000 -> 0x00, 0x0081 -> 0x01, 0x8100 -> 0x02, 0x8181 -> 0x03
                masm.neon.shrnVV(ElementSize.Byte, asRegister(xtmp), asRegister(xtmp), 7);
                // Now we have (_ means 0)
                // ______ab ______cd ______ef ______gh ______ij ______kl ______mn ______op
                masm.neon.umovGX(ElementSize.DoubleWord, asRegister(result), asRegister(xtmp), 0);
                masm.orr(64, asRegister(result), asRegister(result), asRegister(result), AArch64Assembler.ShiftType.LSR, 6);
                // ______ab ____abcd ____cdef ____efgh ____ghij ____ijkl ____klmn ____mnop
                masm.orr(64, asRegister(result), asRegister(result), asRegister(result), AArch64Assembler.ShiftType.LSR, 12);
                // ______ab ____abcd __abcdef abcdefgh cdefghij efghijkl ghijklmn ijklmnop
                masm.and(64, asRegister(result), asRegister(result), 0xFF000000FFL);
                // abcdefgh ________ ________ ________ ijklmnop
                masm.orr(64, asRegister(result), asRegister(result), asRegister(result), AArch64Assembler.ShiftType.LSR, 24);
                // abcdefgh ________ ________ abcdefgh ijklmnop
                masm.and(32, asRegister(result), asRegister(result), 0xFFFF);
                // abcdefgh ijklmnop
                return;
            }

            // All other cases, if it is 16 bytes, it can be narrowed to 8 bytes
            int eDistance = eSize.bits();
            if (vecKind.getSizeInBytes() == 16) {
                // abs and narrow down to a DoubleWord
                masm.neon.absVV(ASIMDSize.FullReg, eSize, asRegister(xtmp), asRegister(vector));
                masm.neon.xtnVV(ElementSize.fromSize(eSize.bits() / 2), asRegister(xtmp), asRegister(xtmp));
                eDistance = eSize.bits() / 2;
            } else {
                masm.neon.absVV(ASIMDSize.HalfReg, eSize, asRegister(xtmp), asRegister(vector));
            }
            // _______a _______b _______c _______d _______e _______f _______g _______h
            masm.neon.umovGX(ElementSize.DoubleWord, asRegister(result), asRegister(xtmp), 0);
            if (vecKind.getVectorLength() >= 2) {
                masm.orr(64, asRegister(result), asRegister(result), asRegister(result), AArch64Assembler.ShiftType.LSR, eDistance - 1);
                // _______a ______ab ______bc ______cd ______de ______ef ______fg ______gh
                if (vecKind.getVectorLength() >= 4) {
                    masm.orr(64, asRegister(result), asRegister(result), asRegister(result), AArch64Assembler.ShiftType.LSR, (eDistance - 1) * 2);
                    // _______a ______ab _____abc ____abcd ____bcde ____cdef ____defg ____efgh
                    if (vecKind.getVectorLength() == 8) {
                        masm.orr(64, asRegister(result), asRegister(result), asRegister(result), AArch64Assembler.ShiftType.LSR, (eDistance - 1) * 4);
                        // _______a ______ab _____abc ____abcd ___abcde __abcdef _abcdefg abcdefgh
                    }
                }
            }
            masm.and(32, asRegister(result), asRegister(result), CodeUtil.mask(vecKind.getVectorLength()));
            // abcdefgh
        }
    }

    /**
     * Combines two registers into a single register twice the width.
     */
    @Opcode("VCONCAT")
    public static class VectorConcat extends AArch64LIRInstruction {
        public static final LIRInstructionClass<VectorConcat> TYPE = LIRInstructionClass.create(VectorConcat.class);

        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue high;
        @Use({REG}) protected AllocatableValue low;

        public VectorConcat(AllocatableValue result, AllocatableValue high, AllocatableValue low) {
            super(TYPE);
            assert isValidConcat(result.getPlatformKind(), high, low) : "Must be a valid concat";
            int resultSize = result.getPlatformKind().getSizeInBytes();
            int highSize = high.getPlatformKind().getSizeInBytes() * 2;
            assert resultSize == highSize : resultSize + "!=" + highSize + " " + high.getPlatformKind();
            this.result = result;
            this.low = low;
            this.high = high;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            /*
             * Uses the zip1 instruction to combine the two values together. By treating each value
             * as an "element" of its entire length, then this instruction will place the values
             * next to each other.
             */
            int regBitSize = high.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            ElementSize eSize = ElementSize.fromSize(regBitSize);
            ASIMDSize size = ASIMDSize.fromVectorKind(result.getPlatformKind());

            masm.neon.zip1VVV(size, eSize, asRegister(result), asRegister(low), asRegister(high));

        }

        /**
         * Checks whether this operation can be performed on the provided inputs.
         *
         * Note I could add more concat options, but they aren't needed right now.
         */
        public static boolean isValidConcat(PlatformKind resultKind, AllocatableValue high, AllocatableValue low) {
            AArch64Kind kind = (AArch64Kind) high.getPlatformKind();
            assert kind == low.getPlatformKind() : kind + "!=" + low.getPlatformKind();
            assert resultKind.getSizeInBytes() == kind.getSizeInBytes() * 2 : resultKind.getSizeInBytes() + "!=" + kind.getSizeInBytes() * 2;
            return kind.getSizeInBytes() <= ASIMDSize.HalfReg.bytes();
        }
    }
}
