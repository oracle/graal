/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag.Equal;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag.Less;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag.NotEqual;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag.NotZero;
import static org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag.Zero;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.isAVX;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.movdl;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.movdqu;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.movlhps;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.palignr;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.pand;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.pandU;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.pandn;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.pcmpeqb;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.pcmpeqd;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.pcmpeqw;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.pcmpgtb;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.pcmpgtd;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.pmovmsk;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.por;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.pshufb;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.pslld;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.psllw;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.psrld;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.psrlw;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.ptest;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.ptestU;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.pxor;
import static org.graalvm.compiler.asm.amd64.AVXKind.AVXSize.DWORD;
import static org.graalvm.compiler.asm.amd64.AVXKind.AVXSize.QWORD;
import static org.graalvm.compiler.asm.amd64.AVXKind.AVXSize.XMM;
import static org.graalvm.compiler.asm.amd64.AVXKind.AVXSize.YMM;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Address.Scale;
import org.graalvm.compiler.asm.amd64.AMD64Assembler;
import org.graalvm.compiler.asm.amd64.AMD64BaseAssembler;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.asm.amd64.AVXKind;
import org.graalvm.compiler.code.DataSection;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.ArrayDataPointerConstant;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

/**
 * This intrinsic calculates the code range and codepoint length of strings in various encodings.
 * The code range of a string is a flag that coarsely describes upper and lower bounds of all
 * codepoints contained in a string, or indicates whether a string was encoded correctly.
 */
@Opcode("AMD64_CALC_STRING_ATTRIBUTES")
public final class AMD64CalcStringAttributesOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64CalcStringAttributesOp> TYPE = LIRInstructionClass.create(AMD64CalcStringAttributesOp.class);

    public enum Op {
        /**
         * Calculate the code range of a LATIN-1/ISO-8859-1 string. The result is either CR_7BIT or
         * CR_8BIT.
         */
        LATIN1(JavaKind.Byte),
        /**
         * Calculate the code range of a 16-bit (UTF-16 or compacted UTF-32) string that is already
         * known to be in the BMP code range, so no UTF-16 surrogates are present. The result is
         * either CR_7BIT, CR_8BIT, or CR_16BIT.
         */
        BMP(JavaKind.Char),
        /**
         * Calculate the code range and codepoint length of a UTF-8 string. The result is a long
         * value, where the upper 32 bit are the calculated codepoint length, and the lower 32 bit
         * are the code range. The resulting code range is either CR_7BIT, CR_VALID_MULTIBYTE or
         * CR_BROKEN_MULTIBYTE. If the string is broken (not encoded correctly), the resulting
         * codepoint length is the number of non-continuation bytes in the string.
         */
        UTF_8(JavaKind.Byte),
        /**
         * Calculate the code range and codepoint length of a UTF-16 string. The result is a long
         * value, where the upper 32 bit are the calculated codepoint length, and the lower 32 bit
         * are the code range. The resulting code range can be any of the following: CR_7BIT,
         * CR_8BIT, CR_16BIT, CR_VALID_MULTIBYTE, CR_BROKEN_MULTIBYTE. If the string is broken (not
         * encoded correctly), the resulting codepoint length includes all invalid surrogate
         * characters.
         */
        UTF_16(JavaKind.Char),
        /**
         * Calculate the code range of a UTF-32 string. The result can be any of the following:
         * CR_7BIT, CR_8BIT, CR_16BIT, CR_VALID_FIXED_WIDTH, CR_BROKEN_FIXED_WIDTH.
         */
        UTF_32(JavaKind.Int);

        /**
         * Stride to use when reading array elements.
         */
        private final JavaKind stride;

        Op(JavaKind stride) {
            this.stride = stride;
        }
    }

    private static final Register REG_ARRAY = rsi;
    private static final Register REG_OFFSET = rcx;
    private static final Register REG_LENGTH = rdx;

    // NOTE:
    // The following fields must be kept in sync with com.oracle.truffle.api.strings.TSCodeRange,
    // TStringOpsCalcStringAttributesReturnValuesInSyncTest verifies this.
    /**
     * All codepoints are ASCII (0x00 - 0x7f).
     */
    public static final int CR_7BIT = 0;
    /**
     * All codepoints are LATIN-1 (0x00 - 0xff).
     */
    public static final int CR_8BIT = 1;
    /**
     * All codepoints are BMP (0x0000 - 0xffff, no UTF-16 surrogates).
     */
    public static final int CR_16BIT = 2;
    /**
     * The string is encoded correctly in the given fixed-width encoding.
     */
    public static final int CR_VALID_FIXED_WIDTH = 3;
    /**
     * The string is not encoded correctly in the given fixed-width encoding.
     */
    public static final int CR_BROKEN_FIXED_WIDTH = 4;
    /**
     * The string is encoded correctly in the given multi-byte/variable-width encoding.
     */
    public static final int CR_VALID_MULTIBYTE = 5;
    /**
     * The string is not encoded correctly in the given multi-byte/variable-width encoding.
     */
    public static final int CR_BROKEN_MULTIBYTE = 6;

    private final Op op;

    private final Scale scale;
    private final AVXKind.AVXSize avxSize;
    private final int vectorSize;
    /**
     * If true, assume the string to be encoded correctly.
     */
    private final boolean assumeValid;

    @Def({REG}) private Value result;
    @Use({REG}) private Value array;
    @Use({REG}) private Value offset;
    @Use({REG}) private Value length;

    @Temp({REG}) private Value arrayTmp;
    @Temp({REG}) private Value offsetTmp;
    @Temp({REG}) private Value lengthTmp;

    @Temp({REG}) private Value[] temp;
    @Temp({REG}) private Value[] vectorTemp;

    private AMD64CalcStringAttributesOp(LIRGeneratorTool tool, Op op, Value array, Value offset, Value length, Value result, int maxVectorSize, boolean assumeValid) {
        super(TYPE);
        this.op = op;
        this.assumeValid = assumeValid;

        assert ((AMD64) tool.target().arch).getFeatures().contains(CPUFeature.SSE4_1);
        assert op.stride.isNumericInteger();

        this.scale = Objects.requireNonNull(Scale.fromInt(tool.getProviders().getMetaAccess().getArrayIndexScale(op.stride)));
        this.avxSize = ((AMD64) tool.target().arch).getFeatures().contains(CPUFeature.AVX2) && (maxVectorSize < 0 || maxVectorSize >= 32) ? YMM : XMM;
        this.vectorSize = avxSize.getBytes() / op.stride.getByteCount();

        this.arrayTmp = this.array = array;
        this.offsetTmp = this.offset = offset;
        this.lengthTmp = this.length = length;
        this.result = result;

        this.temp = new Value[getNumberOfTempRegisters(op, assumeValid)];
        for (int i = 0; i < temp.length; i++) {
            temp[i] = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
        }
        this.vectorTemp = new Value[getNumberOfRequiredVectorRegisters((AMD64) tool.target().arch, op, assumeValid)];
        for (int i = 0; i < vectorTemp.length; i++) {
            vectorTemp[i] = tool.newVariable(LIRKind.value(useYMM() ? AMD64Kind.V256_BYTE : AMD64Kind.V128_BYTE));
        }
    }

    private static int getNumberOfTempRegisters(Op op, boolean assumeValid) {
        switch (op) {
            case UTF_8:
                return assumeValid ? 1 : 3;
            case UTF_16:
                return assumeValid ? 1 : 2;
            default:
                return 0;
        }
    }

    private static int getNumberOfRequiredVectorRegisters(AMD64 arch, Op op, boolean assumeValid) {
        switch (op) {
            case LATIN1:
                return isAVX(arch) ? 1 : 2;
            case BMP:
                return isAVX(arch) ? 2 : 3;
            case UTF_8:
                return assumeValid ? 5 : 10;
            case UTF_16:
                return 6;
            case UTF_32:
                return 8;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    private int elementsPerVector(AVXKind.AVXSize size) {
        return size.getBytes() / op.stride.getByteCount();
    }

    private int elementsPerVector(AMD64BaseAssembler.OperandSize size) {
        return size.getBytes() / op.stride.getByteCount();
    }

    /**
     * Calculates the code range and codepoint length of strings in various encodings.
     *
     * @param op operation to run.
     * @param array arbitrary array.
     * @param byteOffset byteOffset to start from. Must include array base byteOffset!
     * @param length length of the array region to consider, scaled to {@link Op#stride}.
     * @param assumeValid assume that the string is encoded correctly.
     */
    public static AMD64CalcStringAttributesOp movParamsAndCreate(LIRGeneratorTool tool, Op op, Value array, Value byteOffset, Value length, Value result, int maxVectorSize, boolean assumeValid) {
        RegisterValue regArray = REG_ARRAY.asValue(array.getValueKind());
        RegisterValue regOffset = REG_OFFSET.asValue(byteOffset.getValueKind());
        RegisterValue regLength = REG_LENGTH.asValue(length.getValueKind());
        tool.emitMove(regArray, array);
        tool.emitMove(regOffset, byteOffset);
        tool.emitMove(regLength, length);
        return new AMD64CalcStringAttributesOp(tool, op, regArray, regOffset, regLength, result, maxVectorSize, assumeValid);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler asm) {
        Register arr = asRegister(array);
        Register off = asRegister(offset);
        Register len = asRegister(length);
        Register ret = asRegister(result);

        Register vec1 = asRegister(vectorTemp[0]);

        asm.leaq(arr, new AMD64Address(arr, off, Scale.Times1));

        asm.movl(off, len);

        switch (op) {
            case LATIN1:
                emitLatin1(crb, asm, arr, len, off, ret, vec1);
                break;
            case BMP:
                emitBMP(crb, asm, arr, len, off, ret, vec1);
                break;
            case UTF_8:
                emitUTF8(crb, asm, arr, len, off, ret, vec1);
                break;
            case UTF_16:
                emitUTF16(crb, asm, arr, len, off, ret, vec1);
                break;
            case UTF_32:
                emitUTF32(crb, asm, arr, len, off, ret, vec1);
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    private void emitLatin1(CompilationResultBuilder crb, AMD64MacroAssembler asm, Register arr, Register len, Register lengthTail, Register ret, Register vecMask) {
        assert scale.log2 == 0;
        Label tailLessThan32 = new Label();
        Label tailLessThan16 = new Label();
        Label tailLessThan8 = new Label();
        Label tailLessThan4 = new Label();
        Label tailLessThan2 = new Label();

        Label returnLatin1 = new Label();
        Label returnAscii = new Label();
        Label end = new Label();

        Register vecArray = isAVX(asm) ? null : asRegister(vectorTemp[1]);

        // load ascii PTEST mask consisting of 0x80 bytes
        DataSection.Data mask = createMask(crb, 0x80);
        movdqu(asm, avxSize, vecMask, (AMD64Address) crb.recordDataSectionReference(mask));

        vectorLoopPrologue(asm, arr, len, lengthTail, tailLessThan32, tailLessThan16, true);
        // main loop: check if all bytes are ascii with PTEST mask 0x80
        emitPTestLoop(crb, asm, arr, len, vecArray, vecMask, returnLatin1);
        emitPTestTail(asm, avxSize, arr, lengthTail, vecArray, vecMask, null, returnLatin1, returnAscii, false);

        if (useYMM()) {
            // tail for 16 - 31 bytes
            asm.bind(tailLessThan32);
            asm.cmplAndJcc(lengthTail, elementsPerVector(XMM), Less, tailLessThan16, true);
            emitPTestCurr(asm, XMM, arr, vecArray, vecMask, null, returnLatin1);
            emitPTestTail(asm, XMM, arr, lengthTail, vecArray, vecMask, null, returnLatin1, returnAscii);
        }

        emitExit(asm, ret, returnLatin1, end, CR_8BIT);

        asm.bind(tailLessThan16);
        // move mask into general purpose register for regular TEST instructions
        asm.movq(len, (AMD64Address) crb.recordDataSectionReference(mask));
        // tail for 8 - 15 bytes
        latin1Tail(asm, AMD64BaseAssembler.OperandSize.QWORD, arr, lengthTail, len, null, tailLessThan8, returnLatin1, returnAscii);
        // tail for 4 - 7 bytes
        latin1Tail(asm, AMD64BaseAssembler.OperandSize.DWORD, arr, lengthTail, len, tailLessThan8, tailLessThan4, returnLatin1, returnAscii);
        // tail for 2 - 3 bytes
        latin1Tail(asm, AMD64BaseAssembler.OperandSize.WORD, arr, lengthTail, len, tailLessThan4, tailLessThan2, returnLatin1, returnAscii);

        asm.bind(tailLessThan2);
        // tail for 0 - 1 bytes
        asm.testlAndJcc(lengthTail, lengthTail, Zero, returnAscii, true);
        asm.movzbq(len, new AMD64Address(arr));
        asm.testAndJcc(AMD64BaseAssembler.OperandSize.QWORD, len, 0x80, NotZero, returnLatin1, true);
        asm.jmpb(returnAscii);

        emitExitAtEnd(asm, ret, returnAscii, end, CR_7BIT);
    }

    private void latin1Tail(AMD64MacroAssembler asm, AMD64BaseAssembler.OperandSize size,
                    Register arr, Register lengthTail, Register mask,
                    Label entry, Label tooSmall, Label labelLatin1, Label labelAscii) {
        bind(asm, entry);
        asm.cmplAndJcc(lengthTail, elementsPerVector(size), Less, tooSmall, true);
        asm.testAndJcc(size, mask, new AMD64Address(arr), NotZero, labelLatin1, true);
        asm.testAndJcc(size, mask, new AMD64Address(arr, lengthTail, scale, -size.getBytes()), NotZero, labelLatin1, true);
        asm.jmpb(labelAscii);
    }

    /**
     * Vector loop prologue: calculate tail length and save it to {@code lengthTail}, convert
     * {@code len} to the vector loop count. If the vector loop count is zero, jump to
     * {@code tailLessThan32} if we are using YMM vectors, otherwise jump to {@code tailLessThan16}.
     * If the loop count is greater than zero, move the array pointer to the end of the vector loop,
     * and negate {@code len}. This way, we can read vectors from address {@code (arr, len)},
     * increase len by {@link #elementsPerVector(AVXKind.AVXSize)} in every iteration, and terminate
     * when {@code len is zero}.
     */
    private void vectorLoopPrologue(AMD64MacroAssembler asm, Register arr, Register len, Register lengthTail, Label tailLessThan32, Label tailLessThan16, boolean isShortJump) {
        asm.andl(lengthTail, vectorSize - 1);
        asm.andlAndJcc(len, -vectorSize, Zero, useYMM() ? tailLessThan32 : tailLessThan16, isShortJump);

        asm.leaq(arr, new AMD64Address(arr, len, scale));
        asm.negq(len);
    }

    /**
     * Emit a loop that compares array elements to a PTEST mask and breaks as soon as the result of
     * one such PTEST is non-zero.
     *
     * @param arr array pointer as calculated by
     *            {@link #vectorLoopPrologue(AMD64MacroAssembler, Register, Register, Register, Label, Label, boolean)}
     *            .
     * @param len negated loop count as calculated by
     *            {@link #vectorLoopPrologue(AMD64MacroAssembler, Register, Register, Register, Label, Label, boolean)}
     *            .
     * @param vecArray the register to load the array content into. Only used if AVX is not
     *            available.
     * @param vecMask the mask to compare against.
     * @param labelBreak target to jump to when a matching element was found.
     */
    private void emitPTestLoop(CompilationResultBuilder crb, AMD64MacroAssembler asm, Register arr, Register len, Register vecArray, Register vecMask, Label labelBreak) {
        Label loopHead = new Label();
        alignLoopHead(crb, asm);
        asm.bind(loopHead);
        ptestU(asm, avxSize, vecMask, new AMD64Address(arr, len, scale), vecArray);
        asm.jccb(NotZero, labelBreak);
        asm.addqAndJcc(len, vectorSize, NotZero, loopHead, true);
    }

    /**
     * PTEST the array at address {@code arr} with mask {@code vecMask}. If the result is nonzero,
     * jump to label {@code match}.
     */
    private static void emitPTestCurr(AMD64MacroAssembler asm, AVXKind.AVXSize size, Register arr, Register vecArray, Register vecMask, Label entry, Label match) {
        bind(asm, entry);
        ptestU(asm, size, vecMask, new AMD64Address(arr), vecArray);
        asm.jccb(NotZero, match);
    }

    private void emitPTestTail(AMD64MacroAssembler asm, AVXKind.AVXSize size, Register arr, Register lengthTail, Register vecArray, Register vecMask, Label entry, Label match, Label noMatch) {
        emitPTestTail(asm, size, arr, lengthTail, vecArray, vecMask, entry, match, noMatch, true);
    }

    /**
     * PTEST the array at address {@code (arr, lengthTail, -avxSize} with mask {@code vecMask}. If
     * the result is nonzero, jump to label {@code match}, otherwise jump to label {@code noMatch}.
     */
    private void emitPTestTail(AMD64MacroAssembler asm, AVXKind.AVXSize size, Register arr, Register lengthTail, Register vecArray, Register vecMask, Label entry, Label match, Label noMatch,
                    boolean isShortJmp) {
        // tail: align the last vector load to the end of the array, overlapping with the
        // main loop's last load
        bind(asm, entry);
        ptestU(asm, size, vecMask, new AMD64Address(arr, lengthTail, scale, -size.getBytes()), vecArray);
        asm.jccb(NotZero, match);
        asm.jmp(noMatch, isShortJmp);
    }

    private static void bind(AMD64MacroAssembler asm, Label entry) {
        if (entry != null) {
            asm.bind(entry);
        }
    }

    private void emitBMP(CompilationResultBuilder crb, AMD64MacroAssembler asm, Register arr, Register len, Register lengthTail, Register ret, Register vecMaskAscii) {
        assert scale.log2 == 1;
        Register vecMaskBMP = asRegister(vectorTemp[1]);
        Register vecArray = isAVX(asm) ? null : asRegister(vectorTemp[2]);
        Label latin1Entry = new Label();
        Label latin1TailCmp = new Label();

        Label tailLessThan32 = new Label();
        Label tailLessThan16 = new Label();
        Label tailLessThan8 = new Label();
        Label tailLessThan4 = new Label();

        Label returnBMP = new Label();
        Label returnLatin1 = new Label();
        Label returnAscii = new Label();
        Label end = new Label();

        // load ascii mask 0xff80
        loadMask(crb, asm, vecMaskAscii, 0xff80);
        // create bmp mask 0xff00
        psllw(asm, avxSize, vecMaskBMP, vecMaskAscii, 1);

        vectorLoopPrologue(asm, arr, len, lengthTail, tailLessThan32, tailLessThan16, true);

        // ascii loop: check if all chars are |<| 0x80 with PTEST mask 0xff80
        emitPTestLoop(crb, asm, arr, len, vecArray, vecMaskAscii, latin1Entry);
        emitPTestTail(asm, avxSize, arr, lengthTail, vecArray, vecMaskAscii, null, latin1TailCmp, returnAscii);

        asm.bind(latin1Entry);
        // latin1 loop: check if all chars are |<| 0x100 with PTEST mask 0xff00
        emitPTestLoop(crb, asm, arr, len, vecArray, vecMaskBMP, returnBMP);
        emitPTestTail(asm, avxSize, arr, lengthTail, vecArray, vecMaskBMP, latin1TailCmp, returnBMP, returnLatin1);

        if (useYMM()) {
            // tail for 16 - 31 bytes
            bmpTail(asm, arr, lengthTail, vecArray, vecMaskAscii, vecMaskBMP, tailLessThan32, tailLessThan16, returnBMP, returnLatin1, returnAscii);
        }

        emitExit(asm, ret, returnAscii, end, CR_7BIT);
        emitExit(asm, ret, returnLatin1, end, CR_8BIT);
        emitExit(asm, ret, returnBMP, end, CR_16BIT);

        asm.bind(tailLessThan16);
        // move masks into general purpose registers for regular TEST instructions
        asm.movdq(len, vecMaskAscii);
        asm.movdq(ret, vecMaskBMP);
        // tail for 8 - 15 bytes
        bmpTail(asm, AMD64BaseAssembler.OperandSize.QWORD, arr, lengthTail, len, ret, null, tailLessThan8, returnBMP, returnLatin1, returnAscii);
        // tail for 4 - 7 bytes
        bmpTail(asm, AMD64BaseAssembler.OperandSize.DWORD, arr, lengthTail, len, ret, tailLessThan8, tailLessThan4, returnBMP, returnLatin1, returnAscii);

        asm.bind(tailLessThan4);
        // tail for 0 - 3 bytes
        // since we're on a 2-byte stride, the only possible lengths are 0 and 2 bytes
        asm.testlAndJcc(lengthTail, lengthTail, Zero, returnAscii, true);
        asm.movzwq(len, new AMD64Address(arr));
        asm.testAndJcc(AMD64BaseAssembler.OperandSize.QWORD, len, 0xff80, Zero, returnAscii, true);
        asm.testAndJcc(AMD64BaseAssembler.OperandSize.QWORD, len, 0xff00, Zero, returnLatin1, true);
        asm.jmpb(returnBMP);

        asm.bind(end);
    }

    private void bmpTail(AMD64MacroAssembler asm,
                    Register arr, Register lengthTail, Register vecArray, Register vecMaskAscii, Register vecMaskBMP,
                    Label entry, Label tooSmall, Label returnBMP, Label returnLatin1, Label returnAscii) {
        asm.bind(entry);
        Label latin1Cur = new Label();
        Label latin1Tail = new Label();
        asm.cmplAndJcc(lengthTail, elementsPerVector(XMM), Less, tooSmall, true);

        // ascii
        emitPTestCurr(asm, XMM, arr, vecArray, vecMaskAscii, null, latin1Cur);
        emitPTestTail(asm, XMM, arr, lengthTail, vecArray, vecMaskAscii, null, latin1Tail, returnAscii);

        // latin1
        emitPTestCurr(asm, XMM, arr, vecArray, vecMaskBMP, latin1Cur, returnBMP);
        emitPTestTail(asm, XMM, arr, lengthTail, vecArray, vecMaskBMP, latin1Tail, returnBMP, returnLatin1);
    }

    private void bmpTail(AMD64MacroAssembler asm, AMD64BaseAssembler.OperandSize size,
                    Register arr, Register lengthTail, Register maskAscii, Register maskBMP,
                    Label entry, Label tooSmall, Label returnBMP, Label returnLatin1, Label returnAscii) {
        bind(asm, entry);
        Label latin1Cur = new Label();
        Label latin1Tail = new Label();
        asm.cmplAndJcc(lengthTail, elementsPerVector(size), Less, tooSmall, true);

        // ascii
        emitTestCurr(asm, size, arr, maskAscii, null, latin1Cur);
        emitTestTail(asm, size, arr, lengthTail, maskAscii, null, latin1Tail, returnAscii);

        // latin1
        emitTestCurr(asm, size, arr, maskBMP, latin1Cur, returnBMP);
        emitTestTail(asm, size, arr, lengthTail, maskBMP, latin1Tail, returnBMP, returnLatin1);
    }

    private static void emitTestCurr(AMD64MacroAssembler asm, AMD64BaseAssembler.OperandSize size, Register arr, Register mask, Label entry, Label match) {
        bind(asm, entry);
        asm.testAndJcc(size, mask, new AMD64Address(arr), NotZero, match, true);
    }

    private void emitTestTail(AMD64MacroAssembler asm, AMD64BaseAssembler.OperandSize size, Register arr, Register lengthTail, Register mask, Label entry, Label match, Label noMatch) {
        // tail: align the last vector load to the end of the array, overlapping with the
        // main loop's last load
        bind(asm, entry);
        asm.testAndJcc(size, mask, new AMD64Address(arr, lengthTail, scale, -size.getBytes()), NotZero, match, true);
        asm.jmpb(noMatch);
    }

    private static final byte TOO_SHORT = 1 << 0;
    private static final byte TOO_LONG = 1 << 1;
    private static final byte OVERLONG_3 = 1 << 2;
    private static final byte SURROGATE = 1 << 4;
    private static final byte OVERLONG_2 = 1 << 5;
    private static final byte TWO_CONTS = (byte) (1 << 7);
    private static final byte TOO_LARGE = 1 << 3;
    private static final byte TOO_LARGE_1000 = 1 << 6; // intentionally equal to OVERLONG_4
    private static final byte OVERLONG_4 = 1 << 6; // intentionally equal to TOO_LARGE_1000
    private static final byte CARRY = TOO_SHORT | TOO_LONG | TWO_CONTS;

    private static final byte[] UTF8_BYTE_1_HIGH_TABLE = {
                    TOO_LONG, TOO_LONG, TOO_LONG, TOO_LONG,
                    TOO_LONG, TOO_LONG, TOO_LONG, TOO_LONG,

                    TWO_CONTS, TWO_CONTS, TWO_CONTS, TWO_CONTS,

                    TOO_SHORT | OVERLONG_2,
                    TOO_SHORT,
                    TOO_SHORT | OVERLONG_3 | SURROGATE,
                    TOO_SHORT | TOO_LARGE | TOO_LARGE_1000 | OVERLONG_4
    };

    private static final byte[] UTF8_BYTE_1_LOW_TABLE = {
                    CARRY | OVERLONG_3 | OVERLONG_2 | OVERLONG_4,
                    CARRY | OVERLONG_2,
                    CARRY,
                    CARRY,

                    CARRY | TOO_LARGE,
                    CARRY | TOO_LARGE | TOO_LARGE_1000,
                    CARRY | TOO_LARGE | TOO_LARGE_1000,
                    CARRY | TOO_LARGE | TOO_LARGE_1000,

                    CARRY | TOO_LARGE | TOO_LARGE_1000,
                    CARRY | TOO_LARGE | TOO_LARGE_1000,
                    CARRY | TOO_LARGE | TOO_LARGE_1000,
                    CARRY | TOO_LARGE | TOO_LARGE_1000,

                    CARRY | TOO_LARGE | TOO_LARGE_1000,
                    CARRY | TOO_LARGE | TOO_LARGE_1000 | SURROGATE,
                    CARRY | TOO_LARGE | TOO_LARGE_1000,
                    CARRY | TOO_LARGE | TOO_LARGE_1000
    };

    private static final byte[] UTF8_BYTE_2_HIGH_TABLE = {
                    TOO_SHORT, TOO_SHORT, TOO_SHORT, TOO_SHORT,
                    TOO_SHORT, TOO_SHORT, TOO_SHORT, TOO_SHORT,

                    TOO_LONG | OVERLONG_2 | TWO_CONTS | OVERLONG_3 | TOO_LARGE_1000 | OVERLONG_4,
                    TOO_LONG | OVERLONG_2 | TWO_CONTS | OVERLONG_3 | TOO_LARGE,
                    TOO_LONG | OVERLONG_2 | TWO_CONTS | SURROGATE | TOO_LARGE,
                    TOO_LONG | OVERLONG_2 | TWO_CONTS | SURROGATE | TOO_LARGE,

                    TOO_SHORT, TOO_SHORT, TOO_SHORT, TOO_SHORT
    };

    /**
     * Copyright (c) 2008-2010 Bjoern Hoehrmann <bjoern@hoehrmann.de>
     *
     * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
     * and associated documentation files (the "Software"), to deal in the Software without
     * restriction, including without limitation the rights to use, copy, modify, merge, publish,
     * distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
     * Software is furnished to do so, subject to the following conditions:
     *
     * The above copyright notice and this permission notice shall be included in all copies or
     * substantial portions of the Software.
     *
     * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
     * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
     * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
     * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
     * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
     *
     * See http://bjoern.hoehrmann.de/utf-8/decoder/dfa/ for details.
     */
    private static final byte[] UTF_8_STATE_MACHINE = {
                    // The first part of the table maps bytes to character classes
                    // to reduce the size of the transition table and create bitmasks.
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9,
                    7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
                    8, 8, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
                    10, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 3, 3, 11, 6, 6, 6, 5, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8,

                    // The second part is a transition table that maps a combination
                    // of a state of the automaton and a character class to a state.
                    0, 12, 24, 36, 60, 96, 84, 12, 12, 12, 48, 72, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12,
                    12, 0, 12, 12, 12, 12, 12, 0, 12, 0, 12, 12, 12, 24, 12, 12, 12, 12, 12, 24, 12, 24, 12, 12,
                    12, 12, 12, 12, 12, 12, 12, 24, 12, 12, 12, 12, 12, 24, 12, 12, 12, 12, 12, 12, 12, 24, 12, 12,
                    12, 12, 12, 12, 12, 12, 12, 36, 12, 36, 12, 12, 12, 36, 12, 12, 12, 12, 12, 36, 12, 36, 12, 12,
                    12, 36, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12,
    };

    /**
     * Based on the paper <a href="https://arxiv.org/abs/2010.03090">Validating UTF-8 In Less Than
     * One Instruction Per Byte</a> by John Keiser and Daniel Lemire, and the author's
     * implementation in <a href=
     * "https://github.com/simdjson/simdjson/blob/d996ffc49423cee75922c30432323288c34f3c04/src/generic/stage1/utf8_lookup4_algorithm.h">
     * the simdjson library</a>.
     *
     * @see <a href="https://github.com/simdjson/simdjson">https://github.com/simdjson/simdjson</a>
     * @see <a href="https://lemire.me/blog/2020/10/20/ridiculously-fast-unicode-utf-8-validation/">
     *      https://lemire.me/blog/2020/10/20/ridiculously-fast-unicode-utf-8-validation/</a>
     */
    private void emitUTF8(CompilationResultBuilder crb, AMD64MacroAssembler asm, Register arr, Register len, Register lengthTail, Register ret, Register vecArray) {
        assert scale.log2 == 0;
        Register tmp = asRegister(temp[0]);
        Register vecMask = asRegister(vectorTemp[1]);
        Register vecMaskCB = asRegister(vectorTemp[2]);
        Register vecTmp1 = asRegister(vectorTemp[3]);
        Register vecTmp2 = asRegister(vectorTemp[4]);

        Register vecPrevArray = assumeValid ? null : asRegister(vectorTemp[5]);
        Register vecError = assumeValid ? null : asRegister(vectorTemp[6]);
        Register vecPrevIsIncomplete = assumeValid ? null : asRegister(vectorTemp[7]);

        Label asciiLoop = new Label();

        Label labelMultiByteEntry = new Label();
        Label labelMultiByteLoop = new Label();
        Label labelMultiByteTail = new Label();

        Label tailLessThan32 = new Label();
        Label tailLessThan16 = new Label();
        Label tailLessThan8 = new Label();
        Label tailSingleVector = new Label();

        Label labelScalarTail = new Label();
        Label labelScalarAsciiLoop = new Label();
        Label labelScalarMultiByteLoop = new Label();
        Label labelScalarMultiByteLoopEntry = new Label();
        Label labelScalarMultiByteLoopSkipDec = new Label();

        Label returnValid = new Label();
        Label returnAscii = new Label();
        Label end = new Label();

        // copy array length into lengthTail and return value
        asm.movl(ret, len);

        loadMask(crb, asm, vecMask, 0x80);
        loadMask(crb, asm, vecMaskCB, 0xc0);
        DataSection.Data validMaskTail = assumeValid ? createTailMask(crb) : null;

        if (!assumeValid) {
            // initialize multibyte loop state registers
            pxor(asm, avxSize, vecPrevArray, vecPrevArray);
            pxor(asm, avxSize, vecError, vecError);
            pxor(asm, avxSize, vecPrevIsIncomplete, vecPrevIsIncomplete);
        }

        vectorLoopPrologue(asm, arr, len, lengthTail, tailLessThan32, !assumeValid ? tailLessThan32 : tailLessThan16, false);

        // ascii loop: check if all bytes are |<| 0x80 with PTEST
        alignLoopHead(crb, asm);
        asm.bind(asciiLoop);
        movdqu(asm, avxSize, vecArray, new AMD64Address(arr, len, scale));
        ptest(asm, avxSize, vecArray, vecMask);
        asm.jccb(NotZero, labelMultiByteEntry);
        asm.addqAndJcc(len, vectorSize, NotZero, asciiLoop, true);

        // ascii tail
        movdqu(asm, avxSize, vecArray, new AMD64Address(arr, lengthTail, scale, -avxSize.getBytes()));
        ptest(asm, avxSize, vecArray, vecMask);
        asm.jcc(NotZero, labelMultiByteTail, assumeValid);
        asm.jmp(returnAscii);

        if (assumeValid) {
            // multibyte loop: at least one byte is not ascii, calculate the codepoint length
            alignLoopHead(crb, asm);
            asm.bind(labelMultiByteLoop);
            movdqu(asm, avxSize, vecArray, new AMD64Address(arr, len, scale));
            asm.bind(labelMultiByteEntry);
            utf8SubtractContinuationBytes(asm, ret, vecArray, tmp, vecMask, vecMaskCB);
            asm.addqAndJcc(len, vectorSize, NotZero, labelMultiByteLoop, true);

            // multibyte loop tail: do an overlapping tail load, and zero out all bytes that would
            // overlap with the last loop iteration
            asm.testlAndJcc(lengthTail, lengthTail, Zero, returnValid, false);
            // load tail vector
            movdqu(asm, avxSize, vecArray, new AMD64Address(arr, lengthTail, scale, -avxSize.getBytes()));
            asm.bind(labelMultiByteTail);
            // load tail mask address
            asm.leaq(tmp, (AMD64Address) crb.recordDataSectionReference(validMaskTail));
            // remove overlapping bytes
            pandU(asm, avxSize, vecArray, new AMD64Address(tmp, lengthTail, scale), vecTmp1);
            // identify continuation bytes
            utf8SubtractContinuationBytes(asm, ret, vecArray, tmp, vecMask, vecMaskCB);
            asm.jmp(returnValid);

            if (useYMM()) {
                // special case: array is too short for YMM, try XMM
                // no loop, because the array is guaranteed to be smaller that 2 XMM registers
                asm.bind(tailLessThan32);
                asm.cmplAndJcc(lengthTail, elementsPerVector(XMM), Less, tailLessThan16, true);
                loadLessThan32IntoYMMUnordered(crb, asm, validMaskTail, arr, lengthTail, tmp, vecArray, vecTmp1, vecTmp2);
                asm.jmpb(tailSingleVector);
            }
            asm.bind(tailLessThan16);
            asm.cmplAndJcc(lengthTail, elementsPerVector(QWORD), Less, tailLessThan8, true);
            loadLessThan16IntoXMMUnordered(crb, asm, validMaskTail, arr, lengthTail, tmp, vecArray, vecTmp1, vecTmp2);
            asm.jmpb(tailSingleVector);

            asm.bind(tailLessThan8);
            asm.cmplAndJcc(lengthTail, elementsPerVector(DWORD), Less, labelScalarTail, true);
            loadLessThan8IntoXMMOrdered(asm, arr, lengthTail, vecArray, tmp, len);

            asm.bind(tailSingleVector);
            // check if first vector is ascii
            ptest(asm, avxSize, vecArray, vecMask);
            // not ascii, go to scalar loop
            asm.jccb(Zero, returnAscii);
            utf8SubtractContinuationBytes(asm, ret, vecArray, tmp, vecMask, vecMaskCB);
            asm.jmp(returnValid);
        } else {
            Label labelMultiByteEnd = new Label();
            Label labelMultiByteTailLoopEntry = new Label();

            byte[] isIncompleteMaskBytes = new byte[avxSize.getBytes()];
            Arrays.fill(isIncompleteMaskBytes, (byte) ~0);
            isIncompleteMaskBytes[avxSize.getBytes() - 3] = (byte) (0b11110000 - 1);
            isIncompleteMaskBytes[avxSize.getBytes() - 2] = (byte) (0b11100000 - 1);
            isIncompleteMaskBytes[avxSize.getBytes() - 1] = (byte) (0b11000000 - 1);
            DataSection.Data isIncompleteMask = writeToDataSection(crb, isIncompleteMaskBytes);
            DataSection.Data mask0x0F = createMask(crb, (byte) 0x0F);
            DataSection.Data mask3ByteSeq = createMask(crb, (byte) 0b11100000 - 1);
            DataSection.Data mask4ByteSeq = createMask(crb, (byte) 0b11110000 - 1);
            DataSection.Data xmmTailShuffleMask = writeToDataSection(crb, createXMMTailShuffleMask(XMM.getBytes()));

            Register vecTmp3 = asRegister(vectorTemp[8]);
            Register vecTmp4 = asRegister(vectorTemp[9]);

            // multibyte loop: at least one byte is not ascii, calculate the codepoint length
            alignLoopHead(crb, asm);
            asm.bind(labelMultiByteLoop);
            movdqu(asm, avxSize, vecArray, new AMD64Address(arr, len, scale));
            // fast path: check if current vector is ascii
            asm.bind(labelMultiByteTailLoopEntry);
            ptest(asm, avxSize, vecArray, vecMask);
            asm.jccb(NotZero, labelMultiByteEntry);
            // current vector is ascii, if the previous vector ended with an incomplete sequence, we
            // found an error
            por(asm, avxSize, vecError, vecPrevIsIncomplete);
            // continue
            asm.addqAndJcc(len, vectorSize, NotZero, labelMultiByteLoop, true);
            asm.jmp(labelMultiByteTail);

            asm.bind(labelMultiByteEntry);

            // codepoint counting logic:
            // AND the string's bytes with 0xc0
            pand(asm, avxSize, vecTmp1, vecArray, vecMaskCB);
            // check if the result is equal to 0x80, identifying continuation bytes
            pcmpeqb(asm, avxSize, vecTmp1, vecMask);
            // count the number of continuation bytes
            pmovmsk(asm, avxSize, tmp, vecTmp1);
            asm.popcntl(tmp, tmp);
            // subtract the number of continuation bytes from the total byte length
            asm.subl(ret, tmp);

            // load a view of the array at current index - 1 into vecTmp3
            prev(asm, avxSize, vecTmp3, vecArray, vecPrevArray, 1);
            // load lower nibbles of prev1 into vecTmp3
            // load upper nibbles of prev1 into vecTmp4
            psrlw(asm, avxSize, vecTmp4, vecTmp3, 4);
            pandData(crb, asm, avxSize, vecTmp3, mask0x0F, vecTmp1);
            pandData(crb, asm, avxSize, vecTmp4, mask0x0F, vecTmp1);
            // perform table lookup for both nibbles
            movdqu(asm, avxSize, vecTmp1, getMaskOnce(crb, getStaticLUT(UTF8_BYTE_1_LOW_TABLE)));
            movdqu(asm, avxSize, vecTmp2, getMaskOnce(crb, getStaticLUT(UTF8_BYTE_1_HIGH_TABLE)));
            pshufb(asm, avxSize, vecTmp1, vecTmp3);
            pshufb(asm, avxSize, vecTmp2, vecTmp4);
            // combine result into vecTmp1
            pand(asm, avxSize, vecTmp1, vecTmp2);
            movdqu(asm, avxSize, vecTmp2, getMaskOnce(crb, getStaticLUT(UTF8_BYTE_2_HIGH_TABLE)));
            // load upper nibbles of array at current index into vecTmp3
            psrlw(asm, avxSize, vecTmp3, vecArray, 4);
            pandData(crb, asm, avxSize, vecTmp3, mask0x0F, vecTmp4);
            // table lookup
            pshufb(asm, avxSize, vecTmp2, vecTmp3);
            // combine result into vecTmp1
            pand(asm, avxSize, vecTmp1, vecTmp2);

            // load a view of the array at current index - 2 into vecTmp2
            prev(asm, avxSize, vecTmp2, vecArray, vecPrevArray, 2);
            // load a view of the array at current index - 3 into vecTmp3
            prev(asm, avxSize, vecTmp3, vecArray, vecPrevArray, 3);
            // find all bytes greater or equal to 0b11100000 in prev2
            psubusbData(crb, asm, avxSize, vecTmp2, vecTmp2, mask3ByteSeq, vecTmp4);
            // find all bytes greater or equal to 0b11110000 in prev3
            psubusbData(crb, asm, avxSize, vecTmp3, vecTmp3, mask4ByteSeq, vecTmp4);
            // combine result into vecTmp2
            por(asm, avxSize, vecTmp2, vecTmp3);
            // convert result to 0xff for all matching bytes
            pxor(asm, avxSize, vecTmp3, vecTmp3);
            pcmpgtb(asm, avxSize, vecTmp2, vecTmp3);
            // convert result to 0x80 for all matching bytes
            pand(asm, avxSize, vecTmp2, vecMask);
            // xor the result with the combined table lookup results
            pxor(asm, avxSize, vecTmp1, vecTmp2);
            // if the result of all this is non-zero, we found an error
            por(asm, avxSize, vecError, vecTmp1);

            // check if the current vector ends with an incomplete multibyte-sequence
            // (needed only for ascii fast-path)
            psubusbData(crb, asm, avxSize, vecPrevIsIncomplete, vecArray, isIncompleteMask, vecTmp1);
            // save current vector as vecPrevArray
            movdqu(asm, avxSize, vecPrevArray, vecArray);
            asm.addqAndJcc(len, vectorSize, NotZero, labelMultiByteLoop, false);

            asm.bind(labelMultiByteTail);
            asm.testqAndJcc(lengthTail, lengthTail, Zero, labelMultiByteEnd, true);
            loadTailIntoYMMOrdered(crb, asm, xmmTailShuffleMask, arr, lengthTail, vecArray, tmp, vecTmp1, vecTmp2);
            // adjust length registers such that the loop will terminate after the following
            // iteration
            asm.xorq(lengthTail, lengthTail);
            asm.subq(len, vectorSize);
            asm.jmp(labelMultiByteTailLoopEntry);

            asm.bind(labelMultiByteEnd);
            por(asm, avxSize, vecError, vecPrevIsIncomplete);
            ptest(asm, avxSize, vecError, vecError);
            asm.jcc(Zero, returnValid);
            asm.shlq(ret, 32);
            asm.orq(ret, CR_BROKEN_MULTIBYTE);
            asm.jmp(end);

            asm.bind(tailLessThan32);
            Register tmp2 = asRegister(temp[1]);

            if (useYMM()) {
                asm.cmplAndJcc(lengthTail, 16, Less, tailLessThan16, true);
                loadLessThan32IntoYMMOrdered(crb, asm, xmmTailShuffleMask, arr, lengthTail, tmp, vecArray, vecTmp1, vecTmp2);
                asm.jmp(tailSingleVector);
                asm.bind(tailLessThan16);
            }
            asm.cmplAndJcc(lengthTail, 8, Less, tailLessThan8, true);
            loadLessThan16IntoXMMOrdered(crb, asm, arr, lengthTail, tmp, vecArray, vecTmp1, vecTmp2);
            asm.jmpb(tailSingleVector);

            asm.bind(tailLessThan8);
            asm.cmplAndJcc(lengthTail, 4, Less, labelScalarTail, true);
            loadLessThan8IntoXMMOrdered(asm, arr, lengthTail, vecArray, tmp, tmp2);

            asm.bind(tailSingleVector);
            ptest(asm, avxSize, vecArray, vecMask);
            asm.jcc(Zero, returnAscii);
            asm.subq(len, vectorSize);
            asm.xorq(lengthTail, lengthTail);
            asm.jmp(labelMultiByteEntry);
        }

        asm.bind(labelScalarTail);
        asm.leaq(arr, new AMD64Address(arr, lengthTail, scale));
        asm.testqAndJcc(lengthTail, lengthTail, Zero, returnAscii, true);
        asm.negq(lengthTail);

        // scalar ascii loop
        alignLoopHead(crb, asm);
        asm.bind(labelScalarAsciiLoop);
        asm.movzbl(tmp, new AMD64Address(arr, lengthTail, scale));
        asm.testlAndJcc(tmp, 0x80, NotZero, labelScalarMultiByteLoopEntry, true);
        asm.incqAndJcc(lengthTail, NotZero, labelScalarAsciiLoop, true);
        asm.jmpb(returnAscii);

        // scalar multibyte loop
        asm.bind(labelScalarMultiByteLoopEntry);
        if (assumeValid) {
            alignLoopHead(crb, asm);
            asm.bind(labelScalarMultiByteLoop);
            asm.movzbq(tmp, new AMD64Address(arr, lengthTail, scale));
            asm.andl(tmp, 0xc0);
            asm.cmplAndJcc(tmp, 0x80, NotEqual, labelScalarMultiByteLoopSkipDec, true);
            asm.decl(ret);
            asm.bind(labelScalarMultiByteLoopSkipDec);
            asm.incqAndJcc(lengthTail, NotZero, labelScalarMultiByteLoop, true);
        } else {
            // assembly implementation of the state machine - based UTF-8 decoder from
            // http://bjoern.hoehrmann.de/utf-8/decoder/dfa/,
            // without the actual codepoint computation.
            Register state = asRegister(temp[1]);
            Register type = asRegister(temp[2]);
            asm.leaq(len, getMaskOnce(crb, UTF_8_STATE_MACHINE));
            asm.xorq(state, state);
            alignLoopHead(crb, asm);
            asm.bind(labelScalarMultiByteLoop);
            asm.movzbq(tmp, new AMD64Address(arr, lengthTail, scale));
            asm.movzbq(type, new AMD64Address(len, tmp, scale));
            asm.andl(tmp, 0xc0);
            asm.addq(type, state);
            asm.movzbq(state, new AMD64Address(len, type, scale, 256));
            asm.cmplAndJcc(tmp, 0x80, NotEqual, labelScalarMultiByteLoopSkipDec, true);
            asm.decl(ret);
            asm.bind(labelScalarMultiByteLoopSkipDec);
            asm.incqAndJcc(lengthTail, NotZero, labelScalarMultiByteLoop, true);

            asm.testqAndJcc(state, state, Zero, returnValid, true);
            asm.shlq(ret, 32);
            asm.orq(ret, CR_BROKEN_MULTIBYTE);
            asm.jmpb(end);
        }

        emitExitMultiByte(asm, ret, returnValid, end, CR_VALID_MULTIBYTE);
        emitExitMultiByteAtEnd(asm, ret, returnAscii, end, CR_7BIT);
    }

    /**
     * Find all UTF-8 continuation bytes in {@code vecArray}, count them, and subtract their count
     * from {@code ret}.
     *
     * Kills {@code tmp} and {@code vecArray}.
     */
    private void utf8SubtractContinuationBytes(AMD64MacroAssembler asm, Register ret, Register vecArray, Register tmp, Register vecMask, Register vecMaskCB) {
        // AND the string's bytes with 0xc0
        pand(asm, avxSize, vecArray, vecMaskCB);
        // check if the result is equal to 0x80, identifying continuation bytes
        pcmpeqb(asm, avxSize, vecArray, vecMask);
        // count the number of continuation bytes
        pmovmsk(asm, avxSize, tmp, vecArray);
        asm.popcntl(tmp, tmp);
        // subtract the number of continuation bytes from the total byte length
        asm.subl(ret, tmp);
    }

    /**
     * Load the tail of an XMM or YMM vector loop into a XMM/YMM register, via vector loads aligned
     * to the end of the array. The preceding loop must have had at least one iteration, or this
     * will read out of bounds. After this operation, {@code vecArray} contains all tail byes (0-15
     * bytes on XMM, 0-31 bytes on YMM) as if it was loaded directly from address {@code (arr)}.
     *
     * Kills all parameter registers except {@code arr}.
     *
     * @param xmmTailShuffleMask result of {@link #createXMMTailShuffleMask(int)}.
     */
    private void loadTailIntoYMMOrdered(CompilationResultBuilder crb, AMD64MacroAssembler asm, DataSection.Data xmmTailShuffleMask,
                    Register arr, Register lengthTail, Register vecArray, Register tmp, Register vecTmp1, Register vecTmp2) {
        if (useYMM()) {
            Label lessThan16 = new Label();
            Label done = new Label();
            asm.leaq(tmp, (AMD64Address) crb.recordDataSectionReference(xmmTailShuffleMask));
            // load last 16 bytes
            movdqu(asm, XMM, vecArray, new AMD64Address(arr, lengthTail, scale, -XMM.getBytes()));
            // if we're using YMM vectors and the tail is greater than 16 bytes, load the tail's
            // first 16 bytes and prepend them to the shifted vector
            asm.cmpqAndJcc(lengthTail, elementsPerVector(XMM), Less, lessThan16, true);
            movdqu(asm, XMM, vecTmp1, new AMD64Address(arr));
            asm.negq(lengthTail);
            // load shuffle mask into a tmp vector, because pshufb doesn't support misaligned
            // memory parameters
            movdqu(asm, XMM, vecTmp2, new AMD64Address(tmp, lengthTail, scale, XMM.getBytes() * 2));
            // shuffle the tail vector such that its content effectively gets left-shifted by
            // 16 - lengthTail bytes
            pshufb(asm, XMM, vecArray, vecTmp2);
            AMD64Assembler.VexRVMIOp.VPERM2I128.emit(asm, avxSize, vecArray, vecArray, vecTmp1, 0x02);
            asm.jmpb(done);
            asm.bind(lessThan16);
            asm.negq(lengthTail);
            // load shuffle mask into a tmp vector, because pshufb doesn't support misaligned
            // memory parameters
            movdqu(asm, XMM, vecTmp2, new AMD64Address(tmp, lengthTail, scale, XMM.getBytes()));
            // shuffle the tail vector such that its content effectively gets right-shifted by
            // 16 - lengthTail bytes
            pshufb(asm, XMM, vecArray, vecTmp2);
            asm.bind(done);
        } else {
            asm.leaq(tmp, (AMD64Address) crb.recordDataSectionReference(xmmTailShuffleMask));
            // load last 16 bytes
            movdqu(asm, XMM, vecArray, new AMD64Address(arr, lengthTail, scale, -XMM.getBytes()));
            asm.negq(lengthTail);
            // load shuffle mask into a tmp vector, because pshufb doesn't support misaligned
            // memory parameters
            movdqu(asm, XMM, vecTmp2, new AMD64Address(tmp, lengthTail, scale, XMM.getBytes()));
            // shuffle the tail vector such that its content effectively gets right-shifted by
            // 16 - lengthTail bytes
            pshufb(asm, XMM, vecArray, vecTmp2);
        }
    }

    /**
     * Load an array of 16-31 bytes into a YMM register.
     */
    private void loadLessThan32IntoYMMOrdered(CompilationResultBuilder crb, AMD64MacroAssembler asm, DataSection.Data xmmTailShuffleMask,
                    Register arr, Register lengthTail, Register tmp, Register vecArray, Register vecTmp1, Register vecTmp2) {
        // array is between 16 and 31 bytes long, load it into a YMM register via two XMM loads
        movdqu(asm, XMM, vecTmp1, new AMD64Address(arr));
        movdqu(asm, XMM, vecArray, new AMD64Address(arr, lengthTail, scale, -XMM.getBytes()));
        asm.leaq(tmp, (AMD64Address) crb.recordDataSectionReference(xmmTailShuffleMask));
        asm.negq(lengthTail);
        // load shuffle mask into a tmp vector, because pshufb doesn't support misaligned
        // memory parameters
        movdqu(asm, XMM, vecTmp2, new AMD64Address(tmp, lengthTail, scale, XMM.getBytes() * 2));
        // shuffle the tail vector such that its content effectively gets right-shifted by
        // 16 - lengthTail bytes
        pshufb(asm, XMM, vecArray, vecTmp2);
        AMD64Assembler.VexRVMIOp.VPERM2I128.emit(asm, avxSize, vecArray, vecArray, vecTmp1, 0x02);
    }

    /**
     * Load an array of 16-31 bytes into a YMM register, with a gap in the middle, e.g. the
     * resulting vector of an array of length 20 is:
     *
     * <pre>
     * {@code
     * [<array bytes 0-15> <0x00, 12 times> <array bytes 16-19>]
     * }
     * </pre>
     */
    private void loadLessThan32IntoYMMUnordered(CompilationResultBuilder crb, AMD64MacroAssembler asm, DataSection.Data maskTail,
                    Register arr, Register lengthTail, Register tmp, Register vecArray, Register vecTmp1, Register vecTmp2) {
        movdqu(asm, XMM, vecArray, new AMD64Address(arr));
        movdqu(asm, XMM, vecTmp1, new AMD64Address(arr, lengthTail, scale, -XMM.getBytes()));
        asm.leaq(tmp, (AMD64Address) crb.recordDataSectionReference(maskTail));
        pandU(asm, avxSize, vecTmp1, new AMD64Address(tmp, lengthTail, scale), vecTmp2);
        AMD64Assembler.VexRVMIOp.VPERM2I128.emit(asm, avxSize, vecArray, vecArray, vecTmp1, 0x02);
    }

    /**
     * Load an array of 8-15 bytes into a XMM register.
     */
    private void loadLessThan16IntoXMMOrdered(CompilationResultBuilder crb, AMD64MacroAssembler asm,
                    Register arr, Register lengthTail, Register tmp, Register vecArray, Register vecTmp1, Register vecTmp2) {
        // array is between 8 and 15 bytes long, load it into a YMM register via two QWORD loads
        asm.movdq(vecArray, new AMD64Address(arr));
        asm.movdq(vecTmp1, new AMD64Address(arr, lengthTail, scale, -8));
        asm.leaq(tmp, getMaskOnce(crb, createXMMTailShuffleMask(8), XMM.getBytes() * 2));
        asm.negq(lengthTail);
        movdqu(asm, XMM, vecTmp2, new AMD64Address(tmp, lengthTail, scale, XMM.getBytes()));
        pshufb(asm, XMM, vecTmp1, vecTmp2);
        movlhps(asm, vecArray, vecTmp1);
    }

    /**
     * Load an array of 8-15 bytes into a XMM register, with a gap in the middle.
     */
    private void loadLessThan16IntoXMMUnordered(CompilationResultBuilder crb, AMD64MacroAssembler asm, DataSection.Data maskTail,
                    Register arr, Register lengthTail, Register tmp, Register vecArray, Register vecTmp1, Register vecTmp2) {
        // array is between 8 and 15 bytes long, load it into a YMM register via two QWORD loads
        asm.movdq(vecArray, new AMD64Address(arr));
        asm.movdq(vecTmp1, new AMD64Address(arr, lengthTail, scale, -8));
        asm.leaq(tmp, (AMD64Address) crb.recordDataSectionReference(maskTail));
        pandU(asm, avxSize, vecTmp1, new AMD64Address(tmp, lengthTail, scale, useYMM() ? XMM.getBytes() : 0), vecTmp2);
        movlhps(asm, vecArray, vecTmp1);
    }

    /**
     * Load an array of 4-7 bytes into a XMM register.
     */
    private void loadLessThan8IntoXMMOrdered(AMD64MacroAssembler asm, Register arr, Register lengthTail, Register vecArray, Register tmp, Register tmp2) {
        assert scale.log2 < 2;
        // array is between 4 and 7 bytes long, load it into a YMM register via two DWORD loads
        asm.movl(tmp, new AMD64Address(arr));
        asm.movl(tmp2, new AMD64Address(arr, lengthTail, scale, -4));
        // compute tail length
        asm.andq(lengthTail, 3 >> scale.log2);
        // convert byte count to bit count
        asm.shlq(lengthTail, 3 + scale.log2);
        assert lengthTail.equals(rcx);
        // shift second vector to the left by tailCount bits
        asm.shlq(tmp2);
        // shift to the right and left by 32 to zero the lower 4 bytes
        asm.shrq(tmp2, 32);
        asm.shlq(tmp2, 32);
        asm.orq(tmp, tmp2);
        asm.movdq(vecArray, tmp);
    }

    /**
     * Load an array of 4-7 bytes into a XMM register, with a gap in the middle.
     */
    private void loadLessThan8IntoXMMUnordered(CompilationResultBuilder crb, AMD64MacroAssembler asm, DataSection.Data maskTail,
                    Register arr, Register lengthTail, Register vecArray, Register tmp, Register tmp2) {
        // array is between 4 and 7 bytes long, load it into a YMM register via two DWORD loads
        asm.leaq(tmp, (AMD64Address) crb.recordDataSectionReference(maskTail));
        asm.movl(tmp2, new AMD64Address(arr, lengthTail, scale, -4));
        asm.andq(tmp2, new AMD64Address(tmp, lengthTail, scale, (useYMM() ? XMM.getBytes() : 0) + 8));
        asm.movl(tmp, new AMD64Address(arr));
        asm.shlq(tmp2, 32);
        asm.orq(tmp, tmp2);
        asm.movdq(vecArray, tmp);
    }

    /**
     * Get PSHUFB lookup table, extended to 32 bytes if vector size is YMM.
     */
    private byte[] getStaticLUT(byte[] table) {
        assert table.length == XMM.getBytes();
        if (useYMM()) {
            byte[] ret = Arrays.copyOf(table, table.length * 2);
            System.arraycopy(table, 0, ret, table.length, table.length);
            return ret;
        }
        return table;
    }

    /**
     * Perform a PSUBUSB with an address directly from the data section.
     */
    private static void psubusbData(CompilationResultBuilder crb, AMD64MacroAssembler asm, AVXKind.AVXSize size, Register dst, Register src1, DataSection.Data src2, Register tmp) {
        if (isAVX(asm)) {
            AMD64Assembler.VexRVMOp.VPSUBUSB.emit(asm, size, dst, src1, (AMD64Address) crb.recordDataSectionReference(src2));
        } else {
            // SSE
            if (!dst.equals(src1)) {
                asm.movdqu(dst, src1);
            }
            asm.movdqu(tmp, (AMD64Address) crb.recordDataSectionReference(src2));
            asm.psubusb(dst, tmp);
        }
    }

    private static void pandData(CompilationResultBuilder crb, AMD64MacroAssembler asm, AVXKind.AVXSize avxSize, Register vecDst, DataSection.Data mask, Register vecTmp) {
        if (isAVX(asm)) {
            pand(asm, avxSize, vecDst, (AMD64Address) crb.recordDataSectionReference(mask));
        } else {
            asm.movdqu(vecTmp, (AMD64Address) crb.recordDataSectionReference(mask));
            pand(asm, avxSize, vecDst, vecTmp);
        }
    }

    /**
     * Shifts vector {@code cur} to the left by {@code n} bytes, and replaces the first {@code n}
     * bytes with the last {@code n} bytes of vector {@code prev}, yielding the result in vector
     * {@code dst}.
     */
    private static void prev(AMD64MacroAssembler asm, AVXKind.AVXSize size, Register dst, Register cur, Register prev, int n) {
        if (size == YMM) {
            AMD64Assembler.VexRVMIOp.VPERM2I128.emit(asm, size, dst, prev, cur, 0x21);
            palignr(asm, size, dst, cur, dst, 16 - n);
        } else {
            palignr(asm, size, dst, cur, prev, 16 - n);
        }
    }

    private void emitUTF16(CompilationResultBuilder crb, AMD64MacroAssembler asm, Register arr, Register len, Register lengthTail, Register ret, Register vecArray) {
        assert scale.log2 == 1;
        Register vecArrayTail = asRegister(vectorTemp[1]);
        Register vecMaskAscii = asRegister(vectorTemp[2]);
        Register vecMaskLatin = asRegister(vectorTemp[3]);
        Register vecMaskSurrogate = asRegister(vectorTemp[4]);
        Register vecTmp = asRegister(vectorTemp[5]);
        Register tmp = asRegister(temp[0]);
        Register retBroken = assumeValid ? null : asRegister(temp[1]);

        Label latin1Entry = new Label();
        Label latin1Tail = new Label();
        Label bmpLoop = new Label();
        Label bmpTail = new Label();
        Label labelSurrogateEntry = new Label();
        Label labelSurrogateLoop = new Label();
        Label labelSurrogateTail = new Label();

        Label tailLessThan32 = new Label();
        Label tailLessThan16 = new Label();
        Label tailLessThan8 = new Label();
        Label tailLessThan4 = new Label();
        Label tailSingleVector = new Label();

        Label tailSingleVectorSurrogate = new Label();
        Label surrogateExactlyVectorSize = new Label();

        Label returnValidOrBroken = new Label();
        Label returnBMP = new Label();
        Label returnLatin1 = new Label();
        Label returnAscii = new Label();
        Label end = new Label();

        asm.movl(ret, len);

        if (!assumeValid) {
            asm.movl(retBroken, CR_VALID_MULTIBYTE);
        }

        loadMask(crb, asm, vecMaskAscii, 0xff80);
        loadMask(crb, asm, vecMaskSurrogate, assumeValid ? 0x36 : 0x1b);
        psllw(asm, avxSize, vecMaskLatin, vecMaskAscii, 1);
        DataSection.Data maskTail = createTailMask(crb);
        DataSection.Data xmmTailShuffleMask = assumeValid ? null : writeToDataSection(crb, createXMMTailShuffleMask(XMM.getBytes()));

        vectorLoopPrologue(asm, arr, len, lengthTail, tailLessThan32, tailLessThan16, false);

        movdqu(asm, avxSize, vecArrayTail, new AMD64Address(arr, lengthTail, scale, -avxSize.getBytes()));
        // ascii loop: check if all chars are |<| 0x80 with VPTEST mask 0xff80
        emitPTestLoop(crb, asm, arr, len, vecArray, vecMaskAscii, latin1Entry);
        emitPTestTail(asm, avxSize, arr, lengthTail, vecArray, vecMaskAscii, null, latin1Tail, returnAscii, false);

        asm.bind(latin1Entry);
        // latin1 loop: check if all chars are |<| 0x100 with VPTEST mask 0xff00
        emitPTestLoop(crb, asm, arr, len, vecArray, vecMaskLatin, bmpLoop);
        emitPTestTail(asm, avxSize, arr, lengthTail, vecArray, vecMaskLatin, latin1Tail, bmpTail, returnLatin1, false);

        // bmp loop: search for UTF-16 surrogate characters
        alignLoopHead(crb, asm);
        asm.bind(bmpLoop);
        movdqu(asm, avxSize, vecArray, new AMD64Address(arr, len, scale));
        utf16FindSurrogatesAndTest(asm, vecArray, vecArray, vecMaskSurrogate);
        asm.jccb(NotZero, assumeValid ? labelSurrogateLoop : labelSurrogateEntry);
        asm.addqAndJcc(len, vectorSize, NotZero, bmpLoop, true);

        // bmp tail
        asm.bind(bmpTail);
        utf16FindSurrogatesAndTest(asm, vecArrayTail, vecArrayTail, vecMaskSurrogate);
        asm.jcc(Zero, returnBMP);
        if (assumeValid) {
            // surrogate tail
            utf16SubtractMatchedChars(asm, ret, vecArrayTail, tmp);
            asm.jmp(returnValidOrBroken);

            // surrogate loop: surrogates have been found, calculate the codepoint length
            // (assuming the string is encoded correctly)
            alignLoopHead(crb, asm);
            asm.bind(labelSurrogateLoop);
            movdqu(asm, avxSize, vecArray, new AMD64Address(arr, len, scale));
            utf16MatchSurrogates(asm, vecArray, vecMaskSurrogate);
            utf16SubtractMatchedChars(asm, ret, vecArray, tmp);
            asm.addqAndJcc(len, vectorSize, NotZero, labelSurrogateLoop, true);

            asm.testlAndJcc(lengthTail, lengthTail, Zero, returnValidOrBroken, false);
            // surrogate tail
            asm.leaq(tmp, (AMD64Address) crb.recordDataSectionReference(maskTail));
            pandU(asm, avxSize, vecArrayTail, new AMD64Address(tmp, lengthTail, scale), vecTmp);
            utf16MatchSurrogates(asm, vecArrayTail, vecMaskSurrogate);
            utf16SubtractMatchedChars(asm, ret, vecArrayTail, tmp);
            asm.jmp(returnValidOrBroken);
        } else {
            // surrogate prologue: surrogates have been found, calculate the codepoint length
            // (no assumptions about the string made)
            asm.bind(labelSurrogateEntry);
            // corner case: if the array length is exactly one vector register, go to tail
            // variant - the vector loop would read out of bounds
            // (register "ret" contains the array length at this point)
            asm.cmplAndJcc(ret, vectorSize, Equal, surrogateExactlyVectorSize, false);
            // corner case: check if the first char is a low surrogate, and if so, set code
            // range to BROKEN
            asm.movzwl(tmp, new AMD64Address(arr, len, scale));
            asm.shrl(tmp, 10);
            asm.cmpl(tmp, 0x37);
            asm.movl(tmp, CR_BROKEN_MULTIBYTE);
            asm.cmovl(Equal, retBroken, tmp);
            // high surrogate mask: 0x1b << 1 == 0x36
            psllw(asm, avxSize, vecMaskSurrogate, 1);
            // mask 0xff80 >> 15 == 0x1
            psrlw(asm, avxSize, vecMaskAscii, 15);
            // low surrogate mask: 0x1 | 0x36 == 0x37
            por(asm, avxSize, vecMaskAscii, vecMaskSurrogate);
            // if there is no tail, we would read out of bounds in the loop, so remove the last
            // iteration if lengthTail is zero
            Label labelSurrogateCheckLoopCountZero = new Label();
            asm.testlAndJcc(lengthTail, lengthTail, NotZero, labelSurrogateCheckLoopCountZero, true);
            asm.subq(arr, avxSize.getBytes());
            asm.addq(lengthTail, vectorSize);
            asm.addq(len, vectorSize);
            asm.bind(labelSurrogateCheckLoopCountZero);
            asm.testqAndJcc(len, len, Zero, labelSurrogateTail, true);

            // surrogate loop
            alignLoopHead(crb, asm);
            asm.bind(labelSurrogateLoop);
            // load at current index
            movdqu(asm, avxSize, vecArray, new AMD64Address(arr, len, scale));
            // load at current index + 1
            movdqu(asm, avxSize, vecArrayTail, new AMD64Address(arr, len, scale, 2));
            utf16ValidateSurrogates(asm, ret, vecArray, vecArrayTail, vecMaskSurrogate, vecMaskAscii, tmp, retBroken);
            asm.addqAndJcc(len, vectorSize, NotZero, labelSurrogateLoop, true);

            // surrogate tail
            asm.bind(labelSurrogateTail);
            asm.leaq(tmp, (AMD64Address) crb.recordDataSectionReference(maskTail));
            // load at up to array end - 1
            movdqu(asm, avxSize, vecArray, new AMD64Address(arr, lengthTail, scale, -(avxSize.getBytes() + 2)));
            // load at up to array end
            movdqu(asm, avxSize, vecArrayTail, new AMD64Address(arr, lengthTail, scale, -avxSize.getBytes()));
            // remove elements overlapping with the last loop vector
            pandU(asm, avxSize, vecArray, new AMD64Address(tmp, lengthTail, scale, -2), vecTmp);
            pandU(asm, avxSize, vecArrayTail, new AMD64Address(tmp, lengthTail, scale, -2), vecTmp);
            utf16ValidateSurrogates(asm, ret, vecArray, vecArrayTail, vecMaskSurrogate, vecMaskAscii, tmp, retBroken);

            // corner case: check if last char is a high surrogate
            asm.movzwl(tmp, new AMD64Address(arr, lengthTail, scale, -2));
            asm.shrl(tmp, 10);
            asm.cmplAndJcc(tmp, 0x36, NotEqual, returnValidOrBroken, false);
            // last char is a high surrogate, return BROKEN
            asm.movl(retBroken, CR_BROKEN_MULTIBYTE);
            asm.jmp(returnValidOrBroken);
        }

        if (useYMM()) {
            // special case: array is too short for YMM, try XMM
            // no loop, because the array is guaranteed to be smaller that 2 XMM registers
            asm.bind(tailLessThan32);
            asm.cmplAndJcc(lengthTail, elementsPerVector(XMM), Less, tailLessThan16, true);
            if (assumeValid) {
                loadLessThan32IntoYMMUnordered(crb, asm, maskTail, arr, lengthTail, tmp, vecArray, vecTmp, vecArrayTail);
            } else {
                loadLessThan32IntoYMMOrdered(crb, asm, xmmTailShuffleMask, arr, lengthTail, tmp, vecArray, vecTmp, vecArrayTail);
            }
            asm.jmpb(tailSingleVector);
        }

        asm.bind(tailLessThan16);
        asm.cmplAndJcc(lengthTail, elementsPerVector(QWORD), Less, tailLessThan8, true);
        if (assumeValid) {
            loadLessThan16IntoXMMUnordered(crb, asm, maskTail, arr, lengthTail, tmp, vecArray, vecTmp, vecArrayTail);
        } else {
            loadLessThan16IntoXMMOrdered(crb, asm, arr, lengthTail, tmp, vecArray, vecTmp, vecArrayTail);
        }
        asm.jmpb(tailSingleVector);

        asm.bind(tailLessThan8);
        asm.cmplAndJcc(lengthTail, elementsPerVector(DWORD), Less, tailLessThan4, true);
        if (assumeValid) {
            loadLessThan8IntoXMMUnordered(crb, asm, maskTail, arr, lengthTail, vecArray, tmp, len);
        } else {
            loadLessThan8IntoXMMOrdered(asm, arr, lengthTail, vecArray, tmp, len);
        }
        asm.jmpb(tailSingleVector);

        asm.bind(tailLessThan4);
        asm.testlAndJcc(lengthTail, lengthTail, Zero, returnAscii, false);
        asm.movzwq(tmp, new AMD64Address(arr));
        asm.movdq(vecArray, tmp);

        asm.bind(tailSingleVector);
        ptest(asm, avxSize, vecArray, vecMaskAscii);
        asm.jcc(Zero, returnAscii);
        ptest(asm, avxSize, vecArray, vecMaskLatin);
        asm.jcc(Zero, returnLatin1);
        utf16FindSurrogatesAndTest(asm, vecTmp, vecArray, vecMaskSurrogate);
        asm.jcc(Zero, returnBMP);

        if (assumeValid) {
            // surrogate head: surrogates have been found, calculate the codepoint length
            // (assuming the string is encoded correctly)
            utf16SubtractMatchedChars(asm, ret, vecTmp, tmp);
            asm.jmp(returnValidOrBroken);
        } else {
            asm.jmpb(tailSingleVectorSurrogate);
            // surrogate prologue: surrogates have been found, calculate the codepoint length
            // (no assumptions about the string made)

            // special handling for arrays that are exactly one vector register in length
            asm.bind(surrogateExactlyVectorSize);
            movdqu(asm, avxSize, vecArray, new AMD64Address(arr, -avxSize.getBytes()));
            // corner case: check if the last char is a high surrogate, and if so, set code
            // range to BROKEN
            asm.movzwl(tmp, new AMD64Address(arr, -2));
            asm.shrl(tmp, 10);
            asm.cmplAndJcc(tmp, 0x36, NotEqual, tailSingleVectorSurrogate, true);
            asm.movl(retBroken, CR_BROKEN_MULTIBYTE);

            asm.bind(tailSingleVectorSurrogate);
            // high surrogate mask: 0x1b << 1 == 0x36
            psllw(asm, avxSize, vecMaskSurrogate, 1);
            // mask 0xff80 >> 15 == 0x1
            psrlw(asm, avxSize, vecMaskAscii, 15);
            // low surrogate mask: 0x1 | 0x36 == 0x37
            por(asm, avxSize, vecMaskAscii, vecMaskSurrogate);

            // generate 2nd vector by left-shifting the entire vector by 2 bytes
            pxor(asm, avxSize, vecTmp, vecTmp);
            prev(asm, avxSize, vecArrayTail, vecArray, vecTmp, 2);

            utf16ValidateSurrogates(asm, ret, vecArrayTail, vecArray, vecMaskSurrogate, vecMaskAscii, tmp, retBroken);
            asm.jmpb(returnValidOrBroken);
        }
        emitExitMultiByte(asm, ret, returnAscii, end, CR_7BIT);
        emitExitMultiByte(asm, ret, returnLatin1, end, CR_8BIT);
        emitExitMultiByte(asm, ret, returnBMP, end, CR_16BIT);

        asm.bind(returnValidOrBroken);
        asm.shlq(ret, 32);
        if (!assumeValid) {
            asm.orq(ret, retBroken);
        } else {
            asm.orq(ret, CR_VALID_MULTIBYTE);
        }
        asm.bind(end);
    }

    private void utf16ValidateSurrogates(AMD64MacroAssembler asm,
                    Register ret, Register vecArray0, Register vecArray1, Register vecMaskHiSurrogate, Register vecMaskLoSurrogate, Register tmp, Register retBroken) {
        Label blockIsValid = new Label();
        utf16MatchSurrogates(asm, vecArray0, vecMaskHiSurrogate);
        utf16MatchSurrogates(asm, vecArray1, vecMaskLoSurrogate);
        pmovmsk(asm, avxSize, tmp, vecArray0);
        // XOR the high surrogates identified at current index, with low surrogates
        // identified at current index + 1; if the result is zero, the current block is
        // encoded correctly
        pxor(asm, avxSize, vecArray0, vecArray1);
        // count the number of high surrogates
        asm.popcntl(tmp, tmp);
        asm.shrl(tmp, 1);
        // subtract the number of high surrogates from the total char length
        asm.subq(ret, tmp);
        // continue if block is valid
        ptest(asm, avxSize, vecArray0, vecArray0);
        asm.jccb(Zero, blockIsValid);
        // block is invalid, find all lone high surrogates
        pandn(asm, avxSize, vecArray1, vecArray0);
        // count them
        pmovmsk(asm, avxSize, tmp, vecArray1);
        asm.popcntl(tmp, tmp);
        asm.shrl(tmp, 1);
        // and add them to the total char length
        asm.addq(ret, tmp);
        // set code range to BROKEN
        asm.movl(retBroken, CR_BROKEN_MULTIBYTE);
        asm.bind(blockIsValid);
    }

    private void utf16MatchSurrogates(AMD64MacroAssembler asm, Register vecArray, Register vecMaskSurrogate) {
        // identify only high or low surrogates
        psrlw(asm, avxSize, vecArray, 10);
        pcmpeqw(asm, avxSize, vecArray, vecMaskSurrogate);
    }

    private void utf16FindSurrogatesAndTest(AMD64MacroAssembler asm, Register vecDst, Register vecArray, Register vecMaskSurrogate) {
        // identify surrogates by checking the upper 6 or 5 bits
        psrlw(asm, avxSize, vecDst, vecArray, assumeValid ? 10 : 11);
        pcmpeqw(asm, avxSize, vecDst, vecMaskSurrogate);
        ptest(asm, avxSize, vecDst, vecDst);
    }

    private void utf16SubtractMatchedChars(AMD64MacroAssembler asm, Register ret, Register vecArrayTail, Register tmp) {
        pmovmsk(asm, avxSize, tmp, vecArrayTail);
        asm.popcntl(tmp, tmp);
        asm.shrl(tmp, 1);
        asm.subq(ret, tmp);
    }

    private void emitUTF32(CompilationResultBuilder crb, AMD64MacroAssembler asm, Register arr, Register len, Register lengthTail, Register ret, Register vecArray) {
        assert scale.log2 == 2;
        Register vecMaskAscii = asRegister(vectorTemp[1]);
        Register vecMaskLatin1 = asRegister(vectorTemp[2]);
        Register vecMaskBMP = asRegister(vectorTemp[3]);
        Register vecMaskSurrogate = asRegister(vectorTemp[4]);
        Register vecMaskOutOfRange = asRegister(vectorTemp[5]);
        Register vecArrayTail = asRegister(vectorTemp[6]);
        Register vecArrayTmp = asRegister(vectorTemp[7]);

        Label labelLatin1Entry = new Label();
        Label labelLatin1Tail = new Label();
        Label labelBMPEntry = new Label();
        Label labelBMPLoop = new Label();
        Label labelBMPTail = new Label();
        Label labelAstralLoop = new Label();
        Label labelAstralTail = new Label();

        Label tailLessThan32 = new Label();
        Label tailLessThan16 = new Label();
        Label tailLessThan8 = new Label();
        Label tailSingleVector = new Label();

        Label returnBroken = new Label();
        Label returnAstral = new Label();
        Label returnBMP = new Label();
        Label returnLatin1 = new Label();
        Label returnAscii = new Label();
        Label end = new Label();

        loadMask(crb, asm, vecMaskAscii, 0xffffff80);
        loadMask(crb, asm, vecMaskSurrogate, 0x1b);
        loadMask(crb, asm, vecMaskOutOfRange, 0x10);
        // generate latin1 mask 0xffffff00
        pslld(asm, avxSize, vecMaskLatin1, vecMaskAscii, 1);
        // generate bmp mask 0xffff0000
        pslld(asm, avxSize, vecMaskBMP, vecMaskAscii, 9);

        vectorLoopPrologue(asm, arr, len, lengthTail, tailLessThan32, tailLessThan16, false);

        movdqu(asm, avxSize, vecArrayTail, new AMD64Address(arr, lengthTail, scale, -avxSize.getBytes()));

        // ascii loop: check if all codepoints are |<| 0x80 with VPTEST mask 0xffffff80
        emitPTestLoop(crb, asm, arr, len, vecArray, vecMaskAscii, labelLatin1Entry);
        emitPTestTail(asm, avxSize, arr, lengthTail, vecArray, vecMaskAscii, null, labelLatin1Tail, returnAscii, false);

        asm.bind(labelLatin1Entry);
        // latin1 loop: check if all codepoints are |<| 0x100 with VPTEST mask 0xffffff00
        emitPTestLoop(crb, asm, arr, len, vecArray, vecMaskLatin1, labelBMPEntry);
        emitPTestTail(asm, avxSize, arr, lengthTail, vecArray, vecMaskLatin1, labelLatin1Tail, labelBMPTail, returnLatin1, false);

        asm.bind(labelBMPEntry);
        // bmp loop: check if all codepoints are |<| 0x10000 with VPTEST mask 0xffff0000
        alignLoopHead(crb, asm);
        asm.bind(labelBMPLoop);
        movdqu(asm, avxSize, vecArray, new AMD64Address(arr, len, scale));
        ptest(asm, avxSize, vecArray, vecMaskBMP);
        asm.jccb(NotZero, labelAstralLoop);
        // check if any codepoints are in the forbidden UTF-16 surrogate range; if so, break
        // immediately and return BROKEN
        utf32CheckInvalid(asm, vecArray, vecArray, vecArrayTmp, vecMaskSurrogate, vecMaskOutOfRange, returnBroken, true);
        asm.addqAndJcc(len, vectorSize, NotZero, labelBMPLoop, true);

        // bmp tail
        asm.bind(labelBMPTail);
        ptest(asm, avxSize, vecArrayTail, vecMaskBMP);
        asm.jccb(NotZero, labelAstralTail);
        utf32CheckInvalid(asm, vecArrayTail, vecArrayTail, vecArrayTmp, vecMaskSurrogate, vecMaskOutOfRange, returnBroken, true);
        asm.jmpb(returnBMP);

        emitExit(asm, ret, returnBroken, end, CR_BROKEN_FIXED_WIDTH, false);
        emitExit(asm, ret, returnBMP, end, CR_16BIT, false);

        // astral loop: check if any codepoints are in the forbidden UTF-16 surrogate range;
        // if so, break immediately and return BROKEN
        alignLoopHead(crb, asm);
        asm.bind(labelAstralLoop);
        movdqu(asm, avxSize, vecArray, new AMD64Address(arr, len, scale));
        utf32CheckInvalid(asm, vecArray, vecArray, vecArrayTmp, vecMaskSurrogate, vecMaskOutOfRange, returnBroken, true);
        asm.addqAndJcc(len, vectorSize, NotZero, labelAstralLoop, true);

        // astral tail
        asm.bind(labelAstralTail);
        utf32CheckInvalid(asm, vecArrayTail, vecArrayTail, vecArrayTmp, vecMaskSurrogate, vecMaskOutOfRange, returnBroken, true);
        asm.jmp(returnAstral);

        if (useYMM()) {
            // special case: array is too short for YMM, try XMM
            // no loop, because the array is guaranteed to be smaller that 2 XMM registers
            asm.bind(tailLessThan32);
            asm.cmplAndJcc(lengthTail, elementsPerVector(XMM), Less, tailLessThan16, true);

            movdqu(asm, XMM, vecArray, new AMD64Address(arr));
            movdqu(asm, XMM, vecArrayTail, new AMD64Address(arr, lengthTail, scale, -XMM.getBytes()));
            AMD64Assembler.VexRVMIOp.VPERM2I128.emit(asm, avxSize, vecArray, vecArray, vecArrayTail, 0x02);
            asm.jmpb(tailSingleVector);
        }

        asm.bind(tailLessThan16);
        asm.cmplAndJcc(lengthTail, elementsPerVector(QWORD), Less, tailLessThan8, true);
        // array is between 8 and 12 bytes long, load it into a vector register via two QWORD loads
        asm.movdq(vecArray, new AMD64Address(arr));
        asm.movdq(vecArrayTail, new AMD64Address(arr, lengthTail, scale, -QWORD.getBytes()));
        movlhps(asm, vecArray, vecArrayTail);
        asm.jmpb(tailSingleVector);

        asm.bind(tailLessThan8);
        asm.testlAndJcc(lengthTail, lengthTail, Zero, returnAscii, true);
        // array is 4 bytes long, load it into a vector register via VMOVDL
        movdl(asm, vecArray, new AMD64Address(arr));

        asm.bind(tailSingleVector);
        ptest(asm, avxSize, vecArray, vecMaskAscii);
        asm.jccb(Zero, returnAscii);
        ptest(asm, avxSize, vecArray, vecMaskLatin1);
        asm.jccb(Zero, returnLatin1);
        utf32CheckInvalid(asm, vecArrayTail, vecArray, vecArrayTmp, vecMaskSurrogate, vecMaskOutOfRange, returnBroken, false);
        ptest(asm, avxSize, vecArray, vecMaskBMP);
        asm.jcc(Zero, returnBMP);

        emitExit(asm, ret, returnAstral, end, CR_VALID_FIXED_WIDTH);

        emitExit(asm, ret, returnLatin1, end, CR_8BIT);
        emitExitAtEnd(asm, ret, returnAscii, end, CR_7BIT);
    }

    private void utf32CheckInvalid(AMD64MacroAssembler asm, Register vecArrayDst, Register vecArraySrc, Register vecArrayTmp, Register vecMaskBroken, Register vecMaskOutOfRange, Label returnBroken,
                    boolean isShortJmp) {
        // right-shift string contents by 16
        psrld(asm, avxSize, vecArrayTmp, vecArraySrc, 16);
        // right-shift string contents (copy) by 11
        psrld(asm, avxSize, vecArrayDst, vecArraySrc, 11);
        // identify codepoints larger than 0x10ffff ((codepoint >> 16) > 0x10)
        pcmpgtd(asm, avxSize, vecArrayTmp, vecMaskOutOfRange);
        // identify codepoints in the forbidden UTF-16 surrogate range [0xd800-0xdfff]
        // ((codepoint >> 11) == 0x1b)
        pcmpeqd(asm, avxSize, vecArrayDst, vecMaskBroken);
        // merge comparison results
        por(asm, avxSize, vecArrayDst, vecArrayTmp);
        // check if any invalid character was present
        ptest(asm, avxSize, vecArrayDst, vecArrayDst);
        // return CR_BROKEN if any invalid character was present
        asm.jcc(NotZero, returnBroken, isShortJmp);
    }

    @Override
    public boolean needsClearUpperVectorRegisters() {
        return true;
    }

    private boolean useYMM() {
        return avxSize == YMM;
    }

    private static void alignLoopHead(CompilationResultBuilder crb, AMD64MacroAssembler asm) {
        asm.align(crb.target.wordSize * 2);
    }

    private void loadMask(CompilationResultBuilder crb, AMD64MacroAssembler asm, Register vecMask, int value) {
        movdqu(asm, avxSize, vecMask, getMaskOnce(crb, createMaskBytes(value)));
    }

    private static AMD64Address getMaskOnce(CompilationResultBuilder crb, byte[] mask) {
        return getMaskOnce(crb, mask, mask.length);
    }

    private static AMD64Address getMaskOnce(CompilationResultBuilder crb, byte[] mask, int alignLength) {
        int align = crb.dataBuilder.ensureValidDataAlignment(alignLength);
        return (AMD64Address) crb.recordDataReferenceInCode(mask, align);
    }

    private DataSection.Data createMask(CompilationResultBuilder crb, int value) {
        return writeToDataSection(crb, createMaskBytes(value));
    }

    /**
     * Creates the following mask in the data section: {@code [ 0x00 <n times> 0xff <n times> ]},
     * where {@code n} is the AVX vector size in bytes.
     *
     * With this mask, bytes loaded by a vector load aligned to the end of the array can be set to
     * zero with a PAND instruction, e.g.:
     *
     * Given an array of 20 bytes, and XMM vectors of 16 bytes, we can load bytes 0-15 with a MOVDQU
     * instruction aligned to the beginning of the array, and bytes 4-19 with another MOVDQU
     * instruction aligned to the end of the array, using the address (arrayBasePointer,
     * arrayLength, -16). To avoid processing bytes 4-15 twice, we can zero them in the second
     * vector with this mask and the tail count {@code 20 % 16 = 4}:
     *
     * {@code PAND vector2, (maskBasePointer, tailCount)}
     *
     * {@code (maskBasePointer, tailCount)} yields a mask where all lower bytes are {@code 0x00},
     * and exactly the last {@code tailCount} bytes are {@code 0xff}, in this case:
     *
     * {@code [0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0xff 0xff 0xff 0xff] }
     */
    private DataSection.Data createTailMask(CompilationResultBuilder crb) {
        byte[] mask = new byte[avxSize.getBytes() * 2];
        for (int i = vectorSize; i < vectorSize * 2; i++) {
            writeValue(mask, scale, i, ~0);
        }
        return writeToDataSection(crb, mask);
    }

    /**
     * Creates the following mask: {@code [0x00 0x01 0x02 ... 0x0f 0xff <n times>]}, where {@code n}
     * is the AVX vector size in bytes.
     *
     * This mask can be used with PSHUFB to not only remove duplicate bytes in a vector tail load
     * (see {@link #createTailMask(CompilationResultBuilder)}, but also move the remaining bytes to
     * the beginning of the vector, as if the vector was right-shifted by {@code 16 - tailCount}
     * bytes.
     *
     * This only works on XMM vectors; to achieve the same on a YMM vector additional instructions
     * are needed.
     */
    private static byte[] createXMMTailShuffleMask(int length) {
        byte[] mask = new byte[XMM.getBytes() + length];
        for (int i = 0; i < length; i++) {
            mask[i] = (byte) i;
        }
        Arrays.fill(mask, length, XMM.getBytes() + length, (byte) ~0);
        return mask;
    }

    private byte[] createMaskBytes(int value) {
        byte[] mask = new byte[avxSize.getBytes()];
        for (int i = 0; i < vectorSize; i++) {
            writeValue(mask, scale, i, value);
        }
        return mask;
    }

    private static DataSection.Data writeToDataSection(CompilationResultBuilder crb, byte[] array) {
        int align = crb.dataBuilder.ensureValidDataAlignment(array.length);
        ArrayDataPointerConstant arrayConstant = new ArrayDataPointerConstant(array, align);
        return crb.dataBuilder.createSerializableData(arrayConstant, align);
    }

    private static void emitExit(AMD64MacroAssembler asm, Register ret, Label entry, Label labelDone, int returnValue) {
        emitExit(asm, ret, entry, labelDone, returnValue, true);
    }

    private static void emitExit(AMD64MacroAssembler asm, Register ret, Label entry, Label labelDone, int returnValue, boolean isShortJmp) {
        asm.bind(entry);
        if (returnValue == 0) {
            asm.xorq(ret, ret);
        } else {
            asm.movl(ret, returnValue);
        }
        asm.jmp(labelDone, isShortJmp);
    }

    private static void emitExitAtEnd(AMD64MacroAssembler asm, Register ret, Label entry, Label end, int returnValue) {
        asm.bind(entry);
        if (returnValue == 0) {
            asm.xorq(ret, ret);
        } else {
            asm.movl(ret, returnValue);
        }
        asm.bind(end);
    }

    private static void emitExitMultiByte(AMD64MacroAssembler asm, Register ret, Label entry, Label end, int returnValue) {
        asm.bind(entry);
        asm.shlq(ret, 32);
        asm.orq(ret, returnValue);
        asm.jmpb(end);
    }

    private static void emitExitMultiByteAtEnd(AMD64MacroAssembler asm, Register ret, Label entry, Label end, int returnValue) {
        asm.bind(entry);
        asm.shlq(ret, 32);
        asm.orq(ret, returnValue);
        asm.bind(end);
    }

    private static void writeValue(byte[] array, Scale stride, int index, int value) {
        int i = index << stride.log2;
        if (stride == Scale.Times1) {
            array[i] = (byte) value;
            return;
        }
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            if (stride == Scale.Times2) {
                array[i] = (byte) value;
                array[i + 1] = (byte) (value >> 8);
            } else {
                array[i] = (byte) value;
                array[i + 1] = (byte) (value >> 8);
                array[i + 2] = (byte) (value >> 16);
                array[i + 3] = (byte) (value >> 24);
            }
        } else {
            if (stride == Scale.Times2) {
                array[i] = (byte) (value >> 8);
                array[i + 1] = (byte) value;
            } else {
                array[i] = (byte) (value >> 24);
                array[i + 1] = (byte) (value >> 16);
                array[i + 2] = (byte) (value >> 8);
                array[i + 3] = (byte) value;
            }
        }
    }
}
