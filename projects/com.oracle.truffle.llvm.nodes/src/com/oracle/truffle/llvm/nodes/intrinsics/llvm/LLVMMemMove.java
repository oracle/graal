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
package com.oracle.truffle.llvm.nodes.intrinsics.llvm;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMGlobalVariableDescriptor;
import com.oracle.truffle.llvm.runtime.memory.LLVMHeapFunctions;
import com.oracle.truffle.llvm.runtime.memory.LLVMHeapFunctions.MemCopyNode;

public abstract class LLVMMemMove {

    @GenerateNodeFactory
    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class, value = "dest"), @NodeChild(type = LLVMExpressionNode.class, value = "src"),
                    @NodeChild(type = LLVMExpressionNode.class, value = "length"),
                    @NodeChild(type = LLVMExpressionNode.class, value = "align"), @NodeChild(type = LLVMExpressionNode.class, value = "isVolatile")})
    public abstract static class LLVMMemMoveI64 extends LLVMExpressionNode {

        @Child private MemCopyNode memMove;

        protected LLVMMemMoveI64(LLVMHeapFunctions heapFunctions) {
            memMove = heapFunctions.createMemMoveNode();
        }

        @SuppressWarnings("unused")
        @Specialization
        public Object executeVoid(LLVMAddress dest, LLVMAddress source, long length, int align, boolean isVolatile) {
            memMove.execute(dest, source, length);
            return null;
        }

        @SuppressWarnings("unused")
        @Specialization
        public Object executeVoid(LLVMGlobalVariableDescriptor dest, LLVMAddress source, long length, int align, boolean isVolatile) {
            memMove.execute(dest.getNativeAddress(), source, length);
            return null;
        }

        @SuppressWarnings("unused")
        @Specialization
        public Object executeVoid(LLVMAddress dest, LLVMGlobalVariableDescriptor source, long length, int align, boolean isVolatile) {
            memMove.execute(dest, source.getNativeAddress(), length);
            return null;
        }

        @SuppressWarnings("unused")
        @Specialization
        public Object executeVoid(LLVMGlobalVariableDescriptor dest, LLVMGlobalVariableDescriptor source, long length, int align, boolean isVolatile) {
            memMove.execute(dest.getNativeAddress(), source.getNativeAddress(), length);
            return null;
        }

    }

}
