/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.vars;

import java.nio.ByteOrder;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNode.LLVMI64OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI8StoreNode.LLVMI8OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public abstract class AggregateLiteralInPlaceNode extends LLVMStatementNode {

    @Children private final LLVMOffsetStoreNode[] stores;

    @CompilationFinal(dimensions = 1) private final byte[] data;
    @CompilationFinal(dimensions = 1) private final int[] offsets;
    @CompilationFinal(dimensions = 1) private final int[] sizes;
    @CompilationFinal(dimensions = 1) private final int[] bufferOffsets;
    @CompilationFinal(dimensions = 1) private final LLVMGlobal[] descriptors;

    private static final ByteArraySupport byteSupport = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN ? ByteArraySupport.bigEndian() : ByteArraySupport.littleEndian();

    /**
     * This node initializes a block of memory with a combination of raw bytes and explicit store
     * nodes. When executed, it transfers all bytes from {@code data} to the target (using i8 and
     * i64 stores as appropriate), except for those covered by a node in {@code stores}. Every store
     * node has a corresponding entry in {@code offsets} and {@sizes}.
     */
    public AggregateLiteralInPlaceNode(byte[] data, LLVMOffsetStoreNode[] stores, int[] offsets, int[] sizes, int[] bufferOffsets, LLVMGlobal[] descriptors) {
        assert offsets.length == stores.length + 1 && stores.length == sizes.length;
        assert offsets[offsets.length - 1] == data.length : "offsets is expected to have a trailing entry with the overall size";
        assert bufferOffsets.length == descriptors.length;
        this.data = data;
        this.stores = stores;
        this.sizes = sizes;
        this.offsets = offsets;
        this.bufferOffsets = bufferOffsets;
        this.descriptors = descriptors;
    }

    @Specialization
    protected void initialize(VirtualFrame frame,
                    @Cached LLVMI8OffsetStoreNode storeI8,
                    @Cached LLVMI64OffsetStoreNode storeI64,
                    @Cached BranchProfile exception) {
        LLVMContext context = getContext();
        writePrimitives(context, storeI8, storeI64, exception);
        writeObjects(frame, context, exception);
    }

    private void writePrimitives(LLVMContext context, LLVMI8OffsetStoreNode storeI8, LLVMI64OffsetStoreNode storeI64, BranchProfile exception) {
        int offset = 0;
        int nextStore = 0;
        for (int i = 0; i < descriptors.length; i++) {
            LLVMPointer address = context.getSymbol(descriptors[i], exception);
            int bufferOffset = bufferOffsets[i];
            int bufferEnd = i == descriptors.length - 1 ? data.length : bufferOffsets[i + 1];
            while (offset < bufferEnd) {
                int nextStoreOffset = Math.min(offsets[nextStore], bufferEnd);
                offset = initializePrimitiveBlock(address, storeI8, storeI64, offset, nextStoreOffset, bufferOffset);
                if (offset < bufferEnd && nextStore < stores.length) {
                    offset += sizes[nextStore++];
                }
            }
        }
    }

    @ExplodeLoop
    private void writeObjects(VirtualFrame frame, LLVMContext context, BranchProfile exception) {
        int offset = 0;
        int nextStore = 0;
        for (int i = 0; i < descriptors.length; i++) {
            LLVMPointer address = context.getSymbol(descriptors[i], exception);
            int bufferOffset = bufferOffsets[i];
            int bufferEnd = i == descriptors.length - 1 ? data.length : bufferOffsets[i + 1];
            while (offset < bufferEnd) {
                offset = Math.min(offsets[nextStore], bufferEnd);
                if (offset < bufferEnd && nextStore < stores.length) {
                    stores[nextStore].executeWithTarget(frame, address, offset - bufferOffset);
                    offset += sizes[nextStore++];
                }
            }
        }
    }

    private int initializePrimitiveBlock(LLVMPointer address, LLVMI8OffsetStoreNode storeI8, LLVMI64OffsetStoreNode storeI64, int startOffset, int nextStoreOffset, int bufferOffset) {
        int offset = startOffset;
        // This loop ensures that the target offset is aligned for the 64-bit write loop below.
        // (the source offset might not be aligned)
        while (offset < nextStoreOffset && (((offset - bufferOffset) & 0x7) != 0)) {
            storeI8.executeWithTarget(address, offset - bufferOffset, data[offset]);
            offset++;
        }
        while (offset < (nextStoreOffset - 7)) {
            storeI64.executeWithTarget(address, offset - bufferOffset, byteSupport.getLong(data, offset));
            offset += Long.BYTES;
        }
        while (offset < nextStoreOffset) {
            storeI8.executeWithTarget(address, offset - bufferOffset, data[offset]);
            offset++;
        }
        return offset;
    }
}
