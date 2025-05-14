/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.Equal;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.Greater;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.Less;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.Negative;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.NotZero;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.Positive;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.Zero;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.XMM;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.YMM;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.graal.compiler.replacements.nodes.StringCodepointIndexToByteIndexNode.InputEncoding;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import java.util.EnumSet;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.code.DataSection;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.replacements.nodes.StringCodepointIndexToByteIndexNode;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

/**
 * This converts a given codepoint index to a byte index on a <i>correctly encoded</i> UTF-8 or
 * UTF-16 string.
 */
@Opcode("AMD64_CODEPOINT_INDEX_TO_BYTE_INDEX")
public final class AMD64CodepointIndexToByteIndexOp extends AMD64ComplexVectorOp {
    public static final LIRInstructionClass<AMD64CodepointIndexToByteIndexOp> TYPE = LIRInstructionClass.create(AMD64CodepointIndexToByteIndexOp.class);

    private static final Register REG_ARRAY = rsi;
    private static final Register REG_OFFSET = rcx;
    private static final Register REG_LENGTH = rdx;
    private static final Register REG_INDEX = rdi;

    private final InputEncoding inputEncoding;
    private final int vectorLength;

    @Def({REG}) private Value result;
    @Use({REG}) private Value array;
    @Use({REG}) private Value offset;
    @Use({REG}) private Value length;
    @Use({REG}) private Value index;

    @Temp({REG}) private Value arrayTmp;
    @Temp({REG}) private Value offsetTmp;
    @Temp({REG}) private Value lengthTmp;
    @Temp({REG}) private Value indexTmp;

    @Temp({REG}) private Value[] temp;
    @Temp({REG}) private Value[] vectorTemp;

    private AMD64CodepointIndexToByteIndexOp(LIRGeneratorTool tool, InputEncoding inputEncoding, EnumSet<CPUFeature> runtimeCheckedCPUFeatures, Value array, Value offset, Value length, Value index,
                    Value result) {
        super(TYPE, tool, runtimeCheckedCPUFeatures, YMM);
        this.inputEncoding = inputEncoding;

        GraalError.guarantee(supports(tool.target(), runtimeCheckedCPUFeatures, StringCodepointIndexToByteIndexNode.minFeaturesAMD64()), "needs at least SSSE3 and POPCNT support");

        this.vectorLength = vectorSize.getBytes();

        this.arrayTmp = this.array = array;
        this.offsetTmp = this.offset = offset;
        this.lengthTmp = this.length = length;
        this.indexTmp = this.index = index;
        this.result = result;

        this.temp = allocateTempRegisters(tool, AMD64Kind.QWORD, 2);
        this.vectorTemp = allocateVectorRegisters(tool, JavaKind.Byte, 5);
    }

    /**
     * Converts a given codepoint index to a byte index on a <i>correctly encoded</i> UTF-8 or
     * UTF-16 string.
     *
     * @param inputEncoding operation to run.
     * @param array arbitrary array.
     * @param byteOffset byteOffset to start from. Must include array base byteOffset!
     * @param length length of the array region to consider, in the given encoding's natural stride,
     *            which is 1 byte for {@link InputEncoding#UTF_8} and 2 bytes for
     *            {@link InputEncoding#UTF_16}.
     * @param index the codepoint index to translate.
     */
    public static AMD64CodepointIndexToByteIndexOp movParamsAndCreate(LIRGeneratorTool tool, InputEncoding inputEncoding, EnumSet<CPUFeature> runtimeCheckedCPUFeatures,
                    Value array, Value byteOffset, Value length, Value index, Value result) {
        RegisterValue regArray = REG_ARRAY.asValue(array.getValueKind());
        RegisterValue regOffset = REG_OFFSET.asValue(byteOffset.getValueKind());
        RegisterValue regLength = REG_LENGTH.asValue(length.getValueKind());
        RegisterValue regIndex = REG_INDEX.asValue(index.getValueKind());
        tool.emitConvertNullToZero(regArray, array);
        tool.emitMove(regOffset, byteOffset);
        tool.emitMove(regLength, length);
        tool.emitMove(regIndex, index);
        return new AMD64CodepointIndexToByteIndexOp(tool, inputEncoding, runtimeCheckedCPUFeatures, regArray, regOffset, regLength, regIndex, result);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler asm) {
        Register arr = asRegister(array);
        Register off = asRegister(offset);
        Register len = asRegister(length);
        Register idx = asRegister(index);
        Register ret = asRegister(result);

        asm.addq(arr, off);
        emitOp(crb, asm, arr, len, off, idx, ret);
    }

    /**
     * This is a vectorized version of the following functions:
     *
     * <pre>
     *
     * // UTF-8 variant:
     * static int codePointIndexToByteIndexUTF8Valid(byte[] array, long offset, int length, int index) {
     *     int cpi = index;
     *     for (int i = 0; i < length; i++) {
     *         if (!isUTF8ContinuationByte(readValueS0(array, offset, i))) {
     *             if (--cpi < 0) {
     *                 return i;
     *             }
     *         }
     *     }
     *     return cpi == 0 ? length : -1;
     * }
     *
     * // UTF-16 variant:
     * static int runCodePointIndexToByteIndexUTF16Valid(byte[] array, long offset, int length, int index) {
     *     int cpi = index;
     *     for (int i = 0; i < length; i++) {
     *         if (!isUTF16LowSurrogate(readValueS1(array, offset, i))) {
     *             if (--cpi < 0) {
     *                 return i;
     *             }
     *         }
     *     }
     *     return cpi == 0 ? length : -1;
     * }
     * </pre>
     * <p>
     * The functions' purpose is to convert from a codepoint index to a byte index, by a linear scan
     * starting from <code>offset</code>, counting the number of codepoints traversed until the
     * number is equal to <code>index</code>, and returning the corresponding byte position.
     * </p>
     *
     * <p>
     * To vectorize this, we convert every vector chunk loaded from the string into a bitset,
     * mapping the beginning of every codepoint to 1, and everything else to 0.
     * </p>
     *
     * <p>
     * Example for UTF-8, with the current 16-byte chunk being "ABCD\\u{ffff}A\\u{10ffff}ABCD", for
     * a total of 11 codepoints:
     *
     * <pre>
     * first, load the string chunk into a vector:
     *
     * 0x41 0x42 0x43 0x44 0xef 0xbf 0xbf 0x41 0xf4 0x8f 0xbf 0xbf 0x41 0x42 0x43 0x44
     *
     *
     * then, identify continuation bytes by checking <code>(b & 0xc0) == 0x80</code> on every byte:
     *
     * PAND
     * 0x41 0x42 0x43 0x44 0xef 0xbf 0xbf 0x41 0xf4 0x8f 0xbf 0xbf 0x41 0x42 0x43 0x44
     * 0xc0 0xc0 0xc0 0xc0 0xc0 0xc0 0xc0 0xc0 0xc0 0xc0 0xc0 0xc0 0xc0 0xc0 0xc0 0xc0
     * =
     * 0x40 0x40 0x40 0x40 0xc0 0x80 0x80 0x40 0xc0 0x80 0x80 0x80 0x40 0x40 0x40 0x40
     *
     * PCMPEQ
     * 0x40 0x40 0x40 0x40 0xc0 0x80 0x80 0x40 0xc0 0x80 0x80 0x80 0x40 0x40 0x40 0x40
     * 0x80 0x80 0x80 0x80 0x80 0x80 0x80 0x80 0x80 0x80 0x80 0x80 0x80 0x80 0x80 0x80
     * =
     * 0x00 0x00 0x00 0x00 0x00 0xff 0xff 0x00 0x00 0xff 0xff 0xff 0x00 0x00 0x00 0x00
     *
     *
     * Invert the result (only sign bits are relevant):
     *
     * PXOR
     * 0x00 0x00 0x00 0x00 0x00 0xff 0xff 0x00 0x00 0xff 0xff 0xff 0x00 0x00 0x00 0x00
     * 0x80 0x80 0x80 0x80 0x80 0x80 0x80 0x80 0x80 0x80 0x80 0x80 0x80 0x80 0x80 0x80
     * =
     * 0x80 0x80 0x80 0x80 0x80 0x7f 0x7f 0x80 0x80 0x7f 0x7f 0x7f 0x80 0x80 0x80 0x80
     *
     *
     * Convert to bitmask:
     *
     * PMOVMSK
     * 0x80 0x80 0x80 0x80 0x80 0x7f 0x7f 0x80 0x80 0x7f 0x7f 0x7f 0x80 0x80 0x80 0x80
     * =
     * b1111100110001111
     *
     *
     * Now we can use this bitmask to get the number of codepoints in the current chunk via POPCNT.
     * As long as the total number of codepoints found is less than the target index, we continue this loop.
     *
     * If the number of codepoints found so far is larger than the target index, we know the the target
     * codepoint's position is in the current chunk; so now we have to find it in the chunk.
     * We can use the bitmask for this as well, by performing a "bit-wise binary search":
     *
     * Suppose the target codepoint index is 7. In the vector loop, we compute the number of codepoints in
     * the chunk, which is 11, and subtract it from the target index. The resulting negative value (-4)
     * tells us that the target codepoint is in the current chunk. Now we split the bitmask into a upper and
     * lower half:
     *
     * lower: b11111001
     * upper: b10001111
     *
     * We get the lower half's codepoint count with POPCNT and add the result to the current negative index:
     *
     * POPCNT b11111001
     * = 6
     *
     * -4 + 6
     * = 2
     *
     * The result of this tells us whether the target codepoint is in the upper or lower half. If the result
     * was still negative, the target codepoint would be in the lower half, otherwise it's in the upper half.
     * We repeat this process with the resulting half bitmask, until the target codepoint is found, and add
     * the resulting index to the number of bytes processed by the vector loop so far, which gives us the
     * final result.
     *
     *
     * There is one shortcut to this process as well: if the current bitmask is all ones, we can simply add its
     * POPCNT to the current negative index and get the correct result:
     *
     * If e.g. the bitmask is b1111111111111111 and the target index is 7, we get 7 - 16 + 16 = 7.
     *
     * </pre>
     * </p>
     */
    private void emitOp(CompilationResultBuilder crb, AMD64MacroAssembler asm, Register arr, Register len, Register lengthTail, Register idx, Register ret) {
        Stride stride = Stride.S1;

        Label tailLessThan32 = new Label();
        Label tailLessThan16 = new Label();
        Label tailScalarLoop = new Label();
        Label tailScalarLoopHead = new Label();
        Label tailScalarLoopSkip = new Label();

        Label loop = new Label();
        Label loopTail = new Label();
        Label tailMask = new Label();
        Label tailMask16 = new Label();
        Label tailMask8 = new Label();
        Label tailMask4 = new Label();
        Label tailMask2 = new Label();
        Label tailMaskAscii = new Label();

        Label tailYMM = new Label();

        Label outOfBounds = new Label();
        Label end = new Label();

        Register vecArray = asRegister(vectorTemp[0]);
        Register vecMask2 = asRegister(vectorTemp[1]);
        Register vecMask1 = asRegister(vectorTemp[2]);
        Register vecTmp1 = asRegister(vectorTemp[3]);
        Register vecTmp2 = asRegister(vectorTemp[4]);

        Register tmp = asRegister(temp[0]);
        Register mask = asRegister(temp[1]);
        DataSection.Data xmmTailShuffleMask = writeToDataSection(crb, createXMMTailShuffleMask(XMM.getBytes()));

        if (inputEncoding == InputEncoding.UTF_16) {
            // convert char indices to byte indices, to avoid having to shift the result of every
            // popcnt
            asm.shll(len, 1);
            asm.shll(idx, 1);
        }
        // set result to zero
        asm.xorq(ret, ret);
        // duplicate length into lengthTail
        asm.movl(lengthTail, len);

        // if the target index is less than four, the scalar loop is faster
        asm.cmplAndJcc(idx, inputEncoding == InputEncoding.UTF_16 ? 8 : 4, Less, tailScalarLoop, false);

        switch (inputEncoding) {
            case InputEncoding.UTF_8:
                // create a vector filled with 0xc0
                loadMask(crb, asm, stride, vecMask1, 0xc0);
                // create a vector filled with 0x80
                loadMask(crb, asm, stride, vecMask2, 0x80);
                break;
            case InputEncoding.UTF_16:
                // create a vector filled with 0x37
                loadMask(crb, asm, Stride.S2, vecMask1, 0x37);
                // create a vector filled with 0xff
                asm.pcmpeqb(vectorSize, vecMask2, vecMask2);
                break;
        }

        // lengthTail = length % vectorLength
        asm.andl(lengthTail, vectorLength - 1);
        // check if length is large enough for the vector loop.
        asm.andlAndJcc(len, -vectorLength, Zero, supportsAVX2AndYMM() ? tailLessThan32 : tailLessThan16, false);

        // main vector loop
        asm.align(preferredLoopAlignment(crb));
        asm.bind(loop);
        // load one full vector from the array
        asm.movdqu(vectorSize, vecArray, new AMD64Address(arr, ret, stride));
        // count the number of codepoints in that vector
        countCodepoints(asm, ret, vecArray, vecMask1, vecMask2, mask, tmp, vectorSize);
        // subtract the number of code points from target index.
        // if the result is negative, the target index must be in the current vector
        asm.subl(idx, tmp);
        asm.jccb(Negative, tailMask);
        // otherwise, continue the loop
        asm.sublAndJcc(len, vectorLength, NotZero, loop, true);
        asm.jmp(loopTail);

        asm.align(preferredBranchTargetAlignment(crb));
        asm.bind(tailMask);
        // add current byte count (== total number of vector bytes processed so far) to len.
        // If the loop tail was hit, this will fully restore the original array length into len (due
        // to the preceding leaq instruction), otherwise the result is the array length modulo
        // vectorLength. This is good enough for the check at the "end" label where it is used
        // again.
        asm.addl(len, ret);
        // identify the byte position of target index in the last mask generated by pmovmsk via
        // binary search
        if (supportsAVX2AndYMM()) {
            // determine whether the target index is in the mask's lower or upper 16 bits
            emitMaskTailBinarySearch(crb, asm, tmp, idx, ret, mask, lengthTail, null, tailMask16, tailMaskAscii, 32, false);
        }
        // determine whether the target index is in the mask's lower or upper 8 bits
        emitMaskTailBinarySearch(crb, asm, tmp, idx, ret, mask, lengthTail, tailMask16, tailMask8, tailMaskAscii, 16, inputEncoding == InputEncoding.UTF_16);
        // determine whether the target index is in the mask's lower or upper 4 bits
        emitMaskTailBinarySearch(crb, asm, tmp, idx, ret, mask, lengthTail, tailMask8, tailMask4, tailMaskAscii, 8, true);
        switch (inputEncoding) {
            case InputEncoding.UTF_8:
                // determine whether the target index is in the mask's lower or upper 2 bits
                emitMaskTailBinarySearch(crb, asm, tmp, idx, ret, mask, lengthTail, tailMask4, tailMask2, tailMaskAscii, 4, true);
                asm.align(preferredBranchTargetAlignment(crb));
                asm.bind(tailMask2);
                // binary search is down to two bits at this point. check for b11
                asm.cmplAndJcc(mask, 0x3, Equal, tailMaskAscii, true);
                // at this point, the mask is either b10 or b01, and all we need to do is sub 1 from
                // ret if the mask is b10, or sub 2 from ret if the mask is b01. That is, the
                // negative index is either b10 - 3 = -1 or b01 - 3 = -2, so in either case it is
                // mask - 3.
                asm.subl(mask, 3);
                asm.movl(idx, mask);
                break;
            case InputEncoding.UTF_16:
                asm.align(preferredBranchTargetAlignment(crb));
                asm.bind(tailMask4);
                // binary search is down to four bits at this point. check for b1111
                asm.cmplAndJcc(mask, 0xf, Equal, tailMaskAscii, true);
                // at this point, the mask is either b1100 or b0011, and all we need to do is sub 2
                // from ret if the mask is b1100, or sub 4 from ret if the mask is b0011.
                asm.bsfq(idx, mask);
                asm.subl(idx, 4);
                break;
        }

        asm.align(preferredBranchTargetAlignment(crb));
        // The current mask's bits are all one, i.e. the corresponding substring is ascii, and the
        // current number of codepoints is equal to the current mask width. In this case, we can
        // simply add the current negative index to the result to get the correct byte index.
        asm.bind(tailMaskAscii);
        asm.addl(ret, idx);
        asm.jmp(end);

        asm.align(preferredLoopAlignment(crb));
        asm.bind(loopTail);
        // loop tail: load last elements into vector
        // move array pointer to begin of tail section (arr += [bytes processed in loop so far])
        asm.addq(arr, ret);
        // prepare to restore the original array length:
        // len = lengthTail - vectorLength
        asm.leaq(len, new AMD64Address(lengthTail, -vectorLength));
        // load the rest of the array into a vector via a load aligned to the end of the array and
        // eliminate all bytes already processed by the vector loop
        loadTailIntoYMMOrdered(crb, asm, stride, xmmTailShuffleMask, arr, lengthTail, vecArray, tmp, vecTmp1, vecTmp2);
        if (supportsAVX2AndYMM()) {
            asm.align(preferredBranchTargetAlignment(crb));
            asm.bind(tailYMM);
        }
        // process the last vector the same way the vector loop would
        countCodepoints(asm, ret, vecArray, vecMask1, vecMask2, mask, tmp, vectorSize);
        // subtract the number of code points from the target index
        asm.subl(idx, tmp);
        asm.jcc(Positive, outOfBounds);
        asm.jmp(tailMask);

        if (supportsAVX2AndYMM()) {
            asm.align(preferredBranchTargetAlignment(crb));
            asm.bind(tailLessThan32);
            asm.cmplAndJcc(lengthTail, 16, Less, tailLessThan16, true);
            // prepare to restore the original array length:
            // len = lengthTail - vectorLength
            asm.leaq(len, new AMD64Address(lengthTail, -vectorLength));
            // the array is smaller that a YMM register. In this case, we can still load it into a
            // YMM register via two overlapping XMM loads and some PSHUFB magic
            loadLessThan32IntoYMMOrdered(crb, asm, stride, xmmTailShuffleMask, arr, lengthTail, tmp, vecArray, vecTmp1, vecTmp2);
            asm.jmpb(tailYMM);
        }
        asm.align(preferredBranchTargetAlignment(crb));
        asm.bind(tailLessThan16);
        // restore array length
        asm.movl(len, lengthTail);
        asm.cmplAndJcc(lengthTail, 8, Less, tailScalarLoop, true);
        loadLessThan16IntoXMMOrdered(crb, asm, stride, arr, lengthTail, tmp, vecArray, vecTmp1, vecTmp2);
        countCodepoints(asm, ret, vecArray, vecMask1, vecMask2, mask, tmp, XMM);
        // subtract the number of code points from the target index
        asm.subl(idx, tmp);
        asm.jcc(Negative, tailMask16);
        asm.jmpb(outOfBounds);

        // scalar loop
        asm.align(preferredLoopAlignment(crb));
        asm.bind(tailScalarLoop);
        // exit if length is zero
        asm.testlAndJcc(lengthTail, lengthTail, Zero, end, true);
        asm.align(preferredLoopAlignment(crb));
        asm.bind(tailScalarLoopHead);
        switch (inputEncoding) {
            case InputEncoding.UTF_8:
                // load current byte
                asm.movzbl(tmp, new AMD64Address(arr, ret, stride));
                // check if it is a continuation byte
                asm.andl(tmp, 0xc0);
                asm.cmplAndJcc(tmp, 0x80, Equal, tailScalarLoopSkip, true);
                // not a continuation byte -> decrease idx.
                // if idx becomes negative, we found the target codepoint
                asm.decl(idx);
                asm.jccb(Negative, end);
                asm.bind(tailScalarLoopSkip);
                asm.incl(ret);
                asm.declAndJcc(lengthTail, NotZero, tailScalarLoopHead, true);
                break;
            case InputEncoding.UTF_16:
                // load current char
                asm.movzwl(tmp, new AMD64Address(arr, ret, stride));
                // check if it is a low surrogate
                asm.shrl(tmp, 10);
                asm.cmplAndJcc(tmp, 0x37, Equal, tailScalarLoopSkip, true);
                // not low surrogate -> decrease idx.
                // if idx becomes negative, we found the target codepoint
                asm.subl(idx, 2);
                asm.jccb(Negative, end);
                asm.bind(tailScalarLoopSkip);
                asm.addl(ret, 2);
                asm.sublAndJcc(lengthTail, 2, NotZero, tailScalarLoopHead, true);
                break;
        }
        asm.testlAndJcc(idx, idx, Zero, end, true);

        asm.align(preferredBranchTargetAlignment(crb));
        asm.bind(outOfBounds);
        // index is out of bounds, return -1
        asm.movl(ret, -1);
        asm.align(preferredBranchTargetAlignment(crb));
        asm.bind(end);
        // If the target index was out of bounds, our vectorized tail may produce an out-of-bounds
        // result due to the additional zero values generated by e.g. loadLessThan32IntoYMMOrdered.
        // Avoid returning this by comparing the result to the original array length.
        asm.movl(tmp, -1);
        asm.cmpl(ret, len);
        asm.cmovl(Greater, ret, tmp);
        if (inputEncoding == InputEncoding.UTF_16) {
            // convert result back into char index
            asm.sarl(ret, 1);
        }
    }

    private void countCodepoints(AMD64MacroAssembler asm, Register ret, Register vecArray, Register vecMask1, Register vecMask2, Register mask, Register tmp, AVXSize avxSize) {
        // codepoint counting logic:
        switch (inputEncoding) {
            case InputEncoding.UTF_8:
                // vecMask1 is filled with 0xc0
                // vecMask2 is filled with 0x80

                // AND the string's bytes with 0xc0
                asm.pand(avxSize, vecArray, vecMask1);
                // check if the result is equal to 0x80, identifying continuation bytes
                asm.pcmpeqb(avxSize, vecArray, vecMask2);
                // invert the result, identifying all non-continuation bytes => number of code
                // points. We can re-use the 0x80 mask to invert here, because pmovmsk only
                // considers the sign bit in each byte
                asm.pxor(avxSize, vecArray, vecMask2);
                break;
            case InputEncoding.UTF_16:
                // vecMask1 is filled with 0x0037
                // vecMask2 is filled with 0xffff

                // right-shift chars by 10
                asm.psrlw(avxSize, vecArray, vecArray, 10);
                // check if the result is equal to 0x37, identifying low surrogates
                asm.pcmpeqw(avxSize, vecArray, vecMask1);
                // invert the result
                asm.pxor(avxSize, vecArray, vecMask2);
                break;
        }
        // count the number of code points
        asm.pmovmsk(avxSize, mask, vecArray);
        asm.popcntl(tmp, mask);
        // add processed vector size to return value (byte index)
        asm.addl(ret, avxSize.getBytes());
    }

    private void emitMaskTailBinarySearch(CompilationResultBuilder crb, AMD64MacroAssembler asm, Register tmp, Register idx, Register ret, Register mask, Register maskCopy,
                    Label begin, Label nextTail, Label tailMaskAscii, int bits, boolean shortJmp) {
        if (begin != null) {
            asm.align(preferredBranchTargetAlignment(crb));
            asm.bind(begin);
        }
        // fast path:
        // check if bitmask is all ones. if so, we can skip the rest of the search, add the current
        // negative index to result and return
        asm.cmplAndJcc(mask, ~0 >>> (32 - bits), Equal, tailMaskAscii, shortJmp);

        // copy the current bitmask
        asm.movl(maskCopy, mask);
        // get upper half of bitmask
        asm.shrl(mask, bits / 2);
        // count upper half
        asm.popcntl(tmp, mask);
        // add current negative index.
        // if the result is still negative, target index is in the lower half.
        asm.addl(tmp, idx);
        asm.jccb(Positive, nextTail);
        // get lower half of bitmask
        asm.andl(maskCopy, ~0 >>> (32 - (bits / 2)));
        // adjust result for lower half
        asm.subl(ret, bits / 2);
        // set index to addition result
        asm.movl(idx, tmp);
        // set mask to lower half
        asm.movl(mask, maskCopy);
    }
}
