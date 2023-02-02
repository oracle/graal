/*
 * Copyright (c) 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.op;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChild("left")
@NodeChild("right")
@NodeChild("shift")
public abstract class LLVMFunnelShiftNode extends LLVMExpressionNode {

    public abstract static class Fshl_I8 extends LLVMFunnelShiftNode {

        @Specialization
        byte doFshl(byte left, byte right, byte shift) {
            return (byte) ((left << shift) | (byte) ((right & 0xFF) >>> (Byte.SIZE - shift)));
        }
    }

    public abstract static class Fshl_I16 extends LLVMFunnelShiftNode {

        @Specialization
        short doFshl(short left, short right, short shift) {
            return (short) ((left << shift) | (short) ((right & 0xFFFF) >>> (Short.SIZE - shift)));
        }
    }

    public abstract static class Fshl_I32 extends LLVMFunnelShiftNode {

        @Specialization
        int doFshl(int left, int right, int shift) {
            return (left << shift) | (right >>> (Integer.SIZE - shift));
        }
    }

    public abstract static class Fshl_I64 extends LLVMFunnelShiftNode {

        @Specialization
        long doFshl(long left, long right, long shift) {
            return (left << shift) | (right >>> (Long.SIZE - shift));
        }
    }

    public abstract static class Fshr_I8 extends LLVMFunnelShiftNode {

        @Specialization
        byte doFshl(byte left, byte right, byte shift) {
            return (byte) ((left << (Byte.SIZE - shift)) | (byte) ((right & 0xFF) >>> shift));
        }
    }

    public abstract static class Fshr_I16 extends LLVMFunnelShiftNode {

        @Specialization
        short doFshl(short left, short right, short shift) {
            return (short) ((left << (Short.SIZE - shift)) | (short) ((right & 0xFFFF) >>> shift));
        }
    }

    public abstract static class Fshr_I32 extends LLVMFunnelShiftNode {

        @Specialization
        int doFshl(int left, int right, int shift) {
            return (left << (Integer.SIZE - shift)) | (right >>> shift);
        }
    }

    public abstract static class Fshr_I64 extends LLVMFunnelShiftNode {

        @Specialization
        long doFshl(long left, long right, long shift) {
            return (left << (Long.SIZE - shift)) | (right >>> shift);
        }
    }
}
