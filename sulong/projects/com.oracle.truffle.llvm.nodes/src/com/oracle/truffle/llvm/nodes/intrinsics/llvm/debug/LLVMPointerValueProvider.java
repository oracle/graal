/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.nodes.intrinsics.llvm.debug;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugTypeConstants;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugValue;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;

import java.math.BigInteger;

public class LLVMPointerValueProvider implements LLVMDebugValue {

    private final LLVMPointer pointer;
    private final LLVMContext context;

    protected LLVMPointerValueProvider(LLVMPointer pointer, LLVMContext context) {
        this.pointer = pointer;
        this.context = context;
    }

    protected LLVMPointer getPointer() {
        return pointer;
    }

    @Override
    @TruffleBoundary
    public String describeValue(long bitOffset, int bitSize) {
        final StringBuilder builder = new StringBuilder();
        if (isByteAligned(bitSize)) {
            builder.append(bitSize / Byte.SIZE).append(" byte at ");
        } else if (bitSize == 1) {
            builder.append("first bit at ");
        } else {
            builder.append(String.valueOf(bitSize)).append(" bits at ");
        }
        builder.append(computeAddress(bitOffset));
        return builder.toString();
    }

    @Override
    public boolean canRead(long bitOffset, int bits) {
        // reading from a managed pointer may entail sending TO_NATIVE to a pointed to
        // TruffleObject, we avoid this to prevent the debugger from changing an objects internal
        // state
        return !pointer.isNull() && !isInteropValue();
    }

    private LLVMExpressionNode createLoadNode(Type loadType, int byteOffset) {
        assert byteOffset >= 0;

        final NodeFactory nodeFactory = context.getNodeFactory();
        LLVMExpressionNode pointerNode = new ConstantNode(pointer);

        if (byteOffset != 0) {
            final LLVMExpressionNode offset = new ConstantNode(byteOffset);
            pointerNode = nodeFactory.createTypedElementPointer(pointerNode, offset, 1, loadType);
        }

        return nodeFactory.createLoad(loadType, pointerNode);
    }

    @Override
    public Object readBoolean(long bitOffset) {
        if (!canRead(bitOffset, LLVMDebugTypeConstants.BOOLEAN_SIZE)) {
            return unavailable(bitOffset, LLVMDebugTypeConstants.BOOLEAN_SIZE);
        }

        if (isByteAligned(bitOffset)) {
            final LLVMExpressionNode node = createLoadNode(PrimitiveType.I1, (int) (bitOffset / Byte.SIZE));
            final boolean value;
            try {
                value = node.executeI1(null);
            } catch (Throwable t) {
                return unavailable(bitOffset, LLVMDebugTypeConstants.BOOLEAN_SIZE);
            }
            return value;
        }

        final LLVMExpressionNode byteNode = createLoadNode(PrimitiveType.I8, (int) (bitOffset / Byte.SIZE));
        final byte containingByte;
        try {
            containingByte = byteNode.executeI8(null);
        } catch (Throwable t) {
            return unavailable(bitOffset, LLVMDebugTypeConstants.BOOLEAN_SIZE);
        }

        final int shift = (int) bitOffset % Byte.SIZE;
        final int shiftedByte = containingByte >> shift;
        return (shiftedByte & 0b1) != 0;
    }

    @Override
    public Object readFloat(long bitOffset) {
        if (!canRead(bitOffset, LLVMDebugTypeConstants.FLOAT_SIZE)) {
            return unavailable(bitOffset, LLVMDebugTypeConstants.FLOAT_SIZE);
        }

        if (isByteAligned(bitOffset)) {
            final LLVMExpressionNode node = createLoadNode(PrimitiveType.FLOAT, (int) (bitOffset / Byte.SIZE));
            final float value;
            try {
                value = node.executeFloat(null);
            } catch (Throwable t) {
                return unavailable(bitOffset, LLVMDebugTypeConstants.FLOAT_SIZE);
            }
            return value;
        }

        final int alignedOffset = (int) (bitOffset / Byte.SIZE);
        long bits = 0;
        for (int i = 0; i < Float.BYTES + 1; i++) {
            final LLVMExpressionNode byteNode = createLoadNode(PrimitiveType.I8, alignedOffset + i);
            final byte byteValue;
            try {
                byteValue = byteNode.executeI8(null);
            } catch (Throwable t) {
                return unavailable(bitOffset, LLVMDebugTypeConstants.FLOAT_SIZE);
            }
            long shiftedByte = (long) byteValue << (Byte.SIZE * i);
            bits |= shiftedByte;
        }

        final int shift = (int) bitOffset % Byte.SIZE;
        final long shiftedBits = bits >> shift;
        return Float.intBitsToFloat((int) (shiftedBits & LLVMExpressionNode.I32_MASK));
    }

    @Override
    public Object readDouble(long bitOffset) {
        if (!canRead(bitOffset, LLVMDebugTypeConstants.DOUBLE_SIZE)) {
            return unavailable(bitOffset, LLVMDebugTypeConstants.DOUBLE_SIZE);
        }

        if (isByteAligned(bitOffset)) {
            final LLVMExpressionNode node = createLoadNode(PrimitiveType.DOUBLE, (int) (bitOffset / Byte.SIZE));
            final double value;
            try {
                value = node.executeDouble(null);
            } catch (Throwable t) {
                return unavailable(bitOffset, LLVMDebugTypeConstants.DOUBLE_SIZE);
            }
            return value;
        }

        final Object asBits = readBigInteger(bitOffset, LLVMDebugTypeConstants.DOUBLE_SIZE, false);
        if (asBits instanceof BigInteger) {
            final BigInteger bits = (BigInteger) asBits;
            return Double.longBitsToDouble(bits.longValue());
        }

        return unavailable(bitOffset, LLVMDebugTypeConstants.DOUBLE_SIZE);
    }

    @Override
    public Object read80BitFloat(long bitOffset) {
        if (!canRead(bitOffset, LLVMDebugTypeConstants.LLVM80BIT_SIZE_ACTUAL)) {
            return unavailable(bitOffset, LLVMDebugTypeConstants.LLVM80BIT_SIZE_ACTUAL);
        }

        if (isByteAligned(bitOffset)) {
            final LLVMExpressionNode node = createLoadNode(PrimitiveType.X86_FP80, (int) (bitOffset / Byte.SIZE));
            final LLVM80BitFloat value;
            try {
                value = node.executeLLVM80BitFloat(null);
            } catch (Throwable t) {
                return unavailable(bitOffset, LLVMDebugTypeConstants.LLVM80BIT_SIZE_ACTUAL);
            }
            return value;
        }

        final Object asBits = readBigInteger(bitOffset, LLVMDebugTypeConstants.LLVM80BIT_SIZE_ACTUAL, false);
        if (asBits instanceof BigInteger) {
            final BigInteger bits = (BigInteger) asBits;
            return LLVM80BitFloat.fromBytesBigEndian(bits.toByteArray());
        }

        return unavailable(bitOffset, LLVMDebugTypeConstants.LLVM80BIT_SIZE_ACTUAL);
    }

    @Override
    public Object readAddress(long bitOffset) {
        if (!canRead(bitOffset, LLVMDebugTypeConstants.ADDRESS_SIZE)) {
            return unavailable(bitOffset, LLVMDebugTypeConstants.ADDRESS_SIZE);
        }

        if (isByteAligned(bitOffset)) {
            final LLVMExpressionNode node = createLoadNode(PointerType.VOID, (int) (bitOffset / Byte.SIZE));
            final LLVMPointer value;
            try {
                value = node.executeLLVMPointer(null);
            } catch (Throwable t) {
                return unavailable(bitOffset, LLVMDebugTypeConstants.ADDRESS_SIZE);
            }
            return value;
        }

        final Object asBits = readBigInteger(bitOffset, LLVMDebugTypeConstants.ADDRESS_SIZE, false);
        if (asBits instanceof BigInteger) {
            final BigInteger bits = (BigInteger) asBits;
            return LLVMNativePointer.create(bits.longValue());
        }

        return unavailable(bitOffset, LLVMDebugTypeConstants.ADDRESS_SIZE);
    }

    @Override
    public Object readUnknown(long bitOffset, int bitSize) {
        if (canRead(bitOffset, bitSize)) {
            final Object integerObject = readBigInteger(bitOffset, bitSize, false);
            if (integerObject instanceof BigInteger) {
                return LLVMDebugValue.toHexString((BigInteger) integerObject);
            }
        }
        return unavailable(bitOffset, bitSize);
    }

    @Override
    @TruffleBoundary
    public Object computeAddress(long bitOffset) {
        if (pointer.isNull()) {
            // special case to prevent dereferencing of an incremented 0x0
            return pointer;
        }

        if (LLVMManagedPointer.isInstance(pointer)) {
            String address = "<managed pointer>";
            if (bitOffset != 0) {
                if (isByteAligned(bitOffset)) {
                    address += " + " + (bitOffset / Byte.SIZE) + " byte";
                } else {
                    address += " + " + bitOffset + " bits";
                }
            }
            return address;
        }

        if (LLVMNativePointer.isInstance(pointer)) {
            LLVMNativePointer nativePointer = LLVMNativePointer.cast(pointer);
            long offset = bitOffset;
            if (bitOffset != 0) {
                nativePointer = nativePointer.increment(offset / Byte.SIZE);
                offset = offset % Byte.SIZE;
            }

            String address = nativePointer.toString();
            if (offset != 0) {
                address += " + " + offset + " bits";
            }

            return address;
        }

        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Unknown Pointer: " + pointer);
    }

    @Override
    @TruffleBoundary
    public Object readBigInteger(long bitOffset, int bitSize, boolean signed) {
        if (!canRead(bitOffset, bitSize)) {
            return unavailable(bitOffset, bitSize);
        }

        final int byteOffset = (int) (bitOffset / Byte.SIZE);
        if (isByteAligned(bitOffset)) {
            switch (bitSize) {
                case LLVMDebugTypeConstants.BYTE_SIZE: {
                    final LLVMExpressionNode node = createLoadNode(PrimitiveType.I8, byteOffset);
                    byte value;
                    try {
                        value = node.executeI8(null);
                    } catch (Throwable t) {
                        return unavailable(bitOffset, LLVMDebugTypeConstants.BYTE_SIZE);
                    }
                    return BigInteger.valueOf(signed ? value : Byte.toUnsignedInt(value));
                }

                case LLVMDebugTypeConstants.SHORT_SIZE: {
                    final LLVMExpressionNode node = createLoadNode(PrimitiveType.I16, byteOffset);
                    short value;
                    try {
                        value = node.executeI16(null);
                    } catch (Throwable t) {
                        return unavailable(bitOffset, LLVMDebugTypeConstants.SHORT_SIZE);
                    }
                    return BigInteger.valueOf(signed ? value : Short.toUnsignedInt(value));
                }

                case LLVMDebugTypeConstants.INTEGER_SIZE: {
                    final LLVMExpressionNode node = createLoadNode(PrimitiveType.I32, byteOffset);
                    int value;
                    try {
                        value = node.executeI32(null);
                    } catch (Throwable t) {
                        return unavailable(bitOffset, LLVMDebugTypeConstants.INTEGER_SIZE);
                    }
                    return BigInteger.valueOf(signed ? value : Integer.toUnsignedLong(value));
                }

                case LLVMDebugTypeConstants.LONG_SIZE: {
                    final LLVMExpressionNode node = createLoadNode(PrimitiveType.I64, byteOffset);
                    long value;
                    try {
                        value = node.executeI64(null);
                    } catch (Throwable t) {
                        return unavailable(bitOffset, LLVMDebugTypeConstants.LONG_SIZE);
                    }
                    return signed ? BigInteger.valueOf(value) : new BigInteger(Long.toUnsignedString(value));
                }
            }
        }

        final int alignedBitSize = bitSize + ((int) bitOffset % Byte.SIZE);
        final int alignedByteSize = ((alignedBitSize - 1) / Byte.SIZE) + 1;
        final int alignedOffset = (int) (bitOffset / Byte.SIZE);
        final byte[] bytes = new byte[alignedByteSize];

        for (int i = 0; i < alignedByteSize; i++) {
            final LLVMExpressionNode byteNode = createLoadNode(PrimitiveType.I8, alignedOffset + i);
            final byte byteValue;
            try {
                byteValue = byteNode.executeI8(null);
            } catch (Throwable t) {
                return unavailable(bitOffset, LLVMDebugTypeConstants.FLOAT_SIZE);
            }
            // BigInteger magnitudes are stored as Big-Endian
            bytes[alignedByteSize - 1 - i] = byteValue;
        }

        if (isAllZeros(bytes)) {
            return BigInteger.ZERO;
        }

        final BigInteger totalMemory = new BigInteger(1, bytes);
        final BigInteger shiftedMemory = totalMemory.shiftRight((int) (bitOffset % Byte.SIZE));

        BigInteger maskedMemory = shiftedMemory;
        for (int i = bitSize; i < shiftedMemory.bitLength(); i++) {
            maskedMemory = maskedMemory.clearBit(i);
        }

        if (BigInteger.ZERO.equals(maskedMemory) || isAllZeros(maskedMemory.toByteArray())) {
            return BigInteger.ZERO;

        } else if (!signed || !maskedMemory.testBit(bitSize - 1)) {
            // the value is either signed or its sign bit is not set, in either case the positive
            // signum is correct
            return maskedMemory;

        } else {
            // 'maskedMemory' contains a negative signed integer in two's complement format, but
            // BigInteger treats it as a positive value whose sign is determined from the separate
            // signum. since BigInteger won't do it for us if we just change the signum, we convert
            // the magnitude manually
            BigInteger value = maskedMemory;

            // flip the bits instead of binary negation to prevent sign extension
            for (int i = 0; i < bitSize; i++) {
                value = value.flipBit(i);
            }

            value = value.add(BigInteger.ONE);

            // correct the signum to negative
            value = value.negate();

            return value;
        }
    }

    private static boolean isAllZeros(byte[] bytes) {
        for (byte b : bytes) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public LLVMDebugValue dereferencePointer(long bitOffset) {
        if (!canRead(bitOffset, LLVMDebugTypeConstants.ADDRESS_SIZE) || !isByteAligned(bitOffset)) {
            return null;
        }

        final Object pointerRead = readAddress(bitOffset);
        if (LLVMPointer.isInstance(pointerRead)) {
            return context.getNodeFactory().createDebugDeclarationBuilder().build(pointerRead);
        }

        return null;
    }

    @Override
    public boolean isInteropValue() {
        return LLVMManagedPointer.isInstance(pointer);
    }

    @Override
    public Object asInteropValue() {
        return LLVMManagedPointer.cast(pointer).getObject();
    }

    private static boolean isByteAligned(long offset) {
        return (offset & (Byte.SIZE - 1)) == 0;
    }

    private static final class ConstantNode extends LLVMExpressionNode {

        private final Object value;

        private ConstantNode(Object value) {
            this.value = value;
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return value;
        }
    }
}
