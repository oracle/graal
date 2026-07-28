/*
 * Copyright (c) 2026, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeChild(type = LLVMExpressionNode.class, value = "address")
@NodeChild(type = LLVMExpressionNode.class, value = "value")
@NodeChild(type = LLVMExpressionNode.class, value = "count")
@NodeChild(type = LLVMExpressionNode.class, value = "isVolatile")
@NodeField(type = long.class, name = "patternSize")
public abstract class LLVMMemSetPattern extends LLVMBuiltin {

    @Child private LLVMStoreNode store;

    protected LLVMMemSetPattern(LLVMStoreNode store) {
        this.store = store;
    }

    protected abstract long getPatternSize();

    @Specialization
    protected Object doI32(VirtualFrame frame, LLVMPointer address, Object value, int count, @SuppressWarnings("unused") boolean isVolatile) {
        return doPattern(frame, address, value, Integer.toUnsignedLong(count));
    }

    @Specialization
    protected Object doI64(VirtualFrame frame, LLVMPointer address, Object value, long count, @SuppressWarnings("unused") boolean isVolatile) {
        return doPattern(frame, address, value, count);
    }

    private Object doPattern(VirtualFrame frame, LLVMPointer address, Object value, long count) {
        for (long i = 0; Long.compareUnsigned(i, count) < 0; i++) {
            store.executeWithTarget(frame, address.increment(i * getPatternSize()), value);
        }
        return null;
    }
}
