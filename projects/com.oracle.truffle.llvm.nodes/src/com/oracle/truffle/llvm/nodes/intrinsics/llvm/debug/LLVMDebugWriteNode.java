/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugValueProvider;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChildren({
                @NodeChild(value = "containerRead", type = LLVMExpressionNode.class),
                @NodeChild(value = "llvmValueRead", type = LLVMExpressionNode.class)
})
public abstract class LLVMDebugWriteNode extends LLVMExpressionNode {

    private final LLVMDebugBuilder builder;

    protected LLVMDebugWriteNode(LLVMDebugBuilder builder) {
        this.builder = builder;
    }

    protected LLVMDebugValueProvider.Builder createBuilder() {
        return builder.createBuilder(getContextReference());
    }

    public abstract static class SimpleWriteNode extends LLVMDebugWriteNode {

        protected SimpleWriteNode(LLVMDebugBuilder builder) {
            super(builder);
        }

        @Specialization
        protected Object write(LLVMDebugSimpleValue container, Object llvmValue, @Cached("createBuilder()") LLVMDebugValueProvider.Builder valueProcessor) {
            container.set(valueProcessor, llvmValue);
            return null;
        }
    }

    public abstract static class AggregateWriteNode extends LLVMDebugWriteNode {

        private final int partIndex;
        @CompilerDirectives.CompilationFinal(dimensions = 1) private final int[] clearIndices;

        protected AggregateWriteNode(LLVMDebugBuilder builder, int partIndex, int[] clearIndices) {
            super(builder);
            this.partIndex = partIndex;
            this.clearIndices = clearIndices;
        }

        @Specialization
        protected Object setPart(LLVMDebugAggregateValue container, Object partLLVMValue, @Cached("createBuilder()") LLVMDebugValueProvider.Builder valueProcessor) {
            container.setPart(partIndex, valueProcessor, partLLVMValue);
            clearIndices(container);
            return null;
        }

        @ExplodeLoop
        private void clearIndices(LLVMDebugAggregateValue value) {
            for (int i : clearIndices) {
                value.clear(i);
            }
        }
    }
}
