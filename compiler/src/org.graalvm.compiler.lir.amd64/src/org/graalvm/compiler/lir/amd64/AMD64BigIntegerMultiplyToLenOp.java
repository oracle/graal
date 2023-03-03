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
package org.graalvm.compiler.lir.amd64;

import static jdk.vm.ci.amd64.AMD64.r10;
import static jdk.vm.ci.amd64.AMD64.r11;
import static jdk.vm.ci.amd64.AMD64.r12;
import static jdk.vm.ci.amd64.AMD64.r13;
import static jdk.vm.ci.amd64.AMD64.r14;
import static jdk.vm.ci.amd64.AMD64.r8;
import static jdk.vm.ci.amd64.AMD64.r9;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rbx;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.ADX;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.AVX;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.BMI2;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.StubPort;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

// @formatter:off
@StubPort(path      = "src/hotspot/cpu/x86/stubGenerator_x86_64.cpp",
          lineStart = 2949,
          lineEnd   = 3010,
          commit    = "db483a38a815f85bd9668749674b5f0f6e4b27b4",
          sha1      = "8ada7fcdb170eda06a7852fc90450193b2a0f7a0")
@StubPort(path      = "src/hotspot/cpu/x86/macroAssembler_x86.cpp",
          lineStart = 6144,
          lineEnd   = 6601,
          commit    = "db483a38a815f85bd9668749674b5f0f6e4b27b4",
          sha1      = "3b967007055fd0134e74b90898a6ab12dc531653")
// @formatter:on
public final class AMD64BigIntegerMultiplyToLenOp extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64BigIntegerMultiplyToLenOp> TYPE = LIRInstructionClass.create(AMD64BigIntegerMultiplyToLenOp.class);

    @Use({REG}) private Value xValue;
    @Use({REG}) private Value xlenValue;
    @Use({REG}) private Value yValue;
    @Use({REG}) private Value ylenValue;
    @Use({REG}) private Value zValue;
    @Use({REG}) private Value zlenValue;

    @Temp({REG}) private Value tmp1Value;
    @Temp({REG}) private Value[] tmpValues;

    public AMD64BigIntegerMultiplyToLenOp(
                    Value xValue,
                    Value xlenValue,
                    Value yValue,
                    Value ylenValue,
                    Value zValue,
                    Value zlenValue,
                    Register heapBaseRegister) {
        super(TYPE);

        // Due to lack of allocatable registers, we use fixed registers and mark them as @Use+@Temp.
        // This allows the fixed registers to be reused for hosting temporary values.
        GraalError.guarantee(asRegister(xValue).equals(rdi), "expect xValue at rdi, but was %s", xValue);
        GraalError.guarantee(asRegister(xlenValue).equals(rax), "expect xlenValue at rax, but was %s", xlenValue);
        GraalError.guarantee(asRegister(yValue).equals(rsi), "expect yValue at rsi, but was %s", yValue);
        GraalError.guarantee(asRegister(ylenValue).equals(rcx), "expect ylenValue at rcx, but was %s", ylenValue);
        GraalError.guarantee(asRegister(zValue).equals(r8), "expect zValue at r8, but was %s", zValue);
        GraalError.guarantee(asRegister(zlenValue).equals(r9), "expect zlenValue at r9, but was %s", zlenValue);

        this.xValue = xValue;
        this.xlenValue = xlenValue;
        this.yValue = yValue;
        this.ylenValue = ylenValue;
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
                        r8.asValue(),
                        r9.asValue(),
                        r10.asValue(),
                        r11.asValue(),
                        r13.asValue(),
        };
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        GraalError.guarantee(xValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid xValue kind: %s", xValue);
        GraalError.guarantee(xlenValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid xlenValue kind: %s", xlenValue);
        GraalError.guarantee(yValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid yValue kind: %s", yValue);
        GraalError.guarantee(ylenValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid ylenValue kind: %s", ylenValue);
        GraalError.guarantee(zValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid zValue kind: %s", zValue);
        GraalError.guarantee(zlenValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid zlenValue kind: %s", zlenValue);

        Register x = asRegister(xValue);
        Register xlen = asRegister(xlenValue);
        Register y = asRegister(yValue);
        Register ylen = asRegister(ylenValue);
        Register z = asRegister(zValue);
        Register zlen = asRegister(zlenValue);

        Register tmp1 = asRegister(tmp1Value);
        Register tmp2 = r13;
        Register tmp3 = r11;
        Register tmp4 = r10;
        Register tmp5 = rbx;

        multiplyToLen(masm, x, xlen, y, ylen, z, zlen, tmp1, tmp2, tmp3, tmp4, tmp5);
    }

    private static void add2WithCarry(AMD64MacroAssembler masm,
                    Register destHi,
                    Register destLo,
                    Register src1,
                    Register src2) {
        masm.addq(destLo, src1);
        masm.adcq(destHi, 0);
        masm.addq(destLo, src2);
        masm.adcq(destHi, 0);
    }

    /**
     * Multiply 64 bit by 64 bit first loop.
     */
    private static void multiply64x64Loop(AMD64MacroAssembler masm,
                    Register x,
                    Register xstart,
                    Register xAtXstart,
                    Register y,
                    Register yAtIdx,
                    Register z,
                    Register carry,
                    Register product,
                    Register idx,
                    Register kdx) {
        // @formatter:off
        //  jlong carry, x[], y[], z[];
        //  for (int idx=ystart, kdx=ystart+1+xstart; idx >= 0; idx-, kdx--) {
        //    huge_128 product = y[idx] * x[xstart] + carry;
        //    z[kdx] = (jlong)product;
        //    carry  = (jlong)(product >>> 64);
        //  }
        //  z[xstart] = carry;
        // @formatter:on

        Label labelFirstLoop = new Label();
        Label labelFirstLoopExit = new Label();
        Label labelOneX = new Label();
        Label labelOneY = new Label();
        Label labelMultiply = new Label();

        masm.declAndJcc(xstart, ConditionFlag.Negative, labelOneX, false);

        masm.movq(xAtXstart, new AMD64Address(x, xstart, Stride.S4, 0));
        masm.rorq(xAtXstart, 32); // convert big-endian to little-endian

        masm.bind(labelFirstLoop);
        masm.declAndJcc(idx, ConditionFlag.Negative, labelFirstLoopExit, false);
        masm.declAndJcc(idx, ConditionFlag.Negative, labelOneY, false);
        masm.movq(yAtIdx, new AMD64Address(y, idx, Stride.S4, 0));
        masm.rorq(yAtIdx, 32); // convert big-endian to little-endian
        masm.bind(labelMultiply);
        masm.movq(product, xAtXstart);
        masm.mulq(yAtIdx); // product(rax) * yAtIdx -> rdx:rax
        masm.addq(product, carry);
        masm.adcq(rdx, 0);
        masm.subl(kdx, 2);
        masm.movl(new AMD64Address(z, kdx, Stride.S4, 4), product);
        masm.shrq(product, 32);
        masm.movl(new AMD64Address(z, kdx, Stride.S4, 0), product);
        masm.movq(carry, rdx);
        masm.jmp(labelFirstLoop);

        masm.bind(labelOneY);
        masm.movl(yAtIdx, new AMD64Address(y));
        masm.jmp(labelMultiply);

        masm.bind(labelOneX);
        masm.movl(xAtXstart, new AMD64Address(x));
        masm.jmp(labelFirstLoop);

        masm.bind(labelFirstLoopExit);
    }

    /**
     * Multiply 64 bit by 64 bit and add 128 bit.
     */
    private static void multiplyAdd128x128(AMD64MacroAssembler masm,
                    Register xAtXstart,
                    Register y,
                    Register z,
                    Register yzAtIdx,
                    Register idx,
                    Register carry,
                    Register product,
                    int offset) {
        // huge_128 product = (y[idx] * xAtXstart) + z[kdx] + carry;
        // z[kdx] = (jlong)product;

        masm.movq(yzAtIdx, new AMD64Address(y, idx, Stride.S4, offset));
        masm.rorq(yzAtIdx, 32); // convert big-endian to little-endian
        masm.movq(product, xAtXstart);
        masm.mulq(yzAtIdx);     // product(rax) * yzAtIdx -> rdx:product(rax)
        masm.movq(yzAtIdx, new AMD64Address(z, idx, Stride.S4, offset));
        masm.rorq(yzAtIdx, 32); // convert big-endian to little-endian

        add2WithCarry(masm, rdx, product, carry, yzAtIdx);

        masm.movl(new AMD64Address(z, idx, Stride.S4, offset + 4), product);
        masm.shrq(product, 32);
        masm.movl(new AMD64Address(z, idx, Stride.S4, offset), product);
    }

    /**
     * Multiply 128 bit by 128 bit. Unrolled inner loop.
     */
    private static void multiply128x128Loop(AMD64MacroAssembler masm,
                    Register xAtXstart,
                    Register y,
                    Register z,
                    Register yzAtIdx,
                    Register idx,
                    Register jdx,
                    Register carry,
                    Register product,
                    Register carry2) {
        // @formatter:off
        //   jlong carry, x[], y[], z[];
        //   int kdx = ystart+1;
        //   for (int idx=ystart-2; idx >= 0; idx -= 2) { // Third loop
        //     huge_128 product = (y[idx+1] * xAtXstart) + z[kdx+idx+1] + carry;
        //     z[kdx+idx+1] = (jlong)product;
        //     jlong carry2  = (jlong)(product >>> 64);
        //     product = (y[idx] * xAtXstart) + z[kdx+idx] + carry2;
        //     z[kdx+idx] = (jlong)product;
        //     carry  = (jlong)(product >>> 64);
        //   }
        //   idx += 2;
        //   if (idx > 0) {
        //     product = (y[idx] * xAtXstart) + z[kdx+idx] + carry;
        //     z[kdx+idx] = (jlong)product;
        //     carry  = (jlong)(product >>> 64);
        //   }
        // @formatter:on

        Label labelThirdLoop = new Label();
        Label labelThirdLoopExit = new Label();
        Label labelPostThirdLoopDone = new Label();
        Label labelCheck1 = new Label();

        masm.movl(jdx, idx);
        masm.andl(jdx, 0xFFFFFFFC);
        masm.shrl(jdx, 2);

        masm.bind(labelThirdLoop);
        masm.sublAndJcc(jdx, 1, ConditionFlag.Negative, labelThirdLoopExit, false);
        masm.subl(idx, 4);

        multiplyAdd128x128(masm, xAtXstart, y, z, yzAtIdx, idx, carry, product, 8);
        masm.movq(carry2, rdx);

        multiplyAdd128x128(masm, xAtXstart, y, z, yzAtIdx, idx, carry2, product, 0);
        masm.movq(carry, rdx);
        masm.jmp(labelThirdLoop);

        masm.bind(labelThirdLoopExit);

        masm.andlAndJcc(idx, 0x3, ConditionFlag.Zero, labelPostThirdLoopDone, false);

        masm.sublAndJcc(idx, 2, ConditionFlag.Negative, labelCheck1, false);

        multiplyAdd128x128(masm, xAtXstart, y, z, yzAtIdx, idx, carry, product, 0);
        masm.movq(carry, rdx);

        masm.bind(labelCheck1);
        masm.addl(idx, 0x2);
        masm.andl(idx, 0x1);
        masm.sublAndJcc(idx, 1, ConditionFlag.Negative, labelPostThirdLoopDone, false);

        masm.movl(yzAtIdx, new AMD64Address(y, idx, Stride.S4, 0));
        masm.movq(product, xAtXstart);
        masm.mulq(yzAtIdx); // product(rax) * yzAtIdx -> rdx:product(rax)
        masm.movl(yzAtIdx, new AMD64Address(z, idx, Stride.S4, 0));

        add2WithCarry(masm, rdx, product, yzAtIdx, carry);

        masm.movl(new AMD64Address(z, idx, Stride.S4, 0), product);
        masm.shrq(product, 32);

        masm.shlq(rdx, 32);
        masm.orq(product, rdx);
        masm.movq(carry, product);

        masm.bind(labelPostThirdLoopDone);
    }

    /**
     * Multiply 128 bit by 128 bit using BMI2. Unrolled inner loop.
     */
    private static void multiply128x128BMI2Loop(AMD64MacroAssembler masm,
                    Register y,
                    Register z,
                    Register carry,
                    Register carry2,
                    Register idx,
                    Register jdx,
                    Register yzAtIdx1,
                    Register yzAtIdx2,
                    Register tmp,
                    Register tmp3,
                    Register tmp4) {
        GraalError.guarantee(masm.supports(BMI2) && masm.supports(AVX), "should be used only when BMI2 is available");

        // @formatter:off
        //   jlong carry, x[], y[], z[];
        //   int kdx = ystart+1;
        //   for (int idx=ystart-2; idx >= 0; idx -= 2) { // Third loop
        //     huge_128 tmp3 = (y[idx+1] * rdx) + z[kdx+idx+1] + carry;
        //     jlong carry2  = (jlong)(tmp3 >>> 64);
        //     huge_128 tmp4 = (y[idx]   * rdx) + z[kdx+idx] + carry2;
        //     carry  = (jlong)(tmp4 >>> 64);
        //     z[kdx+idx+1] = (jlong)tmp3;
        //     z[kdx+idx] = (jlong)tmp4;
        //   }
        //   idx += 2;
        //   if (idx > 0) {
        //     yzAtIdx1 = (y[idx] * rdx) + z[kdx+idx] + carry;
        //     z[kdx+idx] = (jlong)yzAtIdx1;
        //     carry  = (jlong)(yzAtIdx1 >>> 64);
        //   }
        // @formatter:on

        Label labelThirdLoop = new Label();
        Label labelThirdLoopExit = new Label();
        Label labelPostThirdLoopDone = new Label();
        Label labelCheck1 = new Label();

        masm.movl(jdx, idx);
        masm.andl(jdx, 0xFFFFFFFC);
        masm.shrl(jdx, 2);

        masm.bind(labelThirdLoop);
        masm.sublAndJcc(jdx, 1, ConditionFlag.Negative, labelThirdLoopExit, false);
        masm.subl(idx, 4);

        masm.movq(yzAtIdx1, new AMD64Address(y, idx, Stride.S4, 8));
        masm.rorxq(yzAtIdx1, yzAtIdx1, 32); // convert big-endian to little-endian
        masm.movq(yzAtIdx2, new AMD64Address(y, idx, Stride.S4, 0));
        masm.rorxq(yzAtIdx2, yzAtIdx2, 32);

        masm.mulxq(tmp4, tmp3, yzAtIdx1);  // yzAtIdx1 * rdx -> tmp4:tmp3
        masm.mulxq(carry2, tmp, yzAtIdx2); // yzAtIdx2 * rdx -> carry2:tmp

        masm.movq(yzAtIdx1, new AMD64Address(z, idx, Stride.S4, 8));
        masm.rorxq(yzAtIdx1, yzAtIdx1, 32);
        masm.movq(yzAtIdx2, new AMD64Address(z, idx, Stride.S4, 0));
        masm.rorxq(yzAtIdx2, yzAtIdx2, 32);

        if (masm.supports(ADX)) {
            masm.adcxq(tmp3, carry);
            masm.adoxq(tmp3, yzAtIdx1);

            masm.adcxq(tmp4, tmp);
            masm.adoxq(tmp4, yzAtIdx2);

            masm.movl(carry, 0); // does not affect flags
            masm.adcxq(carry2, carry);
            masm.adoxq(carry2, carry);
        } else {
            add2WithCarry(masm, tmp4, tmp3, carry, yzAtIdx1);
            add2WithCarry(masm, carry2, tmp4, tmp, yzAtIdx2);
        }
        masm.movq(carry, carry2);

        masm.movl(new AMD64Address(z, idx, Stride.S4, 12), tmp3);
        masm.shrq(tmp3, 32);
        masm.movl(new AMD64Address(z, idx, Stride.S4, 8), tmp3);

        masm.movl(new AMD64Address(z, idx, Stride.S4, 4), tmp4);
        masm.shrq(tmp4, 32);
        masm.movl(new AMD64Address(z, idx, Stride.S4, 0), tmp4);

        masm.jmp(labelThirdLoop);

        masm.bind(labelThirdLoopExit);

        masm.andlAndJcc(idx, 0x3, ConditionFlag.Zero, labelPostThirdLoopDone, false);

        masm.sublAndJcc(idx, 2, ConditionFlag.Negative, labelCheck1, false);

        masm.movq(yzAtIdx1, new AMD64Address(y, idx, Stride.S4, 0));
        masm.rorxq(yzAtIdx1, yzAtIdx1, 32);
        masm.mulxq(tmp4, tmp3, yzAtIdx1); // yzAtIdx1 * rdx -> tmp4:tmp3
        masm.movq(yzAtIdx2, new AMD64Address(z, idx, Stride.S4, 0));
        masm.rorxq(yzAtIdx2, yzAtIdx2, 32);

        add2WithCarry(masm, tmp4, tmp3, carry, yzAtIdx2);

        masm.movl(new AMD64Address(z, idx, Stride.S4, 4), tmp3);
        masm.shrq(tmp3, 32);
        masm.movl(new AMD64Address(z, idx, Stride.S4, 0), tmp3);
        masm.movq(carry, tmp4);

        masm.bind(labelCheck1);
        masm.addl(idx, 0x2);
        masm.andl(idx, 0x1);
        masm.sublAndJcc(idx, 1, ConditionFlag.Negative, labelPostThirdLoopDone, false);
        masm.movl(tmp4, new AMD64Address(y, idx, Stride.S4, 0));
        masm.mulxq(carry2, tmp3, tmp4);  // tmp4 * rdx -> carry2:tmp3
        masm.movl(tmp4, new AMD64Address(z, idx, Stride.S4, 0));

        add2WithCarry(masm, carry2, tmp3, tmp4, carry);

        masm.movl(new AMD64Address(z, idx, Stride.S4, 0), tmp3);
        masm.shrq(tmp3, 32);

        masm.shlq(carry2, 32);
        masm.orq(tmp3, carry2);
        masm.movq(carry, tmp3);

        masm.bind(labelPostThirdLoopDone);
    }

    private static void multiplyToLen(AMD64MacroAssembler masm,
                    Register x,
                    Register xlen,
                    Register y,
                    Register ylen,
                    Register z,
                    Register zlen,
                    Register tmp1,
                    Register tmp2,
                    Register tmp3,
                    Register tmp4,
                    Register tmp5) {
        Register idx = tmp1;
        Register kdx = tmp2;
        Register xstart = tmp3;
        Register yAtIdx = tmp4;
        Register carry = tmp5;

        Register product = xlen;
        Register xAtXstart = zlen;

        Label labelDone = new Label();
        Label labelSecondLoop = new Label();
        Label labelCarry = new Label();
        Label labelLastX = new Label();
        Label labelThirdLoopPrologue = new Label();

        boolean useBMI2Instructions = masm.supports(BMI2) && masm.supports(AVX);

        // @formatter:off
        // First Loop.
        //
        //  final static long LONG_MASK = 0xffffffffL;
        //  int xstart = xlen - 1;
        //  int ystart = ylen - 1;
        //  long carry = 0;
        //  for (int idx=ystart, kdx=ystart+1+xstart; idx >= 0; idx-, kdx--) {
        //    long product = (y[idx] & LONG_MASK) * (x[xstart] & LONG_MASK) + carry;
        //    z[kdx] = (int)product;
        //    carry = product >>> 32;
        //  }
        //  z[xstart] = (int)carry;
        // @formatter:on

        masm.movl(idx, ylen);      // idx = ylen;
        masm.movl(kdx, zlen);      // kdx = xlen+ylen;
        masm.xorq(carry, carry);   // carry = 0;

        masm.movl(xstart, xlen);
        masm.declAndJcc(xstart, ConditionFlag.Negative, labelDone, false);

        multiply64x64Loop(masm, x, xstart, xAtXstart, y, yAtIdx, z, carry, product, idx, kdx);

        masm.testlAndJcc(kdx, kdx, ConditionFlag.Zero, labelSecondLoop, false);

        masm.sublAndJcc(kdx, 1, ConditionFlag.Zero, labelCarry, false);

        masm.movl(new AMD64Address(z, kdx, Stride.S4, 0), carry);
        masm.shrq(carry, 32);
        masm.subl(kdx, 1);

        masm.bind(labelCarry);
        masm.movl(new AMD64Address(z, kdx, Stride.S4, 0), carry);

        // @formatter:off
        // Second and third (nested) loops.
        //
        // for (int i = xstart-1; i >= 0; i--) { // Second loop
        //   carry = 0;
        //   for (int jdx=ystart, k=ystart+1+i; jdx >= 0; jdx--, k--) { // Third loop
        //     long product = (y[jdx] & LONG_MASK) * (x[i] & LONG_MASK) +
        //                    (z[k] & LONG_MASK) + carry;
        //     z[k] = (int)product;
        //     carry = product >>> 32;
        //   }
        //   z[i] = (int)carry;
        // }
        //
        // i = xlen, j = tmp1, k = tmp2, carry = tmp5, x[i] = rdx
        // @formatter:on

        Register jdx = tmp1;

        masm.bind(labelSecondLoop);
        masm.xorq(carry, carry);    // carry = 0;
        masm.movl(jdx, ylen);       // j = ystart+1
        // i = xstart-1;
        masm.sublAndJcc(xstart, 1, ConditionFlag.Negative, labelDone, false);

        masm.push(z);

        // z = z + k - j
        masm.leaq(z, new AMD64Address(z, xstart, Stride.S4, 4));
        // i = xstart-1;
        masm.sublAndJcc(xstart, 1, ConditionFlag.Negative, labelLastX, false);

        if (useBMI2Instructions) {
            masm.movq(rdx, new AMD64Address(x, xstart, Stride.S4, 0));
            masm.rorxq(rdx, rdx, 32); // convert big-endian to little-endian
        } else {
            masm.movq(xAtXstart, new AMD64Address(x, xstart, Stride.S4, 0));
            masm.rorq(xAtXstart, 32);  // convert big-endian to little-endian
        }

        masm.bind(labelThirdLoopPrologue);

        masm.push(x);
        masm.push(xstart);
        masm.push(ylen);

        if (useBMI2Instructions) {
            multiply128x128BMI2Loop(masm, y, z, carry, x, jdx, ylen, product, tmp2, xAtXstart, tmp3, tmp4);
        } else { // !UseBMI2Instructions
            multiply128x128Loop(masm, xAtXstart, y, z, yAtIdx, jdx, ylen, carry, product, x);
        }

        masm.pop(ylen);
        masm.pop(xlen);
        masm.pop(x);
        masm.pop(z);

        masm.movl(tmp3, xlen);
        masm.addl(tmp3, 1);
        masm.movl(new AMD64Address(z, tmp3, Stride.S4, 0), carry);
        masm.sublAndJcc(tmp3, 1, ConditionFlag.Negative, labelDone, false);

        masm.shrq(carry, 32);
        masm.movl(new AMD64Address(z, tmp3, Stride.S4, 0), carry);
        masm.jmp(labelSecondLoop);

        // Next infrequent code is moved outside loops.
        masm.bind(labelLastX);

        if (useBMI2Instructions) {
            masm.movl(rdx, new AMD64Address(x));
        } else {
            masm.movl(xAtXstart, new AMD64Address(x));
        }
        masm.jmp(labelThirdLoopPrologue);

        masm.bind(labelDone);
    }
}
