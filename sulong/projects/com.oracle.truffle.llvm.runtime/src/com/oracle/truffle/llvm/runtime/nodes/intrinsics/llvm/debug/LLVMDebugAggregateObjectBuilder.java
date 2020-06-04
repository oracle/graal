/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.debug;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugObject;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugTypeConstants;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugObjectBuilder;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugValue;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;

public final class LLVMDebugAggregateObjectBuilder extends LLVMDebugObjectBuilder {

    @CompilationFinal(dimensions = 1) private final int[] offsets;
    @CompilationFinal(dimensions = 1) private final int[] lengths;

    private final LLVMDebugValue.Builder[] partBuilders;
    private final Object[] partValues;

    public LLVMDebugAggregateObjectBuilder(int[] offsets, int[] lengths) {
        super();
        this.offsets = offsets;
        this.lengths = lengths;
        this.partBuilders = new LLVMDebugValue.Builder[offsets.length];
        this.partValues = new Object[offsets.length];
    }

    public void setPart(int partIndex, LLVMDebugValue.Builder builder, Object value) {
        partBuilders[partIndex] = builder;
        partValues[partIndex] = value;
    }

    public void clear(int[] clearIndices) {
        for (int partIndex : clearIndices) {
            partValues[partIndex] = null;
            partBuilders[partIndex] = null;
        }
    }

    @Override
    @TruffleBoundary
    public LLVMDebugObject getValue(LLVMSourceType type, LLVMSourceLocation declaration) {
        final LLVMDebugValue provider = new FragmentValueProvider(offsets, lengths, partBuilders, partValues);
        return LLVMDebugObject.create(type, 0, provider, declaration);
    }

    private static final class FragmentValueProvider implements LLVMDebugValue {

        private final int[] offsets;
        private final int[] lengths;

        private final LLVMDebugValue.Builder[] partBuilders;
        private final Object[] partValues;

        private FragmentValueProvider(int[] offsets, int[] lengths, Builder[] partBuilders, Object[] partValues) {
            this.offsets = offsets;
            this.lengths = lengths;
            this.partBuilders = partBuilders;
            this.partValues = partValues;
        }

        private LLVMDebugValue getProvider(long bitOffset, int bitSize) {
            int start = (int) bitOffset;
            int end = start + bitSize;

            int bestFitIndex = -1;
            int bestFitSlack = -1;

            int i = 0;
            while (i < offsets.length && offsets[i] <= start) {
                if (partValues[i] != null && end <= (offsets[i] + lengths[i])) {
                    int newSlack = getSlack(i, start, end);
                    if (newSlack < bestFitSlack || bestFitSlack < 0) {
                        bestFitIndex = i;
                        bestFitSlack = newSlack;
                    }
                }
                i++;
            }

            if (bestFitIndex >= 0) {
                final LLVMDebugValue provider = partBuilders[bestFitIndex].build(partValues[bestFitIndex]);
                return new OffsetValueProvider(provider, offsets[bestFitIndex]);
            }

            return LLVMDebugValue.UNAVAILABLE;
        }

        private int getSlack(int partIndex, int start, int end) {
            int slack = start - offsets[partIndex];
            slack += offsets[partIndex] + lengths[partIndex] - end;
            return slack;
        }

        @Override
        public String describeValue(long bitOffset, int bitSize) {
            return getProvider(bitOffset, bitSize).describeValue(bitOffset, bitSize);
        }

        @Override
        public boolean canRead(long bitOffset, int bits) {
            return getProvider(bitOffset, bits).canRead(bitOffset, bits);
        }

        @Override
        public Object readBoolean(long bitOffset) {
            return getProvider(bitOffset, LLVMDebugTypeConstants.BOOLEAN_SIZE).readBoolean(bitOffset);
        }

        @Override
        public Object readFloat(long bitOffset) {
            return getProvider(bitOffset, LLVMDebugTypeConstants.FLOAT_SIZE).readFloat(bitOffset);
        }

        @Override
        public Object readDouble(long bitOffset) {
            return getProvider(bitOffset, LLVMDebugTypeConstants.DOUBLE_SIZE).readDouble(bitOffset);
        }

        @Override
        public Object read80BitFloat(long bitOffset) {
            return getProvider(bitOffset, LLVMDebugTypeConstants.LLVM80BIT_SIZE_ACTUAL).read80BitFloat(bitOffset);
        }

        @Override
        public Object readAddress(long bitOffset) {
            return getProvider(bitOffset, LLVMDebugTypeConstants.ADDRESS_SIZE).readAddress(bitOffset);
        }

        @Override
        public Object readUnknown(long bitOffset, int bitSize) {
            return getProvider(bitOffset, bitSize).readUnknown(bitOffset, bitSize);
        }

        @Override
        public Object computeAddress(long bitOffset) {
            return getProvider(bitOffset, 0).computeAddress(bitOffset);
        }

        @Override
        public Object readBigInteger(long bitOffset, int bitSize, boolean signed) {
            return getProvider(bitOffset, bitSize).readBigInteger(bitOffset, bitSize, signed);
        }

        @Override
        public LLVMDebugValue dereferencePointer(long bitOffset) {
            return getProvider(bitOffset, LLVMDebugTypeConstants.ADDRESS_SIZE).dereferencePointer(bitOffset);
        }

        @Override
        public boolean isInteropValue() {
            return false;
        }

        @Override
        public Object asInteropValue() {
            return null;
        }
    }

    private static final class OffsetValueProvider implements LLVMDebugValue {

        private final LLVMDebugValue baseProvider;
        private final long offset;

        private OffsetValueProvider(LLVMDebugValue baseProvider, long offset) {
            this.baseProvider = baseProvider;
            this.offset = offset;
        }

        private long getOffset(long originalOffset) {
            return originalOffset - offset;
        }

        @Override
        public String describeValue(long bitOffset, int bitSize) {
            return baseProvider.describeValue(getOffset(bitOffset), bitSize);
        }

        @Override
        public boolean canRead(long bitOffset, int bits) {
            return baseProvider.canRead(getOffset(bitOffset), bits);
        }

        @Override
        public Object readBoolean(long bitOffset) {
            return baseProvider.readBoolean(getOffset(bitOffset));
        }

        @Override
        public Object readFloat(long bitOffset) {
            return baseProvider.readFloat(getOffset(bitOffset));
        }

        @Override
        public Object readDouble(long bitOffset) {
            return baseProvider.readDouble(getOffset(bitOffset));
        }

        @Override
        public Object read80BitFloat(long bitOffset) {
            return baseProvider.read80BitFloat(getOffset(bitOffset));
        }

        @Override
        public Object readAddress(long bitOffset) {
            return baseProvider.readAddress(getOffset(bitOffset));
        }

        @Override
        public Object readUnknown(long bitOffset, int bitSize) {
            return baseProvider.readUnknown(getOffset(bitOffset), bitSize);
        }

        @Override
        public Object computeAddress(long bitOffset) {
            return baseProvider.computeAddress(getOffset(bitOffset));
        }

        @Override
        public Object readBigInteger(long bitOffset, int bitSize, boolean signed) {
            return baseProvider.readBigInteger(getOffset(bitOffset), bitSize, signed);
        }

        @Override
        public LLVMDebugValue dereferencePointer(long bitOffset) {
            return baseProvider.dereferencePointer(getOffset(bitOffset));
        }

        @Override
        public boolean isInteropValue() {
            return baseProvider.isInteropValue();
        }

        @Override
        public Object asInteropValue() {
            return baseProvider.asInteropValue();
        }
    }
}
