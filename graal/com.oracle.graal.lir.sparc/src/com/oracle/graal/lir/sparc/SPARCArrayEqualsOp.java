/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.sparc;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.compiler.common.UnsafeAccess.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;
import static com.oracle.graal.sparc.SPARC.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.CC.*;

import java.lang.reflect.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Cmp;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Mov;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Nop;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.gen.*;

/**
 * Emits code which compares two arrays of the same length.
 */
@Opcode("ARRAY_EQUALS")
public class SPARCArrayEqualsOp extends SPARCLIRInstruction {

    private final Kind kind;
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
    @Temp({REG}) protected Value temp5;

    public SPARCArrayEqualsOp(LIRGeneratorTool tool, Kind kind, Value result, Value array1, Value array2, Value length) {
        this.kind = kind;

        Class<?> arrayClass = Array.newInstance(kind.toJavaClass(), 0).getClass();
        this.arrayBaseOffset = unsafe.arrayBaseOffset(arrayClass);
        this.arrayIndexScale = unsafe.arrayIndexScale(arrayClass);

        this.resultValue = result;
        this.array1Value = array1;
        this.array2Value = array2;
        this.lengthValue = length;

        // Allocate some temporaries.
        this.temp1 = tool.newVariable(LIRKind.derivedReference(tool.target().wordKind));
        this.temp2 = tool.newVariable(LIRKind.derivedReference(tool.target().wordKind));
        this.temp3 = tool.newVariable(LIRKind.value(tool.target().wordKind));
        this.temp4 = tool.newVariable(LIRKind.value(tool.target().wordKind));
        this.temp5 = tool.newVariable(LIRKind.value(tool.target().wordKind));
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        Register result = asRegister(resultValue);
        Register array1 = asRegister(temp1);
        Register array2 = asRegister(temp2);
        Register length = asRegister(temp3);

        Label trueLabel = new Label();
        Label falseLabel = new Label();
        Label done = new Label();

        // Load array base addresses.
        new Add(asObjectReg(array1Value), arrayBaseOffset, array1).emit(masm);
        new Add(asObjectReg(array2Value), arrayBaseOffset, array2).emit(masm);

        // Get array length in bytes.
        new Mulx(asIntReg(lengthValue), arrayIndexScale, length).emit(masm);
        new Mov(length, result).emit(masm); // copy

        emit8ByteCompare(masm, result, array1, array2, length, trueLabel, falseLabel);
        emitTailCompares(masm, result, array1, array2, trueLabel, falseLabel);

        // Return true
        masm.bind(trueLabel);
        new Mov(1, result).emit(masm);
        new Bpa(done).emit(masm);
        new Nop().emit(masm);

        // Return false
        masm.bind(falseLabel);
        new Mov(g0, result).emit(masm);

        // That's it
        masm.bind(done);
    }

    /**
     * Vector size used in {@link #emit8ByteCompare}.
     */
    private static final int VECTOR_SIZE = 8;

    /**
     * Emits code that uses 8-byte vector compares.
     */
    private void emit8ByteCompare(SPARCMacroAssembler masm, Register result, Register array1, Register array2, Register length, Label trueLabel, Label falseLabel) {
        Label loop = new Label();
        Label compareTail = new Label();
        // new Ldx(new SPARCAddress(o6, 3), g0).emit(masm);
        Register tempReg1 = asRegister(temp4);
        Register tempReg2 = asRegister(temp5);
        new And(result, VECTOR_SIZE - 1, result).emit(masm); // tail count (in bytes)
        new Andcc(length, ~(VECTOR_SIZE - 1), length).emit(masm);  // vector count (in bytes)
        new Bpe(CC.Xcc, compareTail).emit(masm);
        new Nop().emit(masm);

        Label compareTailCorrectVectorEnd = new Label();
        new Sub(length, VECTOR_SIZE, length).emit(masm);
        new Add(array1, length, array1).emit(masm);
        new Add(array2, length, array2).emit(masm);
        new Sub(g0, length, length).emit(masm);

        // Compare the last element first
        new Ldx(new SPARCAddress(array1, 0), tempReg1).emit(masm);
        new Ldx(new SPARCAddress(array2, 0), tempReg2).emit(masm);
        new Cmp(tempReg1, tempReg2).emit(masm);
        new Bpne(Xcc, true, false, falseLabel).emit(masm);
        new Nop().emit(masm);
        new Bpr(RCondition.Rc_z, false, false, length, compareTailCorrectVectorEnd).emit(masm);
        new Nop().emit(masm);

        // Load the first value from array 1 (Later done in back branch delay-slot)
        new Ldx(new SPARCAddress(array1, length), tempReg1).emit(masm);
        masm.bind(loop);
        new Ldx(new SPARCAddress(array2, length), tempReg2).emit(masm);
        new Cmp(tempReg1, tempReg2).emit(masm);
        new Bpne(Xcc, false, false, falseLabel).emit(masm);
        // Delay slot, not annul, add for next iteration
        new Addcc(length, VECTOR_SIZE, length).emit(masm);
        new Bpne(Xcc, true, true, loop).emit(masm); // Annul, to prevent access past the array
        new Ldx(new SPARCAddress(array1, length), tempReg1).emit(masm); // Load in delay slot

        // Tail count zero, therefore we can go to the end
        new Bpr(RCondition.Rc_z, true, true, result, trueLabel).emit(masm);
        new Nop().emit(masm);

        masm.bind(compareTailCorrectVectorEnd);
        // Correct the array pointers
        new Add(array1, VECTOR_SIZE, array1).emit(masm);
        new Add(array2, VECTOR_SIZE, array2).emit(masm);

        masm.bind(compareTail);
    }

    /**
     * Emits code to compare the remaining 1 to 4 bytes.
     */
    private void emitTailCompares(SPARCMacroAssembler masm, Register result, Register array1, Register array2, Label trueLabel, Label falseLabel) {
        Label compare2Bytes = new Label();
        Label compare1Byte = new Label();

        Register tempReg1 = asRegister(temp3);
        Register tempReg2 = asRegister(temp4);

        if (kind.getByteCount() <= 4) {
            // Compare trailing 4 bytes, if any.
            new Cmp(result, 4).emit(masm);
            new Bpl(Xcc, false, false, compare2Bytes).emit(masm);
            new Nop().emit(masm);
            new Lduw(new SPARCAddress(array1, 0), tempReg1).emit(masm);
            new Lduw(new SPARCAddress(array2, 0), tempReg2).emit(masm);
            new Cmp(tempReg1, tempReg2).emit(masm);

            new Bpne(Xcc, false, false, falseLabel).emit(masm);
            new Nop().emit(masm);

            if (kind.getByteCount() <= 2) {
                // Move array pointers forward.
                new Add(array1, 4, array1).emit(masm);
                new Add(array2, 4, array2).emit(masm);
                new Sub(result, 4, result).emit(masm);

                // Compare trailing 2 bytes, if any.
                masm.bind(compare2Bytes);

                new Cmp(result, 2).emit(masm);
                new Bpl(Xcc, false, true, compare1Byte).emit(masm);
                new Nop().emit(masm);
                new Lduh(new SPARCAddress(array1, 0), tempReg1).emit(masm);
                new Lduh(new SPARCAddress(array2, 0), tempReg2).emit(masm);
                new Cmp(tempReg1, tempReg2).emit(masm);
                new Bpne(Xcc, false, true, falseLabel).emit(masm);
                new Nop().emit(masm);

                // The one-byte tail compare is only required for boolean and byte arrays.
                if (kind.getByteCount() <= 1) {
                    // Move array pointers forward before we compare the last trailing byte.
                    new Add(array1, 2, array1).emit(masm);
                    new Add(array2, 2, array2).emit(masm);
                    new Sub(result, 2, result).emit(masm);

                    // Compare trailing byte, if any.
                    masm.bind(compare1Byte);
                    new Cmp(result, 1).emit(masm);
                    new Bpne(Xcc, trueLabel).emit(masm);
                    new Nop().emit(masm);
                    new Ldub(new SPARCAddress(array1, 0), tempReg1).emit(masm);
                    new Ldub(new SPARCAddress(array2, 0), tempReg2).emit(masm);
                    new Cmp(tempReg1, tempReg2).emit(masm);
                    new Bpne(Xcc, falseLabel).emit(masm);
                    new Nop().emit(masm);
                } else {
                    masm.bind(compare1Byte);
                }
            } else {
                masm.bind(compare2Bytes);
            }
        }
    }
}
