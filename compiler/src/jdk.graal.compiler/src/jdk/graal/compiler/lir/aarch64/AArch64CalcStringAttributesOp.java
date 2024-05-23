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

import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction.LD1_MULTIPLE_4R;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize.DoubleWord;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize.HalfWord;
import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize.Word;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createImmediateAddress;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createPairBaseRegisterOnlyAddress;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createRegisterOffsetAddress;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createStructureImmediatePostIndexAddress;
import static jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler.PREFERRED_BRANCH_TARGET_ALIGNMENT;
import static jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.graal.compiler.lir.gen.LIRGeneratorTool.CalcStringAttributesEncoding.CR_16BIT;
import static jdk.graal.compiler.lir.gen.LIRGeneratorTool.CalcStringAttributesEncoding.CR_7BIT;
import static jdk.graal.compiler.lir.gen.LIRGeneratorTool.CalcStringAttributesEncoding.CR_8BIT;
import static jdk.graal.compiler.lir.gen.LIRGeneratorTool.CalcStringAttributesEncoding.CR_BROKEN;
import static jdk.graal.compiler.lir.gen.LIRGeneratorTool.CalcStringAttributesEncoding.CR_BROKEN_MULTIBYTE;
import static jdk.graal.compiler.lir.gen.LIRGeneratorTool.CalcStringAttributesEncoding.CR_VALID;
import static jdk.graal.compiler.lir.gen.LIRGeneratorTool.CalcStringAttributesEncoding.CR_VALID_MULTIBYTE;
import static jdk.graal.compiler.lir.gen.LIRGeneratorTool.CalcStringAttributesEncoding.UTF_16;
import static jdk.graal.compiler.lir.gen.LIRGeneratorTool.CalcStringAttributesEncoding.UTF_32;
import static jdk.graal.compiler.lir.gen.LIRGeneratorTool.CalcStringAttributesEncoding.UTF_8;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import java.util.Arrays;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ShiftType;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.code.DataSection;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool.CalcStringAttributesEncoding;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

/**
 * This intrinsic calculates the code range and codepoint length of strings in various encodings.
 * The code range of a string is a flag that coarsely describes upper and lower bounds of all
 * codepoints contained in a string, or indicates whether a string was encoded correctly.
 */
@Opcode("CALC_STRING_ATTRIBUTES")
public final class AArch64CalcStringAttributesOp extends AArch64ComplexVectorOp {
    public static final LIRInstructionClass<AArch64CalcStringAttributesOp> TYPE = LIRInstructionClass.create(AArch64CalcStringAttributesOp.class);

    private final CalcStringAttributesEncoding encoding;

    private final Stride stride;
    /**
     * If true, assume the string to be encoded correctly.
     */
    private final boolean assumeValid;

    @Def({REG}) private Value result;
    @Alive({REG}) private Value array;
    @Alive({REG}) private Value offset;
    @Alive({REG}) private Value length;

    @Temp({REG}) private Value[] temp;
    @Temp({REG}) private Value[] vectorTemp;

    public AArch64CalcStringAttributesOp(LIRGeneratorTool tool, CalcStringAttributesEncoding encoding,
                    Value array, Value offset, Value length, Value result, boolean assumeValid) {
        super(TYPE);
        this.encoding = encoding;
        this.assumeValid = assumeValid;

        this.stride = encoding.stride;

        GraalError.guarantee(array.getPlatformKind() == AArch64Kind.QWORD, "pointer value expected");
        GraalError.guarantee(offset.getPlatformKind() == AArch64Kind.QWORD, "long value expected");
        GraalError.guarantee(length.getPlatformKind() == AArch64Kind.DWORD, "int value expected");
        if (encoding == UTF_8 || encoding == UTF_16) {
            GraalError.guarantee(result.getPlatformKind() == AArch64Kind.QWORD, "long value expected");
        } else {
            GraalError.guarantee(result.getPlatformKind() == AArch64Kind.DWORD, "int value expected");
        }

        this.array = array;
        this.offset = offset;
        this.length = length;
        this.result = result;

        temp = allocateTempRegisters(tool, getNumberOfTempRegisters(encoding, assumeValid));
        int nVectors = getNumberOfRequiredVectorRegisters(encoding, assumeValid);
        vectorTemp = needConsecutiveVectors(encoding, assumeValid) ? allocateConsecutiveVectorRegisters(tool, nVectors) : allocateVectorRegisters(tool, nVectors);
    }

    private static boolean needConsecutiveVectors(CalcStringAttributesEncoding encoding, boolean assumeValid) {
        // variants using the LD1 instruction need consecutive vector registers
        return encoding == UTF_8 && !assumeValid || encoding == UTF_32;
    }

    private static int getNumberOfTempRegisters(CalcStringAttributesEncoding encoding, boolean assumeValid) {
        switch (encoding) {
            case LATIN1:
            case BMP:
            case UTF_32:
                return 1;
            case UTF_16:
                return 2;
            case UTF_8:
                return assumeValid ? 2 : 5;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(encoding); // ExcludeFromJacocoGeneratedReport
        }
    }

    private static int getNumberOfRequiredVectorRegisters(CalcStringAttributesEncoding encoding, boolean assumeValid) {
        switch (encoding) {
            case LATIN1:
                return 3;
            case BMP:
                return 4;
            case UTF_8:
                return assumeValid ? 8 : 17;
            case UTF_16:
                return 8;
            case UTF_32:
                return 11;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(encoding); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler asm) {
        try (AArch64MacroAssembler.ScratchRegister sc1 = asm.getScratchRegister(); AArch64MacroAssembler.ScratchRegister sc2 = asm.getScratchRegister()) {
            Register arr = sc1.getRegister();
            Register len = sc2.getRegister();
            Register tmp = asRegister(temp[0]);
            Register ret = asRegister(result);
            Label end = new Label();

            asm.add(64, arr, asRegister(array), asRegister(offset));

            asm.mov(32, len, asRegister(length));

            switch (encoding) {
                case LATIN1:
                    emitLatin1(asm, arr, len, tmp, ret, end, asRegister(vectorTemp[0]), asRegister(vectorTemp[1]), asRegister(vectorTemp[2]));
                    break;
                case BMP:
                    emitBMP(asm, arr, len, tmp, ret, end);
                    break;
                case UTF_8:
                    emitUTF8(crb, asm, arr, len, tmp, ret, end);
                    break;
                case UTF_16:
                    emitUTF16(crb, asm, arr, len, tmp, ret, end);
                    break;
                case UTF_32:
                    emitUTF32(asm, arr, len, tmp, ret, end);
                    break;
                default:
                    throw GraalError.shouldNotReachHereUnexpectedValue(encoding); // ExcludeFromJacocoGeneratedReport
            }
            asm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
            asm.bind(end);
        }
    }

    /**
     * Implements the operation described in {@link CalcStringAttributesEncoding#LATIN1}.
     */
    static void emitLatin1(AArch64MacroAssembler asm, Register arr, Register len, Register tmp, Register ret, Label end, Register vecArray1, Register vecArray2, Register vecMask) {
        Label tailLessThan32 = new Label();
        Label tailLessThan16 = new Label();
        Label tailLessThan8 = new Label();
        Label tailLessThan4 = new Label();
        Label tailLessThan2 = new Label();

        Label tailLoaded = new Label();
        Label vectorLoop = new Label();

        asm.neon.moveVI(FullReg, ElementSize.Byte, vecMask, (byte) 0x80);

        // subtract 32 from len. if the result is negative, jump to branch for less than 32 bytes
        asm.subs(64, len, len, 32);
        asm.branchConditionally(ConditionFlag.MI, tailLessThan32);

        Register refAddress = len;
        asm.add(64, refAddress, arr, len);
        asm.mov(ret, CR_8BIT);

        // 32 byte loop
        asm.align(PREFERRED_LOOP_ALIGNMENT);
        asm.bind(vectorLoop);
        // load 2 vectors from the string
        asm.fldp(128, vecArray1, vecArray2, createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arr, 32));
        asm.neon.orrVVV(FullReg, vecArray1, vecArray1, vecArray2);
        asm.neon.andVVV(FullReg, vecArray1, vecArray1, vecMask);
        cbnzVector(asm, ElementSize.Byte, vecArray1, vecArray1, tmp, false, end);
        asm.cmp(64, arr, refAddress);
        asm.branchConditionally(ConditionFlag.LO, vectorLoop);

        // 32 byte loop tail
        asm.mov(64, arr, refAddress);
        asm.fldp(128, vecArray1, vecArray2, createPairBaseRegisterOnlyAddress(128, arr));
        asm.neon.orrVVV(FullReg, vecArray1, vecArray1, vecArray2);
        asm.neon.andVVV(FullReg, vecArray1, vecArray1, vecMask);
        cmpZeroVector(asm, vecArray1, vecArray1, tmp);
        // use the fact that CR_7BIT is 0 and CR_8BIT is 1 for setting the return value via cset
        asm.cset(64, ret, ConditionFlag.NE);
        asm.jmp(end);

        // tail for 16 - 31 bytes
        asm.bind(tailLessThan32);
        // len is original len - 32 and guaranteed negative. add 16. if result is still negative,
        // jump to branch for less than 16 bytes
        asm.adds(64, len, len, 16);
        asm.branchConditionally(ConditionFlag.MI, tailLessThan16);
        // load arr[0:16]
        asm.fldr(128, vecArray1, AArch64Address.createBaseRegisterOnlyAddress(128, arr));
        // load arr[arrayLength-16:arrayLength]
        asm.fldr(128, vecArray2, AArch64Address.createRegisterOffsetAddress(128, arr, len, false));
        asm.neon.orrVVV(FullReg, vecArray1, vecArray1, vecArray2);
        asm.neon.andVVV(FullReg, vecArray1, vecArray1, vecMask);
        cmpZeroVector(asm, vecArray1, vecArray1, tmp);
        assert returnValueAssertions();
        // use the fact that CR_7BIT is 0 and CR_8BIT is 1 for setting the return value via cset
        asm.cset(64, ret, ConditionFlag.NE);
        asm.jmp(end);

        asm.bind(tailLessThan16);
        // tail for 8 - 15 bytes
        tailLoad(asm, arr, len, ret, tmp, null, tailLessThan8, tailLoaded, 8);
        // tail for 4 - 7 bytes
        tailLoad(asm, arr, len, ret, tmp, tailLessThan8, tailLessThan4, tailLoaded, 4);
        // tail for 2 - 3 bytes
        tailLoad(asm, arr, len, ret, tmp, tailLessThan4, tailLessThan2, tailLoaded, 2);

        asm.bind(tailLessThan2);
        asm.mov(64, ret, zr);
        // tail for 0 - 1 bytes
        asm.adds(64, len, len, 1);
        asm.branchConditionally(ConditionFlag.MI, end);
        asm.ldr(8, tmp, AArch64Address.createBaseRegisterOnlyAddress(8, arr));

        asm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
        asm.bind(tailLoaded);
        Register mask = len;
        // array is loaded into registers ret and tmp, check bytes in general-purpose registers with
        // the mask from vecMask
        asm.neon.moveFromIndex(DoubleWord, DoubleWord, mask, vecMask, 0);
        asm.orr(64, ret, ret, tmp);
        asm.tst(64, ret, mask);
        assert returnValueAssertions();
        // use the fact that CR_7BIT is 0 and CR_8BIT is 1 for setting the return value via cset
        asm.cset(64, ret, ConditionFlag.NE);
    }

    private static void tailLoad(AArch64MacroAssembler asm,
                    Register arr, Register len, Register tmp1, Register tmp2,
                    Label entry, Label nextTail, Label done, int chunkSize) {
        int bits = chunkSize << 3;
        if (entry != null) {
            asm.bind(entry);
        }
        asm.adds(64, len, len, chunkSize);
        asm.branchConditionally(ConditionFlag.MI, nextTail);
        asm.ldr(bits, tmp1, AArch64Address.createBaseRegisterOnlyAddress(bits, arr));
        asm.ldr(bits, tmp2, AArch64Address.createRegisterOffsetAddress(bits, arr, len, false));
        asm.jmp(done);
    }

    /**
     * Implements the operation described in {@link CalcStringAttributesEncoding#BMP}.
     */
    private void emitBMP(AArch64MacroAssembler asm, Register arr, Register len, Register tmp, Register ret, Label end) {
        assert stride.log2 == 1 : stride;
        Label tailLessThan32 = new Label();
        Label tailLessThan16 = new Label();
        Label tailLessThan8 = new Label();
        Label tailLessThan4 = new Label();

        Label tailLoaded = new Label();
        Label asciiLoop = new Label();
        Label latinLoop = new Label();

        Label latinFound = new Label();
        Label latinContinue = new Label();

        Register vecArray1 = asRegister(vectorTemp[0]);
        Register vecArray2 = asRegister(vectorTemp[1]);
        Register vecMask = asRegister(vectorTemp[2]);
        Register vecTmp1 = asRegister(vectorTemp[3]);

        // convert length to byte length
        asm.lsl(64, len, len, 1);

        asm.neon.moveVI(FullReg, ElementSize.HalfWord, vecMask, (short) 0xff80);

        // subtract 32 from len. if the result is negative, jump to branch for less than 32 bytes
        asm.subs(64, len, len, 32);
        asm.branchConditionally(ConditionFlag.MI, tailLessThan32);

        Register refAddress = len;
        asm.add(64, refAddress, arr, len);

        // 32 byte ascii loop
        asm.align(PREFERRED_LOOP_ALIGNMENT);
        asm.bind(asciiLoop);
        asm.fldp(128, vecArray1, vecArray2, createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arr, 32));
        asm.neon.orrVVV(FullReg, vecTmp1, vecArray1, vecArray2);
        asm.neon.cmtstVVV(FullReg, HalfWord, vecTmp1, vecTmp1, vecMask);
        cbnzVector(asm, HalfWord, vecTmp1, vecTmp1, tmp, true, latinFound);
        asm.cmp(64, arr, refAddress);
        asm.branchConditionally(ConditionFlag.LO, asciiLoop);

        // 32 byte ascii loop tail
        asm.mov(64, arr, refAddress);
        asm.fldp(128, vecArray1, vecArray2, createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arr, 32));
        asm.neon.orrVVV(FullReg, vecTmp1, vecArray1, vecArray2);
        asm.neon.cmtstVVV(FullReg, HalfWord, vecTmp1, vecTmp1, vecMask);
        cmpZeroVector(asm, HalfWord, vecTmp1, vecTmp1, tmp, true);
        assert returnValueAssertions();
        asm.cset(64, ret, ConditionFlag.NE);
        asm.neon.uzp2VVV(FullReg, ElementSize.Byte, vecTmp1, vecArray1, vecArray2);
        cmpZeroVector(asm, vecTmp1, vecTmp1, tmp);
        asm.csinc(64, ret, ret, ret, ConditionFlag.EQ);
        asm.jmp(end);

        asm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
        asm.bind(latinFound);
        asm.mov(ret, CR_16BIT);
        asm.jmp(latinContinue);

        // 32 byte latin1 loop
        asm.align(PREFERRED_LOOP_ALIGNMENT);
        asm.bind(latinLoop);
        asm.fldp(128, vecArray1, vecArray2, createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arr, 32));
        asm.bind(latinContinue);
        asm.neon.uzp2VVV(FullReg, ElementSize.Byte, vecTmp1, vecArray1, vecArray2);
        // Check if characters are latin1 by extracting the upper bytes and checking if the
        // resulting vector is zero.
        cbnzVector(asm, ElementSize.Byte, vecTmp1, vecTmp1, tmp, false, end);
        asm.cmp(64, arr, refAddress);
        asm.branchConditionally(ConditionFlag.LO, latinLoop);

        // 32 byte latin1 loop tail
        asm.mov(64, arr, refAddress);
        asm.fldp(128, vecArray1, vecArray2, createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arr, 32));
        asm.mov(ret, CR_8BIT);
        asm.neon.uzp2VVV(FullReg, ElementSize.Byte, vecTmp1, vecArray1, vecArray2);
        cmpZeroVector(asm, vecTmp1, vecTmp1, tmp);
        asm.csinc(64, ret, ret, ret, ConditionFlag.EQ);
        asm.jmp(end);

        // tail for 16 - 31 bytes
        asm.bind(tailLessThan32);
        // len is original len - 32 and guaranteed negative. add 16. if result is still negative,
        // jump to branch for less than 16 bytes
        asm.adds(64, len, len, 16);
        asm.branchConditionally(ConditionFlag.MI, tailLessThan16);
        asm.fldr(128, vecArray1, AArch64Address.createBaseRegisterOnlyAddress(128, arr));
        asm.fldr(128, vecArray2, AArch64Address.createRegisterOffsetAddress(128, arr, len, false));
        asm.neon.orrVVV(FullReg, vecTmp1, vecArray1, vecArray2);
        asm.neon.cmtstVVV(FullReg, HalfWord, vecTmp1, vecTmp1, vecMask);
        cmpZeroVector(asm, HalfWord, vecTmp1, vecTmp1, tmp, true);
        asm.cset(64, ret, ConditionFlag.NE);
        asm.neon.uzp2VVV(FullReg, ElementSize.Byte, vecTmp1, vecArray1, vecArray2);
        cmpZeroVector(asm, vecTmp1, vecTmp1, tmp);
        asm.csinc(64, ret, ret, ret, ConditionFlag.EQ);
        asm.jmp(end);

        // tail for 8 - 15 bytes
        tailLoad(asm, arr, len, ret, tmp, tailLessThan16, tailLessThan8, tailLoaded, 8);
        // tail for 4 - 7 bytes
        tailLoad(asm, arr, len, ret, tmp, tailLessThan8, tailLessThan4, tailLoaded, 4);
        // tail for 2 - 3 bytes
        asm.bind(tailLessThan4);
        asm.mov(64, ret, zr); // ret = CR_7BIT
        asm.adds(64, len, len, 2);
        asm.branchConditionally(ConditionFlag.MI, end);
        asm.ldr(16, tmp, AArch64Address.createBaseRegisterOnlyAddress(16, arr));

        asm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
        asm.bind(tailLoaded);
        Register maskAscii = len;
        Register maskLatin = arr;
        asm.neon.moveFromIndex(DoubleWord, DoubleWord, maskAscii, vecMask, 0);
        asm.neon.shlVVI(FullReg, ElementSize.HalfWord, vecMask, vecMask, 1);
        asm.neon.moveFromIndex(DoubleWord, DoubleWord, maskLatin, vecMask, 0);
        asm.orr(64, tmp, ret, tmp);
        asm.tst(64, tmp, maskAscii);
        asm.cset(64, ret, ConditionFlag.NE);
        asm.tst(64, tmp, maskLatin);
        asm.csinc(64, ret, ret, ret, ConditionFlag.EQ);
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
     * Implements the operation described in {@link CalcStringAttributesEncoding#UTF_8}.
     * <p>
     * Based on the paper <a href="https://arxiv.org/abs/2010.03090">Validating UTF-8 In Less Than
     * One Instruction Per Byte</a> by John Keiser and Daniel Lemire, and the author's
     * implementation in <a href=
     * "https://github.com/simdjson/simdjson/blob/d996ffc49423cee75922c30432323288c34f3c04/src/generic/stage1/utf8_lookup4_algorithm.h">
     * the simdjson library</a>. Follow-up changes made sure this is compatible with simdjson 3.6.4.
     *
     * @see <a href="https://github.com/simdjson/simdjson">https://github.com/simdjson/simdjson</a>
     * @see <a href="https://lemire.me/blog/2020/10/20/ridiculously-fast-unicode-utf-8-validation/">
     *      https://lemire.me/blog/2020/10/20/ridiculously-fast-unicode-utf-8-validation/</a>
     */
    private void emitUTF8(CompilationResultBuilder crb, AArch64MacroAssembler asm, Register arr, Register len, Register tmp, Register ret, Label end) {
        assert stride.log2 == 0 : stride;
        Label tailLessThan32 = new Label();
        Label tailLessThan32Continue = new Label();
        Label tailLessThan16 = new Label();
        Label tailLessThan8 = new Label();

        Label asciiLoop = new Label();

        Label multibyteFound = new Label();
        Label multibyteContinue = new Label();
        Label multibyteLoop = new Label();
        Label multibyteFoundTail = new Label();

        Label labelScalarTail = new Label();

        Register vecArray1 = asRegister(vectorTemp[0]);
        Register vecArray2 = asRegister(vectorTemp[1]);
        Register vecTmp1 = asRegister(vectorTemp[2]);
        Register vecTmp2 = asRegister(vectorTemp[3]);
        Register vecTmp3 = asRegister(vectorTemp[4]);
        Register vecMask0x80 = asRegister(vectorTemp[5]);
        Register vecMask0xC0 = asRegister(vectorTemp[6]);
        Register vecNCB = asRegister(vectorTemp[7]);

        Register tmp2 = asRegister(temp[1]);

        DataSection.Data maskTail = assumeValid ? createTailANDMask(crb, 32) : createTailShuffleMask(crb, 16);

        asm.mov(32, ret, len);

        asm.neon.moveVI(FullReg, ElementSize.Byte, vecMask0xC0, (byte) 0xc0);
        asm.neon.moveVI(FullReg, ElementSize.Byte, vecMask0x80, (byte) 0x80);

        // subtract 32 from len. if the result is negative, jump to branch for less than 32 bytes
        asm.subs(64, len, len, 32);
        asm.branchConditionally(ConditionFlag.MI, tailLessThan32);

        Register refAddress = len;
        asm.add(64, refAddress, arr, len);

        // 32 byte ascii loop
        asm.align(PREFERRED_LOOP_ALIGNMENT);
        asm.bind(asciiLoop);
        asm.fldp(128, vecArray1, vecArray2, createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arr, 32));
        asm.neon.orrVVV(FullReg, vecTmp1, vecArray1, vecArray2);
        asm.neon.andVVV(FullReg, vecTmp2, vecTmp1, vecMask0x80);
        cbnzVector(asm, ElementSize.Byte, vecTmp2, vecTmp2, tmp, false, multibyteFound);
        asm.cmp(64, arr, refAddress);
        asm.branchConditionally(ConditionFlag.LO, asciiLoop);

        // 32 byte ascii loop tail
        asm.mov(64, arr, refAddress);
        asm.fldp(128, vecArray1, vecArray2, createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arr, 32));
        asm.bind(tailLessThan32Continue);
        asm.neon.orrVVV(FullReg, vecTmp1, vecArray1, vecArray2);
        asm.neon.andVVV(FullReg, vecTmp2, vecTmp1, vecMask0x80);
        cbnzVector(asm, ElementSize.Byte, vecTmp2, vecTmp2, tmp, false, assumeValid ? multibyteFoundTail : multibyteFound);
        assert returnValueAssertions();
        asm.lsl(64, ret, ret, 32);
        asm.jmp(end);

        if (assumeValid) {

            // non-ascii bytes have been found, calculate the codepoint length
            // (assuming the string is encoded correctly)

            asm.bind(multibyteFoundTail);
            asm.neon.moveVI(FullReg, ElementSize.Byte, vecNCB, 0);
            // identify continuation bytes via ((b & 0xc0) == 0x80)
            utf8CountContinuationBytes(asm, vecArray1, vecArray2, vecTmp1, vecTmp2, vecMask0xC0, vecMask0x80, vecNCB);
            asm.neon.uaddlvSV(FullReg, ElementSize.Byte, vecNCB, vecNCB);
            asm.neon.moveFromIndex(DoubleWord, DoubleWord, tmp, vecNCB, 0);
            // subtract continuation byte count from return value (== byte length)
            asm.sub(64, ret, ret, tmp);
            asm.mov(tmp, CR_VALID_MULTIBYTE);
            asm.orr(64, ret, tmp, ret, ShiftType.LSL, 32);
            asm.jmp(end);

            asm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
            asm.bind(multibyteFound);
            setCodepointCountOuterLoopRefAddress(asm, arr, refAddress, tmp2, 254 * 16, 32);
            asm.neon.moveVI(FullReg, ElementSize.Byte, vecNCB, 0);
            asm.jmp(multibyteContinue);

            // --- begin of outer loop ---
            // The main loop is nested in an outer loop so that the inner loop can update the
            // continuation byte count without the expensive uaddlvSV instruction, which according
            // to the Arm Cortex-A76 Software Optimization Guide has an execution latency of 6
            // cycles. The inner loop accumulates the continuation byte count in vecNCB as long as
            // it cannot overflow, and the outer loop processes the accumulated vector.

            Label multibyteOuterLoop = emitCodepointCountOuterLoopBegin(asm, arr, refAddress, tmp2, 254 * 16, 32);

            // --- begin of inner loop ---

            // 32 byte loop
            asm.align(PREFERRED_LOOP_ALIGNMENT);
            asm.bind(multibyteLoop);
            asm.fldp(128, vecArray1, vecArray2, createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arr, 32));
            asm.bind(multibyteContinue);
            utf8CountContinuationBytes(asm, vecArray1, vecArray2, vecTmp1, vecTmp2, vecMask0xC0, vecMask0x80, vecNCB);
            asm.cmp(64, arr, tmp2);
            asm.branchConditionally(ConditionFlag.LS, multibyteLoop);

            // --- end of inner loop ---

            emitCodepointCountOuterLoopEnd(asm, tmp, ret, vecNCB, tmp2, refAddress, multibyteOuterLoop);

            // --- end of outer loop ---

            // 32 byte loop tail
            Label skipTail = new Label();
            asm.mov(64, arr, refAddress);
            asm.and(32, len, asRegister(length), 31);
            asm.cbz(32, len, skipTail);
            asm.fldp(128, vecArray1, vecArray2, createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arr, 32));
            loadDataSectionAddress(crb, asm, tmp, maskTail);
            asm.add(64, tmp, tmp, len, AArch64Assembler.ExtendType.UXTW, 0);
            asm.fldp(128, vecTmp1, vecTmp2, AArch64Address.createPairBaseRegisterOnlyAddress(128, tmp));
            asm.neon.andVVV(FullReg, vecArray1, vecArray1, vecTmp1);
            asm.neon.andVVV(FullReg, vecArray2, vecArray2, vecTmp2);
            utf8CountContinuationBytes(asm, vecArray1, vecArray2, vecTmp1, vecTmp2, vecMask0xC0, vecMask0x80, vecNCB);
            asm.neon.uaddlvSV(FullReg, ElementSize.Byte, vecNCB, vecNCB);
            asm.neon.moveFromIndex(DoubleWord, DoubleWord, tmp, vecNCB, 0);
            asm.sub(64, ret, ret, tmp);
            asm.bind(skipTail);
            asm.mov(tmp, CR_VALID_MULTIBYTE);
            asm.orr(64, ret, tmp, ret, ShiftType.LSL, 32);
            asm.jmp(end);
        } else {

            Label multibyteTail = new Label();
            Label multibyteNoFastPath = new Label();

            byte[] masks = new byte[64];
            // [0:16]: isIncompleteMask.
            Arrays.fill(masks, 0, 16, (byte) ~0);
            masks[16 - 3] = (byte) 0b11110000;
            masks[16 - 2] = (byte) 0b11100000;
            masks[16 - 1] = (byte) 0b11000000;
            // [16:32]: vecLUTByte1Lo.
            System.arraycopy(UTF8_BYTE_1_LOW_TABLE, 0, masks, 16, 16);
            // [32:48]: vecLUTByte1Hi.
            System.arraycopy(UTF8_BYTE_1_HIGH_TABLE, 0, masks, 32, 16);
            // [48:64]: vecLUTByte2Hi.
            System.arraycopy(UTF8_BYTE_2_HIGH_TABLE, 0, masks, 48, 16);

            DataSection.Data masksData = writeToDataSection(crb, masks);

            Register vecMask3ByteSeq = asRegister(vectorTemp[8]);
            Register vecMask4ByteSeq = asRegister(vectorTemp[9]);
            Register vecMask0x0F = asRegister(vectorTemp[10]);
            Register vecIsIncompleteMask = asRegister(vectorTemp[11]);
            Register vecLUTByte1Lo = asRegister(vectorTemp[12]);
            Register vecLUTByte1Hi = asRegister(vectorTemp[13]);
            Register vecLUTByte2Hi = asRegister(vectorTemp[14]);
            Register vecPrevIsIncomplete = asRegister(vectorTemp[15]);
            Register vecPrev = asRegister(vectorTemp[16]);
            Register vecResult = vecArray2;

            // non-ascii loop: non-ascii bytes have been found, check encoding validity and
            // calculate the codepoint length

            asm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
            asm.bind(multibyteFound);
            asm.sub(64, arr, arr, 16);
            asm.add(64, refAddress, refAddress, 16);

            asm.bind(multibyteFoundTail);

            loadDataSectionAddress(crb, asm, tmp, masksData);

            // load all data section masks at once
            asm.neon.ld1MultipleVVVV(FullReg, ElementSize.Byte, vecIsIncompleteMask, vecLUTByte1Lo, vecLUTByte1Hi, vecLUTByte2Hi,
                            createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, FullReg, ElementSize.Byte, tmp, 64));
            asm.neon.moveVI(FullReg, ElementSize.Byte, vecMask4ByteSeq, (byte) 0b11110000);
            asm.neon.moveVI(FullReg, ElementSize.Byte, vecMask3ByteSeq, (byte) 0b11100000);
            asm.neon.moveVI(FullReg, ElementSize.Byte, vecMask0x0F, 0x0F);
            asm.neon.moveVI(FullReg, ElementSize.Byte, vecPrevIsIncomplete, 0);
            asm.neon.moveVI(FullReg, ElementSize.Byte, vecPrev, 0);
            asm.neon.moveVI(FullReg, ElementSize.Byte, vecResult, 0);
            asm.neon.moveVI(FullReg, ElementSize.Byte, vecNCB, 0);

            setCodepointCountOuterLoopRefAddress(asm, arr, refAddress, tmp2, 254 * 16, 16);
            asm.jmp(multibyteContinue);

            // --- begin of outer loop ---
            // nested to avoid using uaddlvSV in the inner loop, see the "assumeValid" variant above

            Label multibyteOuterLoop = emitCodepointCountOuterLoopBegin(asm, arr, refAddress, tmp2, 254 * 16, 16);

            // --- begin of inner loop ---

            // 16 byte multibyte loop
            asm.align(PREFERRED_LOOP_ALIGNMENT);
            asm.bind(multibyteLoop);
            asm.fldr(128, vecArray1, createImmediateAddress(128, AddressingMode.IMMEDIATE_POST_INDEXED, arr, 16));

            // fast path: check if current vector is ascii
            asm.bind(multibyteContinue);
            asm.neon.andVVV(FullReg, vecTmp1, vecArray1, vecMask0x80);
            cbnzVector(asm, ElementSize.Byte, vecTmp1, vecTmp1, tmp, false, multibyteNoFastPath);
            // current vector is ascii, if the previous vector ended with an incomplete sequence, we
            // found an error
            asm.neon.orrVVV(FullReg, vecResult, vecResult, vecPrevIsIncomplete);
            // continue
            asm.cmp(64, arr, tmp2);
            asm.branchConditionally(ConditionFlag.LS, multibyteLoop);
            asm.jmp(multibyteTail);

            asm.bind(multibyteNoFastPath);

            // codepoint counting logic:
            // AND the string's bytes with 0xc0
            asm.neon.andVVV(FullReg, vecTmp1, vecArray1, vecMask0xC0);
            // check if the result is equal to 0x80, identifying continuation bytes
            asm.neon.cmeqVVV(FullReg, ElementSize.Byte, vecTmp1, vecTmp1, vecMask0x80);
            // add identified continuation bytes to accumulator
            asm.neon.usraVVI(FullReg, ElementSize.Byte, vecNCB, vecTmp1, 7);

            // set vecTmp1 = vecPrev[15]:vecArray1[1:16]
            asm.neon.extVVV(FullReg, vecTmp1, vecPrev, vecArray1, 15);
            // load upper nibbles of vecTmp1 into vecTmp2
            asm.neon.ushrVVI(FullReg, ElementSize.Byte, vecTmp2, vecTmp1, 4);
            // load lower nibbles of vecTmp1 into vecTmp1
            asm.neon.andVVV(FullReg, vecTmp1, vecTmp1, vecMask0x0F);
            // perform table lookup for both nibbles
            asm.neon.tblVVV(FullReg, vecTmp2, vecLUTByte1Hi, vecTmp2);
            asm.neon.tblVVV(FullReg, vecTmp1, vecLUTByte1Lo, vecTmp1);
            // combine result into vecTmp1
            asm.neon.andVVV(FullReg, vecTmp1, vecTmp1, vecTmp2);
            // load upper nibbles of array at current index into vecTmp2
            asm.neon.ushrVVI(FullReg, ElementSize.Byte, vecTmp2, vecArray1, 4);
            // table lookup
            asm.neon.tblVVV(FullReg, vecTmp2, vecLUTByte2Hi, vecTmp2);
            // combine result into vecTmp1
            asm.neon.andVVV(FullReg, vecTmp1, vecTmp1, vecTmp2);
            // set vecTmp2 = vecPrev[14:]:vecArray1[2:16]
            asm.neon.extVVV(FullReg, vecTmp2, vecPrev, vecArray1, 14);
            // set vecTmp3 = vecPrev[13:]:vecArray1[3:16]
            asm.neon.extVVV(FullReg, vecTmp3, vecPrev, vecArray1, 13);
            // find all bytes greater or equal to 0b11100000 in vecTmp2
            asm.neon.cmhsVVV(FullReg, ElementSize.Byte, vecTmp2, vecTmp2, vecMask3ByteSeq);
            // find all bytes greater or equal to 0b11110000 in vecTmp3
            asm.neon.cmhsVVV(FullReg, ElementSize.Byte, vecTmp3, vecTmp3, vecMask4ByteSeq);
            // combine result into vecTmp2
            asm.neon.orrVVV(FullReg, vecTmp2, vecTmp2, vecTmp3);
            // convert result to 0x80 for all matching bytes
            asm.neon.andVVV(FullReg, vecTmp2, vecTmp2, vecMask0x80);
            // eor the result with the combined table lookup results
            asm.neon.eorVVV(FullReg, vecTmp1, vecTmp1, vecTmp2);
            // if the result of all this is non-zero, we found an error
            asm.neon.orrVVV(FullReg, vecResult, vecResult, vecTmp1);
            // check if the current vector ends with an incomplete multibyte-sequence
            // (needed only for ascii fast-path)
            asm.neon.cmhsVVV(FullReg, ElementSize.Byte, vecPrevIsIncomplete, vecArray1, vecIsIncompleteMask);
            // save current vector as vecPrev
            asm.neon.moveVV(FullReg, vecPrev, vecArray1);
            // continue loop
            asm.cmp(64, arr, tmp2);
            asm.branchConditionally(ConditionFlag.LS, multibyteLoop);

            // --- end of inner loop ---

            asm.bind(multibyteTail);
            Label done = emitCodepointCountOuterLoopEnd(asm, tmp, ret, vecNCB, tmp2, refAddress, multibyteOuterLoop, arr, 16);

            // --- end of outer loop ---

            // 16 byte multibyte loop tail
            asm.mov(64, arr, refAddress);
            asm.fldr(128, vecArray1, createImmediateAddress(128, AddressingMode.IMMEDIATE_POST_INDEXED, arr, 16));
            loadDataSectionAddress(crb, asm, tmp, maskTail);
            asm.and(32, tmp2, asRegister(length), 15);
            asm.add(64, tmp, tmp, 16);
            asm.neg(64, tmp2, tmp2);
            asm.fldr(128, vecTmp1, AArch64Address.createRegisterOffsetAddress(128, tmp, tmp2, false));
            asm.mov(64, tmp2, refAddress);
            asm.neon.tblVVV(FullReg, vecArray1, vecArray1, vecTmp1);
            asm.jmp(multibyteContinue);

            asm.bind(done);
            asm.neon.orrVVV(FullReg, vecResult, vecResult, vecPrevIsIncomplete);
            asm.mov(tmp, CR_VALID_MULTIBYTE);
            cmpZeroVector(asm, vecResult, vecResult, tmp2);
            assert returnValueAssertions();
            asm.csinc(64, tmp, tmp, tmp, ConditionFlag.EQ);
            asm.orr(64, ret, tmp, ret, ShiftType.LSL, 32);
            asm.jmp(end);
        }

        Label tailLessThan16Continue = new Label();

        // tail for 16 - 31 bytes
        asm.bind(tailLessThan32);
        if (assumeValid) {
            loadDataSectionAddress(crb, asm, tmp, maskTail);
        }
        // len is original len - 32 and guaranteed negative. add 16. if result is still negative,
        // jump to branch for less than 16 bytes
        asm.adds(64, len, len, 16);
        asm.branchConditionally(ConditionFlag.MI, tailLessThan16);
        asm.fldr(128, vecArray1, AArch64Address.createBaseRegisterOnlyAddress(128, arr));
        asm.fldr(128, vecArray2, AArch64Address.createRegisterOffsetAddress(128, arr, len, false));
        if (assumeValid) {
            asm.add(64, tmp, tmp, 16);
            asm.fldr(128, vecTmp2, AArch64Address.createRegisterOffsetAddress(128, tmp, len, false));
            asm.neon.andVVV(FullReg, vecArray2, vecArray2, vecTmp2);
            asm.jmp(tailLessThan32Continue);
        } else {
            asm.add(64, refAddress, arr, len);
            asm.add(64, arr, arr, 16);
            asm.neon.orrVVV(FullReg, vecTmp1, vecArray1, vecArray2);
            asm.neon.andVVV(FullReg, vecTmp2, vecTmp1, vecMask0x80);
            cbnzVector(asm, ElementSize.Byte, vecTmp2, vecTmp2, tmp, false, multibyteFoundTail);
            assert returnValueAssertions();
            asm.lsl(64, ret, ret, 32);
            asm.jmp(end);
        }

        // tail for 8 - 15 bytes
        asm.bind(tailLessThan16);
        asm.adds(64, len, len, 8);
        asm.branchConditionally(ConditionFlag.MI, tailLessThan8);
        asm.fldr(64, vecArray1, AArch64Address.createBaseRegisterOnlyAddress(64, arr));
        asm.fldr(64, vecArray2, AArch64Address.createRegisterOffsetAddress(64, arr, len, false));
        if (assumeValid) {
            asm.add(64, tmp, tmp, 24);
            asm.fldr(128, vecTmp2, AArch64Address.createRegisterOffsetAddress(128, tmp, len, false));
            asm.neon.andVVV(FullReg, vecArray2, vecArray2, vecTmp2);
            asm.jmp(tailLessThan32Continue);
        } else {
            loadDataSectionAddress(crb, asm, tmp, createTailShuffleMask(crb, 8));
            asm.add(64, tmp, tmp, 8);
            asm.neg(64, len, len);
            asm.fldr(128, vecTmp2, AArch64Address.createRegisterOffsetAddress(128, tmp, len, false));
            asm.neon.tblVVV(FullReg, vecArray2, vecArray2, vecTmp2);
            asm.neon.insXX(DoubleWord, vecArray1, 1, vecArray2, 0);
            asm.bind(tailLessThan16Continue);
            asm.neon.andVVV(FullReg, vecTmp1, vecArray1, vecMask0x80);
            asm.sub(64, refAddress, arr, 16);
            cbnzVector(asm, ElementSize.Byte, vecTmp1, vecTmp1, tmp, false, multibyteFoundTail);
            assert returnValueAssertions();
            asm.lsl(64, ret, ret, 32);
            asm.jmp(end);
        }

        // tail for 4 - 7 bytes
        asm.bind(tailLessThan8);
        asm.adds(64, len, len, 4);
        asm.branchConditionally(ConditionFlag.MI, labelScalarTail);
        asm.fldr(32, vecArray1, AArch64Address.createBaseRegisterOnlyAddress(32, arr));
        if (assumeValid) {
            asm.fldr(32, vecArray2, AArch64Address.createRegisterOffsetAddress(32, arr, len, false));
            asm.add(64, tmp, tmp, 28);
            asm.fldr(128, vecTmp2, AArch64Address.createRegisterOffsetAddress(128, tmp, len, false));
            asm.neon.andVVV(FullReg, vecArray2, vecArray2, vecTmp2);
            asm.jmp(tailLessThan32Continue);
        } else {
            asm.ldr(32, tmp, AArch64Address.createRegisterOffsetAddress(32, arr, len, false));
            asm.sub(64, len, len, 4);
            asm.neg(64, len, len, ShiftType.LSL, 3);
            asm.lsr(64, tmp, tmp, len);
            asm.neon.insXG(Word, vecArray1, 1, tmp);
            asm.jmp(tailLessThan16Continue);
        }

        Label scalarAsciiLoop = new Label();
        Label scalarMultiByteFound = new Label();
        Label scalarMultiByteLoop = new Label();

        asm.bind(labelScalarTail);
        asm.mov(32, len, asRegister(length));
        asm.cbz(64, len, end);
        asm.mov(64, tmp2, zr);

        // scalar ascii loop
        asm.align(PREFERRED_LOOP_ALIGNMENT);
        asm.bind(scalarAsciiLoop);
        asm.ldr(8, tmp, createImmediateAddress(8, AddressingMode.IMMEDIATE_POST_INDEXED, arr, 1));
        asm.tst(64, tmp, 0x80);
        asm.branchConditionally(ConditionFlag.NE, scalarMultiByteFound);
        asm.subs(64, len, len, 1);
        asm.branchConditionally(ConditionFlag.NE, scalarAsciiLoop);
        assert returnValueAssertions();
        asm.lsl(64, ret, ret, 32);
        asm.jmp(end);

        // scalar multibyte loop
        if (assumeValid) {
            asm.align(PREFERRED_LOOP_ALIGNMENT);
            asm.bind(scalarMultiByteLoop);
            asm.ldr(8, tmp, createImmediateAddress(8, AddressingMode.IMMEDIATE_POST_INDEXED, arr, 1));
            asm.bind(scalarMultiByteFound);
            asm.and(64, tmp, tmp, 0xc0);
            asm.compare(64, tmp, 0x80);
            asm.csinc(64, tmp2, tmp2, tmp2, ConditionFlag.NE);
            asm.subs(64, len, len, 1);
            asm.branchConditionally(ConditionFlag.NE, scalarMultiByteLoop);

            asm.sub(64, ret, ret, tmp2);
            asm.mov(tmp, CR_VALID_MULTIBYTE);
            asm.orr(64, ret, tmp, ret, ShiftType.LSL, 32);
            asm.jmp(end);

        } else {
            // assembly implementation of the state machine - based UTF-8 decoder from
            // http://bjoern.hoehrmann.de/utf-8/decoder/dfa/,
            // without the actual codepoint computation.
            Register state = asRegister(temp[2]);
            Register type = asRegister(temp[3]);

            Label scalarMultibyteFoundContinue = new Label();
            asm.bind(scalarMultiByteFound);
            loadDataSectionAddress(crb, asm, tmp2, writeToDataSection(crb, UTF_8_STATE_MACHINE));
            asm.mov(64, state, zr);
            asm.neg(64, ret, ret);
            asm.jmp(scalarMultibyteFoundContinue);

            asm.align(PREFERRED_LOOP_ALIGNMENT);
            asm.bind(scalarMultiByteLoop);
            asm.ldr(8, tmp, createImmediateAddress(8, AddressingMode.IMMEDIATE_POST_INDEXED, arr, 1));
            asm.bind(scalarMultibyteFoundContinue);
            asm.ldr(8, type, createRegisterOffsetAddress(8, tmp2, tmp, false));
            asm.add(64, type, type, state);
            asm.add(64, type, type, 256);
            asm.ldr(8, state, createRegisterOffsetAddress(8, tmp2, type, false));
            asm.and(64, tmp, tmp, 0xc0);
            asm.compare(64, tmp, 0x80);
            asm.csinc(64, ret, ret, ret, ConditionFlag.NE);
            asm.subs(64, len, len, 1);
            asm.branchConditionally(ConditionFlag.NE, scalarMultiByteLoop);

            asm.neg(64, ret, ret);
            asm.mov(tmp, CR_VALID_MULTIBYTE);
            asm.tst(64, state, state);
            asm.csinc(64, tmp, tmp, tmp, ConditionFlag.EQ);
            asm.orr(64, ret, tmp, ret, ShiftType.LSL, 32);
            asm.jmp(end);
        }
    }

    /**
     * Accumulate the number of continuation bytes present in {@code vecArray1} and
     * {@code vecArray2} to {@code vecNCB}.
     */
    private static void utf8CountContinuationBytes(AArch64MacroAssembler asm,
                    Register vecArray1,
                    Register vecArray2,
                    Register vecTmp1,
                    Register vecTmp2,
                    Register vecMask0xC0,
                    Register vecMask0x80,
                    Register vecNCB) {
        asm.neon.andVVV(FullReg, vecTmp1, vecArray1, vecMask0xC0);
        asm.neon.andVVV(FullReg, vecTmp2, vecArray2, vecMask0xC0);
        asm.neon.cmeqVVV(FullReg, ElementSize.Byte, vecTmp1, vecTmp1, vecMask0x80);
        asm.neon.cmeqVVV(FullReg, ElementSize.Byte, vecTmp2, vecTmp2, vecMask0x80);
        asm.neon.usraVVI(FullReg, ElementSize.Byte, vecNCB, vecTmp1, 7);
        asm.neon.usraVVI(FullReg, ElementSize.Byte, vecNCB, vecTmp2, 7);
    }

    private static void setCodepointCountOuterLoopRefAddress(AArch64MacroAssembler asm, Register arr, Register refAddressTail, Register refAddressOuter, int outerBlockSize, int innerBlockSize) {
        assert outerBlockSize > innerBlockSize * 2 : Assertions.errorMessage(outerBlockSize, innerBlockSize);
        asm.adds(64, refAddressOuter, arr, outerBlockSize);
        asm.csel(64, refAddressOuter, refAddressTail, refAddressOuter, ConditionFlag.VS);
        asm.cmp(64, refAddressOuter, refAddressTail);
        // make sure that there is at least one iteration for the inner loop to process when
        // switching over to refAddressTail
        asm.sub(64, refAddressOuter, refAddressOuter, innerBlockSize);
        asm.csel(64, refAddressOuter, refAddressOuter, refAddressTail, ConditionFlag.LO);
    }

    private static Label emitCodepointCountOuterLoopBegin(AArch64MacroAssembler asm, Register arr, Register refAddressTail, Register refAddressOuter, int outerBlockSize, int innerBlockSize) {
        Label loopHead = new Label();
        asm.align(PREFERRED_LOOP_ALIGNMENT);
        asm.bind(loopHead);
        // calculate new reference address
        setCodepointCountOuterLoopRefAddress(asm, arr, refAddressTail, refAddressOuter, outerBlockSize, innerBlockSize);
        return loopHead;
    }

    private static void emitCodepointCountOuterLoopEnd(AArch64MacroAssembler asm, Register tmp, Register ret, Register vecNCB, Register refAddressOuter, Register refAddressTail, Label loopHead) {
        // calculate sum
        asm.neon.uaddlvSV(FullReg, ElementSize.Byte, vecNCB, vecNCB);
        asm.neon.moveFromIndex(DoubleWord, DoubleWord, tmp, vecNCB, 0);
        // reset
        asm.neon.moveVI(FullReg, ElementSize.Byte, vecNCB, 0);
        // subtract sum from total count
        asm.sub(64, ret, ret, tmp);
        // continue loop
        asm.cmp(64, refAddressOuter, refAddressTail);
        asm.branchConditionally(ConditionFlag.NE, loopHead);
    }

    private static Label emitCodepointCountOuterLoopEnd(AArch64MacroAssembler asm, Register tmp, Register ret, Register vecNCB, Register refAddressOuter, Register refAddressTail, Label loopHead,
                    Register arr, int innerLoopBlockSize) {
        // calculate sum
        asm.neon.uaddlvSV(FullReg, ElementSize.Byte, vecNCB, vecNCB);
        // subtract the number of continuation bytes from the total byte length
        asm.neon.moveFromIndex(DoubleWord, DoubleWord, tmp, vecNCB, 0);
        // reset
        asm.neon.moveVI(FullReg, ElementSize.Byte, vecNCB, 0);
        // subtract sum from total count
        asm.sub(64, ret, ret, tmp);

        // break if inner loop already processed the tail
        Label breakLabel = new Label();
        asm.add(64, tmp, refAddressTail, innerLoopBlockSize);
        asm.cmp(64, arr, tmp);
        asm.branchConditionally(ConditionFlag.EQ, breakLabel);

        // continue loop
        asm.cmp(64, refAddressOuter, refAddressTail);
        asm.branchConditionally(ConditionFlag.NE, loopHead);
        return breakLabel;
    }

    /**
     * Implements the operation described in {@link CalcStringAttributesEncoding#UTF_16}.
     */
    private void emitUTF16(CompilationResultBuilder crb, AArch64MacroAssembler asm, Register arr, Register len, Register tmp, Register ret, Label end) {
        assert stride.log2 == 1 : stride;
        Label tailLessThan32 = new Label();
        Label tailLessThan32Continue = new Label();
        Label tailLessThan16 = new Label();
        Label tailLessThan8 = new Label();
        Label tailLessThan4 = new Label();

        Label asciiLoop = new Label();

        Label latinContinue = new Label();
        Label latinLoop = new Label();
        Label latinFoundTail = new Label();

        Label bmpContinue = new Label();
        Label bmpLoop = new Label();
        Label bmpFoundTail = new Label();

        Label surrogateFound = new Label();
        Label surrogateContinue = new Label();
        Label surrogateLoop = new Label();
        Label surrogateFoundTail = new Label();

        Register vecArray1 = asRegister(vectorTemp[0]);
        Register vecArray2 = asRegister(vectorTemp[1]);
        Register vecTmp1 = asRegister(vectorTemp[2]);
        Register vecTmp2 = asRegister(vectorTemp[3]);
        Register vecTmp3 = asRegister(vectorTemp[4]);
        Register vecMask = asRegister(vectorTemp[5]);
        Register vecMaskSurrogates = asRegister(vectorTemp[6]);
        Register vecNSurrogates = asRegister(vectorTemp[7]);

        Register tmp2 = asRegister(temp[1]);

        DataSection.Data maskTail = assumeValid ? createTailANDMask(crb, 16) : createTailShuffleMask(crb, 16);

        asm.mov(32, ret, len);
        // convert length to byte length
        asm.lsl(64, len, len, 1);

        asm.neon.moveVI(FullReg, ElementSize.HalfWord, vecMask, (short) 0xff80);
        asm.neon.moveVI(FullReg, ElementSize.Byte, vecMaskSurrogates, assumeValid ? 0x36 : 0x1b);

        // subtract 32 from len. if the result is negative, jump to branch for less than 32 bytes
        asm.subs(64, len, len, 32);
        asm.branchConditionally(ConditionFlag.MI, tailLessThan32);

        Register refAddress = len;
        asm.add(64, refAddress, arr, len);

        // 32 byte ascii loop
        asm.align(PREFERRED_LOOP_ALIGNMENT);
        asm.bind(asciiLoop);
        asm.fldp(128, vecArray1, vecArray2, createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arr, 32));
        asm.neon.orrVVV(FullReg, vecTmp1, vecArray1, vecArray2);
        asm.neon.cmtstVVV(FullReg, HalfWord, vecTmp2, vecTmp1, vecMask);
        cbnzVector(asm, HalfWord, vecTmp2, vecTmp2, tmp, true, latinContinue);
        asm.cmp(64, arr, refAddress);
        asm.branchConditionally(ConditionFlag.LO, asciiLoop);

        // 32 byte ascii loop tail
        asm.mov(64, arr, refAddress);
        asm.fldp(128, vecArray1, vecArray2, createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arr, 32));
        asm.bind(tailLessThan32Continue);
        asm.neon.orrVVV(FullReg, vecTmp1, vecArray1, vecArray2);
        asm.neon.cmtstVVV(FullReg, HalfWord, vecTmp2, vecTmp1, vecMask);
        cbnzVector(asm, HalfWord, vecTmp2, vecTmp2, tmp, true, latinFoundTail);
        assert returnValueAssertions();
        asm.lsl(64, ret, ret, 32);
        asm.jmp(end);

        // 32 byte latin1 loop
        asm.align(PREFERRED_LOOP_ALIGNMENT);
        asm.bind(latinLoop);
        asm.fldp(128, vecArray1, vecArray2, createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arr, 32));
        asm.bind(latinContinue);
        asm.neon.uzp2VVV(FullReg, ElementSize.Byte, vecArray2, vecArray1, vecArray2);
        cbnzVector(asm, ElementSize.Byte, vecTmp1, vecArray2, tmp, false, bmpContinue);
        asm.cmp(64, arr, refAddress);
        asm.branchConditionally(ConditionFlag.LO, latinLoop);

        // 32 byte latin1 loop tail
        asm.mov(64, arr, refAddress);
        asm.fldp(128, vecArray1, vecArray2, createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arr, 32));
        asm.bind(latinFoundTail);
        asm.neon.uzp2VVV(FullReg, ElementSize.Byte, vecArray2, vecArray1, vecArray2);
        cbnzVector(asm, ElementSize.Byte, vecTmp1, vecArray2, tmp, false, bmpFoundTail);
        asm.mov(tmp, CR_8BIT);
        asm.orr(64, ret, tmp, ret, ShiftType.LSL, 32);
        asm.jmp(end);

        // 32 byte bmp loop
        asm.align(PREFERRED_LOOP_ALIGNMENT);
        asm.bind(bmpLoop);
        asm.fldp(128, vecArray1, vecArray2, createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arr, 32));
        asm.neon.uzp2VVV(FullReg, ElementSize.Byte, vecArray2, vecArray1, vecArray2);
        asm.bind(bmpContinue);
        utf16MatchSurrogates(asm, vecArray2, vecTmp1, vecMaskSurrogates);
        cbnzVector(asm, ElementSize.Byte, vecTmp2, vecTmp1, tmp, true, surrogateFound);
        asm.cmp(64, arr, refAddress);
        asm.branchConditionally(ConditionFlag.LO, bmpLoop);

        // 32 byte bmp loop tail
        asm.mov(64, arr, refAddress);
        asm.fldp(128, vecArray1, vecArray2, createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arr, 32));
        asm.neon.uzp2VVV(FullReg, ElementSize.Byte, vecArray2, vecArray1, vecArray2);
        asm.bind(bmpFoundTail);
        utf16MatchSurrogates(asm, vecArray2, vecTmp1, vecMaskSurrogates);
        cbnzVector(asm, ElementSize.Byte, vecTmp2, vecTmp1, tmp, true, surrogateFoundTail);
        asm.mov(tmp, CR_16BIT);
        asm.orr(64, ret, tmp, ret, ShiftType.LSL, 32);
        asm.jmp(end);

        if (assumeValid) {

            asm.bind(surrogateFoundTail);
            utf16SubtractMatched(asm, tmp, ret, vecTmp1);
            asm.mov(tmp, CR_VALID_MULTIBYTE);
            asm.orr(64, ret, tmp, ret, ShiftType.LSL, 32);
            asm.jmp(end);

            asm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
            asm.bind(surrogateFound);
            setCodepointCountOuterLoopRefAddress(asm, arr, refAddress, tmp2, 4096, 32);
            asm.neon.moveVI(FullReg, ElementSize.Byte, vecNSurrogates, 0);
            asm.jmp(surrogateContinue);

            // --- begin of outer loop ---

            Label surrogateOuterLoop = emitCodepointCountOuterLoopBegin(asm, arr, refAddress, tmp2, 4096, 32);

            // --- begin of inner loop ---
            // surrogate loop: surrogates have been found, calculate the codepoint length
            // (assuming the string is encoded correctly)

            // 32 byte surrogate loop
            asm.align(PREFERRED_LOOP_ALIGNMENT);
            asm.bind(surrogateLoop);
            asm.fldp(128, vecArray1, vecArray2, createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arr, 32));
            // extract upper bytes
            asm.neon.uzp2VVV(FullReg, ElementSize.Byte, vecArray2, vecArray1, vecArray2);
            // identify high surrogates ((upper byte >> 2) == 0x36)
            asm.neon.ushrVVI(FullReg, ElementSize.Byte, vecTmp1, vecArray2, 2);
            asm.neon.cmeqVVV(FullReg, ElementSize.Byte, vecTmp1, vecTmp1, vecMaskSurrogates);
            asm.bind(surrogateContinue);
            // add number of high surrogates to accumulator
            asm.neon.usraVVI(FullReg, ElementSize.Byte, vecNSurrogates, vecTmp1, 7);
            asm.cmp(64, arr, tmp2);
            asm.branchConditionally(ConditionFlag.LS, surrogateLoop);

            // --- end of inner loop ---

            // subtract total number of high surrogates from char length
            emitCodepointCountOuterLoopEnd(asm, tmp, ret, vecNSurrogates, tmp2, refAddress, surrogateOuterLoop);

            // --- end of outer loop ---

            // 32 byte surrogate loop tail
            Label skipTail = new Label();
            asm.mov(64, arr, refAddress);
            asm.and(32, len, asRegister(length), 15);
            asm.cbz(64, len, skipTail);
            asm.fldp(128, vecArray1, vecArray2, createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arr, 32));
            asm.neon.uzp2VVV(FullReg, ElementSize.Byte, vecArray2, vecArray1, vecArray2);
            loadDataSectionAddress(crb, asm, tmp, maskTail);
            asm.fldr(128, vecTmp1, AArch64Address.createRegisterOffsetAddress(128, tmp, len, false));
            asm.neon.andVVV(FullReg, vecArray2, vecArray2, vecTmp1);
            utf16MatchSurrogates(asm, vecArray2, vecTmp1, vecMaskSurrogates);
            utf16SubtractMatched(asm, tmp, ret, vecTmp1);
            asm.bind(skipTail);
            asm.mov(tmp, CR_VALID_MULTIBYTE);
            asm.orr(64, ret, tmp, ret, ShiftType.LSL, 32);
            asm.jmp(end);
        } else {

            Register vecPrev = vecTmp2;
            Register vecResult = vecTmp3;

            asm.bind(surrogateFoundTail);
            asm.add(64, arr, asRegister(array), asRegister(offset));
            asm.sub(64, refAddress, arr, 32);

            // surrogate loop: surrogates have been found, check encoding validity and calculate the
            // codepoint length

            asm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
            asm.bind(surrogateFound);
            // high surrogate mask
            asm.neon.moveVI(FullReg, ElementSize.Byte, vecMaskSurrogates, 0x36);
            // low surrogate mask
            asm.neon.moveVI(FullReg, ElementSize.Byte, vecMask, 0x37);
            // clear vecPrev and vecResult
            asm.neon.moveVI(FullReg, ElementSize.Byte, vecPrev, 0);
            asm.neon.moveVI(FullReg, ElementSize.Byte, vecResult, 0);
            asm.neon.moveVI(FullReg, ElementSize.Byte, vecNSurrogates, 0);
            setCodepointCountOuterLoopRefAddress(asm, arr, refAddress, tmp2, 4096, 32);
            asm.jmp(surrogateContinue);

            // --- begin of outer loop ---
            // nested loop avoids uaddlvSV - see utf8 variant

            Label surrogateOuterLoop = emitCodepointCountOuterLoopBegin(asm, arr, refAddress, tmp2, 4096, 32);

            // --- begin of inner loop ---

            // 32 byte surrogate loop
            asm.align(PREFERRED_LOOP_ALIGNMENT);
            asm.bind(surrogateLoop);
            asm.fldp(128, vecArray1, vecArray2, createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arr, 32));
            asm.neon.uzp2VVV(FullReg, ElementSize.Byte, vecArray2, vecArray1, vecArray2);
            asm.bind(surrogateContinue);
            // set vecArray1 = vecPrev[15]:vecArray2[1:16]
            asm.neon.extVVV(FullReg, vecArray1, vecPrev, vecArray2, 15);
            asm.neon.moveVV(FullReg, vecPrev, vecArray2);
            asm.neon.ushrVVI(FullReg, ElementSize.Byte, vecArray1, vecArray1, 2);
            asm.neon.ushrVVI(FullReg, ElementSize.Byte, vecArray2, vecArray2, 2);
            // identify high surrogates at current index - 1 ((high bytes >> 2) == 0x36)
            asm.neon.cmeqVVV(FullReg, ElementSize.Byte, vecArray1, vecArray1, vecMaskSurrogates);
            // identify low surrogates at current index ((high bytes >> 2) == 0x37)
            asm.neon.cmeqVVV(FullReg, ElementSize.Byte, vecArray2, vecArray2, vecMask);
            // identify the number of correctly encoded surrogate pairs
            asm.neon.andVVV(FullReg, vecTmp1, vecArray1, vecArray2);
            // EOR the high surrogates identified at current index - 1, with low surrogates
            // identified at current index; if the result is zero, the current block is
            // encoded correctly
            asm.neon.eorVVV(FullReg, vecArray1, vecArray1, vecArray2);
            // add number of correct surrogate pairs to accumulator
            asm.neon.usraVVI(FullReg, ElementSize.Byte, vecNSurrogates, vecTmp1, 7);
            // merge the validation result into the final result
            asm.neon.orrVVV(FullReg, vecResult, vecResult, vecArray1);
            asm.cmp(64, arr, tmp2);
            asm.branchConditionally(ConditionFlag.LS, surrogateLoop);

            // --- end of inner loop ---

            Label done = emitCodepointCountOuterLoopEnd(asm, tmp, ret, vecNSurrogates, tmp2, refAddress, surrogateOuterLoop, arr, 32);

            // --- end of outer loop ---

            // 32 byte surrogate loop tail
            asm.mov(64, arr, refAddress);
            asm.fldp(128, vecArray1, vecArray2, createImmediateAddress(128, AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, arr, 32));
            asm.neon.uzp2VVV(FullReg, ElementSize.Byte, vecArray2, vecArray1, vecArray2);
            loadDataSectionAddress(crb, asm, tmp, maskTail);
            asm.and(32, tmp2, asRegister(length), 15);
            asm.add(64, tmp, tmp, 16);
            asm.neg(64, tmp2, tmp2);
            asm.fldr(128, vecTmp1, AArch64Address.createRegisterOffsetAddress(128, tmp, tmp2, false));
            asm.mov(64, tmp2, refAddress);
            asm.neon.tblVVV(FullReg, vecArray2, vecArray2, vecTmp1);
            asm.jmp(surrogateContinue);

            asm.bind(done);
            // corner case: check if last char is a high surrogate, since this is not detected by
            // the main loop
            asm.neon.dupVX(FullReg, ElementSize.Byte, vecPrev, vecPrev, 15);
            asm.neon.ushrVVI(FullReg, ElementSize.Byte, vecPrev, vecPrev, 2);
            asm.neon.cmeqVVV(FullReg, ElementSize.Byte, vecPrev, vecPrev, vecMaskSurrogates);
            asm.neon.orrVVV(FullReg, vecResult, vecResult, vecPrev);
            asm.mov(tmp, CR_VALID_MULTIBYTE);
            cmpZeroVector(asm, vecResult, vecResult, len);
            assert returnValueAssertions();
            asm.csinc(64, tmp, tmp, tmp, ConditionFlag.EQ);
            asm.orr(64, ret, tmp, ret, ShiftType.LSL, 32);
            asm.jmp(end);
        }

        // tail for 16 - 31 bytes
        asm.bind(tailLessThan32);
        if (assumeValid) {
            loadDataSectionAddress(crb, asm, tmp, createTailANDMask(crb, 32));
        }
        // len is original len - 32 and guaranteed negative. add 16. if result is still negative,
        // jump to branch for less than 16 bytes
        asm.adds(64, len, len, 16);
        asm.branchConditionally(ConditionFlag.MI, tailLessThan16);
        asm.fldr(128, vecArray1, AArch64Address.createBaseRegisterOnlyAddress(128, arr));
        asm.fldr(128, vecArray2, AArch64Address.createRegisterOffsetAddress(128, arr, len, false));
        if (assumeValid) {
            asm.add(64, tmp, tmp, 16);
            asm.fldr(128, vecTmp2, AArch64Address.createRegisterOffsetAddress(128, tmp, len, false));
            asm.neon.andVVV(FullReg, vecArray2, vecArray2, vecTmp2);
        } else {
            loadDataSectionAddress(crb, asm, tmp, maskTail);
            asm.add(64, tmp, tmp, 16);
            asm.neg(64, len, len);
            asm.fldr(128, vecTmp2, AArch64Address.createRegisterOffsetAddress(128, tmp, len, false));
            asm.neon.tblVVV(FullReg, vecArray2, vecArray2, vecTmp2);
        }
        asm.jmp(tailLessThan32Continue);

        // tail for 8 - 15 bytes
        asm.bind(tailLessThan16);
        asm.adds(64, len, len, 8);
        asm.branchConditionally(ConditionFlag.MI, tailLessThan8);
        asm.fldr(64, vecArray1, AArch64Address.createBaseRegisterOnlyAddress(64, arr));
        asm.fldr(64, vecArray2, AArch64Address.createRegisterOffsetAddress(64, arr, len, false));
        if (assumeValid) {
            asm.add(64, tmp, tmp, 24);
            asm.fldr(128, vecTmp2, AArch64Address.createRegisterOffsetAddress(128, tmp, len, false));
            asm.neon.andVVV(FullReg, vecArray2, vecArray2, vecTmp2);
        } else {
            loadDataSectionAddress(crb, asm, tmp, createTailShuffleMask(crb, 8));
            asm.add(64, tmp, tmp, 8);
            asm.neg(64, len, len);
            asm.fldr(128, vecTmp2, AArch64Address.createRegisterOffsetAddress(128, tmp, len, false));
            asm.neon.tblVVV(FullReg, vecArray2, vecArray2, vecTmp2);
            asm.neon.insXX(DoubleWord, vecArray1, 1, vecArray2, 0);
            asm.neon.moveVI(FullReg, ElementSize.Byte, vecArray2, 0);
        }
        asm.jmp(tailLessThan32Continue);

        // tail for 4 - 7 bytes
        asm.bind(tailLessThan8);
        asm.adds(64, len, len, 4);
        asm.branchConditionally(ConditionFlag.MI, tailLessThan4);
        asm.fldr(32, vecArray1, AArch64Address.createBaseRegisterOnlyAddress(32, arr));
        if (assumeValid) {
            asm.fldr(32, vecArray2, AArch64Address.createRegisterOffsetAddress(32, arr, len, false));
            asm.add(64, tmp, tmp, 28);
            asm.fldr(128, vecTmp2, AArch64Address.createRegisterOffsetAddress(128, tmp, len, false));
            asm.neon.andVVV(FullReg, vecArray2, vecArray2, vecTmp2);
        } else {
            asm.ldr(32, tmp, AArch64Address.createRegisterOffsetAddress(32, arr, len, false));
            asm.sub(64, len, len, 4);
            asm.neg(64, len, len, ShiftType.LSL, 3);
            asm.lsr(64, tmp, tmp, len);
            asm.neon.insXG(Word, vecArray1, 1, tmp);
            asm.neon.moveVI(FullReg, ElementSize.Byte, vecArray2, 0);
        }
        asm.jmp(tailLessThan32Continue);

        // tail for 0 - 3 bytes
        asm.bind(tailLessThan4);
        asm.adds(64, len, len, 2);
        asm.branchConditionally(ConditionFlag.MI, end);
        asm.fldr(16, vecArray1, AArch64Address.createBaseRegisterOnlyAddress(16, arr));
        asm.neon.moveVI(FullReg, ElementSize.Byte, vecArray2, 0);
        asm.jmp(tailLessThan32Continue);
    }

    private static void utf16SubtractMatched(AArch64MacroAssembler asm, Register tmp, Register ret, Register vecTmp1) {
        asm.neon.ushrVVI(FullReg, ElementSize.Byte, vecTmp1, vecTmp1, 7);
        asm.neon.addvSV(FullReg, ElementSize.Byte, vecTmp1, vecTmp1);
        asm.neon.moveFromIndex(DoubleWord, DoubleWord, tmp, vecTmp1, 0);
        asm.sub(64, ret, ret, tmp);
    }

    private void utf16MatchSurrogates(AArch64MacroAssembler asm, Register vecArray2, Register vecTmp1, Register vecMaskSurrogates) {
        // identify surrogates by checking the upper 6 or 5 bits
        asm.neon.ushrVVI(FullReg, ElementSize.Byte, vecTmp1, vecArray2, assumeValid ? 2 : 3);
        // identify surrogates (if assume valid: (codepoint >> 11) == 0x1b)
        // (otherwise: (codepoint >> 10) == 0x36)
        asm.neon.cmeqVVV(FullReg, ElementSize.Byte, vecTmp1, vecTmp1, vecMaskSurrogates);
    }

    /**
     * Implements the operation described in {@link CalcStringAttributesEncoding#UTF_32}.
     */
    private void emitUTF32(AArch64MacroAssembler asm, Register arr, Register len, Register tmp, Register ret, Label end) {
        assert stride.log2 == 2 : stride;
        Label tailLessThan64 = new Label();
        Label tailLessThan32 = new Label();
        Label tailLessThan64Continue = new Label();
        Label tailLessThan16 = new Label();
        Label tailLessThan8 = new Label();

        Label asciiLoop = new Label();

        Label latinFound = new Label();
        Label latinContinue = new Label();
        Label latinLoop = new Label();
        Label latinFoundTail = new Label();

        Label bmpFound = new Label();
        Label bmpContinue = new Label();
        Label bmpLoop = new Label();
        Label bmpFoundTail = new Label();

        Label astralContinue = new Label();
        Label astralLoop = new Label();
        Label astralFoundTail = new Label();

        Label returnBroken = new Label();

        Register vecArray1 = asRegister(vectorTemp[0]);
        Register vecArray2 = asRegister(vectorTemp[1]);
        Register vecArray3 = asRegister(vectorTemp[2]);
        Register vecArray4 = asRegister(vectorTemp[3]);
        Register vecTmp1 = asRegister(vectorTemp[4]);
        Register vecTmp2 = asRegister(vectorTemp[5]);
        Register vecTmp3 = asRegister(vectorTemp[6]);
        Register vecTmp4 = asRegister(vectorTemp[7]);
        Register vecMask = asRegister(vectorTemp[8]);
        Register vecMaskBroken = asRegister(vectorTemp[9]);
        Register vecMaskOutOfRange = asRegister(vectorTemp[10]);

        // convert length to byte length
        asm.lsl(64, len, len, 2);

        asm.neon.moveVI(FullReg, Word, vecMask, 0xffffff80);
        asm.neon.moveVI(FullReg, Word, vecMaskBroken, 0x1b);
        asm.neon.moveVI(FullReg, Word, vecMaskOutOfRange, 0x10ffff);

        // subtract 64 from len. if the result is negative, jump to branch for less than 64 bytes
        asm.subs(64, len, len, 64);
        asm.branchConditionally(ConditionFlag.MI, tailLessThan64);

        Register refAddress = len;
        asm.add(64, refAddress, arr, len);

        // 64 byte ascii loop
        asm.align(PREFERRED_LOOP_ALIGNMENT);
        asm.bind(asciiLoop);
        asm.neon.ld1MultipleVVVV(FullReg, ElementSize.Byte, vecArray1, vecArray2, vecArray3, vecArray4,
                        createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, FullReg, ElementSize.Byte, arr, 64));
        asm.neon.orrVVV(FullReg, vecTmp1, vecArray1, vecArray2);
        asm.neon.orrVVV(FullReg, vecTmp2, vecArray3, vecArray4);
        asm.neon.orrVVV(FullReg, vecTmp1, vecTmp1, vecTmp2);
        asm.neon.cmtstVVV(FullReg, Word, vecTmp2, vecTmp1, vecMask);
        cbnzVector(asm, Word, vecTmp2, vecTmp2, tmp, true, latinFound);
        asm.cmp(64, arr, refAddress);
        asm.branchConditionally(ConditionFlag.LO, asciiLoop);

        // 64 byte ascii loop tail
        asm.mov(64, arr, refAddress);
        asm.neon.ld1MultipleVVVV(FullReg, ElementSize.Byte, vecArray1, vecArray2, vecArray3, vecArray4,
                        createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, FullReg, ElementSize.Byte, arr, 64));
        asm.neon.orrVVV(FullReg, vecTmp1, vecArray1, vecArray2);
        asm.neon.orrVVV(FullReg, vecTmp2, vecArray3, vecArray4);
        asm.neon.orrVVV(FullReg, vecTmp1, vecTmp1, vecTmp2);
        asm.bind(tailLessThan64Continue);
        asm.neon.cmtstVVV(FullReg, Word, vecTmp2, vecTmp1, vecMask);
        asm.neon.shlVVI(FullReg, Word, vecMask, vecMask, 1);
        cbnzVector(asm, Word, vecTmp2, vecTmp2, tmp, true, latinFoundTail);
        asm.mov(ret, CR_7BIT);
        asm.jmp(end);

        asm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
        asm.bind(latinFound);
        // convert mask to 0xffffff00
        asm.neon.shlVVI(FullReg, Word, vecMask, vecMask, 1);
        asm.jmp(latinContinue);

        // 64 byte latin1 loop
        asm.align(PREFERRED_LOOP_ALIGNMENT);
        asm.bind(latinLoop);
        asm.neon.ld1MultipleVVVV(FullReg, ElementSize.Byte, vecArray1, vecArray2, vecArray3, vecArray4,
                        createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, FullReg, ElementSize.Byte, arr, 64));
        asm.neon.orrVVV(FullReg, vecTmp1, vecArray1, vecArray2);
        asm.neon.orrVVV(FullReg, vecTmp2, vecArray3, vecArray4);
        asm.neon.orrVVV(FullReg, vecTmp1, vecTmp1, vecTmp2);
        asm.bind(latinContinue);
        asm.neon.cmtstVVV(FullReg, Word, vecTmp2, vecTmp1, vecMask);
        cbnzVector(asm, Word, vecTmp2, vecTmp2, tmp, true, bmpFound);
        asm.cmp(64, arr, refAddress);
        asm.branchConditionally(ConditionFlag.LO, latinLoop);

        // 64 byte latin1 loop tail
        asm.mov(64, arr, refAddress);
        asm.neon.ld1MultipleVVVV(FullReg, ElementSize.Byte, vecArray1, vecArray2, vecArray3, vecArray4,
                        createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, FullReg, ElementSize.Byte, arr, 64));
        asm.neon.orrVVV(FullReg, vecTmp1, vecArray1, vecArray2);
        asm.neon.orrVVV(FullReg, vecTmp2, vecArray3, vecArray4);
        asm.neon.orrVVV(FullReg, vecTmp1, vecTmp1, vecTmp2);
        asm.bind(latinFoundTail);
        asm.neon.cmtstVVV(FullReg, Word, vecTmp2, vecTmp1, vecMask);
        asm.neon.shlVVI(FullReg, Word, vecMask, vecMask, 8);
        cbnzVector(asm, Word, vecTmp2, vecTmp2, tmp, true, bmpFoundTail);
        asm.mov(ret, CR_8BIT);
        asm.jmp(end);

        asm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
        asm.bind(bmpFound);
        // convert mask to 0xffff0000
        asm.neon.shlVVI(FullReg, Word, vecMask, vecMask, 8);
        asm.mov(ret, 0);
        asm.jmp(bmpContinue);

        // 64 byte bmp loop
        asm.align(PREFERRED_LOOP_ALIGNMENT);
        asm.bind(bmpLoop);
        asm.neon.ld1MultipleVVVV(FullReg, ElementSize.Byte, vecArray1, vecArray2, vecArray3, vecArray4,
                        createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, FullReg, ElementSize.Byte, arr, 64));
        asm.neon.orrVVV(FullReg, vecTmp1, vecArray1, vecArray2);
        asm.neon.orrVVV(FullReg, vecTmp2, vecArray3, vecArray4);
        asm.neon.orrVVV(FullReg, vecTmp1, vecTmp1, vecTmp2);
        asm.bind(bmpContinue);
        asm.neon.cmtstVVV(FullReg, Word, vecTmp2, vecTmp1, vecMask);
        cbnzVector(asm, Word, vecTmp2, vecTmp2, tmp, true, astralContinue);
        utf32CheckInvalidBMP(asm, vecArray1, vecArray2, vecArray3, vecArray4, vecTmp1, vecTmp2, vecTmp3, vecTmp4, vecMaskBroken, tmp, returnBroken);
        asm.cmp(64, arr, refAddress);
        asm.branchConditionally(ConditionFlag.LO, bmpLoop);

        // 64 byte bmp loop tail
        asm.mov(64, arr, refAddress);
        asm.neon.ld1MultipleVVVV(FullReg, ElementSize.Byte, vecArray1, vecArray2, vecArray3, vecArray4,
                        createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, FullReg, ElementSize.Byte, arr, 64));
        asm.neon.orrVVV(FullReg, vecTmp1, vecArray1, vecArray2);
        asm.neon.orrVVV(FullReg, vecTmp2, vecArray3, vecArray4);
        asm.neon.orrVVV(FullReg, vecTmp1, vecTmp1, vecTmp2);
        asm.bind(bmpFoundTail);
        asm.neon.cmtstVVV(FullReg, Word, vecTmp2, vecTmp1, vecMask);
        cbnzVector(asm, Word, vecTmp2, vecTmp2, tmp, true, astralFoundTail);
        utf32CheckInvalidBMP(asm, vecArray1, vecArray2, vecArray3, vecArray4, vecTmp1, vecTmp2, vecTmp3, vecTmp4, vecMaskBroken, tmp, returnBroken);
        asm.mov(ret, CR_16BIT);
        asm.jmp(end);

        // 64 byte astral loop
        asm.align(PREFERRED_LOOP_ALIGNMENT);
        asm.bind(astralLoop);
        asm.neon.ld1MultipleVVVV(FullReg, ElementSize.Byte, vecArray1, vecArray2, vecArray3, vecArray4,
                        createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, FullReg, ElementSize.Byte, arr, 64));
        asm.bind(astralContinue);
        utf32CheckInvalid(asm, vecArray1, vecArray2, vecArray3, vecArray4, vecTmp1, vecTmp2, vecTmp3, vecTmp4, vecMaskBroken, vecMaskOutOfRange, tmp, returnBroken);
        asm.cmp(64, arr, refAddress);
        asm.branchConditionally(ConditionFlag.LO, astralLoop);

        // 64 byte astral loop tail
        asm.mov(64, arr, refAddress);
        asm.neon.ld1MultipleVVVV(FullReg, ElementSize.Byte, vecArray1, vecArray2, vecArray3, vecArray4,
                        createStructureImmediatePostIndexAddress(LD1_MULTIPLE_4R, FullReg, ElementSize.Byte, arr, 64));
        asm.bind(astralFoundTail);
        utf32CheckInvalid(asm, vecArray1, vecArray2, vecArray3, vecArray4, vecTmp1, vecTmp2, vecTmp3, vecTmp4, vecMaskBroken, vecMaskOutOfRange, tmp, returnBroken);
        asm.mov(ret, CR_VALID);
        asm.jmp(end);

        // tail for 32 - 63 bytes
        asm.bind(tailLessThan64);
        // len is original len - 64 and guaranteed negative. add 32. if result is still negative,
        // jump to branch for less than 32 bytes
        asm.adds(64, len, len, 32);
        asm.branchConditionally(ConditionFlag.MI, tailLessThan32);
        asm.fldp(128, vecArray1, vecArray2, AArch64Address.createPairBaseRegisterOnlyAddress(128, arr));
        asm.add(64, arr, arr, len);
        asm.fldp(128, vecArray3, vecArray4, AArch64Address.createPairBaseRegisterOnlyAddress(128, arr));
        asm.neon.orrVVV(FullReg, vecTmp1, vecArray1, vecArray2);
        asm.neon.orrVVV(FullReg, vecTmp2, vecArray3, vecArray4);
        asm.neon.orrVVV(FullReg, vecTmp1, vecTmp1, vecTmp2);
        asm.jmp(tailLessThan64Continue);

        // tail for 16 - 31 bytes
        asm.bind(tailLessThan32);
        // len is original len - 32 and guaranteed negative. add 16. if result is still negative,
        // jump to branch for less than 16 bytes
        asm.adds(64, len, len, 16);
        asm.branchConditionally(ConditionFlag.MI, tailLessThan16);
        asm.fldr(128, vecArray1, AArch64Address.createBaseRegisterOnlyAddress(128, arr));
        asm.fldr(128, vecArray2, AArch64Address.createRegisterOffsetAddress(128, arr, len, false));
        asm.neon.moveVI(FullReg, ElementSize.Byte, vecArray3, 0);
        asm.neon.moveVI(FullReg, ElementSize.Byte, vecArray4, 0);
        asm.neon.orrVVV(FullReg, vecTmp1, vecArray1, vecArray2);
        asm.jmp(tailLessThan64Continue);

        // tail for 8 - 15 bytes
        asm.bind(tailLessThan16);
        asm.adds(64, len, len, 8);
        asm.branchConditionally(ConditionFlag.MI, tailLessThan8);
        asm.fldr(64, vecArray1, AArch64Address.createBaseRegisterOnlyAddress(64, arr));
        asm.fldr(64, vecArray2, AArch64Address.createRegisterOffsetAddress(64, arr, len, false));
        asm.neon.moveVI(FullReg, ElementSize.Byte, vecArray3, 0);
        asm.neon.moveVI(FullReg, ElementSize.Byte, vecArray4, 0);
        asm.neon.orrVVV(FullReg, vecTmp1, vecArray1, vecArray2);
        asm.jmp(tailLessThan64Continue);

        // tail for 4 - 7 bytes
        asm.bind(tailLessThan8);
        asm.mov(64, ret, zr); // ret = CR_7BIT
        asm.adds(64, len, len, 4);
        asm.branchConditionally(ConditionFlag.MI, end);
        asm.fldr(32, vecArray1, AArch64Address.createBaseRegisterOnlyAddress(32, arr));
        asm.neon.moveVI(FullReg, ElementSize.Byte, vecArray2, 0);
        asm.neon.moveVI(FullReg, ElementSize.Byte, vecArray3, 0);
        asm.neon.moveVI(FullReg, ElementSize.Byte, vecArray4, 0);
        asm.neon.moveVV(FullReg, vecTmp1, vecArray1);
        asm.jmp(tailLessThan64Continue);

        asm.align(PREFERRED_BRANCH_TARGET_ALIGNMENT);
        asm.bind(returnBroken);
        asm.mov(ret, CR_BROKEN);
    }

    private static void utf32CheckInvalid(AArch64MacroAssembler asm,
                    Register vecArray1,
                    Register vecArray2,
                    Register vecArray3,
                    Register vecArray4,
                    Register vecTmp1,
                    Register vecTmp2,
                    Register vecTmp3,
                    Register vecTmp4,
                    Register vecMaskBroken,
                    Register vecMaskOutOfRange,
                    Register tmp,
                    Label returnBroken) {
        // right-shift string contents (copy) by 11
        asm.neon.ushrVVI(FullReg, Word, vecTmp1, vecArray1, 11);
        asm.neon.ushrVVI(FullReg, Word, vecTmp2, vecArray2, 11);
        asm.neon.ushrVVI(FullReg, Word, vecTmp3, vecArray3, 11);
        asm.neon.ushrVVI(FullReg, Word, vecTmp4, vecArray4, 11);
        // identify codepoints larger than 0x10ffff
        asm.neon.umaxVVV(FullReg, Word, vecArray1, vecArray1, vecArray2);
        asm.neon.umaxVVV(FullReg, Word, vecArray3, vecArray3, vecArray4);
        asm.neon.umaxVVV(FullReg, Word, vecArray1, vecArray1, vecArray3);
        asm.neon.cmhiVVV(FullReg, Word, vecArray1, vecArray1, vecMaskOutOfRange);
        // identify codepoints in the forbidden UTF-16 surrogate range [0xd800-0xdfff]
        // ((codepoint >> 11) == 0x1b)
        asm.neon.cmeqVVV(FullReg, Word, vecTmp1, vecTmp1, vecMaskBroken);
        asm.neon.cmeqVVV(FullReg, Word, vecTmp2, vecTmp2, vecMaskBroken);
        asm.neon.cmeqVVV(FullReg, Word, vecTmp3, vecTmp3, vecMaskBroken);
        asm.neon.cmeqVVV(FullReg, Word, vecTmp4, vecTmp4, vecMaskBroken);
        // merge comparison results
        asm.neon.orrVVV(FullReg, vecTmp1, vecTmp1, vecTmp2);
        asm.neon.orrVVV(FullReg, vecTmp3, vecTmp3, vecTmp4);
        asm.neon.orrVVV(FullReg, vecTmp1, vecTmp1, vecTmp3);
        asm.neon.orrVVV(FullReg, vecArray1, vecArray1, vecTmp1);
        // check if any invalid character was present
        // return CR_BROKEN if any invalid character was present
        cbnzVector(asm, Word, vecArray1, vecArray1, tmp, true, returnBroken);
    }

    private static void utf32CheckInvalidBMP(AArch64MacroAssembler asm,
                    Register vecArray1,
                    Register vecArray2,
                    Register vecArray3,
                    Register vecArray4,
                    Register vecTmp1,
                    Register vecTmp2,
                    Register vecTmp3,
                    Register vecTmp4,
                    Register vecMaskBroken,
                    Register tmp,
                    Label returnBroken) {
        // right-shift string contents (copy) by 11
        asm.neon.ushrVVI(FullReg, Word, vecTmp1, vecArray1, 11);
        asm.neon.ushrVVI(FullReg, Word, vecTmp2, vecArray2, 11);
        asm.neon.ushrVVI(FullReg, Word, vecTmp3, vecArray3, 11);
        asm.neon.ushrVVI(FullReg, Word, vecTmp4, vecArray4, 11);
        // identify codepoints in the forbidden UTF-16 surrogate range [0xd800-0xdfff]
        // ((codepoint >> 11) == 0x1b)
        asm.neon.cmeqVVV(FullReg, Word, vecTmp1, vecTmp1, vecMaskBroken);
        asm.neon.cmeqVVV(FullReg, Word, vecTmp2, vecTmp2, vecMaskBroken);
        asm.neon.cmeqVVV(FullReg, Word, vecTmp3, vecTmp3, vecMaskBroken);
        asm.neon.cmeqVVV(FullReg, Word, vecTmp4, vecTmp4, vecMaskBroken);
        // merge comparison results
        asm.neon.orrVVV(FullReg, vecTmp1, vecTmp1, vecTmp2);
        asm.neon.orrVVV(FullReg, vecTmp3, vecTmp3, vecTmp4);
        asm.neon.orrVVV(FullReg, vecTmp1, vecTmp1, vecTmp3);
        // check if any invalid character was present
        // return CR_BROKEN if any invalid character was present
        cbnzVector(asm, Word, vecTmp1, vecTmp1, tmp, true, returnBroken);
    }

    /**
     * This op uses {@code cset} and {@code csinc} to avoid branches for return value calculation,
     * but for this to work, the values in {@link CalcStringAttributesEncoding} must not change.
     */
    @SuppressWarnings("all")
    private static boolean returnValueAssertions() {
        assert CR_7BIT == 0;
        assert CR_8BIT == 1;
        assert CR_16BIT == 2;
        assert CR_VALID == 3;
        assert CR_BROKEN == 4;
        assert CR_BROKEN_MULTIBYTE == CR_VALID_MULTIBYTE + 1;
        return true;
    }
}
