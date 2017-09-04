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
package com.oracle.truffle.llvm.nodes.asm;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64UpdateFlagsNode.LLVMAMD64UpdateCPAZSOFlagsNode;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64WriteRegisterNode.LLVMAMD64WriteI16RegisterNode;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64WriteRegisterNode.LLVMAMD64WriteI32RegisterNode;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64WriteRegisterNode.LLVMAMD64WriteI64RegisterNode;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64WriteRegisterNode.LLVMAMD64WriteI8RegisterNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChildren({@NodeChild("a"), @NodeChild("src"), @NodeChild("dst")})
public abstract class LLVMAMD64CmpXchgNode extends LLVMExpressionNode {
    @Child protected LLVMAMD64UpdateCPAZSOFlagsNode flags;

    private LLVMAMD64CmpXchgNode(LLVMAMD64UpdateCPAZSOFlagsNode flags) {
        this.flags = flags;
    }

    public abstract static class LLVMAMD64CmpXchgbNode extends LLVMAMD64CmpXchgNode {
        @Child LLVMAMD64WriteI8RegisterNode out2;

        public LLVMAMD64CmpXchgbNode(LLVMAMD64UpdateCPAZSOFlagsNode flags, LLVMAMD64WriteI8RegisterNode out2) {
            super(flags);
            this.out2 = out2;
        }

        @Specialization
        protected byte executeI8(VirtualFrame frame, byte a, byte src, byte dst) {
            int result = a - dst;
            boolean carry = Byte.toUnsignedInt(a) < Byte.toUnsignedInt(dst);
            boolean adjust = (((a ^ dst) ^ result) & 0x10) != 0;
            flags.execute(frame, false, carry, adjust, result);
            if (a == dst) {
                return src;
            } else {
                out2.execute(frame, dst);
                return dst;
            }
        }
    }

    public abstract static class LLVMAMD64CmpXchgwNode extends LLVMAMD64CmpXchgNode {
        @Child LLVMAMD64WriteI16RegisterNode out2;

        public LLVMAMD64CmpXchgwNode(LLVMAMD64UpdateCPAZSOFlagsNode flags, LLVMAMD64WriteI16RegisterNode out2) {
            super(flags);
            this.out2 = out2;
        }

        @Specialization
        protected short executeI16(VirtualFrame frame, short a, short src, short dst) {
            int result = a - dst;
            boolean carry = Short.toUnsignedInt(a) < Short.toUnsignedInt(dst);
            boolean adjust = (((a ^ dst) ^ result) & 0x10) != 0;
            flags.execute(frame, false, carry, adjust, result);
            if (a == dst) {
                return src;
            } else {
                out2.execute(frame, dst);
                return dst;
            }
        }
    }

    public abstract static class LLVMAMD64CmpXchglNode extends LLVMAMD64CmpXchgNode {
        @Child LLVMAMD64WriteI32RegisterNode out2;

        public LLVMAMD64CmpXchglNode(LLVMAMD64UpdateCPAZSOFlagsNode flags, LLVMAMD64WriteI32RegisterNode out2) {
            super(flags);
            this.out2 = out2;
        }

        @Specialization
        protected int executeI32(VirtualFrame frame, int a, int src, int dst) {
            int result = a - dst;
            boolean carry = Integer.compareUnsigned(a, dst) < 0;
            boolean adjust = (((a ^ dst) ^ result) & 0x10) != 0;
            flags.execute(frame, false, carry, adjust, result);
            if (a == dst) {
                return src;
            } else {
                out2.execute(frame, dst);
                return dst;
            }
        }
    }

    public abstract static class LLVMAMD64CmpXchglrNode extends LLVMAMD64CmpXchgNode {
        @Child LLVMAMD64WriteI32RegisterNode out1;
        @Child LLVMAMD64WriteI32RegisterNode out2;

        public LLVMAMD64CmpXchglrNode(LLVMAMD64UpdateCPAZSOFlagsNode flags, LLVMAMD64WriteI32RegisterNode out1, LLVMAMD64WriteI32RegisterNode out2) {
            super(flags);
            this.out1 = out1;
            this.out2 = out2;
        }

        @Specialization
        protected Object executeObject(VirtualFrame frame, int a, int src, int dst) {
            int result = a - dst;
            boolean carry = Integer.compareUnsigned(a, dst) < 0;
            boolean adjust = (((a ^ dst) ^ result) & 0x10) != 0;
            flags.execute(frame, false, carry, adjust, result);
            if (a == dst) {
                out1.execute(frame, src);
                return null;
            } else {
                out2.execute(frame, dst);
                return null;
            }
        }
    }

    public abstract static class LLVMAMD64CmpXchgqNode extends LLVMAMD64CmpXchgNode {
        private final LLVMAMD64WriteI64RegisterNode out2;

        public LLVMAMD64CmpXchgqNode(LLVMAMD64UpdateCPAZSOFlagsNode flags, LLVMAMD64WriteI64RegisterNode out2) {
            super(flags);
            this.out2 = out2;
        }

        @Specialization
        protected long executeI64(VirtualFrame frame, long a, long src, long dst) {
            long result = a - dst;
            boolean carry = Long.compareUnsigned(a, dst) < 0;
            boolean adjust = (((a ^ dst) ^ result) & 0x10) != 0;
            flags.execute(frame, false, carry, adjust, result);
            if (a == dst) {
                return src;
            } else {
                out2.execute(frame, dst);
                return dst;
            }
        }
    }
}
