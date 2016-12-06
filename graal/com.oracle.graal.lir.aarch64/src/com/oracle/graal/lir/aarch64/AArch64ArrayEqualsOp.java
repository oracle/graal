/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.lir.aarch64;

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import java.lang.reflect.Array;
import java.lang.reflect.Field;

import com.oracle.graal.asm.Label;
import com.oracle.graal.asm.aarch64.AArch64Address;
import com.oracle.graal.asm.aarch64.AArch64Assembler.ConditionFlag;
import com.oracle.graal.asm.aarch64.AArch64MacroAssembler;
import com.oracle.graal.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.Opcode;
import com.oracle.graal.lir.asm.CompilationResultBuilder;
import com.oracle.graal.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import sun.misc.Unsafe;

/**
 * Emits code which compares two arrays of the same length. If the CPU supports any vector
 * instructions specialized code is emitted to leverage these instructions.
 */
@Opcode("ARRAY_EQUALS")
public final class AArch64ArrayEqualsOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64ArrayEqualsOp> TYPE = LIRInstructionClass.create(AArch64ArrayEqualsOp.class);

    private final JavaKind kind;
    private final int arrayBaseOffset;
    private final int arrayIndexScale;

    @Def({REG}) protected Value resultValue;
    @Alive({REG}) protected Value array1Value;
    @Alive({REG}) protected Value array2Value;
    @Alive({REG}) protected Value lengthValue;
    @Temp({REG}) protected Value temp1;
    @Temp({REG}) protected Value temp2;
    @Temp({REG}) protected Value temp3;
    @Temp({REG}) protected Value temp4;
    @Temp({REG, ILLEGAL}) protected Value vectorTemp1;
    @Temp({REG, ILLEGAL}) protected Value vectorTemp2;

    public AArch64ArrayEqualsOp(LIRGeneratorTool tool, JavaKind kind, Value result, Value array1, Value array2, Value length) {
        super(TYPE);
        this.kind = kind;

        Class<?> arrayClass = Array.newInstance(kind.toJavaClass(), 0).getClass();
        this.arrayBaseOffset = UNSAFE.arrayBaseOffset(arrayClass);
        this.arrayIndexScale = UNSAFE.arrayIndexScale(arrayClass);

        this.resultValue = result;
        this.array1Value = array1;
        this.array2Value = array2;
        this.lengthValue = length;

        // Allocate some temporaries.
        this.temp1 = tool.newVariable(LIRKind.unknownReference(tool.target().arch.getWordKind()));
        this.temp2 = tool.newVariable(LIRKind.unknownReference(tool.target().arch.getWordKind()));
        this.temp3 = tool.newVariable(LIRKind.value(tool.target().arch.getWordKind()));
        this.temp4 = tool.newVariable(LIRKind.value(tool.target().arch.getWordKind()));

        this.vectorTemp1 = tool.newVariable(LIRKind.value(tool.target().arch.getWordKind()));
        this.vectorTemp2 = tool.newVariable(LIRKind.value(tool.target().arch.getWordKind()));
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register result = asRegister(resultValue);
        Register array1 = asRegister(temp1);
        Register array2 = asRegister(temp2);
        Register length = asRegister(temp3);

        Label trueLabel = new Label();
        Label falseLabel = new Label();
        Label done = new Label();

        try (ScratchRegister sc1 = masm.getScratchRegister()) {
            Register rscratch1 = sc1.getRegister();
            // Load array base addresses.
            masm.lea(array1, AArch64Address.createUnscaledImmediateAddress(asRegister(array1Value), arrayBaseOffset));
            masm.lea(array2, AArch64Address.createUnscaledImmediateAddress(asRegister(array2Value), arrayBaseOffset));

            // Get array length in bytes.
            masm.mov(rscratch1, arrayIndexScale);
            masm.smaddl(length, asRegister(lengthValue), rscratch1, zr);
            masm.mov(64, result, length); // copy

            emit8ByteCompare(crb, masm, result, array1, array2, length, trueLabel, falseLabel, rscratch1);
            emitTailCompares(masm, result, array1, array2, trueLabel, falseLabel, rscratch1);

            // Return true
            masm.bind(trueLabel);
            masm.mov(result, 1);
            masm.jmp(done);

            // Return false
            masm.bind(falseLabel);
            masm.mov(32, result, zr);

            // That's it
            masm.bind(done);
        }
    }

    /**
     * Vector size used in {@link #emit8ByteCompare}.
     */
    private static final int VECTOR_SIZE = 8;

    /**
     * Emits code that uses 8-byte vector compares.
     *
     */
    private void emit8ByteCompare(CompilationResultBuilder crb, AArch64MacroAssembler masm, Register result, Register array1, Register array2, Register length, Label trueLabel, Label falseLabel,
                    Register rscratch1) {
        Label loop = new Label();
        Label compareTail = new Label();

        Register temp = asRegister(temp4);

        masm.and(64, result, result, VECTOR_SIZE - 1); // tail count (in bytes)
        masm.ands(64, length, length, ~(VECTOR_SIZE - 1));  // vector count (in bytes)
        masm.branchConditionally(ConditionFlag.EQ, compareTail);

        masm.lea(array1, AArch64Address.createRegisterOffsetAddress(array1, length, false));
        masm.lea(array2, AArch64Address.createRegisterOffsetAddress(array2, length, false));
        masm.sub(64, length, zr, length);

        // Align the main loop
        masm.align(crb.target.wordSize * 2);
        {
            masm.bind(loop);
            masm.ldr(64, temp, AArch64Address.createRegisterOffsetAddress(array1, length, false));
            masm.ldr(64, rscratch1, AArch64Address.createRegisterOffsetAddress(array2, length, false));
            masm.eor(64, rscratch1, temp, rscratch1);
            masm.cbnz(64, rscratch1, falseLabel);
            masm.add(64, length, length, VECTOR_SIZE);
            masm.cbnz(64, length, loop);
        }

        masm.cbz(64, result, trueLabel);

        /*
         * Compare the remaining bytes with an unaligned memory load aligned to the end of the
         * array.
         */
        masm.lea(array1, AArch64Address.createUnscaledImmediateAddress(array1, -VECTOR_SIZE));
        masm.lea(array2, AArch64Address.createUnscaledImmediateAddress(array2, -VECTOR_SIZE));
        masm.ldr(64, temp, AArch64Address.createRegisterOffsetAddress(array1, result, false));
        masm.ldr(64, rscratch1, AArch64Address.createRegisterOffsetAddress(array2, result, false));
        masm.eor(64, rscratch1, temp, rscratch1);
        masm.cbnz(64, rscratch1, falseLabel);
        masm.jmp(trueLabel);

        masm.bind(compareTail);
        masm.mov(64, length, result);
    }

    /**
     * Emits code to compare the remaining 1 to 4 bytes.
     *
     */
    private void emitTailCompares(AArch64MacroAssembler masm, Register result, Register array1, Register array2, Label trueLabel, Label falseLabel, Register rscratch1) {
        Label compare2Bytes = new Label();
        Label compare1Byte = new Label();

        Register temp = asRegister(temp4);

        if (kind.getByteCount() <= 4) {
            // Compare trailing 4 bytes, if any.
            masm.ands(32, zr, result, 4);
            masm.branchConditionally(ConditionFlag.EQ, compare2Bytes);
            masm.ldr(32, temp, AArch64Address.createBaseRegisterOnlyAddress(array1));
            masm.ldr(32, rscratch1, AArch64Address.createBaseRegisterOnlyAddress(array2));
            masm.eor(32, rscratch1, temp, rscratch1);
            masm.cbnz(32, rscratch1, falseLabel);

            if (kind.getByteCount() <= 2) {
                // Move array pointers forward.
                masm.lea(array1, AArch64Address.createUnscaledImmediateAddress(array1, 4));
                masm.lea(array2, AArch64Address.createUnscaledImmediateAddress(array2, 4));

                // Compare trailing 2 bytes, if any.
                masm.bind(compare2Bytes);
                masm.ands(32, zr, result, 2);
                masm.branchConditionally(ConditionFlag.EQ, compare1Byte);
                masm.ldr(16, temp, AArch64Address.createBaseRegisterOnlyAddress(array1));
                masm.ldr(16, rscratch1, AArch64Address.createBaseRegisterOnlyAddress(array2));
                masm.eor(32, rscratch1, temp, rscratch1);
                masm.cbnz(32, rscratch1, falseLabel);

                // The one-byte tail compare is only required for boolean and byte arrays.
                if (kind.getByteCount() <= 1) {
                    // Move array pointers forward before we compare the last trailing byte.
                    masm.lea(array1, AArch64Address.createUnscaledImmediateAddress(array1, 2));
                    masm.lea(array2, AArch64Address.createUnscaledImmediateAddress(array2, 2));

                    // Compare trailing byte, if any.
                    masm.bind(compare1Byte);
                    masm.ands(32, zr, result, 1);
                    masm.branchConditionally(ConditionFlag.EQ, trueLabel);
                    masm.ldr(8, temp, AArch64Address.createBaseRegisterOnlyAddress(array1));
                    masm.ldr(8, rscratch1, AArch64Address.createBaseRegisterOnlyAddress(array2));
                    masm.eor(32, rscratch1, temp, rscratch1);
                    masm.cbnz(32, rscratch1, falseLabel);
                } else {
                    masm.bind(compare1Byte);
                }
            } else {
                masm.bind(compare2Bytes);
            }
        }
    }

    private static final Unsafe UNSAFE = initUnsafe();

    private static Unsafe initUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException se) {
            try {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (Unsafe) theUnsafe.get(Unsafe.class);
            } catch (Exception e) {
                throw new RuntimeException("exception while trying to get Unsafe", e);
            }
        }
    }
}
