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
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64UpdateFlagsNode.LLVMAMD64UpdateCPAZSOFlagsNode;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64WriteValueNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChildren({@NodeChild("a"), @NodeChild("src"), @NodeChild("dst")})
public abstract class LLVMAMD64CmpXchgNode extends LLVMExpressionNode {
    @Child protected LLVMAMD64UpdateCPAZSOFlagsNode flags;

    @Child protected LLVMAMD64WriteValueNode out1;
    @Child protected LLVMAMD64WriteValueNode out2;

    protected final ConditionProfile profile;

    private LLVMAMD64CmpXchgNode(LLVMAMD64UpdateCPAZSOFlagsNode flags, LLVMAMD64WriteValueNode out1, LLVMAMD64WriteValueNode out2) {
        this.flags = flags;
        this.out1 = out1;
        this.out2 = out2;
        profile = ConditionProfile.createCountingProfile();
    }

    public abstract static class LLVMAMD64CmpXchgbNode extends LLVMAMD64CmpXchgNode {
        public LLVMAMD64CmpXchgbNode(LLVMAMD64UpdateCPAZSOFlagsNode flags, LLVMAMD64WriteValueNode dst1, LLVMAMD64WriteValueNode dst2) {
            super(flags, dst1, dst2);
        }

        @Specialization
        protected Object execute(VirtualFrame frame, byte a, byte src, byte dst) {
            int result = a - dst;
            boolean carry = Byte.toUnsignedInt(a) < Byte.toUnsignedInt(dst);
            boolean adjust = (((a ^ dst) ^ result) & 0x10) != 0;
            flags.execute(frame, false, carry, adjust, result);
            if (profile.profile(a == dst)) {
                out1.execute(frame, src);
            } else {
                out2.execute(frame, dst);
            }
            return null;
        }
    }

    public abstract static class LLVMAMD64CmpXchgwNode extends LLVMAMD64CmpXchgNode {
        public LLVMAMD64CmpXchgwNode(LLVMAMD64UpdateCPAZSOFlagsNode flags, LLVMAMD64WriteValueNode dst1, LLVMAMD64WriteValueNode dst2) {
            super(flags, dst1, dst2);
        }

        @Specialization
        protected Object execute(VirtualFrame frame, short a, short src, short dst) {
            int result = a - dst;
            boolean carry = Short.toUnsignedInt(a) < Short.toUnsignedInt(dst);
            boolean adjust = (((a ^ dst) ^ result) & 0x10) != 0;
            flags.execute(frame, false, carry, adjust, result);
            if (profile.profile(a == dst)) {
                out1.execute(frame, src);
            } else {
                out2.execute(frame, dst);
            }
            return null;
        }
    }

    public abstract static class LLVMAMD64CmpXchglNode extends LLVMAMD64CmpXchgNode {
        public LLVMAMD64CmpXchglNode(LLVMAMD64UpdateCPAZSOFlagsNode flags, LLVMAMD64WriteValueNode dst1, LLVMAMD64WriteValueNode dst2) {
            super(flags, dst1, dst2);
        }

        @Specialization
        protected Object execute(VirtualFrame frame, int a, int src, int dst) {
            int result = a - dst;
            boolean carry = Integer.compareUnsigned(a, dst) < 0;
            boolean adjust = (((a ^ dst) ^ result) & 0x10) != 0;
            flags.execute(frame, false, carry, adjust, result);
            if (profile.profile(a == dst)) {
                out1.execute(frame, src);
            } else {
                out2.execute(frame, dst);
            }
            return null;
        }
    }

    public abstract static class LLVMAMD64CmpXchgqNode extends LLVMAMD64CmpXchgNode {
        public LLVMAMD64CmpXchgqNode(LLVMAMD64UpdateCPAZSOFlagsNode flags, LLVMAMD64WriteValueNode dst1, LLVMAMD64WriteValueNode dst2) {
            super(flags, dst1, dst2);
        }

        @Specialization
        protected Object execute(VirtualFrame frame, long a, long src, long dst) {
            long result = a - dst;
            boolean carry = Long.compareUnsigned(a, dst) < 0;
            boolean adjust = (((a ^ dst) ^ result) & 0x10) != 0;
            flags.execute(frame, false, carry, adjust, result);
            if (profile.profile(a == dst)) {
                out1.execute(frame, src);
            } else {
                out2.execute(frame, dst);
            }
            return null;
        }

        @Specialization
        protected Object execute(VirtualFrame frame, LLVMAddress a, LLVMAddress src, LLVMAddress dst) {
            long result = a.getVal() - dst.getVal();
            boolean carry = Long.compareUnsigned(a.getVal(), dst.getVal()) < 0;
            boolean adjust = (((a.getVal() ^ dst.getVal()) ^ result) & 0x10) != 0;
            flags.execute(frame, false, carry, adjust, result);
            if (profile.profile(a.equals(dst))) {
                out1.execute(frame, src);
            } else {
                out2.execute(frame, dst);
            }
            return null;
        }
    }
}
