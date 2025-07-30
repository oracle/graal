/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.xmm0;
import static jdk.vm.ci.amd64.AMD64.xmm1;
import static jdk.vm.ci.amd64.AMD64.xmm10;
import static jdk.vm.ci.amd64.AMD64.xmm11;
import static jdk.vm.ci.amd64.AMD64.xmm13;
import static jdk.vm.ci.amd64.AMD64.xmm14;
import static jdk.vm.ci.amd64.AMD64.xmm15;
import static jdk.vm.ci.amd64.AMD64.xmm2;
import static jdk.vm.ci.amd64.AMD64.xmm3;
import static jdk.vm.ci.amd64.AMD64.xmm4;
import static jdk.vm.ci.amd64.AMD64.xmm5;
import static jdk.vm.ci.amd64.AMD64.xmm6;
import static jdk.vm.ci.amd64.AMD64.xmm7;
import static jdk.vm.ci.amd64.AMD64.xmm8;
import static jdk.vm.ci.amd64.AMD64.xmm9;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.pointerConstant;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/4994bd594299e91e804438692e068b1c5dd5cc02/src/hotspot/cpu/x86/stubGenerator_x86_64_ghash.cpp#L33-L538",
          sha1 = "08a9206ec007eb5dc3ad1b39535e5621091f00fb")
// @formatter:on
public final class AMD64GHASHProcessBlocksOp extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64GHASHProcessBlocksOp> TYPE = LIRInstructionClass.create(AMD64GHASHProcessBlocksOp.class);

    @Alive({OperandFlag.REG}) private Value stateValue;
    @Alive({OperandFlag.REG}) private Value htblValue;
    @Alive({OperandFlag.REG}) private Value originalDataValue;
    @Alive({OperandFlag.REG}) private Value originalBlocksValue;

    @Temp protected Value dataValue;
    @Temp protected Value blocksValue;

    @Temp protected Value[] temps;

    public AMD64GHASHProcessBlocksOp(LIRGeneratorTool tool,
                    AllocatableValue stateValue,
                    AllocatableValue htblValue,
                    AllocatableValue originalDataValue,
                    AllocatableValue originalBlocksValue) {
        super(TYPE);

        this.stateValue = stateValue;
        this.htblValue = htblValue;
        this.originalDataValue = originalDataValue;
        this.originalBlocksValue = originalBlocksValue;

        this.dataValue = tool.newVariable(originalDataValue.getValueKind());
        this.blocksValue = tool.newVariable(originalBlocksValue.getValueKind());

        if (((AMD64) tool.target().arch).getFeatures().contains(AMD64.CPUFeature.AVX)) {
            this.temps = new Value[]{
                            rax.asValue(),
                            xmm0.asValue(),
                            xmm1.asValue(),
                            xmm2.asValue(),
                            xmm3.asValue(),
                            xmm4.asValue(),
                            xmm5.asValue(),
                            xmm6.asValue(),
                            xmm7.asValue(),
                            xmm8.asValue(),
                            xmm9.asValue(),
                            xmm10.asValue(),
                            xmm11.asValue(),
                            xmm13.asValue(),
                            xmm14.asValue(),
                            xmm15.asValue(),
            };
        } else {
            this.temps = new Value[]{
                            xmm0.asValue(),
                            xmm1.asValue(),
                            xmm2.asValue(),
                            xmm3.asValue(),
                            xmm4.asValue(),
                            xmm5.asValue(),
                            xmm6.asValue(),
                            xmm7.asValue(),
                            xmm8.asValue(),
                            xmm9.asValue(),
                            xmm10.asValue(),
            };
        }
    }

    private static ArrayDataPointerConstant ghashLongSwapMask = pointerConstant(16, new int[]{
            // @formatter:off
            0x0b0a0908, 0x0f0e0d0c, 0x03020100, 0x07060504
            // @formatter:on
    });

    private static ArrayDataPointerConstant ghashByteSwapMask = pointerConstant(16, new int[]{
            // @formatter:off
            0x0c0d0e0f, 0x08090a0b, 0x04050607, 0x00010203
            // @formatter:on
    });

    private static ArrayDataPointerConstant ghashShuffleMask = pointerConstant(16, new int[]{
            // @formatter:off
            0x0f0f0f0f, 0x0f0f0f0f, 0x0f0f0f0f, 0x0f0f0f0f
            // @formatter:on
    });

    private static ArrayDataPointerConstant ghashPolynomial = pointerConstant(16, new int[]{
            // @formatter:off
            0x00000001, 0x00000000, 0x00000000, 0xc2000000
            // @formatter:on
    });

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        GraalError.guarantee(stateValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid stateValue kind: %s", stateValue);
        GraalError.guarantee(htblValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid htblValue kind: %s", htblValue);
        GraalError.guarantee(originalDataValue.getPlatformKind().equals(AMD64Kind.QWORD), "Invalid originalDataValue kind: %s", originalDataValue);
        GraalError.guarantee(originalBlocksValue.getPlatformKind().equals(AMD64Kind.DWORD), "Invalid originalBlocksValue kind: %s", originalBlocksValue);

        if (masm.supports(AMD64.CPUFeature.AVX)) {
            Label labelBeginProcess = new Label();
            Label labelBlock8Reduction = new Label();
            Label labelOneBlkInit = new Label();
            Label labelProcess1Block = new Label();
            Label labelProcess8Blocks = new Label();
            Label labelSaveState = new Label();
            Label labelExitGHASH = new Label();

            Register inputState = asRegister(stateValue);
            Register htbl = asRegister(htblValue);
            Register originalData = asRegister(originalDataValue);
            Register originalBlocks = asRegister(originalBlocksValue);

            Register inputData = asRegister(dataValue);
            Register blocks = asRegister(blocksValue);

            masm.movq(inputData, originalData);
            masm.movl(blocks, originalBlocks);

            // temporary variables to hold input data and input state
            Register data = xmm1;
            Register state = xmm0;
            // temporary variables to hold intermediate results
            Register tmp0 = xmm3;
            Register tmp1 = xmm4;
            Register tmp2 = xmm5;
            Register tmp3 = xmm6;
            // temporary variables to hold byte and long swap masks
            Register bswapMask = xmm2;
            Register lswapMask = xmm14;

            masm.testlAndJcc(blocks, blocks, ConditionFlag.Zero, labelExitGHASH, false);

            // Check if Hashtable (1*16) has been already generated
            // For anything less than 8 blocks, we generate only the first power of H.
            masm.movdqu(tmp2, new AMD64Address(htbl, 1 * 16));
            masm.vptest(tmp2, tmp2, AVXSize.XMM);
            masm.jcc(ConditionFlag.NotZero, labelBeginProcess);
            generateHtblOneBlock(crb, masm, htbl);

            masm.bind(labelBeginProcess);
            masm.movdqu(lswapMask, recordExternalAddress(crb, ghashLongSwapMask));
            masm.movdqu(state, new AMD64Address(inputState));
            masm.vpshufb(state, state, lswapMask, AVXSize.XMM);

            masm.cmplAndJcc(blocks, 8, ConditionFlag.Below, labelOneBlkInit, false);
            // If we have 8 blocks or more data, then generate remaining powers of H
            masm.movdqu(tmp2, new AMD64Address(htbl, 8 * 16));
            masm.vptest(tmp2, tmp2, AVXSize.XMM);
            masm.jcc(ConditionFlag.NotZero, labelProcess8Blocks);
            generateHtblEightBlocks(masm, htbl);

            // Do 8 multiplies followed by a reduction processing 8 blocks of data at a time
            // Each block = 16 bytes.
            masm.bind(labelProcess8Blocks);
            masm.subl(blocks, 8);
            masm.movdqu(bswapMask, recordExternalAddress(crb, ghashByteSwapMask));
            masm.movdqu(data, new AMD64Address(inputData, 16 * 7));
            masm.vpshufb(data, data, bswapMask, AVXSize.XMM);
            // Loading 1*16 as calculated powers of H required starts at that location.
            masm.movdqu(xmm15, new AMD64Address(htbl, 1 * 16));
            // Perform carryless multiplication of (H*2, data block #7)
            masm.vpclmulhqlqdq(tmp2, data, xmm15); // a0 * b1
            masm.vpclmullqlqdq(tmp0, data, xmm15); // a0 * b0
            masm.vpclmulhqhqdq(tmp1, data, xmm15); // a1 * b1
            masm.vpclmullqhqdq(tmp3, data, xmm15); // a1 * b0
            masm.vpxor(tmp2, tmp2, tmp3, AVXSize.XMM); // (a0 * b1) + (a1 * b0)

            masm.movdqu(data, new AMD64Address(inputData, 16 * 6));
            masm.vpshufb(data, data, bswapMask, AVXSize.XMM);
            // Perform carryless multiplication of (H^2 * 2, data block #6)
            schoolbookAAD(masm, 2, htbl, data, tmp0, tmp1, tmp2, tmp3);

            masm.movdqu(data, new AMD64Address(inputData, 16 * 5));
            masm.vpshufb(data, data, bswapMask, AVXSize.XMM);
            // Perform carryless multiplication of (H^3 * 2, data block #5)
            schoolbookAAD(masm, 3, htbl, data, tmp0, tmp1, tmp2, tmp3);
            masm.movdqu(data, new AMD64Address(inputData, 16 * 4));
            masm.vpshufb(data, data, bswapMask, AVXSize.XMM);
            // Perform carryless multiplication of (H^4 * 2, data block #4)
            schoolbookAAD(masm, 4, htbl, data, tmp0, tmp1, tmp2, tmp3);
            masm.movdqu(data, new AMD64Address(inputData, 16 * 3));
            masm.vpshufb(data, data, bswapMask, AVXSize.XMM);
            // Perform carryless multiplication of (H^5 * 2, data block #3)
            schoolbookAAD(masm, 5, htbl, data, tmp0, tmp1, tmp2, tmp3);
            masm.movdqu(data, new AMD64Address(inputData, 16 * 2));
            masm.vpshufb(data, data, bswapMask, AVXSize.XMM);
            // Perform carryless multiplication of (H^6 * 2, data block #2)
            schoolbookAAD(masm, 6, htbl, data, tmp0, tmp1, tmp2, tmp3);
            masm.movdqu(data, new AMD64Address(inputData, 16 * 1));
            masm.vpshufb(data, data, bswapMask, AVXSize.XMM);
            // Perform carryless multiplication of (H^7 * 2, data block #1)
            schoolbookAAD(masm, 7, htbl, data, tmp0, tmp1, tmp2, tmp3);
            masm.movdqu(data, new AMD64Address(inputData, 16 * 0));
            // xor data block#0 with input state before performing carry-less multiplication
            masm.vpshufb(data, data, bswapMask, AVXSize.XMM);
            masm.vpxor(data, data, state, AVXSize.XMM);
            // Perform carryless multiplication of (H^8 * 2, data block #0)
            schoolbookAAD(masm, 8, htbl, data, tmp0, tmp1, tmp2, tmp3);
            masm.vpslldq(tmp3, tmp2, 8, AVXSize.XMM);
            masm.vpsrldq(tmp2, tmp2, 8, AVXSize.XMM);
            // tmp0, tmp1 contains aggregated results of the multiplication operation
            masm.vpxor(tmp0, tmp0, tmp3, AVXSize.XMM);
            masm.vpxor(tmp1, tmp1, tmp2, AVXSize.XMM);

            // we have the 2 128-bit partially accumulated multiplication results in tmp0:tmp1
            // with higher 128-bit in tmp1 and lower 128-bit in corresponding tmp0
            // Follows the reduction technique mentioned in
            // Shift-XOR reduction described in Gueron-Kounavis May 2010
            masm.bind(labelBlock8Reduction);
            // First Phase of the reduction
            masm.vpslld(xmm8, tmp0, 31, AVXSize.XMM);  // packed right shifting << 31
            masm.vpslld(xmm9, tmp0, 30, AVXSize.XMM);  // packed right shifting << 30
            masm.vpslld(xmm10, tmp0, 25, AVXSize.XMM); // packed right shifting << 25
            // xor the shifted versions
            masm.vpxor(xmm8, xmm8, xmm10, AVXSize.XMM);
            masm.vpxor(xmm8, xmm8, xmm9, AVXSize.XMM);

            masm.vpslldq(xmm9, xmm8, 12, AVXSize.XMM);
            masm.vpsrldq(xmm8, xmm8, 4, AVXSize.XMM);

            masm.vpxor(tmp0, tmp0, xmm9, AVXSize.XMM); // first phase of reduction is complete
            // second phase of the reduction
            masm.vpsrld(xmm9, tmp0, 1, AVXSize.XMM);  // packed left shifting >> 1
            masm.vpsrld(xmm10, tmp0, 2, AVXSize.XMM); // packed left shifting >> 2
            masm.vpsrld(tmp2, tmp0, 7, AVXSize.XMM);  // packed left shifting >> 7
            // xor the shifted versions
            masm.vpxor(xmm9, xmm9, xmm10, AVXSize.XMM);
            masm.vpxor(xmm9, xmm9, tmp2, AVXSize.XMM);
            masm.vpxor(xmm9, xmm9, xmm8, AVXSize.XMM);
            masm.vpxor(tmp0, xmm9, tmp0, AVXSize.XMM);
            // Final result is in state
            masm.vpxor(state, tmp0, tmp1, AVXSize.XMM);

            masm.leaq(inputData, new AMD64Address(inputData, 16 * 8));
            masm.cmplAndJcc(blocks, 8, AMD64Assembler.ConditionFlag.Below, labelOneBlkInit, false);
            masm.jmp(labelProcess8Blocks);

            // Since this is one block operation we will only use H * 2 i.e. the first power of H
            masm.bind(labelOneBlkInit);
            masm.movdqu(tmp0, new AMD64Address(htbl, 1 * 16));
            masm.movdqu(bswapMask, recordExternalAddress(crb, ghashByteSwapMask));

            // Do one (128 bit x 128 bit) carry-less multiplication at a time followed by a
            // reduction.
            masm.bind(labelProcess1Block);
            masm.cmplAndJcc(blocks, 0, AMD64Assembler.ConditionFlag.Equal, labelSaveState, false);
            masm.subl(blocks, 1);
            masm.movdqu(data, new AMD64Address(inputData));
            masm.vpshufb(data, data, bswapMask, AVXSize.XMM);
            masm.vpxor(state, state, data, AVXSize.XMM);
            // gfmul(H*2, state)
            gfmul(masm, tmp0, state);
            masm.addq(inputData, 16);
            masm.jmp(labelProcess1Block);

            masm.bind(labelSaveState);
            masm.vpshufb(state, state, lswapMask, AVXSize.XMM);
            masm.movdqu(new AMD64Address(inputState), state);

            masm.bind(labelExitGHASH);
            // zero out xmm registers used for Htbl storage
            masm.vpxor(xmm0, xmm0, xmm0, AVXSize.XMM);
            masm.vpxor(xmm1, xmm1, xmm1, AVXSize.XMM);
            masm.vpxor(xmm3, xmm3, xmm3, AVXSize.XMM);
            masm.vpxor(xmm15, xmm15, xmm15, AVXSize.XMM);
        } else {
            Label labelGHASHLoop = new Label();
            Label labelExit = new Label();

            Register state = asRegister(stateValue);
            Register subkeyH = asRegister(htblValue);
            Register originalData = asRegister(originalDataValue);
            Register originalBlocks = asRegister(originalBlocksValue);

            Register data = asRegister(dataValue);
            Register blocks = asRegister(blocksValue);

            masm.movq(data, originalData);
            masm.movl(blocks, originalBlocks);

            Register xmmTemp0 = xmm0;
            Register xmmTemp1 = xmm1;
            Register xmmTemp2 = xmm2;
            Register xmmTemp3 = xmm3;
            Register xmmTemp4 = xmm4;
            Register xmmTemp5 = xmm5;
            Register xmmTemp6 = xmm6;
            Register xmmTemp7 = xmm7;
            Register xmmTemp8 = xmm8;
            Register xmmTemp9 = xmm9;
            Register xmmTemp10 = xmm10;

            masm.movdqu(xmmTemp10, recordExternalAddress(crb, ghashLongSwapMask));

            masm.movdqu(xmmTemp0, new AMD64Address(state));
            masm.pshufb(xmmTemp0, xmmTemp10);

            masm.bind(labelGHASHLoop);
            masm.movdqu(xmmTemp2, new AMD64Address(data));
            masm.pshufb(xmmTemp2, recordExternalAddress(crb, ghashByteSwapMask));

            masm.movdqu(xmmTemp1, new AMD64Address(subkeyH));
            masm.pshufb(xmmTemp1, xmmTemp10);

            masm.pxor(xmmTemp0, xmmTemp2);

            // Multiply with the hash key
            masm.movdqu(xmmTemp3, xmmTemp0);
            masm.pclmulqdq(xmmTemp3, xmmTemp1, 0);  // xmm3 holds a0*b0
            masm.movdqu(xmmTemp4, xmmTemp0);
            masm.pclmulqdq(xmmTemp4, xmmTemp1, 16); // xmm4 holds a0*b1

            masm.movdqu(xmmTemp5, xmmTemp0);
            masm.pclmulqdq(xmmTemp5, xmmTemp1, 1);  // xmm5 holds a1*b0
            masm.movdqu(xmmTemp6, xmmTemp0);
            masm.pclmulqdq(xmmTemp6, xmmTemp1, 17); // xmm6 holds a1*b1

            masm.pxor(xmmTemp4, xmmTemp5);      // xmm4 holds a0*b1 + a1*b0

            masm.movdqu(xmmTemp5, xmmTemp4);    // move the contents of xmm4 to xmm5
            masm.psrldq(xmmTemp4, 8);    // shift by xmm4 64 bits to the right
            masm.pslldq(xmmTemp5, 8);    // shift by xmm5 64 bits to the left
            // Register pair <xmm6:xmm3> holds the result of the carry-less multiplication of xmm0
            // by xmm1.
            masm.pxor(xmmTemp3, xmmTemp5);
            masm.pxor(xmmTemp6, xmmTemp4);

            // We shift the result of the multiplication by one bit position
            // to the left to cope for the fact that the bits are reversed.
            masm.movdqu(xmmTemp7, xmmTemp3);
            masm.movdqu(xmmTemp8, xmmTemp6);
            masm.pslld(xmmTemp3, 1);
            masm.pslld(xmmTemp6, 1);
            masm.psrld(xmmTemp7, 31);
            masm.psrld(xmmTemp8, 31);
            masm.movdqu(xmmTemp9, xmmTemp7);
            masm.pslldq(xmmTemp8, 4);
            masm.pslldq(xmmTemp7, 4);
            masm.psrldq(xmmTemp9, 12);
            masm.por(xmmTemp3, xmmTemp7);
            masm.por(xmmTemp6, xmmTemp8);
            masm.por(xmmTemp6, xmmTemp9);

            //
            // First phase of the reduction
            //
            // Move xmm3 into xmm7, xmm8, xmm9 in order to perform the shifts
            // independently.
            masm.movdqu(xmmTemp7, xmmTemp3);
            masm.movdqu(xmmTemp8, xmmTemp3);
            masm.movdqu(xmmTemp9, xmmTemp3);
            masm.pslld(xmmTemp7, 31);    // packed right shift shifting << 31
            masm.pslld(xmmTemp8, 30);    // packed right shift shifting << 30
            masm.pslld(xmmTemp9, 25);    // packed right shift shifting << 25
            masm.pxor(xmmTemp7, xmmTemp8);      // xor the shifted versions
            masm.pxor(xmmTemp7, xmmTemp9);
            masm.movdqu(xmmTemp8, xmmTemp7);
            masm.pslldq(xmmTemp7, 12);
            masm.psrldq(xmmTemp8, 4);
            masm.pxor(xmmTemp3, xmmTemp7);      // first phase of the reduction complete

            //
            // Second phase of the reduction
            //
            // Make 3 copies of xmm3 in xmm2, xmm4, xmm5 for doing these
            // shift operations.
            masm.movdqu(xmmTemp2, xmmTemp3);
            masm.movdqu(xmmTemp4, xmmTemp3);
            masm.movdqu(xmmTemp5, xmmTemp3);
            masm.psrld(xmmTemp2, 1);     // packed left shifting >> 1
            masm.psrld(xmmTemp4, 2);     // packed left shifting >> 2
            masm.psrld(xmmTemp5, 7);     // packed left shifting >> 7
            masm.pxor(xmmTemp2, xmmTemp4);      // xor the shifted versions
            masm.pxor(xmmTemp2, xmmTemp5);
            masm.pxor(xmmTemp2, xmmTemp8);
            masm.pxor(xmmTemp3, xmmTemp2);
            masm.pxor(xmmTemp6, xmmTemp3);      // the result is in xmm6

            masm.declAndJcc(blocks, AMD64Assembler.ConditionFlag.Zero, labelExit, false);
            masm.movdqu(xmmTemp0, xmmTemp6);
            masm.addq(data, 16);
            masm.jmp(labelGHASHLoop);

            masm.bind(labelExit);
            masm.pshufb(xmmTemp6, xmmTemp10);          // Byte swap 16-byte result
            masm.movdqu(new AMD64Address(state), xmmTemp6);   // store the result
        }
    }

    /**
     * Multiply 128 x 128 bits, using 4 pclmulqdq operations.
     */
    private static void schoolbookAAD(AMD64MacroAssembler masm, int i, Register htbl, Register data, Register tmp0, Register tmp1, Register tmp2, Register tmp3) {
        masm.movdqu(xmm15, new AMD64Address(htbl, i * 16));
        masm.vpclmulhqlqdq(tmp3, data, xmm15); // 0x01
        masm.vpxor(tmp2, tmp2, tmp3, AVXSize.XMM);
        masm.vpclmullqlqdq(tmp3, data, xmm15); // 0x00
        masm.vpxor(tmp0, tmp0, tmp3, AVXSize.XMM);
        masm.vpclmulhqhqdq(tmp3, data, xmm15); // 0x11
        masm.vpxor(tmp1, tmp1, tmp3, AVXSize.XMM);
        masm.vpclmullqhqdq(tmp3, data, xmm15); // 0x10
        masm.vpxor(tmp2, tmp2, tmp3, AVXSize.XMM);
    }

    /**
     * Multiply two 128 bit numbers resulting in a 256 bit value Result of the multiplication
     * followed by reduction stored in state.
     */
    private static void gfmul(AMD64MacroAssembler masm, Register tmp0, Register state) {
        Register tmp1 = xmm4;
        Register tmp2 = xmm5;
        Register tmp3 = xmm6;
        Register tmp4 = xmm7;

        masm.vpclmullqlqdq(tmp1, state, tmp0); // 0x00 (a0 * b0)
        masm.vpclmulhqhqdq(tmp4, state, tmp0); // 0x11 (a1 * b1)
        masm.vpclmullqhqdq(tmp2, state, tmp0); // 0x10 (a1 * b0)
        masm.vpclmulhqlqdq(tmp3, state, tmp0); // 0x01 (a0 * b1)

        masm.vpxor(tmp2, tmp2, tmp3, AVXSize.XMM); // (a0 * b1) + (a1 * b0)

        masm.vpslldq(tmp3, tmp2, 8, AVXSize.XMM);
        masm.vpsrldq(tmp2, tmp2, 8, AVXSize.XMM);
        masm.vpxor(tmp1, tmp1, tmp3, AVXSize.XMM); // tmp1 and tmp4 hold the result
        masm.vpxor(tmp4, tmp4, tmp2, AVXSize.XMM); // of carryless multiplication
        // Follows the reduction technique mentioned in
        // Shift-XOR reduction described in Gueron-Kounavis May 2010

        // First phase of reduction
        masm.vpslld(xmm8, tmp1, 31, AVXSize.XMM); // packed right shift shifting << 31
        masm.vpslld(xmm9, tmp1, 30, AVXSize.XMM); // packed right shift shifting << 30
        masm.vpslld(xmm10, tmp1, 25, AVXSize.XMM); // packed right shift shifting << 25
        // xor the shifted versions
        masm.vpxor(xmm8, xmm8, xmm9, AVXSize.XMM);
        masm.vpxor(xmm8, xmm8, xmm10, AVXSize.XMM);
        masm.vpslldq(xmm9, xmm8, 12, AVXSize.XMM);
        masm.vpsrldq(xmm8, xmm8, 4, AVXSize.XMM);
        masm.vpxor(tmp1, tmp1, xmm9, AVXSize.XMM); // first phase of the reduction complete

        // Second phase of the reduction
        masm.vpsrld(xmm9, tmp1, 1, AVXSize.XMM); // packed left shifting >> 1
        masm.vpsrld(xmm10, tmp1, 2, AVXSize.XMM); // packed left shifting >> 2
        masm.vpsrld(xmm11, tmp1, 7, AVXSize.XMM); // packed left shifting >> 7
        masm.vpxor(xmm9, xmm9, xmm10, AVXSize.XMM); // xor the shifted versions
        masm.vpxor(xmm9, xmm9, xmm11, AVXSize.XMM);
        masm.vpxor(xmm9, xmm9, xmm8, AVXSize.XMM);
        masm.vpxor(tmp1, tmp1, xmm9, AVXSize.XMM);
        masm.vpxor(state, tmp4, tmp1, AVXSize.XMM); // the result is in state
    }

    private static void generateHtblOneBlock(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register htbl) {
        Register t = xmm13;

        // load the original subkey hash
        masm.movdqu(t, new AMD64Address(htbl));
        // shuffle using long swap mask
        masm.movdqu(xmm10, recordExternalAddress(crb, ghashLongSwapMask));
        masm.vpshufb(t, t, xmm10, AVXSize.XMM);

        // Compute H' = GFMUL(H, 2)
        masm.vpsrld(xmm3, t, 7, AVXSize.XMM);
        masm.movdqu(xmm4, recordExternalAddress(crb, ghashShuffleMask));
        masm.vpshufb(xmm3, xmm3, xmm4, AVXSize.XMM);
        masm.movl(rax, 0xff00);
        masm.movdl(xmm4, rax);
        masm.vpshufb(xmm4, xmm4, xmm3, AVXSize.XMM);
        masm.movdqu(xmm5, recordExternalAddress(crb, ghashPolynomial));
        masm.vpand(xmm5, xmm5, xmm4, AVXSize.XMM);
        masm.vpsrld(xmm3, t, 31, AVXSize.XMM);
        masm.vpslld(xmm4, t, 1, AVXSize.XMM);
        masm.vpslldq(xmm3, xmm3, 4, AVXSize.XMM);
        masm.vpxor(t, xmm4, xmm3, AVXSize.XMM); // t holds p(x) <<1 or H * 2

        // Adding p(x)<<1 to xmm5 which holds the reduction polynomial
        masm.vpxor(t, t, xmm5, AVXSize.XMM);
        masm.movdqu(new AMD64Address(htbl, 1 * 16), t); // H * 2
    }

    /**
     * This method takes the subkey after expansion as input and generates the remaining powers of
     * subkey H. The power of H is used in reduction process for eight block ghash.
     */
    private static void generateHtblEightBlocks(AMD64MacroAssembler masm, Register htbl) {
        Register t = xmm13;
        Register tmp0 = xmm1;

        masm.movdqu(t, new AMD64Address(htbl, 1 * 16));
        masm.movdqu(tmp0, t);

        // tmp0 and t hold H. Now we compute powers of H by using GFMUL(H, H)
        gfmul(masm, tmp0, t);
        masm.movdqu(new AMD64Address(htbl, 2 * 16), t); // H ^ 2 * 2
        gfmul(masm, tmp0, t);
        masm.movdqu(new AMD64Address(htbl, 3 * 16), t); // H ^ 3 * 2
        gfmul(masm, tmp0, t);
        masm.movdqu(new AMD64Address(htbl, 4 * 16), t); // H ^ 4 * 2
        gfmul(masm, tmp0, t);
        masm.movdqu(new AMD64Address(htbl, 5 * 16), t); // H ^ 5 * 2
        gfmul(masm, tmp0, t);
        masm.movdqu(new AMD64Address(htbl, 6 * 16), t); // H ^ 6 * 2
        gfmul(masm, tmp0, t);
        masm.movdqu(new AMD64Address(htbl, 7 * 16), t); // H ^ 7 * 2
        gfmul(masm, tmp0, t);
        masm.movdqu(new AMD64Address(htbl, 8 * 16), t); // H ^ 8 * 2
    }
}
