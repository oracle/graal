/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.gen;

import java.util.EnumSet;

import jdk.graal.compiler.asm.VectorSize;
import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.LabelRef;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.VirtualStackSlot;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.ValueKindFactory;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

public interface LIRGeneratorTool extends CoreProviders, DiagnosticLIRGeneratorTool, ValueKindFactory<LIRKind> {

    abstract class BlockScope implements AutoCloseable {

        public abstract BasicBlock<?> getCurrentBlock();

        @Override
        public abstract void close();

    }

    ArithmeticLIRGeneratorTool getArithmetic();

    /**
     * Get the current barrier set code generator.
     */
    BarrierSetLIRGeneratorTool getBarrierSet();

    default WriteBarrierSetLIRGeneratorTool getWriteBarrierSet() {
        return (WriteBarrierSetLIRGeneratorTool) getBarrierSet();
    }

    TargetDescription target();

    BasicBlock<?> getCurrentBlock();

    LIRGenerationResult getResult();

    RegisterConfig getRegisterConfig();

    MoveFactory getMoveFactory();

    /**
     * Get a special {@link MoveFactory} for spill moves.
     *
     * The instructions returned by this factory must only depend on the input values. References to
     * values that require interaction with register allocation are strictly forbidden.
     */
    MoveFactory getSpillMoveFactory();

    boolean canInlineConstant(Constant constant);

    boolean mayEmbedConstantLoad(Constant constant);

    Value emitConstant(LIRKind kind, Constant constant);

    Value emitJavaConstant(JavaConstant constant);

    /**
     * Some backends need to convert sub-word kinds to a larger kind in
     * {@link ArithmeticLIRGeneratorTool#emitLoad} and {@link #emitLoadConstant} because sub-word
     * registers can't be accessed. This method converts the {@link LIRKind} of a memory location or
     * constant to the {@link LIRKind} that will be used when it is loaded into a register.
     */
    <K extends ValueKind<K>> K toRegisterKind(K kind);

    AllocatableValue emitLoadConstant(ValueKind<?> kind, Constant constant);

    void emitNullCheck(Value address, LIRFrameState state);

    Variable emitLogicCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue, Value trueValue, Value falseValue, MemoryOrderMode memoryOrder, BarrierType barrierType);

    Value emitValueCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue, MemoryOrderMode memoryOrder, BarrierType barrierType);

    /**
     * Emit an atomic read-and-add instruction.
     *
     * @param accessKind the access kind for the value to be written
     * @param address address of the value to be read and written
     * @param delta the value to be added
     */
    Value emitAtomicReadAndAdd(LIRKind accessKind, Value address, Value delta);

    /**
     * Emit an atomic read-and-write instruction.
     *
     * @param accessKind the access kind for the value to be written
     * @param address address of the value to be read and written
     * @param newValue the new value to be written
     */
    Value emitAtomicReadAndWrite(LIRKind accessKind, Value address, Value newValue, BarrierType barrierType);

    void emitDeoptimize(Value actionAndReason, Value failedSpeculation, LIRFrameState state);

    Variable emitForeignCall(ForeignCallLinkage linkage, LIRFrameState state, Value... args);

    /**
     * Create a new {@link Variable}.
     *
     * @param kind The type of the value that will be stored in this {@link Variable}. See
     *            {@link LIRKind} for documentation on what to pass here. Note that in most cases,
     *            simply passing {@link Value#getValueKind()} is wrong.
     * @return A new {@link Variable}.
     */
    Variable newVariable(ValueKind<?> kind);

    Variable emitMove(Value input);

    Variable emitMove(ValueKind<?> dst, Value src);

    void emitMove(AllocatableValue dst, Value src);

    Variable emitReadRegister(Register register, ValueKind<?> kind);

    void emitWriteRegister(Register dst, Value src, ValueKind<?> wordStamp);

    void emitMoveConstant(AllocatableValue dst, Constant src);

    Variable emitAddress(AllocatableValue stackslot);

    void emitMembar(int barriers);

    void emitUnwind(Value operand);

    default void emitHalt() {
        throw GraalError.unimplemented("Halt operation is not implemented on this architecture");  // ExcludeFromJacocoGeneratedReport
    }

    /**
     * Emits a return instruction. Implementations need to insert a move if the input is not in the
     * correct location.
     */
    void emitReturn(JavaKind javaKind, Value input);

    /**
     * Returns an {@link AllocatableValue} holding the {@code value} by moving it if necessary. If
     * {@code value} is already an {@link AllocatableValue}, returns it unchanged.
     */
    AllocatableValue asAllocatable(Value value);

    /**
     * Returns an {@link AllocatableValue} of the address {@code value} with an integer
     * representation.
     */
    default AllocatableValue addressAsAllocatableInteger(Value value) {
        return asAllocatable(value);
    }

    <I extends LIRInstruction> I append(I op);

    void emitJump(LabelRef label);

    Variable emitConditionalMove(PlatformKind cmpKind, Value leftVal, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue);

    Variable emitIntegerTestMove(Value leftVal, Value right, Value trueValue, Value falseValue);

    Variable emitReverseBytes(Value operand);

    @SuppressWarnings("unused")
    default Variable emitArrayCompareTo(Stride strideA, Stride strideB, EnumSet<?> runtimeCheckedCPUFeatures, Value arrayA, Value lengthA, Value arrayB, Value lengthB) {
        throw GraalError.unimplemented("String.compareTo substitution is not implemented on this architecture"); // ExcludeFromJacocoGeneratedReport
    }

    @SuppressWarnings("unused")
    default Variable emitArrayRegionCompareTo(EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value length, Value dynamicStrides) {
        throw GraalError.unimplemented("String.compareTo substitution is not implemented on this architecture"); // ExcludeFromJacocoGeneratedReport
    }

    @SuppressWarnings("unused")
    default Variable emitArrayRegionCompareTo(Stride strideA, Stride strideB, EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value length) {
        throw GraalError.unimplemented("String.compareTo substitution is not implemented on this architecture"); // ExcludeFromJacocoGeneratedReport
    }

    @SuppressWarnings("unused")
    default Variable emitVectorizedMismatch(EnumSet<?> runtimeCheckedCPUFeatures, Value arrayA, Value arrayB, Value length, Value stride) {
        throw GraalError.unimplemented("vectorizedMismatch substitution is not implemented on this architecture"); // ExcludeFromJacocoGeneratedReport
    }

    @SuppressWarnings("unused")
    default Variable emitVectorizedHashCode(EnumSet<?> runtimeCheckedCPUFeatures, Value arrayStart, Value length, Value initialValue, JavaKind arrayKind) {
        throw GraalError.unimplemented("vectorizedHashCode substitution is not implemented on this architecture"); // ExcludeFromJacocoGeneratedReport
    }

    @SuppressWarnings("unused")
    default Variable emitArrayEquals(JavaKind commonElementKind, EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value length) {
        throw GraalError.unimplemented("Array.equals substitution is not implemented on this architecture"); // ExcludeFromJacocoGeneratedReport
    }

    @SuppressWarnings("unused")
    default Variable emitArrayEquals(Stride strideA, Stride strideB, EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value length) {
        throw GraalError.unimplemented("Array.equals with different types with offset substitution is not implemented on this architecture"); // ExcludeFromJacocoGeneratedReport
    }

    @SuppressWarnings("unused")
    default Variable emitArrayEqualsDynamicStrides(EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value length, Value dynamicStrides) {
        throw GraalError.unimplemented("Array.equals with different types with offset substitution is not implemented on this architecture"); // ExcludeFromJacocoGeneratedReport
    }

    @SuppressWarnings("unused")
    default Variable emitArrayEqualsWithMask(Stride strideA, Stride strideB, Stride strideMask, EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value mask, Value length) {
        throw GraalError.unimplemented("Array.equals with different types with offset substitution is not implemented on this architecture"); // ExcludeFromJacocoGeneratedReport
    }

    @SuppressWarnings("unused")
    default Variable emitArrayEqualsWithMaskDynamicStrides(EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value mask, Value length, Value dynamicStrides) {
        throw GraalError.unimplemented("Array.equals with different types with offset substitution is not implemented on this architecture"); // ExcludeFromJacocoGeneratedReport
    }

    @SuppressWarnings("unused")
    default void emitArrayCopyWithConversion(Stride strideSrc, Stride strideDst, EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arraySrc, Value offsetSrc, Value arrayDst, Value offsetDst, Value length) {
        throw GraalError.unimplemented("Array.copy with variable stride substitution is not implemented on this architecture"); // ExcludeFromJacocoGeneratedReport
    }

    @SuppressWarnings("unused")
    default void emitArrayCopyWithConversion(EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arraySrc, Value offsetSrc, Value arrayDst, Value offsetDst, Value length, Value dynamicStrides) {
        throw GraalError.unimplemented("Array.copy with variable stride substitution is not implemented on this architecture"); // ExcludeFromJacocoGeneratedReport
    }

    /**
     * Variants of {@link jdk.graal.compiler.replacements.nodes.CalcStringAttributesNode}. This
     * intrinsic calculates the code range and codepoint length of strings in various encodings. The
     * code range of a string is a flag that coarsely describes upper and lower bounds of all
     * codepoints contained in a string, or indicates whether a string was encoded correctly.
     */
    enum CalcStringAttributesEncoding {
        /**
         * Calculate the code range of a LATIN-1/ISO-8859-1 string. The result is either CR_7BIT or
         * CR_8BIT.
         */
        LATIN1(Stride.S1),
        /**
         * Calculate the code range of a 16-bit (UTF-16 or compacted UTF-32) string that is already
         * known to be in the BMP code range, so no UTF-16 surrogates are present. The result is
         * either CR_7BIT, CR_8BIT, or CR_16BIT.
         */
        BMP(Stride.S2),
        /**
         * Calculate the code range and codepoint length of a UTF-8 string. The result is a long
         * value, where the upper 32 bit are the calculated codepoint length, and the lower 32 bit
         * are the code range. The resulting code range is either CR_7BIT, CR_VALID_MULTIBYTE or
         * CR_BROKEN_MULTIBYTE. If the string is broken (not encoded correctly), the resulting
         * codepoint length is the number of non-continuation bytes in the string.
         */
        UTF_8(Stride.S1),
        /**
         * Calculate the code range and codepoint length of a UTF-16 string. The result is a long
         * value, where the upper 32 bit are the calculated codepoint length, and the lower 32 bit
         * are the code range. The resulting code range can be any of the following: CR_7BIT,
         * CR_8BIT, CR_16BIT, CR_VALID_MULTIBYTE, CR_BROKEN_MULTIBYTE. If the string is broken (not
         * encoded correctly), the resulting codepoint length includes all invalid surrogate
         * characters.
         */
        UTF_16(Stride.S2),
        /**
         * Calculate the code range of a UTF-32 string. The result can be any of the following:
         * CR_7BIT, CR_8BIT, CR_16BIT, CR_VALID_FIXED_WIDTH, CR_BROKEN_FIXED_WIDTH.
         */
        UTF_32(Stride.S4);

        /**
         * Stride to use when reading array elements.
         */
        public final Stride stride;

        CalcStringAttributesEncoding(Stride stride) {
            this.stride = stride;
        }

        /*
         * RETURN VALUES
         */
        private static final int FLAG_MULTIBYTE = 1 << 3;

        // NOTE:
        // The following fields must be kept in sync with
        // com.oracle.truffle.api.strings.TSCodeRange,
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
        public static final int CR_VALID = 3;
        /**
         * The string is not encoded correctly in the given fixed-width encoding.
         */
        public static final int CR_BROKEN = 4;
        /**
         * The string is encoded correctly in the given multi-byte/variable-width encoding.
         */
        public static final int CR_VALID_MULTIBYTE = CR_VALID | FLAG_MULTIBYTE;
        /**
         * The string is not encoded correctly in the given multi-byte/variable-width encoding.
         */
        public static final int CR_BROKEN_MULTIBYTE = CR_BROKEN | FLAG_MULTIBYTE;

        /**
         * Copyright (c) 2008-2010 Bjoern Hoehrmann <bjoern@hoehrmann.de>
         *
         * Permission is hereby granted, free of charge, to any person obtaining a copy of this
         * software and associated documentation files (the "Software"), to deal in the Software
         * without restriction, including without limitation the rights to use, copy, modify, merge,
         * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
         * persons to whom the Software is furnished to do so, subject to the following conditions:
         *
         * The above copyright notice and this permission notice shall be included in all copies or
         * substantial portions of the Software.
         *
         * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
         * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
         * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
         * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
         * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
         * DEALINGS IN THE SOFTWARE.
         *
         * See http://bjoern.hoehrmann.de/utf-8/decoder/dfa/ for details.
         *
         * <p>
         * Automaton for decoding/verifying UTF-8 strings. To use, initialize a state variable to
         * {@link #UTF_8_STATE_MACHINE_ACCEPTING_STATE}, and calculate next states with
         * {@link #utf8GetNextState(int, int)}. If the state after consuming all bytes of a UTF-8
         * string is {@link #UTF_8_STATE_MACHINE_ACCEPTING_STATE}, the string is valid.
         */
        public static final byte[] UTF_8_STATE_MACHINE = {
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
        public static final int UTF_8_STATE_MACHINE_ACCEPTING_STATE = 0;

        private static final int POSSIBLE_BYTE_VALUES = 256;

        public static int utf8GetNextState(int currentState, int currentByte) {
            final int byteClassification = UTF_8_STATE_MACHINE[currentByte];
            return UTF_8_STATE_MACHINE[POSSIBLE_BYTE_VALUES + currentState + byteClassification];
        }
    }

    @SuppressWarnings("unused")
    default Variable emitCalcStringAttributes(CalcStringAttributesEncoding encoding, EnumSet<?> runtimeCheckedCPUFeatures,
                    Value array, Value offset, Value length, boolean assumeValid) {
        throw GraalError.unimplemented("CalcStringAttributes substitution is not implemented on this architecture"); // ExcludeFromJacocoGeneratedReport
    }

    /**
     * Supported variants of
     * {@link #emitArrayIndexOf(Stride, ArrayIndexOfVariant, EnumSet, Value, Value, Value, Value, Value...)}.
     */
    enum ArrayIndexOfVariant {
        /**
         * Find index {@code i} where for any index {@code j} {@code array[i] == searchValues[j]}.
         * Supports up to four search values.
         */
        MatchAny,
        /**
         * Find index {@code i} where for any index {@code j}
         * {@code searchValues[j * 2] <= array[i] && array[i] <= searchValues[j * 2 + 1]}. Supports
         * up to two ranges.
         */
        MatchRange,
        /**
         * Find index {@code i} where {@code (array[i] | searchValues[1]) == searchValues[0]}.
         */
        WithMask,
        /**
         * Find index {@code i} where
         * {@code array[i] == searchValues[0] && array[i + 1] == searchValues[1]}.
         */
        FindTwoConsecutive,
        /**
         * Find index {@code i} where
         * {@code (array[i] | searchValues[2]) == searchValues[0] && (array[i + 1] | searchValues[3]) == searchValues[1]}.
         */
        FindTwoConsecutiveWithMask,
        /**
         * Find index {@code i} where
         *
         * <pre>
         * {@code
         * RawBytePointer lut = searchValues[0];
         * int v = array[i] & 0xff;
         * array[i] == v && (lut[v >> 4] & lut[16 + (v & 0xf)]) != 0
         * }
         * </pre>
         *
         * This variant can match a larger set of byte values simultaneously, e.g. to match the set
         * {@code {0x01, 0x03, 0x11, 0x12, 0x14}}, the following lookup table would suffice:
         *
         * <pre>
         * {@code
         * index:    0x0  0x1  0x2  0x3  0x4  0x5  0x6  0x7  0x8  0x9  0xa  0xb  0xc  0xd  0xe  0xf
         * tableHi: 0x01 0x02 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00
         * tableLo: 0x00 0x03 0x02 0x01 0x02 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00 0x00
         * }
         * </pre>
         *
         * Note that this variant expects {@code searchValue[0]} to be a <b>direct pointer</b> into
         * a 32-byte memory region.
         */
        Table
    }

    @SuppressWarnings("unused")
    default Variable emitArrayIndexOf(Stride stride, ArrayIndexOfVariant variant, EnumSet<?> runtimeCheckedCPUFeatures,
                    Value array, Value offset, Value length, Value fromIndex, Value... searchValues) {
        throw GraalError.unimplemented("String.indexOf substitution is not implemented on this architecture"); // ExcludeFromJacocoGeneratedReport
    }

    /*
     * The routines emitStringLatin1Inflate/3 and emitStringUTF16Compress/3 models a simplified
     * version of
     *
     * emitStringLatin1Inflate(Value src, Value src_ndx, Value dst, Value dst_ndx, Value len) and
     * emitStringUTF16Compress(Value src, Value src_ndx, Value dst, Value dst_ndx, Value len)
     *
     * respectively, where we have hoisted the offset address computations in a method replacement
     * snippet.
     */
    @SuppressWarnings("unused")
    default void emitStringLatin1Inflate(EnumSet<?> runtimeCheckedCPUFeatures, Value src, Value dst, Value len) {
        throw GraalError.unimplemented("StringLatin1.inflate substitution is not implemented on this architecture"); // ExcludeFromJacocoGeneratedReport
    }

    @SuppressWarnings("unused")
    default Variable emitStringUTF16Compress(EnumSet<?> runtimeCheckedCPUFeatures, Value src, Value dst, Value len) {
        throw GraalError.unimplemented("StringUTF16.compress substitution is not implemented on this architecture"); // ExcludeFromJacocoGeneratedReport
    }

    enum CharsetName {
        ASCII,
        ISO_8859_1
    }

    @SuppressWarnings("unused")
    default Variable emitEncodeArray(EnumSet<?> runtimeCheckedCPUFeatures, Value src, Value dst, Value length, CharsetName charset) {
        throw GraalError.unimplemented("No specialized implementation available"); // ExcludeFromJacocoGeneratedReport
    }

    @SuppressWarnings("unused")
    default Variable emitCountPositives(EnumSet<?> runtimeCheckedCPUFeatures, Value array, Value length) {
        throw GraalError.unimplemented("No specialized implementation available"); // ExcludeFromJacocoGeneratedReport
    }

    @SuppressWarnings("unused")
    default void emitAESEncrypt(Value from, Value to, Value key) {
        throw GraalError.unimplemented("No specialized implementation available"); // ExcludeFromJacocoGeneratedReport
    }

    @SuppressWarnings("unused")
    default void emitAESDecrypt(Value from, Value to, Value key) {
        throw GraalError.unimplemented("No specialized implementation available"); // ExcludeFromJacocoGeneratedReport
    }

    @SuppressWarnings("unused")
    default Variable emitCTRAESCrypt(Value inAddr, Value outAddr, Value kAddr, Value counterAddr, Value len, Value encryptedCounterAddr, Value usedPtr) {
        throw GraalError.unimplemented("No specialized implementation available"); // ExcludeFromJacocoGeneratedReport
    }

    @SuppressWarnings("unused")
    default Variable emitCBCAESEncrypt(Value inAddr, Value outAddr, Value kAddr, Value rAddr, Value len) {
        throw GraalError.unimplemented("No specialized implementation available"); // ExcludeFromJacocoGeneratedReport
    }

    @SuppressWarnings("unused")
    default Variable emitCBCAESDecrypt(Value inAddr, Value outAddr, Value kAddr, Value rAddr, Value len) {
        throw GraalError.unimplemented("No specialized implementation available"); // ExcludeFromJacocoGeneratedReport
    }

    @SuppressWarnings("unused")
    default void emitGHASHProcessBlocks(Value state, Value hashSubkey, Value data, Value blocks) {
        throw GraalError.unimplemented("No specialized implementation available"); // ExcludeFromJacocoGeneratedReport
    }

    @SuppressWarnings("unused")
    default void emitBigIntegerMultiplyToLen(Value x, Value xlen, Value y, Value ylen, Value z, Value zlen) {
        throw GraalError.unimplemented("No specialized implementation available"); // ExcludeFromJacocoGeneratedReport
    }

    @SuppressWarnings("unused")
    default Variable emitBigIntegerMulAdd(Value out, Value in, Value offset, Value len, Value k) {
        throw GraalError.unimplemented("No specialized implementation available"); // ExcludeFromJacocoGeneratedReport
    }

    @SuppressWarnings("unused")
    default void emitBigIntegerSquareToLen(Value x, Value len, Value z, Value zlen) {
        throw GraalError.unimplemented("No specialized implementation available");
    }

    @SuppressWarnings("unused")
    default void emitSha1ImplCompress(Value buf, Value state) {
        throw GraalError.unimplemented("No specialized implementation available");
    }

    @SuppressWarnings("unused")
    default void emitSha256ImplCompress(Value buf, Value state) {
        throw GraalError.unimplemented("No specialized implementation available");
    }

    @SuppressWarnings("unused")
    default void emitSha3ImplCompress(Value buf, Value state, Value blockSize) {
        throw GraalError.unimplemented("No specialized implementation available");
    }

    @SuppressWarnings("unused")
    default void emitSha512ImplCompress(Value buf, Value state) {
        throw GraalError.unimplemented("No specialized implementation available");
    }

    @SuppressWarnings("unused")
    default void emitMD5ImplCompress(Value buf, Value state) {
        throw GraalError.unimplemented("No specialized implementation available");
    }

    void emitBlackhole(Value operand);

    LIRKind getLIRKind(Stamp stamp);

    void emitPause();

    /**
     * Perform no operation by default. See also {@link Thread#onSpinWait()}.
     */
    default void emitSpinWait() {
    }

    void emitPrefetchAllocate(Value address);

    Value emitCompress(Value pointer, CompressEncoding encoding, boolean nonNull);

    Value emitUncompress(Value pointer, CompressEncoding encoding, boolean nonNull);

    default void emitConvertNullToZero(AllocatableValue result, Value input) {
        if (LIRValueUtil.isJavaConstant(input)) {
            if (LIRValueUtil.asJavaConstant(input).isNull()) {
                emitMoveConstant(result, JavaConstant.forPrimitiveInt(input.getPlatformKind().getSizeInBytes() * Byte.SIZE, 0L));
            } else {
                emitMove(result, input);
            }
        } else {
            emitConvertNullToZero(result, (AllocatableValue) input);
        }
    }

    default void emitConvertNullToZero(AllocatableValue result, AllocatableValue input) {
        emitMove(result, input);
    }

    default void emitConvertZeroToNull(AllocatableValue result, Value input) {
        emitMove(result, input);
    }

    /**
     * Emits an instruction that prevents speculative execution from proceeding: no instruction
     * after this fence will execute until all previous instructions have retired.
     */
    void emitSpeculationFence();

    /**
     * Write value to the protection key register.
     *
     * @param value to be written
     */
    default void emitProtectionKeyRegisterWrite(Value value) {
        throw new GraalError("Emitting code to write a value to the protection key register is not currently supported on %s", target().arch);
    }

    /**
     * Read contents of the protection key register.
     *
     * @return value read from the register
     */
    default Value emitProtectionKeyRegisterRead() {
        throw new GraalError("Emitting code to read the contents of the protection key register is not currently supported on %s", target().arch);
    }

    default VirtualStackSlot allocateStackMemory(int sizeInBytes, int alignmentInBytes) {
        return getResult().getFrameMapBuilder().allocateStackMemory(sizeInBytes, alignmentInBytes);
    }

    default Value emitTimeStamp() {
        throw new GraalError("Emitting code to return the current value of the timestamp counter is not currently supported on %s", target().arch);
    }

    @SuppressWarnings("unused")
    default void emitProcid(AllocatableValue dst) {
        throw new GraalError("Emitting code to return the current value of the procid is not currently supported on %s", target().arch);
    }

    default Value emitReadCallerStackPointer(Stamp wordStamp) {
        /*
         * We do not know the frame size yet. So we load the address of the first spill slot
         * relative to the beginning of the frame, which is equivalent to the stack pointer of the
         * caller.
         */
        return emitAddress(StackSlot.get(getLIRKind(wordStamp), 0, true));
    }

    default Value emitReadReturnAddress(Stamp wordStamp, int returnAddressSize) {
        return emitMove(StackSlot.get(getLIRKind(wordStamp), -returnAddressSize, true));
    }

    @SuppressWarnings("unused")
    default void emitZeroMemory(Value address, Value length, boolean isAligned) {
        throw GraalError.unimplemented("Bulk zeroing is not implemented on this architecture"); // ExcludeFromJacocoGeneratedReport
    }

    /**
     * Emits instruction(s) to flush an individual cache line that starts at {@code address}.
     */
    void emitCacheWriteback(Value address);

    /**
     * Emits instruction(s) to serialize cache writeback operations relative to preceding (if
     * {@code isPreSync == true}) or following (if {@code isPreSync == false}) memory writes.
     */
    void emitCacheWritebackSync(boolean isPreSync);

    /**
     * Returns the maximum size of vector registers.
     */
    @SuppressWarnings("unused")
    default VectorSize getMaxVectorSize(EnumSet<?> runtimeCheckedCPUFeatures) {
        throw GraalError.unimplemented("Max vector size is not specified on this architecture"); // ExcludeFromJacocoGeneratedReport
    }
}
