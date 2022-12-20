/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.gen;

import java.util.EnumSet;

import org.graalvm.compiler.asm.VectorSize;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.spi.CodeGenProviders;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRValueUtil;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.VirtualStackSlot;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.ValueKindFactory;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

public interface LIRGeneratorTool extends DiagnosticLIRGeneratorTool, ValueKindFactory<LIRKind> {

    abstract class BlockScope implements AutoCloseable {

        public abstract AbstractBlockBase<?> getCurrentBlock();

        @Override
        public abstract void close();

    }

    ArithmeticLIRGeneratorTool getArithmetic();

    CodeGenProviders getProviders();

    TargetDescription target();

    MetaAccessProvider getMetaAccess();

    CodeCacheProvider getCodeCache();

    ForeignCallsProvider getForeignCalls();

    AbstractBlockBase<?> getCurrentBlock();

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

    Variable emitLogicCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue, Value trueValue, Value falseValue, MemoryOrderMode memoryOrder);

    Value emitValueCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue, MemoryOrderMode memoryOrder);

    /**
     * Emit an atomic read-and-add instruction.
     *
     * @param accessKind the access kind for the value to be written
     * @param address address of the value to be read and written
     * @param delta the value to be added
     */
    default Value emitAtomicReadAndAdd(LIRKind accessKind, Value address, Value delta) {
        throw GraalError.unimplemented();
    }

    /**
     * Emit an atomic read-and-write instruction.
     *
     * @param accessKind the access kind for the value to be written
     * @param address address of the value to be read and written
     * @param newValue the new value to be written
     */
    default Value emitAtomicReadAndWrite(LIRKind accessKind, Value address, Value newValue) {
        throw GraalError.unimplemented();
    }

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

    void emitMove(AllocatableValue dst, Value src);

    Variable emitReadRegister(Register register, ValueKind<?> kind);

    void emitWriteRegister(Register dst, Value src, ValueKind<?> wordStamp);

    void emitMoveConstant(AllocatableValue dst, Constant src);

    Variable emitAddress(AllocatableValue stackslot);

    void emitMembar(int barriers);

    void emitUnwind(Value operand);

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

    Variable emitByteSwap(Value operand);

    @SuppressWarnings("unused")
    default Variable emitArrayCompareTo(Stride strideA, Stride strideB, EnumSet<?> runtimeCheckedCPUFeatures, Value arrayA, Value lengthA, Value arrayB, Value lengthB) {
        throw GraalError.unimplemented("String.compareTo substitution is not implemented on this architecture");
    }

    @SuppressWarnings("unused")
    default Variable emitArrayRegionCompareTo(EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value length, Value dynamicStrides) {
        throw GraalError.unimplemented("String.compareTo substitution is not implemented on this architecture");
    }

    @SuppressWarnings("unused")
    default Variable emitArrayRegionCompareTo(Stride strideA, Stride strideB, EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value length) {
        throw GraalError.unimplemented("String.compareTo substitution is not implemented on this architecture");
    }

    @SuppressWarnings("unused")
    default Variable emitVectorizedMismatch(EnumSet<?> runtimeCheckedCPUFeatures, Value arrayA, Value arrayB, Value length, Value stride) {
        throw GraalError.unimplemented("vectorizedMismatch substitution is not implemented on this architecture");
    }

    @SuppressWarnings("unused")
    default Variable emitArrayEquals(JavaKind commonElementKind, EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value length) {
        throw GraalError.unimplemented("Array.equals substitution is not implemented on this architecture");
    }

    @SuppressWarnings("unused")
    default Variable emitArrayEquals(Stride strideA, Stride strideB, EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value length) {
        throw GraalError.unimplemented("Array.equals with different types with offset substitution is not implemented on this architecture");
    }

    @SuppressWarnings("unused")
    default Variable emitArrayEqualsDynamicStrides(EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value length, Value dynamicStrides) {
        throw GraalError.unimplemented("Array.equals with different types with offset substitution is not implemented on this architecture");
    }

    @SuppressWarnings("unused")
    default Variable emitArrayEqualsWithMask(Stride strideA, Stride strideB, Stride strideMask, EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value mask, Value length) {
        throw GraalError.unimplemented("Array.equals with different types with offset substitution is not implemented on this architecture");
    }

    @SuppressWarnings("unused")
    default Variable emitArrayEqualsWithMaskDynamicStrides(EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value mask, Value length, Value dynamicStrides) {
        throw GraalError.unimplemented("Array.equals with different types with offset substitution is not implemented on this architecture");
    }

    @SuppressWarnings("unused")
    default void emitArrayCopyWithConversion(Stride strideSrc, Stride strideDst, EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arraySrc, Value offsetSrc, Value arrayDst, Value offsetDst, Value length) {
        throw GraalError.unimplemented("Array.copy with variable stride substitution is not implemented on this architecture");
    }

    @SuppressWarnings("unused")
    default void emitArrayCopyWithConversion(EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arraySrc, Value offsetSrc, Value arrayDst, Value offsetDst, Value length, Value dynamicStrides) {
        throw GraalError.unimplemented("Array.copy with variable stride substitution is not implemented on this architecture");
    }

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
    }

    @SuppressWarnings("unused")
    default Variable emitCalcStringAttributes(CalcStringAttributesEncoding encoding, EnumSet<?> runtimeCheckedCPUFeatures,
                    Value array, Value offset, Value length, boolean assumeValid) {
        throw GraalError.unimplemented("CalcStringAttributes substitution is not implemented on this architecture");
    }

    @SuppressWarnings("unused")
    default Variable emitArrayIndexOf(Stride stride, boolean findTwoConsecutive, boolean withMask, EnumSet<?> runtimeCheckedCPUFeatures,
                    Value array, Value offset, Value length, Value fromIndex, Value... searchValues) {
        throw GraalError.unimplemented("String.indexOf substitution is not implemented on this architecture");
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
        throw GraalError.unimplemented("StringLatin1.inflate substitution is not implemented on this architecture");
    }

    @SuppressWarnings("unused")
    default Variable emitStringUTF16Compress(EnumSet<?> runtimeCheckedCPUFeatures, Value src, Value dst, Value len) {
        throw GraalError.unimplemented("StringUTF16.compress substitution is not implemented on this architecture");
    }

    enum CharsetName {
        ASCII,
        ISO_8859_1
    }

    @SuppressWarnings("unused")
    default Variable emitEncodeArray(EnumSet<?> runtimeCheckedCPUFeatures, Value src, Value dst, Value length, CharsetName charset) {
        throw GraalError.unimplemented("No specialized implementation available");
    }

    @SuppressWarnings("unused")
    default Variable emitHasNegatives(EnumSet<?> runtimeCheckedCPUFeatures, Value array, Value length) {
        throw GraalError.unimplemented("No specialized implementation available");
    }

    @SuppressWarnings("unused")
    default void emitAESEncrypt(Value from, Value to, Value key) {
        throw GraalError.unimplemented("No specialized implementation available");
    }

    @SuppressWarnings("unused")
    default void emitAESDecrypt(Value from, Value to, Value key) {
        throw GraalError.unimplemented("No specialized implementation available");
    }

    @SuppressWarnings("unused")
    default Variable emitCTRAESCrypt(Value inAddr, Value outAddr, Value kAddr, Value counterAddr, Value len, Value encryptedCounterAddr, Value usedPtr) {
        throw GraalError.unimplemented("No specialized implementation available");
    }

    @SuppressWarnings("unused")
    default Variable emitCBCAESEncrypt(Value inAddr, Value outAddr, Value kAddr, Value rAddr, Value len) {
        throw GraalError.unimplemented("No specialized implementation available");
    }

    @SuppressWarnings("unused")
    default Variable emitCBCAESDecrypt(Value inAddr, Value outAddr, Value kAddr, Value rAddr, Value len) {
        throw GraalError.unimplemented("No specialized implementation available");
    }

    @SuppressWarnings("unused")
    default void emitGHASHProcessBlocks(Value state, Value hashSubkey, Value data, Value blocks) {
        throw GraalError.unimplemented("No specialized implementation available");
    }

    @SuppressWarnings("unused")
    default void emitBigIntegerMultiplyToLen(Value x, Value xlen, Value y, Value ylen, Value z, Value zlen) {
        throw GraalError.unimplemented("No specialized implementation available");
    }

    void emitBlackhole(Value operand);

    LIRKind getLIRKind(Stamp stamp);

    void emitPause();

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

    default VirtualStackSlot allocateStackMemory(int sizeInBytes, int alignmentInBytes) {
        return getResult().getFrameMapBuilder().allocateStackMemory(sizeInBytes, alignmentInBytes);
    }

    default Value emitTimeStampWithProcid() {
        throw new GraalError("Emitting code to return the current value of the timestamp counter with procid is not currently supported on %s", target().arch);
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
        throw GraalError.unimplemented("Bulk zeroing is not implemented on this architecture");
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
        throw GraalError.unimplemented("Max vector size is not specified on this architecture");
    }
}
