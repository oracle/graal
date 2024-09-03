/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.memory.store;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeChild(value = "target", type = LLVMExpressionNode.class)
@NodeChild(value = "offset", type = LLVMExpressionNode.class)
@NodeChild(value = "value", type = LLVMExpressionNode.class)
public abstract class LLVMOffsetStoreNode extends LLVMNode {

    public abstract void executeWithTarget(VirtualFrame frame, LLVMPointer receiver, long offset);

    /**
     * Primitive stores shouldn't need a frame. These can also be used e.g. in InteropLibrary.
     */
    public abstract static class LLVMPrimitiveOffsetStoreNode extends LLVMOffsetStoreNode {

        public abstract void executeWithTargetGeneric(LLVMPointer receiver, long offset, Object value);

        public static final LLVMPrimitiveOffsetStoreNode create(LLVMInteropType.ValueKind kind) {
            return CommonNodeFactory.createOffsetStoreNode(kind);
        }

        public static LLVMPrimitiveOffsetStoreNode getUncached(LLVMInteropType.ValueKind kind) {
            return CommonNodeFactory.getUncachedOffsetStoreNode(kind);
        }
    }

    public abstract static class LLVMGenericOffsetStoreNode extends LLVMOffsetStoreNode {

        @Child private LLVMStoreNode store;

        protected LLVMGenericOffsetStoreNode(LLVMStoreNode store) {
            this.store = store;
        }

        @Specialization
        protected void doOp(VirtualFrame frame, LLVMPointer addr, long offset, Object value) {
            store.executeWithTarget(frame, addr.increment(offset), value);
        }
    }
}
