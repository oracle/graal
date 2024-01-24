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
package jdk.graal.compiler.lir.amd64;

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.Equal;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.Less;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.NotEqual;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.NotZero;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.Zero;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.DWORD;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.QWORD;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.XMM;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.YMM;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import java.util.Arrays;
import java.util.EnumSet;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.code.DataSection;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool.CalcStringAttributesEncoding;
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
public final class AMD64CalcStringAttributesOp extends AMD64ComplexVectorOp {
    public static final LIRInstructionClass<AMD64CalcStringAttributesOp> TYPE = LIRInstructionClass.create(AMD64CalcStringAttributesOp.class);

    private static final Register REG_ARRAY = rsi;
    private static final Register REG_OFFSET = rcx;
    private static final Register REG_LENGTH = rdx;

    // NOTE:
    // The following fields must be kept in sync with com.oracle.truffle.api.strings.TSCodeRange,
    // TStringOpsCalcStringAttributesReturnValuesInSyncTest verifies this.

    private final CalcStringAttributesEncoding encoding;

    private final Stride stride;
    private final int vectorLength;
    /**
     * If true, assume the string to be encoded correctly.
     */
    private final boolean assumeValid;

    @Def({OperandFlag.REG}) private Value result;
    @Use({OperandFlag.REG}) private Value array;
    @Use({OperandFlag.REG}) private Value offset;
    @Use({OperandFlag.REG}) private Value length;

    @Temp({OperandFlag.REG}) private Value arrayTmp;
    @Temp({OperandFlag.REG}) private Value offsetTmp;
    @Temp({OperandFlag.REG}) private Value lengthTmp;

    @Temp({OperandFlag.REG}) private Value[] temp;
    @Temp({OperandFlag.REG}) private Value[] vectorTemp;

    private AMD64CalcStringAttributesOp(LIRGeneratorTool tool, CalcStringAttributesEncoding encoding, EnumSet<CPUFeature> runtimeCheckedCPUFeatures, Value array, Value offset,
                    Value length, Value result, boolean assumeValid) {
        super(TYPE, tool, runtimeCheckedCPUFeatures, YMM);
        this.encoding = encoding;
        this.assumeValid = assumeValid;

        GraalError.guarantee(supports(tool.target(), runtimeCheckedCPUFeatures, CPUFeature.SSE4_1), "needs at least SSE4.1 support");

        this.stride = encoding.stride;
        this.vectorLength = vectorSize.getBytes() / encoding.stride.value;

        this.arrayTmp = this.array = array;
        this.offsetTmp = this.offset = offset;
        this.lengthTmp = this.length = length;
        this.result = result;

        this.temp = new Value[getNumberOfTempRegisters(encoding, assumeValid)];
        for (int i = 0; i < temp.length; i++) {
            temp[i] = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
        }
        this.vectorTemp = new Value[getNumberOfRequiredVectorRegisters(encoding, supports(tool.target(), runtimeCheckedCPUFeatures, CPUFeature.AVX), assumeValid)];
        for (int i = 0; i < vectorTemp.length; i++) {
            vectorTemp[i] = tool.newVariable(LIRKind.value(getVectorKind(JavaKind.Byte)));
        }
    }

    private static int getNumberOfTempRegisters(CalcStringAttributesEncoding encoding, boolean assumeValid) {
        switch (encoding) {
            case UTF_8:
                return assumeValid ? 1 : 3;
            case UTF_16:
                return assumeValid ? 1 : 2;
            default:
                return 0;
        }
    }

    private static int getNumberOfRequiredVectorRegisters(CalcStringAttributesEncoding encoding, boolean isAVX, boolean assumeValid) {
        switch (encoding) {
            case LATIN1:
                return isAVX ? 1 : 2;
            case BMP:
                return isAVX ? 2 : 3;
            case UTF_8:
                return assumeValid ? 5 : 10;
            case UTF_16:
                return 7;
            case UTF_32:
                return 8;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(encoding); // ExcludeFromJacocoGeneratedReport
        }
    }

    private int elementsPerVector(AVXSize size) {
        return size.getBytes() / encoding.stride.value;
    }

    private int elementsPerVector(AMD64BaseAssembler.OperandSize size) {
        return size.getBytes() / encoding.stride.value;
    }

    /**
     * Calculates the code range and codepoint length of strings in various encodings.
     *
     * @param encoding operation to run.
     * @param runtimeCheckedCPUFeatures
     * @param array arbitrary array.
     * @param byteOffset byteOffset to start from. Must include array base byteOffset!
     * @param length length of the array region to consider, scaled to
     *            {@link CalcStringAttributesEncoding#stride}.
     * @param assumeValid assume that the string is encoded correctly.
     */
    public static AMD64CalcStringAttributesOp movParamsAndCreate(LIRGeneratorTool tool, CalcStringAttributesEncoding encoding, EnumSet<CPUFeature> runtimeCheckedCPUFeatures,
                    Value array, Value byteOffset,
                    Value length, Value result,
                    boolean assumeValid) {
        RegisterValue regArray = REG_ARRAY.asValue(array.getValueKind());
        RegisterValue regOffset = REG_OFFSET.asValue(byteOffset.getValueKind());
        RegisterValue regLength = REG_LENGTH.asValue(length.getValueKind());
        tool.emitConvertNullToZero(regArray, array);
        tool.emitMove(regOffset, byteOffset);
        tool.emitMove(regLength, length);
        return new AMD64CalcStringAttributesOp(tool, encoding, runtimeCheckedCPUFeatures, regArray, regOffset, regLength, result, assumeValid);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler asm) {
        Register arr = asRegister(array);
        Register off = asRegister(offset);
        Register len = asRegister(length);
        Register ret = asRegister(result);

        Register vec1 = asRegister(vectorTemp[0]);

        asm.leaq(arr, new AMD64Address(arr, off, Stride.S1));

        asm.movl(off, len);

        switch (encoding) {
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
                throw GraalError.shouldNotReachHereUnexpectedValue(encoding); // ExcludeFromJacocoGeneratedReport
        }
    }

    private void emitLatin1(CompilationResultBuilder crb, AMD64MacroAssembler asm, Register arr, Register len, Register lengthTail, Register ret, Register vecMask) {
        assert stride.log2 == 0 : stride.log2;
        Label tailLessThan32 = new Label();
        Label tailLessThan16 = new Label();
        Label tailLessThan8 = new Label();
        Label tailLessThan4 = new Label();
        Label tailLessThan2 = new Label();

        Label returnLatin1 = new Label();
        Label returnAscii = new Label();
        Label end = new Label();

        Register vecArray = asm.isAVX() ? null : asRegister(vectorTemp[1]);

        // load ascii PTEST mask consisting of 0x80 bytes
        DataSection.Data mask = createMask(crb, stride, 0x80);
        asm.movdqu(vectorSize, vecMask, (AMD64Address) crb.recordDataSectionReference(mask));

        vectorLoopPrologue(asm, arr, len, lengthTail, tailLessThan32, tailLessThan16, true);
        // main loop: check if all bytes are ascii with PTEST mask 0x80
        emitPTestLoop(crb, asm, arr, len, vecArray, vecMask, returnLatin1);
        emitPTestTail(asm, vectorSize, arr, lengthTail, vecArray, vecMask, null, returnLatin1, returnAscii, false);

        if (supportsAVX2AndYMM()) {
            // tail for 16 - 31 bytes
            asm.bind(tailLessThan32);
            asm.cmplAndJcc(lengthTail, elementsPerVector(XMM), Less, tailLessThan16, true);
            emitPTestCurr(asm, XMM, arr, vecArray, vecMask, null, returnLatin1);
            emitPTestTail(asm, XMM, arr, lengthTail, vecArray, vecMask, null, returnLatin1, returnAscii);
        }

        emitExit(asm, ret, returnLatin1, end, CalcStringAttributesEncoding.CR_8BIT);

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

        emitExitAtEnd(asm, ret, returnAscii, end, CalcStringAttributesEncoding.CR_7BIT);
    }

    private void latin1Tail(AMD64MacroAssembler asm, AMD64BaseAssembler.OperandSize size,
                    Register arr, Register lengthTail, Register mask,
                    Label entry, Label tooSmall, Label labelLatin1, Label labelAscii) {
        bind(asm, entry);
        asm.cmplAndJcc(lengthTail, elementsPerVector(size), Less, tooSmall, true);
        asm.testAndJcc(size, mask, new AMD64Address(arr), NotZero, labelLatin1, true);
        asm.testAndJcc(size, mask, new AMD64Address(arr, lengthTail, stride, -size.getBytes()), NotZero, labelLatin1, true);
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
        asm.andl(lengthTail, vectorLength - 1);
        asm.andlAndJcc(len, -vectorLength, Zero, supportsAVX2AndYMM() ? tailLessThan32 : tailLessThan16, isShortJump);

        asm.leaq(arr, new AMD64Address(arr, len, stride));
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
        asm.align(preferredLoopAlignment(crb));
        asm.bind(loopHead);
        asm.ptestU(vectorSize, vecMask, new AMD64Address(arr, len, stride), vecArray);
        asm.jccb(NotZero, labelBreak);
        asm.addqAndJcc(len, vectorLength, NotZero, loopHead, true);
    }

    /**
     * PTEST the array at address {@code arr} with mask {@code vecMask}. If the result is nonzero,
     * jump to label {@code match}.
     */
    private static void emitPTestCurr(AMD64MacroAssembler asm, AVXKind.AVXSize size, Register arr, Register vecArray, Register vecMask, Label entry, Label match) {
        bind(asm, entry);
        asm.ptestU(size, vecMask, new AMD64Address(arr), vecArray);
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
        asm.ptestU(size, vecMask, new AMD64Address(arr, lengthTail, stride, -size.getBytes()), vecArray);
        asm.jccb(NotZero, match);
        asm.jmp(noMatch, isShortJmp);
    }

    private static void bind(AMD64MacroAssembler asm, Label entry) {
        if (entry != null) {
            asm.bind(entry);
        }
    }

    private void emitBMP(CompilationResultBuilder crb, AMD64MacroAssembler asm, Register arr, Register len, Register lengthTail, Register ret, Register vecMaskAscii) {
        assert stride.log2 == 1 : stride.log2;
        Register vecMaskBMP = asRegister(vectorTemp[1]);
        Register vecArray = asm.isAVX() ? null : asRegister(vectorTemp[2]);
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
        loadMask(crb, asm, stride, vecMaskAscii, 0xff80);
        // create bmp mask 0xff00
        asm.psllw(vectorSize, vecMaskBMP, vecMaskAscii, 1);

        vectorLoopPrologue(asm, arr, len, lengthTail, tailLessThan32, tailLessThan16, true);

        // ascii loop: check if all chars are |<| 0x80 with PTEST mask 0xff80
        emitPTestLoop(crb, asm, arr, len, vecArray, vecMaskAscii, latin1Entry);
        emitPTestTail(asm, vectorSize, arr, lengthTail, vecArray, vecMaskAscii, null, latin1TailCmp, returnAscii);

        asm.bind(latin1Entry);
        // latin1 loop: check if all chars are |<| 0x100 with PTEST mask 0xff00
        emitPTestLoop(crb, asm, arr, len, vecArray, vecMaskBMP, returnBMP);
        emitPTestTail(asm, vectorSize, arr, lengthTail, vecArray, vecMaskBMP, latin1TailCmp, returnBMP, returnLatin1);

        if (supportsAVX2AndYMM()) {
            // tail for 16 - 31 bytes
            bmpTail(asm, arr, lengthTail, vecArray, vecMaskAscii, vecMaskBMP, tailLessThan32, tailLessThan16, returnBMP, returnLatin1, returnAscii);
        }

        emitExit(asm, ret, returnAscii, end, CalcStringAttributesEncoding.CR_7BIT);
        emitExit(asm, ret, returnLatin1, end, CalcStringAttributesEncoding.CR_8BIT);
        emitExit(asm, ret, returnBMP, end, CalcStringAttributesEncoding.CR_16BIT);

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
        asm.testAndJcc(size, mask, new AMD64Address(arr, lengthTail, stride, -size.getBytes()), NotZero, match, true);
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
        assert stride.log2 == 0 : stride.log2;
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

        loadMask(crb, asm, stride, vecMask, 0x80);
        loadMask(crb, asm, stride, vecMaskCB, 0xc0);
        DataSection.Data validMaskTail = assumeValid ? createTailMask(crb, stride) : null;

        if (!assumeValid) {
            // initialize multibyte loop state registers
            asm.pxor(vectorSize, vecPrevArray, vecPrevArray);
            asm.pxor(vectorSize, vecError, vecError);
            asm.pxor(vectorSize, vecPrevIsIncomplete, vecPrevIsIncomplete);
        }

        vectorLoopPrologue(asm, arr, len, lengthTail, tailLessThan32, !assumeValid ? tailLessThan32 : tailLessThan16, false);

        // ascii loop: check if all bytes are |<| 0x80 with PTEST
        asm.align(preferredLoopAlignment(crb));
        asm.bind(asciiLoop);
        asm.movdqu(vectorSize, vecArray, new AMD64Address(arr, len, stride));
        asm.ptest(vectorSize, vecArray, vecMask);
        asm.jccb(NotZero, labelMultiByteEntry);
        asm.addqAndJcc(len, vectorLength, NotZero, asciiLoop, true);

        // ascii tail
        asm.movdqu(vectorSize, vecArray, new AMD64Address(arr, lengthTail, stride, -vectorSize.getBytes()));
        asm.ptest(vectorSize, vecArray, vecMask);
        asm.jcc(NotZero, labelMultiByteTail, assumeValid);
        asm.jmp(returnAscii);

        if (assumeValid) {
            // multibyte loop: at least one byte is not ascii, calculate the codepoint length
            asm.align(preferredLoopAlignment(crb));
            asm.bind(labelMultiByteLoop);
            asm.movdqu(vectorSize, vecArray, new AMD64Address(arr, len, stride));
            asm.bind(labelMultiByteEntry);
            utf8SubtractContinuationBytes(asm, ret, vecArray, tmp, vecMask, vecMaskCB);
            asm.addqAndJcc(len, vectorLength, NotZero, labelMultiByteLoop, true);

            // multibyte loop tail: do an overlapping tail load, and zero out all bytes that would
            // overlap with the last loop iteration
            asm.testlAndJcc(lengthTail, lengthTail, Zero, returnValid, false);
            // load tail vector
            asm.movdqu(vectorSize, vecArray, new AMD64Address(arr, lengthTail, stride, -vectorSize.getBytes()));
            asm.bind(labelMultiByteTail);
            // load tail mask address
            asm.leaq(tmp, (AMD64Address) crb.recordDataSectionReference(validMaskTail));
            // remove overlapping bytes
            asm.pandU(vectorSize, vecArray, new AMD64Address(tmp, lengthTail, stride), vecTmp1);
            // identify continuation bytes
            utf8SubtractContinuationBytes(asm, ret, vecArray, tmp, vecMask, vecMaskCB);
            asm.jmp(returnValid);

            if (supportsAVX2AndYMM()) {
                // special case: array is too short for YMM, try XMM
                // no loop, because the array is guaranteed to be smaller that 2 XMM registers
                asm.bind(tailLessThan32);
                asm.cmplAndJcc(lengthTail, elementsPerVector(XMM), Less, tailLessThan16, true);
                loadLessThan32IntoYMMUnordered(crb, asm, stride, validMaskTail, arr, lengthTail, tmp, vecArray, vecTmp1, vecTmp2);
                asm.jmpb(tailSingleVector);
            }
            asm.bind(tailLessThan16);
            asm.cmplAndJcc(lengthTail, elementsPerVector(QWORD), Less, tailLessThan8, true);
            loadLessThan16IntoXMMUnordered(crb, asm, stride, validMaskTail, arr, lengthTail, tmp, vecArray, vecTmp1, vecTmp2);
            asm.jmpb(tailSingleVector);

            asm.bind(tailLessThan8);
            asm.cmplAndJcc(lengthTail, elementsPerVector(DWORD), Less, labelScalarTail, true);
            loadLessThan8IntoXMMOrdered(asm, stride, arr, lengthTail, vecArray, tmp, len);

            asm.bind(tailSingleVector);
            // check if first vector is ascii
            asm.ptest(vectorSize, vecArray, vecMask);
            // not ascii, go to scalar loop
            asm.jcc(Zero, returnAscii);
            utf8SubtractContinuationBytes(asm, ret, vecArray, tmp, vecMask, vecMaskCB);
            asm.jmp(returnValid);
        } else {
            Label labelMultiByteEnd = new Label();
            Label labelMultiByteTailLoopEntry = new Label();

            byte[] isIncompleteMaskBytes = new byte[vectorSize.getBytes()];
            Arrays.fill(isIncompleteMaskBytes, (byte) ~0);
            isIncompleteMaskBytes[vectorSize.getBytes() - 3] = (byte) (0b11110000 - 1);
            isIncompleteMaskBytes[vectorSize.getBytes() - 2] = (byte) (0b11100000 - 1);
            isIncompleteMaskBytes[vectorSize.getBytes() - 1] = (byte) (0b11000000 - 1);
            DataSection.Data isIncompleteMask = writeToDataSection(crb, isIncompleteMaskBytes);
            DataSection.Data mask0x0F = createMask(crb, stride, (byte) 0x0F);
            DataSection.Data mask3ByteSeq = createMask(crb, stride, (byte) 0b11100000 - 1);
            DataSection.Data mask4ByteSeq = createMask(crb, stride, (byte) 0b11110000 - 1);
            DataSection.Data xmmTailShuffleMask = writeToDataSection(crb, createXMMTailShuffleMask(XMM.getBytes()));

            Register vecTmp3 = asRegister(vectorTemp[8]);
            Register vecTmp4 = asRegister(vectorTemp[9]);

            // multibyte loop: at least one byte is not ascii, calculate the codepoint length
            asm.align(preferredLoopAlignment(crb));
            asm.bind(labelMultiByteLoop);
            asm.movdqu(vectorSize, vecArray, new AMD64Address(arr, len, stride));
            // fast path: check if current vector is ascii
            asm.bind(labelMultiByteTailLoopEntry);
            asm.ptest(vectorSize, vecArray, vecMask);
            asm.jccb(NotZero, labelMultiByteEntry);
            // current vector is ascii, if the previous vector ended with an incomplete sequence, we
            // found an error
            asm.por(vectorSize, vecError, vecPrevIsIncomplete);
            // continue
            asm.addqAndJcc(len, vectorLength, NotZero, labelMultiByteLoop, true);
            asm.jmp(labelMultiByteTail);

            asm.bind(labelMultiByteEntry);

            // codepoint counting logic:
            // AND the string's bytes with 0xc0
            asm.pand(vectorSize, vecTmp1, vecArray, vecMaskCB);
            // check if the result is equal to 0x80, identifying continuation bytes
            asm.pcmpeqb(vectorSize, vecTmp1, vecMask);
            // count the number of continuation bytes
            asm.pmovmsk(vectorSize, tmp, vecTmp1);
            asm.popcntl(tmp, tmp);
            // subtract the number of continuation bytes from the total byte length
            asm.subl(ret, tmp);

            // load a view of the array at current index - 1 into vecTmp3
            prev(asm, vectorSize, vecTmp3, vecArray, vecPrevArray, 1);
            // load lower nibbles of prev1 into vecTmp3
            // load upper nibbles of prev1 into vecTmp4
            asm.psrlw(vectorSize, vecTmp4, vecTmp3, 4);
            pandData(crb, asm, vectorSize, vecTmp3, mask0x0F, vecTmp1);
            pandData(crb, asm, vectorSize, vecTmp4, mask0x0F, vecTmp1);
            // perform table lookup for both nibbles
            asm.movdqu(vectorSize, vecTmp1, getMaskOnce(crb, getStaticLUT(UTF8_BYTE_1_LOW_TABLE)));
            asm.movdqu(vectorSize, vecTmp2, getMaskOnce(crb, getStaticLUT(UTF8_BYTE_1_HIGH_TABLE)));
            asm.pshufb(vectorSize, vecTmp1, vecTmp3);
            asm.pshufb(vectorSize, vecTmp2, vecTmp4);
            // combine result into vecTmp1
            asm.pand(vectorSize, vecTmp1, vecTmp2);
            asm.movdqu(vectorSize, vecTmp2, getMaskOnce(crb, getStaticLUT(UTF8_BYTE_2_HIGH_TABLE)));
            // load upper nibbles of array at current index into vecTmp3
            asm.psrlw(vectorSize, vecTmp3, vecArray, 4);
            pandData(crb, asm, vectorSize, vecTmp3, mask0x0F, vecTmp4);
            // table lookup
            asm.pshufb(vectorSize, vecTmp2, vecTmp3);
            // combine result into vecTmp1
            asm.pand(vectorSize, vecTmp1, vecTmp2);

            // load a view of the array at current index - 2 into vecTmp2
            prev(asm, vectorSize, vecTmp2, vecArray, vecPrevArray, 2);
            // load a view of the array at current index - 3 into vecTmp3
            prev(asm, vectorSize, vecTmp3, vecArray, vecPrevArray, 3);
            // find all bytes greater or equal to 0b11100000 in prev2
            psubusbData(crb, asm, vectorSize, vecTmp2, vecTmp2, mask3ByteSeq, vecTmp4);
            // find all bytes greater or equal to 0b11110000 in prev3
            psubusbData(crb, asm, vectorSize, vecTmp3, vecTmp3, mask4ByteSeq, vecTmp4);
            // combine result into vecTmp2
            asm.por(vectorSize, vecTmp2, vecTmp3);
            // convert result to 0xff for all matching bytes
            asm.pxor(vectorSize, vecTmp3, vecTmp3);
            asm.pcmpgtb(vectorSize, vecTmp2, vecTmp3);
            // convert result to 0x80 for all matching bytes
            asm.pand(vectorSize, vecTmp2, vecMask);
            // xor the result with the combined table lookup results
            asm.pxor(vectorSize, vecTmp1, vecTmp2);
            // if the result of all this is non-zero, we found an error
            asm.por(vectorSize, vecError, vecTmp1);

            // check if the current vector ends with an incomplete multibyte-sequence
            // (needed only for ascii fast-path)
            psubusbData(crb, asm, vectorSize, vecPrevIsIncomplete, vecArray, isIncompleteMask, vecTmp1);
            // save current vector as vecPrevArray
            asm.movdqu(vectorSize, vecPrevArray, vecArray);
            asm.addqAndJcc(len, vectorLength, NotZero, labelMultiByteLoop, false);

            asm.bind(labelMultiByteTail);
            asm.testqAndJcc(lengthTail, lengthTail, Zero, labelMultiByteEnd, true);
            loadTailIntoYMMOrdered(crb, asm, stride, xmmTailShuffleMask, arr, lengthTail, vecArray, tmp, vecTmp1, vecTmp2);
            // adjust length registers such that the loop will terminate after the following
            // iteration
            asm.xorq(lengthTail, lengthTail);
            asm.subq(len, vectorLength);
            asm.jmp(labelMultiByteTailLoopEntry);

            asm.bind(labelMultiByteEnd);
            asm.por(vectorSize, vecError, vecPrevIsIncomplete);
            asm.ptest(vectorSize, vecError, vecError);
            asm.jcc(Zero, returnValid);
            asm.shlq(ret, 32);
            asm.orq(ret, CalcStringAttributesEncoding.CR_BROKEN_MULTIBYTE);
            asm.jmp(end);

            asm.bind(tailLessThan32);
            Register tmp2 = asRegister(temp[1]);

            if (supportsAVX2AndYMM()) {
                asm.cmplAndJcc(lengthTail, 16, Less, tailLessThan16, true);
                loadLessThan32IntoYMMOrdered(crb, asm, stride, xmmTailShuffleMask, arr, lengthTail, tmp, vecArray, vecTmp1, vecTmp2);
                asm.jmp(tailSingleVector);
                asm.bind(tailLessThan16);
            }
            asm.cmplAndJcc(lengthTail, 8, Less, tailLessThan8, true);
            loadLessThan16IntoXMMOrdered(crb, asm, stride, arr, lengthTail, tmp, vecArray, vecTmp1, vecTmp2);
            asm.jmpb(tailSingleVector);

            asm.bind(tailLessThan8);
            asm.cmplAndJcc(lengthTail, 4, Less, labelScalarTail, true);
            loadLessThan8IntoXMMOrdered(asm, stride, arr, lengthTail, vecArray, tmp, tmp2);

            asm.bind(tailSingleVector);
            asm.ptest(vectorSize, vecArray, vecMask);
            asm.jcc(Zero, returnAscii);
            asm.subq(len, vectorLength);
            asm.xorq(lengthTail, lengthTail);
            asm.jmp(labelMultiByteEntry);
        }

        asm.bind(labelScalarTail);
        asm.leaq(arr, new AMD64Address(arr, lengthTail, stride));
        asm.testqAndJcc(lengthTail, lengthTail, Zero, returnAscii, false);
        asm.negq(lengthTail);

        // scalar ascii loop
        asm.align(preferredLoopAlignment(crb));
        asm.bind(labelScalarAsciiLoop);
        asm.movzbl(tmp, new AMD64Address(arr, lengthTail, stride));
        asm.testlAndJcc(tmp, 0x80, NotZero, labelScalarMultiByteLoopEntry, true);
        asm.incqAndJcc(lengthTail, NotZero, labelScalarAsciiLoop, true);
        asm.jmpb(returnAscii);

        // scalar multibyte loop
        asm.bind(labelScalarMultiByteLoopEntry);
        if (assumeValid) {
            asm.align(preferredLoopAlignment(crb));
            asm.bind(labelScalarMultiByteLoop);
            asm.movzbq(tmp, new AMD64Address(arr, lengthTail, stride));
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
            asm.align(preferredLoopAlignment(crb));
            asm.bind(labelScalarMultiByteLoop);
            asm.movzbq(tmp, new AMD64Address(arr, lengthTail, stride));
            asm.movzbq(type, new AMD64Address(len, tmp, stride));
            asm.andl(tmp, 0xc0);
            asm.addq(type, state);
            asm.movzbq(state, new AMD64Address(len, type, stride, 256));
            asm.cmplAndJcc(tmp, 0x80, NotEqual, labelScalarMultiByteLoopSkipDec, true);
            asm.decl(ret);
            asm.bind(labelScalarMultiByteLoopSkipDec);
            asm.incqAndJcc(lengthTail, NotZero, labelScalarMultiByteLoop, true);

            asm.testqAndJcc(state, state, Zero, returnValid, true);
            asm.shlq(ret, 32);
            asm.orq(ret, CalcStringAttributesEncoding.CR_BROKEN_MULTIBYTE);
            asm.jmpb(end);
        }

        emitExitMultiByte(asm, ret, returnValid, end, CalcStringAttributesEncoding.CR_VALID_MULTIBYTE);
        emitExitMultiByteAtEnd(asm, ret, returnAscii, end, CalcStringAttributesEncoding.CR_7BIT);
    }

    /**
     * Find all UTF-8 continuation bytes in {@code vecArray}, count them, and subtract their count
     * from {@code ret}.
     *
     * Kills {@code tmp} and {@code vecArray}.
     */
    private void utf8SubtractContinuationBytes(AMD64MacroAssembler asm, Register ret, Register vecArray, Register tmp, Register vecMask, Register vecMaskCB) {
        // AND the string's bytes with 0xc0
        asm.pand(vectorSize, vecArray, vecMaskCB);
        // check if the result is equal to 0x80, identifying continuation bytes
        asm.pcmpeqb(vectorSize, vecArray, vecMask);
        // count the number of continuation bytes
        asm.pmovmsk(vectorSize, tmp, vecArray);
        asm.popcntl(tmp, tmp);
        // subtract the number of continuation bytes from the total byte length
        asm.subl(ret, tmp);
    }

    /**
     * Get PSHUFB lookup table, extended to 32 bytes if vector size is YMM.
     */
    private byte[] getStaticLUT(byte[] table) {
        assert table.length == XMM.getBytes() : table.length;
        if (supportsAVX2AndYMM()) {
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
        if (asm.isAVX()) {
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
        if (asm.isAVX()) {
            asm.pand(avxSize, vecDst, (AMD64Address) crb.recordDataSectionReference(mask));
        } else {
            asm.movdqu(vecTmp, (AMD64Address) crb.recordDataSectionReference(mask));
            asm.pand(avxSize, vecDst, vecTmp);
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
            asm.palignr(size, dst, cur, dst, 16 - n);
        } else {
            asm.palignr(size, dst, cur, prev, 16 - n);
        }
    }

    private void emitUTF16(CompilationResultBuilder crb, AMD64MacroAssembler asm, Register arr, Register len, Register lengthTail, Register ret, Register vecArray) {
        assert stride.log2 == 1 : stride.log2;
        Register vecArrayTail = asRegister(vectorTemp[1]);
        Register vecMaskAscii = asRegister(vectorTemp[2]);
        Register vecMaskLatin = asRegister(vectorTemp[3]);
        Register vecMaskSurrogate = asRegister(vectorTemp[4]);
        Register vecTmp = asRegister(vectorTemp[5]);
        Register vecResult = asRegister(vectorTemp[6]);
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
            asm.movl(retBroken, CalcStringAttributesEncoding.CR_VALID_MULTIBYTE);
            asm.pxor(vectorSize, vecResult, vecResult);
        }

        loadMask(crb, asm, stride, vecMaskAscii, 0xff80);
        loadMask(crb, asm, stride, vecMaskSurrogate, assumeValid ? 0x36 : 0x1b);
        asm.psllw(vectorSize, vecMaskLatin, vecMaskAscii, 1);
        DataSection.Data maskTail = createTailMask(crb, stride);
        DataSection.Data xmmTailShuffleMask = assumeValid ? null : writeToDataSection(crb, createXMMTailShuffleMask(XMM.getBytes()));

        vectorLoopPrologue(asm, arr, len, lengthTail, tailLessThan32, tailLessThan16, false);

        asm.movdqu(vectorSize, vecArrayTail, new AMD64Address(arr, lengthTail, stride, -vectorSize.getBytes()));
        // ascii loop: check if all chars are |<| 0x80 with VPTEST mask 0xff80
        emitPTestLoop(crb, asm, arr, len, vecArray, vecMaskAscii, latin1Entry);
        emitPTestTail(asm, vectorSize, arr, lengthTail, vecArray, vecMaskAscii, null, latin1Tail, returnAscii, false);

        asm.bind(latin1Entry);
        // latin1 loop: check if all chars are |<| 0x100 with VPTEST mask 0xff00
        emitPTestLoop(crb, asm, arr, len, vecArray, vecMaskLatin, bmpLoop);
        emitPTestTail(asm, vectorSize, arr, lengthTail, vecArray, vecMaskLatin, latin1Tail, bmpTail, returnLatin1, false);

        // bmp loop: search for UTF-16 surrogate characters
        asm.align(preferredLoopAlignment(crb));
        asm.bind(bmpLoop);
        asm.movdqu(vectorSize, vecArray, new AMD64Address(arr, len, stride));
        utf16FindSurrogatesAndTest(asm, vecArray, vecArray, vecMaskSurrogate);
        asm.jccb(NotZero, assumeValid ? labelSurrogateLoop : labelSurrogateEntry);
        asm.addqAndJcc(len, vectorLength, NotZero, bmpLoop, true);

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
            asm.align(preferredLoopAlignment(crb));
            asm.bind(labelSurrogateLoop);
            asm.movdqu(vectorSize, vecArray, new AMD64Address(arr, len, stride));
            utf16MatchSurrogates(asm, vecArray, vecMaskSurrogate);
            utf16SubtractMatchedChars(asm, ret, vecArray, tmp);
            asm.addqAndJcc(len, vectorLength, NotZero, labelSurrogateLoop, true);

            asm.testlAndJcc(lengthTail, lengthTail, Zero, returnValidOrBroken, false);
            // surrogate tail
            asm.leaq(tmp, (AMD64Address) crb.recordDataSectionReference(maskTail));
            asm.pandU(vectorSize, vecArrayTail, new AMD64Address(tmp, lengthTail, stride), vecTmp);
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
            asm.cmplAndJcc(ret, vectorLength, Equal, surrogateExactlyVectorSize, false);
            // corner case: check if the first char is a low surrogate, and if so, set code
            // range to BROKEN
            asm.movzwl(tmp, new AMD64Address(arr, len, stride));
            asm.shrl(tmp, 10);
            asm.cmpl(tmp, 0x37);
            asm.movl(tmp, CalcStringAttributesEncoding.CR_BROKEN_MULTIBYTE);
            asm.cmovl(Equal, retBroken, tmp);
            // high surrogate mask: 0x1b << 1 == 0x36
            asm.psllw(vectorSize, vecMaskSurrogate, vecMaskSurrogate, 1);
            // mask 0xff80 >> 15 == 0x1
            asm.psrlw(vectorSize, vecMaskAscii, vecMaskAscii, 15);
            // low surrogate mask: 0x1 | 0x36 == 0x37
            asm.por(vectorSize, vecMaskAscii, vecMaskSurrogate);
            // if there is no tail, we would read out of bounds in the loop, so remove the last
            // iteration if lengthTail is zero
            Label labelSurrogateCheckLoopCountZero = new Label();
            asm.testlAndJcc(lengthTail, lengthTail, NotZero, labelSurrogateCheckLoopCountZero, true);
            asm.subq(arr, vectorSize.getBytes());
            asm.addq(lengthTail, vectorLength);
            asm.addq(len, vectorLength);
            asm.bind(labelSurrogateCheckLoopCountZero);
            asm.testqAndJcc(len, len, Zero, labelSurrogateTail, true);

            // surrogate loop
            asm.align(preferredLoopAlignment(crb));
            asm.bind(labelSurrogateLoop);
            // load at current index
            asm.movdqu(vectorSize, vecArray, new AMD64Address(arr, len, stride));
            // load at current index + 1
            asm.movdqu(vectorSize, vecArrayTail, new AMD64Address(arr, len, stride, 2));
            utf16ValidateSurrogates(asm, ret, vecArray, vecArrayTail, vecMaskSurrogate, vecMaskAscii, vecTmp, vecResult, tmp);
            asm.addqAndJcc(len, vectorLength, NotZero, labelSurrogateLoop, true);

            // surrogate tail
            asm.bind(labelSurrogateTail);
            asm.leaq(tmp, (AMD64Address) crb.recordDataSectionReference(maskTail));
            // load at up to array end - 1
            asm.movdqu(vectorSize, vecArray, new AMD64Address(arr, lengthTail, stride, -(vectorSize.getBytes() + 2)));
            // load at up to array end
            asm.movdqu(vectorSize, vecArrayTail, new AMD64Address(arr, lengthTail, stride, -vectorSize.getBytes()));
            // remove elements overlapping with the last loop vector
            asm.pandU(vectorSize, vecArray, new AMD64Address(tmp, lengthTail, stride, -2), vecTmp);
            asm.pandU(vectorSize, vecArrayTail, new AMD64Address(tmp, lengthTail, stride, -2), vecTmp);
            utf16ValidateSurrogates(asm, ret, vecArray, vecArrayTail, vecMaskSurrogate, vecMaskAscii, vecTmp, vecResult, tmp);

            // corner case: check if last char is a high surrogate
            asm.movzwl(tmp, new AMD64Address(arr, lengthTail, stride, -2));
            asm.shrl(tmp, 10);
            // if last char is a high surrogate, return BROKEN
            asm.cmpl(tmp, 0x36);
            asm.movl(tmp, CalcStringAttributesEncoding.CR_BROKEN_MULTIBYTE);
            asm.cmovl(Equal, retBroken, tmp);
            asm.jmp(returnValidOrBroken);
        }

        if (supportsAVX2AndYMM()) {
            // special case: array is too short for YMM, try XMM
            // no loop, because the array is guaranteed to be smaller that 2 XMM registers
            asm.bind(tailLessThan32);
            asm.cmplAndJcc(lengthTail, elementsPerVector(XMM), Less, tailLessThan16, true);
            if (assumeValid) {
                loadLessThan32IntoYMMUnordered(crb, asm, stride, maskTail, arr, lengthTail, tmp, vecArray, vecTmp, vecArrayTail);
            } else {
                loadLessThan32IntoYMMOrdered(crb, asm, stride, xmmTailShuffleMask, arr, lengthTail, tmp, vecArray, vecTmp, vecArrayTail);
            }
            asm.jmpb(tailSingleVector);
        }

        asm.bind(tailLessThan16);
        asm.cmplAndJcc(lengthTail, elementsPerVector(QWORD), Less, tailLessThan8, true);
        if (assumeValid) {
            loadLessThan16IntoXMMUnordered(crb, asm, stride, maskTail, arr, lengthTail, tmp, vecArray, vecTmp, vecArrayTail);
        } else {
            loadLessThan16IntoXMMOrdered(crb, asm, stride, arr, lengthTail, tmp, vecArray, vecTmp, vecArrayTail);
        }
        asm.jmpb(tailSingleVector);

        asm.bind(tailLessThan8);
        asm.cmplAndJcc(lengthTail, elementsPerVector(DWORD), Less, tailLessThan4, true);
        if (assumeValid) {
            loadLessThan8IntoXMMUnordered(crb, asm, stride, maskTail, arr, lengthTail, vecArray, tmp, len);
        } else {
            loadLessThan8IntoXMMOrdered(asm, stride, arr, lengthTail, vecArray, tmp, len);
        }
        asm.jmpb(tailSingleVector);

        asm.bind(tailLessThan4);
        asm.testlAndJcc(lengthTail, lengthTail, Zero, returnAscii, false);
        asm.movzwq(tmp, new AMD64Address(arr));
        asm.movdq(vecArray, tmp);

        asm.bind(tailSingleVector);
        asm.ptest(vectorSize, vecArray, vecMaskAscii);
        asm.jcc(Zero, returnAscii);
        asm.ptest(vectorSize, vecArray, vecMaskLatin);
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
            asm.movdqu(vectorSize, vecArray, new AMD64Address(arr, -vectorSize.getBytes()));
            // corner case: check if the last char is a high surrogate, and if so, set code
            // range to BROKEN
            asm.movzwl(tmp, new AMD64Address(arr, -2));
            asm.shrl(tmp, 10);
            asm.cmpl(tmp, 0x36);
            asm.movl(tmp, CalcStringAttributesEncoding.CR_BROKEN_MULTIBYTE);
            asm.cmovl(Equal, retBroken, tmp);

            asm.bind(tailSingleVectorSurrogate);
            // high surrogate mask: 0x1b << 1 == 0x36
            asm.psllw(vectorSize, vecMaskSurrogate, vecMaskSurrogate, 1);
            // mask 0xff80 >> 15 == 0x1
            asm.psrlw(vectorSize, vecMaskAscii, vecMaskAscii, 15);
            // low surrogate mask: 0x1 | 0x36 == 0x37
            asm.por(vectorSize, vecMaskAscii, vecMaskSurrogate);

            // generate 2nd vector by left-shifting the entire vector by 2 bytes
            asm.pxor(vectorSize, vecTmp, vecTmp);
            prev(asm, vectorSize, vecArrayTail, vecArray, vecTmp, 2);

            utf16ValidateSurrogates(asm, ret, vecArrayTail, vecArray, vecMaskSurrogate, vecMaskAscii, vecTmp, vecResult, tmp);
            asm.jmpb(returnValidOrBroken);
        }
        emitExitMultiByte(asm, ret, returnAscii, end, CalcStringAttributesEncoding.CR_7BIT);
        emitExitMultiByte(asm, ret, returnLatin1, end, CalcStringAttributesEncoding.CR_8BIT);
        emitExitMultiByte(asm, ret, returnBMP, end, CalcStringAttributesEncoding.CR_16BIT);

        asm.bind(returnValidOrBroken);
        asm.shlq(ret, 32);
        if (assumeValid) {
            asm.orq(ret, CalcStringAttributesEncoding.CR_VALID_MULTIBYTE);
        } else {
            asm.ptest(vectorSize, vecResult, vecResult);
            asm.movl(tmp, CalcStringAttributesEncoding.CR_BROKEN_MULTIBYTE);
            asm.cmovl(NotZero, retBroken, tmp);
            asm.orq(ret, retBroken);
        }
        asm.bind(end);
    }

    private void utf16ValidateSurrogates(AMD64MacroAssembler asm,
                    Register ret, Register vecArray0, Register vecArray1, Register vecMaskHiSurrogate, Register vecMaskLoSurrogate, Register vecTmp, Register vecResult, Register tmp) {
        utf16MatchSurrogates(asm, vecArray0, vecMaskHiSurrogate);
        utf16MatchSurrogates(asm, vecArray1, vecMaskLoSurrogate);
        // identify the number of correctly encoded surrogate pairs
        asm.pand(vectorSize, vecTmp, vecArray0, vecArray1);
        // XOR the high surrogates identified at current index, with low surrogates
        // identified at current index + 1; if the result is zero, the current block is
        // encoded correctly
        asm.pxor(vectorSize, vecArray0, vecArray1);
        asm.pmovmsk(vectorSize, tmp, vecTmp);
        // count the number of surrogate pairs
        asm.popcntl(tmp, tmp);
        // merge the validation result into the final result
        asm.por(vectorSize, vecResult, vecArray0);
        asm.shrl(tmp, 1);
        // subtract the number of surrogate pairs from the total char length
        asm.subq(ret, tmp);
    }

    private void utf16MatchSurrogates(AMD64MacroAssembler asm, Register vecArray, Register vecMaskSurrogate) {
        // identify only high or low surrogates
        asm.psrlw(vectorSize, vecArray, vecArray, 10);
        asm.pcmpeqw(vectorSize, vecArray, vecMaskSurrogate);
    }

    private void utf16FindSurrogatesAndTest(AMD64MacroAssembler asm, Register vecDst, Register vecArray, Register vecMaskSurrogate) {
        // identify surrogates by checking the upper 6 or 5 bits
        asm.psrlw(vectorSize, vecDst, vecArray, assumeValid ? 10 : 11);
        asm.pcmpeqw(vectorSize, vecDst, vecMaskSurrogate);
        asm.ptest(vectorSize, vecDst, vecDst);
    }

    private void utf16SubtractMatchedChars(AMD64MacroAssembler asm, Register ret, Register vecArrayTail, Register tmp) {
        asm.pmovmsk(vectorSize, tmp, vecArrayTail);
        asm.popcntl(tmp, tmp);
        asm.shrl(tmp, 1);
        asm.subq(ret, tmp);
    }

    private void emitUTF32(CompilationResultBuilder crb, AMD64MacroAssembler asm, Register arr, Register len, Register lengthTail, Register ret, Register vecArray) {
        assert stride.log2 == 2 : stride.log2;
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

        loadMask(crb, asm, stride, vecMaskAscii, 0xffffff80);
        loadMask(crb, asm, stride, vecMaskSurrogate, 0x1b);
        loadMask(crb, asm, stride, vecMaskOutOfRange, 0x10);
        // generate latin1 mask 0xffffff00
        asm.pslld(vectorSize, vecMaskLatin1, vecMaskAscii, 1);
        // generate bmp mask 0xffff0000
        asm.pslld(vectorSize, vecMaskBMP, vecMaskAscii, 9);

        vectorLoopPrologue(asm, arr, len, lengthTail, tailLessThan32, tailLessThan16, false);

        asm.movdqu(vectorSize, vecArrayTail, new AMD64Address(arr, lengthTail, stride, -vectorSize.getBytes()));

        // ascii loop: check if all codepoints are |<| 0x80 with VPTEST mask 0xffffff80
        emitPTestLoop(crb, asm, arr, len, vecArray, vecMaskAscii, labelLatin1Entry);
        emitPTestTail(asm, vectorSize, arr, lengthTail, vecArray, vecMaskAscii, null, labelLatin1Tail, returnAscii, false);

        asm.bind(labelLatin1Entry);
        // latin1 loop: check if all codepoints are |<| 0x100 with VPTEST mask 0xffffff00
        emitPTestLoop(crb, asm, arr, len, vecArray, vecMaskLatin1, labelBMPEntry);
        emitPTestTail(asm, vectorSize, arr, lengthTail, vecArray, vecMaskLatin1, labelLatin1Tail, labelBMPTail, returnLatin1, false);

        asm.bind(labelBMPEntry);
        // bmp loop: check if all codepoints are |<| 0x10000 with VPTEST mask 0xffff0000
        asm.align(preferredLoopAlignment(crb));
        asm.bind(labelBMPLoop);
        asm.movdqu(vectorSize, vecArray, new AMD64Address(arr, len, stride));
        asm.ptest(vectorSize, vecArray, vecMaskBMP);
        asm.jccb(NotZero, labelAstralLoop);
        // check if any codepoints are in the forbidden UTF-16 surrogate range; if so, break
        // immediately and return BROKEN
        utf32CheckInvalid(asm, vecArray, vecArray, vecArrayTmp, vecMaskSurrogate, vecMaskOutOfRange, returnBroken, true);
        asm.addqAndJcc(len, vectorLength, NotZero, labelBMPLoop, true);

        // bmp tail
        asm.bind(labelBMPTail);
        asm.ptest(vectorSize, vecArrayTail, vecMaskBMP);
        asm.jccb(NotZero, labelAstralTail);
        utf32CheckInvalid(asm, vecArrayTail, vecArrayTail, vecArrayTmp, vecMaskSurrogate, vecMaskOutOfRange, returnBroken, true);
        asm.jmpb(returnBMP);

        emitExit(asm, ret, returnBroken, end, CalcStringAttributesEncoding.CR_BROKEN, false);
        emitExit(asm, ret, returnBMP, end, CalcStringAttributesEncoding.CR_16BIT, false);

        // astral loop: check if any codepoints are in the forbidden UTF-16 surrogate range;
        // if so, break immediately and return BROKEN
        asm.align(preferredLoopAlignment(crb));
        asm.bind(labelAstralLoop);
        asm.movdqu(vectorSize, vecArray, new AMD64Address(arr, len, stride));
        utf32CheckInvalid(asm, vecArray, vecArray, vecArrayTmp, vecMaskSurrogate, vecMaskOutOfRange, returnBroken, true);
        asm.addqAndJcc(len, vectorLength, NotZero, labelAstralLoop, true);

        // astral tail
        asm.bind(labelAstralTail);
        utf32CheckInvalid(asm, vecArrayTail, vecArrayTail, vecArrayTmp, vecMaskSurrogate, vecMaskOutOfRange, returnBroken, true);
        asm.jmp(returnAstral);

        if (supportsAVX2AndYMM()) {
            // special case: array is too short for YMM, try XMM
            // no loop, because the array is guaranteed to be smaller that 2 XMM registers
            asm.bind(tailLessThan32);
            asm.cmplAndJcc(lengthTail, elementsPerVector(XMM), Less, tailLessThan16, true);

            asm.movdqu(XMM, vecArray, new AMD64Address(arr));
            asm.movdqu(XMM, vecArrayTail, new AMD64Address(arr, lengthTail, stride, -XMM.getBytes()));
            AMD64Assembler.VexRVMIOp.VPERM2I128.emit(asm, vectorSize, vecArray, vecArray, vecArrayTail, 0x02);
            asm.jmpb(tailSingleVector);
        }

        asm.bind(tailLessThan16);
        asm.cmplAndJcc(lengthTail, elementsPerVector(QWORD), Less, tailLessThan8, true);
        // array is between 8 and 12 bytes long, load it into a vector register via two QWORD loads
        asm.movdq(vecArray, new AMD64Address(arr));
        asm.movdq(vecArrayTail, new AMD64Address(arr, lengthTail, stride, -QWORD.getBytes()));
        asm.movlhps(vecArray, vecArrayTail);
        asm.jmpb(tailSingleVector);

        asm.bind(tailLessThan8);
        asm.testlAndJcc(lengthTail, lengthTail, Zero, returnAscii, true);
        // array is 4 bytes long, load it into a vector register via VMOVDL
        asm.movdl(vecArray, new AMD64Address(arr));

        asm.bind(tailSingleVector);
        asm.ptest(vectorSize, vecArray, vecMaskAscii);
        asm.jccb(Zero, returnAscii);
        asm.ptest(vectorSize, vecArray, vecMaskLatin1);
        asm.jccb(Zero, returnLatin1);
        utf32CheckInvalid(asm, vecArrayTail, vecArray, vecArrayTmp, vecMaskSurrogate, vecMaskOutOfRange, returnBroken, false);
        asm.ptest(vectorSize, vecArray, vecMaskBMP);
        asm.jcc(Zero, returnBMP);

        emitExit(asm, ret, returnAstral, end, CalcStringAttributesEncoding.CR_VALID);

        emitExit(asm, ret, returnLatin1, end, CalcStringAttributesEncoding.CR_8BIT);
        emitExitAtEnd(asm, ret, returnAscii, end, CalcStringAttributesEncoding.CR_7BIT);
    }

    private void utf32CheckInvalid(AMD64MacroAssembler asm, Register vecArrayDst, Register vecArraySrc, Register vecArrayTmp, Register vecMaskBroken, Register vecMaskOutOfRange, Label returnBroken,
                    boolean isShortJmp) {
        // right-shift string contents by 16
        asm.psrld(vectorSize, vecArrayTmp, vecArraySrc, 16);
        // right-shift string contents (copy) by 11
        asm.psrld(vectorSize, vecArrayDst, vecArraySrc, 11);
        // identify codepoints larger than 0x10ffff ((codepoint >> 16) > 0x10)
        asm.pcmpgtd(vectorSize, vecArrayTmp, vecMaskOutOfRange);
        // identify codepoints in the forbidden UTF-16 surrogate range [0xd800-0xdfff]
        // ((codepoint >> 11) == 0x1b)
        asm.pcmpeqd(vectorSize, vecArrayDst, vecMaskBroken);
        // merge comparison results
        asm.por(vectorSize, vecArrayDst, vecArrayTmp);
        // check if any invalid character was present
        asm.ptest(vectorSize, vecArrayDst, vecArrayDst);
        // return CR_BROKEN if any invalid character was present
        asm.jcc(NotZero, returnBroken, isShortJmp);
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
}
