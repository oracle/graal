/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexMoveOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRMOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRVMOp;
import org.graalvm.compiler.asm.amd64.AMD64BaseAssembler;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.asm.amd64.AVXKind;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

/**
 */
@Opcode("AMD64_STRING_INDEX_OF_STRING")
public final class AMD64StringIndexOfStringOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64StringIndexOfStringOp> TYPE = LIRInstructionClass.create(AMD64StringIndexOfStringOp.class);

    public static final class Options {
        // @formatter:off
        @Option(help = "Search for potential matches in java.lang.String#indexOf(String) by searching for occurrences " +
                        "of the first two characters instead of the first single character. Example: when searching fo" +
                        "r \\\"asdf\\\", String#indexOf(String) will search for occurrences of \\\"as\\\" and compare " +
                        "any matches to the full string. If this option is disabled, String#indexOf(String) will searc" +
                        "h for \\\"a\\\" instead. Searching for a single character is faster than searching for two co" +
                        "nsecutive characters, but also leads to more false positives.", type = OptionType.Expert)
        public static final OptionKey<Boolean> IndexOfStringUseTwoCharPrefix = new OptionKey<>(true);
        // @formatter:on
    }

    private final JavaKind kind;
    private final int vmPageSize;
    private final int constantNeedleLength;
    private final int arrayIndexScale;
    private final AMD64Kind vectorKind;

    private final boolean useIndexOfTwoCharPrefix;

    @Def({REG}) protected Value resultValue;
    @Alive({REG}) protected Value haystackArrayPtrValue;
    @Use({REG}) protected Value haystackArrayLengthValue;
    @Alive({REG}) protected Value needleArrayPtrValue;
    @Alive({REG}) protected Value needleArrayLengthValue;
    @Temp({REG}) protected Value firstChar;
    @Temp({REG}) protected Value arraySlotsRemaining;
    @Temp({REG}) protected Value comparisonResult1;
    @Temp({REG}) protected Value comparisonResult2;
    @Temp({REG}) protected Value comparisonResult3;
    @Temp({REG}) protected Value comparisonResult4;
    @Temp({REG, ILLEGAL}) protected Value vectorFindBeginCompareVal;
    @Temp({REG, ILLEGAL}) protected Value vectorArray1;
    @Temp({REG, ILLEGAL}) protected Value vectorArray2;
    @Temp({REG, ILLEGAL}) protected Value vectorArray3;
    @Temp({REG, ILLEGAL}) protected Value vectorArray4;
    @Temp({REG, ILLEGAL}) protected Value arrayEqualsTemp;

    public AMD64StringIndexOfStringOp(JavaKind kind, int vmPageSize, LIRGeneratorTool tool,
                    Value result,
                    Value haystackArrayPtr,
                    Value haystackArrayLength,
                    Value needleArrayPtr,
                    Value needleArrayLength,
                    int constantNeedleLength,
                    int maxVectorSize) {
        super(TYPE);
        this.kind = kind;
        this.vmPageSize = vmPageSize;
        this.constantNeedleLength = constantNeedleLength;
        this.arrayIndexScale = tool.getProviders().getArrayOffsetProvider().arrayScalingFactor(kind);
        this.useIndexOfTwoCharPrefix = Options.IndexOfStringUseTwoCharPrefix.getValue(tool.getResult().getLIR().getOptions());
        assert byteMode() || charMode();
        assert supports(tool, CPUFeature.SSSE3) || supports(tool, CPUFeature.AVX) || supportsAVX2(tool);
        resultValue = result;
        haystackArrayPtrValue = haystackArrayPtr;
        haystackArrayLengthValue = haystackArrayLength;
        needleArrayPtrValue = needleArrayPtr;
        needleArrayLengthValue = needleArrayLength;
        firstChar = tool.newVariable(LIRKind.value(AMD64Kind.DWORD));
        arraySlotsRemaining = tool.newVariable(LIRKind.value(AMD64Kind.DWORD));
        comparisonResult1 = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
        comparisonResult2 = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
        comparisonResult3 = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
        comparisonResult4 = tool.newVariable(LIRKind.value(AMD64Kind.QWORD));
        vectorKind = supportsAVX2(tool) && (maxVectorSize < 0 || maxVectorSize >= 32) ? byteMode() ? AMD64Kind.V256_BYTE : AMD64Kind.V256_WORD : byteMode() ? AMD64Kind.V128_BYTE : AMD64Kind.V128_WORD;
        vectorFindBeginCompareVal = tool.newVariable(LIRKind.value(vectorKind));
        vectorArray1 = tool.newVariable(LIRKind.value(vectorKind));
        vectorArray2 = tool.newVariable(LIRKind.value(vectorKind));
        vectorArray3 = tool.newVariable(LIRKind.value(vectorKind));
        vectorArray4 = tool.newVariable(LIRKind.value(vectorKind));
        arrayEqualsTemp = constantNeedleLength == -1 ? tool.newVariable(LIRKind.value(AMD64Kind.QWORD)) : Value.ILLEGAL;
    }

    private boolean byteMode() {
        return kind == JavaKind.Byte;
    }

    private boolean charMode() {
        return kind == JavaKind.Char;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler asm) {
        Register haystackArrayPtr = asRegister(haystackArrayPtrValue);
        Register haystackArrayLength = asRegister(haystackArrayLengthValue);
        Register needleArrayPtr = asRegister(needleArrayPtrValue);
        Register needleArrayLength = asRegister(needleArrayLengthValue);
        Register result = asRegister(resultValue);
        Register slotsRemaining = asRegister(arraySlotsRemaining);
        Register vecFindBeginCmp = asRegister(vectorFindBeginCompareVal);
        Register[] vecArray = {
                        asRegister(vectorArray1),
                        asRegister(vectorArray2),
                        asRegister(vectorArray3),
                        asRegister(vectorArray4),
        };
        Register[] cmpResult = {
                        asRegister(comparisonResult1),
                        asRegister(comparisonResult2),
                        asRegister(comparisonResult3),
                        asRegister(comparisonResult4),
        };
        Register[] searchValue = {asRegister(firstChar)};
        Label indexOfFirstChar = new Label();
        Label substringMatch = new Label();
        Label substringNoMatch = new Label();
        Label retFound = new Label();
        Label retNotFound = new Label();
        Label end = new Label();

        AVXKind.AVXSize vectorSize = AVXKind.getDataSize(vectorKind);

        // load array length
        // important: this must be the first register manipulation, since haystackArrayLengthValue
        // is annotated with @Use
        asm.movl(slotsRemaining, haystackArrayLength);
        // load first char of needle into comparison vector
        if (byteMode()) {
            if (useIndexOfTwoCharPrefix) {
                asm.movzwl(searchValue[0], new AMD64Address(needleArrayPtr));
            } else {
                asm.movzbl(searchValue[0], new AMD64Address(needleArrayPtr));
            }
        } else {
            if (useIndexOfTwoCharPrefix) {
                asm.movl(searchValue[0], new AMD64Address(needleArrayPtr));
            } else {
                asm.movzwl(searchValue[0], new AMD64Address(needleArrayPtr));
            }
        }
        // subtract length of substring - 1 from search string length
        asm.subl(slotsRemaining, needleArrayLength);
        asm.incrementl(slotsRemaining, useIndexOfTwoCharPrefix ? 2 : 1);
        // load first char of needle into comparison vector
        asm.movdl(vecFindBeginCmp, searchValue[0]);
        // load array pointer
        asm.movq(result, haystackArrayPtr);
        // fill comparison vector with copies of the search value
        AMD64ArrayIndexOfOp.emitBroadcast(asm, useIndexOfTwoCharPrefix ? (byteMode() ? JavaKind.Char : JavaKind.Int) : kind, vecFindBeginCmp, vecArray[0], vectorSize);

        asm.align(crb.target.wordSize * 2);
        asm.bind(indexOfFirstChar);
        // find first occurrence of needle's first one or two chars
        AMD64ArrayIndexOfOp.emitArrayIndexOfChars(crb, asm, kind, vectorSize, result, slotsRemaining, searchValue, new Register[]{vecFindBeginCmp}, vecArray, cmpResult,
                        retFound, retNotFound, vmPageSize, 1, 4, useIndexOfTwoCharPrefix);

        // return -1 (no match)
        asm.bind(retNotFound);
        asm.movl(result, -1);
        asm.jmp(end);

        asm.bind(retFound);
        // prefix was found, check for full match
        if (constantNeedleLength != -1) {
            if (!useIndexOfTwoCharPrefix || constantNeedleLength > 2) {
                emitConstantSizeArrayEquals(asm, vectorSize, result, needleArrayPtr, cmpResult, vecArray, substringNoMatch, constantNeedleLength * kind.getByteCount());
            }
        } else {
            asm.movq(cmpResult[0], needleArrayLength);
            asm.movq(cmpResult[1], result);
            asm.movq(cmpResult[2], needleArrayPtr);
            if (arrayIndexScale > 1) {
                asm.shll(cmpResult[0], NumUtil.log2Ceil(arrayIndexScale)); // scale length
            }
            asm.movq(cmpResult[3], cmpResult[0]);
            AMD64ArrayEqualsOp.emitArrayCompare(crb, asm, kind, cmpResult[0], cmpResult[1], cmpResult[2], cmpResult[3],
                            arrayEqualsTemp, Value.ILLEGAL, Value.ILLEGAL, vectorArray1, vectorArray2, substringMatch, substringNoMatch);
        }
        asm.jmpb(substringMatch);

        // no full match, search for next occurrence of the prefix
        asm.bind(substringNoMatch);
        asm.addq(result, kind.getByteCount());
        asm.decrementl(slotsRemaining);
        asm.jmp(indexOfFirstChar);

        asm.bind(substringMatch);
        // convert array pointer to offset
        asm.subq(result, haystackArrayPtr);
        AMD64ArrayIndexOfOp.emitBytesToArraySlots(asm, kind, result);
        asm.bind(end);
    }

    /**
     * Emits specialized assembly for checking equality of memory regions
     * {@code haystackArrayPtr[0..nBytes]} and {@code needleArrayPtr[0..nBytes]}. If they match,
     * execution continues directly after the emitted code block, otherwise we jump to
     * {@code noMatch}.
     */
    private static void emitConstantSizeArrayEquals(
                    AMD64MacroAssembler asm,
                    AVXKind.AVXSize vectorSize,
                    Register haystackArrayPtr,
                    Register needleArrayPtr,
                    Register[] tmp,
                    Register[] tmpVectors,
                    Label noMatch,
                    int nBytes) {
        assert nBytes > 1;
        int bytesPerVector = vectorSize.getBytes();
        if (nBytes < 16) {
            // array is shorter than any vector register, use regular CMP instructions
            AMD64Assembler.AMD64RMOp movOp = nBytes < 4 ? AMD64Assembler.AMD64RMOp.MOVZX : AMD64Assembler.AMD64RMOp.MOV;
            AMD64BaseAssembler.OperandSize movSize = (nBytes < 4) ? AMD64BaseAssembler.OperandSize.WORD : ((nBytes < 8) ? AMD64BaseAssembler.OperandSize.DWORD : AMD64BaseAssembler.OperandSize.QWORD);
            movOp.emit(asm, movSize, tmp[0], new AMD64Address(haystackArrayPtr));
            movOp.emit(asm, movSize, tmp[1], new AMD64Address(needleArrayPtr));
            if (nBytes > movSize.getBytes()) {
                movOp.emit(asm, movSize, tmp[2], new AMD64Address(haystackArrayPtr, nBytes - movSize.getBytes()));
                movOp.emit(asm, movSize, tmp[3], new AMD64Address(needleArrayPtr, nBytes - movSize.getBytes()));
                AMD64Assembler.AMD64BinaryArithmetic.CMP.getRMOpcode(movSize).emit(asm, movSize, tmp[2], tmp[3]);
                asm.jcc(AMD64Assembler.ConditionFlag.NotEqual, noMatch);
            }
            AMD64Assembler.AMD64BinaryArithmetic.CMP.getRMOpcode(movSize).emit(asm, movSize, tmp[0], tmp[1]);
            asm.jcc(AMD64Assembler.ConditionFlag.NotEqual, noMatch);
        } else if (nBytes < 32 && bytesPerVector >= 32) {
            // we could use YMM registers, but the array is too short, force XMM registers
            bytesPerVector = AVXKind.AVXSize.XMM.getBytes();
            VexMoveOp.VMOVDQU.emit(asm, AVXKind.AVXSize.XMM, tmpVectors[0], new AMD64Address(haystackArrayPtr));
            VexMoveOp.VMOVDQU.emit(asm, AVXKind.AVXSize.XMM, tmpVectors[1], new AMD64Address(needleArrayPtr));
            VexRVMOp.VPXOR.emit(asm, AVXKind.AVXSize.XMM, tmpVectors[0], tmpVectors[0], tmpVectors[1]);
            if (nBytes > bytesPerVector) {
                VexMoveOp.VMOVDQU.emit(asm, AVXKind.AVXSize.XMM, tmpVectors[2], new AMD64Address(haystackArrayPtr, nBytes - bytesPerVector));
                VexMoveOp.VMOVDQU.emit(asm, AVXKind.AVXSize.XMM, tmpVectors[3], new AMD64Address(needleArrayPtr, nBytes - bytesPerVector));
                VexRVMOp.VPXOR.emit(asm, AVXKind.AVXSize.XMM, tmpVectors[2], tmpVectors[2], tmpVectors[3]);
                VexRMOp.VPTEST.emit(asm, AVXKind.AVXSize.XMM, tmpVectors[2], tmpVectors[3]);
                asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
            }
            VexRMOp.VPTEST.emit(asm, AVXKind.AVXSize.XMM, tmpVectors[0], tmpVectors[1]);
            asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
        } else if (bytesPerVector >= 32) {
            // AVX2 supported, use YMM vectors
            assert asm.supports(CPUFeature.AVX2);
            int loopCount = nBytes / (bytesPerVector * 2);
            int rest = nBytes % (bytesPerVector * 2);
            Register arrayPtr1 = haystackArrayPtr;
            Register arrayPtr2 = needleArrayPtr;
            if (loopCount > 0) {
                if (0 < rest && rest < bytesPerVector) {
                    loopCount--;
                }
                if (loopCount > 0) {
                    Register loopReg = tmp[0];
                    arrayPtr1 = tmp[1];
                    arrayPtr2 = tmp[2];
                    if (loopCount > 1) {
                        asm.movl(loopReg, loopCount);
                    }
                    asm.movq(arrayPtr1, haystackArrayPtr);
                    asm.movq(arrayPtr2, needleArrayPtr);
                    Label loopBegin = new Label();
                    asm.bind(loopBegin);
                    asm.vmovdqu(tmpVectors[0], new AMD64Address(arrayPtr1));
                    asm.vmovdqu(tmpVectors[1], new AMD64Address(arrayPtr2));
                    asm.vmovdqu(tmpVectors[2], new AMD64Address(arrayPtr1, bytesPerVector));
                    asm.vmovdqu(tmpVectors[3], new AMD64Address(arrayPtr2, bytesPerVector));
                    asm.vpxor(tmpVectors[0], tmpVectors[0], tmpVectors[1]);
                    asm.vpxor(tmpVectors[2], tmpVectors[2], tmpVectors[3]);
                    asm.vptest(tmpVectors[0], tmpVectors[0]);
                    asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
                    asm.vptest(tmpVectors[2], tmpVectors[2]);
                    asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
                    asm.addq(arrayPtr1, bytesPerVector * 2);
                    asm.addq(arrayPtr2, bytesPerVector * 2);
                    if (loopCount > 1) {
                        asm.decrementl(loopReg);
                        asm.jcc(AMD64Assembler.ConditionFlag.NotZero, loopBegin);
                    }
                }
                if (0 < rest && rest < bytesPerVector) {
                    asm.vmovdqu(tmpVectors[0], new AMD64Address(arrayPtr1));
                    asm.vmovdqu(tmpVectors[1], new AMD64Address(arrayPtr2));
                    asm.vmovdqu(tmpVectors[2], new AMD64Address(arrayPtr1, bytesPerVector));
                    asm.vmovdqu(tmpVectors[3], new AMD64Address(arrayPtr2, bytesPerVector));
                    asm.vpxor(tmpVectors[0], tmpVectors[0], tmpVectors[1]);
                    asm.vpxor(tmpVectors[2], tmpVectors[2], tmpVectors[3]);
                    asm.vptest(tmpVectors[0], tmpVectors[0]);
                    asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
                    asm.vptest(tmpVectors[2], tmpVectors[2]);
                    asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
                    asm.vmovdqu(tmpVectors[0], new AMD64Address(arrayPtr1, bytesPerVector + rest));
                    asm.vmovdqu(tmpVectors[1], new AMD64Address(arrayPtr2, bytesPerVector + rest));
                    asm.vpxor(tmpVectors[0], tmpVectors[0], tmpVectors[1]);
                    asm.vptest(tmpVectors[0], tmpVectors[0]);
                    asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
                }
            }
            if (rest >= bytesPerVector) {
                asm.vmovdqu(tmpVectors[0], new AMD64Address(arrayPtr1));
                asm.vmovdqu(tmpVectors[1], new AMD64Address(arrayPtr2));
                asm.vpxor(tmpVectors[0], tmpVectors[0], tmpVectors[1]);
                if (rest > bytesPerVector) {
                    asm.vmovdqu(tmpVectors[2], new AMD64Address(arrayPtr1, rest - bytesPerVector));
                    asm.vmovdqu(tmpVectors[3], new AMD64Address(arrayPtr2, rest - bytesPerVector));
                    asm.vpxor(tmpVectors[2], tmpVectors[2], tmpVectors[3]);
                    asm.vptest(tmpVectors[2], tmpVectors[2]);
                    asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
                }
                asm.vptest(tmpVectors[0], tmpVectors[0]);
                asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
            }
        } else {
            // on AVX or SSE, use XMM vectors
            int loopCount = nBytes / (bytesPerVector * 2);
            int rest = nBytes % (bytesPerVector * 2);
            Register arrayPtr1 = haystackArrayPtr;
            Register arrayPtr2 = needleArrayPtr;
            if (loopCount > 0) {
                if (0 < rest && rest < bytesPerVector) {
                    loopCount--;
                }
                if (loopCount > 0) {
                    Register loopReg = tmp[0];
                    arrayPtr1 = tmp[1];
                    arrayPtr2 = tmp[2];
                    if (loopCount > 1) {
                        asm.movl(loopReg, loopCount);
                    }
                    asm.movq(arrayPtr1, haystackArrayPtr);
                    asm.movq(arrayPtr2, needleArrayPtr);
                    Label loopBegin = new Label();
                    asm.bind(loopBegin);
                    asm.movdqu(tmpVectors[0], new AMD64Address(arrayPtr1));
                    asm.movdqu(tmpVectors[1], new AMD64Address(arrayPtr2));
                    asm.movdqu(tmpVectors[2], new AMD64Address(arrayPtr1, bytesPerVector));
                    asm.movdqu(tmpVectors[3], new AMD64Address(arrayPtr2, bytesPerVector));
                    asm.pxor(tmpVectors[0], tmpVectors[1]);
                    asm.pxor(tmpVectors[2], tmpVectors[3]);
                    asm.ptest(tmpVectors[0], tmpVectors[0]);
                    asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
                    asm.ptest(tmpVectors[2], tmpVectors[2]);
                    asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
                    asm.addq(arrayPtr1, bytesPerVector * 2);
                    asm.addq(arrayPtr2, bytesPerVector * 2);
                    if (loopCount > 1) {
                        asm.decrementl(loopReg);
                        asm.jcc(AMD64Assembler.ConditionFlag.NotZero, loopBegin);
                    }
                }
                if (0 < rest && rest < bytesPerVector) {
                    asm.movdqu(tmpVectors[0], new AMD64Address(arrayPtr1));
                    asm.movdqu(tmpVectors[1], new AMD64Address(arrayPtr2));
                    asm.movdqu(tmpVectors[2], new AMD64Address(arrayPtr1, bytesPerVector));
                    asm.movdqu(tmpVectors[3], new AMD64Address(arrayPtr2, bytesPerVector));
                    asm.pxor(tmpVectors[0], tmpVectors[1]);
                    asm.pxor(tmpVectors[2], tmpVectors[3]);
                    asm.ptest(tmpVectors[0], tmpVectors[0]);
                    asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
                    asm.ptest(tmpVectors[2], tmpVectors[2]);
                    asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
                    asm.movdqu(tmpVectors[0], new AMD64Address(arrayPtr1, bytesPerVector + rest));
                    asm.movdqu(tmpVectors[1], new AMD64Address(arrayPtr2, bytesPerVector + rest));
                    asm.pxor(tmpVectors[0], tmpVectors[1]);
                    asm.ptest(tmpVectors[0], tmpVectors[0]);
                    asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
                }
            }
            if (rest >= bytesPerVector) {
                asm.movdqu(tmpVectors[0], new AMD64Address(arrayPtr1));
                asm.movdqu(tmpVectors[1], new AMD64Address(arrayPtr2));
                asm.pxor(tmpVectors[0], tmpVectors[1]);
                if (rest > bytesPerVector) {
                    asm.movdqu(tmpVectors[2], new AMD64Address(arrayPtr1, rest - bytesPerVector));
                    asm.movdqu(tmpVectors[3], new AMD64Address(arrayPtr2, rest - bytesPerVector));
                    asm.pxor(tmpVectors[2], tmpVectors[3]);
                    asm.ptest(tmpVectors[2], tmpVectors[2]);
                    asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
                }
                asm.ptest(tmpVectors[0], tmpVectors[1]);
                asm.jcc(AMD64Assembler.ConditionFlag.NotZero, noMatch);
            }
        }
    }

    private static boolean supportsAVX2(LIRGeneratorTool tool) {
        return supports(tool, CPUFeature.AVX2);
    }

    private static boolean supports(LIRGeneratorTool tool, CPUFeature cpuFeature) {
        return ((AMD64) tool.target().arch).getFeatures().contains(cpuFeature);
    }
}
