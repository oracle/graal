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
import static jdk.vm.ci.aarch64.AArch64.r3;
import static jdk.vm.ci.aarch64.AArch64.r4;
import static jdk.vm.ci.aarch64.AArch64.r5;
import static jdk.vm.ci.aarch64.AArch64.r6;
import static jdk.vm.ci.aarch64.AArch64.r7;
import static jdk.vm.ci.aarch64.AArch64.v0;
import static jdk.vm.ci.aarch64.AArch64.v1;
import static jdk.vm.ci.aarch64.AArch64.v2;
import static jdk.vm.ci.aarch64.AArch64.v3;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD1_MULTIPLE_4R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createStructureImmediatePostIndexAddress;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PAIR_SIGNED_SCALED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_SIGNED_UNSCALED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.PrefetchMode.PLDL1KEEP;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ShiftType.LSL;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.core.aarch64.AArch64LIRGenerator;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * Returns the number of positive bytes.
 */
// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/be2b92bd8b43841cc2b9c22ed4fde29be30d47bb/src/hotspot/cpu/aarch64/macroAssembler_aarch64.cpp#L5121-L5190",
          sha1 = "ce54a7cf2fcfe7ccb8f6604c038887fc1c4ebce1")
@SyncPort(from = "https://github.com/openjdk/jdk/blob/ce8399fd6071766114f5f201b6e44a7abdba9f5a/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L4971-L5137",
          sha1 = "3b4e6edb4372e8babb009763c2d05961348dd723")
// @formatter:on
@Opcode("AARCH64_COUNT_POSITIVES")
public final class AArch64CountPositivesOp extends AArch64ComplexVectorOp {
    public static final LIRInstructionClass<AArch64CountPositivesOp> TYPE = LIRInstructionClass.create(AArch64CountPositivesOp.class);

    @LIRInstruction.Def({LIRInstruction.OperandFlag.REG}) private Value resultValue;
    @LIRInstruction.Alive({LIRInstruction.OperandFlag.REG}) private Value arrayValue;
    @LIRInstruction.Alive({LIRInstruction.OperandFlag.REG}) private Value lengthValue;

    @LIRInstruction.Temp({LIRInstruction.OperandFlag.REG}) private Value arrayTempValue;
    @LIRInstruction.Temp({LIRInstruction.OperandFlag.REG}) private Value lengthTempValue;
    @LIRInstruction.Temp({LIRInstruction.OperandFlag.REG}) private Value[] temp;

    private final int vmPageSize;
    private final int softwarePrefetchHintDistance;

    public AArch64CountPositivesOp(AArch64LIRGenerator tool, AllocatableValue resultValue, AllocatableValue arrayValue, AllocatableValue lengthValue,
                    int vmPageSize, int softwarePrefetchHintDistance) {
        super(TYPE);
        this.resultValue = resultValue;
        this.arrayValue = arrayValue;
        this.lengthValue = lengthValue;

        this.vmPageSize = vmPageSize;
        this.softwarePrefetchHintDistance = softwarePrefetchHintDistance;

        this.arrayTempValue = tool.newVariable(arrayValue.getValueKind());
        this.lengthTempValue = tool.newVariable(lengthValue.getValueKind());

        this.temp = new Value[]{
                        r3.asValue(),
                        r4.asValue(),
                        r5.asValue(),
                        r6.asValue(),
                        r7.asValue(),
                        // r8 and r9 are scratch registers
                        r10.asValue(),
                        v0.asValue(),
                        v1.asValue(),
                        v2.asValue(),
                        v3.asValue(),
        };
    }

    private static final long UPPER_BIT_MASK = 0x8080808080808080L;

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        try (AArch64MacroAssembler.ScratchRegister scratchReg1 = masm.getScratchRegister();
                        AArch64MacroAssembler.ScratchRegister scratchReg2 = masm.getScratchRegister();) {
            Register rscratch1 = scratchReg1.getRegister();
            Register rscratch2 = scratchReg2.getRegister();

            int wordSize = crb.target.wordSize;
            Register result = asRegister(resultValue);
            Register ary1 = asRegister(arrayTempValue);
            Register len = asRegister(lengthTempValue);

            // The original HotSpot code inlines the simple and most common case of aligned small
            // array which is not at the end of memory page, and calls into stubs for all other
            // cases. By default, we emit everything in a single stub, and it can be inlined via
            // -Djdk.graal.InlineGraalStubs=true
            masm.mov(64, ary1, asRegister(arrayValue));
            masm.mov(32, len, asRegister(lengthValue));

            Label labelLoop = new Label();
            Label labelEnd = new Label();
            Label labelStub = new Label();
            Label labelStubLong = new Label();
            Label labelSetResult = new Label();
            Label labelDone = new Label();

            masm.mov(32, result, len);
            masm.compare(32, len, 0);
            masm.branchConditionally(ConditionFlag.LE, labelDone);
            masm.compare(32, len, 4 * wordSize);
            // size > 32 then go to stub
            masm.branchConditionally(ConditionFlag.GE, labelStubLong);

            if (vmPageSize > 0) {
                GraalError.guarantee(CodeUtil.isPowerOf2(vmPageSize), "vmPageSize is not power of 2: %d", vmPageSize);
                int shift = 64 - CodeUtil.log2(vmPageSize);
                masm.lsl(64, rscratch1, ary1, shift);
                masm.mov(rscratch2, (4L * wordSize) << shift);
                masm.adds(64, rscratch2, rscratch1, rscratch2);
                // at the end of page then go to stub
                masm.branchConditionally(ConditionFlag.HS, labelStub);
            }
            masm.subs(64, len, len, wordSize);
            masm.branchConditionally(ConditionFlag.LT, labelEnd);

            masm.bind(labelLoop);
            masm.ldr(64, rscratch1, AArch64Address.createImmediateAddress(64, IMMEDIATE_POST_INDEXED, ary1, wordSize));
            masm.tst(64, rscratch1, UPPER_BIT_MASK);
            masm.branchConditionally(ConditionFlag.NE, labelSetResult);
            masm.subs(32, len, len, wordSize);
            masm.branchConditionally(ConditionFlag.GE, labelLoop);
            masm.compare(32, len, -wordSize);
            masm.branchConditionally(ConditionFlag.EQ, labelDone);

            masm.bind(labelEnd);
            masm.ldr(64, rscratch1, AArch64Address.createBaseRegisterOnlyAddress(64, ary1));
            masm.sub(64, rscratch2, zr, len, LSL, 3); // LSL 3 is to get bits from bytes
            masm.lsl(64, rscratch1, rscratch1, rscratch2);
            masm.tst(64, rscratch1, UPPER_BIT_MASK);
            masm.branchConditionally(ConditionFlag.NE, labelSetResult);
            masm.jmp(labelDone);

            masm.bind(labelStub);
            // StubRoutines::aarch64::_count_positives is inlined
            emitStub(masm, result, ary1, len, rscratch1, rscratch2, labelStubLong, labelDone);
            GraalError.guarantee(labelStubLong.isBound(), "labelStubLong should be bound");
            masm.jmp(labelDone);

            masm.bind(labelSetResult);

            masm.add(32, len, len, wordSize);
            masm.sub(32, result, result, len);

            masm.bind(labelDone);
        }
    }

    private static final int LARGE_LOOP_SIZE = 64;

    private void emitStub(AArch64MacroAssembler masm, Register result, Register ary1, Register len,
                    Register rscratch1, Register rscratch2, Label labelStubLong, Label labelDone) {
        Label labelRetAdjust = new Label();
        Label labelRetAdjust16 = new Label();
        Label labelRetAdjustLong = new Label();
        Label labelRetNoPop = new Label();
        Label labelAligned = new Label();
        Label labelLoop16 = new Label();
        Label labelCheck16 = new Label();
        Label labelLargeLoop = new Label();
        Label labelPostLoop16 = new Label();
        Label labelLenOver8 = new Label();
        Label labelPostLoop16LoadTail = new Label();

        Register tmp1 = r3;
        Register tmp2 = r4;
        Register tmp3 = r5;
        Register tmp4 = r6;
        Register tmp5 = r7;
        Register tmp6 = r10;

        Register vtmp0 = v0;
        Register vtmp1 = v1;
        Register vtmp2 = v2;
        Register vtmp3 = v3;

        masm.compare(32, len, 15);
        masm.branchConditionally(ConditionFlag.GT, labelStubLong);
        // The only case when execution falls into this code is when pointer is near
        // the end of memory page and we have to avoid reading next page
        masm.add(64, ary1, ary1, len);
        masm.subs(32, len, len, 8);
        masm.branchConditionally(ConditionFlag.GT, labelLenOver8);
        masm.ldr(64, rscratch2, AArch64Address.createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, ary1, -8));
        masm.sub(64, rscratch1, zr, len, LSL, 3);  // LSL 3 is to get bits from bytes.
        masm.lsr(64, rscratch2, rscratch2, rscratch1);
        masm.tst(64, rscratch2, UPPER_BIT_MASK);
        masm.csel(32, result, zr, result, ConditionFlag.NE);
        masm.jmp(labelDone);

        masm.bind(labelLenOver8);
        masm.ldp(64, rscratch1, rscratch2, AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_SIGNED_SCALED, ary1, -16));
        masm.sub(32, len, len, 8); // no data dep., then sub can be executed while loading
        masm.tst(64, rscratch2, UPPER_BIT_MASK);
        masm.branchConditionally(ConditionFlag.NE, labelRetNoPop);
        masm.sub(64, rscratch2, zr, len, LSL, 3); // LSL 3 is to get bits from bytes
        masm.lsr(64, rscratch1, rscratch1, rscratch2);
        masm.tst(64, rscratch1, UPPER_BIT_MASK);
        masm.bind(labelRetNoPop);
        masm.csel(32, result, zr, result, ConditionFlag.NE);
        masm.jmp(labelDone);

        masm.bind(labelStubLong);
        // check pointer for 16-byte alignment
        masm.and(64, rscratch2, ary1, 15);
        masm.cbz(64, rscratch2, labelAligned);
        masm.ldp(64, tmp6, tmp1, AArch64Address.createPairBaseRegisterOnlyAddress(64, ary1));
        masm.mov(tmp5, 16);
        // amount of bytes until aligned address
        masm.sub(64, rscratch1, tmp5, rscratch2);
        masm.add(64, ary1, ary1, rscratch1);
        masm.orr(64, tmp6, tmp6, tmp1);
        masm.tst(64, tmp6, UPPER_BIT_MASK);
        masm.branchConditionally(ConditionFlag.NE, labelRetAdjust);
        masm.sub(32, len, len, rscratch1);

        masm.bind(labelAligned);
        masm.compare(32, len, LARGE_LOOP_SIZE);
        masm.branchConditionally(ConditionFlag.LT, labelCheck16);
        // Perform 16-byte load as early return in pre-loop to handle situation
        // when initially aligned large array has negative values at starting bytes,
        // so LARGE_LOOP would do 4 reads instead of 1 (in worst case), which is
        // slower. Cases with negative bytes further ahead won't be affected that
        // much. In fact, it'll be faster due to early loads, less instructions and
        // less branches in LARGE_LOOP.
        masm.ldp(64, tmp6, tmp1, AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_POST_INDEXED, ary1, 16));
        masm.sub(32, len, len, 16);
        masm.orr(64, tmp6, tmp6, tmp1);
        masm.tst(64, tmp6, UPPER_BIT_MASK);
        masm.branchConditionally(ConditionFlag.NE, labelRetAdjust16);
        masm.compare(32, len, LARGE_LOOP_SIZE);
        masm.branchConditionally(ConditionFlag.LT, labelCheck16);

        // preloop prefetch is omitted due to lack of dcache line size.
        masm.bind(labelLargeLoop);
        if (softwarePrefetchHintDistance >= 0) {
            masm.prfm(AArch64Address.createImmediateAddress(64, IMMEDIATE_UNSIGNED_SCALED, ary1, softwarePrefetchHintDistance & (~0x7)), PLDL1KEEP);
        }

        // Issue load instructions first, since it can save few CPU/MEM cycles, also
        // instead of 4 triples of "orr(...), addr(...);cbnz(...);" (for each ldp)
        // better generate 7 * orr(...) + 1 andr(...) + 1 cbnz(...) which saves 3
        // instructions per cycle and have less branches, but this approach disables
        // early return, thus, all 64 bytes are loaded and checked every time.
        // masm.ldp(64, tmp2, tmp3, AArch64Address.createPairBaseRegisterOnlyAddress(64, ary1));
        // masm.ldp(64, tmp4, tmp5, AArch64Address.createImmediateAddress(64,
        // IMMEDIATE_PAIR_SIGNED_SCALED, ary1, 16));
        // masm.ldp(64, rscratch1, rscratch2, AArch64Address.createImmediateAddress(64,
        // IMMEDIATE_PAIR_SIGNED_SCALED, ary1, 32));
        // masm.ldp(64, tmp6, tmp1, AArch64Address.createImmediateAddress(64,
        // IMMEDIATE_PAIR_SIGNED_SCALED, ary1, 48));
        // masm.add(64, ary1, ary1, LARGE_LOOP_SIZE);
        // masm.sub(32, len, len, LARGE_LOOP_SIZE);
        // masm.orr(64, tmp2, tmp2, tmp3);
        // masm.orr(64, tmp4, tmp4, tmp5);
        // masm.orr(64, rscratch1, rscratch1, rscratch2);
        // masm.orr(64, tmp6, tmp6, tmp1);
        // masm.orr(64, tmp2, tmp2, tmp4);
        // masm.orr(64, rscratch1, rscratch1, tmp6);
        // masm.orr(64, tmp2, tmp2, rscratch1);
        // masm.tst(64, tmp2, UPPER_BIT_MASK);

        // read 4 vectors
        masm.neon.ld1MultipleVVVV(FullReg, ElementSize.Byte, vtmp0, vtmp1, vtmp2, vtmp3,
                        createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, FullReg, ElementSize.Byte, ary1, LARGE_LOOP_SIZE));
        masm.sub(32, len, len, LARGE_LOOP_SIZE);
        // combine all into 1 vector
        masm.neon.orrVVV(FullReg, vtmp0, vtmp0, vtmp1);
        masm.neon.orrVVV(FullReg, vtmp2, vtmp2, vtmp3);
        masm.neon.orrVVV(FullReg, vtmp0, vtmp0, vtmp2);
        // reduce to 8 bytes with pairwise signed minimum
        masm.neon.sminpVVV(FullReg, ElementSize.Byte, vtmp0, vtmp0, vtmp2);
        // right-shift by 7 to get only the sign bits
        masm.neon.ushrVVI(FullReg, ElementSize.Byte, vtmp0, vtmp0, 7);
        // check if result is zero
        cbnzVector(masm, ElementSize.Byte, vtmp0, vtmp0, tmp5, false, labelRetAdjustLong);

        masm.compare(32, len, LARGE_LOOP_SIZE);
        masm.branchConditionally(ConditionFlag.GE, labelLargeLoop);

        masm.bind(labelCheck16); // small 16-byte load pre-loop
        masm.compare(32, len, 16);
        masm.branchConditionally(ConditionFlag.LT, labelPostLoop16);

        masm.bind(labelLoop16); // small 16-byte load loop
        masm.ldp(64, tmp2, tmp3, AArch64Address.createImmediateAddress(64, IMMEDIATE_PAIR_POST_INDEXED, ary1, 16));
        masm.sub(32, len, len, 16);
        masm.orr(64, tmp2, tmp2, tmp3);
        masm.tst(64, tmp2, UPPER_BIT_MASK);
        masm.branchConditionally(ConditionFlag.NE, labelRetAdjust16);
        masm.compare(32, len, 16);
        masm.branchConditionally(ConditionFlag.GE, labelLoop16); // 16-byte load loop end

        masm.bind(labelPostLoop16); // 16-byte aligned, so we can read unconditionally
        masm.compare(32, len, 8);
        masm.branchConditionally(ConditionFlag.LE, labelPostLoop16LoadTail);
        masm.ldr(64, tmp3, AArch64Address.createImmediateAddress(64, IMMEDIATE_POST_INDEXED, ary1, 8));
        masm.tst(64, tmp3, UPPER_BIT_MASK);
        masm.branchConditionally(ConditionFlag.NE, labelRetAdjust);
        masm.sub(32, len, len, 8);

        masm.bind(labelPostLoop16LoadTail);
        masm.cbz(32, len, labelDone); // Can't shift left by 64 when len==0
        masm.ldr(64, tmp1, AArch64Address.createBaseRegisterOnlyAddress(64, ary1));
        masm.mov(tmp2, 64);
        masm.sub(64, tmp4, tmp2, len, LSL, 3);
        masm.lsl(64, tmp1, tmp1, tmp4);
        masm.tst(64, tmp1, UPPER_BIT_MASK);
        masm.branchConditionally(ConditionFlag.NE, labelRetAdjust);
        masm.jmp(labelDone);

        // difference result - len is the count of guaranteed to be positive bytes
        masm.bind(labelRetAdjustLong);
        masm.add(32, len, len, LARGE_LOOP_SIZE - 16);
        masm.bind(labelRetAdjust16);
        masm.add(32, len, len, 16);
        masm.bind(labelRetAdjust);
        masm.sub(32, result, result, len);
    }
}
