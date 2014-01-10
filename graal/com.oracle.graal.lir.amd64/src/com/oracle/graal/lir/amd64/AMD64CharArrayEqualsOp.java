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
package com.oracle.graal.lir.amd64;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;
import sun.misc.*;

import com.oracle.graal.amd64.*;
import com.oracle.graal.amd64.AMD64.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.asm.amd64.AMD64Address.Scale;
import com.oracle.graal.asm.amd64.AMD64Assembler.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Emits code which compares two {@code char[]}. If the CPU supports any vector instructions
 * specialized code is emitted to leverage these instructions.
 */
@Opcode("CHAR_ARRAY_EQUALS")
public class AMD64CharArrayEqualsOp extends AMD64LIRInstruction {

    @Def({REG}) protected Value resultValue;
    @Alive({REG}) protected Value array1Value;
    @Alive({REG}) protected Value array2Value;
    @Alive({REG}) protected Value lengthValue;
    @Temp({REG}) protected Value temp1;
    @Temp({REG}) protected Value temp2;
    @Temp({REG}) protected Value temp3;
    @Temp({REG}) protected Value temp4;
    @Temp({REG}) protected Value vectorTemp1;
    @Temp({REG}) protected Value vectorTemp2;

    public AMD64CharArrayEqualsOp(LIRGeneratorTool tool, Value result, Value array1, Value array2, Value length) {
        this.resultValue = result;
        this.array1Value = array1;
        this.array2Value = array2;
        this.lengthValue = length;

        // Allocate some temporaries.
        this.temp1 = tool.newVariable(tool.target().wordKind);
        this.temp2 = tool.newVariable(tool.target().wordKind);
        this.temp3 = tool.newVariable(tool.target().wordKind);
        this.temp4 = tool.newVariable(tool.target().wordKind);

        // We only need the vector temporaries if we generate SSE code.
        if (supportsSSE41(tool.target())) {
            this.vectorTemp1 = tool.newVariable(Kind.Double);
            this.vectorTemp2 = tool.newVariable(Kind.Double);
        }
    }

    /**
     * Returns if the underlying AMD64 architecture supports SSE 4.1 instructions.
     * 
     * @param target target description of the underlying architecture
     * @return true if the underlying architecture supports SSE 4.1
     */
    private static boolean supportsSSE41(TargetDescription target) {
        AMD64 arch = (AMD64) target.arch;
        return arch.getFeatures().contains(CPUFeature.SSE4_1);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register result = asRegister(resultValue);
        Register array1 = asRegister(temp1);
        Register array2 = asRegister(temp2);
        Register length = asRegister(temp3);

        Label trueLabel = new Label();
        Label falseLabel = new Label();

        // Load array base addresses.
        masm.leaq(array1, new AMD64Address(asRegister(array1Value), Unsafe.ARRAY_CHAR_BASE_OFFSET));
        masm.leaq(array2, new AMD64Address(asRegister(array2Value), Unsafe.ARRAY_CHAR_BASE_OFFSET));

        masm.movq(length, asRegister(lengthValue));
        masm.shll(length, 1); // get length in bytes
        masm.movl(result, length); // copy

        if (supportsSSE41(crb.target)) {
            emitSSE41Compare(crb, masm, result, array1, array2, length, falseLabel, trueLabel);
            // Fall-through to tail compare.
        }
        emit4ByteCompare(crb, masm, result, array1, array2, length, falseLabel, trueLabel);
    }

    /**
     * Vector size used in {@link #emit4ByteCompare}.
     */
    private static final int VECTOR_SIZE = 4;

    /**
     * Emits code that uses 4-byte vector compares.
     */
    private void emit4ByteCompare(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register result, Register array1, Register array2, Register length, Label falseLabel, Label trueLabel) {
        Label compareVectors = new Label();
        Label compareChar = new Label();
        Label done = new Label();
        Register temp = asRegister(temp4);

        masm.andl(length, 0xfffffffc); // vector count (in bytes)
        masm.jccb(ConditionFlag.Zero, compareChar);

        masm.leaq(array1, new AMD64Address(array1, length, Scale.Times1, 0));
        masm.leaq(array2, new AMD64Address(array2, length, Scale.Times1, 0));
        masm.negq(length);

        // Align the main loop
        masm.align(crb.target.wordSize * 2);
        masm.bind(compareVectors);
        masm.movl(temp, new AMD64Address(array1, length, Scale.Times1, 0));
        masm.cmpl(temp, new AMD64Address(array2, length, Scale.Times1, 0));
        masm.jccb(ConditionFlag.NotEqual, falseLabel);
        masm.addq(length, VECTOR_SIZE);
        masm.jcc(ConditionFlag.NotZero, compareVectors);

        // Compare trailing char (final 2 bytes), if any
        masm.bind(compareChar);
        masm.testl(result, 0x2); // tail char
        masm.jccb(ConditionFlag.Zero, trueLabel);
        masm.movzwl(temp, new AMD64Address(array1, 0));
        masm.movzwl(length, new AMD64Address(array2, 0));
        masm.cmpl(temp, length);
        masm.jccb(ConditionFlag.NotEqual, falseLabel);

        masm.bind(trueLabel);
        masm.movl(result, 1); // return true
        masm.jmpb(done);

        masm.bind(falseLabel);
        masm.xorl(result, result); // return false

        // That's it
        masm.bind(done);
    }

    /**
     * Vector size used in {@link #emitSSE41Compare}.
     */
    private static final int SSE4_1_VECTOR_SIZE = 16;

    /**
     * Emits code that uses SSE4.1 128-bit (16-byte) vector compares.
     */
    private void emitSSE41Compare(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register result, Register array1, Register array2, Register length, Label falseLabel, Label trueLabel) {
        assert supportsSSE41(crb.target);

        Register vector1 = asDoubleReg(vectorTemp1);
        Register vector2 = asDoubleReg(vectorTemp2);

        Label compareWideVectors = new Label();
        Label compareTail = new Label();

        // Compare 16-byte vectors
        masm.andl(result, 0x0000000e); // tail count (in bytes)
        masm.andl(length, 0xfffffff0); // vector count (in bytes)
        masm.jccb(ConditionFlag.Zero, compareTail);

        masm.leaq(array1, new AMD64Address(array1, length, Scale.Times1, 0));
        masm.leaq(array2, new AMD64Address(array2, length, Scale.Times1, 0));
        masm.negq(length);

        // Align the main loop
        masm.align(crb.target.wordSize * 2);
        masm.bind(compareWideVectors);
        masm.movdqu(vector1, new AMD64Address(array1, length, Scale.Times1, 0));
        masm.movdqu(vector2, new AMD64Address(array2, length, Scale.Times1, 0));
        masm.pxor(vector1, vector2);

        masm.ptest(vector1, vector1);
        masm.jccb(ConditionFlag.NotZero, falseLabel);
        masm.addq(length, SSE4_1_VECTOR_SIZE);
        masm.jcc(ConditionFlag.NotZero, compareWideVectors);

        masm.testl(result, result);
        masm.jccb(ConditionFlag.Zero, trueLabel);

        masm.movdqu(vector1, new AMD64Address(array1, result, Scale.Times1, -SSE4_1_VECTOR_SIZE));
        masm.movdqu(vector2, new AMD64Address(array2, result, Scale.Times1, -SSE4_1_VECTOR_SIZE));
        masm.pxor(vector1, vector2);

        masm.ptest(vector1, vector1);
        masm.jccb(ConditionFlag.NotZero, falseLabel);
        masm.jmpb(trueLabel);

        masm.bind(compareTail); // length is zero
        masm.movl(length, result);
    }
}
