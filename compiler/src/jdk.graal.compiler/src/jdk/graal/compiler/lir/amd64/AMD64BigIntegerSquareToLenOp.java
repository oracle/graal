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
package jdk.graal.compiler.lir.amd64;

import static jdk.graal.compiler.lir.amd64.AMD64BigIntegerMulAddOp.multiplyAdd64;
import static jdk.graal.compiler.lir.amd64.AMD64BigIntegerMulAddOp.multiplyAdd64Bmi2;
import static jdk.graal.compiler.lir.amd64.AMD64BigIntegerMulAddOp.useBMI2Instructions;
import static jdk.vm.ci.amd64.AMD64.r10;
import static jdk.vm.ci.amd64.AMD64.r11;
import static jdk.vm.ci.amd64.AMD64.r12;
import static jdk.vm.ci.amd64.AMD64.r13;
import static jdk.vm.ci.amd64.AMD64.r14;
import static jdk.vm.ci.amd64.AMD64.r9;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rbx;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/43a2f17342af8f5bf1f5823df9fa0bf0bdfdfce2/src/hotspot/cpu/x86/stubGenerator_x86_64.cpp#L3146-L3190",
          sha1 = "ab70559cefe0dc177a290d417047955fba3ad1fc")
@SyncPort(from = "https://github.com/openjdk/jdk/blob/7bb59dc8da0c61c5da5c3aab5d56a6e4880001ce/src/hotspot/cpu/x86/macroAssembler_x86.cpp#L7369-L7682",
          sha1 = "2e4ea1436904cbd5a933eb8c687296d9bbefe4f0")
// @formatter:on
public final class AMD64BigIntegerSquareToLenOp extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64BigIntegerSquareToLenOp> TYPE = LIRInstructionClass.create(AMD64BigIntegerSquareToLenOp.class);

    @Use({OperandFlag.REG}) private Value xValue;
    @Use({OperandFlag.REG}) private Value lenValue;
    @Use({OperandFlag.REG}) private Value zValue;
    @Use({OperandFlag.REG}) private Value zlenValue;

    @Temp({OperandFlag.REG}) private Value tmp1Value;
    @Temp({OperandFlag.REG}) private Value[] tmpValues;

    public AMD64BigIntegerSquareToLenOp(
                    Value xValue,
                    Value lenValue,
                    Value zValue,
                    Value zlenValue,
                    Register heapBaseRegister) {
        super(TYPE);

        // Due to lack of allocatable registers, we use fixed registers and mark them as @Use+@Temp.
        // This allows the fixed registers to be reused for hosting temporary values.
        GraalError.guarantee(asRegister(xValue).equals(rdi), "expect xValue at rdi, but was %s", xValue);
        GraalError.guarantee(asRegister(lenValue).equals(rsi), "expect lenValue at rsi, but was %s", lenValue);
        GraalError.guarantee(asRegister(zValue).equals(r11), "expect zValue at r11, but was %s", zValue);
        GraalError.guarantee(asRegister(zlenValue).equals(rcx), "expect zlenValue at rcx, but was %s", zlenValue);

        this.xValue = xValue;
        this.lenValue = lenValue;
        this.zValue = zValue;
        this.zlenValue = zlenValue;

        this.tmp1Value = r12.equals(heapBaseRegister) ? r14.asValue() : r12.asValue();
        this.tmpValues = new Value[]{
                        rax.asValue(),
                        rcx.asValue(),
                        rdx.asValue(),
                        rbx.asValue(),
                        rsi.asValue(),
                        rdi.asValue(),
                        r9.asValue(),
                        r10.asValue(),
                        r11.asValue(),
                        r13.asValue(),
        };
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        GraalError.guarantee(xValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid xValue kind: %s", xValue);
        GraalError.guarantee(lenValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid lenValue kind: %s", lenValue);
        GraalError.guarantee(zValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid zValue kind: %s", zValue);
        GraalError.guarantee(zlenValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid zlenValue kind: %s", zlenValue);

        Register x = asRegister(xValue);
        Register len = asRegister(lenValue);
        Register z = asRegister(zValue);
        Register zlen = asRegister(zlenValue);

        Register tmp1 = asRegister(tmp1Value);
        Register tmp2 = r13;
        Register tmp3 = r9;
        Register tmp4 = r10;
        Register tmp5 = rbx;

        squareToLen(masm, x, len, z, zlen, tmp1, tmp2, tmp3, tmp4, tmp5, rdx, rax);
    }

    static void squareRshift(AMD64MacroAssembler masm, Register x, Register xlen, Register z,
                    Register tmp1, Register tmp4, Register tmp5, Register rdxReg, Register raxReg) {
        // Perform square and right shift by 1
        // Handle odd xlen case first, then for even xlen do the following
        // @formatter:off
        // jlong carry = 0;
        // for (int j=0, i=0; j < xlen; j+=2, i+=4) {
        //     huge_128 product = x[j:j+1] * x[j:j+1];
        //     z[i:i+1] = (carry << 63) | (jlong)(product >>> 65);
        //     z[i+2:i+3] = (jlong)(product >>> 1);
        //     carry = (jlong)product;
        // }
        // @formatter:on

        masm.xorq(tmp5, tmp5);     // carry
        masm.xorq(rdxReg, rdxReg);
        masm.xorl(tmp1, tmp1);     // index for x
        masm.xorl(tmp4, tmp4);     // index for z

        Label lFirstLoop = new Label();
        Label lFirstLoopExit = new Label();

        // jump if xlen is even
        masm.testlAndJcc(xlen, 1, ConditionFlag.Zero, lFirstLoop, true);

        // Square and right shift by 1 the odd element using 32 bit multiply
        masm.movl(raxReg, new AMD64Address(x, tmp1, Stride.S4, 0));
        masm.imulq(raxReg, raxReg);
        masm.shrq(raxReg, 1);
        masm.adcq(tmp5, 0);
        masm.movq(new AMD64Address(z, tmp4, Stride.S4, 0), raxReg);
        masm.incl(tmp1);
        masm.addl(tmp4, 2);

        // Square and right shift by 1 the rest using 64 bit multiply
        masm.bind(lFirstLoop);

        masm.cmpqAndJcc(tmp1, xlen, ConditionFlag.Equal, lFirstLoopExit, true);

        // Square
        masm.movq(raxReg, new AMD64Address(x, tmp1, Stride.S4, 0));
        masm.rorq(raxReg, 32);    // convert big-endian to little-endian
        masm.mulq(raxReg);        // 64-bit multiply rax * rax -> rdx:rax

        // Right shift by 1 and save carry
        masm.shrq(tmp5, 1);       // rdx:rax:tmp5 = (tmp5:rdx:rax) >>> 1
        masm.rcrq(rdxReg, 1);
        masm.rcrq(raxReg, 1);
        masm.adcq(tmp5, 0);

        // Store result in z
        masm.movq(new AMD64Address(z, tmp4, Stride.S4, 0), rdxReg);
        masm.movq(new AMD64Address(z, tmp4, Stride.S4, 8), raxReg);

        // Update indices for x and z
        masm.addl(tmp1, 2);
        masm.addl(tmp4, 4);
        masm.jmp(lFirstLoop);

        masm.bind(lFirstLoopExit);
    }

    static void addOne64(AMD64MacroAssembler masm, Register z, Register zlen, Register carry, Register tmp1) {
        Label lFourthLoop = new Label();
        Label lFourthLoopExit = new Label();

        masm.movl(tmp1, 1);
        masm.subl(zlen, 2);
        masm.addq(new AMD64Address(z, zlen, Stride.S4, 0), carry);

        masm.bind(lFourthLoop);
        masm.jccb(ConditionFlag.CarryClear, lFourthLoopExit);
        masm.sublAndJcc(zlen, 2, ConditionFlag.Negative, lFourthLoopExit, true);
        masm.addq(new AMD64Address(z, zlen, Stride.S4, 0), tmp1);
        masm.jmp(lFourthLoop);
        masm.bind(lFourthLoopExit);
    }

    static void lshiftBy1(AMD64MacroAssembler masm, Register z, Register zlen,
                    Register tmp1, Register tmp2, Register tmp3, Register tmp4) {

        Label lFifthLoop = new Label();
        Label lFifthLoopExit = new Label();

        // Fifth loop
        // Perform primitiveLeftShift(z, zlen, 1)

        Register prevCarry = tmp1;
        Register newCarry = tmp4;
        Register value = tmp2;
        Register zidx = tmp3;

        // @formatter:off
        // int zidx, carry;
        // long value;
        // carry = 0;
        // for (zidx = zlen-2; zidx >=0; zidx -= 2) {
        //    (carry:value)  = (z[i] << 1) | carry ;
        //    z[i] = value;
        // }
        // @formatter:on

        masm.movl(zidx, zlen);
        masm.xorl(prevCarry, prevCarry); // clear carry flag and prevCarry register

        masm.bind(lFifthLoop);
        masm.decl(zidx);  // Use decl to preserve carry flag
        masm.declAndJcc(zidx, ConditionFlag.Negative, lFifthLoopExit, true);

        if (useBMI2Instructions(masm)) {
            masm.movq(value, new AMD64Address(z, zidx, Stride.S4, 0));
            masm.rclq(value, 1);
            masm.rorxq(value, value, 32);
            // Store back in big endian form
            masm.movq(new AMD64Address(z, zidx, Stride.S4, 0), value);
        } else {
            // clear newCarry
            masm.xorl(newCarry, newCarry);

            // Shift z[i] by 1, or in previous carry and save new carry
            masm.movq(value, new AMD64Address(z, zidx, Stride.S4, 0));
            masm.shlq(value, 1);
            masm.adcl(newCarry, 0);

            masm.orq(value, prevCarry);
            masm.rorq(value, 0x20);
            // Store back in big endian form
            masm.movq(new AMD64Address(z, zidx, Stride.S4, 0), value);

            // Set previous carry = new carry
            masm.movl(prevCarry, newCarry);
        }
        masm.jmp(lFifthLoop);

        masm.bind(lFifthLoopExit);
    }

    private static void squareToLen(AMD64MacroAssembler masm, Register x, Register len, Register z, Register zlen,
                    Register tmp1, Register tmp2, Register tmp3, Register tmp4, Register tmp5, Register rdxReg, Register raxReg) {
        Label lSecondLoop = new Label();
        Label lSecondLoopExit = new Label();
        Label lThirdLoop = new Label();
        Label lThirdLoopExit = new Label();
        Label lLastX = new Label();
        Label lMultiply = new Label();

        // First loop
        // Store the squares, right shifted one bit (i.e., divided by 2).
        squareRshift(masm, x, len, z, tmp1, tmp4, tmp5, rdxReg, raxReg);

        // Add in off-diagonal sums.
        //
        // Second, third (nested) and fourth loops.
        // @formatter:off
        // zlen +=2;
        // for (int xidx=len-2,zidx=zlen-4; xidx > 0; xidx-=2,zidx-=4) {
        //    carry = 0;
        //    long op2 = x[xidx:xidx+1];
        //    for (int j=xidx-2,k=zidx; j >= 0; j-=2) {
        //       k -= 2;
        //       long op1 = x[j:j+1];
        //       long sum = z[k:k+1];
        //       carry:sum = multiply_add_64(sum, op1, op2, carry, tmp_regs);
        //       z[k:k+1] = sum;
        //    }
        //    add_one_64(z, k, carry, tmp_regs);
        // }
        // @formatter:on

        Register carry = tmp5;
        Register sum = tmp3;
        Register op1 = tmp4;
        Register op2 = tmp2;

        masm.push(zlen);
        masm.push(len);
        masm.addl(zlen, 2);
        masm.bind(lSecondLoop);
        masm.xorq(carry, carry);
        masm.subl(zlen, 4);
        masm.subl(len, 2);
        masm.push(zlen);
        masm.push(len);
        masm.cmplAndJcc(len, 0, ConditionFlag.LessEqual, lSecondLoopExit, true);

        // Multiply an array by one 64 bit long.
        if (useBMI2Instructions(masm)) {
            op2 = rdxReg;
            masm.movq(op2, new AMD64Address(x, len, Stride.S4, 0));
            masm.rorxq(op2, op2, 32);
        } else {
            masm.movq(op2, new AMD64Address(x, len, Stride.S4, 0));
            masm.rorq(op2, 32);
        }

        masm.bind(lThirdLoop);
        masm.declAndJcc(len, ConditionFlag.Negative, lThirdLoopExit, true);
        masm.declAndJcc(len, ConditionFlag.Negative, lLastX, true);

        masm.movq(op1, new AMD64Address(x, len, Stride.S4, 0));
        masm.rorq(op1, 32);

        masm.bind(lMultiply);
        masm.subl(zlen, 2);
        masm.movq(sum, new AMD64Address(z, zlen, Stride.S4, 0));

        // Multiply 64 bit by 64 bit and add 64 bits lower half and upper 64 bits as carry.
        if (useBMI2Instructions(masm)) {
            multiplyAdd64Bmi2(masm, sum, op1, op2, carry, tmp2);
        } else {
            multiplyAdd64(masm, sum, op1, op2, carry, rdxReg, raxReg);
        }

        masm.movq(new AMD64Address(z, zlen, Stride.S4, 0), sum);

        masm.jmp(lThirdLoop);
        masm.bind(lThirdLoopExit);

        // Fourth loop
        // Add 64 bit long carry into z with carry propagation.
        // Uses offsetted zlen.
        addOne64(masm, z, zlen, carry, tmp1);

        masm.pop(len);
        masm.pop(zlen);
        masm.jmp(lSecondLoop);

        // Next infrequent code is moved outside loops.
        masm.bind(lLastX);
        masm.movl(op1, new AMD64Address(x, 0));
        masm.jmp(lMultiply);

        masm.bind(lSecondLoopExit);
        masm.pop(len);
        masm.pop(zlen);
        masm.pop(len);
        masm.pop(zlen);

        // Fifth loop
        // Shift z left 1 bit.
        lshiftBy1(masm, z, zlen, tmp1, tmp2, tmp3, tmp4);

        // z[zlen-1] |= x[len-1] & 1;
        masm.movl(tmp3, new AMD64Address(x, len, Stride.S4, -4));
        masm.andl(tmp3, 1);
        masm.orl(new AMD64Address(z, zlen, Stride.S4, -4), tmp3);
    }

    @Override
    public boolean modifiesStackPointer() {
        return true;
    }
}
