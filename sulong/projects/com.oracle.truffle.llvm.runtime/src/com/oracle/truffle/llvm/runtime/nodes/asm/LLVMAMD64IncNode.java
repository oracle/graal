/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.asm;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64UpdateFlagsNode.LLVMAMD64UpdatePZSOFlagsNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChild("valueNode")
public abstract class LLVMAMD64IncNode extends LLVMExpressionNode {
    @Child protected LLVMAMD64UpdatePZSOFlagsNode flags;

    public LLVMAMD64IncNode(LLVMAMD64UpdatePZSOFlagsNode flags) {
        this.flags = flags;
    }

    public abstract static class LLVMAMD64IncbNode extends LLVMAMD64IncNode {
        public LLVMAMD64IncbNode(LLVMAMD64UpdatePZSOFlagsNode flags) {
            super(flags);
        }

        @Specialization
        protected byte doI8(VirtualFrame frame, byte value) {
            byte result = (byte) (value + 1);
            boolean of = value == Byte.MAX_VALUE;
            flags.execute(frame, of, result);
            return result;
        }
    }

    public abstract static class LLVMAMD64IncwNode extends LLVMAMD64IncNode {
        public LLVMAMD64IncwNode(LLVMAMD64UpdatePZSOFlagsNode flags) {
            super(flags);
        }

        @Specialization
        protected short doI16(VirtualFrame frame, short value) {
            short result = (short) (value + 1);
            boolean of = value == Short.MAX_VALUE;
            flags.execute(frame, of, result);
            return result;
        }
    }

    public abstract static class LLVMAMD64InclNode extends LLVMAMD64IncNode {
        public LLVMAMD64InclNode(LLVMAMD64UpdatePZSOFlagsNode flags) {
            super(flags);
        }

        @Specialization
        protected int doI32(VirtualFrame frame, int value) {
            int result = value + 1;
            boolean of = value == Integer.MAX_VALUE;
            flags.execute(frame, of, result);
            return result;
        }
    }

    public abstract static class LLVMAMD64IncqNode extends LLVMAMD64IncNode {
        public LLVMAMD64IncqNode(LLVMAMD64UpdatePZSOFlagsNode flags) {
            super(flags);
        }

        @Specialization
        protected long doI64(VirtualFrame frame, long value) {
            long result = value + 1;
            boolean of = value == Long.MAX_VALUE;
            flags.execute(frame, of, result);
            return result;
        }
    }
}
