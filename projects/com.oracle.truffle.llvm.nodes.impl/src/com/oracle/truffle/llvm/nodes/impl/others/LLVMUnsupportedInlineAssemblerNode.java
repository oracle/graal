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
package com.oracle.truffle.llvm.nodes.impl.others;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMFunctionNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVM80BitFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMDoubleNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException.UnsupportedReason;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.floating.LLVM80BitFloat;

public class LLVMUnsupportedInlineAssemblerNode extends LLVMExpressionNode {

    public static class LLVMI1UnsupportedInlineAssemblerNode extends LLVMI1Node {

        @Override
        public boolean executeI1(VirtualFrame frame) {
            throw new LLVMUnsupportedException(UnsupportedReason.INLINE_ASSEMBLER);
        }

    }

    public static class LLVMI8UnsupportedInlineAssemblerNode extends LLVMI8Node {

        @Override
        public byte executeI8(VirtualFrame frame) {
            throw new LLVMUnsupportedException(UnsupportedReason.INLINE_ASSEMBLER);
        }

    }

    public static class LLVMI16UnsupportedInlineAssemblerNode extends LLVMI16Node {

        @Override
        public short executeI16(VirtualFrame frame) {
            throw new LLVMUnsupportedException(UnsupportedReason.INLINE_ASSEMBLER);
        }

    }

    public static class LLVMI32UnsupportedInlineAssemblerNode extends LLVMI32Node {

        @Override
        public int executeI32(VirtualFrame frame) {
            throw new LLVMUnsupportedException(UnsupportedReason.INLINE_ASSEMBLER);
        }

    }

    public static class LLVMI64UnsupportedInlineAssemblerNode extends LLVMI64Node {

        @Override
        public long executeI64(VirtualFrame frame) {
            throw new LLVMUnsupportedException(UnsupportedReason.INLINE_ASSEMBLER);
        }

    }

    public static class LLVMFloatUnsupportedInlineAssemblerNode extends LLVMFloatNode {

        @Override
        public float executeFloat(VirtualFrame frame) {
            throw new LLVMUnsupportedException(UnsupportedReason.INLINE_ASSEMBLER);
        }

    }

    public static class LLVMDoubleUnsupportedInlineAssemblerNode extends LLVMDoubleNode {

        @Override
        public double executeDouble(VirtualFrame frame) {
            throw new LLVMUnsupportedException(UnsupportedReason.INLINE_ASSEMBLER);
        }

    }

    public static class LLVM80BitFloatUnsupportedInlineAssemblerNode extends LLVM80BitFloatNode {

        @Override
        public LLVM80BitFloat execute80BitFloat(VirtualFrame frame) {
            throw new LLVMUnsupportedException(UnsupportedReason.INLINE_ASSEMBLER);
        }

    }

    public static class LLVMAddressUnsupportedInlineAssemblerNode extends LLVMAddressNode {

        @Override
        public LLVMAddress executePointee(VirtualFrame frame) {
            throw new LLVMUnsupportedException(UnsupportedReason.INLINE_ASSEMBLER);
        }

    }

    public static class LLVMFunctionUnsupportedInlineAssemblerNode extends LLVMFunctionNode {

        @Override
        public LLVMFunctionDescriptor executeFunction(VirtualFrame frame) {
            throw new LLVMUnsupportedException(UnsupportedReason.INLINE_ASSEMBLER);
        }

    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        throw new LLVMUnsupportedException(UnsupportedReason.INLINE_ASSEMBLER);
    }

}
