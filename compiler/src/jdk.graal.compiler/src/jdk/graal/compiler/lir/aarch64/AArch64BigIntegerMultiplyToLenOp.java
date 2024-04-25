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
package jdk.graal.compiler.lir.aarch64;

import static jdk.vm.ci.aarch64.AArch64.r10;
import static jdk.vm.ci.aarch64.AArch64.r11;
import static jdk.vm.ci.aarch64.AArch64.r12;
import static jdk.vm.ci.aarch64.AArch64.r13;
import static jdk.vm.ci.aarch64.AArch64.r14;
import static jdk.vm.ci.aarch64.AArch64.r15;
import static jdk.vm.ci.aarch64.AArch64.r16;
import static jdk.vm.ci.aarch64.AArch64.r17;
import static jdk.vm.ci.aarch64.AArch64.r19;
import static jdk.vm.ci.aarch64.AArch64.r20;
import static jdk.vm.ci.aarch64.AArch64.r21;
import static jdk.vm.ci.aarch64.AArch64.r22;
import static jdk.vm.ci.aarch64.AArch64.r23;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag.MI;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ExtendType.UXTW;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/ce8399fd6071766114f5f201b6e44a7abdba9f5a/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L4642-L4680",
          sha1 = "9c106817eae54d0e6783c1442b26fee08bc7a07a")
@SyncPort(from = "https://github.com/openjdk/jdk/blob/7bb59dc8da0c61c5da5c3aab5d56a6e4880001ce/src/hotspot/cpu/aarch64/macroAssembler_aarch64.cpp#L3505-L3514",
          sha1 = "376de6fbb2caccaac53c4aa934ce96f8f0dc7f18")
@SyncPort(from = "https://github.com/openjdk/jdk/blob/7bb59dc8da0c61c5da5c3aab5d56a6e4880001ce/src/hotspot/cpu/aarch64/macroAssembler_aarch64.cpp#L3702-L4012",
          sha1 = "dfdfc5113a04698da12c5cb29bc78ced09a2eb63")
// @formatter:on
public final class AArch64BigIntegerMultiplyToLenOp extends AArch64LIRInstruction {

    public static final LIRInstructionClass<AArch64BigIntegerMultiplyToLenOp> TYPE = LIRInstructionClass.create(AArch64BigIntegerMultiplyToLenOp.class);

    @Alive({REG}) private Value xValue;
    @Alive({REG}) private Value xlenValue;
    @Alive({REG}) private Value yValue;
    @Alive({REG}) private Value ylenValue;
    @Alive({REG}) private Value zValue;
    @Alive({REG}) private Value zlenValue;

    @Temp protected Value[] temps;

    public AArch64BigIntegerMultiplyToLenOp(
                    Value xValue,
                    Value xlenValue,
                    Value yValue,
                    Value ylenValue,
                    Value zValue,
                    Value zlenValue) {
        super(TYPE);

        this.xValue = xValue;
        this.xlenValue = xlenValue;
        this.yValue = yValue;
        this.ylenValue = ylenValue;
        this.zValue = zValue;
        this.zlenValue = zlenValue;

        this.temps = new Value[]{
                        r10.asValue(),
                        r11.asValue(),
                        r12.asValue(),
                        r13.asValue(),
                        r14.asValue(),
                        r15.asValue(),
                        r16.asValue(),
                        r17.asValue(),
                        r19.asValue(),
                        r20.asValue(),
                        r21.asValue(),
                        r22.asValue(),
                        r23.asValue(),
        };
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        GraalError.guarantee(xValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid xValue kind: %s", xValue);
        GraalError.guarantee(xlenValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid xlenValue kind: %s", xlenValue);
        GraalError.guarantee(yValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid yValue kind: %s", yValue);
        GraalError.guarantee(ylenValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid ylenValue kind: %s", ylenValue);
        GraalError.guarantee(zValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid zValue kind: %s", zValue);
        GraalError.guarantee(zlenValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid zlenValue kind: %s", zlenValue);

        Register x = asRegister(xValue);
        Register xlen = asRegister(xlenValue);
        Register y = asRegister(yValue);
        Register ylen = asRegister(ylenValue);
        Register z = asRegister(zValue);
        Register zlen = asRegister(zlenValue);

        multiplyToLen(masm, x, xlen, y, ylen, z, zlen,
                        r10, r11, r12, r13, r14, r15, r16, r17,
                        r19, r20, r21, r22, r23);
    }

    private static void add2WithCarry(AArch64MacroAssembler masm,
                    Register finalDestHi,
                    Register destHi,
                    Register destLo,
                    Register src1,
                    Register src2) {
        masm.adds(64, destLo, destLo, src1);
        masm.adc(64, destHi, destHi, zr);
        masm.adds(64, destLo, destLo, src2);
        masm.adc(64, finalDestHi, destHi, zr);
    }

    /**
     * Multiply 64 bit by 64 bit first loop.
     */
    private static void multiply64x64Loop(AArch64MacroAssembler masm,
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

        try (ScratchRegister sr1 = masm.getScratchRegister();
                        ScratchRegister sr2 = masm.getScratchRegister()) {
            Register rscratch1 = sr1.getRegister();
            Register rscratch2 = sr2.getRegister();

            masm.subs(32, xstart, xstart, 1);
            masm.branchConditionally(MI, labelOneX);

            masm.loadAddress(rscratch1, AArch64Address.createRegisterOffsetAddress(32, x, xstart, true));
            masm.ldr(64, xAtXstart, AArch64Address.createBaseRegisterOnlyAddress(64, rscratch1));
            masm.ror(64, xAtXstart, xAtXstart, 32); // convert big-endian to little-endian

            masm.bind(labelFirstLoop);
            masm.subs(32, idx, idx, 1);
            masm.branchConditionally(MI, labelFirstLoopExit);
            masm.subs(32, idx, idx, 1);
            masm.branchConditionally(MI, labelOneY);
            masm.loadAddress(rscratch1, AArch64Address.createExtendedRegisterOffsetAddress(32, y, idx, true, UXTW));
            masm.ldr(64, yAtIdx, AArch64Address.createBaseRegisterOnlyAddress(64, rscratch1));
            masm.ror(64, yAtIdx, yAtIdx, 32); // convert big-endian to little-endian
            masm.bind(labelMultiply);

            // AArch64 has a multiply-accumulate instruction that we can't use
            // here because it has no way to process carries, so we have to use
            // separate add and adc instructions. Bah.
            masm.umulh(64, rscratch1, xAtXstart, yAtIdx); // xAtXstart * yAtIdx -> rscratch1:product
            masm.mul(64, product, xAtXstart, yAtIdx);
            masm.adds(64, product, product, carry);
            masm.adc(64, carry, rscratch1, zr);   // xAtXstart * yAtIdx + carry -> carry:product

            masm.sub(32, kdx, kdx, 2);
            masm.ror(64, product, product, 32); // back to big-endian
            masm.loadAddress(rscratch2, AArch64Address.createExtendedRegisterOffsetAddress(32, z, kdx, true, UXTW));
            masm.str(64, product, AArch64Address.createBaseRegisterOnlyAddress(64, rscratch2));

            masm.jmp(labelFirstLoop);

            masm.bind(labelOneY);
            masm.ldr(32, yAtIdx, AArch64Address.createBaseRegisterOnlyAddress(32, y));
            masm.jmp(labelMultiply);

            masm.bind(labelOneX);
            masm.ldr(32, xAtXstart, AArch64Address.createBaseRegisterOnlyAddress(32, x));
            masm.jmp(labelFirstLoop);

            masm.bind(labelFirstLoopExit);
        }
    }

    /**
     * Multiply 128 bit by 128. Unrolled inner loop.
     *
     */
    private static void multiply128x128Loop(AArch64MacroAssembler masm,
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
                    Register tmp4,
                    Register tmp6,
                    Register productHi) {
        // @formatter:off
        //   jlong carry, x[], y[], z[];
        //   int kdx = ystart+1;
        //   for (int idx=ystart-2; idx >= 0; idx -= 2) { // Third loop
        //     huge_128 tmp3 = (y[idx+1] * productHi) + z[kdx+idx+1] + carry;
        //     jlong carry2  = (jlong)(tmp3 >>> 64);
        //     huge_128 tmp4 = (y[idx]   * productHi) + z[kdx+idx] + carry2;
        //     carry  = (jlong)(tmp4 >>> 64);
        //     z[kdx+idx+1] = (jlong)tmp3;
        //     z[kdx+idx] = (jlong)tmp4;
        //   }
        //   idx += 2;
        //   if (idx > 0) {
        //     yzAtIdx1 = (y[idx] * productHi) + z[kdx+idx] + carry;
        //     z[kdx+idx] = (jlong)yzAtIdx1;
        //     carry  = (jlong)(yzAtIdx1 >>> 64);
        //   }
        // @formatter:on

        Label labelThirdLoop = new Label();
        Label labelThirdLoopExit = new Label();
        Label labelPostThirdLoopDone = new Label();
        Label labelCheck1 = new Label();

        try (ScratchRegister sr1 = masm.getScratchRegister();
                        ScratchRegister sr2 = masm.getScratchRegister()) {
            Register rscratch1 = sr1.getRegister();
            Register rscratch2 = sr2.getRegister();

            masm.lsr(32, jdx, idx, 2);

            masm.bind(labelThirdLoop);

            masm.subs(32, jdx, jdx, 1);
            masm.branchConditionally(MI, labelThirdLoopExit);
            masm.sub(32, idx, idx, 4);

            masm.loadAddress(rscratch1, AArch64Address.createExtendedRegisterOffsetAddress(32, y, idx, true, UXTW));

            masm.ldp(64, yzAtIdx2, yzAtIdx1, AArch64Address.createPairBaseRegisterOnlyAddress(64, rscratch1));

            masm.loadAddress(tmp6, AArch64Address.createExtendedRegisterOffsetAddress(32, z, idx, true, UXTW));

            masm.ror(64, yzAtIdx1, yzAtIdx1, 32); // convert big-endian to little-endian
            masm.ror(64, yzAtIdx2, yzAtIdx2, 32);

            masm.ldp(64, rscratch2, rscratch1, AArch64Address.createPairBaseRegisterOnlyAddress(64, tmp6));

            masm.mul(64, tmp3, productHi, yzAtIdx1);  // yzAtIdx1 * productHi -> tmp4:tmp3
            masm.umulh(64, tmp4, productHi, yzAtIdx1);

            masm.ror(64, rscratch1, rscratch1, 32); // convert big-endian to little-endian
            masm.ror(64, rscratch2, rscratch2, 32);

            masm.mul(64, tmp, productHi, yzAtIdx2);   // yzAtIdx2 * productHi -> carry2:tmp
            masm.umulh(64, carry2, productHi, yzAtIdx2);

            // propagate sum of both multiplications into carry:tmp4:tmp3
            masm.adds(64, tmp3, tmp3, carry);
            masm.adc(64, tmp4, tmp4, zr);
            masm.adds(64, tmp3, tmp3, rscratch1);
            masm.adcs(64, tmp4, tmp4, tmp);
            masm.adc(64, carry, carry2, zr);
            masm.adds(64, tmp4, tmp4, rscratch2);
            masm.adc(64, carry, carry, zr);

            masm.ror(64, tmp3, tmp3, 32); // convert little-endian to big-endian
            masm.ror(64, tmp4, tmp4, 32);
            masm.stp(64, tmp4, tmp3, AArch64Address.createPairBaseRegisterOnlyAddress(64, tmp6));

            masm.jmp(labelThirdLoop);
            masm.bind(labelThirdLoopExit);

            masm.and(32, idx, idx, 0x3);
            masm.cbz(32, idx, labelPostThirdLoopDone);

            masm.subs(32, idx, idx, 2);
            masm.branchConditionally(MI, labelCheck1);

            masm.loadAddress(rscratch1, AArch64Address.createExtendedRegisterOffsetAddress(32, y, idx, true, UXTW));
            masm.ldr(64, yzAtIdx1, AArch64Address.createBaseRegisterOnlyAddress(64, rscratch1));
            masm.ror(64, yzAtIdx1, yzAtIdx1, 32);
            masm.mul(64, tmp3, productHi, yzAtIdx1);  // yzAtIdx1 * productHi -> tmp4:tmp3
            masm.umulh(64, tmp4, productHi, yzAtIdx1);
            masm.loadAddress(rscratch1, AArch64Address.createExtendedRegisterOffsetAddress(32, z, idx, true, UXTW));
            masm.ldr(64, yzAtIdx2, AArch64Address.createBaseRegisterOnlyAddress(64, rscratch1));
            masm.ror(64, yzAtIdx2, yzAtIdx2, 32);

            add2WithCarry(masm, carry, tmp4, tmp3, carry, yzAtIdx2);

            masm.ror(64, tmp3, tmp3, 32);
            masm.str(64, tmp3, AArch64Address.createBaseRegisterOnlyAddress(64, rscratch1));

            masm.bind(labelCheck1);

            masm.and(32, idx, idx, 0x1);
            masm.subs(32, idx, idx, 1);
            masm.branchConditionally(MI, labelPostThirdLoopDone);
            masm.ldr(32, tmp4, AArch64Address.createExtendedRegisterOffsetAddress(32, y, idx, true, UXTW));
            masm.mul(64, tmp3, tmp4, productHi);  // tmp4 * productHi -> carry2:tmp3
            masm.umulh(64, carry2, tmp4, productHi);
            masm.ldr(32, tmp4, AArch64Address.createExtendedRegisterOffsetAddress(32, z, idx, true, UXTW));

            add2WithCarry(masm, carry2, carry2, tmp3, tmp4, carry);

            masm.str(32, tmp3, AArch64Address.createExtendedRegisterOffsetAddress(32, z, idx, true, UXTW));
            masm.extr(64, carry, carry2, tmp3, 32);

            masm.bind(labelPostThirdLoopDone);
        }
    }

    static void multiplyToLen(AArch64MacroAssembler masm,
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
                    Register tmp5,
                    Register tmp6,
                    Register tmp7,
                    Register tmp8,
                    Register tmp9,
                    Register tmp10,
                    Register tmp11,
                    Register tmp12,
                    Register tmp13) {
        Register idx = tmp1;
        Register kdx = tmp2;
        Register xstart = tmp3;

        Register yAtIdx = tmp4;
        Register carry = tmp5;
        Register productHi = tmp7;

        Register product = tmp8;
        Register xAtXstart = tmp9;

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

        Label labelDone = new Label();
        Label labelSecondLoop = new Label();
        Label labelCarry = new Label();
        Label labelLastX = new Label();
        Label labelThirdLoopPrologue = new Label();

        masm.mov(32, idx, ylen);      // idx = ylen;
        masm.mov(32, kdx, zlen);      // kdx = xlen+ylen;
        masm.mov(64, carry, zr);      // carry = 0;

        masm.mov(32, xstart, xlen);
        masm.subs(32, xstart, xstart, 1);
        masm.branchConditionally(MI, labelDone);

        multiply64x64Loop(masm, x, xstart, xAtXstart, y, yAtIdx, z, carry, product, idx, kdx);

        masm.cbz(32, kdx, labelSecondLoop);

        masm.sub(32, kdx, kdx, 1);
        masm.cbz(32, kdx, labelCarry);

        masm.str(32, carry, AArch64Address.createExtendedRegisterOffsetAddress(32, z, kdx, true, UXTW));
        masm.lsr(64, carry, carry, 32);
        masm.sub(32, kdx, kdx, 1);

        masm.bind(labelCarry);
        masm.str(32, carry, AArch64Address.createExtendedRegisterOffsetAddress(32, z, kdx, true, UXTW));

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
        // i = xlen, j = tmp1, k = tmp2, carry = tmp5, x[i] = productHi
        // @formatter:on

        Register jdx = tmp1;
        Register newZ = tmp10;

        masm.bind(labelSecondLoop);
        masm.mov(64, carry, zr); // carry = 0;
        masm.mov(32, jdx, ylen); // j = ystart+1

        masm.subs(32, xstart, xstart, 1); // i = xstart-1;
        masm.branchConditionally(MI, labelDone);

        // // z = z + k - j
        masm.loadAddress(newZ, AArch64Address.createExtendedRegisterOffsetAddress(32, z, xstart, true, UXTW));
        masm.add(64, newZ, newZ, 4);
        masm.subs(32, xstart, xstart, 1);       // i = xstart-1;
        masm.branchConditionally(MI, labelLastX);

        masm.loadAddress(tmp13, AArch64Address.createExtendedRegisterOffsetAddress(32, x, xstart, true, UXTW));
        masm.ldr(64, productHi, AArch64Address.createBaseRegisterOnlyAddress(64, tmp13));
        masm.ror(64, productHi, productHi, 32);  // convert big-endian to little-endian

        masm.bind(labelThirdLoopPrologue);

        multiply128x128Loop(masm, y, newZ, carry, tmp11, jdx, tmp12, product,
                        tmp2, xAtXstart, tmp13, tmp4, tmp6, productHi);

        masm.add(32, xstart, xstart, 1);
        masm.str(32, carry, AArch64Address.createExtendedRegisterOffsetAddress(32, z, xstart, true, UXTW));
        masm.subs(32, xstart, xstart, 1);
        masm.branchConditionally(MI, labelDone);

        masm.lsr(64, carry, carry, 32);
        masm.str(32, carry, AArch64Address.createExtendedRegisterOffsetAddress(32, z, xstart, true, UXTW));
        masm.jmp(labelSecondLoop);

        // Next infrequent code is moved outside loops.
        masm.bind(labelLastX);
        masm.ldr(32, productHi, AArch64Address.createBaseRegisterOnlyAddress(32, x));
        masm.jmp(labelThirdLoopPrologue);

        masm.bind(labelDone);
    }
}
