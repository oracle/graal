/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.memory;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.Type;

@NodeChildren({@NodeChild, @NodeChild, @NodeChild})
public abstract class LLVMInsertValueNode extends LLVMExpressionNode {

    protected final long sourceAggregateSize;
    protected final int offset;
    @Child private LLVMStoreNode store;
    @Child private LLVMMemMoveNode memMove;
    private final Type type;

    public LLVMInsertValueNode(LLVMStoreNode store, LLVMMemMoveNode memMove, long sourceAggregateSize, int offset, Type type) {
        this.sourceAggregateSize = sourceAggregateSize;
        this.offset = offset;
        this.store = store;
        this.memMove = memMove;
        this.type = type;
    }

    @Specialization
    public LLVMAddress executeLLVMAddress(VirtualFrame frame, LLVMAddress sourceAggr, LLVMAddress targetAggr, Object element) {
        memMove.executeWithTarget(frame, targetAggr, sourceAggr, sourceAggregateSize);
        store.executeWithTarget(frame, targetAggr.increment(offset), element);
        return targetAggr;
    }

    @Specialization
    public Object executeVoid(VirtualFrame frame, LLVMTruffleObject sourceAggr, LLVMTruffleObject targetAggr, Object element) {
        memMove.executeWithTarget(frame, targetAggr, sourceAggr, sourceAggregateSize);
        store.executeWithTarget(frame, targetAggr.increment(offset, type), element);
        return targetAggr;
    }

}
