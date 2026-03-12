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

import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ShiftType;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

@Opcode("AArch64_INDEX_OF_ZERO")
public final class AArch64IndexOfZeroOp extends AArch64ComplexVectorOp {

    public static final LIRInstructionClass<AArch64IndexOfZeroOp> TYPE = LIRInstructionClass.create(AArch64IndexOfZeroOp.class);
    private static final int VECTOR_LOOP_SIZE = 32;
    private static final int MIN_PAGE_SIZE = 4096;

    private final Stride stride;

    @Def({REG}) protected AllocatableValue resultValue;
    @Alive({REG}) protected AllocatableValue arrayPtrValue;

    @Temp({REG}) protected AllocatableValue[] temp;
    @Temp({REG}) protected Value[] vectorTemp;

    public AArch64IndexOfZeroOp(Stride stride, LIRGeneratorTool tool, AllocatableValue result, AllocatableValue arrayPtr) {
        super(TYPE);
        GraalError.guarantee(result.getPlatformKind() == AArch64Kind.QWORD, "long value expected");
        GraalError.guarantee(arrayPtr.getPlatformKind() == AArch64Kind.QWORD, "pointer value expected");
        // Note: on SVM, this check is not sufficient - instead there's a check in
        // CEntryPointSnippets.createIsolate
        GraalError.guarantee((Unsafe.getUnsafe().pageSize() & (MIN_PAGE_SIZE - 1)) == 0, "OS page size must be a multiple of " + MIN_PAGE_SIZE);
        this.stride = stride;
        resultValue = result;
        arrayPtrValue = arrayPtr;
        temp = allocateTempRegisters(tool, 2);
        vectorTemp = allocateVectorRegisters(tool, 4, false);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register result = asRegister(resultValue);
        Register arrayPtr = asRegister(temp[0]);
        Register tmp = asRegister(temp[1]);

        Register vecArray1 = asRegister(vectorTemp[0]);
        Register vecArray2 = asRegister(vectorTemp[1]);
        Register vecZero = asRegister(vectorTemp[2]);
        Register vecTmp = asRegister(vectorTemp[3]);

        Label ret = new Label();
        Label slowPrologue = new Label();
        Label vectorMatchFound = new Label();
        Label vectorLoop = new Label();

        masm.mov(64, arrayPtr, asRegister(arrayPtrValue));
        // init zero vector
        masm.neon.moveVI(FullReg, ElementSize.Word, vecZero, 0);

        // tmp = arrayPtr % pageSize
        masm.and(64, tmp, asRegister(arrayPtrValue), MIN_PAGE_SIZE - 1);
        // check if a vector loop iteration load starting at the current pointer could cross a page
        // boundary. In this case, we have to take a slower path that takes care not to cross the
        // page boundary until all bytes up to the boundary are checked.
        masm.compare(64, tmp, MIN_PAGE_SIZE - VECTOR_LOOP_SIZE);
        masm.branchConditionally(ConditionFlag.GT, slowPrologue);

        // unaligned vector loads cannot cross page boundary - perform one unaligned peeled loop
        // iteration.

        // emit peeled loop iteration.
        emitVectorCompare(masm, arrayPtr, vecArray1, vecArray2, vecZero, vecTmp, vectorMatchFound);

        // align pointer to loop size.
        masm.bic(64, arrayPtr, arrayPtr, VECTOR_LOOP_SIZE - 1);

        // memory-aligned vector loop.
        masm.align(AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT);
        masm.bind(vectorLoop);
        emitVectorCompare(masm, arrayPtr, vecArray1, vecArray2, vecZero, vecTmp, vectorMatchFound);
        masm.jmp(vectorLoop);

        // calculate match index
        masm.align(AArch64MacroAssembler.PREFERRED_BRANCH_TARGET_ALIGNMENT);
        masm.bind(vectorMatchFound);
        try (ScratchRegister scratchReg = masm.getScratchRegister()) {
            Register scratch = scratchReg.getRegister();
            initCalcIndexOfFirstMatchMask(masm, vecTmp, scratch);
            calcIndexOfFirstMatch(masm, scratch, vecArray1, vecArray2, vecTmp, false);
            masm.sub(64, result, arrayPtr, asRegister(arrayPtrValue));
            masm.sub(64, result, result, VECTOR_LOOP_SIZE);
            masm.add(64, result, result, scratch, ShiftType.ASR, 1);
        }
        masm.jmp(ret);

        masm.bind(slowPrologue);
        // Unaligned vector load would cross page boundary - make sure to check all bytes up to the
        // page boundary before reading further. Otherwise, we may cause a segfault by prematurely
        // reading from an adjacent protected page.
        Label scalarLoop = new Label();
        Label scalarMatch = new Label();

        // tmp is still arrayPtr % pageSize.
        // tmp = -(arrayPtr % pageSize)
        masm.neg(64, tmp, tmp);
        // tmp = -(arrayPtr % pageSize) + pageSize
        masm.add(64, tmp, tmp, MIN_PAGE_SIZE);
        // tmp is now the delta between arrayPtr and the page boundary.

        // set array pointer to page boundary
        masm.add(64, arrayPtr, arrayPtr, tmp);
        // result = -delta
        masm.neg(64, result, tmp);

        masm.align(AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT);
        masm.bind(scalarLoop);
        try (ScratchRegister scratchReg = masm.getScratchRegister()) {
            Register scratch = scratchReg.getRegister();
            int memAccessSize = stride.value * Byte.SIZE;
            int compareSize = Math.max(32, memAccessSize);
            masm.ldr(memAccessSize, scratch, AArch64Address.createRegisterOffsetAddress(memAccessSize, arrayPtr, result, false));
            masm.cmp(compareSize, zr, scratch);
        }
        masm.branchConditionally(ConditionFlag.EQ, scalarMatch);
        // Add elementSize to the result and retry if the end of the search region has not yet
        // been reached, i.e., the result is still < 0.
        masm.adds(64, result, result, stride.value);
        masm.branchConditionally(ConditionFlag.MI, scalarLoop);
        // all bytes up to the page boundary have been checked without finding a match; now we can
        // continue with the aligned vector loop starting at the page boundary.
        masm.jmp(vectorLoop);

        masm.align(AArch64MacroAssembler.PREFERRED_BRANCH_TARGET_ALIGNMENT);
        masm.bind(scalarMatch);
        masm.add(64, result, result, tmp);
        masm.bind(ret);
    }

    private void emitVectorCompare(AArch64MacroAssembler masm, Register arrayPtr, Register vecArray1, Register vecArray2, Register vecZero, Register vecTmp, Label matchFound) {
        ElementSize eSize = ElementSize.fromStride(stride);
        masm.fldp(128, vecArray1, vecArray2, AArch64Address.createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arrayPtr, 32));
        masm.neon.cmeqVVV(FullReg, eSize, vecArray1, vecArray1, vecZero);
        masm.neon.cmeqVVV(FullReg, eSize, vecArray2, vecArray2, vecZero);
        masm.neon.orrVVV(FullReg, vecTmp, vecArray1, vecArray2);
        try (ScratchRegister sc = masm.getScratchRegister()) {
            /* If value != 0, then there was a match somewhere. */
            cbnzVector(masm, eSize, vecTmp, vecTmp, sc.getRegister(), true, matchFound);
        }
    }
}
