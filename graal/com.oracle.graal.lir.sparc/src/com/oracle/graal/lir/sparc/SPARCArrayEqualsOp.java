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
package com.oracle.graal.lir.sparc;

import com.oracle.jvmci.code.Register;
import com.oracle.jvmci.meta.LIRKind;
import com.oracle.jvmci.meta.Value;
import com.oracle.jvmci.meta.Kind;
import com.oracle.jvmci.sparc.SPARC.*;

import static com.oracle.jvmci.code.ValueUtil.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Annul.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.BranchPredict.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.CC.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.ConditionFlag.*;
import static com.oracle.graal.asm.sparc.SPARCAssembler.RCondition.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;
import static com.oracle.jvmci.common.UnsafeAccess.*;
import static com.oracle.jvmci.sparc.SPARC.*;
import static com.oracle.jvmci.sparc.SPARC.CPUFeature.*;

import java.lang.reflect.*;

import com.oracle.graal.asm.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCAssembler.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.gen.*;

/**
 * Emits code which compares two arrays of the same length.
 */
@Opcode("ARRAY_EQUALS")
public final class SPARCArrayEqualsOp extends SPARCLIRInstruction {
    public static final LIRInstructionClass<SPARCArrayEqualsOp> TYPE = LIRInstructionClass.create(SPARCArrayEqualsOp.class);

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
        super(TYPE);
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
        masm.add(asObjectReg(array1Value), arrayBaseOffset, array1);
        masm.add(asObjectReg(array2Value), arrayBaseOffset, array2);

        // Get array length in bytes.
        masm.mulx(asIntReg(lengthValue), arrayIndexScale, length);
        masm.mov(length, result); // copy

        emit8ByteCompare(masm, result, array1, array2, length, trueLabel, falseLabel);
        emitTailCompares(masm, result, array1, array2, trueLabel, falseLabel);

        // Return true
        masm.bind(trueLabel);
        masm.mov(1, result);
        masm.bicc(Always, ANNUL, done);
        masm.nop();

        // Return false
        masm.bind(falseLabel);
        masm.mov(g0, result);

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
        assert lengthValue.getPlatformKind().equals(Kind.Int);
        Label loop = new Label();
        Label compareTail = new Label();
        Label compareTailCorrectVectorEnd = new Label();

        Register tempReg1 = asRegister(temp4);
        Register tempReg2 = asRegister(temp5);

        boolean hasCBcond = masm.hasFeature(CPUFeature.CBCOND);

        masm.sra(length, 0, length);
        masm.and(result, VECTOR_SIZE - 1, result); // tail count (in bytes)
        masm.andcc(length, ~(VECTOR_SIZE - 1), length);  // vector count (in bytes)
        masm.bpcc(ConditionFlag.Equal, NOT_ANNUL, compareTail, CC.Xcc, PREDICT_NOT_TAKEN);

        masm.sub(length, VECTOR_SIZE, length); // Delay slot
        masm.add(array1, length, array1);
        masm.add(array2, length, array2);
        masm.sub(g0, length, length);

        // Compare the last element first
        masm.ldx(new SPARCAddress(array1, 0), tempReg1);
        masm.ldx(new SPARCAddress(array2, 0), tempReg2);
        if (hasCBcond) {
            masm.cbcondx(NotEqual, tempReg1, tempReg2, falseLabel);
            masm.nop(); // for optimal performance (see manual)
            masm.cbcondx(Equal, length, 0, compareTailCorrectVectorEnd);
            masm.nop(); // for optimal performance (see manual)
        } else {
            masm.cmp(tempReg1, tempReg2);
            masm.bpcc(NotEqual, NOT_ANNUL, falseLabel, Xcc, PREDICT_NOT_TAKEN);
            masm.nop();
            masm.bpr(Rc_z, NOT_ANNUL, compareTailCorrectVectorEnd, PREDICT_NOT_TAKEN, length);
            masm.nop();
        }

        // Load the first value from array 1 (Later done in back branch delay-slot)
        masm.ldx(new SPARCAddress(array1, length), tempReg1);
        masm.bind(loop);
        masm.ldx(new SPARCAddress(array2, length), tempReg2);
        masm.cmp(tempReg1, tempReg2);
        masm.bpcc(NotEqual, NOT_ANNUL, falseLabel, Xcc, PREDICT_NOT_TAKEN);
        // Delay slot, not annul, add for next iteration
        masm.addcc(length, VECTOR_SIZE, length);
        // Annul, to prevent access past the array
        masm.bpcc(NotEqual, ANNUL, loop, Xcc, PREDICT_TAKEN);
        masm.ldx(new SPARCAddress(array1, length), tempReg1); // Load in delay slot

        // Tail count zero, therefore we can go to the end
        if (hasCBcond) {
            masm.cbcondx(Equal, result, 0, trueLabel);
        } else {
            masm.bpr(Rc_z, NOT_ANNUL, trueLabel, PREDICT_TAKEN, result);
            masm.nop();
        }

        masm.bind(compareTailCorrectVectorEnd);
        // Correct the array pointers
        masm.add(array1, VECTOR_SIZE, array1);
        masm.add(array2, VECTOR_SIZE, array2);

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
        boolean hasCBcond = masm.hasFeature(CBCOND);

        if (kind.getByteCount() <= 4) {
            // Compare trailing 4 bytes, if any.
            if (hasCBcond) {
                masm.cbcondx(Less, result, 4, compare2Bytes);
            } else {
                masm.cmp(result, 4);
                masm.bpcc(Less, NOT_ANNUL, compare2Bytes, Xcc, PREDICT_NOT_TAKEN);
                masm.nop();
            }

            masm.lduw(new SPARCAddress(array1, 0), tempReg1);
            masm.lduw(new SPARCAddress(array2, 0), tempReg2);

            if (hasCBcond) {
                masm.cbcondx(NotEqual, tempReg1, tempReg2, falseLabel);
            } else {
                masm.cmp(tempReg1, tempReg2);
                masm.bpcc(NotEqual, NOT_ANNUL, falseLabel, Xcc, PREDICT_NOT_TAKEN);
                masm.nop();
            }

            if (kind.getByteCount() <= 2) {
                // Move array pointers forward.
                masm.add(array1, 4, array1);
                masm.add(array2, 4, array2);
                masm.sub(result, 4, result);

                // Compare trailing 2 bytes, if any.
                masm.bind(compare2Bytes);

                if (hasCBcond) {
                    masm.cbcondx(Less, result, 2, compare1Byte);
                } else {
                    masm.cmp(result, 2);
                    masm.bpcc(Less, NOT_ANNUL, compare1Byte, Xcc, PREDICT_TAKEN);
                    masm.nop();
                }

                masm.lduh(new SPARCAddress(array1, 0), tempReg1);
                masm.lduh(new SPARCAddress(array2, 0), tempReg2);

                if (hasCBcond) {
                    masm.cbcondx(NotEqual, tempReg1, tempReg2, falseLabel);
                } else {
                    masm.cmp(tempReg1, tempReg2);
                    masm.bpcc(NotEqual, NOT_ANNUL, falseLabel, Xcc, PREDICT_TAKEN);
                    masm.nop();
                }

                // The one-byte tail compare is only required for boolean and byte arrays.
                if (kind.getByteCount() <= 1) {
                    // Move array pointers forward before we compare the last trailing byte.
                    masm.add(array1, 2, array1);
                    masm.add(array2, 2, array2);
                    masm.sub(result, 2, result);

                    // Compare trailing byte, if any.
                    masm.bind(compare1Byte);
                    if (hasCBcond) {
                        masm.cbcondx(NotEqual, result, 1, trueLabel);
                    } else {
                        masm.cmp(result, 1);
                        masm.bpcc(NotEqual, NOT_ANNUL, trueLabel, Xcc, PREDICT_TAKEN);
                        masm.nop();
                    }
                    masm.ldub(new SPARCAddress(array1, 0), tempReg1);
                    masm.ldub(new SPARCAddress(array2, 0), tempReg2);
                    if (hasCBcond) {
                        masm.cbcondx(NotEqual, tempReg1, tempReg2, falseLabel);
                    } else {
                        masm.cmp(tempReg1, tempReg2);
                        masm.bpcc(NotEqual, NOT_ANNUL, falseLabel, Xcc, PREDICT_TAKEN);
                        masm.nop();
                    }
                } else {
                    masm.bind(compare1Byte);
                }
            } else {
                masm.bind(compare2Bytes);
            }
        }
    }
}
