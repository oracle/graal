/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.c;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemSetNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public abstract class LLVMMemIntrinsic extends LLVMExpressionNode {
    @NodeChild(value = "dst", type = LLVMExpressionNode.class)
    @NodeChild(value = "value", type = LLVMExpressionNode.class)
    @NodeChild(value = "len", type = LLVMExpressionNode.class)
    public abstract static class LLVMLibcMemset extends LLVMMemIntrinsic {
        @Child private LLVMMemSetNode memset;

        public LLVMLibcMemset(LLVMMemSetNode memset) {
            this.memset = memset;
        }

        @Specialization
        protected Object op(Object dst, int val, int len) {
            memset.executeWithTarget(dst, (byte) val, len);
            return dst;
        }

        @Specialization
        protected Object op(Object dst, int val, long len) {
            memset.executeWithTarget(dst, (byte) val, len);
            return dst;
        }

        @Specialization
        protected Object op(Object dst, byte val, int len) {
            memset.executeWithTarget(dst, val, len);
            return dst;
        }

        @Specialization
        protected Object op(Object dst, byte val, long len) {
            memset.executeWithTarget(dst, val, len);
            return dst;
        }
    }

    @NodeChild(value = "dst", type = LLVMExpressionNode.class)
    @NodeChild(value = "src", type = LLVMExpressionNode.class)
    @NodeChild(value = "len", type = LLVMExpressionNode.class)
    public abstract static class LLVMLibcMemcpy extends LLVMMemIntrinsic {
        @Child private LLVMMemMoveNode memcpy;

        public LLVMLibcMemcpy(LLVMMemMoveNode memcpy) {
            this.memcpy = memcpy;
        }

        @Specialization
        protected Object op(Object dst, Object src, int len) {
            memcpy.executeWithTarget(dst, src, len);
            return dst;
        }

        @Specialization
        protected Object op(Object dst, Object src, long len) {
            memcpy.executeWithTarget(dst, src, len);
            return dst;
        }
    }
}
