/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNode.LLVMI64OptimizedStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI8StoreNode.LLVMI8OptimizedStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMOptimizedStoreNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeChild(type = LLVMExpressionNode.class)
public abstract class AggregateLiteralInPlaceNode extends LLVMStatementNode {

    @Children private final LLVMOptimizedStoreNode[] stores;

    @CompilationFinal(dimensions = 1) private final byte[] data;
    @CompilationFinal(dimensions = 1) private final int[] offsets;
    @CompilationFinal(dimensions = 1) private final int[] sizes;

    public AggregateLiteralInPlaceNode(byte[] data, LLVMOptimizedStoreNode[] stores, int[] offsets, int[] sizes) {
        assert offsets.length == stores.length + 1 && stores.length == sizes.length;
        assert offsets[offsets.length - 1] == data.length;
        this.data = data;
        this.stores = stores;
        this.sizes = sizes;
        this.offsets = offsets;
    }

    @ExplodeLoop
    @Specialization
    protected void initialize(VirtualFrame frame, LLVMPointer address,
                    @Cached LLVMI8OptimizedStoreNode storeI8,
                    @Cached LLVMI64OptimizedStoreNode storeI64) {
        int offset = 0;
        int nextStore = 0;
        ByteBuffer view = ByteBuffer.wrap(data);
        view.order(ByteOrder.nativeOrder());
        while (offset < data.length) {
            int nextStoreOffset = offsets[nextStore];
            while (offset < nextStoreOffset && ((offset & 0x7) != 0)) {
                storeI8.executeWithTarget(address, offset, data[offset]);
                offset++;
            }
            while (offset < (nextStoreOffset - 7)) {
                storeI64.executeWithTarget(address, offset, view.getLong(offset));
                offset += Long.BYTES;
            }
            while (offset < nextStoreOffset) {
                storeI8.executeWithTarget(address, offset, data[offset]);
                offset++;
            }
            if (nextStore < stores.length) {
                stores[nextStore].executeWithTarget(frame, address, nextStoreOffset);
                offset += sizes[nextStore++];
            }
        }
    }
}
